package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.entity.BorrowApplication;
import com.yunfeil161.ailibrary.book.borrow.entity.BorrowRecordDetailVO;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationStatus;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowRecordStatus;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowApplicationService;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowOverdueService;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowRecordService;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.reading.dto.*;
import com.yunfeil161.ailibrary.book.reading.entity.ReadingPlan;
import com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus;
import com.yunfeil161.ailibrary.book.reading.service.ReadingPlanService;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 使用真实 Redis 和 H2 验证图书扩展模块的缓存命中、绕过缓存和写后失效。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class BookModuleExtendedCacheIntegrationTests {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  StringRedisTemplate redis;

  @Autowired
  ReadingPlanService readingPlans;

  @Autowired
  BorrowApplicationService applications;

  @Autowired
  BorrowRecordService records;

  @Autowired
  BorrowOverdueService overdueService;

  @Autowired
  SqlSessionTemplate sqlSessionTemplate;

  @Autowired
  MockMvc mvc;

  private final List<String> keys = new ArrayList<>();
  private Long readerId;
  private Long readerRole;
  private Long staffId;
  private Long staffRole;
  private Long bookId;
  private Long copyId;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    readerRole = role("READER");
    staffRole = role("LIBRARIAN");
    readerId = member(
      1,
      readerRole,
      "CacheReader" + suffix,
      "cr" + suffix + "@test.local"
    );
    staffId = member(
      2,
      staffRole,
      "CacheStaff" + suffix,
      "cs" + suffix + "@test.local"
    );
    jdbc.update(
      "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
      "CC" + suffix,
      "CacheCategory" + suffix
    );
    Long categoryId = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      "CC" + suffix
    );
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) " +
        "values(?,?,?,?,'zh-CN','ACTIVE',0)",
      categoryId,
      "CacheBook" + suffix,
      "CacheAuthor",
      "CI" + suffix
    );
    bookId = jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      "CI" + suffix
    );
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) values(?,?,?,'AVAILABLE',0)",
      bookId,
      "CB" + suffix,
      "CacheShelf"
    );
    copyId = jdbc.queryForObject(
      "select id from book_copy where barcode=?",
      Long.class,
      "CB" + suffix
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
    if (!keys.isEmpty()) redis.delete(keys);
  }

  @Test
  void readingPlanAndItemSupportCacheHitAndDatabaseFallback() {
    login(readerId, readerRole, 1);
    ReadingPlanCreateRequest create = new ReadingPlanCreateRequest();
    create.setName("Cached plan");
    create.setTargetBooks(3);
    create.setStatus(ReadingPlanStatus.DRAFT);
    ReadingPlan plan = readingPlans.createMine(create);

    String planKey = BookCacheKeys.READING_PLAN_BY_MEMBER + readerId;
    keys.add(planKey);
    assertEquals("Cached plan", readingPlans.getMine().getName());
    assertNotNull(redis.opsForValue().get(planKey));
    jdbc.update(
      "update reading_plan set name='Database plan' where id=?",
      plan.getId()
    );
    assertEquals("Cached plan", readingPlans.getMine().getName());

    login(staffId, staffRole, 2);
    ReadingPlanQueryRequest nonCachedPlanQuery = new ReadingPlanQueryRequest();
    nonCachedPlanQuery.setMemberId(readerId);
    nonCachedPlanQuery.setPlanName("Database plan");
    assertEquals(
      "Database plan",
      readingPlans.queryAdmin(nonCachedPlanQuery).getRows().get(0).getName()
    );

    redis.delete(planKey);
    sqlSessionTemplate.clearCache();
    login(readerId, readerRole, 1);
    assertEquals("Database plan", readingPlans.getMine().getName());

    ReadingItemCreateRequest add = new ReadingItemCreateRequest();
    add.setBookId(bookId);
    add.setExpectedPlanVersion(0L);
    add.setNote("Cached note");
    readingPlans.addMine(add);
    String itemKey = BookCacheKeys.readingItemKey(readerId, bookId);
    keys.add(itemKey);
    ReadingItemQueryRequest itemQuery = new ReadingItemQueryRequest();
    itemQuery.setBookId(bookId);
    assertEquals(
      "Cached note",
      readingPlans.queryMineItems(itemQuery).getRows().get(0).getNote()
    );
    assertNotNull(redis.opsForValue().get(itemKey));
    jdbc.update(
      "update reading_plan_item set note='Database note' where plan_id=? and book_id=?",
      plan.getId(),
      bookId
    );
    assertEquals(
      "Cached note",
      readingPlans.queryMineItems(itemQuery).getRows().get(0).getNote()
    );

    ReadingItemQueryRequest nonCachedItemQuery = new ReadingItemQueryRequest();
    nonCachedItemQuery.setBookId(bookId);
    nonCachedItemQuery.setBookKeyword("CacheBook");
    assertEquals(
      "Database note",
      readingPlans.queryMineItems(nonCachedItemQuery).getRows().get(0).getNote()
    );
    redis.delete(itemKey);
    sqlSessionTemplate.clearCache();
    assertEquals(
      "Database note",
      readingPlans.queryMineItems(itemQuery).getRows().get(0).getNote()
    );
  }

  @Test
  void borrowApplicationCacheIsUsedAndCancelledApplicationEvictsIt() {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest create =
      new BorrowApplicationCreateRequest();
    create.setBookId(bookId);
    create.setRequestedDays(14);
    create.setReason("Cached reason");
    BorrowApplication application = applications.create(create);
    String key = BookCacheKeys.BORROW_APPLICATION_BY_ID + application.getId();
    keys.add(key);

    BorrowApplicationQueryRequest exact = new BorrowApplicationQueryRequest();
    exact.setId(application.getId());
    assertEquals(
      "Cached reason",
      applications.queryMine(exact).getRows().get(0).getReason()
    );
    assertNotNull(redis.opsForValue().get(key));
    jdbc.update(
      "update borrow_application set reason='Database reason' where id=?",
      application.getId()
    );
    assertEquals(
      "Cached reason",
      applications.queryMine(exact).getRows().get(0).getReason()
    );

    BorrowApplicationQueryRequest nonCached =
      new BorrowApplicationQueryRequest();
    nonCached.setId(application.getId());
    nonCached.setStatus(BorrowApplicationStatus.PENDING);
    assertEquals(
      "Database reason",
      applications.queryMine(nonCached).getRows().get(0).getReason()
    );

    BorrowApplicationCancelRequest cancel =
      new BorrowApplicationCancelRequest();
    cancel.setExpectedVersion(0L);
    assertEquals(
      BorrowApplicationStatus.CANCELLED,
      applications.cancel(application.getId(), cancel).getStatus()
    );
    assertNull(redis.opsForValue().get(key));
    assertEquals(
      BorrowApplicationStatus.CANCELLED,
      applications.queryMine(exact).getRows().get(0).getStatus()
    );
  }

  @Test
  void borrowRecordCacheIsUsedAndReturnEvictsIt() {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest create =
      new BorrowApplicationCreateRequest();
    create.setBookId(bookId);
    create.setRequestedDays(7);
    BorrowApplication application = applications.create(create);
    BorrowApplicationQueryRequest applicationExact =
      new BorrowApplicationQueryRequest();
    applicationExact.setId(application.getId());
    applications.queryMine(applicationExact);
    String applicationKey =
      BookCacheKeys.BORROW_APPLICATION_BY_ID + application.getId();
    keys.add(applicationKey);
    assertNotNull(redis.opsForValue().get(applicationKey));
    login(staffId, staffRole, 2);
    BorrowApplicationApproveRequest approve =
      new BorrowApplicationApproveRequest();
    approve.setCopyId(copyId);
    approve.setApprovedDays(7);
    approve.setExpectedVersion(0L);
    applications.approve(application.getId(), approve);
    assertNull(redis.opsForValue().get(applicationKey));
    Long recordId = jdbc.queryForObject(
      "select id from borrow_record where application_id=?",
      Long.class,
      application.getId()
    );
    String key = BookCacheKeys.BORROW_RECORD_BY_ID + recordId;
    keys.add(key);

    BorrowRecordQueryRequest exact = new BorrowRecordQueryRequest();
    exact.setId(recordId);
    BorrowRecordDetailVO first = records.queryAdmin(exact).getRows().get(0);
    assertEquals(BorrowRecordStatus.BORROWING, first.getStatus());
    assertNotNull(redis.opsForValue().get(key));
    jdbc.update(
      "update borrow_record set return_condition_note='Database record note' where id=?",
      recordId
    );
    assertNull(
      records.queryAdmin(exact).getRows().get(0).getReturnConditionNote()
    );

    BorrowRecordQueryRequest nonCached = new BorrowRecordQueryRequest();
    nonCached.setId(recordId);
    nonCached.setStatus(BorrowRecordStatus.BORROWING);
    assertEquals(
      "Database record note",
      records.queryAdmin(nonCached).getRows().get(0).getReturnConditionNote()
    );

    BorrowReturnRequest returned = new BorrowReturnRequest();
    returned.setExpectedVersion(0L);
    returned.setReturnConditionNote("Returned normally");
    assertEquals(
      BorrowRecordStatus.RETURNED,
      records.returnByRecord(recordId, returned).getStatus()
    );
    assertNull(redis.opsForValue().get(key));
    assertEquals(
      BorrowRecordStatus.RETURNED,
      records.queryAdmin(exact).getRows().get(0).getStatus()
    );
  }

  @Test
  void missingUniqueQueriesUseShortEmptyCaches() {
    long missingApplication = 9_000_000_001L;
    long missingRecord = 9_000_000_002L;
    String applicationKey =
      BookCacheKeys.BORROW_APPLICATION_BY_ID + missingApplication;
    String recordKey = BookCacheKeys.BORROW_RECORD_BY_ID + missingRecord;
    String planKey = BookCacheKeys.READING_PLAN_BY_MEMBER + readerId;
    keys.addAll(List.of(applicationKey, recordKey, planKey));

    login(readerId, readerRole, 1);
    BorrowApplicationQueryRequest applicationQuery =
      new BorrowApplicationQueryRequest();
    applicationQuery.setId(missingApplication);
    assertThrows(NotFoundException.class, () ->
      applications.queryMine(applicationQuery)
    );
    assertThrows(NotFoundException.class, () ->
      applications.queryMine(applicationQuery)
    );
    assertEquals("", redis.opsForValue().get(applicationKey));

    login(staffId, staffRole, 2);
    BorrowRecordQueryRequest recordQuery = new BorrowRecordQueryRequest();
    recordQuery.setId(missingRecord);
    assertThrows(NotFoundException.class, () ->
      records.queryAdmin(recordQuery)
    );
    assertThrows(NotFoundException.class, () ->
      records.queryAdmin(recordQuery)
    );
    assertEquals("", redis.opsForValue().get(recordKey));

    login(readerId, readerRole, 1);
    assertThrows(NotFoundException.class, readingPlans::getMine);
    assertThrows(NotFoundException.class, readingPlans::getMine);
    assertEquals("", redis.opsForValue().get(planKey));

    redis.delete(planKey);
    ReadingPlanCreateRequest create = new ReadingPlanCreateRequest();
    create.setName("Empty item cache plan");
    create.setTargetBooks(1);
    create.setStatus(ReadingPlanStatus.DRAFT);
    readingPlans.createMine(create);
    long missingBook = 9_000_000_003L;
    String itemKey = BookCacheKeys.readingItemKey(readerId, missingBook);
    keys.add(itemKey);
    ReadingItemQueryRequest itemQuery = new ReadingItemQueryRequest();
    itemQuery.setBookId(missingBook);
    assertEquals(0, readingPlans.queryMineItems(itemQuery).getTotal());
    assertEquals(0, readingPlans.queryMineItems(itemQuery).getTotal());
    assertEquals("", redis.opsForValue().get(itemKey));
  }

  @Test
  void overdueSynchronizationEvictsBorrowRecordCache() {
    login(readerId, readerRole, 1);
    BorrowApplicationCreateRequest create =
      new BorrowApplicationCreateRequest();
    create.setBookId(bookId);
    create.setRequestedDays(7);
    BorrowApplication application = applications.create(create);
    login(staffId, staffRole, 2);
    BorrowApplicationApproveRequest approve =
      new BorrowApplicationApproveRequest();
    approve.setCopyId(copyId);
    approve.setApprovedDays(7);
    approve.setExpectedVersion(0L);
    applications.approve(application.getId(), approve);
    Long recordId = jdbc.queryForObject(
      "select id from borrow_record where application_id=?",
      Long.class,
      application.getId()
    );
    jdbc.update(
      "update borrow_record set due_at=? where id=?",
      LocalDateTime.now().minusHours(1),
      recordId
    );
    String key = BookCacheKeys.BORROW_RECORD_BY_ID + recordId;
    keys.add(key);
    BorrowRecordQueryRequest exact = new BorrowRecordQueryRequest();
    exact.setId(recordId);
    assertEquals(
      BorrowRecordStatus.BORROWING,
      records.queryAdmin(exact).getRows().get(0).getStatus()
    );
    assertNotNull(redis.opsForValue().get(key));

    assertEquals(
      1,
      overdueService.synchronizeOverdueRecords(LocalDateTime.now())
    );
    assertNull(redis.opsForValue().get(key));
    sqlSessionTemplate.clearCache();
    assertEquals(
      BorrowRecordStatus.OVERDUE,
      records.queryAdmin(exact).getRows().get(0).getStatus()
    );
  }

  @Test
  void modifiedHttpQueryEndpointsWorkWithColdWarmAndBypassedCaches()
    throws Exception {
    login(readerId, readerRole, 1);
    ReadingPlanCreateRequest planRequest = new ReadingPlanCreateRequest();
    planRequest.setName("HTTP cache plan");
    planRequest.setTargetBooks(2);
    planRequest.setStatus(ReadingPlanStatus.DRAFT);
    readingPlans.createMine(planRequest);
    ReadingItemCreateRequest itemRequest = new ReadingItemCreateRequest();
    itemRequest.setBookId(bookId);
    itemRequest.setExpectedPlanVersion(0L);
    readingPlans.addMine(itemRequest);
    keys.add(BookCacheKeys.READING_PLAN_BY_MEMBER + readerId);
    keys.add(BookCacheKeys.readingItemKey(readerId, bookId));

    for (int i = 0; i < 2; i++) {
      mvc
        .perform(get("/reading-plans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberId").value(readerId));
      mvc
        .perform(get("/reading-plans/items").param("bookId", bookId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    }
    mvc
      .perform(
        get("/reading-plans/items")
          .param("bookId", bookId.toString())
          .param("bookKeyword", "CacheBook")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));

    BorrowApplicationCreateRequest applicationRequest =
      new BorrowApplicationCreateRequest();
    applicationRequest.setBookId(bookId);
    applicationRequest.setRequestedDays(7);
    BorrowApplication application = applications.create(applicationRequest);
    keys.add(BookCacheKeys.BORROW_APPLICATION_BY_ID + application.getId());
    for (int i = 0; i < 2; i++) {
      mvc
        .perform(
          get("/borrow-applications").param(
            "id",
            application.getId().toString()
          )
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    }
    mvc
      .perform(
        get("/borrow-applications")
          .param("id", application.getId().toString())
          .param("status", "PENDING")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));

    login(staffId, staffRole, 2);
    BorrowApplicationApproveRequest approve =
      new BorrowApplicationApproveRequest();
    approve.setCopyId(copyId);
    approve.setApprovedDays(7);
    approve.setExpectedVersion(0L);
    applications.approve(application.getId(), approve);
    Long recordId = jdbc.queryForObject(
      "select id from borrow_record where application_id=?",
      Long.class,
      application.getId()
    );
    keys.add(BookCacheKeys.BORROW_RECORD_BY_ID + recordId);
    for (int i = 0; i < 2; i++) {
      mvc
        .perform(get("/admin/borrow-records").param("id", recordId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    }
    mvc
      .perform(
        get("/admin/borrow-records")
          .param("id", recordId.toString())
          .param("status", "BORROWING")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
    mvc
      .perform(
        get("/admin/reading-plans")
          .param("memberId", readerId.toString())
          .param("planName", "HTTP cache")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1));
  }

  private Long role(String code) {
    return jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
    );
  }

  private Long member(int level, Long roleId, String name, String email) {
    jdbc.update(
      "insert into member(member_type,role_id,name,email,account_status,qualification_status," +
        "borrowed_book_count,permanent_borrow_limit,version) values(?,?,?,?, 'ACTIVE','APPROVED',0,5,0)",
      com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(level).name(),
      roleId,
      name,
      email
    );
    jdbc.update("update member set accepted_rule_version='BORROWING-1' where email=?", email);
    return jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
  }

  private void login(Long memberId, Long roleId, int level) {
    LoginUserContext.set(
      new LoginSession(
        "cache-test",
        memberId,
        roleId,
        com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(level),
        level == 3 ? "ADMIN" : level == 2 ? "LIBRARIAN"
          : level == 4 ? "AI_LIBRARIAN" : "READER",
        "cache@test.local",
        "cache"
      )
    );
  }
}

