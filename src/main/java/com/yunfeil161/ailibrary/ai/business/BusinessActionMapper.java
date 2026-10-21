package com.yunfeil161.ailibrary.ai.business;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** CAS状态变更配合资源锁；EXECUTING必须先提交，才能调用真正的业务接口。 */
@Mapper
public interface BusinessActionMapper {
  @Insert(
    "insert into ai_business_action(action_id,member_id,skill_code,title,request_json,request_hash,state,version,expires_at,created_at,updated_at) " +
      "values(#{actionId},#{memberId},#{skillCode},#{title},#{requestJson},#{requestHash},'PREPARED',0,#{expiresAt},#{createdAt},#{updatedAt})"
  )
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(BusinessAction action);

  @Select(
    "select * from ai_business_action where action_id=#{actionId} and member_id=#{memberId}"
  )
  BusinessAction find(
    @Param("actionId") String actionId,
    @Param("memberId") Long memberId
  );

  @Select(
    "select count(*) from ai_business_action where member_id=#{memberId} and state='PREPARED' and expires_at>#{now}"
  )
  int pending(
    @Param("memberId") Long memberId,
    @Param("now") LocalDateTime now
  );

  @Select(
    "select * from ai_business_action where member_id=#{memberId} and request_hash=#{hash} " +
      "and (state in ('EXECUTING','UNKNOWN') or (state='PREPARED' and expires_at>#{now})) order by id desc limit 1"
  )
  BusinessAction reusable(
    @Param("memberId") Long memberId,
    @Param("hash") String hash,
    @Param("now") LocalDateTime now
  );

  @Update(
    "update ai_business_action set state=#{next},version=version+1,updated_at=#{now} " +
      "where action_id=#{actionId} and member_id=#{memberId} and state=#{previous} and version=#{version}"
  )
  int transition(
    @Param("actionId") String actionId,
    @Param("memberId") Long memberId,
    @Param("previous") String previous,
    @Param("next") String next,
    @Param("version") Long version,
    @Param("now") LocalDateTime now
  );

  @Update(
    "update ai_business_action set state=#{state},result_code=#{code},result_json=#{result},version=version+1,updated_at=#{now} " +
      "where action_id=#{actionId} and member_id=#{memberId} and state='EXECUTING'"
  )
  int finish(
    @Param("actionId") String actionId,
    @Param("memberId") Long memberId,
    @Param("state") String state,
    @Param("code") int code,
    @Param("result") String result,
    @Param("now") LocalDateTime now
  );

  @Select(
    "<script>select * from ai_business_action where member_id=#{memberId}" +
      "<if test='actionId != null'> and action_id=#{actionId}</if>" +
      "<choose><when test='state == &quot;EXPIRED&quot;'> and state='PREPARED' and expires_at &lt;= current_timestamp</when>" +
      "<when test='state == &quot;PREPARED&quot;'> and state='PREPARED' and expires_at &gt; current_timestamp</when>" +
      "<when test='state != null'> and state=#{state}</when></choose> order by id desc</script>"
  )
  List<BusinessAction> query(
    @Param("memberId") Long memberId,
    @Param("actionId") String actionId,
    @Param("state") String state
  );
}
