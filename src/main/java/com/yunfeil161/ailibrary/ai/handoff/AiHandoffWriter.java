package com.yunfeil161.ailibrary.ai.handoff;

import com.yunfeil161.ailibrary.communication.conversation.dto.*;
import com.yunfeil161.ailibrary.communication.conversation.service.ConversationService;
import com.yunfeil161.ailibrary.communication.message.dto.MessageSendRequest;
import com.yunfeil161.ailibrary.communication.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单独Bean确保交接、私聊摘要和Outbox在一个事务内保存。 */
@Service
@RequiredArgsConstructor
public class AiHandoffWriter {

  private final AiHandoffMapper mapper;
  private final ConversationService conversations;
  private final MessageService messages;

  @Transactional
  @com.yunfeil161.ailibrary.audit.Audited(
    resource = com.yunfeil161.ailibrary.audit.AuditResource.AI_HANDOFF
  )
  public AiHandoff create(AiHandoffService.Request request, Long memberId) {
    var input = new ConversationCreateRequest();
    input.setTargetMemberId(request.staffMemberId());
    input.setSubject("AI咨询转人工");
    var conversation = conversations.create(input);
    if ("CLOSED".equals(conversation.getStatus())) {
      var update = new ConversationUpdateRequest();
      update.setExpectedVersion(conversation.getVersion());
      update.setStatus("OPEN");
      conversation = conversations.update(conversation.getId(), update);
    }
    var row = new AiHandoff();
    row.setMemberId(memberId);
    row.setStaffMemberId(request.staffMemberId());
    row.setConversationId(conversation.getId());
    row.setAiConversationId(request.aiConversationId());
    row.setSummary(request.summary().trim());
    if (mapper.insert(row) != 1) throw new IllegalStateException(
      "保存人工交接失败"
    );
    var message = new MessageSendRequest();
    message.setConversationId(conversation.getId());
    message.setSubject("AI咨询转人工");
    message.setBody("用户已确认的交接摘要：\n" + row.getSummary());
    message.setClientMessageId("ai-handoff-" + row.getId());
    messages.send(message);
    return mapper.find(row.getId());
  }
}
