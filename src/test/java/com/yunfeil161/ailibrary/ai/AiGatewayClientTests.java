package com.yunfeil161.ailibrary.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.yunfeil161.ailibrary.ai.service.AiGatewayClient;
import com.yunfeil161.ailibrary.common.exception.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

/** 真实HTTP验证内部凭据、当前身份、状态映射、关闭和超时。 */
class AiGatewayClientTests {

  HttpServer server;
  AiGatewayClient client;
  int status = 200;
  boolean slow;

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/assistant/", exchange -> {
      assertEquals(
        "Bearer secret",
        exchange.getRequestHeaders().getFirst("Authorization")
      );
      assertEquals("123", exchange.getRequestHeaders().getFirst("X-Member-Id"));
      if (exchange.getRequestMethod().equals("POST")) {
        assertEquals(
          "user-token",
          exchange.getRequestHeaders().getFirst("X-Library-Token")
        );
        String body = new String(
          exchange.getRequestBody().readAllBytes(),
          StandardCharsets.UTF_8
        );
        assertTrue(body.contains("\"memberId\":123"));
        assertFalse(body.contains("user-token"));
      }
      try {
        if (slow) Thread.sleep(500);
        byte[] body = "{\"messages\":[]}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
      } catch (Exception ignored) {
        // 超时后客户端关闭连接属于预期，不保存或输出内部请求信息。
      } finally {
        exchange.close();
      }
    });
    server.start();
    client = new AiGatewayClient();
    ReflectionTestUtils.setField(
      client,
      "url",
      "http://127.0.0.1:" + server.getAddress().getPort()
    );
    ReflectionTestUtils.setField(client, "secret", "secret");
    ReflectionTestUtils.setField(client, "readTimeout", 100);
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void chatKeepsTokenOutOfModelBody() throws Exception {
    try (var stream = client.chat(123L, "user-token", "查询", null)) {
      assertTrue(stream.body().readAllBytes().length > 0);
    }
  }

  @Test
  void historyAndDeleteStayScopedToMember() {
    assertNotNull(
      client.conversation(123L, UUID.randomUUID().toString(), false)
    );
    assertNotNull(
      client.conversation(123L, UUID.randomUUID().toString(), true)
    );
  }

  @Test
  void statusMappingDoesNotLeakInternalBody() {
    status = 403;
    assertThrows(ForbiddenException.class, () ->
      client.conversation(123L, "id", false)
    );
    status = 404;
    assertThrows(NotFoundException.class, () ->
      client.conversation(123L, "id", false)
    );
    status = 409;
    assertThrows(ConflictException.class, () ->
      client.conversation(123L, "id", false)
    );
    status = 503;
    assertThrows(ServiceUnavailableException.class, () ->
      client.conversation(123L, "id", false)
    );
  }

  @Test
  void timeoutAndMissingConfigurationAreUnavailable() {
    slow = true;
    assertThrows(ServiceUnavailableException.class, () ->
      client.conversation(123L, "id", false)
    );
    ReflectionTestUtils.setField(client, "secret", "");
    assertThrows(ServiceUnavailableException.class, () ->
      client.conversation(123L, "id", false)
    );
  }
}
