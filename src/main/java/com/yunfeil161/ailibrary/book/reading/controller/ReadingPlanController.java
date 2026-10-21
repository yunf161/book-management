package com.yunfeil161.ailibrary.book.reading.controller;

import com.yunfeil161.ailibrary.book.reading.dto.*;
import com.yunfeil161.ailibrary.book.reading.service.ReadingPlanService;
import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 当前成员的阅读计划及条目接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reading-plans")
public class ReadingPlanController {

  private final ReadingPlanService service;
  private final PermissionAccess permissionAccess;

  @PostMapping
  public Result create(@RequestBody ReadingPlanCreateRequest r) {
    requireSelf(PermissionCodes.READING_PLAN_CREATE_SELF);
    return Result.success(service.createMine(r));
  }

  @GetMapping
  public Result get() {
    requireSelf(PermissionCodes.READING_PLAN_QUERY_SELF);
    return Result.success(service.getMine());
  }

  @PutMapping
  public Result update(@RequestBody ReadingPlanUpdateRequest r) {
    requireSelf(PermissionCodes.READING_PLAN_UPDATE_SELF);
    return Result.success(service.updateMine(r));
  }

  @DeleteMapping
  public Result delete(@RequestParam Long expectedVersion) {
    requireSelf(PermissionCodes.READING_PLAN_DELETE_SELF);
    return Result.success(service.deleteMine(expectedVersion));
  }

  @PostMapping("/items")
  public Result addItem(@RequestBody ReadingItemCreateRequest r) {
    requireSelf(PermissionCodes.READING_PLAN_UPDATE_SELF);
    return Result.success(service.addMine(r));
  }

  @GetMapping("/items")
  public Result items(@ModelAttribute ReadingItemQueryRequest q) {
    requireSelf(PermissionCodes.READING_PLAN_QUERY_SELF);
    return Result.success(service.queryMineItems(q));
  }

  @PutMapping("/items/{bookId}")
  public Result updateItem(
    @PathVariable Long bookId,
    @RequestBody ReadingItemUpdateRequest r
  ) {
    requireSelf(PermissionCodes.READING_PLAN_UPDATE_SELF);
    return Result.success(service.updateMineItem(bookId, r));
  }

  @DeleteMapping("/items/{bookId}")
  public Result deleteItem(
    @PathVariable Long bookId,
    @RequestParam Long expectedVersion,
    @RequestParam Long expectedPlanVersion
  ) {
    requireSelf(PermissionCodes.READING_PLAN_UPDATE_SELF);
    return Result.success(
      service.deleteMineItem(bookId, expectedVersion, expectedPlanVersion)
    );
  }

  /** 在进入带锁业务方法前完成基础权限校验，避免越权请求占用资源锁。 */
  private void requireSelf(String permissionCode) {
    permissionAccess.require(permissionCode, DataScope.SELF);
  }
}
