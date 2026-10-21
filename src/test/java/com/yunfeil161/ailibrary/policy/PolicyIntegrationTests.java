package com.yunfeil161.ailibrary.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.policy.dto.*;
import com.yunfeil161.ailibrary.policy.interceptor.RuleConsentInterceptor;
import com.yunfeil161.ailibrary.policy.service.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 覆盖真实数据库政策状态、须知决策、鉴权及原接口阻断规则。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class PolicyIntegrationTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MockMvc mvc;

  @Autowired
  PolicyService policies;

  @Autowired
  PolicyState state;

  @Autowired
  RuleConsentService consent;

  @Autowired
  RuleConsentInterceptor interceptor;

  @Autowired
  LoginSessionService sessions;

  Long member;
  Long admin;
  String jti;

  @BeforeEach
  void setup() {
    member = insert("READER");
    admin = insert("ADMIN");
    login(admin, "ADMIN");
  }

  @AfterEach
  void cleanup() {
    if (jti != null) sessions.delete(jti);
    LoginUserContext.clear();
  }

  private Long insert(String role) {
    String email = UUID.randomUUID() + "@policy.test";
    jdbc.update(
      "insert into member(name,email,member_type,role_id,account_status,qualification_status,permanent_borrow_limit,version) " +
        "select ?,?,? ,id,'ACTIVE','APPROVED',5,0 from role where code=?",
      "Policy-" + UUID.randomUUID(),
      email,
      "READER".equals(role) ? "READER" : "EMPLOYEE",
      role
    );
    return jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
  }

  private void login(Long id, String role) {
    LoginUserContext.set(
      new LoginSession(
        "policy-test",
        id,
        jdbc.queryForObject(
          "select role_id from member where id=?",
          Long.class,
          id
        ),
        "READER".equals(role) ? MemberType.READER : MemberType.EMPLOYEE,
        role,
        "policy@test.local",
        "Policy test"
      )
    );
  }

  private PolicyWriteRequest draft() {
    PolicyWriteRequest r = new PolicyWriteRequest();
    r.setType("BORROWING");
    r.setTitle("Updated rules");
    r.setContent("Return on time.");
    r.setBorrowingParameters(state.borrowing());
    return r;
  }

  private Long create() {
    return ((Number) policies.create(draft()).get("id")).longValue();
  }

  private PolicyPublishRequest publishRequest(Long current, Long version) {
    PolicyPublishRequest r = new PolicyPublishRequest();
    r.setExpectedCurrentPolicyId(current);
    r.setExpectedVersion(version);
    return r;
  }

  private RuleDecisionRequest accept(String rule, long version) {
    RuleDecisionRequest r = new RuleDecisionRequest();
    r.setDecision("ACCEPT");
    r.setRuleVersion(rule);
    r.setExpectedMemberVersion(version);
    return r;
  }

  @Test
  void publicCurrentAndAggregateQuery() throws Exception {
    mvc
      .perform(get("/policies/current"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.ruleVersion").value("BORROWING-1"));
    Long id = create();
    mvc
      .perform(
        get("/admin/policies")
          .param("id", id.toString())
          .param("status", "DRAFT")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  void draftUpdateUsesOptimisticLock() {
    Long id = create();
    var r = draft();
    r.setExpectedVersion(0L);
    assertEquals(1L, policies.update(id, r).get("version"));
    assertThrows(ConflictException.class, () -> policies.update(id, r));
  }

  @Test
  void publishMovesPointerRetiresOldAndWritesOutbox() {
    Long old = state.current("BORROWING").getId();
    Long id = create();
    var result = policies.publish(id, publishRequest(old, 0L));
    assertEquals("BORROWING-2", result.get("ruleVersion"));
    assertEquals(
      "RETIRED",
      jdbc.queryForObject(
        "select status from policy_version where id=?",
        String.class,
        old
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from outbox_event where event_type='policy.published'",
        Integer.class
      )
    );
    assertEquals(result, policies.publish(id, publishRequest(old, 0L)));
    assertThrows(ConflictException.class, () -> policies.update(id, draft()));
  }

  @Test
  void stalePointerAndMissingPointerAreRejected() {
    Long id = create();
    assertThrows(ConflictException.class, () ->
      policies.publish(id, publishRequest(-1L, 0L))
    );
    var r = new PolicyPublishRequest();
    r.setExpectedVersion(0L);
    assertThrows(WrongFormatException.class, () -> policies.publish(id, r));
  }

  @Test
  void invalidParametersAndUnknownFieldsReturn400() throws Exception {
    var r = draft();
    r.getBorrowingParameters().setDefaultBorrowDays(100);
    mvc
      .perform(
        post("/admin/policies")
          .contentType(MediaType.APPLICATION_JSON)
          .content(JsonUtils.toJson(r))
      )
      .andExpect(status().isBadRequest());
    String json = JsonUtils.toJson(draft()).replace(
      "\"defaultBorrowDays\":30",
      "\"unknownDays\":30"
    );
    mvc
      .perform(
        post("/admin/policies")
          .contentType(MediaType.APPLICATION_JSON)
          .content(json)
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void readerCannotManagePolicies() throws Exception {
    login(member, "READER");
    mvc.perform(get("/admin/policies")).andExpect(status().isForbidden());
    mvc
      .perform(
        post("/admin/policies")
          .contentType(MediaType.APPLICATION_JSON)
          .content(JsonUtils.toJson(draft()))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void acceptIsVersionedAndIdempotent() {
    login(member, "READER");
    assertTrue(state.requiresConsent(member));
    assertThrows(ConflictException.class, () ->
      consent.decide(accept("BORROWING-0", 0L))
    );
    assertThrows(ConflictException.class, () ->
      consent.decide(accept("BORROWING-1", 2L))
    );
    consent.decide(accept("BORROWING-1", 0L));
    consent.decide(accept("BORROWING-1", 0L));
    assertFalse(state.requiresConsent(member));
    assertEquals(
      1L,
      jdbc.queryForObject(
        "select version from member where id=?",
        Long.class,
        member
      )
    );
  }

  @Test
  void oldInterfacesBlockedUntilConsentThenAllowed() {
    login(member, "READER");
    assertTrue(
      interceptor.preHandle(
        new MockHttpServletRequest("GET", "/books"),
        new MockHttpServletResponse(),
        new Object()
      )
    );
    for (String path : List.of(
      "/me/permissions",
      "/borrow-applications",
      "/members",
      "/reading-plan"
    )) {
      var request = new MockHttpServletRequest("GET", path);
      var response = new MockHttpServletResponse();
      assertThrows(ForbiddenException.class, () ->
        interceptor.preHandle(request, response, new Object())
      );
      assertEquals("RULE_CONSENT_REQUIRED", response.getHeader("X-Error-Code"));
    }
    consent.decide(accept("BORROWING-1", 0L));
    assertTrue(
      interceptor.preHandle(
        new MockHttpServletRequest("GET", "/me/permissions"),
        new MockHttpServletResponse(),
        new Object()
      )
    );
  }

  @Test
  void declineRevokesAllDeviceSessionsIncludingStaleRules() {
    login(member, "READER");
    jti = UUID.randomUUID().toString();
    String second = UUID.randomUUID().toString();
    var s = LoginUserContext.get();
    sessions.save(
      jti,
      new LoginSession(
        jti,
        member,
        s.getRoleId(),
        MemberType.READER,
        "READER",
        "p@test.local",
        "p"
      ),
      Duration.ofMinutes(5)
    );
    sessions.save(
      second,
      new LoginSession(
        second,
        member,
        s.getRoleId(),
        MemberType.READER,
        "READER",
        "p@test.local",
        "p"
      ),
      Duration.ofMinutes(5)
    );
    var r = accept("BORROWING-0", 0L);
    r.setDecision("DECLINE");
    consent.decide(r);
    assertNull(sessions.find(jti));
    assertNull(sessions.find(second));
  }

  @Test
  void publicationRequiresNewConsentAndRejectsOldApplicationSnapshot() {
    var old = state.current("BORROWING");
    login(member, "READER");
    consent.decide(accept("BORROWING-1", 0L));
    login(admin, "ADMIN");
    policies.publish(create(), publishRequest(old.getId(), 0L));
    assertTrue(state.requiresConsent(member));
    assertThrows(ConflictException.class, () ->
      state.verifyBorrowSnapshot(old.getId(), old.getPolicyVersion())
    );
  }

  @Test
  void missingCurrentPolicyFailsClosed() {
    jdbc.update(
      "update policy_current set policy_id=null where type='BORROWING'"
    );
    assertThrows(ServiceUnavailableException.class, () -> state.ruleVersion());
  }

  @Test
  void newLimitsDoNotRewriteExistingLoanDeadline() {
    long applicationId = Math.abs(System.nanoTime());
    jdbc.update(
      "insert into borrow_record(application_id,member_id,copy_id,borrowed_at,due_at,status,approved_by) " +
      "values(?,?,999,current_timestamp,dateadd('DAY',10,current_timestamp),'BORROWING',?)",
      applicationId, member, admin
    );
    var before = jdbc.queryForObject("select due_at from borrow_record where application_id=?",
      java.sql.Timestamp.class, applicationId);
    var request = draft();
    request.getBorrowingParameters().setMaxRenewCount(0);
    Long old = state.current("BORROWING").getId();
    Long id = ((Number) policies.create(request).get("id")).longValue();
    policies.publish(id, publishRequest(old, 0L));
    assertEquals(0, state.borrowing().getMaxRenewCount());
    assertEquals(before, jdbc.queryForObject("select due_at from borrow_record where application_id=?",
      java.sql.Timestamp.class, applicationId));
  }
}
