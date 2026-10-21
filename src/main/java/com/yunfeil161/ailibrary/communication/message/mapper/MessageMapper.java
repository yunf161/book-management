package com.yunfeil161.ailibrary.communication.message.mapper;

import com.yunfeil161.ailibrary.communication.message.dto.MessageQueryRequest;
import com.yunfeil161.ailibrary.communication.message.entity.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 消息及收件关系数据访问；每个查询显式限定当前成员。 */
@Mapper
public interface MessageMapper {
  CommunicationMessage findNotification(@Param("key") String key);
  CommunicationMessage find(@Param("id") Long id);

  CommunicationMessage findClient(@Param("sender") Long sender, @Param("client") String client);

  int insert(CommunicationMessage message);

  int insertRecipient(@Param("messageId") Long messageId, @Param("recipient") Long recipient);

  List<MessageRecipient> recipients(@Param("messageId") Long messageId);

  MessageRecipient recipient(@Param("messageId") Long messageId, @Param("memberId") Long memberId);

  int markRead(@Param("messageId") Long messageId, @Param("memberId") Long memberId);

  List<CommunicationMessage> query(
      @Param("memberId") Long memberId, @Param("q") MessageQueryRequest q);

  List<CommunicationMessage> unread(@Param("memberId") Long memberId);

  List<CommunicationMessage> unreadIds(
      @Param("memberId") Long memberId, @Param("ids") java.util.Collection<String> ids);

  long unreadCount(@Param("memberId") Long memberId);
}
