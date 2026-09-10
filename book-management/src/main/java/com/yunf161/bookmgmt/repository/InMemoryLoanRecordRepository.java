package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.LoanRecord;
import com.yunf161.bookmgmt.domain.LoanStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 借阅记录仓储的内存实现。
 */
public class InMemoryLoanRecordRepository implements LoanRecordRepository {
    /** 复用通用内存仓储，主键为 recordId。 */
    private final MainMemoryTemp<LoanRecord> baseRepository;

    /** 初始化借阅记录内存仓储。 */
    public InMemoryLoanRecordRepository() {
        this.baseRepository = new MainMemoryTemp<>(LoanRecord::getRecordId);
    }

    /** 保存借阅记录。 */
    @Override
    public void save(LoanRecord loanRecord) {
        baseRepository.save(loanRecord);
    }

    /** 按记录编号查询。 */
    @Override
    public Optional<LoanRecord> findById(String recordId) {
        return baseRepository.findById(recordId);
    }

    /** 查询全部借阅记录。 */
    @Override
    public List<LoanRecord> findAll() {
        return baseRepository.findAll();
    }

    /** 按读者查询借阅记录。 */
    @Override
    public List<LoanRecord> findByReaderId(String readerId) {
        return baseRepository.findAll().stream()
                .filter(record -> record.getReaderId().equals(readerId))
                .collect(Collectors.toList());
    }

    /** 按读者与状态查询借阅记录。 */
    @Override
    public List<LoanRecord> findByReaderIdAndStatus(String readerId, LoanStatus status) {
        return baseRepository.findAll().stream()
                .filter(record -> record.getReaderId().equals(readerId) && record.getStatus() == status)
                .collect(Collectors.toList());
    }

    /** 按状态查询借阅记录。 */
    @Override
    public List<LoanRecord> findByStatus(LoanStatus status) {
        return baseRepository.findAll().stream()
                .filter(record -> record.getStatus() == status)
                .collect(Collectors.toList());
    }

    /** 查询指定副本借阅中的记录。 */
    @Override
    public Optional<LoanRecord> findBorrowingByCopyId(String copyId) {
        return baseRepository.findAll().stream()
                .filter(record -> record.getCopyId().equals(copyId) && record.getStatus() == LoanStatus.BORROWING)
                .findFirst();
    }

    /** 按记录编号删除。 */
    @Override
    public void deleteById(String recordId) {
        baseRepository.deleteById(recordId);
    }
}
