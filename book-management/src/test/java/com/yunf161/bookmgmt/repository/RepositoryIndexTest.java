package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.Librarian;
import com.yunf161.bookmgmt.domain.LibrarianStatus;
import com.yunf161.bookmgmt.domain.Reader;
import com.yunf161.bookmgmt.domain.ReaderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仓储索引一致性测试：验证手机号索引在更新和删除后保持正确。
 */
class RepositoryIndexTest {

    @Test
    void readerPhoneIndexShouldBeUpdatedAfterPhoneChange() {
        InMemoryReaderRepository repository = new InMemoryReaderRepository();
        Reader reader = new Reader("reader-1", ReaderStatus.ACTIVE, 3);
        reader.setName("A");
        reader.setPhone_number("13800000100");
        repository.save(reader);

        reader.setPhone_number("13800000101");
        repository.save(reader);

        assertFalse(repository.findByPhoneNumber("13800000100").isPresent());
        assertTrue(repository.findByPhoneNumber("13800000101").isPresent());
    }

    @Test
    void readerPhoneIndexShouldBeRemovedAfterDelete() {
        InMemoryReaderRepository repository = new InMemoryReaderRepository();
        Reader reader = new Reader("reader-2", ReaderStatus.ACTIVE, 3);
        reader.setName("B");
        reader.setPhone_number("13800000102");
        repository.save(reader);

        repository.deleteById("reader-2");

        assertFalse(repository.findByPhoneNumber("13800000102").isPresent());
    }

    @Test
    void employeePhoneIndexShouldBeUpdatedAfterPhoneChange() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Librarian employee = new Librarian("emp-1", LibrarianStatus.ACTIVE, "ADMIN");
        employee.setName("C");
        employee.setPhone_number("13900000100");
        repository.save(employee);

        employee.setPhone_number("13900000101");
        repository.save(employee);

        assertFalse(repository.findByPhoneNumber("13900000100").isPresent());
        assertTrue(repository.findByPhoneNumber("13900000101").isPresent());
    }

    @Test
    void employeePhoneIndexShouldBeRemovedAfterDelete() {
        InMemoryEmployeeRepository repository = new InMemoryEmployeeRepository();
        Librarian employee = new Librarian("emp-2", LibrarianStatus.ACTIVE, "OPERATOR");
        employee.setName("D");
        employee.setPhone_number("13900000102");
        repository.save(employee);

        repository.deleteById("emp-2");

        assertFalse(repository.findByPhoneNumber("13900000102").isPresent());
    }
}
