package com.yunfeil161.ailibrary.config.security;

import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 交付环境显式使用prod时拒绝示例凭据、关闭认证或root数据库账户。 */
@Component
@Profile("prod")
public class ProductionSafetyChecks implements InitializingBean {

  private final Environment environment;

  public ProductionSafetyChecks(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    if (
      !Boolean.parseBoolean(environment.getProperty("jwt.interceptor-enabled"))
    ) fail("生产环境必须开启JWT认证");
    String secret = required("jwt.secret");
    if (
      secret.equals("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=") ||
      Decoders.BASE64.decode(secret).length < 32
    ) fail("生产环境必须配置独立的随机JWT密钥");
    if ("root".equalsIgnoreCase(required("spring.datasource.username"))) fail(
      "生产数据库必须使用专用低权限账户"
    );
    required("spring.datasource.password");
    required("spring.data.redis.password");
    required("spring.mail.username");
    required("spring.mail.password");
    if (
      Boolean.parseBoolean(
        environment.getProperty("library.communication.mq-enabled", "true")
      )
    ) {
      required("spring.rabbitmq.username");
      required("spring.rabbitmq.password");
    }
    // 咨询和业务助手也共用此凭据；关闭AI推荐不能绕过内部服务认证要求。
    if (required("library.recommendation.ai-token").length() < 24) fail(
      "AI内部服务凭据至少24位"
    );
  }

  private String required(String key) {
    String value = environment.getProperty(key, "");
    if (value.isBlank() || value.startsWith("REPLACE")) fail(
      "生产配置缺失或仍为示例值：" + key
    );
    return value;
  }

  private void fail(String message) {
    throw new IllegalStateException(message);
  }
}
