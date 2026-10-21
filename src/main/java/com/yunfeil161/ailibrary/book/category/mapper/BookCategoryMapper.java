package com.yunfeil161.ailibrary.book.category.mapper;

import com.yunfeil161.ailibrary.book.category.dto.BookCategoryQueryRequest;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryUpdateRequest;
import com.yunfeil161.ailibrary.book.category.entity.BookCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookCategoryMapper {
    List<BookCategory> find(@Param("query") BookCategoryQueryRequest query);
    BookCategory findById(@Param("id") Long id);
    int countByCode(@Param("code") String code);
    int countSiblingName(@Param("parentId") Long parentId, @Param("name") String name,
                         @Param("excludeId") Long excludeId);
    int insert(BookCategory category);
    int update(@Param("id") Long id, @Param("request") BookCategoryUpdateRequest request);
    int countChildren(@Param("id") Long id);
    int countBooks(@Param("id") Long id);
    int disable(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);
}
