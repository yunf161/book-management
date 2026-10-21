package com.yunfeil161.ailibrary.book.cover.service;

import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.book.catalog.dto.BookUpdateRequest;
import com.yunfeil161.ailibrary.book.catalog.entity.Book;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.cover.config.OssProperties;
import com.yunfeil161.ailibrary.book.cover.dto.BookCoverResponse;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 封面与书目使用同一个资源锁；数据库版本和缓存删除规则也与书目保持一致。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookCoverService {

  private final BookMapper books;
  private final CoverStorage storage;
  private final OssProperties properties;
  private final PermissionAccess permissions;
  private final CacheClient cache;

  @DistributedLock(
    keys = "'lock:resource:book:' + #bookId",
    mode = LockMode.READ
  )
  public BookCoverResponse find(Long bookId) {
    return response(requireBook(bookId));
  }

  /** 公开封面沿用公开书目读取规则，不提供任意 objectKey 下载接口。 */
  @DistributedLock(
    keys = "'lock:resource:book:' + #bookId",
    mode = LockMode.READ
  )
  public String signedUrl(Long bookId) {
    Book book = requireBook(bookId);
    if (
      book.getCoverObjectKey() == null || book.getCoverObjectKey().isBlank()
    ) {
      throw new NotFoundException("图书尚未设置封面");
    }
    return storage.signedUrl(book.getCoverObjectKey());
  }

  @Transactional
  @DistributedLock(keys = "'lock:resource:book:' + #bookId")
  @Audited(resource = AuditResource.BOOK, id = "#bookId")
  public BookCoverResponse save(
    Long bookId,
    Long expectedVersion,
    MultipartFile file,
    boolean create
  ) {
    permissions.require(PermissionCodes.BOOK_UPDATE);
    Book book = requireVersion(bookId, expectedVersion);
    boolean hasCover =
      book.getCoverObjectKey() != null && !book.getCoverObjectKey().isBlank();
    if (create && hasCover) throw new ConflictException(
      "图书已有封面，请使用替换接口"
    );
    if (!create && !hasCover) throw new NotFoundException(
      "图书尚未设置封面，请先上传"
    );

    byte[] content = readFile(file);
    String format = validateImage(content, file.getContentType());
    // 对象归属由服务端生成，客户端不能指定路径或覆写其他图书的对象。
    String key =
      "library/covers/" + bookId + "/" + UUID.randomUUID() + "." + format;
    try {
      storage.upload(
        key,
        content,
        "image/" + (format.equals("jpg") ? "jpeg" : format)
      );
    } catch (RuntimeException failure) {
      safeDelete(key);
      throw failure;
    }
    // OSS 不参与数据库事务：回滚清理新对象，提交后才清理旧对象。
    synchronizeObjects(key, book.getCoverObjectKey(), bookId);
    update(bookId, expectedVersion, key);
    book.setCoverObjectKey(key);
    book.setVersion(expectedVersion + 1);
    return response(book);
  }

  @Transactional
  @DistributedLock(keys = "'lock:resource:book:' + #bookId")
  @Audited(resource = AuditResource.BOOK, id = "#bookId")
  public BookCoverResponse delete(Long bookId, Long expectedVersion) {
    permissions.require(PermissionCodes.BOOK_UPDATE);
    Book book = requireVersion(bookId, expectedVersion);
    if (book.getCoverObjectKey() == null) return response(book);
    synchronizeObjects(null, book.getCoverObjectKey(), bookId);
    update(bookId, expectedVersion, null);
    book.setCoverObjectKey(null);
    book.setVersion(expectedVersion + 1);
    return response(book);
  }

  private void update(Long id, Long version, String key) {
    BookUpdateRequest request = new BookUpdateRequest();
    request.setExpectedVersion(version);
    request.setCoverObjectKey(key);
    int affected = books.update(id, request);
    if (affected == 0) throw new ConflictException(
      "图书版本已过期，请刷新后重试"
    );
    if (affected != 1) throw new IllegalStateException(
      "封面更新影响了多条图书记录"
    );
    cache.deleteRequired(BookCacheKeys.BOOK_BY_ID + id);
  }

  private Book requireBook(Long id) {
    if (id == null || id <= 0) throw new WrongFormatException(
      "图书ID必须为正数"
    );
    Book book = books.findById(id);
    if (book == null) throw new NotFoundException("未找到图书");
    return book;
  }

  private Book requireVersion(Long id, Long version) {
    if (version == null || version < 0) throw new WrongFormatException(
      "expectedVersion不能为空且不能小于0"
    );
    Book book = requireBook(id);
    if (!version.equals(book.getVersion())) throw new ConflictException(
      "图书版本已过期，请刷新后重试"
    );
    return book;
  }

  private byte[] readFile(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new WrongFormatException(
      "封面文件不能为空"
    );
    if (
      file.getSize() > properties.getMaxCoverBytes()
    ) throw new WrongFormatException("封面不能超过配置大小，默认5MB");
    try {
      return file.getBytes();
    } catch (IOException failure) {
      throw new WrongFormatException("封面文件无法读取");
    }
  }

  /** 解码核验图片，不依赖上传文件名或客户端声明的 MIME；拒绝SVG和伪装图片。 */
  private String validateImage(byte[] bytes, String contentType) {
    try (
      ImageInputStream stream = ImageIO.createImageInputStream(
        new ByteArrayInputStream(bytes)
      )
    ) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new WrongFormatException(
        "仅支持有效的PNG、JPEG、GIF封面"
      );
      ImageReader reader = readers.next();
      try {
        reader.setInput(stream);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (format.equals("jpeg")) format = "jpg";
        String mime = "image/" + (format.equals("jpg") ? "jpeg" : format);
        if (
          !java.util.Set.of("png", "jpg", "gif").contains(format) ||
          contentType == null ||
          !mime.equalsIgnoreCase(contentType)
        ) {
          throw new WrongFormatException(
            "图片内容与类型不匹配，仅支持PNG、JPEG、GIF"
          );
        }
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > 20_000_000) throw new WrongFormatException(
          "封面像素不得超过2000万"
        );
        reader.read(0);
        return format;
      } finally {
        reader.dispose();
      }
    } catch (IOException failure) {
      throw new WrongFormatException("封面图片损坏或无法解析");
    }
  }

  private BookCoverResponse response(Book book) {
    return new BookCoverResponse(
      book.getId(),
      book.getCoverObjectKey(),
      book.getCoverObjectKey() == null || book.getCoverObjectKey().isBlank()
        ? null
        : "/books/" + book.getId() + "/cover/content",
      book.getVersion()
    );
  }

  private void synchronizeObjects(String newKey, String oldKey, Long bookId) {
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status != STATUS_COMMITTED) {
            if (newKey != null) safeDelete(newKey);
          } else if (
            oldKey != null &&
            oldKey.startsWith("library/covers/" + bookId + "/")
          ) {
            // 仅删除本模块管理的旧对象，避免误删 Tlias 或历史共享图片。
            safeDelete(oldKey);
          }
        }
      }
    );
  }

  private void safeDelete(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException failure) {
      log.error("封面对象清理失败，需重试清理：objectKey={}", key, failure);
    }
  }
}
