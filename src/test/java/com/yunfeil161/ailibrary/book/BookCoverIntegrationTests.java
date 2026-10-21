package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.book.catalog.dto.*;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.catalog.service.BookService;
import com.yunfeil161.ailibrary.book.cover.service.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 使用真实 Mapper 和事务验证封面绑定、回滚补偿、权限与HTTP响应。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:cover_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
class BookCoverIntegrationTests {

  @Autowired
  BookCoverService covers;

  @Autowired
  BookService books;

  @Autowired
  BookMapper mapper;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MockMvc mvc;

  @MockitoBean
  CoverStorage storage;

  @MockitoBean
  CacheClient cache;

  Long id;
  Long categoryId;

  @BeforeEach
  void setup() {
    String code = "COVER-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      code,
      code
    );
    categoryId = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      code
    );
    jdbc.update(
      "insert into book(category_id,title,author,status,version) values(?,?,'作者','ACTIVE',0)",
      categoryId,
      code
    );
    id = jdbc.queryForObject(
      "select id from book where title=?",
      Long.class,
      code
    );
    login("LIBRARIAN", MemberType.EMPLOYEE);
    when(storage.signedUrl(anyString())).thenReturn(
      "https://example.invalid/signed-cover"
    );
  }

  @AfterEach
  void cleanup() {
    LoginUserContext.clear();
    jdbc.update("delete from book where id=?", id);
    jdbc.update("delete from book_category where id=?", categoryId);
  }

  private void login(String role, MemberType type) {
    Long roleId = jdbc.queryForObject(
      "select id from role where code=?",
      Long.class,
      role
    );
    LoginUserContext.set(
      new LoginSession(
        "cover-test",
        1L,
        roleId,
        type,
        role,
        "cover@test.local",
        "封面测试"
      )
    );
  }

  private MockMultipartFile image() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(
      new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
      "png",
      bytes
    );
    return new MockMultipartFile(
      "file",
      "cover.png",
      "image/png",
      bytes.toByteArray()
    );
  }

  @Test
  void uploadReplaceDeleteAndQueryUseSameBookVersion() throws Exception {
    var first = covers.save(id, 0L, image(), true);
    assertTrue(first.coverObjectKey().startsWith("library/covers/" + id + "/"));
    assertEquals(1L, first.version());
    assertEquals(first.coverUrl(), mapper.findDetailById(id).getCoverUrl());
    assertThrows(ConflictException.class, () ->
      covers.save(id, 1L, image(), true)
    );
    assertThrows(ConflictException.class, () ->
      covers.save(id, 0L, image(), false)
    );
    var second = covers.save(id, 1L, image(), false);
    assertNotEquals(first.coverObjectKey(), second.coverObjectKey());
    verify(storage).delete(first.coverObjectKey());
    assertNull(covers.delete(id, 2L).coverObjectKey());
    verify(storage).delete(second.coverObjectKey());
    assertNull(mapper.findDetailById(id).getCoverUrl());
    assertThrows(NotFoundException.class, () -> covers.signedUrl(id));
    assertEquals(3L, covers.delete(id, 3L).version());
    verify(cache, times(3)).deleteRequired("book:catalog:id:" + id);
  }

  @Test
  void invalidContentMimeEmptyAndOversizeAreRejectedBeforeOss() {
    assertThrows(WrongFormatException.class, () ->
      covers.save(
        id,
        0L,
        new MockMultipartFile(
          "file",
          "fake.png",
          "image/png",
          "hello".getBytes()
        ),
        true
      )
    );
    assertThrows(WrongFormatException.class, () ->
      covers.save(id, 0L, new MockMultipartFile("file", new byte[0]), true)
    );
    assertThrows(WrongFormatException.class, () ->
      covers.save(
        id,
        0L,
        new MockMultipartFile(
          "file",
          "large.png",
          "image/png",
          new byte[5 * 1024 * 1024 + 1]
        ),
        true
      )
    );
    assertThrows(WrongFormatException.class, () -> {
      var valid = image();
      covers.save(
        id,
        0L,
        new MockMultipartFile("file", "x.txt", "text/plain", valid.getBytes()),
        true
      );
    });
    verifyNoInteractions(storage);
  }

  @Test
  void cacheDeletionFailureRollsBackBindingAndDeletesNewObject()
    throws Exception {
    doThrow(new ServiceUnavailableException("测试缓存不可用"))
      .when(cache)
      .deleteRequired(anyString());
    assertThrows(ServiceUnavailableException.class, () ->
      covers.save(id, 0L, image(), true)
    );
    assertNull(mapper.findById(id).getCoverObjectKey());
    assertEquals(0L, mapper.findById(id).getVersion());
    verify(storage).delete(startsWith("library/covers/" + id + "/"));
  }

  @Test
  void uploadFailureDoesNotModifyBook() throws Exception {
    doThrow(new ServiceUnavailableException("测试OSS失败"))
      .when(storage)
      .upload(anyString(), any(), anyString());
    assertThrows(ServiceUnavailableException.class, () ->
      covers.save(id, 0L, image(), true)
    );
    assertEquals(0L, mapper.findById(id).getVersion());
    verify(storage).delete(anyString());
    verifyNoInteractions(cache);
  }

  @Test
  void readerCannotWriteAndPublicReaderCanQuery() throws Exception {
    login("READER", MemberType.READER);
    assertThrows(ForbiddenException.class, () ->
      covers.save(id, 0L, image(), true)
    );
    assertThrows(ForbiddenException.class, () -> covers.delete(id, 0L));
    LoginUserContext.clear();
    assertEquals(id, covers.find(id).bookId());
    assertThrows(UnauthorizedException.class, () ->
      covers.save(id, 0L, image(), true)
    );
  }

  @Test
  void genericBookRequestsCannotBindSomeoneElsesCover() {
    BookCreateRequest create = new BookCreateRequest();
    create.setCategoryId(categoryId);
    create.setTitle("非法封面绑定");
    create.setAuthor("作者");
    create.setCoverObjectKey("library/covers/999/stolen.png");
    assertThrows(WrongFormatException.class, () -> books.createBook(create));
    BookUpdateRequest update = new BookUpdateRequest();
    update.setExpectedVersion(0L);
    update.setCoverObjectKey("library/covers/999/stolen.png");
    assertThrows(WrongFormatException.class, () ->
      books.updateBook(id, update)
    );
  }

  @Test
  void httpCoverRoutesAndMultipartValidation() throws Exception {
    mvc
      .perform(
        multipart("/admin/books/{id}/cover", id)
          .file(image())
          .param("expectedVersion", "0")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.version").value(1));
    mvc
      .perform(get("/books/{id}/cover", id))
      .andExpect(status().isOk())
      .andExpect(
        jsonPath("$.data.coverUrl").value("/books/" + id + "/cover/content")
      );
    mvc
      .perform(get("/books/{id}/cover/content", id))
      .andExpect(status().isFound())
      .andExpect(header().string("Cache-Control", "no-store"));
    mvc
      .perform(
        multipart("/admin/books/{id}/cover", id).param("expectedVersion", "1")
      )
      .andExpect(status().isBadRequest());
    mvc.perform(get("/books/999999999/cover")).andExpect(status().isNotFound());
    mvc.perform(get("/books/0/cover")).andExpect(status().isBadRequest());
  }
}
