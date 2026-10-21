package com.yunfeil161.ailibrary.book.category.service;

import com.yunfeil161.ailibrary.book.category.dto.BookCategoryCreateRequest;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryQueryRequest;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryUpdateRequest;
import com.yunfeil161.ailibrary.book.category.entity.BookCategory;
import com.yunfeil161.ailibrary.common.api.PageResult;

public interface BookCategoryService {
    PageResult<BookCategory> getBookCategories(BookCategoryQueryRequest query);
    BookCategory createBookCategory(BookCategoryCreateRequest request);
    BookCategory updateBookCategory(Long id, BookCategoryUpdateRequest request);
    String deleteBookCategory(Long id);
}
