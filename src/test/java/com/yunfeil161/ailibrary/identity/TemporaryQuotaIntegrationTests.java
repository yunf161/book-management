package com.yunfeil161.ailibrary.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowMemberMapper;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.identity.dto.MemberWriteRequest;
import com.yunfeil161.ailibrary.identity.entity.Member;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import com.yunfeil161.ailibrary.identity.quota.service.TemporaryQuotaScheduler;
import com.yunfeil161.ailibrary.identity.quota.service.TemporaryQuotaService;
import com.yunfeil161.ailibrary.identity.service.MemberService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 验证额度边界、缓存动态计算以及定时清理不覆盖新审批。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:temporary_quota_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@Transactional
class TemporaryQuotaIntegrationTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MemberMapper members;

  @Autowired
  MemberService service;

  @Autowired
  BorrowMemberMapper borrowMembers;

  @Autowired
  TemporaryQuotaService quotas;

  @Autowired
  TemporaryQuotaScheduler scheduler;

  @Autowired
  CacheClient cache;

  Long id;

  @BeforeEach
  void setup() {
    String name = "QUOTA-" + UUID.randomUUID();
    jdbc.update(
      "insert into member(name,member_type,role_id,account_status,qualification_status,accepted_rule_version,permanent_borrow_limit) " +
        "select ?,'READER',id,'ACTIVE','APPROVED','BORROWING-1',2 from role where code='READER'",
      name
    );
    id = jdbc.queryForObject(
      "select id from member where name=?",
      Long.class,
      name
    );
  }

  @AfterEach
  void clearCache() {
    cache.delete("member:reader:" + id);
  }

  private void temporary(int value, LocalDateTime expires) {
    jdbc.update(
      "update member set temporary_borrow_limit=?,temporary_quota_expires_at=? where id=?",
      value,
      expires,
      id
    );
  }

  @Test
  void expiryBoundaryAndCacheSerializationRemainDynamic() {
    Member m = new Member();
    LocalDateTime expiry = LocalDateTime.now();
    m.setPermanentBorrowLimit(2);
    m.setTemporaryBorrowLimit(7);
    m.setTemporaryQuotaExpiresAt(expiry);
    assertEquals(7, m.effectiveBorrowLimitAt(expiry.minusNanos(1)));
    assertEquals(2, m.effectiveBorrowLimitAt(expiry));
    Member restored = JsonUtils.fromJson(JsonUtils.toJson(m), Member.class);
    assertEquals(2, restored.getEffectiveBorrowLimit());
    assertEquals(7, restored.getTemporaryBorrowLimit());
  }

  @Test
  void atomicBorrowUsesTemporaryLimitAndRejectsExpiredLimit() {
    temporary(4, LocalDateTime.now().plusDays(1));
    jdbc.update("update member set borrowed_book_count=2 where id=?", id);
    assertEquals(
      1,
      borrowMembers.incrementBorrowed(id, 0L, LocalDateTime.now())
    );
    temporary(4, LocalDateTime.now().minusSeconds(1));
    assertEquals(
      0,
      borrowMembers.incrementBorrowed(id, 1L, LocalDateTime.now())
    );
    // 已借数量超过长期值是合法到期状态，不妨碍归还。
    assertEquals(1, borrowMembers.decrementBorrowed(id, 1L));
    assertEquals(2, borrowMembers.findById(id).getBorrowedBookCount());
  }

  @Test
  void cleanupPreservesLongTermAndBorrowedCountAndEvictsCache() {
    temporary(5, LocalDateTime.now().minusSeconds(1));
    jdbc.update("update member set borrowed_book_count=4 where id=?", id);
    Reader query = new Reader();
    query.setId(id);
    assertEquals(
      2,
      service
        .getReaders(query, 1, 10)
        .getRows()
        .get(0)
        .getEffectiveBorrowLimit()
    );
    assertTrue(quotas.clearExpired(id, LocalDateTime.now()));
    Member m = members.findMemberById(id);
    assertNull(m.getTemporaryBorrowLimit());
    assertNull(m.getTemporaryQuotaExpiresAt());
    assertEquals(2, m.getPermanentBorrowLimit());
    assertEquals(4, m.getBorrowedBookCount());
    assertEquals(1L, m.getVersion());
    assertFalse(quotas.clearExpired(id, LocalDateTime.now()));
    assertNull(
      service
        .getReaders(query, 1, 10)
        .getRows()
        .get(0)
        .getTemporaryBorrowLimit()
    );
  }

  @Test
  void lateCleanupCannotEraseNewApproval() {
    LocalDateTime scanTime = LocalDateTime.now();
    temporary(5, scanTime.plusDays(2));
    assertFalse(quotas.clearExpired(id, scanTime));
    assertEquals(5, members.findMemberById(id).getTemporaryBorrowLimit());
  }

  @Test
  void permanentEditDuringTemporaryPeriodSurvivesExpiry() {
    temporary(7, LocalDateTime.now().plusDays(1));
    Reader update = new Reader();
    update.setMemberType(MemberType.READER);
    update.setPermanentBorrowLimit(6);
    service.updateReader(id, update, null, 0L);
    assertEquals(7, members.findMemberById(id).getEffectiveBorrowLimit());
    assertTrue(quotas.clearExpired(id, LocalDateTime.now().plusDays(2)));
    assertEquals(6, members.findMemberById(id).getEffectiveBorrowLimit());
  }

  @Test
  void queryQuotaFieldsDoNotGetShortCircuitedByIdCache() {
    Reader query = new Reader();
    query.setId(id);
    service.getReaders(query, 1, 10);
    query.setPermanentBorrowLimit(99);
    assertEquals(0, service.getReaders(query, 1, 10).getTotal());
    query.setPermanentBorrowLimit(2);
    assertEquals(1, service.getReaders(query, 1, 10).getTotal());
  }

  @Test
  void directMaintenanceCannotWriteTemporaryFieldsAndSelfCannotRaiseQuota() {
    Reader update = new Reader();
    update.setMemberType(MemberType.READER);
    update.setTemporaryBorrowLimit(9);
    update.setTemporaryQuotaExpiresAt(LocalDateTime.now().plusDays(1));
    assertThrows(WrongFormatException.class, () ->
      service.updateReader(id, update, null, 0L)
    );
    MemberWriteRequest self = new MemberWriteRequest();
    self.setPermanentBorrowLimit(99);
    self.setTemporaryBorrowLimit(99);
    self.setTemporaryQuotaExpiresAt(LocalDateTime.now().plusDays(1));
    self.retainSelfEditableFields();
    assertNull(self.getPermanentBorrowLimit());
    assertNull(self.getTemporaryBorrowLimit());
    assertNull(self.getTemporaryQuotaExpiresAt());
  }

  @Test
  void scheduledEntryCleansExpiredMember() {
    temporary(5, LocalDateTime.now().minusSeconds(1));
    scheduler.synchronize();
    assertNull(members.findMemberById(id).getTemporaryQuotaExpiresAt());
    assertEquals(2, members.findMemberById(id).getPermanentBorrowLimit());
  }
}
