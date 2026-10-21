package com.yunfeil161.ailibrary.config.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 正式配置错误时必须阻止启动；测试不依赖真实生产凭据或外部服务。 */
class ProductionSafetyChecksTests {

  MockEnvironment valid() {
    byte[] secret = new byte[32];
    new java.security.SecureRandom().nextBytes(secret);
    return new MockEnvironment()
      .withProperty("jwt.interceptor-enabled", "true")
      .withProperty("jwt.secret", Base64.getEncoder().encodeToString(secret))
      .withProperty("spring.datasource.username", "library_app")
      .withProperty("spring.datasource.password", "test-only-password")
      .withProperty("spring.data.redis.password", "test-only-redis")
      .withProperty("spring.mail.username", "test@example.test")
      .withProperty("spring.mail.password", "test-only-mail")
      .withProperty("spring.rabbitmq.username", "app")
      .withProperty("spring.rabbitmq.password", "test-only-mq")
      .withProperty(
        "library.recommendation.ai-token",
        "test-only-internal-token-at-least-24-chars"
      );
  }

  @Test
  void correctlyConfiguredProductionCanStart() {
    assertDoesNotThrow(() ->
      new ProductionSafetyChecks(valid()).afterPropertiesSet()
    );
  }

  @Test
  void sampleSecretAndDisabledAuthenticationAreRejected() {
    for (var env : java.util.List.of(
      valid().withProperty("jwt.interceptor-enabled", "false"),
      valid().withProperty(
        "jwt.secret",
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
      )
    ))
      assertThrows(IllegalStateException.class, () ->
        new ProductionSafetyChecks(env).afterPropertiesSet()
      );
  }

  @Test
  void rootAndMissingOrPlaceholderCredentialsAreRejected() {
    for (var env : java.util.List.of(
      valid().withProperty("spring.datasource.username", "root"),
      valid().withProperty("spring.data.redis.password", ""),
      valid().withProperty("spring.mail.password", "REPLACE_ME"),
      valid()
        .withProperty("library.recommendation.ai-enabled", "false")
        .withProperty("library.recommendation.ai-token", "short")
    ))
      assertThrows(IllegalStateException.class, () ->
        new ProductionSafetyChecks(env).afterPropertiesSet()
      );
  }
}
