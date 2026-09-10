package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.LoanRecord;
import com.yunf161.bookmgmt.domain.LoanStatus;

import java.util.List;
import java.util.Optional;

/**
 * 借阅记录仓储接口。
 */
public interface LoanRecordRepository {
    /** 保存借阅记录。 */
    void save(LoanRecord loanRecord);

    /** 按记录编号查询。 */
    Optional<LoanRecord> findById(String recordId);

    /** 查询全部借阅记录。 */
    List<LoanRecord> findAll();

    /** 按读者查询所有借阅记录。 */
    List<LoanRecord> findByReaderId(String readerId);

    /** 按读者和状态查询借阅记录。 */
    List<LoanRecord> findByReaderIdAndStatus(String readerId, LoanStatus status);

    /** 按状态查询借阅记录。 */
    List<LoanRecord> findByStatus(LoanStatus status);

    /** 查询某副本当前是否存在借阅中的记录。 */
    Optional<LoanRecord> findBorrowingByCopyId(String copyId);

    /** 按记录编号删除。 */
    void deleteById(String recordId);
}
