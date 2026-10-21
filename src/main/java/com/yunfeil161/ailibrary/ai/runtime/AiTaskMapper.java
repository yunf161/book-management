package com.yunfeil161.ailibrary.ai.runtime;

import java.util.List;
import org.apache.ibatis.annotations.*;

/** 状态通过条件更新推进，取消和成功竞争时只能有一个终态。 */
@Mapper
public interface AiTaskMapper {
  @Insert(
    "insert into ai_task(task_id,member_id,type,question,attempt,parent_task_id) values(#{taskId},#{memberId},#{type},#{question},#{attempt},#{parentTaskId})"
  )
  int insert(AiTask task);

  @Select("select * from ai_task where task_id=#{id}")
  AiTask find(String id);

  @Select(
    "select * from ai_task where parent_task_id=#{id} order by id limit 1"
  )
  AiTask retryOf(String id);

  @Select(
    "<script>select * from ai_task where 1=1 <if test='owner != null'>and member_id=#{owner}</if><if test='q.taskId != null'>and task_id=#{q.taskId}</if><if test='q.memberId != null'>and member_id=#{q.memberId}</if><if test='q.state != null'>and state=#{q.state}</if><if test='q.type != null'>and type=#{q.type}</if> order by id desc</script>"
  )
  List<AiTask> query(@Param("owner") Long owner, @Param("q") AiTaskQuery query);

  @Update(
    "update ai_task set state=#{next},version=version+1,updated_at=current_timestamp(3) where task_id=#{id} and state=#{previous}"
  )
  int transition(
    @Param("id") String id,
    @Param("previous") String previous,
    @Param("next") String next
  );

  @Update(
    "update ai_task set state='SUCCEEDED',result_json=#{result},prompt_tokens=#{prompt},completion_tokens=#{completion},estimated_cost=#{cost},version=version+1,updated_at=current_timestamp(3) where task_id=#{id} and state='RUNNING'"
  )
  int success(
    @Param("id") String id,
    @Param("result") String result,
    @Param("prompt") Long prompt,
    @Param("completion") Long completion,
    @Param("cost") java.math.BigDecimal cost
  );

  @Update(
    "update ai_task set state='FAILED',error_code=#{code},version=version+1,updated_at=current_timestamp(3) where task_id=#{id} and state in ('QUEUED','RUNNING')"
  )
  int fail(@Param("id") String id, @Param("code") String code);

  @Select(
    "select count(*) from ai_task where member_id=#{id} and state in ('QUEUED','RUNNING')"
  )
  int pending(Long id);
}
