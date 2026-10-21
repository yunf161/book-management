package com.yunfeil161.ailibrary.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yunfeil161.ailibrary.ai.runtime.*;
import com.yunfeil161.ailibrary.ai.service.*;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.*;

/** 覆盖异步任务、授权范围、全局开关、签名审计凭据和UNKNOWN核对。 */
@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:ai_runtime_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "library.recommendation.ai-token=unit-internal-token-for-signed-callbacks",
  }
)
class AiRuntimeIntegrationTests {

  @Autowired
  AiTaskService tasks;

  @Autowired
  AiTaskMapper mapper;

  @Autowired
  AiRuntimeControl controls;

  @Autowired
  AiActionReconciler reconciler;

  @Autowired
  AiActionCorrelation correlation;

  @Autowired
  AiUsageService usage;

  @Autowired
  AuditGateProbe gateProbe;

  @org.springframework.boot.test.context.TestConfiguration
  static class GateConfiguration {

    @org.springframework.context.annotation.Bean
    AuditGateProbe aiAuditGateProbe() {
      return new AuditGateProbe();
    }
  }

  public static class AuditGateProbe {

    int calls;

    @com.yunfeil161.ailibrary.audit.Audited(
      resource = com.yunfeil161.ailibrary.audit.AuditResource.DEPARTMENT,
      always = true
    )
    public void write() {
      calls++;
    }
  }

  @Autowired
  com.yunfeil161.ailibrary.audit.OperationLogWriter audit;

  @Autowired
  com.yunfeil161.ailibrary.audit.OperationLogMapper auditMapper;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  AiGatewayClient gateway;

  @MockitoBean
  com.yunfeil161.ailibrary.auth.service.LoginSessionService sessions;

  @BeforeEach
  void setup() {
    when(sessions.find(anyString())).thenAnswer(call ->
      LoginUserContext.getNullable()
    );
    jdbc.update("delete from ai_task");
    jdbc.update("update ai_runtime_control set enabled=true");
    login("READER", 123L);
    when(
      gateway.chat(anyLong(), anyString(), anyString(), nullable(String.class))
    ).thenAnswer(call ->
      stream(
        "event: delta\ndata: {\"text\":\"测试回答\"}\n\nevent: usage\ndata: {\"calls\":[{\"provider\":\"ollama\",\"promptTokens\":10,\"completionTokens\":20}]}\n\nevent: done\ndata: {}\n\n"
      )
    );
  }

  void login(String role, Long id) {
    LoginUserContext.set(
      new LoginSession(
        "test",
        id,
        jdbc.queryForObject(
          "select id from role where code=?",
          Long.class,
          role
        ),
        MemberType.READER,
        role,
        "test@example.test",
        "测试"
      )
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  AiGatewayClient.Stream stream(String text) {
    return new AiGatewayClient.Stream(
      mock(HttpURLConnection.class),
      new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))
    );
  }

  AiTask await(String id) throws Exception {
    for (int i = 0; i < 100; i++) {
      var task = mapper.find(id);
      if (
        Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(task.getState())
      ) return task;
      Thread.sleep(30);
    }
    throw new AssertionError("task timeout");
  }

  @Test
  void persistentAsyncTaskStoresResultAndActualUsage() throws Exception {
    var task = tasks.submit(
      new AiTaskService.Request("CONSULTATION", "问题"),
      "synthetic-token"
    );
    var done = await(task.getTaskId());
    assertEquals("SUCCEEDED", done.getState());
    assertEquals(10L, done.getPromptTokens());
    assertEquals(20L, done.getCompletionTokens());
    assertEquals(0, done.getEstimatedCost().signum());
    assertTrue(done.getResultJson().contains("测试回答"));
    assertFalse(done.getResultJson().contains("synthetic-token"));
  }

  @Test
  void failedTaskCanRetryButSuccessfulTaskCannot() throws Exception {
    when(
      gateway.chat(anyLong(), anyString(), anyString(), nullable(String.class))
    ).thenThrow(new ServiceUnavailableException("测试失败"));
    var first = await(
      tasks
        .submit(new AiTaskService.Request("CONSULTATION", "问题"), "token")
        .getTaskId()
    );
    assertEquals("FAILED", first.getState());
    var retried = tasks.retry(first.getTaskId(), "token");
    assertEquals(
      retried.getTaskId(),
      tasks.retry(first.getTaskId(), "token").getTaskId()
    );
    assertEquals(1, retried.getAttempt());
    assertEquals(first.getTaskId(), retried.getParentTaskId());
    await(retried.getTaskId());
  }

  @Test
  void scopeProtectsOtherMembersAndAdminListHidesTheirContent()
    throws Exception {
    var task = await(
      tasks
        .submit(new AiTaskService.Request("CONSULTATION", "私人问题"), "token")
        .getTaskId()
    );
    login("READER", 456L);
    var q = new AiTaskQuery();
    q.setMemberId(123L);
    assertThrows(ForbiddenException.class, () -> tasks.query(q));
    assertThrows(ForbiddenException.class, () ->
      tasks.cancel(task.getTaskId())
    );
    login("ADMIN", 789L);
    var all = tasks.query(new AiTaskQuery());
    assertTrue(
      all
        .getRows()
        .stream()
        .allMatch(
          row -> row.getQuestion() == null && row.getResultJson() == null
        )
    );
    var cancelledByAdmin = tasks.cancel(task.getTaskId());
    assertNull(cancelledByAdmin.getQuestion());
    assertNull(cancelledByAdmin.getResultJson());
  }

  @Test
  void pauseBlocksActualSubmissionAndUsesVersion() {
    login("ADMIN", 123L);
    Long version = jdbc.queryForObject(
      "select version from ai_runtime_control where code='GLOBAL'",
      Long.class
    );
    controls.update("GLOBAL", false, version);
    assertThrows(ConflictException.class, () ->
      controls.update("GLOBAL", true, version)
    );
    assertFalse(controls.enabled("CONSULTATION"));
    assertFalse(controls.enabled("BUSINESS"));
    assertFalse(controls.enabled("RECOMMENDATION"));
    assertThrows(ServiceUnavailableException.class, () ->
      tasks.submit(new AiTaskService.Request("CONSULTATION", "问题"), "token")
    );
    jdbc.update(
      "update ai_runtime_control set enabled=true where code='GLOBAL'"
    );
    assertTrue(controls.enabled("BUSINESS"));
  }

  @Test
  void readerCannotChangeControls() {
    assertThrows(ForbiddenException.class, () -> controls.query());
    assertThrows(ForbiddenException.class, () ->
      controls.update("GLOBAL", false, 0L)
    );
  }

  @Test
  void unknownReconciliationRequiresSignedTransactionReceipt() {
    String id = UUID.randomUUID().toString();
    var now = java.time.LocalDateTime.now();
    jdbc.update(
      "insert into ai_business_action(action_id,member_id,skill_code,title,request_json,request_hash,state,expires_at,created_at,updated_at) values(?,123,'borrow_apply','测试','{}',?,'UNKNOWN',?,?,?)",
      id,
      "0".repeat(64),
      now.plusMinutes(1),
      now,
      now
    );
    assertEquals(0, reconciler.reconcile().get("resolved"));
    var request = new MockHttpServletRequest("POST", "/borrow-applications");
    request.addHeader("X-AI-Action-Id", id);
    request.addHeader("X-AI-Action-Signature", "0".repeat(64));
    assertNull(correlation.verified(request));
    var log = audit.entry("BORROW_APPLICATION", 789L, "create");
    log.setEventId(id);
    log.setResultStatus("SUCCESS");
    auditMapper.insert(log);
    assertEquals(1, reconciler.reconcile().get("resolved"));
    assertEquals(
      "SUCCEEDED",
      jdbc.queryForObject(
        "select state from ai_business_action where action_id=?",
        String.class,
        id
      )
    );
  }

  @Test
  void recoveryUsesDatabaseClockAndOnlyExpiresStaleTasks() {
    var row = new AiTask();
    row.setTaskId(UUID.randomUUID().toString());
    row.setMemberId(123L);
    row.setType("CONSULTATION");
    row.setQuestion("测试");
    row.setAttempt(0);
    mapper.insert(row);
    tasks.recover();
    assertEquals("QUEUED", mapper.find(row.getTaskId()).getState());
    jdbc.update(
      "update ai_task set updated_at=timestampadd(MINUTE,-40,current_timestamp(3)) where task_id=?",
      row.getTaskId()
    );
    tasks.recover();
    assertEquals("FAILED", mapper.find(row.getTaskId()).getState());
  }

  @Test
  void cancellationWinsAgainstLateModelCompletion() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var input = new InputStream() {
      @Override
      public int read() throws IOException {
        entered.countDown();
        try {
          release.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
        }
        return -1;
      }

      @Override
      public void close() {
        release.countDown();
      }
    };
    when(
      gateway.chat(anyLong(), anyString(), anyString(), nullable(String.class))
    ).thenReturn(
      new AiGatewayClient.Stream(mock(HttpURLConnection.class), input)
    );
    var row = tasks.submit(
      new AiTaskService.Request("CONSULTATION", "慢回答"),
      "token"
    );
    assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
    assertEquals("CANCELLED", tasks.cancel(row.getTaskId()).getState());
    release.countDown();
    assertEquals("CANCELLED", await(row.getTaskId()).getState());
  }

  @Test
  void revokedSessionCannotRunQueuedModelTask() throws Exception {
    when(sessions.find(anyString())).thenReturn(null);
    var row = await(
      tasks
        .submit(
          new AiTaskService.Request("CONSULTATION", "会话已撤销"),
          "token"
        )
        .getTaskId()
    );
    assertEquals("FAILED", row.getState());
    assertEquals("UnauthorizedException", row.getErrorCode());
    verifyNoInteractions(gateway);
  }

  @Test
  void modelUsageIsIdempotentAndUnknownPriceStaysNull() {
    String event = UUID.randomUUID().toString();
    var call = Map.of(
      "provider",
      "aliyun",
      "model",
      "qwen-plus",
      "promptTokens",
      100,
      "completionTokens",
      200
    );
    usage.record(event, 123L, List.of(call));
    usage.record(event, 123L, List.of(call));
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from ai_model_usage where event_id=?",
        Integer.class,
        event
      )
    );
    assertNull(
      jdbc.queryForObject(
        "select estimated_cost from ai_model_usage where event_id=?",
        java.math.BigDecimal.class,
        event
      )
    );
    assertThrows(ForbiddenException.class, () -> usage.query(null, null, 456L));
    assertFalse(
      ((List<?>) usage.query(null, null, null).get("rows")).isEmpty()
    );
  }

  @Test
  void pausedSignedCallbackCannotEnterActualBusinessWrite() {
    login("ADMIN", 123L);
    String id = UUID.randomUUID().toString();
    var now = java.time.LocalDateTime.now();
    jdbc.update(
      "insert into ai_business_action(action_id,member_id,skill_code,title,request_json,request_hash,state,expires_at,created_at,updated_at) values(?,123,'borrow_apply','测试','{}',?,'EXECUTING',?,?,?)",
      id,
      "0".repeat(64),
      now.plusMinutes(1),
      now,
      now
    );
    var request = new MockHttpServletRequest("POST", "/borrow-applications");
    request.addHeader("X-AI-Action-Id", id);
    request.addHeader(
      "X-AI-Action-Signature",
      correlation.sign(id, "POST", "/borrow-applications", 123L)
    );
    RequestContextHolder.setRequestAttributes(
      new ServletRequestAttributes(request)
    );
    assertEquals(id, correlation.verified(request));
    jdbc.update(
      "update ai_runtime_control set enabled=false where code='BUSINESS'"
    );
    int before = gateProbe.calls;
    assertThrows(ServiceUnavailableException.class, () -> gateProbe.write());
    assertEquals(before, gateProbe.calls);
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from member_operation_log where event_id=? and result_status='FAILURE'",
        Integer.class,
        id
      )
    );
  }

  @Test
  void unresolvableUnknownActionsDoNotStarveVerifiedOnes() {
    String verified = null;
    var now = java.time.LocalDateTime.now();
    for (int i = 0; i < 102; i++) {
      String id = UUID.randomUUID().toString();
      jdbc.update(
        "insert into ai_business_action(action_id,member_id,skill_code,title,request_json,request_hash,state,expires_at,created_at,updated_at) values(?,123,'borrow_apply','测试','{}',?,'UNKNOWN',?,?,?)",
        id,
        "0".repeat(64),
        now.plusMinutes(1),
        now,
        now
      );
      verified = id;
    }
    var event = audit.entry("BORROW_APPLICATION", 789L, "create");
    event.setEventId(verified);
    event.setResultStatus("SUCCESS");
    auditMapper.insert(event);
    assertEquals(1, reconciler.reconcile().get("resolved"));
    assertEquals(
      "SUCCEEDED",
      jdbc.queryForObject(
        "select state from ai_business_action where action_id=?",
        String.class,
        verified
      )
    );
  }

  @Test
  void configuredUnitPricesProduceExplicitEstimate() {
    String event = UUID.randomUUID().toString();
    org.springframework.test.util.ReflectionTestUtils.setField(
      usage,
      "inputPrice",
      new java.math.BigDecimal("1")
    );
    org.springframework.test.util.ReflectionTestUtils.setField(
      usage,
      "outputPrice",
      new java.math.BigDecimal("2")
    );
    try {
      usage.record(
        event,
        123L,
        List.of(
          Map.of(
            "provider",
            "aliyun",
            "model",
            "qwen-plus",
            "promptTokens",
            100,
            "completionTokens",
            200
          )
        )
      );
      assertEquals(
        0,
        new java.math.BigDecimal("0.0005").compareTo(
          jdbc.queryForObject(
            "select estimated_cost from ai_model_usage where event_id=?",
            java.math.BigDecimal.class,
            event
          )
        )
      );
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
        usage,
        "inputPrice",
        new java.math.BigDecimal("-1")
      );
      org.springframework.test.util.ReflectionTestUtils.setField(
        usage,
        "outputPrice",
        new java.math.BigDecimal("-1")
      );
    }
  }
}
