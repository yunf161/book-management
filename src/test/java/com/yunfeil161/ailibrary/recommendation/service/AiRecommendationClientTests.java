package com.yunfeil161.ailibrary.recommendation.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

/** 真正使用本机HTTP连接验证客户端错误处理，不依赖Ollama或外部服务。 */
class AiRecommendationClientTests {

  AiRecommendationClient client;
  HttpServer server;
  String response;
  int status;
  boolean slow;

  @BeforeEach
  void setup() throws Exception {
    response = "{\"items\":[{\"bookId\":1,\"reason\":\"符合兴趣\"}]}";
    status = 200;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/rank", exchange -> {
      assertEquals(
        "Bearer test-token",
        exchange.getRequestHeaders().getFirst("Authorization")
      );
      try {
        if (slow) Thread.sleep(1600);
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      } catch (java.io.IOException ignored) {
        // 客户端超时主动断开属于本用例的预期结果。
      } finally {
        exchange.close();
      }
    });
    server.start();
    client = new AiRecommendationClient();
    ReflectionTestUtils.setField(client, "enabled", true);
    ReflectionTestUtils.setField(client, "token", "test-token");
    ReflectionTestUtils.setField(client, "timeout", 1L);
    ReflectionTestUtils.setField(
      client,
      "url",
      "http://127.0.0.1:" + server.getAddress().getPort() + "/rank"
    );
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void validJson() {
    assertEquals(1L, client.rerank(Map.of()).items().get(0).bookId());
  }

  @Test
  void malformedJsonAndProtocolTypesFallBack() {
    for (String invalid : new String[] {
      "bad json",
      "null",
      "{\"items\":null}",
      "{\"items\":[{\"bookId\":\"1\",\"reason\":\"x\"}]}",
      "{\"items\":[{\"bookId\":1.2,\"reason\":\"x\"}]}",
      "{\"items\":[{\"bookId\":1,\"reason\":\"x\",\"score\":100}]}",
    }) {
      response = invalid;
      assertNull(client.rerank(Map.of()), invalid);
    }
  }

  @Test
  void busyAndFailureFallBack() {
    for (int code : new int[] { 401, 502, 503 }) {
      status = code;
      assertNull(client.rerank(Map.of()));
    }
  }

  @Test
  void timeoutCoversEntireRequest() {
    slow = true;
    assertTimeout(java.time.Duration.ofMillis(1500), () ->
      assertNull(client.rerank(Map.of()))
    );
  }

  @Test
  void disabledAndUnconfiguredSkipNetwork() {
    ReflectionTestUtils.setField(client, "enabled", false);
    assertNull(client.rerank(Map.of()));
    ReflectionTestUtils.setField(client, "enabled", true);
    ReflectionTestUtils.setField(client, "token", "");
    assertNull(client.rerank(Map.of()));
  }

  @Test
  void stoppedServiceFallsBack() {
    server.stop(0);
    assertNull(client.rerank(Map.of()));
  }
}
