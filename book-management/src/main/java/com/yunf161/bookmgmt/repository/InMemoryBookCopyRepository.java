package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.BookCopy;
import com.yunf161.bookmgmt.domain.BookCopyStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 图书副本仓储的内存实现。
 */
public class InMemoryBookCopyRepository implements BookCopyRepository {
    /** 复用通用内存仓储，主键为 copyId。 */
    private final MainMemoryTemp<BookCopy> baseRepository;

    /** 初始化副本内存仓储。 */
    public InMemoryBookCopyRepository() {
        this.baseRepository = new MainMemoryTemp<>(BookCopy::getCopyId);
    }

    /** 保存副本。 */
    @Override
    public void save(BookCopy bookCopy) {
        baseRepository.save(bookCopy);
    }

    /** 按副本编号查询。 */
    @Override
    public Optional<BookCopy> findById(String copyId) {
        return baseRepository.findById(copyId);
    }

    /** 查询全部副本。 */
    @Override
    public List<BookCopy> findAll() {
        return baseRepository.findAll();
    }

    /** 按 ISBN 过滤副本。 */
    @Override
    public List<BookCopy> findByIsbn(String isbn) {
        return baseRepository.findAll().stream()
                .filter(copy -> copy.getIsbn().equals(isbn))
                .collect(Collectors.toList());
    }

    /** 按 ISBN 与状态过滤副本。 */
    @Override
    public List<BookCopy> findByIsbnAndStatus(String isbn, BookCopyStatus status) {
        return baseRepository.findAll().stream()
                .filter(copy -> copy.getIsbn().equals(isbn) && copy.getStatus() == status)
                .collect(Collectors.toList());
    }

    /** 按副本编号删除。 */
    @Override
    public void deleteById(String copyId) {
        baseRepository.deleteById(copyId);
    }
}
