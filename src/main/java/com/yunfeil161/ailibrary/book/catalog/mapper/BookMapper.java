package com.yunfeil161.ailibrary.book.catalog.mapper;

import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.book.catalog.dto.BookUpdateRequest;
import com.yunfeil161.ailibrary.book.catalog.entity.Book;
import com.yunfeil161.ailibrary.book.catalog.entity.BookDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookMapper {
    List<BookDetailVO> find(@Param("query") BookQueryRequest query);
    Book findById(@Param("id") Long id);
    BookDetailVO findDetailById(@Param("id") Long id);
    int countByIsbn(@Param("isbn") String isbn, @Param("excludeId") Long excludeId);
    int insert(Book book);
    int update(@Param("id") Long id, @Param("request") BookUpdateRequest request);
    int countCopies(@Param("id") Long id);
    int disable(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);
}
