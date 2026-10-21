package com.yunfeil161.ailibrary.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCreateRequest;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import com.yunfeil161.ailibrary.identity.application.service.MemberChangeApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** 只等待Spring调度线程执行，禁止通过直接调用任务方法伪造自动执行证据。 */
@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:change_expiration_automatic;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "library.member.change-application-expire-cron=*/1 * * * * *",
  }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberChangeExpirationAutomaticTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MemberChangeApplicationService applications;

  @Test
  void scheduledThreadAutomaticallyExpiresApplicationAndRestoresQualification()
    throws Exception {
    jdbc.update(
      "insert into member(name,email,password_hash,member_type,role_id,account_status,qualification_status,email_verified_at,accepted_rule_version,permanent_borrow_limit,borrowed_book_count,version) select '自动到期测试','automatic@expiry.test','hash','READER',id,'ACTIVE','NOT_APPLIED',current_timestamp,'BORROWING-1',5,0,0 from role where code='READER'"
    );
    Long member = jdbc.queryForObject(
      "select id from member where email='automatic@expiry.test'",
      Long.class
    );
    Long role = jdbc.queryForObject(
      "select id from role where code='READER'",
      Long.class
    );
    long id;
    LoginUserContext.set(
      new LoginSession(
        "automatic",
        member,
        role,
        MemberType.READER,
        "READER",
        "automatic@expiry.test",
        "自动测试"
      )
    );
    try {
      var request = new MemberChangeCreateRequest();
      request.setApplicationType(MemberChangeType.READER_QUALIFICATION);
      request.setExpectedMemberVersion(0L);
      request.setAcceptedRuleVersion("BORROWING-1");
      request.setReason("自动定时到期测试");
      id = applications.create(request).getId();
    } finally {
      LoginUserContext.clear();
    }
    jdbc.update(
      "update member_change_application set expires_at=dateadd('SECOND',-1,current_timestamp) where id=?",
      id
    );
    long deadline = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < deadline) {
      if (
        "EXPIRED".equals(
          jdbc.queryForObject(
            "select status from member_change_application where id=?",
            String.class,
            id
          )
        )
      ) {
        assertEquals(
          "NOT_APPLIED",
          jdbc.queryForObject(
            "select qualification_status from member where id=?",
            String.class,
            member
          )
        );
        assertEquals(
          1,
          jdbc.queryForObject(
            "select count(*) from message where notification_key=?",
            Integer.class,
            "member-change:" + id + ":expired"
          )
        );
        System.out.println(
          "[资料申请自动调度] PENDING → EXPIRED，资格恢复NOT_APPLIED，通知1条"
        );
        return;
      }
      Thread.sleep(100);
    }
    fail("10秒内未观察到Spring定时任务自动完成过期处理");
  }
}
