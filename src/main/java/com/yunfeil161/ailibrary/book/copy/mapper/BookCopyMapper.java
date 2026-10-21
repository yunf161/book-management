package com.yunfeil161.ailibrary.book.copy.mapper;

import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyUpdateRequest;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookCopyMapper {
  List<BookCopy> find(@Param("query") BookCopyQueryRequest query);
  BookCopy findById(@Param("id") Long id);
  int countByBarcode(
    @Param("barcode") String barcode,
    @Param("excludeId") Long excludeId
  );
  int insert(BookCopy copy);
  int update(
    @Param("id") Long id,
    @Param("request") BookCopyUpdateRequest request
  );
  int discard(
    @Param("id") Long id,
    @Param("expectedVersion") Long expectedVersion
  );
  BookCopy findAvailableByBookId(@Param("bookId") Long bookId);
  BookCopy findByBarcode(@Param("barcode") String barcode);
  int borrow(
    @Param("id") Long id,
    @Param("bookId") Long bookId,
    @Param("version") Long version
  );
  /** 从BORROWED结案，目标状态只由归还Service根据结果确定。 */
  int returnCopy(
    @Param("id") Long id,
    @Param("version") Long version,
    @Param("conditionNote") String conditionNote,
    @Param("status") BookCopyStatus status
  );
}
