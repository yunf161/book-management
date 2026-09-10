package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.Book;

import java.util.List;
import java.util.Optional;

/**
 * 书目仓储接口。
 */
public interface BookRepository {
    /** 保存书目。 */
    void save(Book book);

    /** 按 ISBN 查询书目。 */
    Optional<Book> findById(String isbn);

    /** 判断 ISBN 是否存在。 */
    boolean existsById(String isbn);

    /** 查询全部书目。 */
    List<Book> findAll();

    /** 按 ISBN 删除书目。 */
    void deleteById(String isbn);
}
