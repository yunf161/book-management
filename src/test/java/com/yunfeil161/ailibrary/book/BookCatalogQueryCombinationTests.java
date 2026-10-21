package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.book.catalog.entity.BookDetailVO;
import com.yunfeil161.ailibrary.book.catalog.service.BookService;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryQueryRequest;
import com.yunfeil161.ailibrary.book.category.entity.BookCategory;
import com.yunfeil161.ailibrary.book.category.service.BookCategoryService;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import com.yunfeil161.ailibrary.book.copy.service.BookCopyService;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 验证分类、书目和副本聚合查询的单字段、组合字段及分页行为。 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc(addFilters = false)
class BookCatalogQueryCombinationTests {

  @Autowired
  BookCategoryService categories;

  @Autowired
  BookService books;

  @Autowired
  BookCopyService copies;

  @Autowired
  JdbcTemplate jdbc;

  private String suffix;
  private Long rootId;
  private Long childId;
  private Long bookId;
  private Long unavailableBookId;
  private Long copyId;
  private final LocalDateTime fixedTime = LocalDateTime.of(
    2026,
    3,
    4,
    5,
    6,
    7,
    123_000_000
  );

  @BeforeEach
  void setUp() {
    suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    Long librarianRole = jdbc.queryForObject(
      "select id from `role` where code='LIBRARIAN'",
      Long.class
    );
    LoginUserContext.set(
      new LoginSession(
        "query-test",
        1L,
        librarianRole,
        com.yunfeil161.ailibrary.common.enums.MemberType.EMPLOYEE,
        "LIBRARIAN",
        "query@test.local",
        "查询测试馆员"
      )
    );

    jdbc.update(
      "insert into book_category(code,name,description,status,version) values(?,?,?,'ACTIVE',0)",
      "QR" + suffix,
      "技术根分类" + suffix,
      "根分类说明" + suffix
    );
    rootId = id("select id from book_category where code=?", "QR" + suffix);
    jdbc.update(
      "insert into book_category(code,name,parent_id,description,status,version) values(?,?,?,?, 'ACTIVE',0)",
      "QC" + suffix,
      "Java子分类" + suffix,
      rootId,
      "子分类说明" + suffix
    );
    childId = id("select id from book_category where code=?", "QC" + suffix);

    jdbc.update(
      """
      insert into book(category_id,title,subtitle,author,isbn,publisher,published_date,
                       edition,language,description,cover_object_key,status,version)
      values(?,?,?,?,?,?,?,?,?,?,?,?,0)
      """,
      childId,
      "并发Java" + suffix,
      "实践卷" + suffix,
      "张作者" + suffix,
      "ISBN-A-" + suffix,
      "测试出版社" + suffix,
      LocalDate.of(2026, 1, 2),
      "第2版",
      "zh-CN",
      "并发编程说明" + suffix,
      "covers/a-" + suffix,
      "ACTIVE"
    );
    bookId = id("select id from book where isbn=?", "ISBN-A-" + suffix);
    jdbc.update(
      """
      insert into book(category_id,title,subtitle,author,isbn,publisher,published_date,
                       edition,language,description,status,version)
      values(?,?,?,?,?,?,?,?,?,?, 'DISABLED',0)
      """,
      childId,
      "数据库原理" + suffix,
      "理论卷",
      "李作者",
      "ISBN-B-" + suffix,
      "另一出版社",
      LocalDate.of(2025, 5, 6),
      "第1版",
      "en-US",
      "没有可借副本"
    );
    unavailableBookId = id(
      "select id from book where isbn=?",
      "ISBN-B-" + suffix
    );

    jdbc.update(
      """
      insert into book_copy(book_id,barcode,call_number,location,status,acquired_at,price,
                            condition_note,version)
      values(?,?,?,?,?,?,?,?,0)
      """,
      bookId,
      "BC-A-" + suffix,
      "TP312-" + suffix,
      "三层A区" + suffix,
      "AVAILABLE",
      LocalDate.of(2026, 2, 3),
      new BigDecimal("88.50"),
      "书况完好" + suffix
    );
    copyId = id("select id from book_copy where barcode=?", "BC-A-" + suffix);
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) values(?,?,?,'DISCARDED',0)",
      bookId,
      "BC-D-" + suffix,
      "下架区"
    );
    // 使用毫秒精度固定值，明确验证时间字段映射和精确查询。
    jdbc.update(
      "update book_category set created_at=?,updated_at=? where id in (?,?)",
      fixedTime,
      fixedTime,
      rootId,
      childId
    );
    jdbc.update(
      "update book set created_at=?,updated_at=? where id in (?,?)",
      fixedTime,
      fixedTime,
      bookId,
      unavailableBookId
    );
    jdbc.update(
      "update book_copy set created_at=?,updated_at=? where id=?",
      fixedTime,
      fixedTime,
      copyId
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  @Test
  void categoryQuerySupportsEveryBusinessFieldAndCombinedPaging() {
    BookCategory child = oneCategory(queryCategory("id", childId));
    assertEquals(childId, child.getId());
    assertEquals(
      childId,
      oneCategory(queryCategory("code", "QC" + suffix)).getId()
    );
    assertEquals(childId, oneCategory(queryCategory("name", "Java子")).getId());
    assertEquals(
      childId,
      oneCategory(queryCategory("parentId", rootId)).getId()
    );
    assertEquals(
      childId,
      oneCategory(queryCategory("description", "子分类说明")).getId()
    );
    assertEquals(
      childId,
      oneCategory(queryCategory("status", CatalogStatus.ACTIVE)).getId()
    );
    assertEquals(childId, oneCategory(queryCategory("version", 0L)).getId());

    BookCategoryQueryRequest timestamps = new BookCategoryQueryRequest();
    timestamps.setId(childId);
    timestamps.setCreatedAt(fixedTime);
    timestamps.setUpdatedAt(fixedTime);
    assertEquals(1, categories.getBookCategories(timestamps).getTotal());

    BookCategoryQueryRequest rootOnly = new BookCategoryQueryRequest();
    rootOnly.setRootOnly(true);
    assertTrue(
      categories
        .getBookCategories(rootOnly)
        .getRows()
        .stream()
        .anyMatch(row -> row.getId().equals(rootId))
    );
    assertFalse(
      categories
        .getBookCategories(rootOnly)
        .getRows()
        .stream()
        .anyMatch(row -> row.getId().equals(childId))
    );

    BookCategoryQueryRequest combined = new BookCategoryQueryRequest();
    combined.setCode("QC");
    combined.setName("Java");
    combined.setParentId(rootId);
    combined.setDescription("说明");
    combined.setStatus(CatalogStatus.ACTIVE);
    combined.setPage(1);
    combined.setPageSize(1);
    assertEquals(childId, oneCategory(combined).getId());
  }

  @Test
  void bookQuerySupportsEveryFieldKeywordVariantsAndInventoryFilters() {
    oneBook(queryBook("id", bookId));
    assertEquals(bookId, oneBook(queryBook("categoryId", childId)).getId());
    assertEquals(bookId, oneBook(queryBook("title", "并发Java")).getId());
    assertEquals(bookId, oneBook(queryBook("subtitle", "实践卷")).getId());
    assertEquals(bookId, oneBook(queryBook("author", "张作者")).getId());
    assertEquals(bookId, oneBook(queryBook("isbn", "ISBN-A")).getId());
    assertEquals(bookId, oneBook(queryBook("publisher", "测试出版社")).getId());
    assertEquals(
      bookId,
      oneBook(queryBook("publishedDate", LocalDate.of(2026, 1, 2))).getId()
    );
    assertEquals(bookId, oneBook(queryBook("edition", "第2版")).getId());
    assertEquals(bookId, oneBook(queryBook("language", "zh-CN")).getId());
    assertEquals(bookId, oneBook(queryBook("description", "并发编程")).getId());
    assertEquals(
      bookId,
      oneBook(queryBook("coverObjectKey", "covers/a-" + suffix)).getId()
    );
    assertEquals(
      bookId,
      oneBook(queryBook("status", CatalogStatus.ACTIVE)).getId()
    );
    assertEquals(bookId, oneBook(queryBook("version", 0L)).getId());

    // keyword分别命中标题、副标题、作者、ISBN和出版社。
    for (String keyword : new String[] {
      "并发Java",
      "实践卷",
      "张作者",
      "ISBN-A",
      "测试出版社",
    }) {
      assertEquals(
        bookId,
        oneBook(queryBook("keyword", keyword)).getId(),
        keyword
      );
    }

    BookQueryRequest timestamps = new BookQueryRequest();
    timestamps.setId(bookId);
    timestamps.setCreatedAt(fixedTime);
    timestamps.setUpdatedAt(fixedTime);
    assertEquals(1, books.getBooks(timestamps).getTotal());

    BookQueryRequest available = new BookQueryRequest();
    available.setId(bookId);
    available.setAvailableOnly(true);
    BookDetailVO availableBook = oneBook(available);
    assertEquals(1L, availableBook.getTotalCopies());
    assertEquals(1L, availableBook.getAvailableCopies());
    assertEquals(
      "技术根分类" + suffix + "：Java子分类" + suffix,
      availableBook.getCategory()
    );

    BookQueryRequest unavailable = new BookQueryRequest();
    unavailable.setId(unavailableBookId);
    unavailable.setAvailableOnly(true);
    assertThrows(NotFoundException.class, () -> books.getBooks(unavailable));

    BookQueryRequest combined = new BookQueryRequest();
    combined.setCategoryId(childId);
    combined.setTitle("并发");
    combined.setAuthor("张");
    combined.setPublisher("测试");
    combined.setStatus(CatalogStatus.ACTIVE);
    combined.setAvailableOnly(true);
    combined.setPage(1);
    combined.setPageSize(1);
    assertEquals(bookId, oneBook(combined).getId());
  }

  @Test
  void copyQuerySupportsEveryFieldAndCombinedPaging() {
    oneCopy(queryCopy("id", copyId));
    assertEquals(copyId, oneCopy(queryCopy("bookId", bookId)).getId());
    assertEquals(copyId, oneCopy(queryCopy("barcode", "BC-A")).getId());
    assertEquals(copyId, oneCopy(queryCopy("callNumber", "TP312")).getId());
    assertEquals(copyId, oneCopy(queryCopy("location", "三层A区")).getId());
    assertEquals(
      copyId,
      oneCopy(queryCopy("status", BookCopyStatus.AVAILABLE)).getId()
    );
    assertEquals(
      copyId,
      oneCopy(queryCopy("acquiredAt", LocalDate.of(2026, 2, 3))).getId()
    );
    assertEquals(
      copyId,
      oneCopy(queryCopy("price", new BigDecimal("88.50"))).getId()
    );
    assertEquals(
      copyId,
      oneCopy(queryCopy("conditionNote", "书况完好")).getId()
    );
    assertEquals(copyId, oneCopy(queryCopy("version", 0L)).getId());

    BookCopyQueryRequest timestamps = new BookCopyQueryRequest();
    timestamps.setId(copyId);
    timestamps.setCreatedAt(fixedTime);
    timestamps.setUpdatedAt(fixedTime);
    assertEquals(1, copies.getBookCopies(timestamps).getTotal());

    BookCopyQueryRequest combined = new BookCopyQueryRequest();
    combined.setBookId(bookId);
    combined.setBarcode("BC-A");
    combined.setCallNumber("TP312");
    combined.setLocation("三层");
    combined.setStatus(BookCopyStatus.AVAILABLE);
    combined.setAcquiredAt(LocalDate.of(2026, 2, 3));
    combined.setPrice(new BigDecimal("88.50"));
    combined.setConditionNote("完好");
    combined.setPage(1);
    combined.setPageSize(1);
    assertEquals(copyId, oneCopy(combined).getId());
  }

  private BookCategoryQueryRequest queryCategory(String field, Object value) {
    BookCategoryQueryRequest q = new BookCategoryQueryRequest();
    switch (field) {
      case "id" -> q.setId((Long) value);
      case "code" -> q.setCode((String) value);
      case "name" -> q.setName((String) value);
      case "parentId" -> q.setParentId((Long) value);
      case "description" -> q.setDescription((String) value);
      case "status" -> q.setStatus((CatalogStatus) value);
      case "version" -> q.setVersion((Long) value);
      default -> fail("未知分类字段");
    }
    return q;
  }

  private BookQueryRequest queryBook(String field, Object value) {
    BookQueryRequest q = new BookQueryRequest();
    switch (field) {
      case "id" -> q.setId((Long) value);
      case "categoryId" -> q.setCategoryId((Long) value);
      case "keyword" -> q.setKeyword((String) value);
      case "title" -> q.setTitle((String) value);
      case "subtitle" -> q.setSubtitle((String) value);
      case "author" -> q.setAuthor((String) value);
      case "isbn" -> q.setIsbn((String) value);
      case "publisher" -> q.setPublisher((String) value);
      case "publishedDate" -> q.setPublishedDate((LocalDate) value);
      case "edition" -> q.setEdition((String) value);
      case "language" -> q.setLanguage((String) value);
      case "description" -> q.setDescription((String) value);
      case "coverObjectKey" -> q.setCoverObjectKey((String) value);
      case "status" -> q.setStatus((CatalogStatus) value);
      case "version" -> q.setVersion((Long) value);
      default -> fail("未知书目字段");
    }
    return q;
  }

  private BookCopyQueryRequest queryCopy(String field, Object value) {
    BookCopyQueryRequest q = new BookCopyQueryRequest();
    switch (field) {
      case "id" -> q.setId((Long) value);
      case "bookId" -> q.setBookId((Long) value);
      case "barcode" -> q.setBarcode((String) value);
      case "callNumber" -> q.setCallNumber((String) value);
      case "location" -> q.setLocation((String) value);
      case "status" -> q.setStatus((BookCopyStatus) value);
      case "acquiredAt" -> q.setAcquiredAt((LocalDate) value);
      case "price" -> q.setPrice((BigDecimal) value);
      case "conditionNote" -> q.setConditionNote((String) value);
      case "version" -> q.setVersion((Long) value);
      default -> fail("未知副本字段");
    }
    return q;
  }

  private BookCategory oneCategory(BookCategoryQueryRequest q) {
    return categories
      .getBookCategories(q)
      .getRows()
      .stream()
      .filter(row -> row.getId().equals(childId))
      .findFirst()
      .orElseThrow();
  }

  private BookDetailVO oneBook(BookQueryRequest q) {
    return books
      .getBooks(q)
      .getRows()
      .stream()
      .filter(row -> row.getId().equals(bookId))
      .findFirst()
      .orElseThrow();
  }

  private BookCopy oneCopy(BookCopyQueryRequest q) {
    return copies
      .getBookCopies(q)
      .getRows()
      .stream()
      .filter(row -> row.getId().equals(copyId))
      .findFirst()
      .orElseThrow();
  }

  private Long id(String sql, Object value) {
    return jdbc.queryForObject(sql, Long.class, value);
  }
}
