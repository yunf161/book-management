package com.yunfeil161.ailibrary.operations;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/** 真实xlsx与逐行事务、原馆藏Service和Redis资源锁集成。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:operations_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
class OperationsIntegrationTests {

  @Autowired
  OperationsJobsService jobs;

  @Autowired
  OperationsReportService reports;

  @Autowired
  OperationJobMapper mapper;

  @Autowired
  JdbcTemplate jdbc;

  Long member;
  String code;

  @BeforeEach
  void setup() {
    code = "OPS-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
      "insert into member(name,member_type,role_id,account_status,qualification_status) select ?,'EMPLOYEE',id,'ACTIVE','APPROVED' from role where code='ADMIN'",
      code
    );
    member = jdbc.queryForObject(
      "select id from member where name=?",
      Long.class,
      code
    );
    login("ADMIN");
    jdbc.update("insert into book_category(code,name) values(?,?)", code, code);
  }

  void login(String role) {
    LoginUserContext.set(
      new LoginSession(
        "test",
        member,
        jdbc.queryForObject(
          "select id from role where code=?",
          Long.class,
          role
        ),
        role.equals("READER") ? MemberType.READER : MemberType.EMPLOYEE,
        role,
        "test@example.test",
        "测试"
      )
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  OperationJob await(String id) throws Exception {
    for (int i = 0; i < 250; i++) {
      var row = mapper.find(id);
      if (
        Set.of("SUCCEEDED", "PARTIAL", "FAILED").contains(row.getState())
      ) return row;
      Thread.sleep(30);
    }
    throw new AssertionError("job timeout");
  }

  MockMultipartFile file(List<List<String>> rows) {
    return new MockMultipartFile(
      "file",
      "books.xlsx",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      jobs.workbook(rows)
    );
  }

  @Test
  void importSuccessFailureReportAndDuplicateUpload() throws Exception {
    var file = file(
      List.of(
        List.of(
          "categoryCode",
          "title",
          "author",
          "isbn",
          "barcode",
          "location",
          "price"
        ),
        List.of(code, "测试图书", "作者", code, "BC-" + code, "A区", "12.50"),
        List.of("MISSING", "错误图书", "作者", "", "", "", "")
      )
    );
    var first = jobs.upload(file);
    var done = await(first.getJobId());
    assertEquals("PARTIAL", done.getState());
    assertEquals(1, done.getSuccessRows());
    assertEquals(1, done.getFailedRows());
    assertEquals(first.getJobId(), jobs.upload(file).getJobId());
    try (
      var report = new XSSFWorkbook(
        new ByteArrayInputStream(jobs.download(first.getJobId()))
      )
    ) {
      assertEquals(2, report.getSheetAt(0).getLastRowNum());
      assertEquals(
        "SUCCESS",
        report.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()
      );
    }
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from book_copy where barcode=?",
        Integer.class,
        "BC-" + code
      )
    );
  }

  @Test
  void duplicateBarcodeRollsBackNewBookInThatRow() throws Exception {
    var initial = file(
      List.of(
        List.of(
          "categoryCode",
          "title",
          "author",
          "isbn",
          "barcode",
          "location"
        ),
        List.of(code, "书一", "作者", code + "1", code, "A")
      )
    );
    assertEquals(
      "SUCCEEDED",
      await(jobs.upload(initial).getJobId()).getState()
    );
    var duplicate = file(
      List.of(
        List.of(
          "categoryCode",
          "title",
          "author",
          "isbn",
          "barcode",
          "location"
        ),
        List.of(code, "书二", "作者", code + "2", code, "A")
      )
    );
    assertEquals(
      "PARTIAL",
      await(jobs.upload(duplicate).getJobId()).getState()
    );
    assertEquals(
      0,
      jdbc.queryForObject(
        "select count(*) from book where isbn=?",
        Integer.class,
        code + "2"
      )
    );
  }

  @Test
  void exportHonorsBookFiltersAndProtectsFiles() throws Exception {
    var query = new BookQueryRequest();
    query.setTitle(code);
    var job = await(jobs.export(query).getJobId());
    assertEquals("SUCCEEDED", job.getState());
    assertTrue(jobs.download(job.getJobId()).length > 100);
    Long owner = member;
    member = owner + 10000;
    login("ADMIN");
    assertThrows(NotFoundException.class, () -> jobs.download(job.getJobId()));
  }

  @Test
  void readerDeniedAndDateRangesValidated() {
    login("READER");
    assertThrows(ForbiddenException.class, () -> reports.overview(null, null));
    assertThrows(ForbiddenException.class, () -> jobs.template());
    login("ADMIN");
    assertThrows(WrongFormatException.class, () ->
      reports.overview(LocalDate.now(), LocalDate.now().minusDays(1))
    );
    assertNotNull(reports.overview(null, null).get("dailyBorrowing"));
  }

  @Test
  void sameIsbnRowsReuseLockedBookAndCreateDistinctCopies() throws Exception {
    var input = file(
      List.of(
        List.of(
          "categoryCode",
          "title",
          "author",
          "isbn",
          "barcode",
          "location"
        ),
        List.of(code, "同版图书", "作者", code, code + "-1", "A"),
        List.of(code, "同版图书", "作者", code, code + "-2", "B")
      )
    );
    var done = await(jobs.upload(input).getJobId());
    assertEquals("SUCCEEDED", done.getState());
    assertEquals(2, done.getSuccessRows());
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from book where isbn=?",
        Integer.class,
        code
      )
    );
    assertEquals(
      2,
      jdbc.queryForObject(
        "select count(*) from book_copy c join book b on b.id=c.book_id where b.isbn=?",
        Integer.class,
        code
      )
    );
  }

  @Test
  void overdueOverviewCountsUnreturnedPastDueOnly() {
    var now = java.time.LocalDateTime.now();
    long before = ((Number) ((Map<?, ?>) reports.overview(null, null)
      .get("totals")).get("currentOverdue")).longValue();
    // 只建立本测试独占的统计样本，不经过审批或修改其他测试的借阅记录。
    for (int i = 0; i < 3; i++) {
      jdbc.update(
        "insert into borrow_record(application_id,member_id,copy_id,borrowed_at,due_at,returned_at,status,approved_by) values(?,?,?,?,?,?,?,?)",
        -member * 10 - i, member, -member * 10 - i, now.minusDays(2),
        i == 1 ? now.plusHours(1) : now.minusHours(1),
        i == 2 ? now : null, i == 2 ? "RETURNED" : "BORROWING", member
      );
    }
    try {
      var totals = (Map<?, ?>) reports.overview(null, null).get("totals");
      assertEquals(before + 1, ((Number) totals.get("currentOverdue")).longValue());
    } finally {
      jdbc.update("delete from borrow_record where member_id=? and copy_id<0", member);
    }
  }

  @Test
  void badWorkbookFailsWithoutBusinessWrites() throws Exception {
    var file = new MockMultipartFile(
      "file",
      "invalid.xlsx",
      "application/octet-stream",
      "invalid".getBytes()
    );
    var job = await(jobs.upload(file).getJobId());
    assertEquals("FAILED", job.getState());
    assertThrows(ConflictException.class, () -> jobs.download(job.getJobId()));
    assertThrows(WrongFormatException.class, () ->
      jobs.upload(
        new MockMultipartFile("file", "bad.exe", "", new byte[] { 1 })
      )
    );
  }
}
