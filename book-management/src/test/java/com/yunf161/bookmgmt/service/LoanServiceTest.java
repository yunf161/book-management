package com.yunf161.bookmgmt.service;

import com.yunf161.bookmgmt.domain.Book;
import com.yunf161.bookmgmt.domain.BookCopy;
import com.yunf161.bookmgmt.domain.BookCopyStatus;
import com.yunf161.bookmgmt.domain.LoanRecord;
import com.yunf161.bookmgmt.domain.LoanStatus;
import com.yunf161.bookmgmt.domain.Reader;
import com.yunf161.bookmgmt.domain.ReaderStatus;
import com.yunf161.bookmgmt.exception.BusinessRuleException;
import com.yunf161.bookmgmt.exception.NotFoundException;
import com.yunf161.bookmgmt.repository.InMemoryBookCopyRepository;
import com.yunf161.bookmgmt.repository.InMemoryBookRepository;
import com.yunf161.bookmgmt.repository.InMemoryLoanRecordRepository;
import com.yunf161.bookmgmt.repository.InMemoryReaderRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 借阅服务测试：覆盖借还流程与借阅合法性规则。
 */
class LoanServiceTest {

    /** 借书成功后应生成借阅记录并更新副本状态。 */
    @Test
    void borrowBookCreatesLoanAndUpdatesCopyStatus() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Alice", "13800000001", 3);
        ctx.bookRepository.save(new Book("isbn-1", "Java", "Tom", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-1", "isbn-1", "A-01"));

        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-1");

        LoanRecord loan = ctx.loanRepository.findAll().get(0);
        assertEquals(reader.getReaderId(), loan.getReaderId());
        assertEquals(LoanStatus.BORROWING, loan.getStatus());
        assertEquals(BookCopyStatus.BORROWED, ctx.bookCopyRepository.findById("copy-1").orElseThrow().getStatus());
    }

    /** 冻结读者借书应失败。 */
    @Test
    void borrowBookFailsWhenReaderIsFrozen() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Bob", "13800000002", 3);
        ctx.readerService.freezeReader(reader.getReaderId());
        ctx.bookRepository.save(new Book("isbn-2", "Spring", "Jerry", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-2", "isbn-2", "A-02"));

        assertThrows(BusinessRuleException.class, () -> ctx.loanService.borrowBook(reader.getReaderId(), "isbn-2"));
    }

    /** 书目不存在时借书应失败。 */
    @Test
    void borrowBookFailsWhenBookNotFound() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Carol", "13800000003", 3);

        assertThrows(NotFoundException.class, () -> ctx.loanService.borrowBook(reader.getReaderId(), "missing-isbn"));
    }

    /** 无可用副本时借书应失败。 */
    @Test
    void borrowBookFailsWhenNoAvailableCopy() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("David", "13800000004", 3);
        ctx.bookRepository.save(new Book("isbn-3", "SQL", "A", "Tech", 2024));

        assertThrows(BusinessRuleException.class, () -> ctx.loanService.borrowBook(reader.getReaderId(), "isbn-3"));
    }

    /** 达到借阅上限后继续借书应失败。 */
    @Test
    void borrowBookFailsWhenReaderReachedLimit() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Eve", "13800000005", 1);
        ctx.bookRepository.save(new Book("isbn-4", "Java 1", "A", "Tech", 2024));
        ctx.bookRepository.save(new Book("isbn-5", "Java 2", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-4", "isbn-4", "A-04"));
        ctx.bookCopyRepository.save(new BookCopy("copy-5", "isbn-5", "A-05"));

        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-4");

        assertThrows(BusinessRuleException.class, () -> ctx.loanService.borrowBook(reader.getReaderId(), "isbn-5"));
    }

    /** 逾期记录应允许归还，并恢复副本状态。 */
    @Test
    void returnBookAllowsOverdueRecord() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Frank", "13800000006", 3);
        ctx.bookRepository.save(new Book("isbn-6", "Net", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-6", "isbn-6", "A-06"));
        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-6");

        LoanRecord loan = ctx.loanRepository.findAll().get(0);
        loan.setDueTime(LocalDateTime.now().minusDays(1));
        ctx.loanRepository.save(loan);
        ctx.loanService.processOverdue(LocalDateTime.now());

        ctx.loanService.returnBook(loan.getRecordId());

        LoanRecord returned = ctx.loanRepository.findById(loan.getRecordId()).orElseThrow();
        assertEquals(LoanStatus.RETURNED, returned.getStatus());
        assertEquals(BookCopyStatus.AVAILABLE, ctx.bookCopyRepository.findById("copy-6").orElseThrow().getStatus());
    }

    /** 读者累计 5 次逾期后应自动冻结。 */
    @Test
    void processOverdueFreezesReaderAfterFiveTimes() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Grace", "13800000007", 10);

        for (int i = 1; i <= 5; i++) {
            String isbn = "isbn-overdue-" + i;
            String copyId = "copy-overdue-" + i;
            ctx.bookRepository.save(new Book(isbn, "Book-" + i, "A", "Tech", 2024));
            ctx.bookCopyRepository.save(new BookCopy(copyId, isbn, "B-" + i));
            ctx.loanService.borrowBook(reader.getReaderId(), isbn);
        }

        List<LoanRecord> loans = ctx.loanRepository.findAll();
        for (LoanRecord loan : loans) {
            loan.setDueTime(LocalDateTime.now().minusDays(1));
            ctx.loanRepository.save(loan);
        }
        ctx.loanService.processOverdue(LocalDateTime.now());

        Reader updated = ctx.readerService.getReaderById(reader.getReaderId());
        assertEquals(5, updated.getOverdueCount());
        assertEquals(ReaderStatus.FROZEN, updated.getStatus());
        assertEquals(5, ctx.loanService.listOverdue(reader.getReaderId()).size());
    }

    /** dueTime 等于当前时间时不应被判定为逾期。 */
    @Test
    void processOverdueDoesNotMarkWhenDueTimeEqualsNow() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Helen", "13800000015", 3);
        ctx.bookRepository.save(new Book("isbn-7", "Algo", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-7", "isbn-7", "A-07"));
        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-7");

        LoanRecord loan = ctx.loanRepository.findAll().get(0);
        LocalDateTime now = LocalDateTime.now();
        loan.setDueTime(now);
        ctx.loanRepository.save(loan);

        ctx.loanService.processOverdue(now);

        LoanRecord updated = ctx.loanRepository.findById(loan.getRecordId()).orElseThrow();
        assertEquals(LoanStatus.BORROWING, updated.getStatus());
        assertEquals(0, ctx.readerService.getReaderById(reader.getReaderId()).getOverdueCount());
    }

    /** 同一借阅记录重复执行逾期处理不应重复累计。 */
    @Test
    void processOverdueDoesNotDoubleCountSameRecord() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Ivy", "13800000016", 3);
        ctx.bookRepository.save(new Book("isbn-8", "Design", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-8", "isbn-8", "A-08"));
        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-8");

        LoanRecord loan = ctx.loanRepository.findAll().get(0);
        loan.setDueTime(LocalDateTime.now().minusDays(1));
        ctx.loanRepository.save(loan);

        ctx.loanService.processOverdue(LocalDateTime.now());
        ctx.loanService.processOverdue(LocalDateTime.now());

        Reader updated = ctx.readerService.getReaderById(reader.getReaderId());
        assertEquals(1, updated.getOverdueCount());
        assertEquals(1, ctx.loanService.listOverdue(reader.getReaderId()).size());
    }

    /** 已归还记录不允许再次归还。 */
    @Test
    void returnBookFailsWhenAlreadyReturned() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Jack", "13800000017", 3);
        ctx.bookRepository.save(new Book("isbn-9", "DB", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-9", "isbn-9", "A-09"));
        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-9");

        LoanRecord loan = ctx.loanRepository.findAll().get(0);
        ctx.loanService.returnBook(loan.getRecordId());

        assertThrows(BusinessRuleException.class, () -> ctx.loanService.returnBook(loan.getRecordId()));
    }

    /** readerId 不存在时应抛出 NotFoundException，且消息包含定位信息。 */
    @Test
    void borrowBookFailsWhenReaderNotFoundWithExpectedMessage() {
        TestContext ctx = new TestContext();
        ctx.bookRepository.save(new Book("isbn-10", "OS", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-10", "isbn-10", "A-10"));

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> ctx.loanService.borrowBook("missing-reader", "isbn-10"));
        assertTrue(ex.getMessage().contains("[Reader][id=missing-reader] not found"));
    }

    /** 还书 recordId 不存在时应抛出 NotFoundException，且消息包含定位信息。 */
    @Test
    void returnBookFailsWhenRecordNotFoundWithExpectedMessage() {
        TestContext ctx = new TestContext();

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> ctx.loanService.returnBook("missing-record"));
        assertTrue(ex.getMessage().contains("[Loan][recordId=missing-record] not found"));
    }

    /** 副本状态为 LOST 或 OFF_SHELF 时，借书应失败。 */
    @Test
    void borrowBookFailsWhenOnlyLostOrOffShelfCopiesExist() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Kim", "13800000018", 3);
        ctx.bookRepository.save(new Book("isbn-11", "Sec", "A", "Tech", 2024));
        BookCopy lostCopy = new BookCopy("copy-11-1", "isbn-11", "A-11");
        lostCopy.setStatus(BookCopyStatus.LOST);
        BookCopy offShelfCopy = new BookCopy("copy-11-2", "isbn-11", "A-12");
        offShelfCopy.setStatus(BookCopyStatus.OFF_SHELF);
        ctx.bookCopyRepository.save(lostCopy);
        ctx.bookCopyRepository.save(offShelfCopy);

        assertThrows(BusinessRuleException.class, () -> ctx.loanService.borrowBook(reader.getReaderId(), "isbn-11"));
    }

    /** 借还后列表和时间字段应联动变化。 */
    @Test
    void returnBookShouldUpdateListsAndReturnTime() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Leo", "13800000019", 3);
        ctx.bookRepository.save(new Book("isbn-12", "Cloud", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-12", "isbn-12", "A-13"));

        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-12");
        LoanRecord loan = ctx.loanRepository.findAll().get(0);
        assertEquals(1, ctx.loanService.listBorrowing(reader.getReaderId()).size());
        assertEquals(0, ctx.loanService.listReturned(reader.getReaderId()).size());

        ctx.loanService.returnBook(loan.getRecordId());

        LoanRecord returned = ctx.loanRepository.findById(loan.getRecordId()).orElseThrow();
        assertNotNull(returned.getReturnTime());
        assertEquals(0, ctx.loanService.listBorrowing(reader.getReaderId()).size());
        assertEquals(1, ctx.loanService.listReturned(reader.getReaderId()).size());
    }

    /** 提高借阅上限后，应允许继续借书。 */
    @Test
    void increasingBorrowLimitShouldAllowAdditionalBorrow() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Mia", "13800000020", 1);
        ctx.bookRepository.save(new Book("isbn-13", "A", "A", "Tech", 2024));
        ctx.bookRepository.save(new Book("isbn-14", "B", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-13", "isbn-13", "A-14"));
        ctx.bookCopyRepository.save(new BookCopy("copy-14", "isbn-14", "A-15"));

        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-13");
        assertThrows(BusinessRuleException.class, () -> ctx.loanService.borrowBook(reader.getReaderId(), "isbn-14"));

        ctx.readerService.updateMaxBorrowedBooks(reader.getReaderId(), 2);
        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-14");

        assertEquals(2, ctx.loanService.listBorrowing(reader.getReaderId()).size());
    }

    /** 降低借阅上限后，不影响已借记录，但应限制后续新借。 */
    @Test
    void decreasingBorrowLimitShouldRestrictNewBorrowWithoutAffectingCurrentLoans() {
        TestContext ctx = new TestContext();
        Reader reader = ctx.readerService.createReader("Nina", "13800000021", 3);
        ctx.bookRepository.save(new Book("isbn-15", "C", "A", "Tech", 2024));
        ctx.bookRepository.save(new Book("isbn-16", "D", "A", "Tech", 2024));
        ctx.bookRepository.save(new Book("isbn-17", "E", "A", "Tech", 2024));
        ctx.bookCopyRepository.save(new BookCopy("copy-15", "isbn-15", "A-16"));
        ctx.bookCopyRepository.save(new BookCopy("copy-16", "isbn-16", "A-17"));
        ctx.bookCopyRepository.save(new BookCopy("copy-17", "isbn-17", "A-18"));

        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-15");
        ctx.loanService.borrowBook(reader.getReaderId(), "isbn-16");
        assertEquals(2, ctx.loanService.listBorrowing(reader.getReaderId()).size());

        ctx.readerService.updateMaxBorrowedBooks(reader.getReaderId(), 1);
        assertThrows(BusinessRuleException.class, () -> ctx.loanService.borrowBook(reader.getReaderId(), "isbn-17"));
        assertEquals(2, ctx.loanService.listBorrowing(reader.getReaderId()).size());
    }

    /** 测试上下文：组装内存仓储与服务依赖。 */
    private static class TestContext {
        private final InMemoryReaderRepository readerRepository = new InMemoryReaderRepository();
        private final InMemoryBookRepository bookRepository = new InMemoryBookRepository();
        private final InMemoryBookCopyRepository bookCopyRepository = new InMemoryBookCopyRepository();
        private final InMemoryLoanRecordRepository loanRepository = new InMemoryLoanRecordRepository();
        private final ReaderService readerService = new ReaderService(readerRepository);
        private final LoanService loanService = new LoanService(
                loanRepository,
                bookRepository,
                bookCopyRepository,
                readerRepository,
                readerService
        );
    }
}
