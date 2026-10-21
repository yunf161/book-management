package com.yunfeil161.ailibrary.communication.conversation.service;

import static com.yunfeil161.ailibrary.communication.common.CommunicationValidation.*;

import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.communication.common.security.CommunicationAccess;
import com.yunfeil161.ailibrary.communication.conversation.dto.*;
import com.yunfeil161.ailibrary.communication.conversation.entity.Conversation;
import com.yunfeil161.ailibrary.communication.conversation.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 一对一会话业务；同一发送人创建操作串行，数据库唯一约束处理反向并发。 */
@Service
@RequiredArgsConstructor
public class ConversationService {
  private final ConversationMapper mapper;
  private final CommunicationAccess access;

  @DistributedLock(keys = "@communicationKeys.conversation(#q.targetMemberId)")
  @Transactional
  public Conversation create(ConversationCreateRequest q) {
    Long me = access.require("conversation:create:self").getMemberId();
    text(q.getSubject(), 200, false);
    access.pair(me, q.getTargetMemberId());
    Long oneId = Math.min(me, q.getTargetMemberId());
    Long twoId = Math.max(me, q.getTargetMemberId());
    // MySQL INSERT IGNORE仅用于参与者唯一冲突；Service已检查所有输入。
    Conversation found = mapper.findPair(oneId, twoId);
    if (found != null) return found;
    Conversation c = new Conversation();
    c.setInitiatorMemberId(me);
    c.setParticipantOneId(oneId);
    c.setParticipantTwoId(twoId);
    c.setSubject(q.getSubject());
    mapper.insert(c);
    return mapper.findPair(oneId, twoId);
  }

  public PageResult<Conversation> query(ConversationQueryRequest q) {
    Long me = access.require("conversation:query:self").getMemberId();
    id(q.getId());
    id(q.getTargetMemberId());
    choice(q.getStatus(), "OPEN", "CLOSED");
    range(q.getCreatedFrom(), q.getCreatedTo());
    page(q);
    var rows = mapper.query(me, q);
    return new PageResult<>(new PageInfo<>(rows).getTotal(), rows);
  }

  @Transactional
  public Conversation update(Long id, ConversationUpdateRequest q) {
    Long me = access.require("conversation:update:self").getMemberId();
    visible(id, me, true);
    if (q.getExpectedVersion() == null || q.getExpectedVersion() < 0)
      throw new WrongFormatException("expectedVersion不能为空且必须非负");
    text(q.getSubject(), 200, false);
    choice(q.getStatus(), "OPEN", "CLOSED");
    if (q.getSubject() == null && q.getStatus() == null) throw new WrongFormatException("没有更新字段");
    int affected = mapper.update(id, q);
    if (affected == 0) throw new ConflictException("会话版本已过期");
    one(affected);
    return mapper.find(id);
  }

  /** 在权限检查之后锁定会话；外部不可指定其他人的会话。 */
  public Conversation visible(Long id, Long me, boolean lock) {
    id(id);
    Conversation c = lock ? mapper.lock(id) : mapper.find(id);
    if (c == null) throw new NotFoundException("会话不存在");
    if (!me.equals(c.getParticipantOneId()) && !me.equals(c.getParticipantTwoId()))
      throw new ForbiddenException("不是会话参与者");
    return c;
  }
}
