package com.yunfeil161.ailibrary.ai.knowledge;

import com.yunfeil161.ailibrary.common.api.Result;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理接口只操作受控知识内容，任务异步执行，页面轮询持久化进度。 */
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

  private final KnowledgeService service;

  @PostMapping(value = "/import-preview", consumes = "multipart/form-data")
  public Result preview(
    @RequestParam("file") org.springframework.web.multipart.MultipartFile file
  ) {
    return Result.success(service.preview(file));
  }

  @GetMapping("/documents")
  public Result documents(@RequestParam Map<String, String> query) {
    return Result.success(service.query("documents", query));
  }

  @GetMapping("/documents/{id}")
  public Result document(@PathVariable String id) {
    return Result.success(
      service.query("documents/" + service.identifier(id), null)
    );
  }

  @GetMapping("/status")
  public Result status() {
    return Result.success(service.query("status", null));
  }

  @GetMapping("/jobs/{id}")
  public Result job(@PathVariable String id) {
    return Result.success(
      service.query("jobs/" + service.identifier(id), null)
    );
  }

  @PostMapping("/documents")
  public Result create(@RequestBody Map<String, Object> body) {
    return Result.success(service.save(null, body));
  }

  @PutMapping("/documents/{id}")
  public Result update(
    @PathVariable String id,
    @RequestBody Map<String, Object> body
  ) {
    return Result.success(service.save(id, body));
  }

  @PostMapping("/rebuild")
  public Result rebuild(@RequestBody Map<String, Object> body) {
    return Result.success(service.rebuild(body));
  }

  @PostMapping("/search")
  public Result search(@RequestBody Map<String, Object> body) {
    return Result.success(service.search(body));
  }
}
