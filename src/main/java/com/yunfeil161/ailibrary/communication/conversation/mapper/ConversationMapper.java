package com.yunfeil161.ailibrary.communication.conversation.mapper;

import com.yunfeil161.ailibrary.communication.conversation.dto.*;
import com.yunfeil161.ailibrary.communication.conversation.entity.Conversation;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 会话访问；发送时锁定已有会话行以串行分配序号。 */
@Mapper
public interface ConversationMapper {
  Conversation find(@Param("id") Long id);

  Conversation lock(@Param("id") Long id);

  Conversation findPair(@Param("one") Long one, @Param("two") Long two);

  int insert(Conversation conversation);

  int update(@Param("id") Long id, @Param("q") ConversationUpdateRequest request);

  int advance(
      @Param("id") Long id, @Param("messageId") Long messageId, @Param("sequence") Long sequence);

  List<Conversation> query(
      @Param("memberId") Long memberId, @Param("q") ConversationQueryRequest request);
}
