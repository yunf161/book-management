package com.yunf161.bookmgmt.service;

import com.yunf161.bookmgmt.domain.Reader;
import com.yunf161.bookmgmt.domain.ReaderStatus;
import com.yunf161.bookmgmt.exception.BusinessRuleException;
import com.yunf161.bookmgmt.repository.InMemoryReaderRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 读者服务测试：覆盖参数校验与状态流转约束。
 */
class ReaderServiceTest {

    @Test
    void createReaderFailsWhenNameBlank() {
        ReaderService readerService = new ReaderService(new InMemoryReaderRepository());
        assertThrows(BusinessRuleException.class, () -> readerService.createReader(" ", "13800000010", 3));
    }

    @Test
    void createReaderFailsWhenMaxBorrowedBooksInvalid() {
        ReaderService readerService = new ReaderService(new InMemoryReaderRepository());
        assertThrows(BusinessRuleException.class, () -> readerService.createReader("Tom", "13800000011", 0));
    }

    @Test
    void deletedReaderCannotBeUpdated() {
        ReaderService readerService = new ReaderService(new InMemoryReaderRepository());
        Reader reader = readerService.createReader("Tom", "13800000012", 3);
        readerService.deleteReader(reader.getReaderId());
        assertThrows(BusinessRuleException.class, () -> readerService.updateReaderName(reader.getReaderId(), "Jerry"));
    }

    @Test
    void updatePhoneNumberAllowsSamePhoneForSameReader() {
        ReaderService readerService = new ReaderService(new InMemoryReaderRepository());
        Reader reader = readerService.createReader("Amy", "13800000013", 3);
        readerService.updatePhoneNumber(reader.getReaderId(), "13800000013");
        Reader updated = readerService.getReaderById(reader.getReaderId());
        assertEquals("13800000013", updated.getPhone_number());
    }

    @Test
    void recordOverdueFreezesReaderAtFiveTimes() {
        ReaderService readerService = new ReaderService(new InMemoryReaderRepository());
        Reader reader = readerService.createReader("Lucy", "13800000014", 3);
        for (int i = 0; i < 5; i++) {
            readerService.recordOverdue(reader.getReaderId());
        }
        Reader updated = readerService.getReaderById(reader.getReaderId());
        assertEquals(5, updated.getOverdueCount());
        assertEquals(ReaderStatus.FROZEN, updated.getStatus());
    }
}
