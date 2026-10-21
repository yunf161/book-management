package com.yunfeil161.ailibrary.book.borrow.controller;

import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowApplicationService;
import com.yunfeil161.ailibrary.common.api.Result;
import lombok.RequiredArgsConstructor;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import org.springframework.web.bind.annotation.*;

/**
 * 借阅申请 HTTP 接口，分别提供读者本人操作和馆员审批操作。
 */
@RestController
@RequiredArgsConstructor
public class BorrowApplicationController {
    private final BorrowApplicationService service;
    private final PermissionAccess permissionAccess;

    /** 创建当前读者的借阅申请。 */
    @PostMapping("/borrow-applications")
    public Result create(@RequestBody BorrowApplicationCreateRequest r) {
        permissionAccess.require(PermissionCodes.BORROW_APPLICATION_CREATE, DataScope.SELF);
        return Result.success(service.create(r));
    }

    /** 聚合查询当前读者自己的申请。 */
    @GetMapping("/borrow-applications")
    public Result mine(@ModelAttribute BorrowApplicationQueryRequest q) {
        permissionAccess.require(PermissionCodes.BORROW_APPLICATION_QUERY, DataScope.SELF);
        return Result.success(service.queryMine(q));
    }

    /** 取消当前读者自己的待审批申请。 */
    @PostMapping("/borrow-applications/{id}/cancel")
    public Result cancel(@PathVariable Long id, @RequestBody BorrowApplicationCancelRequest r) {
        permissionAccess.require(PermissionCodes.BORROW_APPLICATION_CANCEL, DataScope.SELF);
        return Result.success(service.cancel(id, r));
    }

    /** 馆员按组合条件查询申请。 */
    @GetMapping("/admin/borrow-applications")
    public Result admin(@ModelAttribute BorrowApplicationQueryRequest q) {
        permissionAccess.require(PermissionCodes.BORROW_APPLICATION_QUERY, DataScope.ALL);
        return Result.success(service.queryAdmin(q));
    }

    /** 批准申请并创建借阅记录。 */
    @PostMapping("/admin/borrow-applications/{id}/approve")
    public Result approve(@PathVariable Long id, @RequestBody BorrowApplicationApproveRequest r) {
        permissionAccess.require(PermissionCodes.BORROW_APPLICATION_APPROVE, DataScope.ALL);
        return Result.success(service.approve(id, r));
    }

    /** 拒绝待审批申请。 */
    @PostMapping("/admin/borrow-applications/{id}/reject")
    public Result reject(@PathVariable Long id, @RequestBody BorrowApplicationRejectRequest r) {
        permissionAccess.require(PermissionCodes.BORROW_APPLICATION_REJECT, DataScope.ALL);
        return Result.success(service.reject(id, r));
    }
}
