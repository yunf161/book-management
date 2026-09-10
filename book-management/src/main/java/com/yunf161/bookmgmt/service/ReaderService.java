package com.yunf161.bookmgmt.service;


import com.yunf161.bookmgmt.domain.Reader;
import com.yunf161.bookmgmt.domain.ReaderStatus;
import com.yunf161.bookmgmt.exception.BusinessRuleException;
import com.yunf161.bookmgmt.exception.NotFoundException;
import com.yunf161.bookmgmt.repository.ReaderRepository;

import java.util.UUID;

/**
 * 读者服务：负责读者注册、状态变更、基础资料维护与逾期累计规则。
 */
public class ReaderService  {
    /** 达到该逾期次数后自动冻结。 */
    private static final int OVERDUE_FREEZE_THRESHOLD = 5;
    /** 读者仓储依赖。 */
    private final ReaderRepository readerRepository;

    /** 构造读者服务。 */
    public ReaderService(ReaderRepository readerRepository) {
        this.readerRepository = readerRepository;
    }

    /** 创建读者（默认最大借阅数 5）。 */
    public Reader createReader(String name, String phoneNumber) {
        return createReader(name, phoneNumber, 5);
    }

    /** 创建读者并指定借阅上限。 */
    public Reader createReader(String name, String phoneNumber, int maxBorrowedBooks) {
        validateName(name);
        validatePhoneNumber(phoneNumber);
        validateMaxBorrowedBooks(maxBorrowedBooks);
        if (readerRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new BusinessRuleException("[Reader][phone=" + phoneNumber + "] already registered");
        }
        Reader reader = new Reader(UUID.randomUUID().toString(), ReaderStatus.ACTIVE, maxBorrowedBooks);
        reader.setName(name);
        reader.setPhone_number(phoneNumber);
        readerRepository.save(reader);
        return reader;
    }

    /** 按手机号查询读者。 */
    public Reader getReaderByPhoneNumber(String phoneNumber) {
        return readerRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new NotFoundException("[Reader][phone=" + phoneNumber + "] not found"));
    }

    /** 按读者编号查询读者。 */
    public Reader getReaderById(String readerId) {
        return readerRepository.findById(readerId)
                .orElseThrow(() -> new NotFoundException("[Reader][id=" + readerId + "] not found"));
    }

    /** 冻结读者。 */
    public void freezeReader(String readerId) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] deleted reader cannot be frozen");
        }
        reader.setStatus(ReaderStatus.FROZEN);
        readerRepository.save(reader);
    }

    /** 激活读者。 */
    public void activateReader(String readerId) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] deleted reader cannot be activated");
        }
        reader.setStatus(ReaderStatus.ACTIVE);
        readerRepository.save(reader);
    }

    /** 注销读者。 */
    public void deleteReader(String readerId) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] already deleted");
        }
        reader.setStatus(ReaderStatus.DELETED);
        readerRepository.save(reader);
    }

    /** 更新读者最大借阅上限。 */
    public void updateMaxBorrowedBooks(String readerId, int maxBorrowedBooks) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] deleted reader cannot update maxBorrowedBooks");
        }
        validateMaxBorrowedBooks(maxBorrowedBooks);
        reader.setMaxBorrowedBooks(maxBorrowedBooks);
        readerRepository.save(reader);
    }

    /** 更新读者手机号。 */
    public void updatePhoneNumber(String readerId, String phoneNumber) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] deleted reader cannot update phone");
        }
        validatePhoneNumber(phoneNumber);
        Reader occupiedReader = readerRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (occupiedReader != null && !occupiedReader.getReaderId().equals(readerId)) {
            throw new BusinessRuleException("[Reader][phone=" + phoneNumber + "] already registered");
        }
        reader.setPhone_number(phoneNumber);
        readerRepository.save(reader);
    }

    /** 更新读者姓名。 */
    public void updateReaderName(String readerId, String name) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] deleted reader cannot update name");
        }
        validateName(name);
        reader.setName(name);
        readerRepository.save(reader);
    }

    /** 更新读者年龄。 */
    public void updateReaderAge(String readerId, int age) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] deleted reader cannot update age");
        }
        validateAge(age);
        reader.setAge(age);
        readerRepository.save(reader);
    }

    /** 记录一次逾期并按阈值自动冻结。 */
    public void recordOverdue(String readerId) {
        Reader reader = getReaderById(readerId);
        if (reader.getStatus() == ReaderStatus.DELETED) {
            throw new BusinessRuleException("[Reader][id=" + readerId + "] deleted reader cannot record overdue");
        }
        reader.setOverdueCount(reader.getOverdueCount() + 1);
        if (reader.getOverdueCount() >= OVERDUE_FREEZE_THRESHOLD) {
            reader.setStatus(ReaderStatus.FROZEN);
        }
        readerRepository.save(reader);
    }

    /** 校验姓名。 */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("[Reader] name cannot be null or blank");
        }
    }

    /** 校验手机号。 */
    private void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessRuleException("[Reader] phone cannot be null or blank");
        }
    }

    /** 校验借阅上限。 */
    private void validateMaxBorrowedBooks(int maxBorrowedBooks) {
        if (maxBorrowedBooks <= 0) {
            throw new BusinessRuleException("[Reader] maxBorrowedBooks must be greater than 0");
        }
    }

    /** 校验年龄。 */
    private void validateAge(int age) {
        if (age < 0) {
            throw new BusinessRuleException("[Reader][age=" + age + "] cannot be negative");
        }
    }
}
