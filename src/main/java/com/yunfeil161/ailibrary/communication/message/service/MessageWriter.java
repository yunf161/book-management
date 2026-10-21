package com.yunfeil161.ailibrary.communication.message.service;

import static com.yunfeil161.ailibrary.communication.common.CommunicationValidation.one;

import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.communication.conversation.mapper.ConversationMapper;
import com.yunfeil161.ailibrary.communication.conversation.service.ConversationService;
import com.yunfeil161.ailibrary.communication.event.entity.*;
import com.yunfeil161.ailibrary.communication.event.mapper.OutboxMapper;
import com.yunfeil161.ailibrary.communication.message.entity.CommunicationMessage;
import com.yunfeil161.ailibrary.communication.message.mapper.MessageMapper;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 独立事务边界；唯一冲突回滚后，外层才能查询先前幂等结果。 */
@Service
@RequiredArgsConstructor
public class MessageWriter {
  private final MessageMapper messages;
  private final ConversationService conversations;
  private final ConversationMapper conversationMapper;
  private final OutboxMapper outbox;

  @Transactional
  public CommunicationMessage write(CommunicationMessage message, List<Long> recipients) {
    if (message.getConversationId() != null) {
      var c = conversations.visible(message.getConversationId(), message.getSenderMemberId(), true);
      if (!"OPEN".equals(c.getStatus())) throw new ConflictException("会话已关闭");
      message.setSequence(c.getLastSequence() + 1);
    }
    one(messages.insert(message));
    for (Long recipient : recipients) {
      one(messages.insertRecipient(message.getId(), recipient));
      MessageCreatedEvent event = new MessageCreatedEvent();
      event.setEventId(UUID.randomUUID().toString());
      event.setEventType("message.created");
      event.setSchemaVersion(1);
      event.setMessageId(message.getId());
      event.setRecipientMemberId(recipient);
      event.setOccurredAt(LocalDateTime.now());
      OutboxEvent row = new OutboxEvent();
      row.setEventId(event.getEventId());
      row.setEventType(event.getEventType());
      row.setSchemaVersion(1);
      row.setMessageId(message.getId());
      row.setRecipientMemberId(recipient);
      row.setPayloadJson(JsonUtils.toJson(event));
      one(outbox.insert(row));
    }
    if (message.getConversationId() != null) {
      one(
          conversationMapper.advance(
              message.getConversationId(), message.getId(), message.getSequence()));
    }
    return messages.find(message.getId());
  }
}
