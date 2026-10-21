package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.book.catalog.dto.*;
import com.yunfeil161.ailibrary.book.catalog.entity.BookDetailVO;
import com.yunfeil161.ailibrary.book.catalog.service.BookService;
import com.yunfeil161.ailibrary.book.category.dto.*;
import com.yunfeil161.ailibrary.book.category.entity.BookCategory;
import com.yunfeil161.ailibrary.book.category.service.BookCategoryService;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.book.copy.dto.*;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import com.yunfeil161.ailibrary.book.copy.service.BookCopyService;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc(addFilters = false)
class BookBusinessIntegrationTests {

  @Autowired
  BookCategoryService categoryService;

  @Autowired
  BookService bookService;

  @Autowired
  BookCopyService copyService;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MockMvc mockMvc;

  /** 既有图书业务测试以馆员身份运行，保持原测试关注的业务规则不变。 */
  @BeforeEach
  void loginAsLibrarian() {
    Long roleId = jdbc.queryForObject(
      "select id from `role` where code='LIBRARIAN'",
      Long.class
    );
    LoginUserContext.set(
      new LoginSession(
        "book-test",
        1L,
        roleId,
        com.yunfeil161.ailibrary.common.enums.MemberType.EMPLOYEE,
        "LIBRARIAN",
        "librarian@test.local",
        "测试馆员"
      )
    );
  }

  /** ThreadLocal必须在每个测试后清除，避免测试线程复用导致身份串用。 */
  @AfterEach
  void clearLogin() {
    LoginUserContext.clear();
  }

  @Test
  void allHttpEndpointsAreReachable() throws Exception {
    String suffix = suffix();
    String categoryBody =
      "{\"code\":\"HTTP" + suffix + "\",\"name\":\"HTTP分类" + suffix + "\"}";
    String categoryResponse = mockMvc
      .perform(
        post("/admin/book-categories")
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryBody)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andReturn()
      .getResponse()
      .getContentAsString();
    Long categoryId = extractId(categoryResponse);
    mockMvc
      .perform(get("/book-categories").param("id", categoryId.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));

    String bookBody =
      "{\"categoryId\":" +
      categoryId +
      ",\"title\":\"HTTP图书\",\"author\":\"测试作者\",\"isbn\":\"HTTPISBN" +
      suffix +
      "\"}";
    String bookResponse = mockMvc
      .perform(
        post("/admin/books")
          .contentType(MediaType.APPLICATION_JSON)
          .content(bookBody)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.version").value(0))
      .andReturn()
      .getResponse()
      .getContentAsString();
    Long bookId = extractId(bookResponse);
    mockMvc
      .perform(get("/books").param("id", bookId.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rows[0].availableCopies").value(0))
      .andExpect(
        jsonPath("$.data.rows[0].category").value("HTTP分类" + suffix)
      );

    String copyBody =
      "{\"barcode\":\"HTTP-BC-" + suffix + "\",\"location\":\"A区\"}";
    String copyResponse = mockMvc
      .perform(
        post("/admin/books/{bookId}/copies", bookId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(copyBody)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    Long copyId = extractId(copyResponse);
    mockMvc
      .perform(
        get("/admin/book-copies")
          .param("id", copyId.toString())
          .param("bookId", bookId.toString())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
    mockMvc
      .perform(
        put("/admin/book-copies/{id}", copyId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"MAINTENANCE\",\"expectedVersion\":0}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.version").value(1));
    mockMvc
      .perform(delete("/admin/book-copies/{id}", copyId))
      .andExpect(status().isOk());
    mockMvc
      .perform(
        put("/admin/books/{id}", bookId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"DISABLED\",\"expectedVersion\":0}")
      )
      .andExpect(status().isOk());
    mockMvc
      .perform(delete("/admin/books/{id}", bookId))
      .andExpect(status().isOk());
    mockMvc
      .perform(
        put("/admin/book-categories/{id}", categoryId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"description\":\"已测试\",\"expectedVersion\":0}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.version").value(1));
    mockMvc
      .perform(delete("/admin/book-categories/{id}", categoryId))
      .andExpect(status().isOk());
  }

  @Test
  void categoryRulesAndOptimisticLockWork() {
    String suffix = suffix();
    BookCategory root = category("ROOT" + suffix, "根分类" + suffix, null);
    BookCategory child = category(
      "CHILD" + suffix,
      "子分类" + suffix,
      root.getId()
    );

    BookCategoryQueryRequest query = new BookCategoryQueryRequest();
    query.setId(root.getId());
    assertEquals(
      root.getId(),
      categoryService.getBookCategories(query).getRows().get(0).getId()
    );

    BookCategoryCreateRequest duplicate = new BookCategoryCreateRequest();
    duplicate.setCode(root.getCode());
    duplicate.setName("另一个分类");
    assertThrows(ConflictException.class, () ->
      categoryService.createBookCategory(duplicate)
    );
    duplicate.setCode("MISSING" + suffix);
    duplicate.setParentId(999999L);
    assertThrows(NotFoundException.class, () ->
      categoryService.createBookCategory(duplicate)
    );

    BookCategoryUpdateRequest update = new BookCategoryUpdateRequest();
    update.setName("新名称" + suffix);
    update.setExpectedVersion(0L);
    BookCategory updated = categoryService.updateBookCategory(
      root.getId(),
      update
    );
    assertEquals(1L, updated.getVersion());
    assertThrows(ConflictException.class, () ->
      categoryService.updateBookCategory(root.getId(), update)
    );

    BookCategoryUpdateRequest self = new BookCategoryUpdateRequest();
    self.setParentId(root.getId());
    self.setExpectedVersion(1L);
    assertThrows(ConflictException.class, () ->
      categoryService.updateBookCategory(root.getId(), self)
    );
    BookCategoryUpdateRequest descendant = new BookCategoryUpdateRequest();
    descendant.setParentId(child.getId());
    descendant.setExpectedVersion(1L);
    assertThrows(ConflictException.class, () ->
      categoryService.updateBookCategory(root.getId(), descendant)
    );
    assertThrows(ConflictException.class, () ->
      categoryService.deleteBookCategory(root.getId())
    );
    assertEquals(
      child.getName(),
      categoryService.deleteBookCategory(child.getId())
    );
  }

  @Test
  void bookQueriesInventoryAndCategoryRulesWork() {
    String suffix = suffix();
    BookCategory parentCategory = category(
      "BOOKROOT" + suffix,
      "书目父分类" + suffix,
      null
    );
    BookCategory category = category(
      "BOOK" + suffix,
      "书目子分类" + suffix,
      parentCategory.getId()
    );
    BookDetailVO book = book(
      category.getId(),
      "Java测试" + suffix,
      "ISBN" + suffix
    );
    copy(book.getId(), "A" + suffix);
    copy(book.getId(), "B" + suffix);

    BookQueryRequest query = new BookQueryRequest();
    query.setKeyword("Java测试");
    query.setAvailableOnly(true);
    BookDetailVO found = bookService
      .getBooks(query)
      .getRows()
      .stream()
      .filter(item -> item.getId().equals(book.getId()))
      .findFirst()
      .orElseThrow();
    assertEquals(
      parentCategory.getName() + "：" + category.getName(),
      found.getCategory()
    );
    assertEquals(2L, found.getTotalCopies());
    assertEquals(2L, found.getAvailableCopies());

    BookCreateRequest duplicate = createBookRequest(
      category.getId(),
      "重复",
      "ISBN" + suffix
    );
    assertThrows(ConflictException.class, () ->
      bookService.createBook(duplicate)
    );
    BookCreateRequest emptyIsbn1 = createBookRequest(
      category.getId(),
      "空ISBN一" + suffix,
      " "
    );
    BookCreateRequest emptyIsbn2 = createBookRequest(
      category.getId(),
      "空ISBN二" + suffix,
      ""
    );
    assertNull(bookService.createBook(emptyIsbn1).getIsbn());
    assertNull(bookService.createBook(emptyIsbn2).getIsbn());

    BookUpdateRequest update = new BookUpdateRequest();
    update.setTitle("更新后的书" + suffix);
    update.setExpectedVersion(0L);
    assertEquals(1L, bookService.updateBook(book.getId(), update).getVersion());
    assertThrows(ConflictException.class, () ->
      bookService.updateBook(book.getId(), update)
    );
    assertThrows(ConflictException.class, () ->
      bookService.deleteBook(book.getId())
    );

    BookCategory disabled = category("OFF" + suffix, "停用分类" + suffix, null);
    BookCategoryUpdateRequest disable = new BookCategoryUpdateRequest();
    disable.setStatus(CatalogStatus.DISABLED);
    disable.setExpectedVersion(0L);
    categoryService.updateBookCategory(disabled.getId(), disable);
    assertThrows(ConflictException.class, () ->
      bookService.createBook(
        createBookRequest(disabled.getId(), "不能新增", "OFF" + suffix)
      )
    );
  }

  @Test
  void copyValidationStateMachineAndDeleteRulesWork() {
    String suffix = suffix();
    BookCategory category = category(
      "COPY" + suffix,
      "副本分类" + suffix,
      null
    );
    BookDetailVO book = book(
      category.getId(),
      "副本测试" + suffix,
      "COPYISBN" + suffix
    );
    BookCopy copy = copy(book.getId(), "COPY" + suffix);

    BookCopyQueryRequest query = new BookCopyQueryRequest();
    query.setBookId(book.getId());
    query.setBarcode("COPY");
    assertTrue(
      copyService
        .getBookCopies(query)
        .getRows()
        .stream()
        .anyMatch(item -> item.getId().equals(copy.getId()))
    );
    assertThrows(ConflictException.class, () ->
      copyService.createBookCopy(
        book.getId(),
        copyRequest("COPY" + suffix, BigDecimal.ONE)
      )
    );
    assertThrows(WrongFormatException.class, () ->
      copyService.createBookCopy(
        book.getId(),
        copyRequest("NEG" + suffix, BigDecimal.valueOf(-1))
      )
    );

    BookCopyUpdateRequest maintenance = updateStatus(
      BookCopyStatus.MAINTENANCE,
      0L
    );
    assertEquals(
      BookCopyStatus.MAINTENANCE,
      copyService.updateBookCopy(copy.getId(), maintenance).getStatus()
    );
    BookCopyUpdateRequest available = updateStatus(
      BookCopyStatus.AVAILABLE,
      1L
    );
    assertEquals(
      BookCopyStatus.AVAILABLE,
      copyService.updateBookCopy(copy.getId(), available).getStatus()
    );
    assertThrows(ConflictException.class, () ->
      copyService.updateBookCopy(
        copy.getId(),
        updateStatus(BookCopyStatus.BORROWED, 2L)
      )
    );
    assertThrows(ConflictException.class, () ->
      copyService.updateBookCopy(
        copy.getId(),
        updateStatus(BookCopyStatus.MAINTENANCE, 0L)
      )
    );

    Long borrowedId = insertSpecialCopy(
      book.getId(),
      "BORROW" + suffix,
      "BORROWED"
    );
    assertThrows(ConflictException.class, () ->
      copyService.deleteBookCopy(borrowedId)
    );
    assertThrows(ConflictException.class, () ->
      copyService.updateBookCopy(
        borrowedId,
        updateStatus(BookCopyStatus.AVAILABLE, 0L)
      )
    );
    Long reservedId = insertSpecialCopy(
      book.getId(),
      "RESERVE" + suffix,
      "RESERVED"
    );
    assertThrows(ConflictException.class, () ->
      copyService.deleteBookCopy(reservedId)
    );
    Long discardedId = insertSpecialCopy(
      book.getId(),
      "DISCARD" + suffix,
      "DISCARDED"
    );
    assertThrows(ConflictException.class, () ->
      copyService.updateBookCopy(
        discardedId,
        updateStatus(BookCopyStatus.AVAILABLE, 0L)
      )
    );

    BookCopy removable = copy(book.getId(), "DEL" + suffix);
    assertEquals(
      removable.getBarcode(),
      copyService.deleteBookCopy(removable.getId())
    );
  }

  private BookCategory category(String code, String name, Long parentId) {
    BookCategoryCreateRequest request = new BookCategoryCreateRequest();
    request.setCode(code);
    request.setName(name);
    request.setParentId(parentId);
    return categoryService.createBookCategory(request);
  }

  private BookDetailVO book(Long categoryId, String title, String isbn) {
    return bookService.createBook(createBookRequest(categoryId, title, isbn));
  }

  private BookCreateRequest createBookRequest(
    Long categoryId,
    String title,
    String isbn
  ) {
    BookCreateRequest request = new BookCreateRequest();
    request.setCategoryId(categoryId);
    request.setTitle(title);
    request.setAuthor("测试作者");
    request.setIsbn(isbn);
    return request;
  }

  private BookCopy copy(Long bookId, String barcode) {
    return copyService.createBookCopy(
      bookId,
      copyRequest(barcode, BigDecimal.TEN)
    );
  }

  private BookCopyCreateRequest copyRequest(String barcode, BigDecimal price) {
    BookCopyCreateRequest request = new BookCopyCreateRequest();
    request.setBarcode(barcode);
    request.setLocation("A区");
    request.setPrice(price);
    return request;
  }

  private BookCopyUpdateRequest updateStatus(
    BookCopyStatus status,
    long version
  ) {
    BookCopyUpdateRequest request = new BookCopyUpdateRequest();
    request.setStatus(status);
    request.setExpectedVersion(version);
    return request;
  }

  private Long insertSpecialCopy(Long bookId, String barcode, String status) {
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) values (?,?,?,?,0)",
      bookId,
      barcode,
      "A区",
      status
    );
    return jdbc.queryForObject(
      "select id from book_copy where barcode=?",
      Long.class,
      barcode
    );
  }

  private String suffix() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private Long extractId(String json) {
    String marker = "\"id\":";
    int start = json.indexOf(marker) + marker.length();
    int end = start;
    while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
    return Long.valueOf(json.substring(start, end));
  }
}
