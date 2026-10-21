package com.yunfeil161.ailibrary.book.copy.service.impl;

import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 解析副本及其所属书目的资源锁键。
 *
 * <p>副本变化会改变书目的库存统计，因此按 ID 读取、修改和删除副本时，同时锁定
 * 副本和所属书目。这样书目缓存、副本缓存与数据库更新使用同一组资源边界。</p>
 */
@Component("bookCopyResourceLockKeys")
@RequiredArgsConstructor
public class BookCopyResourceLockKeys {

  private final BookCopyMapper copies;

  /** 按副本 ID 解析副本与所属书目的稳定锁键。 */
  public List<String> byId(Long copyId) {
    List<String> keys = new ArrayList<>();
    keys.add(copy(copyId));
    BookCopy value = copyId == null ? null : copies.findById(copyId);
    if (value != null) keys.add(book(value.getBookId()));
    return keys;
  }

  /** ID 单项查询与写操作复用 byId，其余组合查询使用共享查询锁。 */
  public List<String> query(BookCopyQueryRequest query) {
    if (query != null && query.getId() != null) return byId(query.getId());
    return List.of("lock:resource:book-copy:query");
  }

  private String book(Long id) {
    return "lock:resource:book:" + id;
  }

  private String copy(Long id) {
    return "lock:resource:book-copy:" + id;
  }
}
