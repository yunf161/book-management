package com.yunfeil161.ailibrary.book;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** 阅读条目状态、进度、日期、版本和参数类型的边界集成测试。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ReadingPlanBoundaryIntegrationTests {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JdbcTemplate jdbc;

  private Long memberId;
  private Long roleId;
  private Long book1;
  private Long book2;

  @BeforeEach
  void setUp() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    roleId = jdbc.queryForObject(
      "select id from `role` where code='READER'",
      Long.class
    );
    jdbc.update(
      "insert into member(member_type,role_id,name,email,account_status," +
        "qualification_status,version) values('READER',?,?,?,'ACTIVE','APPROVED',0)",
      roleId,
      "BoundaryReader" + suffix,
      "boundary" + suffix + "@test.local"
    );
    memberId = jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      "boundary" + suffix + "@test.local"
    );

    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      "BD" + suffix,
      "Boundary" + suffix
    );
    Long categoryId = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      "BD" + suffix
    );
    book1 = insertBook(categoryId, "BoundaryBookA" + suffix, "BDA" + suffix);
    book2 = insertBook(categoryId, "BoundaryBookB" + suffix, "BDB" + suffix);

    LoginUserContext.set(
      new LoginSession(
        "boundary-test",
        memberId,
        roleId,
        com.yunfeil161.ailibrary.common.enums.MemberType.READER,
        "READER",
        "boundary@test.local",
        "BoundaryReader"
      )
    );
    createPlan();
  }

  @AfterEach
  void clearContext() {
    LoginUserContext.clear();
  }

  @Test
  void nullStatusAndProgressKeepOriginalValuesAndNullPriorityKeepsOriginalPriority()
    throws Exception {
    addItem(book1, 7);

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            updateJson(null, null, null, itemVersion(book1), planVersion())
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("PLANNED"))
      .andExpect(jsonPath("$.data.progressPercent").value(0))
      .andExpect(jsonPath("$.data.priority").value(7));
  }

  @Test
  void progressBoundariesAreRejectedForEveryStatus() throws Exception {
    addItem(book1, 0);

    updateExpect(book1, "PLANNED", 1, 400);
    updateExpect(book1, "READING", 0, 400);
    updateExpect(book1, "READING", -1, 400);
    updateExpect(book1, "ABANDONED", 101, 400);
  }

  @Test
  void allowedAbandonedTransitionsAndForbiddenTerminalRecoveryAreEnforced()
    throws Exception {
    addItem(book1, 0);
    updateExpect(book1, "ABANDONED", 0, 200);
    updateExpect(book1, "READING", 20, 409);

    addItem(book2, 0);
    updateExpect(book2, "READING", 20, 200);
    updateExpect(book2, "PLANNED", 0, 409);
    updateExpect(book2, "ABANDONED", 20, 200);
    updateExpect(book2, "READING", 30, 409);
  }

  @Test
  void completedItemCannotReturnToReading() throws Exception {
    addItem(book1, 0);
    updateExpect(book1, "READING", 20, 200);
    updateExpect(book1, "READING", 100, 200);

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            updateJson("READING", 50, null, itemVersion(book1), planVersion())
          )
      )
      .andExpect(status().isConflict());
  }

  @Test
  void sequenceDateAndMissingVersionBoundariesAreRejected() throws Exception {
    addItem(book1, 0);

    sequenceExpect(book1, 0, 400);
    sequenceExpect(book1, -1, 400);
    sequenceExpect(book1, 2, 400);

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"plannedStartDate\":\"2026-10-20\"," +
              "\"plannedFinishDate\":\"2026-10-01\"," +
              "\"version\":" +
              itemVersion(book1) +
              "," +
              "\"expectedPlanVersion\":" +
              planVersion() +
              "}"
          )
      )
      .andExpect(status().isBadRequest());

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"status\":\"PLANNED\"," +
              "\"expectedPlanVersion\":" +
              planVersion() +
              "}"
          )
      )
      .andExpect(status().isBadRequest());

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"status\":\"PLANNED\"," +
              "\"version\":" +
              itemVersion(book1) +
              "}"
          )
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void malformedEnumsDatesAndPathIdsReturnBadRequest() throws Exception {
    addItem(book1, 0);

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"status\":\"UNKNOWN\",\"version\":0," +
              "\"expectedPlanVersion\":" +
              planVersion() +
              "}"
          )
      )
      .andExpect(status().isBadRequest());

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", book1)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"plannedStartDate\":\"not-a-date\",\"version\":0," +
              "\"expectedPlanVersion\":" +
              planVersion() +
              "}"
          )
      )
      .andExpect(status().isBadRequest());

    mvc
      .perform(
        put("/reading-plans/items/{bookId}", "not-a-number")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest());
  }

  private void createPlan() throws Exception {
    mvc
      .perform(
        post("/reading-plans")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"name\":\"Boundary Plan\",\"targetBooks\":5," +
              "\"status\":\"DRAFT\"}"
          )
      )
      .andExpect(status().isOk());
  }

  @Test
  void completingPlanRequiresRequestedTargetNotMerelyOneCompletedBook()
    throws Exception {
    addItem(book1, 0);
    updateExpect(book1, "READING", 20, 200);
    updateExpect(book1, "READING", 100, 200);
    completePlan(2, 409);
    // 可在同一次保存中将目标调整为已完成数量；使用本次目标判定。
    completePlan(1, 200);
  }

  @Test
  void targetIsCompletionGoalNotCapacityAndPendingItemsStillBlockCompletion()
    throws Exception {
    // 目标1本仍可加入2本，不把用户目标误当成馆藏/借阅额度。
    mvc
      .perform(
        put("/reading-plans")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"name\":\"目标测试\",\"targetBooks\":1,\"status\":\"ACTIVE\",\"version\":" +
              planVersion() +
              "}"
          )
      )
      .andExpect(status().isOk());
    addItem(book1, 0);
    addItem(book2, 0);
    updateExpect(book1, "READING", 20, 200);
    updateExpect(book1, "READING", 100, 200);
    completePlan(1, 409);
    updateExpect(book2, "ABANDONED", 0, 200);
    completePlan(1, 200);
  }

  private void completePlan(int target, int expectedStatus) throws Exception {
    // 计划状态机仍需先从DRAFT进入ACTIVE。
    jdbc.update(
      "update reading_plan set status='ACTIVE' where member_id=?",
      memberId
    );
    mvc
      .perform(
        put("/reading-plans")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"name\":\"完成目标校验\",\"targetBooks\":" +
              target +
              ",\"status\":\"COMPLETED\",\"version\":" +
              planVersion() +
              "}"
          )
      )
      .andExpect(status().is(expectedStatus));
  }

  private void addItem(Long bookId, int priority) throws Exception {
    mvc
      .perform(
        post("/reading-plans/items")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"bookId\":" +
              bookId +
              ",\"priority\":" +
              priority +
              "," +
              "\"expectedPlanVersion\":" +
              planVersion() +
              "}"
          )
      )
      .andExpect(status().isOk());
  }

  private void updateExpect(
    Long bookId,
    String itemStatus,
    int progress,
    int httpStatus
  ) throws Exception {
    mvc
      .perform(
        put("/reading-plans/items/{bookId}", bookId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            updateJson(
              itemStatus,
              progress,
              null,
              itemVersion(bookId),
              planVersion()
            )
          )
      )
      .andExpect(status().is(httpStatus));
  }

  private void sequenceExpect(Long bookId, int sequence, int httpStatus)
    throws Exception {
    mvc
      .perform(
        put("/reading-plans/items/{bookId}", bookId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            updateJson(null, null, sequence, itemVersion(bookId), planVersion())
          )
      )
      .andExpect(status().is(httpStatus));
  }

  private String updateJson(
    String itemStatus,
    Integer progress,
    Integer sequence,
    Long itemVersion,
    Long currentPlanVersion
  ) {
    StringBuilder json = new StringBuilder("{");
    if (itemStatus != null) json
      .append("\"status\":\"")
      .append(itemStatus)
      .append("\",");
    if (progress != null) json
      .append("\"progressPercent\":")
      .append(progress)
      .append(',');
    if (sequence != null) json
      .append("\"sequenceNo\":")
      .append(sequence)
      .append(',');
    json
      .append("\"version\":")
      .append(itemVersion)
      .append(',')
      .append("\"expectedPlanVersion\":")
      .append(currentPlanVersion)
      .append('}');
    return json.toString();
  }

  private Long planVersion() {
    return jdbc.queryForObject(
      "select version from reading_plan where member_id=?",
      Long.class,
      memberId
    );
  }

  private Long itemVersion(Long bookId) {
    return jdbc.queryForObject(
      "select i.version from reading_plan_item i join reading_plan p on p.id=i.plan_id " +
        "where p.member_id=? and i.book_id=?",
      Long.class,
      memberId,
      bookId
    );
  }

  private Long insertBook(Long categoryId, String title, String isbn) {
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) " +
        "values(?,?,?,?,'zh-CN','ACTIVE',0)",
      categoryId,
      title,
      "BoundaryAuthor",
      isbn
    );
    return jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      isbn
    );
  }
}
