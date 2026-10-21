package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.service.BookCopyService;
import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 验证图书模块的角色授权、HTTP状态以及缓存命中时的权限边界。 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc(addFilters = false)
class BookPermissionIntegrationTests {

  @Autowired
  PermissionQueryService permissions;

  @Autowired
  BookCopyService copyService;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MockMvc mvc;

  private static final List<String> BOOK_PERMISSIONS = List.of(
    PermissionCodes.BOOK_CATEGORY_QUERY,
    PermissionCodes.BOOK_CATEGORY_CREATE,
    PermissionCodes.BOOK_CATEGORY_UPDATE,
    PermissionCodes.BOOK_CATEGORY_DELETE,
    PermissionCodes.BOOK_QUERY,
    PermissionCodes.BOOK_CREATE,
    PermissionCodes.BOOK_UPDATE,
    PermissionCodes.BOOK_DELETE,
    PermissionCodes.BOOK_COPY_QUERY,
    PermissionCodes.BOOK_COPY_CREATE,
    PermissionCodes.BOOK_COPY_UPDATE,
    PermissionCodes.BOOK_COPY_DELETE
  );

  @AfterEach
  void clearLoginContext() {
    LoginUserContext.clear();
  }

  @Test
  void defaultRolePermissionMatrixIsCorrect() {
    Long reader = role("READER");
    Long librarian = role("LIBRARIAN");
    Long admin = role("ADMIN");
    Long ai = role("AI_LIBRARIAN");

    // 馆员和管理员拥有12项图书权限，且每项范围均为NONE。
    for (String code : BOOK_PERMISSIONS) {
      assertTrue(permissions.hasPermission(librarian, code), code);
      assertTrue(permissions.hasPermission(admin, code), code);
      assertEquals(
        DataScope.NONE,
        permissions.findDataScope(librarian, code).orElseThrow()
      );
    }

    // 公开目录无需 AI 成员授权；退役角色不能再拥有任何图书操作权限。
    assertFalse(permissions.hasPermission(ai, PermissionCodes.BOOK_QUERY));
    for (Long roleId : List.of(reader)) {
      assertTrue(
        permissions.hasPermission(roleId, PermissionCodes.BOOK_CATEGORY_QUERY)
      );
      assertTrue(permissions.hasPermission(roleId, PermissionCodes.BOOK_QUERY));
      assertFalse(
        permissions.hasPermission(roleId, PermissionCodes.BOOK_CREATE)
      );
      assertFalse(
        permissions.hasPermission(roleId, PermissionCodes.BOOK_COPY_QUERY)
      );
    }
  }

  @Test
  void publicCatalogWorksButAnonymousAndReaderCannotUseAdminEndpoints()
    throws Exception {
    LoginUserContext.clear();
    mvc.perform(get("/books")).andExpect(status().isOk());
    mvc.perform(get("/book-categories")).andExpect(status().isOk());
    mvc
      .perform(
        post("/admin/books")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value(401));

    login(role("READER"), 1);
    // 每类管理入口均验证；权限检查先于参数和资源校验。
    mvc
      .perform(
        post("/admin/book-categories")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        put("/admin/book-categories/1")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(delete("/admin/book-categories/1"))
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/admin/books")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        put("/admin/books/1")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
    mvc.perform(delete("/admin/books/1")).andExpect(status().isForbidden());
    mvc.perform(get("/admin/book-copies")).andExpect(status().isForbidden());
    mvc
      .perform(
        post("/admin/books/1/copies")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        put("/admin/book-copies/1")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(delete("/admin/book-copies/1"))
      .andExpect(status().isForbidden());

    // AI馆员的成员类型数值为4，也不能通过数值大小继承人类管理权限。
    login(role("AI_LIBRARIAN"), 4);
    mvc
      .perform(
        post("/admin/books")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void cachedCopyCannotBypassPermissionCheck() {
    // 测试内独立创建副本，避免依赖其他测试的事务数据。
    String suffix = Long.toHexString(System.nanoTime());
    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      "PC" + suffix,
      "权限分类" + suffix
    );
    Long categoryId = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      "PC" + suffix
    );
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) values(?,?,?,?,?,'ACTIVE',0)",
      categoryId,
      "权限图书" + suffix,
      "测试作者",
      "PCISBN" + suffix,
      "zh-CN"
    );
    Long bookId = jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      "PCISBN" + suffix
    );
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) values(?,?,?,'AVAILABLE',0)",
      bookId,
      "PCOPY" + suffix,
      "测试区"
    );
    Long copyId = jdbc.queryForObject(
      "select id from book_copy where barcode=?",
      Long.class,
      "PCOPY" + suffix
    );
    BookCopyQueryRequest query = new BookCopyQueryRequest();
    query.setId(copyId);

    // 馆员首次查询填充缓存。
    login(role("LIBRARIAN"), 2);
    assertEquals(
      copyId,
      copyService.getBookCopies(query).getRows().get(0).getId()
    );

    // 同一ID即使已经命中缓存，读者仍在读取缓存前被拒绝。
    login(role("READER"), 1);
    assertThrows(ForbiddenException.class, () ->
      copyService.getBookCopies(query)
    );
  }

  @Test
  void removingRolePermissionImmediatelyRevokesAccess() {
    Long librarian = role("LIBRARIAN");
    // 直接删除本事务中的关联；首次权限查询必须立即反映不存在的授权。
    jdbc.update(
      "delete from role_permission where role_id=? and permission_id=(select id from permission where code=?)",
      librarian,
      PermissionCodes.BOOK_COPY_QUERY
    );
    assertFalse(
      permissions.hasPermission(librarian, PermissionCodes.BOOK_COPY_QUERY)
    );

    login(librarian, 2);
    assertThrows(ForbiddenException.class, () ->
      copyService.getBookCopies(new BookCopyQueryRequest())
    );
  }

  private Long role(String code) {
    return jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
    );
  }

  private void login(Long roleId, int memberType) {
    LoginUserContext.set(
      new LoginSession(
        "permission-test",
        1L,
        roleId,
        com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(memberType),
        memberType == 3 ? "ADMIN" : memberType == 2 ? "LIBRARIAN"
          : memberType == 4 ? "AI_LIBRARIAN" : "READER",
        "test@example.com",
        "测试用户"
      )
    );
  }
}

