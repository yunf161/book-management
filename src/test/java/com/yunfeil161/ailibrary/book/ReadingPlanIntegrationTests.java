package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
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

/** 阅读计划读者端与管理端 HTTP 闭环测试。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ReadingPlanIntegrationTests {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JdbcTemplate jdbc;

  private Long readerId;
  private Long otherId;
  private Long adminId;
  private Long readerRole;
  private Long adminRole;
  private Long book1;
  private Long book2;

  @BeforeEach
  void setup() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    readerRole = role("READER");
    adminRole = role("ADMIN");
    readerId = member(
      1,
      readerRole,
      "读者" + suffix,
      "r" + suffix + "@t.local"
    );
    otherId = member(1, readerRole, "其他" + suffix, "o" + suffix + "@t.local");
    adminId = member(3, adminRole, "管理" + suffix, "a" + suffix + "@t.local");

    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      "RP" + suffix,
      "阅读" + suffix
    );
    Long categoryId = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      "RP" + suffix
    );
    book1 = book(categoryId, "阅读书A" + suffix, "RA" + suffix);
    book2 = book(categoryId, "阅读书B" + suffix, "RB" + suffix);
  }

  @AfterEach
  void clearContext() {
    LoginUserContext.clear();
  }

  /** 验证读者只能维护自己的计划，并跑通计划和条目的增删改查。 */
  @Test
  void readerHttpCrudClosedLoop() throws Exception {
    login(readerId, readerRole, 1);
    String plan = postJson(
      "/reading-plans",
      """
      {"memberId":%d,"name":"我的计划","targetBooks":2,"status":"DRAFT"}
      """.formatted(otherId)
    );
    assertEquals(readerId.longValue(), number(plan, "$.data.memberId"));

    long planVersion = currentPlanVersion(readerId);
    addMine(book1, 1, planVersion);
    planVersion = currentPlanVersion(readerId);
    String item = addMine(book2, 1, planVersion);
    long itemVersion = number(item, "$.data.version");

    mvc
      .perform(get("/reading-plans/items").param("bookId", book2.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));

    planVersion = currentPlanVersion(readerId);
    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book2)
          .contentType(MediaType.APPLICATION_JSON)
          .content(itemJson("READING", 30, itemVersion, planVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("READING"));

    long newItemVersion = currentItemVersion(readerId, book2);
    long newPlanVersion = currentPlanVersion(readerId);
    mvc
      .perform(
        delete("/reading-plans/items/{bookId}", book2)
          .param("expectedVersion", String.valueOf(newItemVersion))
          .param("expectedPlanVersion", String.valueOf(newPlanVersion))
      )
      .andExpect(status().isOk());

    newPlanVersion = currentPlanVersion(readerId);
    mvc
      .perform(
        put("/reading-plans")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"name":"我的新计划","targetBooks":3,"status":"ACTIVE","version":%d}
            """.formatted(newPlanVersion)
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("我的新计划"));

    newPlanVersion = currentPlanVersion(readerId);
    mvc
      .perform(
        delete("/reading-plans").param(
          "expectedVersion",
          String.valueOf(newPlanVersion)
        )
      )
      .andExpect(status().isOk());
  }

  /** 验证管理员可通过聚合接口代管其他成员的计划与条目。 */
  @Test
  void adminHttpCrudAndAggregatedQueriesClosedLoop() throws Exception {
    login(adminId, adminRole, 3);
    String plan = postJson(
      "/admin/reading-plans",
      """
      {"memberId":%d,"name":"馆员计划","targetBooks":2,"status":"ACTIVE"}
      """.formatted(otherId)
    );
    long planVersion = number(plan, "$.data.version");

    String item = postJson(
      "/admin/reading-plans/%d/items".formatted(otherId),
      """
      {"bookId":%d,"priority":10,"source":"AI","expectedPlanVersion":%d}
      """.formatted(book1, planVersion)
    );
    long itemVersion = number(item, "$.data.version");

    mvc
      .perform(
        get("/admin/reading-plans").param("memberId", otherId.toString())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
    mvc
      .perform(
        get("/admin/reading-plans/items")
          .param("memberId", otherId.toString())
          .param("bookId", book1.toString())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));

    planVersion = currentPlanVersion(otherId);
    mvc
      .perform(
        put("/admin/reading-plans/{memberId}/items/{bookId}", otherId, book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(itemJson("READING", 50, itemVersion, planVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.progressPercent").value(50));

    itemVersion = currentItemVersion(otherId, book1);
    planVersion = currentPlanVersion(otherId);
    mvc
      .perform(
        delete("/admin/reading-plans/{memberId}/items/{bookId}", otherId, book1)
          .param("expectedVersion", String.valueOf(itemVersion))
          .param("expectedPlanVersion", String.valueOf(planVersion))
      )
      .andExpect(status().isOk());

    planVersion = currentPlanVersion(otherId);
    mvc
      .perform(
        delete("/admin/reading-plans/{memberId}", otherId).param(
          "expectedVersion",
          String.valueOf(planVersion)
        )
      )
      .andExpect(status().isOk());
  }

  /** 验证未登录、越权访问、自动完成和旧版本冲突。 */
  @Test
  void permissionsOptimisticLockAndTerminalRulesAreEnforced() throws Exception {
    LoginUserContext.clear();
    mvc.perform(get("/reading-plans")).andExpect(status().isUnauthorized());

    login(readerId, readerRole, 1);
    mvc.perform(get("/admin/reading-plans")).andExpect(status().isForbidden());
    postJson(
      "/reading-plans",
      """
      {"name":"规则计划","targetBooks":1,"status":"DRAFT"}
      """
    );

    long planVersion = currentPlanVersion(readerId);
    String item = addMine(book1, 1, planVersion);
    long itemVersion = number(item, "$.data.version");
    planVersion = currentPlanVersion(readerId);

    // 状态机要求条目先开始阅读，再由 100% 进度自动转为 COMPLETED。
    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(itemJson("READING", 50, itemVersion, planVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("READING"));

    itemVersion = currentItemVersion(readerId, book1);
    planVersion = currentPlanVersion(readerId);
    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(itemJson("READING", 100, itemVersion, planVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("COMPLETED"));

    mvc
      .perform(
        put("/reading-plans")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"name":"旧版本","targetBooks":1,"status":"ACTIVE","version":0}
            """
          )
      )
      .andExpect(status().isConflict());
  }

  private String postJson(String path, String json) throws Exception {
    return mvc
      .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
  }

  private String addMine(Long bookId, int sequenceNo, long planVersion)
    throws Exception {
    return postJson(
      "/reading-plans/items",
      """
      {"bookId":%d,"sequenceNo":%d,"expectedPlanVersion":%d}
      """.formatted(bookId, sequenceNo, planVersion)
    );
  }

  private String itemJson(
    String status,
    int progress,
    long itemVersion,
    long planVersion
  ) {
    return """
    {"status":"%s","progressPercent":%d,"version":%d,"expectedPlanVersion":%d}
    """.formatted(status, progress, itemVersion, planVersion);
  }

  private long currentPlanVersion(Long memberId) {
    return jdbc.queryForObject(
      "select version from reading_plan where member_id=?",
      Long.class,
      memberId
    );
  }

  private long currentItemVersion(Long memberId, Long bookId) {
    return jdbc.queryForObject(
      """
      select version from reading_plan_item
      where plan_id=(select id from reading_plan where member_id=?) and book_id=?
      """,
      Long.class,
      memberId,
      bookId
    );
  }

  private long number(String json, String path) {
    return ((Number) JsonPath.read(json, path)).longValue();
  }

  private Long role(String code) {
    return jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
    );
  }

  private Long member(int level, Long roleId, String name, String email) {
    jdbc.update(
      """
      insert into member(
          member_type,role_id,name,email,
          account_status,qualification_status,version
      ) values(?,?,?,?,'ACTIVE','APPROVED',0)
      """,
      com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(level).name(),
      roleId,
      name,
      email
    );
    return jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
  }

  private Long book(Long categoryId, String title, String isbn) {
    jdbc.update(
      """
      insert into book(category_id,title,author,isbn,language,status,version)
      values(?,?,?,?,'zh-CN','ACTIVE',0)
      """,
      categoryId,
      title,
      "作者",
      isbn
    );
    return jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      isbn
    );
  }

  private void login(Long memberId, Long roleId, int level) {
    LoginUserContext.set(
      new LoginSession(
        "test", memberId, roleId,
        com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(level),
        level == 3 ? "ADMIN" : level == 2 ? "LIBRARIAN"
          : level == 4 ? "AI_LIBRARIAN" : "READER",
        "x@x", "x"
      )
    );
  }
}

