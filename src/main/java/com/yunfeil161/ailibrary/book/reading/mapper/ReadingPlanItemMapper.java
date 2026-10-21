package com.yunfeil161.ailibrary.book.reading.mapper;

import com.yunfeil161.ailibrary.book.reading.dto.ReadingItemQueryRequest;
import com.yunfeil161.ailibrary.book.reading.entity.ReadingPlanItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingPlanItemMapper {
  /** 查询计划中与指定图书对应的唯一条目。 */
  ReadingPlanItem findOne(
    @Param("planId") Long planId,
    @Param("bookId") Long bookId
  );

  /** 根据聚合条件、计划范围和权限数据范围查询条目。 */
  List<ReadingPlanItem> find(
    @Param("q") ReadingItemQueryRequest q,
    @Param("planId") Long planId,
    @Param("scopeMemberId") Long scopeMemberId
  );

  /** 锁定计划的全部条目，确保重排顺序时不会出现重复序号。 */
  List<ReadingPlanItem> findByPlanForUpdate(@Param("planId") Long planId);

  /** 新增阅读条目。 */
  int insert(ReadingPlanItem i);

  /** 重排前把受影响条目临时移到序号范围之外，避免唯一键冲突。 */
  int moveToOffset(@Param("id") Long id, @Param("offset") int offset);

  /** 将条目写回最终连续序号。 */
  int writeSequence(@Param("id") Long id, @Param("sequenceNo") int sequenceNo);

  /** 更新条目的业务字段和乐观锁版本。 */
  int updateFields(ReadingPlanItem i);

  /** 按条目 ID 和版本删除单条记录。 */
  int deleteOne(@Param("id") Long id, @Param("version") Long version);

  /** 删除计划时清理其全部条目。 */
  int deleteByPlan(@Param("planId") Long planId);

  /** 统计某一状态的条目，用于校验计划状态转换。 */
  int countStatus(@Param("planId") Long planId, @Param("status") String status);
}
