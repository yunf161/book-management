package com.yunfeil161.ailibrary.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.yunfeil161.ailibrary.auth.dto.RegisterParam;
import com.yunfeil161.ailibrary.auth.service.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import com.yunfeil161.ailibrary.identity.entity.Member;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 真实Redis Lua和数据库验证：注册不能提权、验证码原子消费、错误次数及密码边界。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:auth_hardening;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@Transactional
class AuthHardeningIntegrationTests {

  @Autowired
  LoginService login;

  @Autowired
  VerificationCodeStore codes;

  @Autowired
  LoginAttemptLimiter attempts;

  @Autowired
  StringRedisTemplate redis;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  PermissionQueryService permissions;

  @MockitoBean
  JavaMailSender mail;

  final String email = "hardening-" + UUID.randomUUID() + "@example.test";

  @AfterEach
  void cleanup() {
    redis.delete(
      List.of(
        "auth:register:code:" + email,
        "auth:register:rate:" + email,
        "auth:register:attempts:" + email
      )
    );
    attempts.succeeded(email);
  }

  @Test
  void selfRegisteredEmployeeGetsNoStaffAuthority() {
    String deptCode = UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
      "insert into department(code,name,description,status,version) values(?,?,?,'ACTIVE',0)",
      deptCode,
      deptCode,
      "test"
    );
    Long department = jdbc.queryForObject(
      "select id from department where code=?",
      Long.class,
      deptCode
    );
    codes.issue(email, "123456");
    var p = param("123456");
    p.setMemberType(MemberType.EMPLOYEE);
    p.setName(deptCode);
    p.setDepartmentId(department);
    p.setPositionName("待任职");
    Long member = login.registerEmployee(p);
    var row = jdbc.queryForMap(
      "select m.member_type,r.code,ep.employment_status from member m join role r on r.id=m.role_id join employee_profile ep on ep.member_id=m.id where m.id=?",
      member
    );
    assertEquals("EMPLOYEE", row.get("member_type"));
    assertEquals("READER", row.get("code"));
    assertEquals("SUSPENDED", row.get("employment_status"));
    Long role = jdbc.queryForObject(
      "select role_id from member where id=?",
      Long.class,
      member
    );
    assertFalse(permissions.hasPermission(role, "book:create"));
    assertFalse(permissions.hasPermission(role, "borrow-application:approve"));
  }

  @Test
  void fiveWrongCodesInvalidateCodeUntilNewIssue() {
    codes.issue(email, "123456");
    for (int i = 0; i < 4; i++) assertThrows(
      UnprocessableEntityException.class,
      () -> codes.consume(email, "654321")
    );
    assertThrows(TooManyRequestsException.class, () ->
      codes.consume(email, "654321")
    );
    assertThrows(TooManyRequestsException.class, () ->
      codes.consume(email, "123456")
    );
    redis.delete("auth:register:rate:" + email); // 模拟60秒发送冷却已经结束。
    codes.issue(email, "234567");
    codes.consume(email, "234567");
    assertNull(redis.opsForValue().get("auth:register:code:" + email));
  }

  @Test
  void concurrentCodeConsumptionHasExactlyOneWinner() throws Exception {
    codes.issue(email, "123456");
    var pool = Executors.newFixedThreadPool(10);
    try {
      var tasks = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 10; i++) tasks.add(() -> {
        try {
          codes.consume(email, "123456");
          return true;
        } catch (UnprocessableEntityException rejected) {
          return false;
        }
      });
      int successes = 0;
      for (var future : pool.invokeAll(tasks)) if (future.get()) successes++;
      assertEquals(1, successes);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void delayedMailFailureCannotDeleteNewerCode() {
    doAnswer(call -> {
      redis.opsForValue().set("auth:register:code:" + email, "999999");
      throw new MailSendException("test failure");
    })
      .when(mail)
      .send(any(SimpleMailMessage.class));
    assertThrows(ServiceUnavailableException.class, () ->
      login.loginCode(email)
    );
    assertEquals(
      "999999",
      redis.opsForValue().get("auth:register:code:" + email)
    );
  }

  @Test
  void normalMailFailureReleasesOnlyOwnCodeAndCooldown() {
    doAnswer(call -> {
      throw new MailSendException("test failure");
    })
      .when(mail)
      .send(any(SimpleMailMessage.class));
    assertThrows(ServiceUnavailableException.class, () ->
      login.loginCode(email)
    );
    assertNull(redis.opsForValue().get("auth:register:code:" + email));
    assertNull(redis.opsForValue().get("auth:register:rate:" + email));
  }

  @Test
  void issuingDuringCooldownDoesNotReplaceCode() {
    codes.issue(email, "123456");
    assertThrows(TooManyRequestsException.class, () ->
      codes.issue(email, "999999")
    );
    assertEquals(
      "123456",
      redis.opsForValue().get("auth:register:code:" + email)
    );
  }

  @Test
  void passwordLimitsFailBeforeConsumingCode() {
    codes.issue(email, "123456");
    for (String password : List.of("short", "密".repeat(25))) {
      var p = param("123456");
      p.setPassword(password);
      assertThrows(WrongFormatException.class, () -> login.resetPassword(p));
    }
    assertEquals(
      "123456",
      redis.opsForValue().get("auth:register:code:" + email)
    );
    assertThrows(UnauthorizedException.class, () -> login.login(email, null));
  }

  @Test
  void loginBudgetIsAtomicAndSuccessfulPasswordClearsIt() throws Exception {
    for (int i = 0; i < 10; i++) attempts.reserve(email);
    assertThrows(TooManyRequestsException.class, () -> attempts.reserve(email));
    attempts.succeeded(email);
    assertDoesNotThrow(() -> attempts.reserve(email));
  }

  @Test
  void passwordHashNeverAppearsInMemberJson() {
    var member = new Member();
    member.setId(1L);
    member.setPasswordHash("private-hash");
    String json = JsonUtils.toJson(member);
    assertFalse(json.contains("passwordHash"));
    assertFalse(json.contains("private-hash"));
  }

  private RegisterParam param(String code) {
    var p = new RegisterParam();
    p.setEmail(email);
    p.setPassword("NewPassword123!");
    p.setCode(code);
    return p;
  }
}
