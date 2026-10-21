package com.yunfeil161.ailibrary.operations;

import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.common.api.Result;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 统计、模板、作业聚合查询及授权文件下载。 */
@RestController
@RequestMapping("/admin/operations")
@RequiredArgsConstructor
public class OperationsController {

  private final OperationsReportService reports;
  private final OperationsJobsService jobs;

  @GetMapping("/overview")
  public Result overview(
    @RequestParam(required = false) LocalDate from,
    @RequestParam(required = false) LocalDate to
  ) {
    return Result.success(reports.overview(from, to));
  }

  @GetMapping("/jobs")
  public Result query(@ModelAttribute OperationJobQuery query) {
    return Result.success(jobs.query(query));
  }

  @PostMapping("/imports")
  public Result upload(@RequestPart("file") MultipartFile file) {
    return Result.success(jobs.upload(file));
  }

  @PostMapping("/exports")
  public Result export(@RequestBody BookQueryRequest query) {
    return Result.success(jobs.export(query));
  }

  @GetMapping("/template")
  public ResponseEntity<byte[]> template() {
    return file(jobs.template(), "book-import-template.xlsx");
  }

  @GetMapping("/jobs/{id}/file")
  public ResponseEntity<byte[]> download(@PathVariable String id) {
    return file(jobs.download(id), "operation-report.xlsx");
  }

  private ResponseEntity<byte[]> file(byte[] bytes, String name) {
    return ResponseEntity.ok()
      .header("Cache-Control", "no-store")
      .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
      .contentType(
        MediaType.parseMediaType(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
      )
      .body(bytes);
  }
}
