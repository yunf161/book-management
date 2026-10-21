package com.yunfeil161.ailibrary.operations;

import com.yunfeil161.ailibrary.book.catalog.dto.BookCreateRequest;
import com.yunfeil161.ailibrary.book.catalog.service.BookService;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyCreateRequest;
import com.yunfeil161.ailibrary.book.copy.service.BookCopyService;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

/** 每一行独立事务，复用原Service的校验、审计和缓存失效，不直接拼SQL写业务表。 */
@Service
@RequiredArgsConstructor
public class ImportRowWriter {

  private final JdbcTemplate jdbc;
  private final BookService books;
  private final BookCopyService copies;
  private final RedissonClient locks;
  private final CacheClient cache;

  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:book-category:' + #categoryId",
      "'lock:resource:book:create'",
      "#knownBookId == null ? null : 'lock:resource:book:' + #knownBookId",
    }
  )
  public Map<String, Object> write(
    Long categoryId,
    Map<String, String> row,
    Long knownBookId
  ) {
    var input = new BookCreateRequest();
    input.setCategoryId(categoryId);
    input.setTitle(row.get("title"));
    input.setAuthor(row.get("author"));
    input.setIsbn(blank(row.get("isbn")));
    input.setPublisher(blank(row.get("publisher")));
    input.setLanguage(blank(row.get("language")));
    input.setSubtitle(blank(row.get("subtitle")));
    input.setEdition(blank(row.get("edition")));
    input.setDescription(blank(row.get("description")));
    input.setCoverObjectKey(blank(row.get("coverObjectKey")));
    if (blank(row.get("tagIds")) != null) input.setTagIds(
      Arrays.stream(row.get("tagIds").split(","))
        .map(String::trim)
        .map(Long::valueOf)
        .toList()
    );
    if (blank(row.get("publishedDate")) != null) input.setPublishedDate(
      LocalDate.parse(row.get("publishedDate"))
    );
    // 先解析副本字段，负数/日期错误不能在新增书目后才被发现。
    var copy = new BookCopyCreateRequest();
    copy.setBarcode(blank(row.get("barcode")));
    copy.setLocation(blank(row.get("location")));
    copy.setCallNumber(blank(row.get("callNumber")));
    copy.setConditionNote(blank(row.get("conditionNote")));
    if (blank(row.get("acquiredAt")) != null) copy.setAcquiredAt(
      LocalDate.parse(row.get("acquiredAt"))
    );
    if (blank(row.get("price")) != null) {
      copy.setPrice(new BigDecimal(row.get("price")));
      if (copy.getPrice().signum() < 0) throw new WrongFormatException(
        "价格不能小于0"
      );
    }
    if (
      copy.getBarcode() != null && copy.getLocation() == null
    ) throw new WrongFormatException("有条码时馆藏位置必填");
    List<Map<String, Object>> existing =
      input.getIsbn() == null
        ? List.of()
        : jdbc.queryForList(
            "select id,category_id,title,author,status from book where isbn=?",
            input.getIsbn()
          );
    Long bookId;
    // 事务前只定位ID，完整校验在资源锁内；不使用定位期间新插入的未知书目。
    if (
      !existing.isEmpty() &&
      !Objects.equals(
        knownBookId,
        ((Number) existing.get(0).get("id")).longValue()
      )
    ) throw new ConflictException("书目在定位期间发生变化，请刷新后重试失败行");
    if (existing.isEmpty()) bookId = books.createBook(input).getId();
    else {
      var old = existing.get(0);
      if (
        !Objects.equals(old.get("title"), input.getTitle()) ||
        !Objects.equals(old.get("author"), input.getAuthor()) ||
        ((Number) old.get("category_id")).longValue() != categoryId ||
        !"ACTIVE".equals(old.get("status"))
      ) throw new ConflictException("ISBN已存在但书目字段不同或已停用");
      bookId = ((Number) old.get("id")).longValue();
    }
    // 新ID只有插入后才可知。此锁一直持有到本行事务完成，与原按ID读缓存使用相同资源键。
    var lock = locks
      .getReadWriteLock("lock:resource:book:" + bookId)
      .writeLock();
    try {
      if (!lock.tryLock(5, TimeUnit.SECONDS)) throw new ConflictException(
        "书目正在处理中，请稍后重试"
      );
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ConflictException("导入等待已中断");
    }
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (lock.isHeldByCurrentThread()) lock.unlock();
        }
      }
    );
    cache.deleteRequired(BookCacheKeys.BOOK_BY_ID + bookId);
    Long copyId =
      copy.getBarcode() == null
        ? null
        : copies.createBookCopy(bookId, copy).getId();
    var result = new LinkedHashMap<String, Object>();
    result.put("bookId", bookId);
    result.put("copyId", copyId);
    return result;
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
