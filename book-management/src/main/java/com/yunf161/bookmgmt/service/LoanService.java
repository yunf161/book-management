package com.yunf161.bookmgmt.service;

import com.yunf161.bookmgmt.domain.BookCopy;
import com.yunf161.bookmgmt.domain.BookCopyStatus;
import com.yunf161.bookmgmt.domain.LoanRecord;
import com.yunf161.bookmgmt.domain.LoanStatus;
import com.yunf161.bookmgmt.domain.Reader;
import com.yunf161.bookmgmt.domain.ReaderStatus;
import com.yunf161.bookmgmt.exception.BusinessRuleException;
import com.yunf161.bookmgmt.exception.NotFoundException;
import com.yunf161.bookmgmt.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 借阅服务：负责借书、还书、查询与逾期处理。
 */
public class LoanService {
    /** 借阅记录仓储。 */
    private final LoanRecordRepository loanRepository;
    /** 书目仓储。 */
    private final BookRepository bookRepository;
    /** 读者仓储。 */
    private final ReaderRepository readerRepository;
    /** 副本仓储。 */
    private final BookCopyRepository bookCopyRepository;
    /** 读者服务，用于逾期计数与冻结规则。 */
    private final ReaderService readerService;

    /** 构造借阅服务。 */
    public LoanService(
            LoanRecordRepository loanRepository,
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            ReaderRepository readerRepository,
            ReaderService readerService
    ) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.readerRepository = readerRepository;
        this.readerService = readerService;
    }

    /**
     * 借书流程：
     * 1. 校验读者与书目；
     * 2. 校验读者状态与借阅上限；
     * 3. 选择可用副本并置为已借出；
     * 4. 写入借阅记录。
     */
    public void borrowBook(String readerId, String isbn) {
        Reader reader = readerRepository.findById(readerId)
                .orElseThrow(() -> new NotFoundException("[Reader][id=" + readerId + "] not found"));
        if (reader.getStatus() != ReaderStatus.ACTIVE) {
            throw new BusinessRuleException("[Loan][readerId=" + readerId + "] reader is not active");
        }
        if (bookRepository.findById(isbn).isEmpty()) {
            throw new NotFoundException("[Book][isbn=" + isbn + "] not found");
        }

        int borrowingCount = loanRepository.findByReaderIdAndStatus(readerId, LoanStatus.BORROWING).size();
        if (borrowingCount >= reader.getMaxBorrowedBooks()) {
            throw new BusinessRuleException("[Loan][readerId=" + readerId + "] reached max borrowed books");
        }

        List<BookCopy> availableCopies = bookCopyRepository.findByIsbnAndStatus(isbn, BookCopyStatus.AVAILABLE);
        if (availableCopies.isEmpty()) {
            throw new BusinessRuleException("[Loan][isbn=" + isbn + "] no available copy");
        }

        BookCopy selectedCopy = availableCopies.get(0);
        selectedCopy.setStatus(BookCopyStatus.BORROWED);
        bookCopyRepository.save(selectedCopy);

        LocalDateTime borrowTime = LocalDateTime.now();
        LocalDateTime dueTime = borrowTime.plusDays(14);
        LoanRecord loanRecord = new LoanRecord(
                UUID.randomUUID().toString(),
                readerId,
                selectedCopy.getCopyId(),
                borrowTime,
                dueTime
        );
        loanRepository.save(loanRecord);
    }

    /**
     * 还书流程：
     * 允许 BORROWING 或 OVERDUE 状态归还，
     * 并同步恢复副本状态与更新借阅记录。
     */
    public void returnBook(String recordId) {
        LoanRecord loanRecord = loanRepository.findById(recordId)
                .orElseThrow(() -> new NotFoundException("[Loan][recordId=" + recordId + "] not found"));

        if (loanRecord.getStatus() != LoanStatus.BORROWING && loanRecord.getStatus() != LoanStatus.OVERDUE) {
            throw new BusinessRuleException("[Loan][recordId=" + recordId + "] cannot return in status " + loanRecord.getStatus());
        }

        BookCopy bookCopy = bookCopyRepository.findById(loanRecord.getCopyId())
                .orElseThrow(() -> new NotFoundException("[BookCopy][copyId=" + loanRecord.getCopyId() + "] not found"));

        bookCopy.setStatus(BookCopyStatus.AVAILABLE);
        bookCopyRepository.save(bookCopy);

        loanRecord.setStatus(LoanStatus.RETURNED);
        loanRecord.setReturnTime(LocalDateTime.now());
        loanRepository.save(loanRecord);
    }

    /** 查询读者当前借阅中记录。 */
    public List<LoanRecord> listBorrowing(String readerId) {
        return loanRepository.findByReaderIdAndStatus(readerId, LoanStatus.BORROWING);
    }

    /** 查询读者已归还记录。 */
    public List<LoanRecord> listReturned(String readerId) {
        return loanRepository.findByReaderIdAndStatus(readerId, LoanStatus.RETURNED);
    }

    /** 查询读者逾期记录。 */
    public List<LoanRecord> listOverdue(String readerId) {
        return loanRepository.findByReaderIdAndStatus(readerId, LoanStatus.OVERDUE);
    }

    /** 扫描借阅中记录并处理逾期，联动读者逾期计数。 */
    public void processOverdue(LocalDateTime now) {
        List<LoanRecord> borrowingRecords = loanRepository.findByStatus(LoanStatus.BORROWING);
        for (LoanRecord record : borrowingRecords) {
            if (record.isOverdue(now)) {
                record.setStatus(LoanStatus.OVERDUE);
                loanRepository.save(record);
                readerService.recordOverdue(record.getReaderId());
            }
        }
    }
}
