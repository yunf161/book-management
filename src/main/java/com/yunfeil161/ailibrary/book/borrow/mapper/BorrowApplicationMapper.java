package com.yunfeil161.ailibrary.book.borrow.mapper;

import com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationQueryRequest;
import com.yunfeil161.ailibrary.book.borrow.entity.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 借阅申请数据访问接口。状态变更 SQL 同时校验状态和版本。
 */
@Mapper
public interface BorrowApplicationMapper {
    /** 按组合条件查询。 */
    List<BorrowApplicationDetailVO> find(@Param("q") BorrowApplicationQueryRequest q);

    /** 按主键查询。 */
    BorrowApplication findById(@Param("id") Long id);

    /** 插入新记录。 */
    int insert(BorrowApplication a);

    /** 查询仍在有效期内的待审批申请，用于应用启动时恢复Redis索引。 */
    List<BorrowApplication> findPendingActive(@Param("now") LocalDateTime now);

    /** 按版本将单条待审批申请置为过期。 */
    int expire(@Param("id") Long id, @Param("version") Long version, @Param("now") LocalDateTime now);

    /** 定时任务分批取得需要过期的申请，随后按主键逐条更新和清缓存。 */
    List<BorrowApplication> findPendingExpired(
      @Param("now") LocalDateTime now,
      @Param("limit") int limit
    );

    /** 按成员、状态和版本取消申请。 */
    int cancel(@Param("id") Long id, @Param("memberId") Long memberId, @Param("version") Long version,
               @Param("now") LocalDateTime now);

    /** 按状态和版本批准申请。 */
    int approve(@Param("id") Long id, @Param("version") Long version, @Param("processor") Long processor,
                @Param("copyId") Long copyId, @Param("days") Integer days, @Param("remark") String remark,
                @Param("now") LocalDateTime now);

    /** 按状态和版本拒绝申请。 */
    int reject(@Param("id") Long id, @Param("version") Long version, @Param("processor") Long processor,
               @Param("remark") String remark, @Param("now") LocalDateTime now);
}
