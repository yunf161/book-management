package com.yunfeil161.ailibrary.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.yunfeil161.ailibrary.ai.business.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

/** 使用真实HTTP验证超长响应被中止，并保持不自动重试的业务边界。 */
class BoundedBusinessHttpTests {

  HttpServer server;
  BusinessHttpClient client;
  final BusinessSkillRegistry.Skill skill = new BusinessSkillRegistry().require(
    "plan_create"
  );

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    client = new BusinessHttpClient();
    ReflectionTestUtils.setField(
      client,
      "baseUrl",
      "http://127.0.0.1:" + server.getAddress().getPort()
    );
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void normalResponseAndRefreshedTokenArePreserved() {
    server.createContext("/ok", exchange -> {
      byte[] data = "{\"code\":200,\"data\":{\"id\":1}}".getBytes(
        StandardCharsets.UTF_8
      );
      exchange.getResponseHeaders().add("X-Refreshed-Token", "rotated");
      exchange.sendResponseHeaders(200, data.length);
      exchange.getResponseBody().write(data);
      exchange.close();
    });
    var result = client.execute(skill, "/ok", Map.of(), "test-token");
    assertTrue(result.known());
    assertEquals(200, result.code());
    assertEquals("rotated", result.refreshedToken());
  }

  @Test
  void oversizedBodyBecomesUnknownAndIsNotRetried() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    server.createContext("/large", exchange -> {
      calls.incrementAndGet();
      exchange.sendResponseHeaders(200, 0);
      try {
        byte[] chunk = new byte[65536];
        for (int i = 0; i < 20; i++) exchange.getResponseBody().write(chunk);
      } catch (java.io.IOException expectedCancellation) {
      } finally {
        exchange.close();
      }
    });
    var result = client.execute(skill, "/large", Map.of(), "test-token");
    assertFalse(result.known());
    assertEquals(503, result.code());
    assertEquals(1, calls.get());
  }

  @Test
  void serverErrorAndMalformedResponsesCannotBecomeSuccessfulActions() {
    server.createContext("/error", exchange -> {
      byte[] data = "{\"code\":500}".getBytes();
      exchange.sendResponseHeaders(500, data.length);
      exchange.getResponseBody().write(data);
      exchange.close();
    });
    server.createContext("/invalid", exchange -> {
      byte[] data = "<html>oops</html>".getBytes();
      exchange.sendResponseHeaders(200, data.length);
      exchange.getResponseBody().write(data);
      exchange.close();
    });
    assertFalse(
      client.execute(skill, "/error", Map.of(), "test-token").known()
    );
    assertFalse(
      client.execute(skill, "/invalid", Map.of(), "test-token").known()
    );
  }
}
