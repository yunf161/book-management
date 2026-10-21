package com.yunfeil161.ailibrary.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yunfeil161.ailibrary.ai.handoff.*;
import com.yunfeil161.ailibrary.ai.service.*;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 真实私聊/消息/Outbox事务，AI会话归属查询使用替身。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:ai_handoff_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
class AiHandoffIntegrationTests {

  @Autowired
  AiHandoffService service;

  @Autowired
  AiChatService chats;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  AiGatewayClient gateway;

  Long reader, staff, other;
  String aiId;

  @BeforeEach
  void setup() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    reader = member("r" + suffix, "READER");
    staff = member("s" + suffix, "LIBRARIAN");
    other = member("o" + suffix, "READER");
    jdbc.update(
      "insert into department(code,name) values(?,?)",
      suffix,
      suffix
    );
    Long department = jdbc.queryForObject(
      "select id from department where code=?",
      Long.class,
      suffix
    );
    jdbc.update(
      "insert into employee_profile(member_id,employee_no,department_id,position_name,hired_on) values(?,?,?,'馆员',current_date)",
      staff,
      "STAFF-" + suffix,
      department
    );
    aiId = UUID.randomUUID().toString();
    login(reader, "READER");
    when(gateway.conversation(eq(reader), eq(aiId), eq(false))).thenReturn(
      Map.of("conversationId", aiId)
    );
  }

  Long member(String name, String role) {
    jdbc.update(
      "insert into member(name,member_type,role_id,account_status,qualification_status) select ?,?,id,'ACTIVE','APPROVED' from role where code=?",
      name,
      "READER".equals(role) ? "READER" : "EMPLOYEE",
      role
    );
    return jdbc.queryForObject(
      "select id from member where name=?",
      Long.class,
      name
    );
  }

  void login(Long member, String code) {
    LoginUserContext.set(
      new LoginSession(
        "test",
        member,
        jdbc.queryForObject(
          "select id from role where code=?",
          Long.class,
          code
        ),
        code.equals("READER") ? MemberType.READER : MemberType.EMPLOYEE,
        code,
        "test@example.test",
        "测试"
      )
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  AiHandoff create() {
    return service.create(
      new AiHandoffService.Request(staff, aiId, "本人确认的咨询摘要")
    );
  }

  @Test
  void requestAcceptChatAndCloseLifecycle() {
    var row = create();
    assertEquals("WAITING", row.getStatus());
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from message where conversation_id=?",
        Integer.class,
        row.getConversationId()
      )
    );
    assertEquals(row.getId(), create().getId());
    login(staff, "LIBRARIAN");
    var accepted = service.decide(row.getId(), "ACCEPT", row.getVersion());
    assertEquals("ACCEPTED", accepted.getStatus());
    login(reader, "READER");
    assertThrows(ConflictException.class, () -> chats.requireAutomated(aiId));
    var closed = service.decide(row.getId(), "CLOSE", accepted.getVersion());
    assertEquals("CLOSED", closed.getStatus());
    assertDoesNotThrow(() -> chats.requireAutomated(aiId));
    assertNotEquals(row.getId(), create().getId());
  }

  @Test
  void otherMembersCannotReadOrDecide() {
    var row = create();
    login(other, "READER");
    var query = new AiHandoffQuery();
    query.setId(row.getId());
    assertEquals(0, service.query(query).getTotal());
    assertThrows(ForbiddenException.class, () ->
      service.decide(row.getId(), "ACCEPT", 0L)
    );
  }

  @Test
  void duplicateDifferentRequestAndOldVersionRejected() {
    var row = create();
    assertThrows(ConflictException.class, () ->
      service.create(new AiHandoffService.Request(staff, aiId, "不同摘要"))
    );
    login(staff, "LIBRARIAN");
    service.decide(row.getId(), "ACCEPT", 0L);
    assertThrows(ConflictException.class, () ->
      service.decide(row.getId(), "CLOSE", 0L)
    );
  }

  @Test
  void readerCannotBeAssignedAndOtherAiConversationRejected() {
    assertThrows(ForbiddenException.class, () ->
      service.create(new AiHandoffService.Request(other, aiId, "摘要"))
    );
    when(gateway.conversation(eq(reader), eq(aiId), eq(false))).thenThrow(
      new NotFoundException("会话不存在")
    );
    assertThrows(NotFoundException.class, this::create);
  }
}
