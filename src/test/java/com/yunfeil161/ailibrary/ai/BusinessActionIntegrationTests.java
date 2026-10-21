package com.yunfeil161.ailibrary.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yunfeil161.ailibrary.ai.business.*;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 真实H2、MyBatis、事务及Redisson资源锁；业务HTTP单独验证，避免模型测试写入真实业务。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:ai_business_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
class BusinessActionIntegrationTests {

  @Test
  @org.springframework.transaction.annotation.Transactional
  void addingAnItemUsesOriginalCreatePermissionInsteadOfUpdatePermission() {
    jdbc.update(
      "delete from role_permission where role_id=? and permission_id in (select id from permission where code='reading-plan:update:self')",
      reader.getRoleId()
    );
    var skills = service
      .skills()
      .stream()
      .map(row -> row.get("code"))
      .toList();
    assertTrue(skills.contains("plan_add_item"));
    assertFalse(skills.contains("plan_update"));
    assertDoesNotThrow(() ->
      service.preview(
        new BusinessActionService.Preview(
          "plan_add_item",
          Map.of("bookId", 1, "expectedPlanVersion", 0)
        )
      )
    );
  }

  @Autowired
  BusinessActionService service;

  @Autowired
  BusinessActionStore store;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  BusinessHttpClient http;

  LoginSession reader;

  @BeforeEach
  void setup() {
    jdbc.update("delete from ai_business_action");
    jdbc.update("update role set status='ACTIVE'");
    Long role = jdbc.queryForObject(
      "select id from role where code='READER'",
      Long.class
    );
    reader = new LoginSession(
      "test",
      66L,
      role,
      MemberType.READER,
      "READER",
      "test@local.test",
      "读者"
    );
    LoginUserContext.set(reader);
    when(http.execute(any(), anyString(), anyMap(), anyString())).thenAnswer(
      call -> {
        assertFalse(
          TransactionSynchronizationManager.isActualTransactionActive(),
          "执行HTTP时不应持有数据库事务"
        );
        assertEquals(
          "EXECUTING",
          jdbc.queryForObject(
            "select state from ai_business_action order by id desc limit 1",
            String.class
          )
        );
        return new BusinessHttpClient.Outcome(
          true,
          200,
          Map.of("code", 200, "data", Map.of("id", 1), "msg", "success"),
          null
        );
      }
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  Map<String, Object> preview() {
    return service.preview(
      new BusinessActionService.Preview(
        "plan_create",
        Map.of("name", "测试计划", "targetBooks", 3)
      )
    );
  }

  BusinessActionService.Confirmation confirm(Map<String, Object> action) {
    return new BusinessActionService.Confirmation(
      (String) action.get("requestHash")
    );
  }

  @Test
  void previewDoesNotExecuteAndRepeatedConfirmReplaysOnce() {
    var action = preview();
    assertEquals("PREPARED", action.get("state"));
    verifyNoInteractions(http);
    var first = service.confirm(
      (String) action.get("actionId"),
      confirm(action),
      "user-token"
    );
    var second = service.confirm(
      (String) action.get("actionId"),
      confirm(action),
      "user-token"
    );
    assertEquals("SUCCEEDED", first.action().get("state"));
    assertEquals(first.action(), second.action());
    verify(http, times(1)).execute(
      any(),
      eq("/reading-plans"),
      eq(Map.of("name", "测试计划", "targetBooks", 3)),
      eq("user-token")
    );
    assertFalse(
      jdbc
        .queryForObject(
          "select request_json from ai_business_action",
          String.class
        )
        .contains("user-token")
    );
  }

  @Test
  void duplicatePreviewReusesPendingAction() {
    assertEquals(preview().get("actionId"), preview().get("actionId"));
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from ai_business_action",
        Integer.class
      )
    );
  }

  @Test
  void mismatchedHashCannotExecute() {
    var action = preview();
    assertThrows(ConflictException.class, () ->
      service.confirm(
        (String) action.get("actionId"),
        new BusinessActionService.Confirmation("0".repeat(64)),
        "token"
      )
    );
    verifyNoInteractions(http);
  }

  @Test
  void anotherMemberCannotQueryConfirmOrCancel() {
    var action = preview();
    reader.setMemberId(77L);
    var page = service.query(
      (String) action.get("actionId"),
      null,
      new PageQuery()
    );
    assertTrue(page.getRows().isEmpty());
    assertThrows(NotFoundException.class, () ->
      service.confirm((String) action.get("actionId"), confirm(action), "token")
    );
    assertThrows(NotFoundException.class, () ->
      service.cancel((String) action.get("actionId"))
    );
    verifyNoInteractions(http);
  }

  @Test
  void cancelAndExpiryNeverExecute() {
    var action = preview();
    service.cancel((String) action.get("actionId"));
    assertEquals(
      "CANCELLED",
      service
        .confirm((String) action.get("actionId"), confirm(action), "token")
        .action()
        .get("state")
    );
    var next = preview();
    jdbc.update(
      "update ai_business_action set expires_at=timestamp '2000-01-01 00:00:00' where action_id=?",
      next.get("actionId")
    );
    assertThrows(ConflictException.class, () ->
      service.confirm((String) next.get("actionId"), confirm(next), "token")
    );
    assertEquals(
      1L,
      service.query(null, "EXPIRED", new PageQuery()).getTotal()
    );
    assertEquals(
      0L,
      service.query(null, "PREPARED", new PageQuery()).getTotal()
    );
    verifyNoInteractions(http);
  }

  @Test
  void permissionIsCheckedAgainOnConfirmation() {
    var action = preview();
    jdbc.update(
      "update role set status='DISABLED' where id=?",
      reader.getRoleId()
    );
    try {
      assertThrows(ForbiddenException.class, () ->
        service.confirm(
          (String) action.get("actionId"),
          confirm(action),
          "token"
        )
      );
    } finally {
      jdbc.update(
        "update role set status='ACTIVE' where id=?",
        reader.getRoleId()
      );
    }
    verifyNoInteractions(http);
  }

  @Test
  void unknownResultCannotBeRetriedOrProposedAgain() {
    doReturn(
      new BusinessHttpClient.Outcome(
        false,
        503,
        Map.of("code", 503, "msg", "待核实"),
        null
      )
    )
      .when(http)
      .execute(any(), anyString(), anyMap(), anyString());
    var action = preview();
    assertEquals(
      "UNKNOWN",
      service
        .confirm((String) action.get("actionId"), confirm(action), "token")
        .action()
        .get("state")
    );
    service.confirm((String) action.get("actionId"), confirm(action), "token");
    assertThrows(ConflictException.class, this::preview);
    verify(http, times(1)).execute(any(), anyString(), anyMap(), anyString());
  }

  @Test
  void knownBusinessRejectionIsRecordedWithoutRetry() {
    doReturn(
      new BusinessHttpClient.Outcome(
        true,
        409,
        Map.of("code", 409, "msg", "计划已存在"),
        null
      )
    )
      .when(http)
      .execute(any(), anyString(), anyMap(), anyString());
    var action = preview();
    assertEquals(
      "FAILED",
      service
        .confirm((String) action.get("actionId"), confirm(action), "token")
        .action()
        .get("state")
    );
    service.confirm((String) action.get("actionId"), confirm(action), "token");
    verify(http, times(1)).execute(any(), anyString(), anyMap(), anyString());
  }

  @Test
  void readerCannotUseStaffSkillAndCannotPassIdentityOrUrl() {
    assertThrows(ForbiddenException.class, () ->
      service.preview(
        new BusinessActionService.Preview(
          "category_create",
          Map.of("code", "A", "name", "A")
        )
      )
    );
    for (String key : List.of("memberId", "roleId", "url", "token", "method")) {
      assertThrows(WrongFormatException.class, () ->
        service.preview(
          new BusinessActionService.Preview(
            "plan_create",
            Map.of("name", "计划", key, "x")
          )
        )
      );
    }
    assertFalse(
      service
        .skills()
        .stream()
        .anyMatch(skill -> skill.get("code").equals("borrow_approve"))
    );
  }

  @Test
  void eightConcurrentConfirmationsExecuteOnce() throws Exception {
    var action = preview();
    var workers = Executors.newFixedThreadPool(8);
    var gate = new CountDownLatch(1);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int n = 0; n < 8; n++) results.add(
        workers.submit(() -> {
          LoginUserContext.set(reader);
          try {
            gate.await();
            return (String) service
              .confirm(
                (String) action.get("actionId"),
                confirm(action),
                "token"
              )
              .action()
              .get("state");
          } finally {
            LoginUserContext.clear();
          }
        })
      );
      gate.countDown();
      for (var result : results)
        assertEquals("SUCCEEDED", result.get(10, TimeUnit.SECONDS));
      verify(http, times(1)).execute(any(), anyString(), anyMap(), anyString());
    } finally {
      workers.shutdownNow();
    }
  }
}
