package com.yunfeil161.ailibrary.book.copy.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.book.catalog.entity.Book;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyCreateRequest;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyUpdateRequest;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper;
import com.yunfeil161.ailibrary.book.copy.service.BookCopyService;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCopyServiceImpl implements BookCopyService {

  private final BookCopyMapper copyMapper;
  private final BookMapper bookMapper;
  private final CacheClient cacheClient;
  private final PermissionAccess permissionAccess;

  @Override
  @DistributedLock(
    keys = "@bookCopyResourceLockKeys.query(#query)",
    mode = LockMode.READ
  )
  public PageResult<BookCopy> getBookCopies(BookCopyQueryRequest query) {
    // 副本包含馆藏位置和状态，仅允许获得副本查询权限的角色访问。
    permissionAccess.require(PermissionCodes.BOOK_COPY_QUERY);
    if (query == null) query = new BookCopyQueryRequest();
    if (isIdOnlyQuery(query)) {
      BookCopy copy = cacheClient.queryWithPassThrough(
        BookCacheKeys.COPY_BY_ID,
        query.getId(),
        BookCopy.class,
        copyMapper::findById,
        BookCacheKeys.ID_TTL_MINUTES,
        TimeUnit.MINUTES
      );
      if (copy == null) throw new NotFoundException("未找到图书副本");
      return new PageResult<>(1L, List.of(copy));
    }
    if (query.getBookId() != null) requireBook(query.getBookId());
    startPage(query.getPage(), query.getPageSize());
    List<BookCopy> rows = copyMapper.find(query);
    if (query.getId() != null && rows.isEmpty()) throw new NotFoundException(
      "未找到图书副本"
    );
    PageInfo<BookCopy> info = new PageInfo<>(rows);
    return new PageResult<>(info.getTotal(), info.getList());
  }

  @Override
  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + #bookId",
      "'lock:resource:book-copy:create'",
    }
  )
  @Audited(resource = AuditResource.BOOK_COPY, id = "")
  public BookCopy createBookCopy(Long bookId, BookCopyCreateRequest request) {
    // 权限检查放在缓存和数据库操作之前，避免任何旁路访问。
    permissionAccess.require(PermissionCodes.BOOK_COPY_CREATE);
    Book book = requireBook(bookId);
    if (book.getStatus() != CatalogStatus.ACTIVE) throw new ConflictException(
      "图书已停用，不能新增副本"
    );
    if (
      request == null ||
      blank(request.getBarcode()) ||
      blank(request.getLocation())
    ) {
      throw new WrongFormatException("馆藏条码和馆藏位置不能为空");
    }
    if (
      request.getPrice() != null && request.getPrice().signum() < 0
    ) throw new WrongFormatException("采购价格不能小于0");
    String barcode = request.getBarcode().trim();
    if (
      copyMapper.countByBarcode(barcode, null) > 0
    ) throw new ConflictException("馆藏条码已存在");
    BookCopy copy = new BookCopy();
    copy.setBookId(bookId);
    copy.setBarcode(barcode);
    copy.setCallNumber(normalizeNullable(request.getCallNumber()));
    copy.setLocation(request.getLocation().trim());
    copy.setStatus(BookCopyStatus.AVAILABLE);
    copy.setAcquiredAt(request.getAcquiredAt());
    copy.setPrice(request.getPrice());
    copy.setConditionNote(request.getConditionNote());
    copy.setVersion(0L);
    if (
      copyMapper.insert(copy) != 1 || copy.getId() == null
    ) throw new IllegalStateException("新增图书副本失败");
    evictCopyAndBook(copy.getId(), copy.getBookId());
    return copyMapper.findById(copy.getId());
  }

  @Override
  @Transactional
  @DistributedLock(keys = "@bookCopyResourceLockKeys.byId(#id)")
  @Audited(resource = AuditResource.BOOK_COPY, id = "#id")
  public BookCopy updateBookCopy(Long id, BookCopyUpdateRequest request) {
    // 副本状态维护需要独立的修改权限。
    permissionAccess.require(PermissionCodes.BOOK_COPY_UPDATE);
    if (
      id == null ||
      request == null ||
      request.getExpectedVersion() == null ||
      request.getExpectedVersion() < 0
    ) {
      throw new WrongFormatException("副本ID和有效的expectedVersion不能为空");
    }
    BookCopy existing = requireCopy(id);
    if (request.getBarcode() != null) {
      if (blank(request.getBarcode())) throw new WrongFormatException(
        "馆藏条码不能为空"
      );
      request.setBarcode(request.getBarcode().trim());
      if (
        copyMapper.countByBarcode(request.getBarcode(), id) > 0
      ) throw new ConflictException("馆藏条码已存在");
    }
    if (
      request.getLocation() != null && blank(request.getLocation())
    ) throw new WrongFormatException("馆藏位置不能为空");
    if (
      request.isPriceProvided() &&
      request.getPrice() != null &&
      request.getPrice().signum() < 0
    ) {
      throw new WrongFormatException("采购价格不能小于0");
    }
    if (request.getStatus() != null) validateTransition(
      existing.getStatus(),
      request.getStatus()
    );
    int rows = copyMapper.update(id, request);
    if (rows == 0) {
      if (copyMapper.findById(id) == null) throw new NotFoundException(
        "图书副本已被删除"
      );
      throw new ConflictException("图书副本版本已过期，请刷新后重试");
    }
    if (rows != 1) throw new IllegalStateException(
      "图书副本更新影响了多条记录"
    );
    evictCopyAndBook(id, existing.getBookId());
    return copyMapper.findById(id);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "@bookCopyResourceLockKeys.byId(#id)")
  @Audited(resource = AuditResource.BOOK_COPY, id = "#id")
  public String deleteBookCopy(Long id) {
    // 删除副本需要独立的删除权限。
    permissionAccess.require(PermissionCodes.BOOK_COPY_DELETE);
    BookCopy existing = requireCopy(id);
    if (
      existing.getStatus() == BookCopyStatus.BORROWED ||
      existing.getStatus() == BookCopyStatus.RESERVED
    ) {
      throw new ConflictException("已借出或已预留的副本不能删除");
    }
    if (existing.getStatus() == BookCopyStatus.DISCARDED) {
      throw new ConflictException("图书副本已经下架");
    }
    if (copyMapper.discard(id, existing.getVersion()) != 1) {
      throw new ConflictException("图书副本状态或版本已变化，请刷新后重试");
    }
    evictCopyAndBook(id, existing.getBookId());
    return existing.getBarcode();
  }

  private void validateTransition(BookCopyStatus from, BookCopyStatus to) {
    if (from == to) return;
    Set<BookCopyStatus> allowed = switch (from) {
      case AVAILABLE -> Set.of(
        BookCopyStatus.MAINTENANCE,
        BookCopyStatus.LOST,
        BookCopyStatus.DISCARDED
      );
      case MAINTENANCE -> Set.of(
        BookCopyStatus.AVAILABLE,
        BookCopyStatus.LOST,
        BookCopyStatus.DISCARDED
      );
      case LOST -> Set.of(BookCopyStatus.AVAILABLE, BookCopyStatus.DISCARDED);
      case BORROWED, RESERVED, DISCARDED -> Set.of();
    };
    if (!allowed.contains(to)) throw new ConflictException(
      "不允许的副本状态转换：" + from + " -> " + to
    );
  }

  private Book requireBook(Long id) {
    if (id == null) throw new WrongFormatException("图书ID不能为空");
    Book book = bookMapper.findById(id);
    if (book == null) throw new NotFoundException("未找到图书");
    return book;
  }

  private BookCopy requireCopy(Long id) {
    if (id == null) throw new WrongFormatException("副本ID不能为空");
    BookCopy copy = copyMapper.findById(id);
    if (copy == null) throw new NotFoundException("未找到图书副本");
    return copy;
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

  private void evictCopyAndBook(Long copyId, Long bookId) {
    cacheClient.deleteRequired(BookCacheKeys.COPY_BY_ID + copyId);
    cacheClient.deleteRequired(BookCacheKeys.BOOK_BY_ID + bookId);
  }

  private boolean isIdOnlyQuery(BookCopyQueryRequest query) {
    return (
      query.getId() != null &&
      query.getBookId() == null &&
      query.getBarcode() == null &&
      query.getCallNumber() == null &&
      query.getLocation() == null &&
      query.getStatus() == null &&
      query.getAcquiredAt() == null &&
      query.getPrice() == null &&
      query.getConditionNote() == null &&
      query.getVersion() == null &&
      query.getCreatedAt() == null &&
      query.getUpdatedAt() == null
    );
  }
}
