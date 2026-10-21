package com.yunfeil161.ailibrary.book.reading.mapper;

import com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanQueryRequest;
import com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanUpdateRequest;
import com.yunfeil161.ailibrary.book.reading.entity.ReadingPlan;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingPlanMapper {
  /** 查询成员的唯一阅读计划。 */
  ReadingPlan findByMemberId(@Param("memberId") Long memberId);

  /** 使用行锁读取计划，供需要同时调整多个条目的事务使用。 */
  ReadingPlan findByMemberIdForUpdate(@Param("memberId") Long memberId);

  /** 根据聚合条件及权限数据范围查询计划。 */
  List<ReadingPlan> find(
    @Param("q") ReadingPlanQueryRequest q,
    @Param("scopeMemberId") Long scopeMemberId
  );

  /** 新增阅读计划。 */
  int insert(ReadingPlan p);

  /** 按成员和版本选择性更新计划。 */
  int update(
    @Param("memberId") Long memberId,
    @Param("r") ReadingPlanUpdateRequest r
  );

  /** 条目变化后递增主计划版本，使两级乐观锁保持一致。 */
  int touch(@Param("id") Long id, @Param("version") Long version);

  /** 按成员和版本删除计划。 */
  int delete(@Param("memberId") Long memberId, @Param("version") Long version);
}
