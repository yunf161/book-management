package com.yunfeil161.ailibrary.book.catalog.service;

import com.yunfeil161.ailibrary.book.catalog.dto.BookCreateRequest;
import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.book.catalog.dto.BookUpdateRequest;
import com.yunfeil161.ailibrary.book.catalog.entity.BookDetailVO;
import com.yunfeil161.ailibrary.common.api.PageResult;

public interface BookService {
    PageResult<BookDetailVO> getBooks(BookQueryRequest query);
    BookDetailVO createBook(BookCreateRequest request);
    BookDetailVO updateBook(Long id, BookUpdateRequest request);
    String deleteBook(Long id);
}
