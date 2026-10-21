package com.yunfeil161.ailibrary.communication;

import static org.junit.jupiter.api.Assertions.*;

import com.jayway.jsonpath.JsonPath;
import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

/** 显式运行的本机验收：真实MySQL、Redis、RabbitMQ、HTTP和WebSocket。 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=${MYSQL_URL:jdbc:mysql://localhost:3306/library?serverTimezone=Asia/Hong_Kong}",
      "spring.datasource.username=${MYSQL_USERNAME:root}",
      "spring.datasource.password=${MYSQL_PASSWORD}",
      "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
      "spring.sql.init.mode=never",
      "jwt.interceptor-enabled=true",
      "library.communication.mq-enabled=true",
      "spring.rabbitmq.host=localhost",
      "spring.rabbitmq.port=25673",
      "spring.rabbitmq.username=itcast",
      "spring.rabbitmq.password=${RABBITMQ_PASSWORD}",
      "spring.rabbitmq.virtual-host=/ai-library",
      "spring.rabbitmq.publisher-confirm-type=correlated",
      "spring.rabbitmq.publisher-returns=true",
      "spring.rabbitmq.template.mandatory=true"
    })
@DirtiesContext
class CommunicationLiveHttpIT {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @Autowired LoginSessionService sessions;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.yunfeil161.ailibrary.communication.common.cache.UnreadIndex index;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  org.springframework.data.redis.core.StringRedisTemplate redis;

  @Autowired org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry listeners;
  @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
  final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  final List<Map<String, Object>> report = new ArrayList<>();
  final List<Long> members = new ArrayList<>();
  final List<String> tokens = new ArrayList<>();

  record Identity(long id, String token) {}

  Identity member(String type, String role) throws Exception {
    String email = "http-" + UUID.randomUUID() + "@communication.test";
    jdbc.update(
        "insert into"
            + " member(name,email,password_hash,member_type,role_id,account_status,qualification_status)"
            + " select ?,?,?,?,id,'ACTIVE','NOT_APPLIED' from `role` where code=?",
        email,
        email,
        passwords.encode("HttpTest123!"),
        type,
        role);
    long id = jdbc.queryForObject("select id from member where email=?", Long.class, email);
    members.add(id);
    String response =
        call(
            "登录" + role,
            "POST",
            "/login",
            null,
            Map.of("email", email, "password", "HttpTest123!"),
            200,
            false);
    String token = JsonPath.read(response, "$.data.token");
    tokens.add(token);
    return new Identity(id, token);
  }

  String call(
      String label,
      String method,
      String path,
      String token,
      Object body,
      int expected,
      boolean record)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10));
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.header("Content-Type", "application/json");
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)));
    var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(expected, response.statusCode(), label + ":" + response.body());
    if (record)
      report.add(
          Map.of(
              "case",
              label,
              "method",
              method,
              "path",
              path,
              "status",
              response.statusCode(),
              "response",
              JsonUtils.fromJson(response.body(), Map.class)));
    return response.body();
  }

  /** 登录→建会话→在线提醒→离线广播→查询→已读→关闭；结束清理本次数据。 */
  @Test
  void realNetworkClosedLoop() throws Exception {
    WebSocket socket = null;
    try {
      Identity reader = member("READER", "READER");
      Identity staff = member("EMPLOYEE", "LIBRARIAN");
      Identity offline = member("READER", "READER");
      call("未登录拒绝", "GET", "/messages", null, null, 401, true);
      String ticketResult = call("获取连接票据", "POST", "/ws/tickets", staff.token(), null, 200, false);
      String ticket = JsonPath.read(ticketResult, "$.data.ticket");
      BlockingQueue<String> notifications = new LinkedBlockingQueue<>();
      socket =
          client
              .newWebSocketBuilder()
              .buildAsync(
                  URI.create("ws://localhost:" + port + "/ws/events?ticket=" + ticket),
                  new WebSocket.Listener() {
                    StringBuilder buffer = new StringBuilder();

                    public void onOpen(WebSocket ws) {
                      ws.request(1);
                    }

                    public CompletionStage<?> onText(
                        WebSocket ws, CharSequence text, boolean last) {
                      buffer.append(text);
                      if (last) {
                        notifications.add(buffer.toString());
                        buffer.setLength(0);
                      }
                      ws.request(1);
                      return null;
                    }
                  })
              .get(10, TimeUnit.SECONDS);
      // 票据只能消费一次，复用握手被拒绝。
      assertThrows(
          ExecutionException.class,
          () ->
              client
                  .newWebSocketBuilder()
                  .buildAsync(
                      URI.create("ws://localhost:" + port + "/ws/events?ticket=" + ticket),
                      new WebSocket.Listener() {})
                  .get(5, TimeUnit.SECONDS));
      socket.sendText("ping", true).join();
      assertEquals("pong", notifications.poll(5, TimeUnit.SECONDS));
      long c =
          number(
              call(
                  "创建客服会话",
                  "POST",
                  "/conversations",
                  reader.token(),
                  Map.of("targetMemberId", staff.id(), "subject", "借阅咨询"),
                  200,
                  true),
              "$.data.id");
      call("聚合查询会话", "GET", "/conversations?id=" + c, staff.token(), null, 200, true);
      String clientId = UUID.randomUUID().toString();
      Map<String, Object> body =
          Map.of("conversationId", c, "body", "请问借阅规则", "clientMessageId", clientId);
      long message =
          number(call("发送消息", "POST", "/messages", reader.token(), body, 200, true), "$.data.id");
      String event = notifications.poll(15, TimeUnit.SECONDS);
      assertNotNull(event, "RabbitMQ消费者必须自动触发WebSocket提醒");
      assertEquals(message, number(event, "$.messageId"));
      report.add(Map.of("case", "真实WebSocket实时提醒", "event", JsonUtils.fromJson(event, Map.class)));
      call("重复请求返回原消息", "POST", "/messages", reader.token(), body, 200, true);
      call(
          "重复键不同内容冲突",
          "POST",
          "/messages",
          reader.token(),
          Map.of("conversationId", c, "body", "不同内容", "clientMessageId", clientId),
          409,
          true);
      call("聚合查询未读", "GET", "/messages?box=INBOX&read=false", staff.token(), null, 200, true);
      call("第二次未读索引查询", "GET", "/messages?box=INBOX&read=false", staff.token(), null, 200, true);
      String namespace =
          (String) org.springframework.test.util.ReflectionTestUtils.getField(index, "namespace");
      String indexKey = namespace + ":unread:" + staff.id();
      assertNotNull(redis.opsForZSet().score(indexKey, "" + message), "真实HTTP未读查询应建立Redis索引");
      call("收件人标记已读", "POST", "/messages/" + message + "/read", staff.token(), null, 200, true);
      assertNull(redis.opsForZSet().score(indexKey, "" + message));
      assertTrue(Boolean.TRUE.equals(redis.hasKey(indexKey + ":read:" + message)));
      index.add(staff.id(), message);
      assertNull(redis.opsForZSet().score(indexKey, "" + message), "迟到事件不能复活已读索引");
      delayedIndexAfterHttpRead(reader, staff, c, indexKey);
      concurrentHttpMessages(reader, staff, c);
      call("非收件人越界", "POST", "/messages/" + message + "/read", offline.token(), null, 403, true);
      call(
          "馆员广播包含离线成员",
          "POST",
          "/messages",
          staff.token(),
          Map.of(
              "recipientIds",
              List.of(reader.id(), offline.id()),
              "body",
              "图书馆通知",
              "clientMessageId",
              UUID.randomUUID().toString()),
          200,
          true);
      String inbox =
          call(
              "离线成员重新查询",
              "GET",
              "/messages?box=INBOX&read=false",
              offline.token(),
              null,
              200,
              true);
      assertEquals(1, number(inbox, "$.data.total"));
      call(
          "读者广播拒绝",
          "POST",
          "/messages",
          reader.token(),
          Map.of(
              "recipientIds",
              List.of(offline.id()),
              "body",
              "拒绝",
              "clientMessageId",
              UUID.randomUUID().toString()),
          403,
          true);
      call(
          "关闭会话",
          "PUT",
          "/conversations/" + c,
          reader.token(),
          Map.of("status", "CLOSED", "expectedVersion", 0),
          200,
          true);
      call(
          "旧会话版本冲突",
          "PUT",
          "/conversations/" + c,
          reader.token(),
          Map.of("status", "OPEN", "expectedVersion", 0),
          409,
          true);
      call(
          "关闭后不能发送",
          "POST",
          "/messages",
          reader.token(),
          Map.of(
              "conversationId", c, "body", "已关闭", "clientMessageId", UUID.randomUUID().toString()),
          409,
          true);
      // 等待所有Outbox自动发布，不手工调用定时方法。
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      int pending;
      do {
        pending =
            jdbc.queryForObject(
                "select count(*) from outbox_event where status<>'PUBLISHED' and message_id in "
                    + "(select id from message where sender_member_id in (?,?))",
                Integer.class,
                reader.id(),
                staff.id());
        if (pending == 0) break;
        Thread.sleep(100);
      } while (System.nanoTime() < deadline);
      assertEquals(0, pending, "Outbox应全部被自动发布确认");
      report.add(Map.of("case", "Outbox自动发布与确认", "pending", pending));
    } finally {
      if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "test done").join();
      for (Long id : members) sessions.deleteAllForMember(id);
      // 只清理本次唯一测试成员及其消息，不删除真实成员的数据。
      for (Long id : members) {
        jdbc.update(
            "delete from outbox_event where message_id in (select id from message where"
                + " sender_member_id=?)",
            id);
        jdbc.update(
            "delete from message_recipient where message_id in (select id from message where"
                + " sender_member_id=?)",
            id);
        jdbc.update("delete from message where sender_member_id=?", id);
        jdbc.update(
            "delete from conversation where participant_one_id=? or participant_two_id=?", id, id);
        jdbc.update("delete from member where id=?", id);
      }
      Files.createDirectories(Path.of("target/communication"));
      Files.writeString(
          Path.of("target/communication/http-results.json"), JsonUtils.toJson(report));
    }
  }

  long number(String json, String path) {
    return ((Number) JsonPath.read(json, path)).longValue();
  }

  /** 真正暂停索引消费者制造积压，HTTP先已读，再恢复消费并投递重复事件。 */
  void delayedIndexAfterHttpRead(
      Identity sender, Identity recipient, long conversation, String indexKey) throws Exception {
    var container = listeners.getListenerContainer("communication-index");
    assertNotNull(container);
    container.stop();
    try {
      long id =
          number(
              call(
                  "消费者暂停时发送消息",
                  "POST",
                  "/messages",
                  sender.token(),
                  Map.of(
                      "conversationId",
                      conversation,
                      "body",
                      "先读后消费",
                      "clientMessageId",
                      UUID.randomUUID().toString()),
                  200,
                  true),
              "$.data.id");
      // 等待真实Outbox发布到RabbitMQ；消费者停止，任务仍留在队列中。
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      while (!"PUBLISHED"
          .equals(
              jdbc.queryForObject(
                  "select status from outbox_event where message_id=?", String.class, id))) {
        assertTrue(System.nanoTime() < deadline, "积压事件应已发布到真实RabbitMQ");
        Thread.sleep(100);
      }
      long queued =
          rabbit.execute(
              channel ->
                  (long)
                      channel
                          .queueDeclarePassive(
                              com.yunfeil161.ailibrary.communication.event.service
                                  .CommunicationMessaging.INDEX_QUEUE)
                          .getMessageCount());
      assertTrue(queued > 0, "真实索引队列必须存在尚未处理的事件");
      call(
          "积压尚未消费时HTTP标记已读",
          "POST",
          "/messages/" + id + "/read",
          recipient.token(),
          null,
          200,
          true);
      assertNotNull(
          jdbc.queryForObject(
              "select read_at from message_recipient where message_id=? and recipient_member_id=?",
              java.sql.Timestamp.class,
              id,
              recipient.id()));
      assertNull(redis.opsForZSet().score(indexKey, "" + id));
      // 连Redis已读标记也删掉，证明MySQL已读状态本身即可阻止迟到事件写入。
      redis.delete(indexKey + ":read:" + id);
      CountDownLatch processed = new CountDownLatch(3);
      org.mockito.Mockito.doAnswer(
              invocation -> {
                Object result = invocation.callRealMethod();
                processed.countDown();
                return result;
              })
          .when(index)
          .add(recipient.id(), id);
      String payload =
          jdbc.queryForObject(
              "select payload_json from outbox_event where message_id=?", String.class, id);
      for (int i = 0; i < 2; i++) {
        rabbit.send(
            "",
            com.yunfeil161.ailibrary.communication.event.service.CommunicationMessaging.INDEX_QUEUE,
            new org.springframework.amqp.core.Message(
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      }
      org.mockito.Mockito.clearInvocations(redis);
      container.start();
      assertTrue(processed.await(15, TimeUnit.SECONDS), "真实消费者必须处理原事件及两次重复投递");
      awaitIndexQueueDrained();
      assertNull(redis.opsForZSet().score(indexKey, "" + id));
      assertFalse(Boolean.TRUE.equals(redis.hasKey(indexKey + ":read:" + id)));
      // add在数据库已读检查处就返回，连索引Lua都不能执行。
      org.mockito.Mockito.verify(redis, org.mockito.Mockito.never())
          .execute(
              org.mockito.ArgumentMatchers.any(
                  org.springframework.data.redis.core.script.RedisScript.class),
              org.mockito.ArgumentMatchers.eq(List.of(indexKey, indexKey + ":read:" + id)),
              org.mockito.ArgumentMatchers.any(Object[].class));
      report.add(
          Map.of(
              "case",
              "真实积压消息先已读后消费及重复投递",
              "queuedBeforeRead",
              queued,
              "consumerCalls",
              3,
              "redisWriteCalls",
              0,
              "queueReadyAndUnacked",
              0,
              "messageId",
              id));
    } finally {
      if (!container.isRunning()) container.start();
    }
  }

  /** 通过真实Broker管理接口确认队列没有待处理或未ACK消息，不仅检查Java方法被调用。 */
  void awaitIndexQueueDrained() throws Exception {
    String credential = "itcast:" + System.getenv("RABBITMQ_PASSWORD");
    String authorization =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(credential.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      var request =
          HttpRequest.newBuilder(
                  URI.create(
                      "http://localhost:25672/api/queues/%2Fai-library/library.communication.index"))
              .header("Authorization", authorization)
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), "应能查询真实Broker队列状态");
      Map<?, ?> state = JsonUtils.fromJson(response.body(), Map.class);
      if (state.get("messages_ready") instanceof Number ready
          && ready.longValue() == 0
          && state.get("messages_unacknowledged") instanceof Number unacked
          && unacked.longValue() == 0) return;
      Thread.sleep(200);
    }
    fail("索引队列中的原事件和重复投递应处理完并ACK");
  }

  /** 真实HTTP与MySQL验证，不能只依赖H2中直接调用Service的并发结果。 */
  void concurrentHttpMessages(Identity reader, Identity staff, long conversation) throws Exception {
    for (boolean repeated : List.of(true, false)) {
      long previous =
          jdbc.queryForObject(
              "select last_sequence from conversation where id=?", Long.class, conversation);
      String commonClientId = UUID.randomUUID().toString();
      var executor = Executors.newFixedThreadPool(10);
      CountDownLatch ready = new CountDownLatch(10);
      CountDownLatch start = new CountDownLatch(1);
      try {
        var futures = new ArrayList<Future<String>>();
        for (int i = 0; i < 10; i++) {
          Identity sender = repeated || i % 2 == 0 ? reader : staff;
          String clientId = repeated ? commonClientId : UUID.randomUUID().toString();
          futures.add(
              executor.submit(
                  () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS))
                      throw new IllegalStateException("HTTP并发起跑超时");
                    return call(
                        "并发HTTP发送",
                        "POST",
                        "/messages",
                        sender.token(),
                        Map.of(
                            "conversationId",
                            conversation,
                            "body",
                            "真实HTTP并发消息",
                            "clientMessageId",
                            clientId),
                        200,
                        false);
                  }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        Set<Long> ids = new HashSet<>();
        Set<Long> sequences = new HashSet<>();
        for (var future : futures) {
          String response = future.get(20, TimeUnit.SECONDS);
          ids.add(number(response, "$.data.id"));
          sequences.add(number(response, "$.data.sequence"));
        }
        long expected = repeated ? 1 : 10;
        assertEquals(expected, ids.size());
        assertEquals(expected, sequences.size());
        assertEquals(
            java.util.stream.LongStream.rangeClosed(previous + 1, previous + expected)
                .boxed()
                .collect(java.util.stream.Collectors.toSet()),
            sequences);
        assertEquals(
            previous + expected,
            jdbc.queryForObject(
                "select last_sequence from conversation where id=?", Long.class, conversation));
        assertEquals(
            expected,
            jdbc.queryForObject(
                "select count(*) from outbox_event where message_id in (select id from message"
                    + " where conversation_id=? and sequence>?)",
                Long.class,
                conversation,
                previous));
        report.add(
            Map.of(
                "case",
                repeated ? "10个真实HTTP并发幂等重试" : "10个真实HTTP双向并发发送",
                "requests",
                10,
                "uniqueMessages",
                ids.size(),
                "uniqueSequences",
                sequences.size(),
                "outboxEvents",
                expected));
      } finally {
        start.countDown();
        executor.shutdownNow();
      }
    }
  }
}
