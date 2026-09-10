package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.BookCopy;
import com.yunf161.bookmgmt.domain.BookCopyStatus;

import java.util.List;
import java.util.Optional;

/**
 * 图书副本仓储接口。
 */
public interface BookCopyRepository {
    /** 保存副本。 */
    void save(BookCopy bookCopy);

    /** 按副本编号查询。 */
    Optional<BookCopy> findById(String copyId);

    /** 查询全部副本。 */
    List<BookCopy> findAll();

    /** 按 ISBN 查询副本集合。 */
    List<BookCopy> findByIsbn(String isbn);

    /** 按 ISBN + 状态查询副本集合。 */
    List<BookCopy> findByIsbnAndStatus(String isbn, BookCopyStatus status);

    /** 按副本编号删除。 */
    void deleteById(String copyId);
}
