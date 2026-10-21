package com.yunfeil161.ailibrary.auth;

import com.yunfeil161.ailibrary.auth.dto.RegisterParam;
import com.yunfeil161.ailibrary.auth.service.LoginService;
import com.yunfeil161.ailibrary.common.exception.UnprocessableEntityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 邮箱验证码重置密码的数据库与 Redis 集成测试。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ResetPasswordIntegrationTests {

  @Autowired
  private LoginService loginService;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private StringRedisTemplate redis;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private String codeKey;
  private String rateKey;

  @AfterEach
  void clearRedis() {
    if (codeKey != null) {
      redis.delete(codeKey);
      redis.delete(rateKey);
    }
  }

  @Test
  void resetPasswordRequiresCorrectCodeAndConsumesItAfterSuccess() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String email = "reset." + suffix + "@example.test";
    Long roleId = jdbc.queryForObject(
      "select id from `role` where code='READER'",
      Long.class
    );
    jdbc.update(
      """
      insert into member(
          member_type, role_id, name, email, password_hash,
          account_status, qualification_status, version
      ) values ('READER', ?, ?, ?, ?, 'ACTIVE', 'NOT_APPLIED', 0)
      """,
      roleId,
      "重置密码测试",
      email,
      passwordEncoder.encode("OldPassword123!")
    );

    codeKey = "auth:register:code:" + email;
    rateKey = "auth:register:rate:" + email;
    redis.opsForValue().set(codeKey, "123456");
    redis.opsForValue().set(rateKey, "1");

    RegisterParam wrong = resetParam(email, "NewPassword123!", "654321");
    assertThrows(UnprocessableEntityException.class, () -> loginService.resetPassword(wrong));

    loginService.resetPassword(resetParam(email, "NewPassword123!", "123456"));

    String hash = jdbc.queryForObject(
      "select password_hash from member where email=?",
      String.class,
      email
    );
    assertTrue(passwordEncoder.matches("NewPassword123!", hash));
    assertNull(redis.opsForValue().get(codeKey));
    assertNull(redis.opsForValue().get(rateKey));
  }

  private RegisterParam resetParam(String email, String password, String code) {
    RegisterParam param = new RegisterParam();
    param.setEmail(email);
    param.setPassword(password);
    param.setCode(code);
    return param;
  }
}

