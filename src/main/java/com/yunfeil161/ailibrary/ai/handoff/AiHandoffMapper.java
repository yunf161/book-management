package com.yunfeil161.ailibrary.ai.handoff;

import java.util.List;
import org.apache.ibatis.annotations.*;

/** 一人一条活动交接由唯一约束兜底，结束后释放active_slot。 */
@Mapper
public interface AiHandoffMapper {
  @Insert(
    "insert into ai_handoff(member_id,staff_member_id,conversation_id,ai_conversation_id,summary) values(#{memberId},#{staffMemberId},#{conversationId},#{aiConversationId},#{summary})"
  )
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(AiHandoff row);

  @Select("select * from ai_handoff where id=#{id}")
  AiHandoff find(Long id);

  @Select(
    "select * from ai_handoff where member_id=#{member} and active_slot=1"
  )
  AiHandoff active(Long member);

  @Select(
    "<script>select * from ai_handoff where 1=1<if test='owner != null'> and (member_id=#{owner} or staff_member_id=#{owner})</if><if test='q.id != null'> and id=#{q.id}</if><if test='q.memberId != null'> and member_id=#{q.memberId}</if><if test='q.staffMemberId != null'> and staff_member_id=#{q.staffMemberId}</if><if test='q.status != null'> and status=#{q.status}</if> order by id desc</script>"
  )
  List<AiHandoff> query(
    @Param("owner") Long owner,
    @Param("q") AiHandoffQuery q
  );

  @Update(
    "update ai_handoff set status=#{next},active_slot=#{active},version=version+1,updated_at=current_timestamp(3) where id=#{id} and version=#{version} and status=#{previous}"
  )
  int transition(
    @Param("id") Long id,
    @Param("version") Long version,
    @Param("previous") String previous,
    @Param("next") String next,
    @Param("active") Integer active
  );

  @Select(
    "select count(*) from ai_handoff where member_id=#{member} and ai_conversation_id=#{conversation} and status='ACCEPTED'"
  )
  int managed(
    @Param("member") Long member,
    @Param("conversation") String conversation
  );
}
