package com.yunfeil161.ailibrary.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.*;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.identity.dto.DepartmentUpdateRequest;
import com.yunfeil161.ailibrary.identity.entity.Dept;
import com.yunfeil161.ailibrary.identity.service.DeptService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用真实业务事务及Redis锁验证审计原子性、脱敏、权限和并发。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:operation_audit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OperationAuditIntegrationTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  DeptService departments;

  @Autowired
  MockMvc mvc;

  @Autowired
  AuditRedactor redactor;

  @Autowired
  PlatformTransactionManager transactions;

  @Autowired
  AuditProbe probe;

  @MockitoSpyBean
  OperationLogMapper logs;

  long actor, department;

  @BeforeEach
  void prepare() {
    var tag = UUID.randomUUID().toString();
    jdbc.update(
      "insert into member(name,email,member_type,role_id,account_status,qualification_status,permanent_borrow_limit,version) select ?,?,'EMPLOYEE',id,'ACTIVE','APPROVED',5,0 from role where code='ADMIN'",
      tag,
      tag + "@audit.test"
    );
    actor = jdbc.queryForObject(
      "select id from member where name=?",
      Long.class,
      tag
    );
    jdbc.update(
      "insert into department(code,name,status,version) values(?,?,'ACTIVE',0)",
      tag.substring(0, 24),
      tag
    );
    department = jdbc.queryForObject(
      "select id from department where name=?",
      Long.class,
      tag
    );
    login("ADMIN");
  }

  @AfterEach
  void clear() {
    reset(logs);
    LoginUserContext.clear();
    jdbc.update(
      "delete from role_permission where role_id=(select id from role where code='LIBRARIAN') and permission_id=(select id from permission where code='operation-log:query')"
    );
  }

  @Test
  void successfulUpdateRecordsActorAndBeforeAfterInSameTransaction() {
    departments.updateDepartment(department, update(0));
    var row = jdbc.queryForMap(
      "select * from member_operation_log where target_type='DEPARTMENT' and target_id=?",
      department
    );
    assertEquals(actor, ((Number) row.get("actor_member_id")).longValue());
    assertEquals("EMPLOYEE", row.get("actor_member_type"));
    assertEquals("SUCCESS", row.get("result_status"));
    assertTrue(row.get("before_data").toString().contains("ACTIVE"));
    assertTrue(row.get("after_data").toString().contains("DISABLED"));
    assertNotNull(row.get("actor_role_id"));
  }

  @Test
  void outerRollbackRemovesBusinessAndSuccessAudit() {
    assertThrows(IllegalStateException.class, () ->
      new TransactionTemplate(transactions).execute(status -> {
        departments.updateDepartment(department, update(0));
        throw new IllegalStateException("外层业务失败");
      })
    );
    assertEquals(
      "ACTIVE",
      jdbc.queryForObject(
        "select status from department where id=?",
        String.class,
        department
      )
    );
    assertEquals(0, count("SUCCESS"));
  }

  @Test
  void auditInsertFailureRollsBackBusiness() {
    doThrow(new IllegalStateException("测试审计写入失败"))
      .when(logs)
      .insert(any());
    assertThrows(IllegalStateException.class, () ->
      departments.updateDepartment(department, update(0))
    );
    assertEquals(
      "ACTIVE",
      jdbc.queryForObject(
        "select status from department where id=?",
        String.class,
        department
      )
    );
    assertEquals(
      0L,
      jdbc.queryForObject(
        "select version from department where id=?",
        Long.class,
        department
      )
    );
  }

  @Test
  void businessFailurePersistsIndependentFailureWithoutSnapshots() {
    assertThrows(ConflictException.class, () ->
      departments.updateDepartment(department, update(9))
    );
    assertEquals(1, count("FAILURE"));
    var log = jdbc.queryForMap(
      "select * from member_operation_log where target_type='DEPARTMENT' and target_id=?",
      department
    );
    assertNull(log.get("before_data"));
    assertNull(log.get("after_data"));
    assertEquals("ConflictException", log.get("error_code"));
  }

  @Test
  void redactionDropsCredentialsAndMasksContactsRecursively() {
    var input = new LinkedHashMap<String, Object>();
    input.put("email", "reader@example.test");
    input.put("phone", "13812345678");
    input.put("password_hash", "TOP-SECRET-HASH");
    input.put("token", "JWT-SECRET");
    input.put("description", "API-KEY-SECRET");
    input.put("version", 2);
    String json = redactor.json(redactor.row(input));
    assertFalse(json.contains("13812345678"));
    assertFalse(json.contains("reader@example.test"));
    assertFalse(json.contains("SECRET"));
    assertTrue(json.contains("138****5678"));
    assertTrue(json.contains("r***@example.test"));
    String historic = redactor.stored(
      "{\"member\":[{\"email\":\"reader@example.test\",\"password_hash\":\"SECRET\",\"version\":2}],\"unknown\":\"SECRET\"}"
    );
    assertFalse(historic.contains("SECRET"));
    assertFalse(historic.contains("reader@example.test"));
  }

  @Test
  void authorizedAggregatedQueryAndInvalidRanges() throws Exception {
    departments.updateDepartment(department, update(0));
    mvc
      .perform(
        get("/admin/operation-logs")
          .param("targetType", "DEPARTMENT")
          .param("targetId", "" + department)
          .param("actorMemberId", "" + actor)
          .param("resultStatus", "SUCCESS")
          .param("actionCode", "updateDepartment")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].actorMemberType").value("EMPLOYEE"));
    mvc
      .perform(get("/admin/operation-logs").param("resultStatus", "INVALID"))
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        get("/admin/operation-logs")
          .param("createdFrom", "2030-01-01T00:00:00")
          .param("createdTo", "2020-01-01T00:00:00")
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void roleAndAllScopeAreRequired() throws Exception {
    login("LIBRARIAN");
    mvc.perform(get("/admin/operation-logs")).andExpect(status().isForbidden());
    jdbc.update(
      "insert into role_permission(role_id,permission_id,data_scope) select r.id,p.id,'SELF' from role r cross join permission p where r.code='LIBRARIAN' and p.code='operation-log:query'"
    );
    mvc.perform(get("/admin/operation-logs")).andExpect(status().isForbidden());
    jdbc.update(
      "update role_permission set data_scope='ALL' where role_id=(select id from role where code='LIBRARIAN') and permission_id=(select id from permission where code='operation-log:query')"
    );
    mvc.perform(get("/admin/operation-logs")).andExpect(status().isOk());
    jdbc.update("update role set status='DISABLED' where code='LIBRARIAN'");
    try {
      mvc
        .perform(get("/admin/operation-logs"))
        .andExpect(status().isForbidden());
    } finally {
      jdbc.update("update role set status='ACTIVE' where code='LIBRARIAN'");
    }
  }

  @Test
  void legacySensitiveSnapshotsAreSanitizedAtQueryTime() throws Exception {
    jdbc.update(
      "insert into member_operation_log(event_id,actor_kind,target_type,target_id,action_code,reason,before_data,result_status) values(?,'SYSTEM','DEPARTMENT',?,'LEGACY','包含原手机号13812345678','{\"phone\":\"13812345678\",\"password_hash\":\"HIDDEN-SECRET\"}','SUCCESS')",
      UUID.randomUUID().toString(),
      department
    );
    String result = mvc
      .perform(
        get("/admin/operation-logs")
          .param("targetId", "" + department)
          .param("actionCode", "LEGACY")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    assertFalse(result.contains("13812345678"));
    assertFalse(result.contains("HIDDEN-SECRET"));
    assertTrue(result.contains("138****5678"));
  }

  @Test
  void readsDoNotGenerateMutationAuditAndCreateDeleteRetainSnapshots() {
    departments.getDepartments(department, null, 1, 10);
    assertEquals(0, count("SUCCESS"));
    var dept = new Dept();
    dept.setCode(UUID.randomUUID().toString().substring(0, 20));
    dept.setName(UUID.randomUUID().toString());
    dept.setStatus(DepartmentStatus.ACTIVE);
    departments.addDepartment(dept);
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from member_operation_log where target_type='DEPARTMENT' and target_id=? and before_data is null and after_data is not null",
        Integer.class,
        dept.getId()
      )
    );
    departments.deleteDepartment(dept.getId().longValue());
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from member_operation_log where target_type='DEPARTMENT' and target_id=? and before_data is not null and after_data is null",
        Integer.class,
        dept.getId()
      )
    );
  }

  @Test
  void sixConcurrentUpdatesHaveOneSuccessAndFiveFailures() throws Exception {
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(6);
    try {
      var futures = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 6; i++) futures.add(
        pool.submit(() -> {
          login("ADMIN");
          try {
            start.await();
            departments.updateDepartment(department, update(0));
            return true;
          } catch (ConflictException expected) {
            return false;
          } finally {
            LoginUserContext.clear();
          }
        })
      );
      start.countDown();
      int successes = 0;
      for (var result : futures)
        if (result.get(20, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1, count("SUCCESS"));
    assertEquals(5, count("FAILURE"));
  }

  private DepartmentUpdateRequest update(int version) {
    var r = new DepartmentUpdateRequest();
    r.setStatus(DepartmentStatus.DISABLED);
    r.setExpectedVersion(version);
    return r;
  }

  private int count(String result) {
    return jdbc.queryForObject(
      "select count(*) from member_operation_log where target_type='DEPARTMENT' and target_id=? and result_status=?",
      Integer.class,
      department,
      result
    );
  }

  private void login(String role) {
    var roleId = jdbc.queryForObject(
      "select id from role where code=?",
      Long.class,
      role
    );
    LoginUserContext.set(
      new LoginSession(
        "audit-test",
        actor,
        roleId,
        MemberType.EMPLOYEE,
        role,
        "audit@example.test",
        "审计测试"
      )
    );
  }

  @Test
  void requiresNewBusinessAndAuditCommitTogetherDespiteOuterRollback() {
    assertThrows(IllegalStateException.class, () ->
      new TransactionTemplate(transactions).execute(status -> {
        probe.independent(department);
        throw new IllegalStateException("外层回滚");
      })
    );
    assertEquals(
      "DISABLED",
      jdbc.queryForObject(
        "select status from department where id=?",
        String.class,
        department
      )
    );
    assertEquals(1, count("SUCCESS"));
  }

  @Test
  void permissionQueryWithoutPagingUsesDefaults() throws Exception {
    mvc
      .perform(get("/admin/permissions"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rows").isArray());
  }

  @Test
  void policyRulesKeepOnlyTypedBusinessParameters() {
    String json = redactor.json(
      redactor.row(
        Map.of(
          "parameters_json",
          "{\"maxBorrowDays\":30,\"blockBorrowWhenOverdue\":true,\"password\":\"SECRET\"}"
        )
      )
    );
    assertTrue(json.contains("maxBorrowDays"));
    assertFalse(json.contains("SECRET"));
    assertTrue(
      redactor
        .stored("{\"policy_version\":[" + json + "]}")
        .contains("maxBorrowDays")
    );
  }

  /** 单独验证事务Advisor先于审计执行，不能把REQUIRES_NEW成功日志留在外层事务里。 */
  @org.springframework.boot.test.context.TestConfiguration
  static class ProbeConfiguration {

    @org.springframework.context.annotation.Bean
    AuditProbe auditProbe(JdbcTemplate jdbc) {
      return new AuditProbe(jdbc);
    }
  }

  static class AuditProbe {

    private final JdbcTemplate jdbc;

    AuditProbe(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Audited(resource = AuditResource.DEPARTMENT, id = "#id")
    @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
    )
    public void independent(Long id) {
      jdbc.update(
        "update department set status='DISABLED',version=version+1 where id=?",
        id
      );
    }
  }
}
