package com.yunfeil161.ailibrary.policy.service;

import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.identity.entity.Member;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import com.yunfeil161.ailibrary.policy.dto.*;
import com.yunfeil161.ailibrary.policy.entity.PolicyVersion;
import com.yunfeil161.ailibrary.policy.mapper.PolicyMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 直接读取数据库生效指针，避免过期缓存决定准入和借阅资格。 */
@Service
@RequiredArgsConstructor
public class PolicyState {

  private final PolicyMapper mapper;
  private final MemberMapper members;

  public PolicyVersion current(String type) {
    if (
      !Set.of("ADMISSION", "BORROWING").contains(type)
    ) throw new WrongFormatException("政策类型必须为ADMISSION或BORROWING");
    // 业务事务可能已因权限查询建立旧快照；锁住指针后必须使用当前读。
    PolicyVersion value = org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive() ? mapper.currentLocked(type) : mapper.current(type);
    if (value == null) throw new ServiceUnavailableException(
      "当前政策尚未配置"
    );
    return value;
  }

  public String ruleVersion() {
    return "BORROWING-" + current("BORROWING").getPolicyVersion();
  }

  public boolean requiresConsent(Member member) {
    return (
      member.getMemberType() != MemberType.AI_LIBRARIAN &&
      !ruleVersion().equals(member.getAcceptedRuleVersion())
    );
  }

  public boolean requiresConsent(Long memberId) {
    Member member = members.findMemberById(memberId);
    if (member == null) throw new UnauthorizedException("成员不存在");
    return requiresConsent(member);
  }

  /** 先锁准入再锁借阅指针，与发布和业务写入保持相同数据库锁顺序。 */
  public void lockPolicies() {
    mapper.lockCurrent("ADMISSION");
    mapper.lockCurrent("BORROWING");
  }

  public BorrowingPolicyParameters borrowing() {
    return JsonUtils.fromJson(
      current("BORROWING").getParametersJson(),
      BorrowingPolicyParameters.class
    );
  }

  public void requireAccepted(Long memberId) {
    if (requiresConsent(memberId)) throw new ForbiddenException(
      "请先接受当前借阅须知"
    );
  }

  /** 原申请快照必须与当前政策完全一致；旧申请仍可取消或拒绝。 */
  public void verifyBorrowSnapshot(Long id, Long version) {
    PolicyVersion value = current("BORROWING");
    if (
      !Objects.equals(id, value.getId()) ||
      !Objects.equals(version, value.getPolicyVersion())
    ) throw new ConflictException("借阅政策已更新，请取消原申请并重新提交");
  }

  public void requireAdmission(Member member, String acceptedRule) {
    requireAccepted(member.getId());
    if (!ruleVersion().equals(acceptedRule)) throw new ConflictException(
      "请使用当前借阅须知版本提交资格申请"
    );
    AdmissionPolicyParameters p = JsonUtils.fromJson(
      current("ADMISSION").getParametersJson(),
      AdmissionPolicyParameters.class
    );
    if (
      member.getEmail() == null || member.getEmail().isBlank()
    ) throw new ConflictException("邮箱不能为空");
    if (
      Boolean.TRUE.equals(p.getRequireVerifiedEmail()) &&
      member.getEmailVerifiedAt() == null
    ) throw new ConflictException("请先验证邮箱");
    for (var field : p.getRequiredFields()) {
      String value =
        field == AdmissionPolicyParameters.RequiredField.NAME
          ? member.getName()
          : member.getPhone();
      if (value == null || value.isBlank()) throw new ConflictException(
        "缺少准入字段：" + field
      );
    }
  }

  public int defaultQuota() {
    return JsonUtils.fromJson(
      current("ADMISSION").getParametersJson(),
      AdmissionPolicyParameters.class
    ).getDefaultMaxBorrowCount();
  }

  /** 对外展示解析后的参数，不暴露内部JSON存储格式。 */
  public Map<String, Object> view(PolicyVersion p) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", p.getId());
    result.put("type", p.getType());
    result.put("title", p.getTitle());
    result.put("content", p.getContent());
    result.put(
      "parameters",
      "ADMISSION".equals(p.getType())
        ? JsonUtils.fromJson(
            p.getParametersJson(),
            AdmissionPolicyParameters.class
          )
        : JsonUtils.fromJson(
            p.getParametersJson(),
            BorrowingPolicyParameters.class
          )
    );
    result.put("status", p.getStatus());
    result.put("policyVersion", p.getPolicyVersion());
    result.put(
      "ruleVersion",
      "BORROWING".equals(p.getType()) && p.getPolicyVersion() != null
        ? "BORROWING-" + p.getPolicyVersion()
        : null
    );
    result.put("version", p.getVersion());
    result.put("publishedBy", p.getPublishedBy());
    result.put("publishedAt", p.getPublishedAt());
    result.put("createdAt", p.getCreatedAt());
    result.put("updatedAt", p.getUpdatedAt());
    return result;
  }
}
