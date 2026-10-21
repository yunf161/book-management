package com.yunfeil161.ailibrary.communication.message.service;

import static com.yunfeil161.ailibrary.communication.common.CommunicationValidation.*;

import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.communication.common.cache.UnreadIndex;
import com.yunfeil161.ailibrary.communication.common.security.CommunicationAccess;
import com.yunfeil161.ailibrary.communication.conversation.service.ConversationService;
import com.yunfeil161.ailibrary.communication.message.dto.*;
import com.yunfeil161.ailibrary.communication.message.entity.*;
import com.yunfeil161.ailibrary.communication.message.mapper.MessageMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 消息发送、本人范围查询和已读操作，HTTP与后台共用同一缓存协调组件。 */
@Service
@RequiredArgsConstructor
public class MessageService {
  private final CommunicationAccess access;
  private final ConversationService conversations;
  private final MessageMapper messages;
  private final MessageWriter writer;
  private final UnreadIndex index;

  @DistributedLock(keys = "'communication:send:' + #currentMemberId")
  public CommunicationMessage send(MessageSendRequest q) {
    Long me = access.require("message:send:self").getMemberId();
    text(q.getBody(), 4000, true);
    text(q.getSubject(), 200, false);
    text(q.getClientMessageId(), 64, true);
    List<Long> recipients;
    if (q.getConversationId() != null) {
      if (q.getRecipientIds() != null && !q.getRecipientIds().isEmpty())
        throw new WrongFormatException("会话和收件名单不能同时提供");
      var c = conversations.visible(q.getConversationId(), me, false);
      recipients =
          List.of(
              me.equals(c.getParticipantOneId())
                  ? c.getParticipantTwoId()
                  : c.getParticipantOneId());
    } else {
      access.require("message:send:multiple");
      if (q.getRecipientIds() == null
          || q.getRecipientIds().isEmpty()
          || q.getRecipientIds().size() > 50
          || q.getRecipientIds().contains(null)) {
        throw new WrongFormatException("收件名单必须为1至50个有效ID");
      }
      recipients = q.getRecipientIds().stream().distinct().sorted().toList();
    }
    for (Long recipient : recipients) access.pair(me, recipient);
    String hash =
        hash(Arrays.asList(q.getConversationId(), recipients, q.getSubject(), q.getBody()));
    CommunicationMessage prior = messages.findClient(me, q.getClientMessageId());
    if (prior != null) return replay(prior, hash);
    CommunicationMessage message = new CommunicationMessage();
    message.setConversationId(q.getConversationId());
    message.setSenderMemberId(me);
    message.setSendType(q.getConversationId() == null ? "MULTICAST" : "DIRECT");
    message.setSubject(q.getSubject());
    message.setBody(q.getBody());
    message.setClientMessageId(q.getClientMessageId());
    message.setRequestHash(hash);
    message.setRecipientCount(recipients.size());
    try {
      return safe(writer.write(message, recipients), me);
    } catch (DuplicateKeyException e) {
      prior = messages.findClient(me, q.getClientMessageId());
      if (prior == null) throw e;
      return replay(prior, hash);
    }
  }

  public Map<String, Object> query(MessageQueryRequest q) {
    Long me = access.require("message:query:self").getMemberId();
    id(q.getId());
    id(q.getBusinessId());
    choice(q.getMessageType(), "CHAT", "SYSTEM");
    choice(q.getBusinessType(), "BORROW_APPLICATION", "BORROW_RECORD", "MEMBER_CHANGE");
    id(q.getConversationId());
    id(q.getSenderMemberId());
    id(q.getRecipientMemberId());
    choice(q.getBox(), "INBOX", "OUTBOX", "ALL");
    choice(q.getSendType(), "DIRECT", "MULTICAST");
    range(q.getCreatedFrom(), q.getCreatedTo());
    if (q.getBox() == null) q.setBox("ALL");
    if (q.getAfterSequence() != null && (q.getAfterSequence() < 0 || q.getConversationId() == null))
      throw new WrongFormatException("增量查询必须提供会话和非负序号");
    if (q.getConversationId() != null) conversations.visible(q.getConversationId(), me, false);
    // 接收者不可通过筛选其他收件人推测广播接收名单。
    if (q.getRecipientMemberId() != null
        && !q.getRecipientMemberId().equals(me)
        && !"OUTBOX".equals(q.getBox())) throw new ForbiddenException("只能在本人发件箱筛选其他收件人");
    if (q.getRead() != null && !"INBOX".equals(q.getBox()))
      throw new WrongFormatException("read仅支持收件箱查询");
    List<CommunicationMessage> cached = index.queryIfSimple(me, q);
    PageResult<CommunicationMessage> result;
    if (cached != null) {
      int p = q.getPage() == null ? 1 : q.getPage();
      int size = q.getPageSize() == null ? 20 : q.getPageSize();
      if (p < 1 || size < 1 || size > 100) throw new WrongFormatException("分页参数不合法");
      long start = (long) (p - 1) * size;
      result =
          new PageResult<>(
              (long) cached.size(),
              start >= cached.size()
                  ? List.of()
                  : cached.subList((int) start, (int) Math.min(start + size, cached.size())));
    } else {
      page(q);
      var rows = messages.query(me, q);
      result = new PageResult<>(new PageInfo<>(rows).getTotal(), rows);
    }
    result.getRows().forEach(row -> safe(row, me));
    return Map.of(
        "total",
        result.getTotal(),
        "rows",
        result.getRows(),
        "unreadTotal",
        messages.unreadCount(me));
  }

  public void read(Long messageId) {
    Long me = access.require("message:read:self").getMemberId();
    id(messageId);
    index.read(me, messageId);
  }

  private CommunicationMessage replay(CommunicationMessage prior, String hash) {
    if (!hash.equals(prior.getRequestHash())) throw new ConflictException("clientMessageId已用于不同内容");
    return safe(prior, prior.getSenderMemberId());
  }

  private CommunicationMessage safe(CommunicationMessage message, Long me) {
    if (me.equals(message.getSenderMemberId()))
      message.setRecipients(messages.recipients(message.getId()));
    message.setRequestHash(null);
    return message;
  }

  private String hash(Object normalized) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(JsonUtils.toJson(normalized).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256不可用", e);
    }
  }
}
