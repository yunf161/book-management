package com.yunfeil161.ailibrary.policy.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.policy.dto.*;
import com.yunfeil161.ailibrary.policy.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 政策管理保持聚合查询；公开端只能读取当前已发布政策。 */
@RestController
@RequiredArgsConstructor
public class PolicyController {

  private final PolicyService policies;
  private final PolicyState state;
  private final RuleConsentService consent;

  @GetMapping("/policies/current")
  public Result current(@RequestParam(defaultValue = "BORROWING") String type) {
    return Result.success(state.view(state.current(type)));
  }

  @GetMapping("/admin/policies")
  public Result query(@ModelAttribute PolicyQueryRequest query) {
    return Result.success(policies.query(query));
  }

  @PostMapping("/admin/policies")
  public Result create(@RequestBody PolicyWriteRequest request) {
    return Result.success(policies.create(request));
  }

  @PutMapping("/admin/policies/{id}")
  public Result update(
    @PathVariable Long id,
    @RequestBody PolicyWriteRequest request
  ) {
    return Result.success(policies.update(id, request));
  }

  @PostMapping("/admin/policies/{id}/publish")
  public Result publish(
    @PathVariable Long id,
    @RequestBody PolicyPublishRequest request
  ) {
    return Result.success(policies.publish(id, request));
  }

  @GetMapping("/me/borrowing-rules")
  public Result rules() {
    return Result.success(consent.current());
  }

  @PostMapping("/me/borrowing-rules/decision")
  public Result decision(@RequestBody RuleDecisionRequest request) {
    return Result.success(consent.decide(request));
  }
}
