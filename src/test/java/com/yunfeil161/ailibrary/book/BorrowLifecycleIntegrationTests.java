package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.entity.*;
import com.yunfeil161.ailibrary.book.borrow.enums.*;
import com.yunfeil161.ailibrary.book.borrow.service.*;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import com.yunfeil161.ailibrary.identity.service.MemberService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class BorrowLifecycleIntegrationTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  BorrowApplicationService applications;

  @Autowired
  BorrowRecordService records;

  @Autowired
  BorrowOverdueService overdueService;

  @Autowired
  com.yunfeil161.ailibrary.book.borrow.service.impl.BorrowOverdueScheduler overdueScheduler;

  @Autowired
  MemberService memberService;

  @Autowired
  MockMvc mockMvc;

  @Autowired
  CacheClient cacheClient;

  Long readerId, readerRole, staffId, staffRole, bookId, copyId;
  Long cachedRecordId;
  String barcode;

  @BeforeEach
  void setup() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    readerRole = role("READER");
    staffRole = role("LIBRARIAN");
    jdbc.update(
      "insert into member(member_type,role_id,name,email,account_status,qualification_status,borrowed_book_count,permanent_borrow_limit,version) values('READER',?,?,?,'ACTIVE','APPROVED',0,5,0)",
      readerRole,
      "借阅测试" + s,
      "r" + s + "@test.local"
    );
    jdbc.update("update member set accepted_rule_version='BORROWING-1' where email=?", "r" + s + "@test.local");
    readerId = jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      "r" + s + "@test.local"
    );
    jdbc.update(
      "insert into member(member_type,role_id,name,email,account_status,qualification_status,borrowed_book_count,permanent_borrow_limit,version) values('EMPLOYEE',?,?,?,'ACTIVE','APPROVED',0,5,0)",
      staffRole,
      "馆员测试" + s,
      "s" + s + "@test.local"
    );
    staffId = jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      "s" + s + "@test.local"
    );
    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      "BR" + s,
      "借阅分类" + s
    );
    Long category = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      "BR" + s
    );
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) values(?,?,?,?,?,'ACTIVE',0)",
      category,
      "借阅图书" + s,
      "作者",
      "BI" + s,
      "zh-CN"
    );
    bookId = jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      "BI" + s
    );
    barcode = "BC" + s;
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) values(?,?,?,'AVAILABLE',0)",
      bookId,
      barcode,
      "A区"
    );
    copyId = jdbc.queryForObject(
      "select id from book_copy where barcode=?",
      Long.class,
      barcode
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
    if (cachedRecordId != null) {
      cacheClient.delete(BookCacheKeys.BORROW_RECORD_BY_ID + cachedRecordId);
    }
  }

  @Test
  void aggregatedQueriesApprovalAndReturnFormClosedLoop() {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest cr = new BorrowApplicationCreateRequest();
    cr.setBookId(bookId);
    cr.setRequestedDays(30);
    BorrowApplication app = applications.create(cr);
    BorrowApplicationQueryRequest mine = new BorrowApplicationQueryRequest();
    mine.setMemberId(staffId);
    mine.setId(app.getId());
    assertEquals(
      readerId,
      applications.queryMine(mine).getRows().get(0).getMemberId()
    );
    login(staffId, staffRole, 2);
    BorrowApplicationApproveRequest ar = new BorrowApplicationApproveRequest();
    ar.setApprovedDays(30);
    ar.setExpectedVersion(0L);
    assertEquals(
      BorrowApplicationStatus.APPROVED,
      applications.approve(app.getId(), ar).getStatus()
    );
    BorrowRecordQueryRequest aq = new BorrowRecordQueryRequest();
    aq.setMemberId(readerId);
    BorrowRecordDetailVO record = records.queryAdmin(aq).getRows().get(0);
    assertEquals(BorrowRecordStatus.BORROWING, record.getStatus());
    assertEquals(
      "BORROWED",
      jdbc.queryForObject(
        "select status from book_copy where id=?",
        String.class,
        copyId
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select borrowed_book_count from member where id=?",
        Integer.class,
        readerId
      )
    );
    login(readerId, readerRole, 1);
    BorrowRecordQueryRequest malicious = new BorrowRecordQueryRequest();
    malicious.setMemberId(staffId);
    assertEquals(
      readerId,
      records.queryMine(malicious).getRows().get(0).getMemberId()
    );
    login(staffId, staffRole, 2);
    BorrowReturnRequest rr = new BorrowReturnRequest();
    rr.setExpectedVersion(0L);
    rr.setReturnConditionNote("完好");
    assertEquals(
      BorrowRecordStatus.RETURNED,
      records.returnByRecord(record.getId(), rr).getStatus()
    );
    assertEquals(
      "AVAILABLE",
      jdbc.queryForObject(
        "select status from book_copy where id=?",
        String.class,
        copyId
      )
    );
    assertEquals(
      0,
      jdbc.queryForObject(
        "select borrowed_book_count from member where id=?",
        Integer.class,
        readerId
      )
    );
    assertThrows(ConflictException.class, () ->
      records.returnByRecord(record.getId(), rr)
    );
  }

  @Test
  void cancellationAndPermissionRulesWork() {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest cr = new BorrowApplicationCreateRequest();
    cr.setBookId(bookId);
    cr.setRequestedDays(7);
    BorrowApplication a = applications.create(cr);
    BorrowApplicationCancelRequest cancel =
      new BorrowApplicationCancelRequest();
    cancel.setExpectedVersion(0L);
    assertEquals(
      BorrowApplicationStatus.CANCELLED,
      applications.cancel(a.getId(), cancel).getStatus()
    );
    assertThrows(ConflictException.class, () ->
      applications.cancel(a.getId(), cancel)
    );
    assertThrows(ForbiddenException.class, () ->
      applications.queryAdmin(new BorrowApplicationQueryRequest())
    );
  }

  @Test
  void aggregatedHttpQueriesBindFiltersAndProtectReaderScope()
    throws Exception {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest request =
      new BorrowApplicationCreateRequest();
    request.setBookId(bookId);
    request.setRequestedDays(14);
    BorrowApplication application = applications.create(request);
    mockMvc
      .perform(
        get("/borrow-applications")
          .param("id", application.getId().toString())
          .param("memberId", staffId.toString())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].memberId").value(readerId));
    login(staffId, staffRole, 2);
    mockMvc
      .perform(
        get("/admin/borrow-applications")
          .param("bookId", bookId.toString())
          .param("status", "PENDING")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.total").value(1));
    mockMvc
      .perform(
        get("/admin/borrow-records")
          .param("memberId", readerId.toString())
          .param("currentOnly", "true")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  void borrowQueriesSupportEveryFilterSeparatelyAndInCombination() {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest create =
      new BorrowApplicationCreateRequest();
    create.setBookId(bookId);
    create.setRequestedDays(12);
    create.setReason("组合查询");
    BorrowApplication pending = applications.create(create);
    login(staffId, staffRole, 2);

    // 借阅申请的每个可查询字段分别生效。
    BorrowApplicationQueryRequest q = new BorrowApplicationQueryRequest();
    q.setId(pending.getId());
    assertApplication(q, pending.getId());
    q = new BorrowApplicationQueryRequest();
    q.setStatus(BorrowApplicationStatus.PENDING);
    q.setMemberId(readerId);
    assertApplication(q, pending.getId());
    q = new BorrowApplicationQueryRequest();
    q.setMemberName("借阅测试");
    assertApplication(q, pending.getId());
    q = new BorrowApplicationQueryRequest();
    q.setBookId(bookId);
    assertApplication(q, pending.getId());
    q = new BorrowApplicationQueryRequest();
    q.setBookTitle("借阅图书");
    assertApplication(q, pending.getId());
    q = new BorrowApplicationQueryRequest();
    q.setIsbn("BI");
    assertApplication(q, pending.getId());
    q = new BorrowApplicationQueryRequest();
    q.setSubmittedFrom(pending.getSubmittedAt().minusSeconds(1));
    q.setSubmittedTo(pending.getSubmittedAt().plusSeconds(1));
    assertApplication(q, pending.getId());

    BorrowApplicationRejectRequest reject =
      new BorrowApplicationRejectRequest();
    reject.setExpectedVersion(0L);
    reject.setDecisionRemark("生成已处理申请用于查询");
    BorrowApplication rejected = applications.reject(pending.getId(), reject);
    q = new BorrowApplicationQueryRequest();
    q.setProcessedBy(staffId);
    q.setStatus(BorrowApplicationStatus.REJECTED);
    assertApplication(q, rejected.getId());
    q = new BorrowApplicationQueryRequest();
    q.setMemberId(readerId);
    q.setBookId(bookId);
    q.setMemberName("借阅");
    q.setBookTitle("图书");
    q.setIsbn("BI");
    q.setProcessedBy(staffId);
    q.setStatus(BorrowApplicationStatus.REJECTED);
    q.setPage(1);
    q.setPageSize(1);
    assertApplication(q, rejected.getId());

    // 重新申请并审批，构造可覆盖借阅记录全部筛选字段的数据。
    login(readerId, readerRole, 1);
    BorrowApplication second = applications.create(create);
    login(staffId, staffRole, 2);
    BorrowApplicationApproveRequest approve =
      new BorrowApplicationApproveRequest();
    approve.setApprovedDays(12);
    approve.setExpectedVersion(0L);
    applications.approve(second.getId(), approve);
    BorrowRecordQueryRequest all = new BorrowRecordQueryRequest();
    all.setMemberId(readerId);
    BorrowRecordDetailVO record = records.queryAdmin(all).getRows().get(0);

    BorrowRecordQueryRequest r = new BorrowRecordQueryRequest();
    r.setId(record.getId());
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setMemberId(readerId);
    r.setStatus(BorrowRecordStatus.BORROWING);
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setMemberName("借阅测试");
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setBookId(bookId);
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setBookTitle("借阅图书");
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setIsbn("BI");
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setCopyBarcode(barcode.substring(0, 4));
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setCurrentOnly(true);
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setBorrowedFrom(record.getBorrowedAt().minusSeconds(1));
    r.setBorrowedTo(record.getBorrowedAt().plusSeconds(1));
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setDueFrom(record.getDueAt().minusSeconds(1));
    r.setDueTo(record.getDueAt().plusSeconds(1));
    assertRecord(r, record.getId());
    r = new BorrowRecordQueryRequest();
    r.setMemberId(readerId);
    r.setBookId(bookId);
    r.setBookTitle("借阅");
    r.setIsbn("BI");
    r.setCopyBarcode("BC");
    r.setStatus(BorrowRecordStatus.BORROWING);
    r.setCurrentOnly(true);
    r.setBorrowedFrom(record.getBorrowedAt().minusSeconds(1));
    r.setBorrowedTo(record.getBorrowedAt().plusSeconds(1));
    r.setDueFrom(record.getDueAt().minusSeconds(1));
    r.setDueTo(record.getDueAt().plusSeconds(1));
    r.setPage(1);
    r.setPageSize(1);
    assertRecord(r, record.getId());

    // overdueOnly只返回实时已逾期且尚未归还的记录。
    jdbc.update(
      "update borrow_record set due_at=dateadd('HOUR',-1,current_timestamp) where id=?",
      record.getId()
    );
    r = new BorrowRecordQueryRequest();
    r.setOverdueOnly(true);
    assertRecord(r, record.getId());
  }

  @Test
  void overdueSynchronizationAndRealtimeQueriesWork() throws Exception {
    BorrowRecordDetailVO record = approveBorrowingRecord();
    jdbc.update(
      "update borrow_record set due_at=dateadd('HOUR',-1,current_timestamp), version=0 where id=?",
      record.getId()
    );
    overdueScheduler.synchronize();
    assertEquals(
      "OVERDUE",
      jdbc.queryForObject(
        "select status from borrow_record where id=?",
        String.class,
        record.getId()
      )
    );
    assertEquals(
      1L,
      jdbc.queryForObject(
        "select version from borrow_record where id=?",
        Long.class,
        record.getId()
      )
    );
    assertEquals(
      0,
      overdueService.synchronizeOverdueRecords(java.time.LocalDateTime.now())
    );
    // 首次同步产生一条提醒，重复同步既不增加版本，也不重复产生Outbox任务。
    assertEquals(1, jdbc.queryForObject(
      "select count(*) from message where notification_key=?", Integer.class,
      "borrow-record:" + record.getId() + ":overdue"));
    assertEquals(
      1L,
      jdbc.queryForObject(
        "select version from borrow_record where id=?",
        Long.class,
        record.getId()
      )
    );

    login(readerId, readerRole, 1);
    BorrowRecordQueryRequest malicious = new BorrowRecordQueryRequest();
    malicious.setMemberId(staffId);
    malicious.setOverdueOnly(true);
    BorrowRecordDetailVO overdue = records
      .queryMine(malicious)
      .getRows()
      .get(0);
    assertEquals(readerId, overdue.getMemberId());
    assertEquals(1, overdue.getCurrentOverdueDays());
    mockMvc
      .perform(
        get("/borrow-records")
          .param("memberId", staffId.toString())
          .param("currentOnly", "true")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].memberId").value(readerId));
    mockMvc
      .perform(get("/borrow-records").param("overdueOnly", "true"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].currentOverdueDays").value(1));
    login(staffId, staffRole, 2);
    mockMvc
      .perform(get("/admin/borrow-records").param("overdueOnly", "true"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  void overdueReturnRoundsUpAndValidatesInput() {
    BorrowRecordDetailVO record = approveBorrowingRecord();
    jdbc.update(
      "update borrow_record set due_at=dateadd('HOUR',-1,current_timestamp) where id=?",
      record.getId()
    );
    login(staffId, staffRole, 2);
    BorrowReturnRequest invalid = new BorrowReturnRequest();
    invalid.setExpectedVersion(-1L);
    assertThrows(WrongFormatException.class, () ->
      records.returnByRecord(record.getId(), invalid)
    );
    BorrowReturnRequest tooLong = new BorrowReturnRequest();
    tooLong.setExpectedVersion(0L);
    tooLong.setReturnConditionNote("x".repeat(501));
    assertThrows(WrongFormatException.class, () ->
      records.returnByRecord(record.getId(), tooLong)
    );
    BorrowReturnRequest request = new BorrowReturnRequest();
    request.setExpectedVersion(0L);
    request.setReturnConditionNote("  完好  ");
    BorrowRecord returned = records.returnByRecord(record.getId(), request);
    assertEquals(1, returned.getOverdueDays());
    assertEquals("完好", returned.getReturnConditionNote());
    assertEquals(
      "AVAILABLE",
      jdbc.queryForObject(
        "select status from book_copy where id=?",
        String.class,
        copyId
      )
    );
    assertEquals(
      0,
      jdbc.queryForObject(
        "select borrowed_book_count from member where id=?",
        Integer.class,
        readerId
      )
    );
  }

  @Test
  void completeBorrowOverdueReturnHttpLifecycleCoversEveryEndpoint()
    throws Exception {
    login(readerId, readerRole, 1);
    String created = mockMvc
      .perform(
        post("/borrow-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"bookId\":" + bookId + ",\"requestedDays\":14}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.status").value("PENDING"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    long applicationId = (
      (Number) JsonPath.read(created, "$.data.id")
    ).longValue();
    mockMvc
      .perform(
        get("/borrow-applications").param("id", Long.toString(applicationId))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));

    login(staffId, staffRole, 2);
    mockMvc
      .perform(
        get("/admin/borrow-applications").param(
          "id",
          Long.toString(applicationId)
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
    mockMvc
      .perform(
        post("/admin/borrow-applications/{id}/approve", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"copyId\":" +
              copyId +
              ",\"approvedDays\":14,\"expectedVersion\":0}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("APPROVED"));
    Long recordId = jdbc.queryForObject(
      "select id from borrow_record where application_id=?",
      Long.class,
      applicationId
    );

    login(readerId, readerRole, 1);
    mockMvc
      .perform(get("/borrow-records").param("id", recordId.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
    login(staffId, staffRole, 2);
    mockMvc
      .perform(get("/admin/borrow-records").param("id", recordId.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rows[0].status").value("BORROWING"));

    jdbc.update(
      "update borrow_record set due_at=dateadd('HOUR',-1,current_timestamp) where id=?",
      recordId
    );
    assertEquals(
      1,
      overdueService.synchronizeOverdueRecords(java.time.LocalDateTime.now())
    );
    mockMvc
      .perform(
        get("/admin/borrow-records")
          .param("id", recordId.toString())
          .param("overdueOnly", "true")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rows[0].status").value("OVERDUE"))
      .andExpect(jsonPath("$.data.rows[0].currentOverdueDays").value(1));
    mockMvc
      .perform(
        post("/admin/borrow-records/{id}/return", recordId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"expectedVersion\":1,\"returnConditionNote\":\" HTTP归还完好 \"}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("RETURNED"))
      .andExpect(jsonPath("$.data.overdueDays").value(1));

    login(readerId, readerRole, 1);
    String second = mockMvc
      .perform(
        post("/borrow-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"bookId\":" + bookId + ",\"requestedDays\":7}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    long secondId = ((Number) JsonPath.read(second, "$.data.id")).longValue();
    login(staffId, staffRole, 2);
    mockMvc
      .perform(
        post("/admin/borrow-applications/{id}/approve", secondId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"copyId\":" +
              copyId +
              ",\"approvedDays\":7,\"expectedVersion\":0}"
          )
      )
      .andExpect(status().isOk());
    mockMvc
      .perform(
        post("/admin/book-copies/{barcode}/return", barcode)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"returnConditionNote\":\"条码归还\"}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("RETURNED"));

    login(readerId, readerRole, 1);
    String cancelled = mockMvc
      .perform(
        post("/borrow-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"bookId\":" + bookId + ",\"requestedDays\":3}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    long cancelId = (
      (Number) JsonPath.read(cancelled, "$.data.id")
    ).longValue();
    mockMvc
      .perform(
        post("/borrow-applications/{id}/cancel", cancelId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"expectedVersion\":0}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("CANCELLED"));

    String rejected = mockMvc
      .perform(
        post("/borrow-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"bookId\":" + bookId + ",\"requestedDays\":3}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    long rejectId = ((Number) JsonPath.read(rejected, "$.data.id")).longValue();
    login(staffId, staffRole, 2);
    mockMvc
      .perform(
        post("/admin/borrow-applications/{id}/reject", rejectId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"decisionRemark\":\"测试拒绝\",\"expectedVersion\":0}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("REJECTED"));
  }

  @Test
  void quotaIsCheckedAtSubmission() {
    jdbc.update("update member set permanent_borrow_limit=0 where id=?", readerId);
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest full = new BorrowApplicationCreateRequest();
    full.setBookId(bookId);
    full.setRequestedDays(7);
    assertThrows(ConflictException.class, () -> applications.create(full));
  }

  @Test
  void quotaIsCheckedAgainAtApproval() {
    jdbc.update(
      "update member set borrowed_book_count=0,permanent_borrow_limit=1,version=0 where id=?",
      readerId
    );
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest full = new BorrowApplicationCreateRequest();
    full.setBookId(bookId);
    full.setRequestedDays(7);
    BorrowApplication pending = applications.create(full);
    jdbc.update("update member set borrowed_book_count=1 where id=?", readerId);
    login(staffId, staffRole, 2);
    BorrowApplicationApproveRequest approve =
      new BorrowApplicationApproveRequest();
    approve.setApprovedDays(7);
    approve.setExpectedVersion(0L);
    assertThrows(ConflictException.class, () ->
      applications.approve(pending.getId(), approve)
    );
    assertEquals(
      "AVAILABLE",
      jdbc.queryForObject(
        "select status from book_copy where id=?",
        String.class,
        copyId
      )
    );
    assertEquals(
      0,
      jdbc.queryForObject(
        "select count(*) from borrow_record where application_id=?",
        Integer.class,
        pending.getId()
      )
    );
  }

  @Test
  void memberMaintenanceCannotCorruptBorrowQuota() {
    jdbc.update(
      "update member set borrowed_book_count=1,permanent_borrow_limit=5,version=0 where id=?",
      readerId
    );
    Reader tooLow = new Reader();
    tooLow.setMemberType(com.yunfeil161.ailibrary.common.enums.MemberType.READER);
    tooLow.setPermanentBorrowLimit(0);
    tooLow.setBorrowedBookCount(999);
    assertThrows(ConflictException.class, () ->
      memberService.updateReader(readerId, tooLow, null, 0L)
    );

    Reader valid = new Reader();
    valid.setMemberType(com.yunfeil161.ailibrary.common.enums.MemberType.READER);
    valid.setPermanentBorrowLimit(2);
    valid.setBorrowedBookCount(999);
    memberService.updateReader(readerId, valid, null, 0L);
    assertEquals(
      1,
      jdbc.queryForObject(
        "select borrowed_book_count from member where id=?",
        Integer.class,
        readerId
      )
    );
    assertEquals(
      2,
      jdbc.queryForObject(
        "select permanent_borrow_limit from member where id=?",
        Integer.class,
        readerId
      )
    );
    assertThrows(DataIntegrityViolationException.class, () ->
      jdbc.update(
        "update member set borrowed_book_count=-1 where id=?",
        readerId
      )
    );
  }

  @Test
  void renewalUsesUnifiedApplicationAndStopsAfterThreeApprovals() {
    BorrowRecordDetailVO borrowed = approveBorrowingRecord();
    cachedRecordId = borrowed.getId();
    String recordCacheKey = BookCacheKeys.BORROW_RECORD_BY_ID + cachedRecordId;
    cacheClient.delete(recordCacheKey);
    java.time.LocalDateTime previousDueAt = borrowed.getDueAt();

    // A renewal does not consume another loan slot; a reader at the quota limit may still renew.
    jdbc.update(
      "update member set permanent_borrow_limit=borrowed_book_count where id=?",
      readerId
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select borrowed_book_count from member where id=?",
        Integer.class,
        readerId
      )
    );
    BorrowRecordQueryRequest cachedLookup = new BorrowRecordQueryRequest();
    cachedLookup.setId(borrowed.getId());
    login(staffId, staffRole, 2);
    assertEquals(
      0,
      records.queryAdmin(cachedLookup).getRows().get(0).getRenewCount()
    );

    for (int expectedCount = 1; expectedCount <= 3; expectedCount++) {
      login(readerId, readerRole, 1);
      BorrowApplicationCreateRequest renewal = new BorrowApplicationCreateRequest();
      renewal.setApplicationType(BorrowApplicationType.RENEW);
      renewal.setBorrowRecordId(borrowed.getId());
      renewal.setRequestedDays(7);
      renewal.setReason("第" + expectedCount + "次续借");
      BorrowApplication pending = applications.create(renewal);

      assertEquals(BorrowApplicationType.RENEW, pending.getApplicationType());
      assertEquals(borrowed.getId(), pending.getBorrowRecordId());
      assertEquals(bookId, pending.getBookId());

      // 同一借阅记录的待审批续借由记录级分布式锁串行化，并由数据库再次检查。
      assertThrows(ConflictException.class, () -> applications.create(renewal));

      login(staffId, staffRole, 2);
      BorrowApplicationApproveRequest approval = new BorrowApplicationApproveRequest();
      approval.setApprovedDays(7);
      approval.setExpectedVersion(0L);
      applications.approve(pending.getId(), approval);

      BorrowRecordQueryRequest byId = new BorrowRecordQueryRequest();
      byId.setId(borrowed.getId());
      BorrowRecordDetailVO renewed = records.queryAdmin(byId).getRows().get(0);
      assertEquals(expectedCount, renewed.getRenewCount());
      assertEquals(previousDueAt.plusDays(7), renewed.getDueAt());
      assertNotNull(renewed.getLastRenewedAt());
      assertEquals(expectedCount, records.queryAdmin(cachedLookup).getRows().get(0).getRenewCount(),
        "Renewal approval must evict the cached borrow-record detail");
      assertEquals(
        1,
        jdbc.queryForObject(
          "select borrowed_book_count from member where id=?",
          Integer.class,
          readerId
        ),
        "Renewal must not increment the current borrowed count"
      );
      previousDueAt = renewed.getDueAt();
    }

    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest fourth = new BorrowApplicationCreateRequest();
    fourth.setApplicationType(BorrowApplicationType.RENEW);
    fourth.setBorrowRecordId(borrowed.getId());
    fourth.setRequestedDays(7);
    assertThrows(ConflictException.class, () -> applications.create(fourth));

    assertEquals(1, jdbc.queryForObject(
      "select borrowed_book_count from member where id=?", Integer.class, readerId
    ));
    assertEquals("BORROWED", jdbc.queryForObject(
      "select status from book_copy where id=?", String.class, copyId
    ));
  }

  @Test
  void renewalHttpFlowKeepsApplicationAndRecordQueriesAggregated() throws Exception {
    BorrowRecordDetailVO borrowed = approveBorrowingRecord();
    cachedRecordId = borrowed.getId();
    cacheClient.delete(BookCacheKeys.BORROW_RECORD_BY_ID + borrowed.getId());

    login(staffId, staffRole, 2);
    mockMvc
      .perform(get("/admin/borrow-records").param("id", borrowed.getId().toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rows[0].renewCount").value(0));

    login(readerId, readerRole, 1);
    String createBody =
      "{\"applicationType\":\"RENEW\",\"borrowRecordId\":" +
      borrowed.getId() +
      ",\"requestedDays\":7,\"reason\":\"HTTP renewal\"}";
    String created = mockMvc
      .perform(
        post("/borrow-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(createBody)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.applicationType").value("RENEW"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    long applicationId = (
      (Number) JsonPath.read(created, "$.data.id")
    ).longValue();

    login(staffId, staffRole, 2);
    mockMvc
      .perform(
        get("/admin/borrow-applications")
          .param("id", Long.toString(applicationId))
          .param("applicationType", "RENEW")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].borrowRecordId").value(borrowed.getId()));
    mockMvc
      .perform(
        post("/admin/borrow-applications/{id}/approve", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"approvedDays\":7,\"expectedVersion\":0}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.applicationType").value("RENEW"));

    mockMvc
      .perform(get("/admin/borrow-records").param("id", borrowed.getId().toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rows[0].renewCount").value(1));
    BorrowRecord renewed = jdbc.queryForObject(
      "select * from borrow_record where id=?",
      (result, row) -> {
        BorrowRecord value = new BorrowRecord();
        value.setId(result.getLong("id"));
        value.setDueAt(result.getTimestamp("due_at").toLocalDateTime());
        value.setRenewCount(result.getInt("renew_count"));
        return value;
      },
      borrowed.getId()
    );
    assertEquals(borrowed.getDueAt().plusDays(7), renewed.getDueAt());
    assertEquals(1, renewed.getRenewCount());
    assertEquals("BORROWED", jdbc.queryForObject(
      "select status from book_copy where id=?",
      String.class,
      copyId
    ));
    assertEquals(1, jdbc.queryForObject(
      "select borrowed_book_count from member where id=?",
      Integer.class,
      readerId
    ));
  }

  @Test
  void renewalRejectsForeignRecordAndBookIdAndOrdersBeforeBorrowRequests() {
    BorrowRecordDetailVO borrowed = approveBorrowingRecord();

    // Queue a normal borrow request first; a later renewal must still sort ahead of it.
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) values(?,?,?,'AVAILABLE',0)",
      bookId,
      barcode + "-second",
      "A-02"
    );
    jdbc.update("update member set accepted_rule_version='BORROWING-1' where id=?", staffId);
    login(staffId, staffRole, 2);
    BorrowApplicationCreateRequest borrow = new BorrowApplicationCreateRequest();
    borrow.setBookId(bookId);
    borrow.setRequestedDays(7);
    BorrowApplication pendingBorrow = applications.create(borrow);

    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest forged = new BorrowApplicationCreateRequest();
    forged.setApplicationType(BorrowApplicationType.RENEW);
    forged.setBorrowRecordId(borrowed.getId());
    forged.setBookId(bookId);
    forged.setRequestedDays(3);
    assertThrows(WrongFormatException.class, () -> applications.create(forged));

    jdbc.update(
      "insert into member(member_type,role_id,name,email,account_status,qualification_status,borrowed_book_count,permanent_borrow_limit,version) values('READER',?,?,?,'ACTIVE','APPROVED',0,5,0)",
      readerRole, "其他读者", "other-" + UUID.randomUUID() + "@test.local"
    );
    Long otherReader = jdbc.queryForObject(
      "select max(id) from member where member_type='READER'", Long.class
    );
    jdbc.update("update member set accepted_rule_version='BORROWING-1' where id=?", otherReader);
    login(otherReader, readerRole, 1);
    BorrowApplicationCreateRequest foreign = new BorrowApplicationCreateRequest();
    foreign.setApplicationType(BorrowApplicationType.RENEW);
    foreign.setBorrowRecordId(borrowed.getId());
    foreign.setRequestedDays(3);
    assertThrows(ForbiddenException.class, () -> applications.create(foreign));

    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest renewal = new BorrowApplicationCreateRequest();
    renewal.setApplicationType(BorrowApplicationType.RENEW);
    renewal.setBorrowRecordId(borrowed.getId());
    renewal.setRequestedDays(3);
    BorrowApplication renewalApplication = applications.create(renewal);

    login(staffId, staffRole, 2);
    BorrowApplicationQueryRequest pending = new BorrowApplicationQueryRequest();
    pending.setStatus(BorrowApplicationStatus.PENDING);
    List<BorrowApplicationDetailVO> ordered =
      applications.queryAdmin(pending).getRows();
    assertEquals(2, ordered.size());
    assertEquals(renewalApplication.getId(), ordered.get(0).getId());
    assertEquals(BorrowApplicationType.RENEW, ordered.get(0).getApplicationType());
    assertEquals(pendingBorrow.getId(), ordered.get(1).getId());
    assertEquals(BorrowApplicationType.BORROW, ordered.get(1).getApplicationType());
  }

  private BorrowRecordDetailVO approveBorrowingRecord() {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest create =
      new BorrowApplicationCreateRequest();
    create.setBookId(bookId);
    create.setRequestedDays(14);
    BorrowApplication app = applications.create(create);
    login(staffId, staffRole, 2);
    BorrowApplicationApproveRequest approve =
      new BorrowApplicationApproveRequest();
    approve.setApprovedDays(14);
    approve.setExpectedVersion(0L);
    applications.approve(app.getId(), approve);
    BorrowRecordQueryRequest query = new BorrowRecordQueryRequest();
    query.setMemberId(readerId);
    return records.queryAdmin(query).getRows().get(0);
  }

  private Long role(String code) {
    return jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
    );
  }

  private void assertApplication(
    BorrowApplicationQueryRequest query,
    Long expectedId
  ) {
    assertTrue(
      applications
        .queryAdmin(query)
        .getRows()
        .stream()
        .anyMatch(row -> row.getId().equals(expectedId))
    );
  }

  private void assertRecord(BorrowRecordQueryRequest query, Long expectedId) {
    assertTrue(
      records
        .queryAdmin(query)
        .getRows()
        .stream()
        .anyMatch(row -> row.getId().equals(expectedId))
    );
  }

  private void login(Long member, Long role, Integer level) {
    LoginUserContext.set(
      new LoginSession(
        "test", member, role,
        com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(level),
        level == 3 ? "ADMIN" : level == 2 ? "LIBRARIAN"
          : level == 4 ? "AI_LIBRARIAN" : "READER",
        "test@test.local", "test"
      )
    );
  }
}
