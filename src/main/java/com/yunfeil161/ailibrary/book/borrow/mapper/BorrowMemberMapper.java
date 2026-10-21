package com.yunfeil161.ailibrary.book.borrow.mapper;

import com.yunfeil161.ailibrary.identity.entity.Member;
import org.apache.ibatis.annotations.*;

/**
 * 借阅流程使用的成员数据访问接口，只维护借阅所需字段。
 */
@Mapper
public interface BorrowMemberMapper {
    /** 按主键查询。 */
    Member findById(@Param("id") Long id);

    /** 使用成员版本号增加已借数量。 */
    int incrementBorrowed(@Param("id") Long id, @Param("version") Long version,
                          @Param("now") java.time.LocalDateTime now);

    /** 使用成员版本号减少已借数量。 */
    int decrementBorrowed(@Param("id") Long id, @Param("version") Long version);
}
