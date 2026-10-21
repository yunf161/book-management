package com.yunfeil161.ailibrary.book.reading.controller;

import com.yunfeil161.ailibrary.book.reading.dto.*;
import com.yunfeil161.ailibrary.book.reading.service.ReadingPlanService;
import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 馆员和管理员的聚合阅读计划管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/reading-plans")
public class AdminReadingPlanController {

  private final ReadingPlanService service;
  private final PermissionAccess permissionAccess;

  @PostMapping
  public Result create(@RequestBody ReadingPlanCreateRequest r) {
    permissionAccess.require(PermissionCodes.READING_PLAN_CREATE);
    return Result.success(service.createAdmin(r));
  }

  @GetMapping
  public Result query(@ModelAttribute ReadingPlanQueryRequest q) {
    permissionAccess.require(PermissionCodes.READING_PLAN_QUERY);
    return Result.success(service.queryAdmin(q));
  }

  @PutMapping("/{memberId}")
  public Result update(
    @PathVariable Long memberId,
    @RequestBody ReadingPlanUpdateRequest r
  ) {
    permissionAccess.require(PermissionCodes.READING_PLAN_UPDATE);
    return Result.success(service.updateAdmin(memberId, r));
  }

  @DeleteMapping("/{memberId}")
  public Result delete(
    @PathVariable Long memberId,
    @RequestParam Long expectedVersion
  ) {
    permissionAccess.require(PermissionCodes.READING_PLAN_DELETE);
    return Result.success(service.deleteAdmin(memberId, expectedVersion));
  }

  @PostMapping("/{memberId}/items")
  public Result addItem(
    @PathVariable Long memberId,
    @RequestBody ReadingItemCreateRequest r
  ) {
    permissionAccess.require(PermissionCodes.READING_PLAN_UPDATE);
    return Result.success(service.addAdmin(memberId, r));
  }

  @GetMapping("/items")
  public Result items(@ModelAttribute ReadingItemQueryRequest q) {
    permissionAccess.require(PermissionCodes.READING_PLAN_QUERY);
    return Result.success(service.queryAdminItems(q));
  }

  @PutMapping("/{memberId}/items/{bookId}")
  public Result updateItem(
    @PathVariable Long memberId,
    @PathVariable Long bookId,
    @RequestBody ReadingItemUpdateRequest r
  ) {
    permissionAccess.require(PermissionCodes.READING_PLAN_UPDATE);
    return Result.success(service.updateAdminItem(memberId, bookId, r));
  }

  @DeleteMapping("/{memberId}/items/{bookId}")
  public Result deleteItem(
    @PathVariable Long memberId,
    @PathVariable Long bookId,
    @RequestParam Long expectedVersion,
    @RequestParam Long expectedPlanVersion
  ) {
    permissionAccess.require(PermissionCodes.READING_PLAN_UPDATE);
    return Result.success(
      service.deleteAdminItem(
        memberId,
        bookId,
        expectedVersion,
        expectedPlanVersion
      )
    );
  }
}
