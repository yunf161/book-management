package com.yunfeil161.ailibrary.book.copy.controller;

import com.yunfeil161.ailibrary.book.copy.dto.BookCopyCreateRequest;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyUpdateRequest;
import com.yunfeil161.ailibrary.book.copy.service.BookCopyService;
import com.yunfeil161.ailibrary.common.api.Result;
import lombok.RequiredArgsConstructor;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookCopyController {
    private final BookCopyService service;
    private final PermissionAccess permissionAccess;

    @GetMapping("/admin/book-copies")
    public Result query(@ModelAttribute BookCopyQueryRequest query) {
        permissionAccess.require(PermissionCodes.BOOK_COPY_QUERY);
        return Result.success(service.getBookCopies(query));
    }

    @PostMapping("/admin/books/{bookId}/copies")
    public Result create(@PathVariable Long bookId, @RequestBody BookCopyCreateRequest request) {
        permissionAccess.require(PermissionCodes.BOOK_COPY_CREATE);
        return Result.success(service.createBookCopy(bookId, request));
    }

    @PutMapping("/admin/book-copies/{id}")
    public Result update(@PathVariable Long id, @RequestBody BookCopyUpdateRequest request) {
        permissionAccess.require(PermissionCodes.BOOK_COPY_UPDATE);
        return Result.success(service.updateBookCopy(id, request));
    }

    @DeleteMapping("/admin/book-copies/{id}")
    public Result delete(@PathVariable Long id) {
        permissionAccess.require(PermissionCodes.BOOK_COPY_DELETE);
        return Result.success("成功下架图书副本," + service.deleteBookCopy(id));
    }
}
