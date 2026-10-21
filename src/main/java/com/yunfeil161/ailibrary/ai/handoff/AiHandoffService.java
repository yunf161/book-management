package com.yunfeil161.ailibrary.ai.handoff;

import com.github.pagehelper.*;
import com.yunfeil161.ailibrary.ai.service.AiGatewayClient;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.communication.conversation.dto.*;
import com.yunfeil161.ailibrary.communication.conversation.service.ConversationService;
import com.yunfeil161.ailibrary.communication.message.dto.MessageSendRequest;
import com.yunfeil161.ailibrary.communication.message.service.MessageService;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 交接复用原私聊、Outbox、MQ、未读与WebSocket，不再建立第二套聊天系统。 */
@Service
@RequiredArgsConstructor
public class AiHandoffService {

  public record Request(
    Long staffMemberId,
    String aiConversationId,
    String summary
  ) {}

  private final AiHandoffMapper mapper;
  private final PermissionAccess access;
  private final AiGatewayClient gateway;
  private final JdbcTemplate jdbc;

  @DistributedLock(
    keys = "'lock:resource:ai-handoff:member:' + #currentMemberId"
  )
  public AiHandoff create(Request request) {
    var session = access.require("ai-handoff:create", DataScope.SELF);
    if (
      request == null ||
      request.staffMemberId() == null ||
      request.summary() == null ||
      request.summary().isBlank() ||
      request.summary().length() > 2000
    ) throw new WrongFormatException("请指定馆员并确认2000字以内的交接摘要");
    try {
      if (
        !UUID.fromString(request.aiConversationId())
          .toString()
          .equals(request.aiConversationId())
      ) throw new IllegalArgumentException();
    } catch (RuntimeException invalid) {
      throw new WrongFormatException("AI会话ID格式错误");
    }
    requireStaff(request.staffMemberId());
    var old = mapper.active(session.getMemberId());
    if (old != null) {
      if (
        old.getStaffMemberId().equals(request.staffMemberId()) &&
        old.getAiConversationId().equals(request.aiConversationId()) &&
        old.getSummary().equals(request.summary().trim())
      ) return old;
      throw new ConflictException("已有待处理的人工交接，请先完成或关闭");
    }
    // 在持久事务外核验AI会话归属；网关拒绝访问其他成员的会话。
    gateway.conversation(
      session.getMemberId(),
      request.aiConversationId(),
      false
    );
    return writer.create(request, session.getMemberId());
  }

  private final AiHandoffWriter writer;

  public PageResult<AiHandoff> query(AiHandoffQuery q) {
    var session = access.require("ai-handoff:query", DataScope.SELF);
    Long owner =
      access.requireScope("ai-handoff:query") == DataScope.ALL
        ? null
        : session.getMemberId();
    PageHelper.startPage(
      q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage(),
      q.getPageSize() == null || q.getPageSize() < 1
        ? 10
        : Math.min(q.getPageSize(), 100)
    );
    var page = new PageInfo<>(mapper.query(owner, q));
    return new PageResult<>(page.getTotal(), page.getList());
  }

  @Transactional
  @com.yunfeil161.ailibrary.audit.Audited(
    resource = com.yunfeil161.ailibrary.audit.AuditResource.AI_HANDOFF,
    id = "#id"
  )
  public AiHandoff decide(Long id, String decision, Long version) {
    var session = access.require("ai-handoff:query", DataScope.SELF);
    var row = mapper.find(id);
    if (row == null) throw new NotFoundException("交接不存在");
    boolean assigned = row.getStaffMemberId().equals(session.getMemberId()),
      owner = row.getMemberId().equals(session.getMemberId());
    if (!assigned && !owner) throw new ForbiddenException("不是本次交接参与者");
    String next;
    if ("ACCEPT".equals(decision)) {
      access.require("ai-handoff:accept", DataScope.SELF);
      requireStaff(session.getMemberId());
      if (!assigned) throw new ForbiddenException("只有指定馆员可以接管");
      if (!"WAITING".equals(row.getStatus())) throw new ConflictException(
        "交接已处理"
      );
      next = "ACCEPTED";
    } else if ("CLOSE".equals(decision)) next = "CLOSED";
    else throw new WrongFormatException("decision必须为ACCEPT或CLOSE");
    if (version == null || version < 0) throw new WrongFormatException(
      "expectedVersion必须为非负数"
    );
    if (
      mapper.transition(
        id,
        version,
        row.getStatus(),
        next,
        "CLOSED".equals(next) ? null : 1
      ) != 1
    ) throw new ConflictException("交接版本已变化");
    return mapper.find(id);
  }

  private void requireStaff(Long member) {
    if (
      jdbc.queryForObject(
        "select count(*) from member m join employee_profile e on e.member_id=m.id where m.id=? and m.member_type='EMPLOYEE' and m.account_status='ACTIVE' and e.employment_status='ACTIVE'",
        Integer.class,
        member
      ) != 1
    ) throw new ForbiddenException("接管人必须为启用且在职的馆员");
  }
}
