package com.yunfeil161.ailibrary.policy.service;

import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.communication.websocket.NotificationSocket;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import com.yunfeil161.ailibrary.policy.dto.RuleDecisionRequest;
import com.yunfeil161.ailibrary.policy.mapper.PolicyMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 明确接受才修改成员记录；拒绝撤销所有设备会话，不改变资格状态。 */
@Service
@RequiredArgsConstructor
public class RuleConsentService {

  private final PolicyState state;
  private final PolicyMapper policies;
  private final MemberMapper members;
  private final LoginSessionService sessions;
  private final CacheClient cache;
  private final NotificationSocket sockets;

  private Long memberId() {
    var session = LoginUserContext.get();
    if (session == null) throw new UnauthorizedException("请先登录");
    if (
      session.getMemberType() == MemberType.AI_LIBRARIAN
    ) throw new ForbiddenException("AI不使用人类借阅须知");
    return session.getMemberId();
  }

  public Map<String, Object> current() {
    var member = members.findMemberById(memberId());
    var result = state.view(state.current("BORROWING"));
    result.put("acceptedRuleVersion", member.getAcceptedRuleVersion());
    result.put("requiresConsent", state.requiresConsent(member));
    result.put("memberVersion", member.getVersion());
    return result;
  }

  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:policy-current:ADMISSION'",
      "'lock:resource:policy-current:BORROWING'",
      "'lock:resource:member:' + #currentMemberId",
    }
  )
  @Audited(
    resource = AuditResource.MEMBER,
    id = "#currentMemberId",
    always = true
  )
  public Map<String, Object> decide(RuleDecisionRequest r) {
    Long id = memberId();
    if (
      r.getRuleVersion() == null || r.getRuleVersion().isBlank()
    ) throw new WrongFormatException("ruleVersion不能为空");
    if ("DECLINE".equals(r.getDecision())) {
      try {
        sessions.deleteAllForMember(id);
        sockets.closeMember(id);
      } catch (RuntimeException e) {
        throw new ServiceUnavailableException("会话撤销失败，请稍后重试");
      }
      return Map.of("loggedOut", true);
    }
    if (!"ACCEPT".equals(r.getDecision())) throw new WrongFormatException(
      "decision必须为ACCEPT或DECLINE"
    );
    state.lockPolicies();
    if (
      !state.ruleVersion().equals(r.getRuleVersion())
    ) throw new ConflictException("须知版本已更新");
    var member = members.findMemberById(id);
    if (!state.requiresConsent(member)) return current();
    if (
      r.getExpectedMemberVersion() == null || r.getExpectedMemberVersion() < 0
    ) throw new WrongFormatException("expectedMemberVersion必须为非负数");
    if (
      policies.accept(id, r.getRuleVersion(), r.getExpectedMemberVersion()) != 1
    ) throw new ConflictException("成员资料已变化，请刷新后重新确认");
    // 与人员更新相同，在事务内严格删除缓存，失败使本次接受回滚。
    cache.deleteRequired("member:reader:" + id);
    cache.deleteRequired("member:employee:" + id);
    return current();
  }
}
