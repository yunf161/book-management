package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.Reader;

import java.util.Optional;

/**
 * 读者仓储接口。
 */
public interface ReaderRepository {
    /** 保存读者。 */
    void save(Reader reader);

    /** 按读者编号查询。 */
    Optional<Reader> findById(String readerId);

    /** 按手机号查询。 */
    Optional<Reader> findByPhoneNumber(String phoneNumber);

    /** 按读者编号删除。 */
    void deleteById(String readerId);
}
