package com.yunfeil161.ailibrary.common.redis.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yunfeil161.ailibrary.AiLibraryApplication;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationCreateRequest;
import com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationApproveRequest;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowApplicationService;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyUpdateRequest;
import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import com.yunfeil161.ailibrary.book.copy.service.BookCopyService;
import com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanCreateRequest;
import com.yunfeil161.ailibrary.book.reading.service.ReadingPlanService;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import com.yunfeil161.ailibrary.identity.service.MemberService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/** 验证多个请求竞争同一业务键时，只允许一个请求进入临界区。 */
@SpringBootTest(
  classes = {
    AiLibraryApplication.class,
    DistributedLockIntegrationTests.LockTestConfiguration.class,
  },
  properties =
    "spring.datasource.url=jdbc:h2:mem:distributed_lock_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
class DistributedLockIntegrationTests {

  @DynamicPropertySource
  static void configureOptionalMySqlAcceptance(DynamicPropertyRegistry registry) {
    String url = System.getProperty("mysql.acceptance.url");
    if (url == null || url.isBlank()) return;
    registry.add("spring.datasource.url", () -> url);
    registry.add(
      "spring.datasource.driver-class-name",
      () -> "com.mysql.cj.jdbc.Driver"
    );
    registry.add(
      "spring.datasource.username",
      () -> System.getProperty("mysql.acceptance.username", "root")
    );
    registry.add(
      "spring.datasource.password",
      () -> System.getenv("MYSQL_PASSWORD")
    );
    registry.add("spring.sql.init.mode", () -> "never");
    registry.add("spring.data.redis.database", () -> 14);
  }

  @Autowired
  private LockProbe lockProbe;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private BorrowApplicationService borrowApplicationService;

  @Autowired
  private BookCopyService bookCopyService;

  @Autowired
  private MemberService memberService;

  @Autowired
  private ReadingPlanService readingPlanService;

  @Autowired
  private RedissonClient redissonClient;

  @Test
  void partialMultiLockFailureReleasesAlreadyAcquiredLocks() throws Exception {
    RLock blocked = redissonClient.getReadWriteLock("lock:test:partial:b").writeLock();
    ExecutorService holder = Executors.newSingleThreadExecutor();
    CountDownLatch acquired = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Future<?> holding = holder.submit(() -> {
      blocked.lock();
      acquired.countDown();
      try {
        release.await();
      } finally {
        blocked.unlock();
      }
      return null;
    });

    try {
      acquired.await();
      assertThrows(ConflictException.class, lockProbe::partialMultiLock);

      RLock first = redissonClient
        .getReadWriteLock("lock:test:partial:a")
        .writeLock();
      assertTrue(first.tryLock(), "后续锁失败时，先取得的锁必须释放");
      first.unlock();
    } finally {
      release.countDown();
      holding.get();
      holder.shutdownNow();
    }
  }

  @Test
  void sameBusinessKeySerializesConcurrentRequests() throws Exception {
    int requestCount = 8;
    CountDownLatch ready = new CountDownLatch(requestCount);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(requestCount);
    List<Future<?>> results = new ArrayList<>();

    try {
      for (int index = 0; index < requestCount; index++) {
        results.add(
          executor.submit(() -> {
            ready.countDown();
            start.await();
            lockProbe.write();
            return null;
          })
        );
      }

      ready.await();
      start.countDown();
      for (Future<?> result : results) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }

    assertEquals(requestCount, lockProbe.completedCount());
    assertEquals(1, lockProbe.maximumConcurrentCount());
  }

  @Test
  void sameResourceAllowsConcurrentReadersButWriterRemainsExclusive()
    throws Exception {
    runConcurrent(8, lockProbe::read);
    org.junit.jupiter.api.Assertions.assertTrue(
      lockProbe.maximumReaderCount() > 1,
      "同一资源的读锁应允许并发读取"
    );

    lockProbe.resetOverlap();
    AtomicInteger chooser = new AtomicInteger();
    runConcurrent(8, () -> {
      if ((chooser.getAndIncrement() & 1) == 0) lockProbe.read();
      else lockProbe.writeResource();
    });
    assertEquals(0, lockProbe.writerReaderOverlapCount());
    assertEquals(1, lockProbe.maximumWriterCount());
  }

  @Test
  void concurrentBorrowApplicationsCreateOnlyOnePendingApplication()
    throws Exception {
    TestData data = createReaderAndBook();
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger conflicted = new AtomicInteger();

    runConcurrent(10, () -> {
      login(data.readerId(), data.readerRoleId());
      try {
        BorrowApplicationCreateRequest request =
          new BorrowApplicationCreateRequest();
        request.setBookId(data.bookId());
        request.setRequestedDays(14);
        borrowApplicationService.create(request);
        succeeded.incrementAndGet();
      } catch (ConflictException exception) {
        conflicted.incrementAndGet();
      } finally {
        LoginUserContext.clear();
      }
    });

    assertEquals(1, succeeded.get());
    assertEquals(9, conflicted.get());
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from borrow_application " +
        "where member_id=? and book_id=? and status='PENDING'",
        Integer.class,
        data.readerId(),
        data.bookId()
      )
    );
  }

  @Test
  void concurrentRenewalApplicationsCreateOnlyOnePendingRequest()
    throws Exception {
    TestData data = createReaderAndBook();
    Long copyId = jdbc.queryForObject(
      "select id from book_copy where book_id=?",
      Long.class,
      data.bookId()
    );
    Long staffRoleId = jdbc.queryForObject(
      "select id from `role` where code='LIBRARIAN'",
      Long.class
    );
    String staffEmail = "renew-lock-staff-" +
      UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    String staffName = "续借并发馆员" +
      UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
      "insert into member(member_type,role_id,name,email,password_hash,account_status," +
      "qualification_status,borrowed_book_count,permanent_borrow_limit," +
      "email_verified_at,accepted_rule_version,version) " +
      "values('EMPLOYEE',?,?,?,'synthetic-test-hash','ACTIVE','APPROVED'," +
      "0,5,current_timestamp,'BORROWING-1',0)",
      staffRoleId,
      staffName,
      staffEmail
    );
    Long staffId = jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      staffEmail
    );
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
      "update book_copy set status='BORROWED',version=version+1 where id=?",
      copyId
    );
    jdbc.update(
      "update member set borrowed_book_count=1,version=version+1 where id=?",
      data.readerId()
    );
    jdbc.update(
      "insert into borrow_application(application_type,member_id,book_id," +
      "requested_days,status,submitted_at,expires_at,processed_at,processed_by," +
      "approved_copy_id,approved_days,version) " +
      "values('BORROW',?,?,14,'APPROVED',?,?,?,?,?,14,1)",
      data.readerId(),
      data.bookId(),
      now.minusDays(1),
      now.plusDays(6),
      now.minusDays(1),
      staffId,
      copyId
    );
    Long borrowApplicationId = jdbc.queryForObject(
      "select max(id) from borrow_application where member_id=? and book_id=?",
      Long.class,
      data.readerId(),
      data.bookId()
    );
    jdbc.update(
      "insert into borrow_record(application_id,member_id,copy_id,borrowed_at," +
      "due_at,status,overdue_days,approved_by,renew_count,version) " +
      "values(?,?,?,?,?,'BORROWING',0,?,0,0)",
      borrowApplicationId,
      data.readerId(),
      copyId,
      now.minusDays(1),
      now.plusDays(13),
      staffId
    );
    Long recordId = jdbc.queryForObject(
      "select max(id) from borrow_record where application_id=?",
      Long.class,
      borrowApplicationId
    );

    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger conflicted = new AtomicInteger();
    runConcurrent(10, () -> {
      login(data.readerId(), data.readerRoleId());
      try {
        BorrowApplicationCreateRequest request =
          new BorrowApplicationCreateRequest();
        request.setApplicationType(BorrowApplicationType.RENEW);
        request.setBorrowRecordId(recordId);
        request.setRequestedDays(7);
        borrowApplicationService.create(request);
        succeeded.incrementAndGet();
      } catch (ConflictException exception) {
        conflicted.incrementAndGet();
      } finally {
        LoginUserContext.clear();
      }
    });

    assertEquals(1, succeeded.get());
    assertEquals(9, conflicted.get());
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from borrow_application " +
        "where borrow_record_id=? and application_type='RENEW' and status='PENDING'",
        Integer.class,
        recordId
      )
    );
  }

  @Test
  void concurrentBorrowApprovalAndCopyMaintenanceRemainConsistent()
    throws Exception {
    PendingBorrow pending = createPendingBorrow();
    StaffData librarian = createLibrarian();
    AtomicInteger approved = new AtomicInteger();
    AtomicInteger movedToMaintenance = new AtomicInteger();
    AtomicInteger conflicted = new AtomicInteger();
    AtomicInteger operationChooser = new AtomicInteger();

    runConcurrent(2, () -> {
      loginLibrarian(librarian);
      try {
        if (operationChooser.getAndIncrement() == 0) {
          BookCopyUpdateRequest update = new BookCopyUpdateRequest();
          update.setStatus(BookCopyStatus.MAINTENANCE);
          update.setExpectedVersion(0L);
          bookCopyService.updateBookCopy(pending.copyId(), update);
          movedToMaintenance.incrementAndGet();
        } else {
          BorrowApplicationApproveRequest approval =
            new BorrowApplicationApproveRequest();
          approval.setCopyId(pending.copyId());
          approval.setApprovedDays(14);
          approval.setExpectedVersion(0L);
          borrowApplicationService.approve(pending.applicationId(), approval);
          approved.incrementAndGet();
        }
      } catch (ConflictException exception) {
        conflicted.incrementAndGet();
      } finally {
        LoginUserContext.clear();
      }
    });

    assertEquals(1, approved.get() + movedToMaintenance.get());
    assertEquals(1, conflicted.get());
    if (approved.get() == 1) {
      assertEquals(
        "APPROVED",
        jdbc.queryForObject(
          "select status from borrow_application where id=?",
          String.class,
          pending.applicationId()
        )
      );
      assertEquals(
        "BORROWED",
        jdbc.queryForObject(
          "select status from book_copy where id=?",
          String.class,
          pending.copyId()
        )
      );
      assertEquals(
        1,
        jdbc.queryForObject(
          "select count(*) from borrow_record where application_id=?",
          Integer.class,
          pending.applicationId()
        )
      );
    } else {
      assertEquals(
        "PENDING",
        jdbc.queryForObject(
          "select status from borrow_application where id=?",
          String.class,
          pending.applicationId()
        )
      );
      assertEquals(
        "MAINTENANCE",
        jdbc.queryForObject(
          "select status from book_copy where id=?",
          String.class,
          pending.copyId()
        )
      );
      assertEquals(
        0,
        jdbc.queryForObject(
          "select count(*) from borrow_record where application_id=?",
          Integer.class,
          pending.applicationId()
        )
      );
    }
  }

  @Test
  void concurrentBorrowApprovalAndMemberQuotaUpdateRemainConsistent()
    throws Exception {
    PendingBorrow pending = createPendingBorrow();
    StaffData librarian = createLibrarian();
    AtomicInteger approved = new AtomicInteger();
    AtomicInteger quotaUpdated = new AtomicInteger();
    AtomicInteger conflicted = new AtomicInteger();
    AtomicInteger operationChooser = new AtomicInteger();

    runConcurrent(2, () -> {
      loginLibrarian(librarian);
      try {
        if (operationChooser.getAndIncrement() == 0) {
          Reader update = new Reader();
          update.setMemberType(MemberType.READER);
          update.setPermanentBorrowLimit(0);
          memberService.updateReader(pending.data().readerId(), update, null, 0L);
          quotaUpdated.incrementAndGet();
        } else {
          BorrowApplicationApproveRequest approval =
            new BorrowApplicationApproveRequest();
          approval.setCopyId(pending.copyId());
          approval.setApprovedDays(14);
          approval.setExpectedVersion(0L);
          borrowApplicationService.approve(pending.applicationId(), approval);
          approved.incrementAndGet();
        }
      } catch (ConflictException exception) {
        conflicted.incrementAndGet();
      } finally {
        LoginUserContext.clear();
      }
    });

    assertEquals(1, approved.get() + quotaUpdated.get());
    assertEquals(1, conflicted.get());
    assertEquals(
      approved.get() == 1 ? 1 : 0,
      jdbc.queryForObject(
        "select borrowed_book_count from member where id=?",
        Integer.class,
        pending.data().readerId()
      )
    );
    assertEquals(
      approved.get() == 1 ? 5 : 0,
      jdbc.queryForObject(
        "select permanent_borrow_limit from member where id=?",
        Integer.class,
        pending.data().readerId()
      )
    );
    assertEquals(
      approved.get() == 1 ? "APPROVED" : "PENDING",
      jdbc.queryForObject(
        "select status from borrow_application where id=?",
        String.class,
        pending.applicationId()
      )
    );
    assertEquals(
      approved.get() == 1 ? "BORROWED" : "AVAILABLE",
      jdbc.queryForObject(
        "select status from book_copy where id=?",
        String.class,
        pending.copyId()
      )
    );
    assertEquals(
      approved.get() == 1 ? 1 : 0,
      jdbc.queryForObject(
        "select count(*) from borrow_record where application_id=?",
        Integer.class,
        pending.applicationId()
      )
    );
  }

  @Test
  void concurrentApprovalsForOneCopyAllowAtMostOneBorrow() throws Exception {
    SharedCopyData data = createReadersWithPendingApplications(10);
    StaffData librarian = createLibrarian();
    AtomicInteger approved = new AtomicInteger();
    AtomicInteger conflicted = new AtomicInteger();
    AtomicInteger nextApprovalIndex = new AtomicInteger();

    runConcurrent(data.applicationIds().size(), () -> {
      int index = nextApprovalIndex.getAndIncrement();
      loginLibrarian(librarian);
      try {
        BorrowApplicationApproveRequest approval =
          new BorrowApplicationApproveRequest();
        approval.setCopyId(data.copyId());
        approval.setApprovedDays(14);
        approval.setExpectedVersion(0L);
        borrowApplicationService.approve(
          data.applicationIds().get(index),
          approval
        );
        approved.incrementAndGet();
      } catch (ConflictException exception) {
        conflicted.incrementAndGet();
      } finally {
        LoginUserContext.clear();
      }
    });

    assertEquals(1, approved.get());
    assertEquals(data.applicationIds().size() - 1, conflicted.get());
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from borrow_record where copy_id=? " +
        "and status='BORROWING' and returned_at is null",
        Integer.class,
        data.copyId()
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from book_copy where id=? and status='BORROWED'",
        Integer.class,
        data.copyId()
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from borrow_application " +
        "where id in (" + placeholders(data.applicationIds().size()) + ") " +
        "and status='APPROVED'",
        Integer.class,
        data.applicationIds().toArray()
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select sum(borrowed_book_count) from member where id in (" +
        placeholders(data.readerIds().size()) + ")",
        Integer.class,
        data.readerIds().toArray()
      )
    );
  }

  @Test
  void concurrentDifferentBooksCannotExceedLastReaderQuotaSlot()
    throws Exception {
    TestData reader = createReaderAndBook();
    jdbc.update(
      "update member set borrowed_book_count=4,permanent_borrow_limit=5 where id=?",
      reader.readerId()
    );
    login(reader.readerId(), reader.readerRoleId());
    Long firstBookId = reader.bookId();
    Long secondBookId = createBookWithCopy("QUOTA" + UUID.randomUUID()
      .toString().substring(0, 8));
    Long firstApplicationId = createPendingApplication(firstBookId);
    Long secondApplicationId = createPendingApplication(secondBookId);
    LoginUserContext.clear();
    Long firstCopyId = jdbc.queryForObject(
      "select id from book_copy where book_id=?",
      Long.class,
      firstBookId
    );
    Long secondCopyId = jdbc.queryForObject(
      "select id from book_copy where book_id=?",
      Long.class,
      secondBookId
    );
    StaffData librarian = createLibrarian();
    AtomicInteger approved = new AtomicInteger();
    AtomicInteger conflicted = new AtomicInteger();
    AtomicInteger operationChooser = new AtomicInteger();
    List<Long> applicationIds = List.of(firstApplicationId, secondApplicationId);
    List<Long> copyIds = List.of(firstCopyId, secondCopyId);

    runConcurrent(2, () -> {
      int index = operationChooser.getAndIncrement();
      loginLibrarian(librarian);
      try {
        BorrowApplicationApproveRequest approval =
          new BorrowApplicationApproveRequest();
        approval.setCopyId(copyIds.get(index));
        approval.setApprovedDays(14);
        approval.setExpectedVersion(0L);
        borrowApplicationService.approve(applicationIds.get(index), approval);
        approved.incrementAndGet();
      } catch (ConflictException exception) {
        conflicted.incrementAndGet();
      } finally {
        LoginUserContext.clear();
      }
    });

    assertEquals(1, approved.get());
    assertEquals(1, conflicted.get());
    assertEquals(
      5,
      jdbc.queryForObject(
        "select borrowed_book_count from member where id=?",
        Integer.class,
        reader.readerId()
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from borrow_record where member_id=? " +
        "and application_id in (?,?)",
        Integer.class,
        reader.readerId(),
        firstApplicationId,
        secondApplicationId
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from book_copy where id in (?,?) and status='BORROWED'",
        Integer.class,
        firstCopyId,
        secondCopyId
      )
    );
  }

  @Test
  void concurrentReadingPlanCreationCreatesOnlyOnePlan() throws Exception {
    TestData data = createReaderAndBook();
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger conflicted = new AtomicInteger();

    runConcurrent(10, () -> {
      login(data.readerId(), data.readerRoleId());
      try {
        ReadingPlanCreateRequest request = new ReadingPlanCreateRequest();
        request.setName("并发阅读计划");
        request.setTargetBooks(5);
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusMonths(1));
        readingPlanService.createMine(request);
        succeeded.incrementAndGet();
      } catch (ConflictException exception) {
        conflicted.incrementAndGet();
      } finally {
        LoginUserContext.clear();
      }
    });

    assertEquals(1, succeeded.get());
    assertEquals(9, conflicted.get());
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from reading_plan where member_id=?",
        Integer.class,
        data.readerId()
      )
    );
  }

  private void runConcurrent(int requestCount, CheckedRunnable task)
    throws Exception {
    CountDownLatch ready = new CountDownLatch(requestCount);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(requestCount);
    List<Future<?>> results = new ArrayList<>();

    try {
      for (int index = 0; index < requestCount; index++) {
        results.add(
          executor.submit(() -> {
            ready.countDown();
            start.await();
            task.run();
            return null;
          })
        );
      }
      ready.await();
      start.countDown();
      for (Future<?> result : results) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private TestData createReaderAndBook() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Long readerRoleId = jdbc.queryForObject(
      "select id from `role` where code='READER'",
      Long.class
    );
    String email = "concurrent-" + suffix + "@test.local";
    jdbc.update(
      "insert into member(member_type,role_id,name,email,password_hash," +
      "account_status,qualification_status,borrowed_book_count," +
      "permanent_borrow_limit,email_verified_at,accepted_rule_version,version) " +
      "values('READER',?,?,?,'synthetic-test-hash','ACTIVE','APPROVED',0,5,current_timestamp," +
      "'BORROWING-1',0)",
      readerRoleId,
      "并发读者" + suffix,
      email
    );
    Long readerId = jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
    String categoryCode = "LC" + suffix;
    jdbc.update(
      "insert into book_category(code,name,status,version) " +
      "values(?,?,'ACTIVE',0)",
      categoryCode,
      "并发分类" + suffix
    );
    Long categoryId = jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      categoryCode
    );
    String isbn = "LOCK" + suffix;
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) " +
      "values(?,?,?,?,?,'ACTIVE',0)",
      categoryId,
      "并发图书" + suffix,
      "测试作者",
      isbn,
      "zh-CN"
    );
    Long bookId = jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      isbn
    );
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) " +
      "values(?,?,?,'AVAILABLE',0)",
      bookId,
      "LOCK-COPY-" + suffix,
      "并发测试区"
    );
    return new TestData(readerId, readerRoleId, bookId);
  }

  private PendingBorrow createPendingBorrow() {
    TestData data = createReaderAndBook();
    Long copyId = jdbc.queryForObject(
      "select id from book_copy where book_id=?",
      Long.class,
      data.bookId()
    );
    login(data.readerId(), data.readerRoleId());
    BorrowApplicationCreateRequest request =
      new BorrowApplicationCreateRequest();
    request.setBookId(data.bookId());
    request.setRequestedDays(14);
    var application = borrowApplicationService.create(request);
    LoginUserContext.clear();
    return new PendingBorrow(data, application.getId(), copyId);
  }

  private SharedCopyData createReadersWithPendingApplications(int count) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Long readerRoleId = jdbc.queryForObject(
      "select id from `role` where code='READER'",
      Long.class
    );
    Long categoryId = createCategory("SC" + suffix);
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) " +
      "values(?,?,?,?,?,'ACTIVE',0)",
      categoryId,
      "共享副本图书" + suffix,
      "并发测试",
      "SCISBN" + suffix,
      "zh-CN"
    );
    Long bookId = jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      "SCISBN" + suffix
    );
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) " +
      "values(?,?,?,'AVAILABLE',0)",
      bookId,
      "SC-COPY-" + suffix,
      "并发测试区"
    );
    Long copyId = jdbc.queryForObject(
      "select id from book_copy where book_id=?",
      Long.class,
      bookId
    );
    List<Long> readerIds = new ArrayList<>();
    List<Long> applicationIds = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String email = "same-copy-" + suffix + "-" + index + "@test.local";
      jdbc.update(
        "insert into member(member_type,role_id,name,email,password_hash,account_status," +
        "qualification_status,borrowed_book_count,permanent_borrow_limit," +
        "email_verified_at,accepted_rule_version,version) " +
        "values('READER',?,?,?,'synthetic-test-hash','ACTIVE','APPROVED'," +
        "0,5,current_timestamp,'BORROWING-1',0)",
        readerRoleId,
        "共享副本读者" + suffix + "-" + index,
        email
      );
      Long memberId = jdbc.queryForObject(
        "select id from member where email=?",
        Long.class,
        email
      );
      readerIds.add(memberId);
      login(memberId, readerRoleId);
      BorrowApplicationCreateRequest request =
        new BorrowApplicationCreateRequest();
      request.setBookId(bookId);
      request.setRequestedDays(14);
      applicationIds.add(borrowApplicationService.create(request).getId());
      LoginUserContext.clear();
    }
    return new SharedCopyData(bookId, copyId, readerIds, applicationIds);
  }

  private Long createCategory(String suffix) {
    jdbc.update(
      "insert into book_category(code,name,status,version) " +
      "values(?,?,'ACTIVE',0)",
      suffix,
      "并发分类" + suffix
    );
    return jdbc.queryForObject(
      "select id from book_category where code=?",
      Long.class,
      suffix
    );
  }

  private Long createBookWithCopy(String suffix) {
    Long categoryId = createCategory("QC" + suffix);
    String isbn = "QI" + suffix;
    jdbc.update(
      "insert into book(category_id,title,author,isbn,language,status,version) " +
      "values(?,?,?,?,?,'ACTIVE',0)",
      categoryId,
      "额度竞争图书" + suffix,
      "并发测试",
      isbn,
      "zh-CN"
    );
    Long bookId = jdbc.queryForObject(
      "select id from book where isbn=?",
      Long.class,
      isbn
    );
    jdbc.update(
      "insert into book_copy(book_id,barcode,location,status,version) " +
      "values(?,?,?,'AVAILABLE',0)",
      bookId,
      "QC-COPY-" + suffix,
      "并发测试区"
    );
    return bookId;
  }

  private Long createPendingApplication(Long bookId) {
    BorrowApplicationCreateRequest request =
      new BorrowApplicationCreateRequest();
    request.setBookId(bookId);
    request.setRequestedDays(14);
    return borrowApplicationService.create(request).getId();
  }

  private String placeholders(int count) {
    return String.join(",", java.util.Collections.nCopies(count, "?"));
  }

  private StaffData createLibrarian() {
    Long roleId = jdbc.queryForObject(
      "select id from `role` where code='LIBRARIAN'",
      Long.class
    );
    String email = "cross-module-" +
      UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    String name = "跨模块并发馆员" +
      UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
      "insert into member(member_type,role_id,name,email,password_hash,account_status," +
      "qualification_status,borrowed_book_count,permanent_borrow_limit," +
      "email_verified_at,accepted_rule_version,version) " +
      "values('EMPLOYEE',?,?,?,'synthetic-test-hash','ACTIVE','APPROVED'," +
      "0,5,current_timestamp,'BORROWING-1',0)",
      roleId,
      name,
      email
    );
    Long memberId = jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
    return new StaffData(memberId, roleId, email);
  }

  private void loginLibrarian(StaffData staff) {
    LoginUserContext.set(
      new LoginSession(
        "cross-module-concurrency-test",
        staff.memberId(),
        staff.roleId(),
        MemberType.EMPLOYEE,
        "LIBRARIAN",
        staff.email(),
        "并发测试馆员"
      )
    );
  }

  private void login(Long memberId, Long roleId) {
    LoginUserContext.set(
      new LoginSession(
        "concurrent-test",
        memberId,
        roleId,
        MemberType.READER,
        "READER",
        "concurrent@test.local",
        "并发测试"
      )
    );
  }

  @TestConfiguration
  static class LockTestConfiguration {

    @Bean
    LockProbe lockProbe() {
      return new LockProbe();
    }
  }

  static class LockProbe {

    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger maximumConcurrent = new AtomicInteger();
    private final AtomicInteger activeReaders = new AtomicInteger();
    private final AtomicInteger activeWriters = new AtomicInteger();
    private final AtomicInteger maximumReaders = new AtomicInteger();
    private final AtomicInteger maximumWriters = new AtomicInteger();
    private final AtomicInteger overlaps = new AtomicInteger();

    @DistributedLock("lock:test:distributed-lock-integration")
    public void write() throws InterruptedException {
      int current = active.incrementAndGet();
      maximumConcurrent.accumulateAndGet(current, Math::max);
      try {
        Thread.sleep(40);
        completed.incrementAndGet();
      } finally {
        active.decrementAndGet();
      }
    }

    @DistributedLock(
      value = "lock:test:read-write-resource",
      mode = LockMode.READ
    )
    public void read() throws InterruptedException {
      int readers = activeReaders.incrementAndGet();
      maximumReaders.accumulateAndGet(readers, Math::max);
      if (activeWriters.get() > 0) overlaps.incrementAndGet();
      try {
        Thread.sleep(40);
      } finally {
        activeReaders.decrementAndGet();
      }
    }

    @DistributedLock("lock:test:read-write-resource")
    public void writeResource() throws InterruptedException {
      int writers = activeWriters.incrementAndGet();
      maximumWriters.accumulateAndGet(writers, Math::max);
      if (activeReaders.get() > 0) overlaps.incrementAndGet();
      try {
        Thread.sleep(40);
      } finally {
        activeWriters.decrementAndGet();
      }
    }

    @DistributedLock(keys = { "lock:test:partial:a", "lock:test:partial:b" })
    public void partialMultiLock() {
      throw new AssertionError("第二把锁被占用时不应进入业务方法");
    }

    void resetOverlap() {
      activeReaders.set(0);
      activeWriters.set(0);
      maximumWriters.set(0);
      overlaps.set(0);
    }

    int maximumReaderCount() { return maximumReaders.get(); }
    int maximumWriterCount() { return maximumWriters.get(); }
    int writerReaderOverlapCount() { return overlaps.get(); }

    int completedCount() {
      return completed.get();
    }

    int maximumConcurrentCount() {
      return maximumConcurrent.get();
    }
  }

  private record TestData(Long readerId, Long readerRoleId, Long bookId) {}

  private record PendingBorrow(
    TestData data,
    Long applicationId,
    Long copyId
  ) {}

  private record StaffData(Long memberId, Long roleId, String email) {}

  private record SharedCopyData(
    Long bookId,
    Long copyId,
    List<Long> readerIds,
    List<Long> applicationIds
  ) {}

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
