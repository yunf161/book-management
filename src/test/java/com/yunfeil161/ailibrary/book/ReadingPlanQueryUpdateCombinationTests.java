package com.yunfeil161.ailibrary.book;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 阅读计划和条目查询、更新字段组合矩阵测试。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ReadingPlanQueryUpdateCombinationTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  Long reader1, reader2, admin, readerRole, adminRole;
  Long plan1, plan2, book1, book2, item1, item2;

  @BeforeEach
  void setUp() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    readerRole = role("READER");
    adminRole = role("ADMIN");
    reader1 = member(
      1,
      readerRole,
      "QueryAlice" + s,
      "alice" + s + "@test.local",
      "13800000001"
    );
    reader2 = member(
      1,
      readerRole,
      "QueryBob" + s,
      "bob" + s + "@test.local",
      "13800000002"
    );
    admin = member(
      3,
      adminRole,
      "QueryAdmin" + s,
      "admin" + s + "@test.local",
      "13800000003"
    );

    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      "QC" + s,
      "QueryCategory" + s
    );
    Long category = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      "QC" + s
    );
    book1 = book(category, "Java Matrix " + s, "Author Alpha", "QISBN-A" + s);
    book2 = book(
      category,
      "Database Matrix " + s,
      "Author Beta",
      "QISBN-B" + s
    );

    plan1 = plan(
      reader1,
      "Java Growth " + s,
      "first description",
      4,
      "2026-10-01",
      "2027-01-01",
      "DRAFT"
    );
    plan2 = plan(
      reader2,
      "Database Growth " + s,
      "second description",
      6,
      "2026-11-01",
      "2027-02-01",
      "ACTIVE"
    );
    item1 = item(plan1, book1, 1, 10, "PLANNED", 0, "USER", "first note");
    item2 = item(
      plan1,
      book2,
      2,
      20,
      "READING",
      40,
      "LIBRARIAN",
      "second note"
    );
    // 为 CREATED_TIME 正、倒序测试提供确定的时间顺序，避免两次插入落在同一毫秒。
    jdbc.update(
      "update reading_plan_item set created_at=? where id=?",
      java.sql.Timestamp.valueOf("2026-01-01 00:00:00.000"),
      item1
    );
    jdbc.update(
      "update reading_plan_item set created_at=? where id=?",
      java.sql.Timestamp.valueOf("2026-01-02 00:00:00.000"),
      item2
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  @Test
  void planQuerySupportsEveryFieldSeparatelyAndTogether() throws Exception {
    login(admin, adminRole, 3);

    assertPlanQuery("memberId=" + reader1, 1, reader1);
    assertPlanQuery("memberKeyword=QueryAlice", 1, reader1);
    assertPlanQuery("memberKeyword=alice", 1, reader1);
    assertPlanQuery("memberKeyword=13800000001", 1, reader1);
    assertPlanQuery("planName=Java", 1, reader1);
    assertPlanQuery("status=DRAFT", 1, reader1);
    assertPlanQuery("startDateFrom=2026-10-15", 1, reader2);
    assertPlanQuery("startDateTo=2026-10-15", 1, reader1);
    assertPlanQuery("pageNum=1&pageSize=1", 2, null);

    assertPlanQuery(
      "pageNum=1&pageSize=10&memberId=" +
        reader1 +
        "&memberKeyword=QueryAlice&planName=Java&status=DRAFT" +
        "&startDateFrom=2026-09-01&startDateTo=2026-10-31",
      1,
      reader1
    );
  }

  @Test
  void itemQuerySupportsEveryFilterAndCombinedAdminFilter() throws Exception {
    login(admin, adminRole, 3);

    assertItemQuery("memberId=" + reader1, 2, null);
    assertItemQuery("memberKeyword=QueryAlice", 2, null);
    assertItemQuery("bookId=" + book1, 1, book1);
    assertItemQuery("bookKeyword=Java", 1, book1);
    assertItemQuery("bookKeyword=Alpha", 1, book1);
    assertItemQuery("bookKeyword=QISBN-A", 1, book1);
    assertItemQuery("status=PLANNED", 1, book1);
    assertItemQuery("source=LIBRARIAN", 1, book2);

    assertItemQuery(
      "pageNum=1&pageSize=10&memberId=" +
        reader1 +
        "&memberKeyword=QueryAlice&bookId=" +
        book1 +
        "&bookKeyword=Java&status=PLANNED&source=USER",
      1,
      book1
    );
  }

  @Test
  void itemQuerySupportsEverySortFieldAndDirection() throws Exception {
    login(admin, adminRole, 3);

    assertFirstBook("SEQUENCE", "ASC", book1);
    assertFirstBook("SEQUENCE", "DESC", book2);
    assertFirstBook("PRIORITY", "ASC", book1);
    assertFirstBook("PRIORITY", "DESC", book2);
    assertFirstBook("CREATED_TIME", "ASC", book1);
    assertFirstBook("CREATED_TIME", "DESC", book2);

    mvc
      .perform(
        get("/admin/reading-plans/items").param("orderBy", "SQL_FRAGMENT")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        get("/admin/reading-plans/items").param("orderDirection", "SIDEWAYS")
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void mineItemQueryAlwaysOverridesSubmittedMemberId() throws Exception {
    login(reader1, readerRole, 1);

    mvc
      .perform(
        get("/reading-plans/items")
          .param("memberId", reader2.toString())
          .param("pageNum", "1")
          .param("pageSize", "10")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(2))
      .andExpect(jsonPath("$.data.rows[0].memberId").value(reader1));
  }

  @Test
  void planFullUpdateAppliesEveryFieldAndValidTransitions() throws Exception {
    login(reader1, readerRole, 1);

    updatePlan(
      "Updated Plan",
      "updated description",
      9,
      "2026-12-01",
      "2027-06-01",
      "ACTIVE",
      0
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("Updated Plan"))
      .andExpect(jsonPath("$.data.description").value("updated description"))
      .andExpect(jsonPath("$.data.targetBooks").value(9))
      .andExpect(jsonPath("$.data.startDate").value("2026-12-01"))
      .andExpect(jsonPath("$.data.endDate").value("2027-06-01"))
      .andExpect(jsonPath("$.data.status").value("ACTIVE"))
      .andExpect(jsonPath("$.data.version").value(1));

    updatePlan(
      "Paused Plan",
      "paused description",
      10,
      "2026-12-02",
      "2027-06-02",
      "PAUSED",
      1
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("PAUSED"));

    updatePlan(
      "Invalid Draft",
      "invalid",
      10,
      "2026-12-02",
      "2027-06-02",
      "DRAFT",
      2
    ).andExpect(status().isConflict());
  }

  @Test
  void itemSelectiveAndAllFieldUpdatesAreApplied() throws Exception {
    login(reader1, readerRole, 1);

    // 仅修改优先级，其他状态字段保持原值。
    itemUpdate(
      book1,
      "{\"priority\":99,\"version\":0,\"expectedPlanVersion\":0}"
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.priority").value(99))
      .andExpect(jsonPath("$.data.status").value("PLANNED"))
      .andExpect(jsonPath("$.data.progressPercent").value(0));

    // 同时修改顺序、优先级、状态、进度、日期和备注。
    itemUpdate(
      book1,
      "{\"sequenceNo\":2,\"priority\":30," +
        "\"status\":\"READING\",\"progressPercent\":35," +
        "\"plannedStartDate\":\"2026-12-10\"," +
        "\"plannedFinishDate\":\"2027-01-10\"," +
        "\"note\":\"all fields\",\"version\":1," +
        "\"expectedPlanVersion\":1}"
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.sequenceNo").value(2))
      .andExpect(jsonPath("$.data.priority").value(30))
      .andExpect(jsonPath("$.data.status").value("READING"))
      .andExpect(jsonPath("$.data.progressPercent").value(35))
      .andExpect(jsonPath("$.data.plannedStartDate").value("2026-12-10"))
      .andExpect(jsonPath("$.data.plannedFinishDate").value("2027-01-10"))
      .andExpect(jsonPath("$.data.note").value("all fields"));

    // 同一状态下只更新进度，并保留首次startedAt。
    String startedAt = jdbc.queryForObject(
      "select cast(started_at as varchar) from reading_plan_item where id=?",
      String.class,
      item1
    );
    itemUpdate(
      book1,
      "{\"progressPercent\":80,\"version\":2," + "\"expectedPlanVersion\":2}"
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("READING"))
      .andExpect(jsonPath("$.data.progressPercent").value(80));
    Assertions.assertEquals(
      startedAt,
      jdbc.queryForObject(
        "select cast(started_at as varchar) from reading_plan_item where id=?",
        String.class,
        item1
      )
    );
  }

  private void assertPlanQuery(String query, int total, Long expectedMember)
    throws Exception {
    var result = mvc
      .perform(get("/admin/reading-plans?" + query))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(total));
    if (expectedMember != null) result.andExpect(
      jsonPath("$.data.rows[0].memberId").value(expectedMember)
    );
  }

  private void assertItemQuery(String query, int total, Long expectedBook)
    throws Exception {
    var result = mvc
      .perform(get("/admin/reading-plans/items?" + query))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(total));
    if (expectedBook != null) result.andExpect(
      jsonPath("$.data.rows[0].bookId").value(expectedBook)
    );
  }

  private void assertFirstBook(String orderBy, String direction, Long expected)
    throws Exception {
    mvc
      .perform(
        get("/admin/reading-plans/items")
          .param("memberId", reader1.toString())
          .param("orderBy", orderBy)
          .param("orderDirection", direction)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rows[0].bookId").value(expected));
  }

  private org.springframework.test.web.servlet.ResultActions updatePlan(
    String name,
    String description,
    int targetBooks,
    String start,
    String end,
    String statusValue,
    long version
  ) throws Exception {
    return mvc.perform(
      put("/reading-plans")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
          "{\"name\":\"" +
            name +
            "\",\"description\":\"" +
            description +
            "\",\"targetBooks\":" +
            targetBooks +
            ",\"startDate\":\"" +
            start +
            "\",\"endDate\":\"" +
            end +
            "\",\"status\":\"" +
            statusValue +
            "\",\"version\":" +
            version +
            "}"
        )
    );
  }

  private org.springframework.test.web.servlet.ResultActions itemUpdate(
    Long bookId,
    String body
  ) throws Exception {
    return mvc.perform(
      put("/reading-plans/items/{bookId}", bookId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
    );
  }

  private Long role(String code) {
    return jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
    );
  }

  private Long member(
    int level,
    Long role,
    String name,
    String email,
    String phone
  ) {
    jdbc.update(
      "insert into member(member_type,role_id,name,email,phone,account_status," +
        "qualification_status,version) values(?,?,?,?,?,'ACTIVE','APPROVED',0)",
      com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(level).name(),
      role,
      name,
      email,
      phone
    );
    return jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
  }

  private Long book(Long category, String title, String author, String isbn) {
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) " +
        "values(?,?,?,?,'zh-CN','ACTIVE',0)",
      category,
      title,
      author,
      isbn
    );
    return jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      isbn
    );
  }

  private Long plan(
    Long member,
    String name,
    String description,
    int target,
    String start,
    String end,
    String status
  ) {
    jdbc.update(
      "insert into reading_plan(member_id,name,description,target_books,start_date,end_date," +
        "status,version) values(?,?,?,?,?,?,?,0)",
      member,
      name,
      description,
      target,
      start,
      end,
      status
    );
    return jdbc.queryForObject(
      "select id from reading_plan where member_id=?",
      Long.class,
      member
    );
  }

  private Long item(
    Long plan,
    Long book,
    int sequence,
    int priority,
    String status,
    int progress,
    String source,
    String note
  ) {
    jdbc.update(
      "insert into reading_plan_item(plan_id,book_id,sequence_no,priority,status," +
        "progress_percent,source,note,version) values(?,?,?,?,?,?,?,?,0)",
      plan,
      book,
      sequence,
      priority,
      status,
      progress,
      source,
      note
    );
    return jdbc.queryForObject(
      "select id from reading_plan_item where plan_id=? and book_id=?",
      Long.class,
      plan,
      book
    );
  }

  private void login(Long member, Long role, int level) {
    LoginUserContext.set(
      new LoginSession(
        "matrix", member, role,
        com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(level),
        level == 3 ? "ADMIN" : level == 2 ? "LIBRARIAN"
          : level == 4 ? "AI_LIBRARIAN" : "READER",
        "matrix@test", "matrix"
      )
    );
  }
}

