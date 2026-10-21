package com.yunfeil161.ailibrary.communication;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.communication.common.cache.UnreadIndex;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 使用真实Redis与H2验证通信接口、收件范围、幂等及延迟消费。 */
@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:communication;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(addFilters = false)
class CommunicationIntegrationTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UnreadIndex index;

  @Autowired
  com.yunfeil161.ailibrary.communication.conversation.service.ConversationService conversations;

  @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
  @Autowired com.yunfeil161.ailibrary.communication.message.service.MessageService service;
  long reader;
  long other;
  long staff;

  @Autowired
  com.yunfeil161.ailibrary.communication.notification.service.BusinessNotifications notifications;

  @Autowired org.springframework.transaction.support.TransactionTemplate transactions;

  /** 部门授权固定为本人部门，不能通过查询条件扩展到其他部门或绕过发送检查。 */
  @Test
  void departmentalContactsCannotEscapeScope() throws Exception {
    String code = "CONTACT-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
        "insert into `role`(code,name,description,status) values(?,?,?,'ACTIVE')",
        code,
        code,
        "测试部门范围");
    long roleId = jdbc.queryForObject("select id from `role` where code=?", Long.class, code);
    jdbc.update(
        "insert into role_permission(role_id,permission_id,data_scope) select ?,id,'DEPARTMENT'"
            + " from permission where code='contact:query'",
        roleId);
    jdbc.update("insert into department(code,name,status) values(?,?,'ACTIVE')", code, code);
    long department =
        jdbc.queryForObject("select id from department where code=?", Long.class, code);
    jdbc.update("update member set role_id=? where id=?", roleId, staff);
    jdbc.update(
        "insert into employee_profile(member_id,employee_no,department_id,position_name,hired_on)"
            + " values(?,?,?,'馆员',current_date)",
        staff,
        code,
        department);
    long colleague = member("EMPLOYEE", "LIBRARIAN");
    jdbc.update(
        "insert into employee_profile(member_id,employee_no,department_id,position_name,hired_on)"
            + " values(?,?,?,'馆员',current_date)",
        colleague,
        code + "-2",
        department);
    login(staff);
    mvc.perform(get("/members").param("scene", "CONTACT").param("id", "" + colleague))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/members").param("scene", "CONTACT").param("id", "" + reader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(get("/members").param("scene", "CONTACT").param("departmentId", "999999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    jdbc.update("update `role` set status='DISABLED' where id=?", roleId);
    mvc.perform(get("/members").param("scene", "CONTACT")).andExpect(status().isForbidden());
  }

  /** 联系人只能看到公开摘要；读者不能通过伪造类型查询其他读者。 */
  @Test
  void contactSceneRestrictsTypesAndFields() throws Exception {
    login(reader);
    mvc.perform(
            get("/members")
                .param("scene", "CONTACT")
                .param("id", "" + staff)
                .param("memberType", "READER")
                .param("accountStatus", "DISABLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.rows[0].memberType").value("EMPLOYEE"))
        .andExpect(jsonPath("$.data.rows[0].email").doesNotExist())
        .andExpect(jsonPath("$.data.rows[0].roleId").doesNotExist())
        .andExpect(jsonPath("$.data.rows[0].passwordHash").doesNotExist());
    mvc.perform(get("/members").param("scene", "CONTACT").param("id", "" + other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    login(staff);
    mvc.perform(get("/members").param("scene", "CONTACT").param("id", "" + reader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/members").param("scene", "UNKNOWN")).andExpect(status().isBadRequest());
  }

  /** 列表查询不形成永久授权；账号停用后列表和实际发送同时拒绝。 */
  @Test
  void contactTargetMustRemainActive() throws Exception {
    login(reader);
    jdbc.update("update member set account_status='DISABLED' where id=?", staff);
    mvc.perform(get("/members").param("scene", "CONTACT").param("id", "" + staff))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(
            post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetMemberId\":" + staff + ",\"subject\":\"咨询\"}"))
        .andExpect(status().isForbidden());
  }

  /** 系统通知、收件关系和Outbox同事务保存；重复业务不会生成多条通知。 */
  @Test
  void systemNotificationIsPrivateIdempotentAndReadable() throws Exception {
    var application = notificationApplication();
    transactions.executeWithoutResult(
        tx -> {
          notifications.borrowDecision(application, java.time.LocalDateTime.now().plusDays(7));
          notifications.borrowDecision(application, null);
        });
    transactions.executeWithoutResult(tx -> notifications.borrowDecision(application, null));
    String key = "borrow-application:" + application.getId() + ":decision";
    long messageId =
        jdbc.queryForObject("select id from message where notification_key=?", Long.class, key);
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from message where notification_key=?", Integer.class, key));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from outbox_event where message_id=?", Integer.class, messageId));
    login(reader);
    mvc.perform(
            get("/messages")
                .param("messageType", "SYSTEM")
                .param("businessType", "BORROW_APPLICATION")
                .param("businessId", "" + application.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.rows[0].senderName").value("系统通知"))
        .andExpect(jsonPath("$.data.rows[0].senderMemberId").isEmpty())
        .andExpect(jsonPath("$.data.rows[0].notificationKey").doesNotExist());
    login(other);
    mvc.perform(get("/messages").param("id", "" + messageId))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(post("/messages/{id}/read", messageId)).andExpect(status().isForbidden());
    login(reader);
    mvc.perform(post("/messages/{id}/read", messageId)).andExpect(status().isOk());
    index.add(reader, messageId);
    mvc.perform(
            get("/messages")
                .param("id", "" + messageId)
                .param("box", "INBOX")
                .param("read", "false"))
        .andExpect(jsonPath("$.data.total").value(0));
  }

  /** 业务回滚时不允许留下孤立通知；没有业务事务也不能直接发送系统通知。 */
  @Test
  void notificationRollsBackWithBusinessTransaction() {
    var application = notificationApplication();
    assertThrows(
        IllegalStateException.class,
        () ->
            transactions.executeWithoutResult(
                tx -> {
                  notifications.borrowDecision(application, null);
                  throw new IllegalStateException("模拟业务失败");
                }));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from message where notification_key=?",
            Integer.class,
            "borrow-application:" + application.getId() + ":decision"));
    assertThrows(
        org.springframework.transaction.IllegalTransactionStateException.class,
        () -> notifications.borrowDecision(application, null));
  }

  private com.yunfeil161.ailibrary.book.borrow.entity.BorrowApplication notificationApplication() {
    var application = new com.yunfeil161.ailibrary.book.borrow.entity.BorrowApplication();
    application.setId(Math.abs(UUID.randomUUID().getMostSignificantBits() / 2));
    application.setMemberId(reader);
    application.setApplicationType(
        com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType.BORROW);
    application.setStatus(
        com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationStatus.APPROVED);
    return application;
  }

  @BeforeEach
  void setup() {
    reader = member("READER", "READER");
    other = member("READER", "READER");
    staff = member("EMPLOYEE", "LIBRARIAN");
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  long member(String type, String role) {
    String email = UUID.randomUUID() + "@communication.test";
    jdbc.update(
        "insert into member(name,email,member_type,role_id,account_status,qualification_status) "
            + "select ?,?,?,id,'ACTIVE','APPROVED' from `role` where code=?",
        email,
        email,
        type,
        role);
    return jdbc.queryForObject("select id from member where email=?", Long.class, email);
  }

  void login(long id) {
    long role = jdbc.queryForObject("select role_id from member where id=?", Long.class, id);
    LoginUserContext.set(
        new LoginSession(
            "communication-test",
            id,
            role,
            id == staff ? MemberType.EMPLOYEE : MemberType.READER,
            id == staff ? "LIBRARIAN" : "READER",
            "test@local",
            "测试"));
  }

  long conversation() throws Exception {
    login(reader);
    String response =
        mvc.perform(
                post("/conversations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"targetMemberId\":" + staff + ",\"subject\":\"咨询\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(response, "$.data.id")).longValue();
  }

  /** 发件、收件、本人已读、对方不可读、旧版本与关闭后的发送组成闭环。 */
  @Test
  void directMessageClosedLoop() throws Exception {
    long c = conversation();
    String client = UUID.randomUUID().toString();
    String body =
        "{\"conversationId\":"
            + c
            + ",\"body\":\"请问如何借阅？\",\"clientMessageId\":\""
            + client
            + "\"}";
    String response =
        mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sequence").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long message = ((Number) JsonPath.read(response, "$.data.id")).longValue();
    mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(message));
    mvc.perform(
            post("/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("请问如何借阅？", "不同内容")))
        .andExpect(status().isConflict());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from outbox_event where message_id=?", Integer.class, message));
    login(other);
    mvc.perform(get("/messages").param("id", "" + message))
        .andExpect(jsonPath("$.data.total").value(0));
    mvc.perform(post("/messages/{id}/read", message)).andExpect(status().isForbidden());
    mvc.perform(get("/messages").param("conversationId", "" + c)).andExpect(status().isForbidden());
    login(staff);
    mvc.perform(get("/messages").param("box", "INBOX").param("read", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rows[0].recipients").isEmpty());
    mvc.perform(post("/messages/{id}/read", message)).andExpect(status().isOk());
    mvc.perform(post("/messages/{id}/read", message)).andExpect(status().isOk());
    // 模拟消息已读后到达的旧MQ事件，应跳过Redis写入。
    index.add(staff, message);
    String namespace =
        (String) org.springframework.test.util.ReflectionTestUtils.getField(index, "namespace");
    assertNull(redis.opsForZSet().score(namespace + ":unread:" + staff, "" + message));
    mvc.perform(
            get("/messages").param("id", "" + message).param("box", "INBOX").param("read", "false"))
        .andExpect(jsonPath("$.data.total").value(0));
    login(reader);
    mvc.perform(
            put("/conversations/{id}", c)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CLOSED\",\"expectedVersion\":0}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/conversations/{id}", c)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"OPEN\",\"expectedVersion\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace(client, UUID.randomUUID().toString())))
        .andExpect(status().isConflict());
  }

  @Test
  void multicastAndPermissions() throws Exception {
    login(staff);
    String body =
        "{\"recipientIds\":["
            + reader
            + ","
            + other
            + ","
            + other
            + "],\"body\":\"闭馆通知\",\"clientMessageId\":\""
            + UUID.randomUUID()
            + "\"}";
    String response =
        mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recipientCount").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long message = ((Number) JsonPath.read(response, "$.data.id")).longValue();
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from outbox_event where message_id=?", Integer.class, message));
    login(reader);
    mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetMemberId\":" + other + "}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/messages").param("box", "INBOX").param("recipientMemberId", "" + other))
        .andExpect(status().isForbidden());
    mvc.perform(get("/messages").param("box", "OUTBOX").param("read", "false"))
        .andExpect(status().isBadRequest());
    LoginUserContext.clear();
    mvc.perform(get("/messages")).andExpect(status().isUnauthorized());
  }

  /** 十个并发重试使用同一个业务ID，只能写入一条消息和一个Outbox。 */
  @Test
  void concurrentIdempotentSend() throws Exception {
    long c = conversation();
    String client = UUID.randomUUID().toString();
    var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Long>>();
    for (int i = 0; i < 10; i++) {
      tasks.add(
          () -> {
            login(reader);
            try {
              return service.send(sendRequest(c, client)).getId();
            } finally {
              LoginUserContext.clear();
            }
          });
    }
    var ids = new java.util.HashSet<>(concurrently(tasks));
    assertEquals(1, ids.size());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from message where sender_member_id=? and client_message_id=?",
            Integer.class,
            reader,
            client));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from outbox_event where message_id=?",
            Integer.class,
            ids.iterator().next()));
  }

  @Test
  void queryCombinationsAndInputBoundaries() throws Exception {
    long c = conversation();
    String response =
        mvc.perform(
                post("/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"conversationId\":"
                            + c
                            + ",\"subject\":\"查询主题\",\"body\":\"查询正文\",\"clientMessageId\":\""
                            + UUID.randomUUID()
                            + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(response, "$.data.id")).longValue();
    mvc.perform(
            get("/messages")
                .param("id", "" + id)
                .param("box", "OUTBOX")
                .param("recipientMemberId", "" + staff)
                .param("sendType", "DIRECT")
                .param("subject", "查询")
                .param("conversationId", "" + c)
                .param("afterSequence", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(
            get("/conversations")
                .param("id", "" + c)
                .param("targetMemberId", "" + staff)
                .param("subject", "咨询")
                .param("status", "OPEN")
                .param("pageSize", "1"))
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(get("/messages").param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/messages").param("afterSequence", "1")).andExpect(status().isBadRequest());
    mvc.perform(get("/messages").param("id", "-1")).andExpect(status().isBadRequest());
    mvc.perform(get("/messages").param("box", "UNKNOWN")).andExpect(status().isBadRequest());
    mvc.perform(
            get("/messages")
                .param("createdFrom", "2026-10-08T00:00:00")
                .param("createdTo", "2026-10-07T00:00:00"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    login(staff);
    mvc.perform(get("/conversations").param("unreadOnly", "true").param("id", "" + c))
        .andExpect(jsonPath("$.data.total").value(1));
    mvc.perform(
            get("/messages")
                .param("senderMemberId", "" + reader)
                .param("box", "INBOX")
                .param("read", "false"))
        .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  void aiAndDisabledMembersCannotCommunicate() throws Exception {
    long ai = member("AI_LIBRARIAN", "AI_LIBRARIAN");
    login(staff);
    mvc.perform(
            post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetMemberId\":" + ai + "}"))
        .andExpect(status().isForbidden());
    jdbc.update("update member set account_status='DISABLED' where id=?", reader);
    mvc.perform(
            post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetMemberId\":" + reader + "}"))
        .andExpect(status().isForbidden());
  }

  /** 双方同时创建同一会话，必须取得同一参与者锁且只创建一条记录。 */
  @Test
  void concurrentOppositeConversationCreation() throws Exception {
    var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Long>>();
    for (int i = 0; i < 10; i++) {
      long sender = i % 2 == 0 ? reader : staff;
      long target = i % 2 == 0 ? staff : reader;
      tasks.add(
          () -> {
            login(sender);
            try {
              var request =
                  new com.yunfeil161.ailibrary.communication.conversation.dto
                      .ConversationCreateRequest();
              request.setTargetMemberId(target);
              request.setSubject("并发创建");
              return conversations.create(request).getId();
            } finally {
              LoginUserContext.clear();
            }
          });
    }
    assertEquals(1, new java.util.HashSet<>(concurrently(tasks)).size());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from conversation where participant_one_id=? and participant_two_id=?",
            Integer.class,
            Math.min(reader, staff),
            Math.max(reader, staff)));
  }

  /** 不同发送人并发发送不同消息，必须分配连续且唯一的会话序号。 */
  @Test
  void concurrentDifferentMessagesKeepUniqueSequence() throws Exception {
    long c = conversation();
    var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Long>>();
    for (int i = 0; i < 10; i++) {
      long sender = i % 2 == 0 ? reader : staff;
      String clientId = UUID.randomUUID().toString();
      tasks.add(
          () -> {
            login(sender);
            try {
              return service.send(sendRequest(c, clientId)).getId();
            } finally {
              LoginUserContext.clear();
            }
          });
    }
    assertEquals(10, new java.util.HashSet<>(concurrently(tasks)).size());
    assertEquals(
        java.util.stream.LongStream.rangeClosed(1, 10).boxed().toList(),
        jdbc.queryForList(
            "select sequence from message where conversation_id=? order by sequence",
            Long.class,
            c));
    assertEquals(
        10L,
        jdbc.queryForObject("select last_sequence from conversation where id=?", Long.class, c));
    assertEquals(
        10,
        jdbc.queryForObject(
            "select count(*) from outbox_event where message_id in (select id from message where"
                + " conversation_id=?)",
            Integer.class,
            c));
  }

  /** 重复消费与重复已读同时开始，两种执行顺序都必须最终以MySQL已读为准。 */
  @Test
  void concurrentReadAndDuplicateIndexWritesDoNotResurrectMessage() throws Exception {
    long c = conversation();
    login(reader);
    long id = service.send(sendRequest(c, UUID.randomUUID().toString())).getId();
    index.add(staff, id);
    var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();
    for (int i = 0; i < 20; i++) {
      boolean read = i % 2 == 0;
      tasks.add(
          () -> {
            try {
              if (read) {
                login(staff);
                service.read(id);
              } else {
                index.add(staff, id);
              }
              return true;
            } finally {
              LoginUserContext.clear();
            }
          });
    }
    assertTrue(concurrently(tasks).stream().allMatch(Boolean::booleanValue));
    String namespace =
        (String) org.springframework.test.util.ReflectionTestUtils.getField(index, "namespace");
    String key = namespace + ":unread:" + staff;
    assertNotNull(
        jdbc.queryForObject(
            "select read_at from message_recipient where message_id=? and recipient_member_id=?",
            java.sql.Timestamp.class,
            id,
            staff));
    assertNull(redis.opsForZSet().score(key, "" + id));
    // 已读标记失效以后，旧事件同样不能重新写入索引。
    redis.delete(key + ":read:" + id);
    for (int i = 0; i < 3; i++) index.add(staff, id);
    assertNull(redis.opsForZSet().score(key, "" + id));
    assertFalse(Boolean.TRUE.equals(redis.hasKey(key + ":read:" + id)));
  }

  private com.yunfeil161.ailibrary.communication.message.dto.MessageSendRequest sendRequest(
      long conversation, String clientId) {
    var request = new com.yunfeil161.ailibrary.communication.message.dto.MessageSendRequest();
    request.setConversationId(conversation);
    request.setBody("并发测试消息");
    request.setClientMessageId(clientId);
    return request;
  }

  /** 同一幂等键竞争不同正文，只能有一个请求成功，其余必须明确返回冲突。 */
  @Test
  void concurrentDifferentBodiesWithSameClientIdConflict() throws Exception {
    long c = conversation();
    String clientId = UUID.randomUUID().toString();
    var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();
    for (int i = 0; i < 10; i++) {
      String body = "不同正文" + i;
      tasks.add(
          () -> {
            login(reader);
            try {
              var request = sendRequest(c, clientId);
              request.setBody(body);
              service.send(request);
              return true;
            } catch (com.yunfeil161.ailibrary.common.exception.ConflictException e) {
              assertEquals("clientMessageId已用于不同内容", e.getMessage());
              return false;
            } finally {
              LoginUserContext.clear();
            }
          });
    }
    assertEquals(1L, concurrently(tasks).stream().filter(Boolean::booleanValue).count());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from message where conversation_id=?", Integer.class, c));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from message_recipient where message_id in (select id from message"
                + " where conversation_id=?)",
            Integer.class,
            c));
  }

  /** 关闭与发送竞争同一会话行：先发送则完整保存，先关闭则拒绝发送，不能半写入。 */
  @RepeatedTest(5)
  void concurrentCloseAndSendRemainAtomic() throws Exception {
    long c = conversation();
    List<java.util.concurrent.Callable<String>> tasks =
        List.of(
            () -> {
              login(reader);
              try {
                service.send(sendRequest(c, UUID.randomUUID().toString()));
                return "SENT";
              } catch (com.yunfeil161.ailibrary.common.exception.ConflictException e) {
                assertEquals("会话已关闭", e.getMessage());
                return "REJECTED";
              } finally {
                LoginUserContext.clear();
              }
            },
            () -> {
              login(staff);
              try {
                var request =
                    new com.yunfeil161.ailibrary.communication.conversation.dto
                        .ConversationUpdateRequest();
                request.setStatus("CLOSED");
                request.setExpectedVersion(0L);
                conversations.update(c, request);
                return "CLOSED";
              } finally {
                LoginUserContext.clear();
              }
            });
    var results = concurrently(tasks);
    long expected = "SENT".equals(results.get(0)) ? 1L : 0L;
    assertEquals(
        "CLOSED",
        jdbc.queryForObject("select status from conversation where id=?", String.class, c));
    assertEquals(
        expected,
        jdbc.queryForObject("select last_sequence from conversation where id=?", Long.class, c));
    assertEquals(
        expected,
        jdbc.queryForObject("select count(*) from message where conversation_id=?", Long.class, c));
    assertEquals(
        expected,
        jdbc.queryForObject(
            "select count(*) from outbox_event where message_id in (select id from message where"
                + " conversation_id=?)",
            Long.class,
            c));
    assertEquals(
        expected,
        jdbc.queryForObject(
            "select count(*) from message_recipient where message_id in (select id from message"
                + " where conversation_id=?)",
            Long.class,
            c));
  }

  /** 所有线程到齐再统一释放起跑信号，避免提交任务时已经串行完成。 */
  private <T> List<T> concurrently(List<java.util.concurrent.Callable<T>> tasks) throws Exception {
    var executor = java.util.concurrent.Executors.newFixedThreadPool(tasks.size());
    var ready = new java.util.concurrent.CountDownLatch(tasks.size());
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<T>>();
      for (var task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("未同时起跑");
                  return task.call();
                }));
      }
      assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
      start.countDown();
      var results = new java.util.ArrayList<T>();
      for (var future : futures) results.add(future.get(20, java.util.concurrent.TimeUnit.SECONDS));
      return results;
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }
}
