package com.yunfeil161.ailibrary.book.cover.controller;

import com.yunfeil161.ailibrary.book.cover.service.BookCoverService;
import com.yunfeil161.ailibrary.common.api.Result;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 仅管理图书封面；上传直接经后端核验，不开放通用文件上传授权。 */
@RestController
@RequiredArgsConstructor
public class BookCoverController {

  private final BookCoverService service;

  @GetMapping("/books/{bookId}/cover")
  public Result query(@PathVariable Long bookId) {
    return Result.success(service.find(bookId));
  }

  @GetMapping("/books/{bookId}/cover/content")
  public ResponseEntity<Void> content(@PathVariable Long bookId) {
    // 不缓存短期签名，浏览器每次读取都按当前数据库封面生成签名。
    return ResponseEntity.status(302)
      .location(URI.create(service.signedUrl(bookId)))
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .header("Referrer-Policy", "no-referrer")
      .build();
  }

  @PostMapping(
    value = "/admin/books/{bookId}/cover",
    consumes = "multipart/form-data"
  )
  public Result create(
    @PathVariable Long bookId,
    @RequestParam Long expectedVersion,
    @RequestParam MultipartFile file
  ) {
    return Result.success(service.save(bookId, expectedVersion, file, true));
  }

  @PutMapping(
    value = "/admin/books/{bookId}/cover",
    consumes = "multipart/form-data"
  )
  public Result replace(
    @PathVariable Long bookId,
    @RequestParam Long expectedVersion,
    @RequestParam MultipartFile file
  ) {
    return Result.success(service.save(bookId, expectedVersion, file, false));
  }

  @DeleteMapping("/admin/books/{bookId}/cover")
  public Result delete(
    @PathVariable Long bookId,
    @RequestParam Long expectedVersion
  ) {
    return Result.success(service.delete(bookId, expectedVersion));
  }
}
