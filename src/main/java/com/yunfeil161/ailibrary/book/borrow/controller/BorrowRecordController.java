package com.yunfeil161.ailibrary.book.borrow.controller;

import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowRecordService;
import com.yunfeil161.ailibrary.common.api.Result;
import lombok.RequiredArgsConstructor;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import org.springframework.web.bind.annotation.*;

/**
 * 借阅记录 HTTP 接口，提供本人查询、馆员查询和归还操作。
 */
@RestController
@RequiredArgsConstructor
public class BorrowRecordController {
    private final BorrowRecordService service;
    private final PermissionAccess permissionAccess;

    /** 聚合查询当前读者自己的借阅记录。 */
    @GetMapping("/borrow-records")
    public Result mine(@ModelAttribute BorrowRecordQueryRequest q) {
        permissionAccess.require(PermissionCodes.BORROW_RECORD_QUERY, DataScope.SELF);
        return Result.success(service.queryMine(q));
    }

    /** 馆员按组合条件查询借阅记录。 */
    @GetMapping("/admin/borrow-records")
    public Result admin(@ModelAttribute BorrowRecordQueryRequest q) {
        permissionAccess.require(PermissionCodes.BORROW_RECORD_QUERY, DataScope.ALL);
        return Result.success(service.queryAdmin(q));
    }

    /** 根据借阅记录ID办理归还。 */
    @PostMapping("/admin/borrow-records/{id}/return")
    public Result returnRecord(@PathVariable Long id, @RequestBody BorrowReturnRequest r) {
        permissionAccess.require(PermissionCodes.BORROW_RECORD_RETURN, DataScope.ALL);
        return Result.success(service.returnByRecord(id, r));
    }

    /** 根据馆藏条码定位未归还记录并办理归还。 */
    @PostMapping("/admin/book-copies/{barcode}/return")
    public Result returnBarcode(@PathVariable String barcode, @RequestBody(required = false) BorrowReturnRequest r) {
        permissionAccess.require(PermissionCodes.BORROW_RECORD_RETURN, DataScope.ALL);
        return Result.success(service.returnByBarcode(barcode, r));
    }
}
