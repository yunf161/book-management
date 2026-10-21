package com.yunfeil161.ailibrary.ai.runtime;

import com.yunfeil161.ailibrary.common.api.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 保留单一任务聚合查询；创建/取消/重试不绕过原有角色和业务权限。 */
@RestController
@RequiredArgsConstructor
public class AiRuntimeController {

  private final AiTaskService tasks;
  private final AiRuntimeControl controls;
  private final AiActionReconciler reconciler;
  private final AiUsageService usage;

  @GetMapping("/ai/usage")
  public Result usage(
    @RequestParam(required = false) java.time.LocalDate from,
    @RequestParam(required = false) java.time.LocalDate to,
    @RequestParam(required = false) Long memberId
  ) {
    return Result.success(usage.query(from, to, memberId));
  }

  public record ControlRequest(Boolean enabled, Long expectedVersion) {}

  @GetMapping("/ai/tasks")
  public Result query(@ModelAttribute AiTaskQuery query) {
    return Result.success(tasks.query(query));
  }

  @PostMapping("/ai/tasks")
  public Result submit(
    @RequestBody AiTaskService.Request request,
    @RequestHeader(value = "Authorization", required = false) String auth,
    HttpServletResponse response
  ) {
    return Result.success(tasks.submit(request, token(auth, response)));
  }

  @PostMapping("/ai/tasks/{id}/cancel")
  public Result cancel(@PathVariable String id) {
    return Result.success(tasks.cancel(id));
  }

  @PostMapping("/ai/tasks/{id}/retry")
  public Result retry(
    @PathVariable String id,
    @RequestHeader(value = "Authorization", required = false) String auth,
    HttpServletResponse response
  ) {
    return Result.success(tasks.retry(id, token(auth, response)));
  }

  @GetMapping("/admin/ai-controls")
  public Result controls() {
    return Result.success(controls.query());
  }

  @PostMapping("/admin/ai-actions/reconcile")
  public Result reconcile() {
    return Result.success(reconciler.manual());
  }

  @PutMapping("/admin/ai-controls/{code}")
  public Result update(
    @PathVariable String code,
    @RequestBody ControlRequest request
  ) {
    return Result.success(
      controls.update(code, request.enabled(), request.expectedVersion())
    );
  }

  private String token(String auth, HttpServletResponse response) {
    String refreshed = response.getHeader("X-Refreshed-Token");
    return refreshed != null
      ? refreshed
      : auth != null && auth.startsWith("Bearer ")
        ? auth.substring(7)
        : null;
  }
}
