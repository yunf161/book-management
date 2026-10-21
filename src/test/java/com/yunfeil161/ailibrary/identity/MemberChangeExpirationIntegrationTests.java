package com.yunfeil161.ailibrary.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCreateRequest;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import com.yunfeil161.ailibrary.identity.application.service.MemberChangeApplicationService;
import com.yunfeil161.ailibrary.identity.application.service.impl.MemberChangeExpirationService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** 使用真实事务和Redis资源锁，覆盖到期、恢复、失败回滚以及多线程重复扫描。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:change_expiration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberChangeExpirationIntegrationTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MemberChangeApplicationService applications;

  @Autowired
  MemberChangeExpirationService expiration;

  @Autowired
  MockMvc mvc;

  @MockitoSpyBean
  CacheClient cache;

  long member;

  @BeforeEach
  void prepare() {
    member = insertMember("READER", "NOT_APPLIED");
    login(member, "READER");
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
    reset(cache);
  }

  @Test
  void initialQualificationExpiresRestoresAndAllowsResubmission() {
    long id = create(MemberChangeType.READER_QUALIFICATION);
    clearInvocations(cache);
    LocalDateTime now = due(id);
    assertTrue(expiration.expire(id, now));
    assertEquals("EXPIRED", value("member_change_application", "status", id));
    assertEquals(
      "NOT_APPLIED",
      value("member", "qualification_status", member)
    );
    assertEquals(1L, version(id));
    assertEquals(1, notifications(id));
    assertFalse(expiration.expire(id, now));
    assertEquals(1, notifications(id));
    verify(cache).deleteRequired("member:reader:" + member);
    verify(cache).deleteRequired("member:employee:" + member);
    assertNotEquals(id, create(MemberChangeType.READER_QUALIFICATION));
  }

  @Test
  void previouslyRejectedQualificationReturnsToRejected() {
    jdbc.update(
      "update member set qualification_status='REJECTED' where id=?",
      member
    );
    long id = create(MemberChangeType.READER_QUALIFICATION);
    assertTrue(expiration.expire(id, due(id)));
    assertEquals("REJECTED", value("member", "qualification_status", member));
  }

  @Test
  void suspendedQualificationReturnsToSuspended() {
    jdbc.update(
      "update member set qualification_status='SUSPENDED' where id=?",
      member
    );
    long id = create(MemberChangeType.READER_QUALIFICATION_RESTORE);
    assertTrue(expiration.expire(id, due(id)));
    assertEquals("SUSPENDED", value("member", "qualification_status", member));
    assertNotEquals(id, create(MemberChangeType.READER_QUALIFICATION_RESTORE));
  }

  @Test
  void newerProfileVersionAndRuleAcceptanceArePreserved() {
    long id = create(MemberChangeType.READER_QUALIFICATION);
    jdbc.update(
      "update member set phone='13912345678',accepted_rule_version='NEWER-RULE',version=version+1 where id=?",
      member
    );
    assertTrue(expiration.expire(id, due(id)));
    assertEquals("13912345678", value("member", "phone", member));
    assertEquals(
      "NEWER-RULE",
      value("member", "accepted_rule_version", member)
    );
    assertEquals(
      "NOT_APPLIED",
      value("member", "qualification_status", member)
    );
    assertEquals(
      3L,
      jdbc.queryForObject(
        "select version from member where id=?",
        Long.class,
        member
      )
    );
  }

  @Test
  void manualQualificationChangeIsNeverOverwritten() {
    long id = create(MemberChangeType.READER_QUALIFICATION);
    jdbc.update(
      "update member set qualification_status='APPROVED',version=version+1 where id=?",
      member
    );
    assertTrue(expiration.expire(id, due(id)));
    assertEquals("APPROVED", value("member", "qualification_status", member));
  }

  @Test
  void quotaExpirationDoesNotApplyRequestedQuotaAndReleasesDuplicateRestriction() {
    long id = create(MemberChangeType.BORROW_QUOTA_CHANGE);
    assertThrows(
      com.yunfeil161.ailibrary.common.exception.ConflictException.class,
      () -> create(MemberChangeType.BORROW_QUOTA_CHANGE)
    );
    assertTrue(expiration.expire(id, due(id)));
    assertEquals(
      5,
      jdbc.queryForObject(
        "select permanent_borrow_limit from member where id=?",
        Integer.class,
        member
      )
    );
    assertNotEquals(id, create(MemberChangeType.BORROW_QUOTA_CHANGE));
  }

  @Test
  void futureNullAndTerminalApplicationsAreSkipped() {
    long id = create(MemberChangeType.BORROW_QUOTA_CHANGE);
    assertFalse(expiration.expire(id, LocalDateTime.now()));
    jdbc.update(
      "update member_change_application set expires_at=null where id=?",
      id
    );
    assertFalse(expiration.expire(id, LocalDateTime.now().plusYears(1)));
    var now = due(id);
    jdbc.update(
      "update member_change_application set status='CANCELLED' where id=?",
      id
    );
    assertFalse(expiration.expire(id, now));
    assertFalse(expiration.expire(Long.MAX_VALUE, now));
    assertEquals(0, notifications(id));
  }

  @Test
  void exactDeadlineIsIncludedInScan() {
    long id = create(MemberChangeType.BORROW_QUOTA_CHANGE);
    var now = due(id);
    assertTrue(expiration.findExpiredIds(now, 100).contains(id));
    assertTrue(expiration.expire(id, now));
  }

  @Test
  void expiredApprovalAndCancellationReturnConflictBeforeSchedulerRuns()
    throws Exception {
    long id = create(MemberChangeType.READER_QUALIFICATION);
    due(id);
    mvc
      .perform(
        post("/member-change-applications/{id}/cancel", id)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"expectedVersion\":0}")
      )
      .andExpect(status().isConflict());
    login(insertMember("LIBRARIAN", "APPROVED"), "LIBRARIAN");
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", id)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
      )
      .andExpect(status().isConflict());
    assertEquals("PENDING", value("member_change_application", "status", id));
    assertTrue(expiration.expire(id, LocalDateTime.now()));
  }

  @Test
  void expiredStatusCanBeQueriedThroughExistingAggregatedEndpoint()
    throws Exception {
    long id = create(MemberChangeType.BORROW_QUOTA_CHANGE);
    expiration.expire(id, due(id));
    mvc
      .perform(
        get("/member-change-applications")
          .param("id", "" + id)
          .param("status", "EXPIRED")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].status").value("EXPIRED"));
  }

  @Test
  void cacheFailureRollsBackStateAndNotificationThenNextScanSucceeds() {
    long id = create(MemberChangeType.READER_QUALIFICATION);
    var now = due(id);
    doThrow(new ServiceUnavailableException("测试缓存不可用"))
      .when(cache)
      .deleteRequired(anyString());
    assertThrows(ServiceUnavailableException.class, () ->
      expiration.expire(id, now)
    );
    assertEquals("PENDING", value("member_change_application", "status", id));
    assertEquals("PENDING", value("member", "qualification_status", member));
    assertEquals(0, notifications(id));
    reset(cache);
    assertTrue(expiration.expire(id, now));
  }

  @Test
  void sixConcurrentScansProduceOneTransitionAndOneNotification()
    throws Exception {
    long id = create(MemberChangeType.READER_QUALIFICATION);
    var now = due(id);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 6; i++) results.add(
        pool.submit(() -> {
          start.await();
          return expiration.expire(id, now);
        })
      );
      start.countDown();
      int successes = 0;
      for (var result : results)
        if (result.get(20, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1L, version(id));
    assertEquals(1, notifications(id));
    assertEquals(
      "NOT_APPLIED",
      value("member", "qualification_status", member)
    );
  }

  // 每个用例单独提交数据，工作线程才能看到申请并使用真实事务争抢资源锁。
  long insertMember(String role, String qualification) {
    String email = UUID.randomUUID() + "@expiry.test";
    jdbc.update(
      "insert into member(name,email,password_hash,member_type,role_id,account_status,qualification_status,email_verified_at,accepted_rule_version,permanent_borrow_limit,borrowed_book_count,version) select ?,?,'hash',?,id,'ACTIVE',?,current_timestamp,'BORROWING-1',5,0,0 from role where code=?",
      email.substring(0, 36),
      email,
      role.equals("READER") ? "READER" : "EMPLOYEE",
      qualification,
      role
    );
    return jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
  }

  void login(long id, String role) {
    Long roleId = jdbc.queryForObject(
      "select id from role where code=?",
      Long.class,
      role
    );
    LoginUserContext.set(
      new LoginSession(
        "expiry-test",
        id,
        roleId,
        role.equals("READER") ? MemberType.READER : MemberType.EMPLOYEE,
        role,
        "expiry@test",
        "到期测试"
      )
    );
  }

  long create(MemberChangeType type) {
    var r = new MemberChangeCreateRequest();
    r.setApplicationType(type);
    r.setReason("到期处理测试");
    r.setExpectedMemberVersion(
      jdbc.queryForObject(
        "select version from member where id=?",
        Long.class,
        member
      )
    );
    if (type == MemberChangeType.READER_QUALIFICATION) r.setAcceptedRuleVersion(
      "BORROWING-1"
    );
    if (
      type == MemberChangeType.BORROW_QUOTA_CHANGE
    ) r.setRequestedMaxBorrowCount(8);
    return applications.create(r).getId();
  }

  LocalDateTime due(long id) {
    var now = LocalDateTime.now().minusSeconds(1).withNano(0);
    jdbc.update(
      "update member_change_application set expires_at=? where id=?",
      now,
      id
    );
    return now;
  }

  String value(String table, String column, long id) {
    return jdbc.queryForObject(
      "select " + column + " from " + table + " where id=?",
      String.class,
      id
    );
  }

  long version(long id) {
    return jdbc.queryForObject(
      "select version from member_change_application where id=?",
      Long.class,
      id
    );
  }

  int notifications(long id) {
    return jdbc.queryForObject(
      "select count(*) from message where notification_key=?",
      Integer.class,
      "member-change:" + id + ":expired"
    );
  }
}
