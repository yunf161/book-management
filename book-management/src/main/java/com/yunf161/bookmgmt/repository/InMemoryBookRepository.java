package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.Book;

import java.util.List;
import java.util.Optional;

/**
 * 书目仓储的内存实现。
 */
public class InMemoryBookRepository implements BookRepository {
    /** 复用通用内存仓储，主键为 ISBN。 */
    private final MainMemoryTemp<Book> baseRepository;

    /** 初始化书目内存仓储。 */
    public InMemoryBookRepository() {
        this.baseRepository = new MainMemoryTemp<>(Book::getIsbn);
    }

    /** 保存书目。 */
    @Override
    public void save(Book book) {
        baseRepository.save(book);
    }

    /** 按 ISBN 查询书目。 */
    @Override
    public Optional<Book> findById(String isbn) {
        return baseRepository.findById(isbn);
    }

    /** 判断 ISBN 是否存在。 */
    @Override
    public boolean existsById(String isbn) {
        return baseRepository.existsById(isbn);
    }

    /** 查询全部书目。 */
    @Override
    public List<Book> findAll() {
        return baseRepository.findAll();
    }

    /** 按 ISBN 删除书目。 */
    @Override
    public void deleteById(String isbn) {
        baseRepository.deleteById(isbn);
    }
}
