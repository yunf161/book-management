package com.yunfeil161.ailibrary.book.category.controller;

import com.yunfeil161.ailibrary.book.category.dto.BookCategoryCreateRequest;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryQueryRequest;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryUpdateRequest;
import com.yunfeil161.ailibrary.book.category.service.BookCategoryService;
import com.yunfeil161.ailibrary.common.api.Result;
import lombok.RequiredArgsConstructor;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookCategoryController {
    private final BookCategoryService service;
    private final PermissionAccess permissionAccess;

    @GetMapping("/book-categories")
    public Result query(@ModelAttribute BookCategoryQueryRequest query) {
        return Result.success(service.getBookCategories(query));
    }

    @PostMapping("/admin/book-categories")
    public Result create(@RequestBody BookCategoryCreateRequest request) {
        permissionAccess.require(PermissionCodes.BOOK_CATEGORY_CREATE);
        return Result.success(service.createBookCategory(request));
    }

    @PutMapping("/admin/book-categories/{id}")
    public Result update(@PathVariable Long id, @RequestBody BookCategoryUpdateRequest request) {
        permissionAccess.require(PermissionCodes.BOOK_CATEGORY_UPDATE);
        return Result.success(service.updateBookCategory(id, request));
    }

    @DeleteMapping("/admin/book-categories/{id}")
    public Result delete(@PathVariable Long id) {
        permissionAccess.require(PermissionCodes.BOOK_CATEGORY_DELETE);
        return Result.success("成功停用图书分类," + service.deleteBookCategory(id));
    }
}
