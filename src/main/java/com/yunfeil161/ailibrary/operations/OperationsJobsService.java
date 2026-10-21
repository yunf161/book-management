package com.yunfeil161.ailibrary.operations;

import com.github.pagehelper.*;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.book.catalog.service.BookService;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 有界异步馆藏作业。导入按行事务，重复上传同一文件返回原作业，不重复新增副本。 */
@Service
@RequiredArgsConstructor
public class OperationsJobsService {

  public static final List<String> HEADERS = List.of(
    "categoryCode",
    "title",
    "subtitle",
    "author",
    "isbn",
    "publisher",
    "publishedDate",
    "edition",
    "language",
    "description",
    "coverObjectKey",
    "tagIds",
    "barcode",
    "callNumber",
    "location",
    "acquiredAt",
    "price",
    "conditionNote"
  );
  private final PermissionAccess access;
  private final OperationJobMapper mapper;
  private final JdbcTemplate jdbc;
  private final ImportRowWriter writer;
  private final BookService books;
  private final ExecutorService workers = new ThreadPoolExecutor(
    1,
    1,
    0,
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(8),
    new ThreadPoolExecutor.AbortPolicy()
  );

  public OperationJob upload(MultipartFile file) {
    var session = access.require("operations:import", DataScope.ALL);
    if (
      file == null ||
      file.isEmpty() ||
      file.getSize() > 2000000 ||
      file.getOriginalFilename() == null ||
      !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")
    ) throw new WrongFormatException("只接受2MB以内的xlsx文件");
    try {
      return create("IMPORT", file.getBytes(), session);
    } catch (IOException error) {
      throw new WrongFormatException("无法读取上传文件");
    }
  }

  public OperationJob export(BookQueryRequest query) {
    return create(
      "EXPORT",
      JsonUtils.toJson(query).getBytes(StandardCharsets.UTF_8),
      access.require("operations:export", DataScope.ALL)
    );
  }

  private OperationJob create(String type, byte[] input, LoginSession session) {
    var job = new OperationJob();
    job.setJobId(UUID.randomUUID().toString());
    job.setMemberId(session.getMemberId());
    job.setType(type);
    job.setInputData(input);
    try {
      job.setRequestHash(
        HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(
            (
              session.getMemberId() +
              ":" +
              type +
              ":" +
              (type.equals("EXPORT")
                ? job.getJobId()
                : HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input)
                  ))
            ).getBytes(StandardCharsets.UTF_8)
          )
        )
      );
    } catch (java.security.GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
    var previous = mapper.hash(job.getRequestHash());
    if (previous != null) return previous;
    try {
      mapper.insert(job);
    } catch (DuplicateKeyException duplicate) {
      var existing = mapper.hash(job.getRequestHash());
      if (existing != null) return existing;
      throw duplicate;
    }
    try {
      workers.execute(() -> run(job.getJobId(), session));
    } catch (RejectedExecutionException busy) {
      fail(job.getJobId(), "QUEUE_FULL");
      throw new ServiceUnavailableException("导入导出队列繁忙，请稍后重试");
    }
    return mapper.find(job.getJobId());
  }

  public PageResult<OperationJob> query(OperationJobQuery query) {
    var session = access.require("operations:query", DataScope.ALL);
    PageHelper.startPage(
      query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage(),
      query.getPageSize() == null || query.getPageSize() < 1
        ? 10
        : Math.min(query.getPageSize(), 100)
    );
    var page = new PageInfo<>(mapper.query(session.getMemberId(), query));
    return new PageResult<>(page.getTotal(), page.getList());
  }

  public byte[] download(String id) {
    var session = access.require("operations:query", DataScope.ALL);
    var job = mapper.find(id);
    if (
      job == null || !job.getMemberId().equals(session.getMemberId())
    ) throw new NotFoundException("作业不存在或不属于本人");
    access.require(
      "IMPORT".equals(job.getType())
        ? "operations:import"
        : "operations:export",
      DataScope.ALL
    );
    if (
      !Set.of("SUCCEEDED", "PARTIAL").contains(job.getState())
    ) throw new ConflictException("作业尚未生成下载文件");
    return jdbc.queryForObject(
      "select result_data from operation_job where job_id=?",
      (rs, index) -> rs.getBytes(1),
      id
    );
  }

  public byte[] template() {
    access.require("operations:import", DataScope.ALL);
    return workbook(List.of(HEADERS));
  }

  void run(String id, LoginSession session) {
    if (
      jdbc.update(
        "update operation_job set state='RUNNING',updated_at=current_timestamp(3) where job_id=? and state='QUEUED'",
        id
      ) != 1
    ) return;
    LoginUserContext.set(session);
    try {
      var job = mapper.find(id);
      var input = jdbc.queryForObject(
        "select input_data from operation_job where job_id=?",
        (rs, index) -> rs.getBytes(1),
        id
      );
      if ("IMPORT".equals(job.getType())) importRows(id, input);
      else exportRows(id, input);
    } catch (Exception error) {
      fail(id, error.getClass().getSimpleName());
    } finally {
      LoginUserContext.clear();
    }
  }

  private void importRows(String id, byte[] input) throws IOException {
    var output = new ArrayList<List<String>>();
    output.add(List.of("row", "result", "bookId", "copyId", "error"));
    int success = 0,
      failed = 0;
    org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(20000000);
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(input))) {
      if (
        book
          .getPackagePart()
          .getContentType()
          .toLowerCase(Locale.ROOT)
          .contains("macroenabled") ||
        !book.getExternalLinksTable().isEmpty()
      ) throw new WrongFormatException("不接受宏或外部链接工作簿");
      if (book.getNumberOfSheets() != 1) throw new WrongFormatException(
        "请只保留一张导入工作表"
      );
      var sheet = book.getSheetAt(0);
      int total = sheet.getLastRowNum();
      if (total > 1000 || total < 1) throw new WrongFormatException(
        "一次导入1至1000行"
      );
      var formatter = new DataFormatter(Locale.ROOT);
      var header = sheet.getRow(0);
      if (
        header == null || header.getLastCellNum() > HEADERS.size()
      ) throw new WrongFormatException("模板表头错误");
      var fields = new ArrayList<String>();
      for (int column = 0; column < header.getLastCellNum(); column++) {
        var cell = header.getCell(column);
        String name = formatter.formatCellValue(cell).trim();
        if (
          !HEADERS.contains(name) || fields.contains(name)
        ) throw new WrongFormatException("模板存在空白、未知或重复字段");
        fields.add(name);
      }
      if (
        !fields.containsAll(List.of("categoryCode", "title", "author"))
      ) throw new WrongFormatException("缺少categoryCode/title/author表头");
      jdbc.update(
        "update operation_job set total_rows=? where job_id=?",
        total,
        id
      );
      for (int i = 1; i <= total; i++) {
        try {
          access.require("operations:import", DataScope.ALL);
          requireActor();
          var source = sheet.getRow(i);
          if (source == null) throw new WrongFormatException("空白行");
          var row = new LinkedHashMap<String, String>();
          for (int column = 0; column < fields.size(); column++) {
            var cell = source.getCell(column);
            if (
              cell != null && cell.getCellType() == CellType.FORMULA
            ) throw new WrongFormatException("不接受公式单元格");
            String value =
              cell != null &&
              cell.getCellType() == CellType.NUMERIC &&
              DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                : formatter.formatCellValue(cell).trim();
            if (value.length() > 20000) throw new WrongFormatException(
              "单元格内容过长"
            );
            row.put(fields.get(column), value);
          }
          var categories = jdbc.queryForList(
            "select id from book_category where code=?",
            Long.class,
            row.get("categoryCode")
          );
          if (categories.size() != 1) throw new NotFoundException(
            "分类编码不存在"
          );
          var matching =
            row.get("isbn") == null || row.get("isbn").isBlank()
              ? List.<Long>of()
              : jdbc.queryForList(
                  "select id from book where isbn=?",
                  Long.class,
                  row.get("isbn")
                );
          var result = writer.write(
            categories.get(0),
            row,
            matching.isEmpty() ? null : matching.get(0)
          );
          success++;
          output.add(
            List.of(
              Integer.toString(i + 1),
              "SUCCESS",
              String.valueOf(result.get("bookId")),
              String.valueOf(result.get("copyId")),
              ""
            )
          );
        } catch (Exception error) {
          failed++;
          String message =
            error instanceof WrongFormatException ||
            error instanceof ConflictException ||
            error instanceof NotFoundException ||
            error instanceof ForbiddenException
              ? error.getMessage()
              : "数据格式错误或服务不可用，请修正后重试";
          output.add(
            List.of(
              Integer.toString(i + 1),
              "FAILED",
              "",
              "",
              message == null
                ? "导入失败"
                : message.substring(0, Math.min(400, message.length()))
            )
          );
        }
        jdbc.update(
          "update operation_job set completed_rows=?,success_rows=?,failed_rows=?,updated_at=current_timestamp(3) where job_id=?",
          i,
          success,
          failed,
          id
        );
      }
    }
    finish(
      id,
      failed == 0 ? "SUCCEEDED" : "PARTIAL",
      workbook(output),
      Map.of("rows", output.size() - 1, "success", success, "failed", failed)
    );
  }

  private void exportRows(String id, byte[] input) {
    access.require("operations:export", DataScope.ALL);
    var query = JsonUtils.fromJson(
      new String(input, StandardCharsets.UTF_8),
      BookQueryRequest.class
    );
    query.setPage(1);
    query.setPageSize(10000);
    // 原Service限制单页100；按页导出同一组筛选，并明确最多10000条，防止静默截断。
    query.setPageSize(100);
    var first = books.getBooks(query);
    if (first.getTotal() > 10000) throw new WrongFormatException(
      "结果超过10000条，请缩小查询范围"
    );
    var rows = new ArrayList<List<String>>();
    rows.add(
      List.of(
        "id",
        "title",
        "author",
        "isbn",
        "category",
        "status",
        "totalCopies",
        "availableCopies"
      )
    );
    long total = first.getTotal();
    int pages = (int) Math.max(1, (total + 99) / 100);
    for (int page = 1; page <= pages; page++) {
      requireActor();
      access.require("operations:export", DataScope.ALL);
      query.setPage(page);
      var batch = page == 1 ? first : books.getBooks(query);
      for (var book : batch.getRows())
        rows.add(
          List.of(
            String.valueOf(book.getId()),
            safe(book.getTitle()),
            safe(book.getAuthor()),
            safe(book.getIsbn()),
            safe(book.getCategory()),
            String.valueOf(book.getStatus()),
            String.valueOf(book.getTotalCopies()),
            String.valueOf(book.getAvailableCopies())
          )
        );
      jdbc.update(
        "update operation_job set total_rows=?,completed_rows=?,success_rows=?,updated_at=current_timestamp(3) where job_id=?",
        total,
        rows.size() - 1,
        rows.size() - 1,
        id
      );
    }
    finish(
      id,
      "SUCCEEDED",
      workbook(rows),
      Map.of(
        "rows",
        rows.size() - 1,
        "note",
        "导出为执行时逐页读取的馆藏数据，不是跨页一致性快照"
      )
    );
  }

  private String safe(String text) {
    return text == null ? "" : text;
  }

  /** 排队及逐行执行时复核成员当前角色，不能继续使用已被替换的旧角色快照。 */
  private void requireActor() {
    var session = LoginUserContext.get();
    if (
      jdbc.queryForObject(
        "select count(*) from member where id=? and account_status='ACTIVE' and role_id=?",
        Integer.class,
        session.getMemberId(),
        session.getRoleId()
      ) != 1
    ) throw new ForbiddenException("账号状态或角色已变化");
  }

  private void finish(
    String id,
    String state,
    byte[] file,
    Map<String, Object> result
  ) {
    if (file.length > 10000000) throw new WrongFormatException(
      "导出文件超过限制"
    );
    jdbc.update(
      "update operation_job set state=?,result_data=?,result_json=?,input_data=null,updated_at=current_timestamp(3) where job_id=? and state='RUNNING'",
      state,
      file,
      JsonUtils.toJson(result),
      id
    );
  }

  private void fail(String id, String code) {
    jdbc.update(
      "update operation_job set state='FAILED',error_code=?,input_data=null,updated_at=current_timestamp(3) where job_id=? and state in ('QUEUED','RUNNING')",
      code,
      id
    );
  }

  public byte[] workbook(List<List<String>> rows) {
    try (
      var book = new XSSFWorkbook();
      var output = new ByteArrayOutputStream()
    ) {
      var sheet = book.createSheet("data");
      int index = 0;
      for (var values : rows) {
        var row = sheet.createRow(index++);
        for (int cell = 0; cell < values.size(); cell++) row
          .createCell(cell, CellType.STRING)
          .setCellValue(values.get(cell));
      }
      sheet.createFreezePane(0, 1);
      book.write(output);
      return output.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("生成Excel失败", error);
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }

  /** 崩溃遗留作业标记失败，不自动重放可能已提交的导入行。 */
  @org.springframework.scheduling.annotation.Scheduled(
    fixedDelay = 60000,
    initialDelay = 60000
  )
  public void recover() {
    jdbc.update(
      "update operation_job set state='FAILED',error_code='WORKER_INTERRUPTED',input_data=null,updated_at=current_timestamp(3) where state in ('QUEUED','RUNNING') and updated_at<timestampadd(MINUTE,-30,current_timestamp(3))"
    );
  }
}
