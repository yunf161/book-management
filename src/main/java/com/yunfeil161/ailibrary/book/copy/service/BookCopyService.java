package com.yunfeil161.ailibrary.book.copy.service;

import com.yunfeil161.ailibrary.book.copy.dto.BookCopyCreateRequest;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyUpdateRequest;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.common.api.PageResult;

public interface BookCopyService {
    PageResult<BookCopy> getBookCopies(BookCopyQueryRequest query);
    BookCopy createBookCopy(Long bookId, BookCopyCreateRequest request);
    BookCopy updateBookCopy(Long id, BookCopyUpdateRequest request);
    String deleteBookCopy(Long id);
}
