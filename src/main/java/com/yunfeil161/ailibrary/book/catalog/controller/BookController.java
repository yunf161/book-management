package com.yunfeil161.ailibrary.book.catalog.controller;

import com.yunfeil161.ailibrary.book.catalog.dto.BookCreateRequest;
import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.book.catalog.dto.BookUpdateRequest;
import com.yunfeil161.ailibrary.book.catalog.service.BookService;
import com.yunfeil161.ailibrary.common.api.Result;
import lombok.RequiredArgsConstructor;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookController {
    private final BookService service;
    private final PermissionAccess permissionAccess;

    @GetMapping("/books")
    public Result query(@ModelAttribute BookQueryRequest query) {
        return Result.success(service.getBooks(query));
    }

    @PostMapping("/admin/books")
    public Result create(@RequestBody BookCreateRequest request) {
        permissionAccess.require(PermissionCodes.BOOK_CREATE);
        return Result.success(service.createBook(request));
    }

    @PutMapping("/admin/books/{id}")
    public Result update(@PathVariable Long id, @RequestBody BookUpdateRequest request) {
        permissionAccess.require(PermissionCodes.BOOK_UPDATE);
        return Result.success(service.updateBook(id, request));
    }

    @DeleteMapping("/admin/books/{id}")
    public Result delete(@PathVariable Long id) {
        permissionAccess.require(PermissionCodes.BOOK_DELETE);
        return Result.success("成功停用图书," + service.deleteBook(id));
    }
}
