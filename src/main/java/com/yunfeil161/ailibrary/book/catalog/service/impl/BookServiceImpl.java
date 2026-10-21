package com.yunfeil161.ailibrary.book.catalog.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.book.catalog.dto.BookCreateRequest;
import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.book.catalog.dto.BookUpdateRequest;
import com.yunfeil161.ailibrary.book.catalog.entity.Book;
import com.yunfeil161.ailibrary.book.catalog.entity.BookDetailVO;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.catalog.service.BookService;
import com.yunfeil161.ailibrary.book.category.entity.BookCategory;
import com.yunfeil161.ailibrary.book.category.mapper.BookCategoryMapper;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

  private final com.yunfeil161.ailibrary.recommendation.service.TagService tagService;
  private final BookMapper bookMapper;
  private final BookCategoryMapper categoryMapper;
  private final CacheClient cacheClient;
  private final PermissionAccess permissionAccess;

  @Override
  @DistributedLock(
    keys = "'lock:resource:book:' + (#query != null && #query.id != null ? #query.id : 'query')",
    mode = LockMode.READ
  )
  public PageResult<BookDetailVO> getBooks(BookQueryRequest query) {
    if (query == null) query = new BookQueryRequest();
    if (isIdOnlyQuery(query)) {
      BookDetailVO book = cacheClient.queryWithPassThrough(
        BookCacheKeys.BOOK_BY_ID,
        query.getId(),
        BookDetailVO.class,
        bookMapper::findDetailById,
        BookCacheKeys.ID_TTL_MINUTES,
        TimeUnit.MINUTES
      );
      if (book == null) throw new NotFoundException("未找到图书");
      // 兼容升级前缓存中的对象路径，返回可直接读取图片的稳定接口地址。
      book.setCoverUrl(
        blank(book.getCoverObjectKey())
          ? null
          : "/books/" + book.getId() + "/cover/content"
      );
      book.setTags(tagService.bookTags(book.getId()));
      return new PageResult<>(1L, List.of(book));
    }
    startPage(query.getPage(), query.getPageSize());
    List<BookDetailVO> rows = bookMapper.find(query);
    if (query.getId() != null && rows.isEmpty()) throw new NotFoundException(
      "未找到图书"
    );
    PageInfo<BookDetailVO> info = new PageInfo<>(rows);
    rows.forEach(book -> book.setTags(tagService.bookTags(book.getId())));
    return new PageResult<>(info.getTotal(), info.getList());
  }

  @Override
  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:book-category:' + #request.categoryId",
      "'lock:resource:book:create'",
    }
  )
  @Audited(resource = AuditResource.BOOK, id = "")
  public BookDetailVO createBook(BookCreateRequest request) {
    // 书目写操作按照角色权限表显式授权。
    permissionAccess.require(PermissionCodes.BOOK_CREATE);
    if (
      request == null ||
      request.getCategoryId() == null ||
      blank(request.getTitle()) ||
      blank(request.getAuthor())
    ) {
      throw new WrongFormatException("分类ID、书名和作者不能为空");
    }
    requireActiveCategory(request.getCategoryId());
    // 新封面必须走上传接口，由服务端核验内容并生成属于当前图书的对象路径。
    if (!blank(request.getCoverObjectKey())) {
      throw new WrongFormatException(
        "请先创建图书，再通过图书封面接口上传图片"
      );
    }
    String isbn = normalizeNullable(request.getIsbn());
    if (
      isbn != null && bookMapper.countByIsbn(isbn, null) > 0
    ) throw new ConflictException("ISBN已存在");
    Book book = new Book();
    book.setCategoryId(request.getCategoryId());
    book.setTitle(request.getTitle().trim());
    book.setSubtitle(normalizeNullable(request.getSubtitle()));
    book.setAuthor(request.getAuthor().trim());
    book.setIsbn(isbn);
    book.setPublisher(normalizeNullable(request.getPublisher()));
    book.setPublishedDate(request.getPublishedDate());
    book.setEdition(normalizeNullable(request.getEdition()));
    book.setLanguage(
      blank(request.getLanguage()) ? "zh-CN" : request.getLanguage().trim()
    );
    book.setDescription(request.getDescription());
    book.setCoverObjectKey(normalizeNullable(request.getCoverObjectKey()));
    book.setStatus(CatalogStatus.ACTIVE);
    book.setVersion(0L);
    if (
      bookMapper.insert(book) != 1 || book.getId() == null
    ) throw new IllegalStateException("新增图书失败");
    if (request.getTagIds() != null) tagService.applyBookTags(
      book.getId(),
      request.getTagIds()
    );
    cacheClient.deleteRequired(BookCacheKeys.BOOK_BY_ID + book.getId());
    BookDetailVO created = bookMapper.findDetailById(book.getId());
    created.setTags(tagService.bookTags(book.getId()));
    return created;
  }

  @Override
  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + #id",
      "'lock:resource:book-category:' + (#request != null && #request.categoryId != null ? #request.categoryId : 'unchanged')",
    }
  )
  @Audited(resource = AuditResource.BOOK, id = "#id")
  public BookDetailVO updateBook(Long id, BookUpdateRequest request) {
    // 权限校验先于业务数据读取，防止通过资源是否存在探测管理数据。
    permissionAccess.require(PermissionCodes.BOOK_UPDATE);
    if (
      id == null ||
      request == null ||
      request.getExpectedVersion() == null ||
      request.getExpectedVersion() < 0
    ) {
      throw new WrongFormatException("图书ID和有效的expectedVersion不能为空");
    }
    Book current = requireBook(id);
    // 保留旧客户端携带原路径的兼容性，但不允许绕过封面接口绑定或清除对象。
    if (
      request.isCoverObjectKeyProvided() &&
      !java.util.Objects.equals(
        normalizeNullable(request.getCoverObjectKey()),
        current.getCoverObjectKey()
      )
    ) {
      throw new WrongFormatException("请通过图书封面接口上传、替换或删除封面");
    }
    if (request.getCategoryId() != null) requireActiveCategory(
      request.getCategoryId()
    );
    if (
      request.getTitle() != null && blank(request.getTitle())
    ) throw new WrongFormatException("书名不能为空");
    if (
      request.getAuthor() != null && blank(request.getAuthor())
    ) throw new WrongFormatException("作者不能为空");
    if (
      request.getLanguage() != null && blank(request.getLanguage())
    ) throw new WrongFormatException("语言不能为空");
    if (request.isIsbnProvided()) {
      request.setIsbn(normalizeNullable(request.getIsbn()));
      if (
        request.getIsbn() != null &&
        bookMapper.countByIsbn(request.getIsbn(), id) > 0
      ) {
        throw new ConflictException("ISBN已存在");
      }
    }
    int rows = bookMapper.update(id, request);
    if (rows == 0) {
      if (bookMapper.findById(id) == null) throw new NotFoundException(
        "图书已被删除"
      );
      throw new ConflictException("图书版本已过期，请刷新后重试");
    }
    if (rows != 1) throw new IllegalStateException("图书更新影响了多条记录");
    if (request.getTagIds() != null) tagService.applyBookTags(
      id,
      request.getTagIds()
    );
    cacheClient.deleteRequired(BookCacheKeys.BOOK_BY_ID + id);
    BookDetailVO updated = bookMapper.findDetailById(id);
    updated.setTags(tagService.bookTags(id));
    return updated;
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:book:' + #id")
  @Audited(resource = AuditResource.BOOK, id = "#id")
  public String deleteBook(Long id) {
    // 删除书目需要独立的删除权限。
    permissionAccess.require(PermissionCodes.BOOK_DELETE);
    Book existing = requireBook(id);
    if (existing.getStatus() == CatalogStatus.DISABLED) {
      // 逻辑删除保持幂等，避免客户端因响应丢失而重试时得到误导性失败。
      return existing.getTitle();
    }
    if (bookMapper.countCopies(id) > 0) {
      throw new ConflictException("图书仍有副本，请先将副本下架");
    }
    if (bookMapper.disable(id, existing.getVersion()) != 1) {
      throw new ConflictException("图书状态或版本已变化，请刷新后重试");
    }
    cacheClient.deleteRequired(BookCacheKeys.BOOK_BY_ID + id);
    return existing.getTitle();
  }

  private Book requireBook(Long id) {
    if (id == null) throw new WrongFormatException("图书ID不能为空");
    Book book = bookMapper.findById(id);
    if (book == null) throw new NotFoundException("未找到图书");
    return book;
  }

  private void requireActiveCategory(Long id) {
    BookCategory category = categoryMapper.findById(id);
    if (category == null) throw new NotFoundException("图书分类不存在");
    if (
      category.getStatus() != CatalogStatus.ACTIVE
    ) throw new ConflictException("图书分类已停用");
  }

  private void startPage(Integer page, Integer size) {
    PageHelper.startPage(
      page == null || page < 1 ? 1 : page,
      size == null || size < 1 ? 10 : Math.min(size, 100)
    );
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String normalizeNullable(String value) {
    return blank(value) ? null : value.trim();
  }

  private boolean isIdOnlyQuery(BookQueryRequest query) {
    return (
      query.getId() != null &&
      query.getCategoryId() == null &&
      query.getKeyword() == null &&
      query.getTitle() == null &&
      query.getSubtitle() == null &&
      query.getAuthor() == null &&
      query.getIsbn() == null &&
      query.getPublisher() == null &&
      query.getPublishedDate() == null &&
      query.getEdition() == null &&
      query.getLanguage() == null &&
      query.getDescription() == null &&
      query.getCoverObjectKey() == null &&
      query.getStatus() == null &&
      query.getVersion() == null &&
      query.getCreatedAt() == null &&
      query.getUpdatedAt() == null &&
      query.getAvailableOnly() == null
    );
  }
}
