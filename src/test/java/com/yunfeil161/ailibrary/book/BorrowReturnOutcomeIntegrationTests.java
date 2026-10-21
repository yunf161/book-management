package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.enums.*;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowRecordService;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** 真实事务和资源锁验证三种归还结果、状态联动、权限、回滚和重复结案。 */
@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:return_outcome;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "library.borrow.overdue-sync-cron=-",
  }
)
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BorrowReturnOutcomeIntegrationTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  BorrowRecordService records;

  @Autowired
  MockMvc mvc;

  @MockitoSpyBean
  CacheClient cache;

  long member, staff, book, copy, record;
  String barcode;

  @BeforeEach
  void prepare() {
    String tag = UUID.randomUUID().toString();
    member = member(tag + "r", "READER", 1);
    staff = member(tag + "s", "LIBRARIAN", 0);
    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      tag.substring(0, 30),
      tag
    );
    Long category = jdbc.queryForObject(
      "select id from book_category where name=?",
      Long.class,
      tag
    );
    jdbc.update(
      "insert into book(category_id,title,author,status,version) values(?,?,?,'ACTIVE',0)",
      category,
      tag,
      "测试作者"
    );
    book = jdbc.queryForObject(
      "select id from book where title=?",
      Long.class,
      tag
    );
    barcode = "RETURN-" + tag;
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) values(?,?,'测试馆藏','BORROWED',0)",
      book,
      barcode
    );
    copy = jdbc.queryForObject(
      "select id from book_copy where barcode=?",
      Long.class,
      barcode
    );
    // 已逾期25小时，结案应计两天。直接准备借出状态，HTTP验收另走真实申请和审批流程。
    jdbc.update(
      "insert into borrow_record(application_id,member_id,copy_id,borrowed_at,due_at,status,overdue_days,approved_by,version) values(?,?,?,dateadd('DAY',-5,current_timestamp),dateadd('HOUR',-25,current_timestamp),'OVERDUE',0,?,0)",
      copy,
      member,
      copy,
      staff
    );
    record = jdbc.queryForObject(
      "select id from borrow_record where copy_id=?",
      Long.class,
      copy
    );
    login(staff, "LIBRARIAN");
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
    reset(cache);
    cache.delete(BookCacheKeys.BORROW_RECORD_BY_ID + record);
  }

  @ParameterizedTest
  @EnumSource(BorrowReturnOutcome.class)
  void outcomesPersistAndUpdateCopyAndQuotaAtomically(
    BorrowReturnOutcome outcome
  ) {
    var result = records.returnByRecord(record, request(outcome, 0L));
    assertEquals(outcome, result.getReturnOutcome());
    assertEquals(
      outcome == BorrowReturnOutcome.LOST
        ? BorrowRecordStatus.LOST
        : BorrowRecordStatus.RETURNED,
      result.getStatus()
    );
    assertEquals(2, result.getOverdueDays());
    assertNotNull(result.getReturnedAt());
    assertEquals(staff, result.getReturnedBy());
    assertEquals("测试书况", result.getReturnConditionNote());
    assertEquals(copyStatus(outcome), rowStatus("book_copy", copy));
    assertEquals(0, borrowedCount());
    assertEquals(1L, result.getVersion());
    assertThrows(ConflictException.class, () ->
      records.returnByRecord(record, request(outcome, 1L))
    );
    assertEquals(0, borrowedCount());
    // 历史结果查询不会依赖副本当前状态，按ID缓存同样包含本次结果。
    var query = new BorrowRecordQueryRequest();
    query.setId(record);
    assertEquals(
      outcome,
      records.queryAdmin(query).getRows().get(0).getReturnOutcome()
    );
    assertEquals(
      outcome,
      records.queryAdmin(query).getRows().get(0).getReturnOutcome()
    );
    query.setReturnOutcome(outcome);
    assertEquals(1L, records.queryAdmin(query).getTotal());
    query.setReturnOutcome(
      outcome == BorrowReturnOutcome.NORMAL
        ? BorrowReturnOutcome.LOST
        : BorrowReturnOutcome.NORMAL
    );
    assertThrows(NotFoundException.class, () -> records.queryAdmin(query));
    query.setId(null);
    query.setReturnOutcome(null);
    query.setMemberId(member);
    query.setCurrentOnly(true);
    assertEquals(0L, records.queryAdmin(query).getTotal());
  }

  @ParameterizedTest
  @EnumSource(BorrowReturnOutcome.class)
  void barcodeRouteAcceptsSameOutcomes(BorrowReturnOutcome outcome)
    throws Exception {
    mvc
      .perform(
        post("/admin/book-copies/{barcode}/return", barcode)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"returnOutcome\":\"" +
              outcome +
              "\",\"returnConditionNote\":\"扫码结案\"}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.returnOutcome").value(outcome.name()));
    assertEquals(copyStatus(outcome), rowStatus("book_copy", copy));
    assertEquals(0, borrowedCount());
  }

  @Test
  void oldRequestDefaultsToNormal() {
    assertEquals(
      BorrowReturnOutcome.NORMAL,
      records.returnByRecord(record, request(null, 0L)).getReturnOutcome()
    );
    assertEquals("AVAILABLE", rowStatus("book_copy", copy));
  }

  @Test
  void staleVersionCannotCloseOrChangeStock() {
    assertThrows(ConflictException.class, () ->
      records.returnByRecord(record, request(BorrowReturnOutcome.LOST, 9L))
    );
    assertOpen();
  }

  @Test
  void invalidOutcomeAndReaderPrivilegeAreRejected() throws Exception {
    mvc
      .perform(
        post("/admin/borrow-records/{id}/return", record)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"expectedVersion\":0,\"returnOutcome\":\"BORROWED\"}")
      )
      .andExpect(status().isBadRequest());
    login(member, "READER");
    mvc
      .perform(
        post("/admin/borrow-records/{id}/return", record)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"expectedVersion\":0,\"returnOutcome\":\"LOST\"}")
      )
      .andExpect(status().isForbidden());
    assertOpen();
  }

  @Test
  void cacheFailureRollsBackAllThreeRecords() {
    doThrow(new ServiceUnavailableException("测试缓存删除失败"))
      .when(cache)
      .deleteRequired(anyString());
    assertThrows(ServiceUnavailableException.class, () ->
      records.returnByRecord(record, request(BorrowReturnOutcome.DAMAGED, 0L))
    );
    assertOpen();
    reset(cache);
    assertEquals(
      BorrowReturnOutcome.DAMAGED,
      records
        .returnByRecord(record, request(BorrowReturnOutcome.DAMAGED, 0L))
        .getReturnOutcome()
    );
  }

  @Test
  void concurrentDifferentOutcomesSettleExactlyOnce() throws Exception {
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(6);
    try {
      var results = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 6; i++) {
        var outcome = BorrowReturnOutcome.values()[i % 3];
        results.add(
          pool.submit(() -> {
            login(staff, "LIBRARIAN");
            try {
              start.await();
              records.returnByRecord(record, request(outcome, 0L));
              return true;
            } catch (ConflictException expected) {
              return false;
            } finally {
              LoginUserContext.clear();
            }
          })
        );
      }
      start.countDown();
      int successes = 0;
      for (var r : results) if (r.get(20, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(0, borrowedCount());
    assertEquals(
      1L,
      jdbc.queryForObject(
        "select version from borrow_record where id=?",
        Long.class,
        record
      )
    );
    var outcome = BorrowReturnOutcome.valueOf(
      jdbc.queryForObject(
        "select return_outcome from borrow_record where id=?",
        String.class,
        record
      )
    );
    assertEquals(copyStatus(outcome), rowStatus("book_copy", copy));
  }

  private BorrowReturnRequest request(
    BorrowReturnOutcome outcome,
    Long version
  ) {
    var request = new BorrowReturnRequest();
    request.setReturnOutcome(outcome);
    request.setReturnConditionNote(" 测试书况 ");
    request.setExpectedVersion(version);
    return request;
  }

  private String copyStatus(BorrowReturnOutcome outcome) {
    return switch (outcome) {
      case NORMAL -> "AVAILABLE";
      case DAMAGED -> "MAINTENANCE";
      case LOST -> "LOST";
    };
  }

  private int borrowedCount() {
    return jdbc.queryForObject(
      "select borrowed_book_count from member where id=?",
      Integer.class,
      member
    );
  }

  private String rowStatus(String table, long id) {
    return jdbc.queryForObject(
      "select status from " + table + " where id=?",
      String.class,
      id
    );
  }

  private void assertOpen() {
    assertEquals("OVERDUE", rowStatus("borrow_record", record));
    assertEquals("BORROWED", rowStatus("book_copy", copy));
    assertEquals(1, borrowedCount());
    assertNull(
      jdbc.queryForObject(
        "select return_outcome from borrow_record where id=?",
        String.class,
        record
      )
    );
  }

  private long member(String tag, String role, int borrowed) {
    jdbc.update(
      "insert into member(name,email,member_type,role_id,account_status,qualification_status,borrowed_book_count,permanent_borrow_limit,version) select ?,?,?,id,'ACTIVE','APPROVED',?,5,0 from role where code=?",
      tag,
      tag + "@return.test",
      role.equals("READER") ? "READER" : "EMPLOYEE",
      borrowed,
      role
    );
    return jdbc.queryForObject(
      "select id from member where name=?",
      Long.class,
      tag
    );
  }

  private void login(long id, String role) {
    Long roleId = jdbc.queryForObject(
      "select id from role where code=?",
      Long.class,
      role
    );
    LoginUserContext.set(
      new LoginSession(
        "return-test",
        id,
        roleId,
        role.equals("READER") ? MemberType.READER : MemberType.EMPLOYEE,
        role,
        "return@test",
        "测试"
      )
    );
  }
}
