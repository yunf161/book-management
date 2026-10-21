package com.yunfeil161.ailibrary.policy.service;

import com.github.pagehelper.*;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.communication.event.entity.OutboxEvent;
import com.yunfeil161.ailibrary.communication.event.mapper.OutboxMapper;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.policy.dto.*;
import com.yunfeil161.ailibrary.policy.entity.PolicyVersion;
import com.yunfeil161.ailibrary.policy.mapper.PolicyMapper;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 草稿维护和发布；指针、历史状态及Outbox在同一事务提交。 */
@Service
@RequiredArgsConstructor
public class PolicyService {

  private final PolicyMapper mapper;
  private final PolicyState state;
  private final PermissionAccess access;
  private final OutboxMapper outbox;

  public PageResult<Map<String, Object>> query(PolicyQueryRequest q) {
    access.require(PermissionCodes.POLICY_QUERY, DataScope.ALL);
    if (q.getId() != null && q.getId() < 1) throw new WrongFormatException(
      "政策ID必须为正数"
    );
    if (q.getType() != null) type(q.getType());
    if (
      q.getStatus() != null &&
      !Set.of("DRAFT", "ACTIVE", "RETIRED").contains(q.getStatus())
    ) throw new WrongFormatException("政策状态错误");
    PageHelper.startPage(
      q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage(),
      q.getPageSize() == null || q.getPageSize() < 1
        ? 10
        : Math.min(q.getPageSize(), 100)
    );
    var page = new PageInfo<>(mapper.query(q));
    return new PageResult<>(
      page.getTotal(),
      page.getList().stream().map(state::view).toList()
    );
  }

  @Transactional
  @Audited(resource = AuditResource.POLICY, id = "")
  public Map<String, Object> create(PolicyWriteRequest r) {
    access.require(PermissionCodes.POLICY_CREATE, DataScope.ALL);
    type(r.getType());
    PolicyVersion p = draft(r, r.getType());
    if (mapper.insert(p) != 1) throw new IllegalStateException("新增政策失败");
    return state.view(mapper.find(p.getId()));
  }

  @Transactional
  @Audited(resource = AuditResource.POLICY, id = "#id")
  public Map<String, Object> update(Long id, PolicyWriteRequest r) {
    access.require(PermissionCodes.POLICY_UPDATE, DataScope.ALL);
    PolicyVersion old = find(id);
    if (!"DRAFT".equals(old.getStatus())) throw new ConflictException(
      "已发布政策不能修改"
    );
    if (
      r.getType() != null && !old.getType().equals(r.getType())
    ) throw new WrongFormatException("政策类型创建后不能修改");
    version(r.getExpectedVersion());
    PolicyVersion p = draft(r, old.getType());
    p.setId(id);
    p.setVersion(r.getExpectedVersion());
    if (mapper.update(p) != 1) throw new ConflictException("政策版本已变化");
    return state.view(mapper.find(id));
  }

  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:policy-current:ADMISSION'",
      "'lock:resource:policy-current:BORROWING'",
    }
  )
  @Audited(resource = AuditResource.POLICY, id = "#id")
  public Map<String, Object> publish(Long id, PolicyPublishRequest r) {
    var session = access.require(PermissionCodes.POLICY_PUBLISH, DataScope.ALL);
    state.lockPolicies();
    PolicyVersion p = find(id);
    Long previous = mapper.lockCurrent(p.getType());
    // 同一已生效版本重试不重复发布、递增序号或生成事件。
    if (
      "ACTIVE".equals(p.getStatus()) && Objects.equals(previous, id)
    ) return state.view(p);
    if (!"DRAFT".equals(p.getStatus())) throw new ConflictException(
      "只有草稿可以发布"
    );
    version(r.getExpectedVersion());
    if (!r.isCurrentPolicyIdProvided()) throw new WrongFormatException(
      "必须提供expectedCurrentPolicyId"
    );
    if (
      !Objects.equals(previous, r.getExpectedCurrentPolicyId())
    ) throw new ConflictException("当前政策已变化，请刷新后重试");
    long sequence = mapper.sequence(p.getType()) + 1;
    if (
      previous != null && mapper.retire(previous) != 1
    ) throw new ConflictException("原政策已变化");
    if (
      mapper.activate(
        id,
        r.getExpectedVersion(),
        sequence,
        session.getMemberId()
      ) != 1
    ) throw new ConflictException("草稿已变化");
    if (
      mapper.point(p.getType(), id, sequence) != 1
    ) throw new IllegalStateException("生效指针更新失败");

    OutboxEvent event = new OutboxEvent();
    event.setEventId(UUID.randomUUID().toString());
    event.setEventType("policy.published");
    event.setSchemaVersion(1);
    event.setPayloadJson(
      JsonUtils.toJson(
        Map.of(
          "eventId",
          event.getEventId(),
          "eventType",
          "policy.published",
          "policyId",
          id,
          "type",
          p.getType(),
          "policyVersion",
          sequence,
          "occurredAt",
          LocalDateTime.now().toString()
        )
      )
    );
    if (outbox.insert(event) != 1) throw new IllegalStateException(
      "政策事件保存失败"
    );
    return state.view(mapper.find(id));
  }

  private PolicyVersion find(Long id) {
    if (id == null || id < 1) throw new WrongFormatException(
      "政策ID必须为正数"
    );
    var p = mapper.find(id);
    if (p == null) throw new NotFoundException("政策不存在");
    return p;
  }

  private void type(String value) {
    if (
      value == null || !Set.of("ADMISSION", "BORROWING").contains(value)
    ) throw new WrongFormatException("政策类型错误");
  }

  private void version(Long value) {
    if (value == null || value < 0) throw new WrongFormatException(
      "expectedVersion必须为非负数"
    );
  }

  private PolicyVersion draft(PolicyWriteRequest r, String type) {
    if (
      r.getTitle() == null ||
      r.getTitle().isBlank() ||
      r.getTitle().length() > 200 ||
      r.getContent() == null ||
      r.getContent().isBlank()
    ) throw new WrongFormatException("政策标题和正文不能为空，标题不超过200字");
    Object parameters;
    if ("ADMISSION".equals(type)) {
      var p = r.getAdmissionParameters();
      if (
        p == null ||
        r.getBorrowingParameters() != null ||
        p.getRequireVerifiedEmail() == null ||
        p.getRequiredFields() == null ||
        p.getRequiredFields().contains(null) ||
        p.getDefaultMaxBorrowCount() == null ||
        p.getDefaultMaxBorrowCount() < 0
      ) throw new WrongFormatException("准入参数不完整或无效");
      parameters = p;
    } else {
      var p = r.getBorrowingParameters();
      if (
        p == null ||
        r.getAdmissionParameters() != null ||
        p.getBlockBorrowWhenOverdue() == null ||
        p.getMaxRenewCount() == null ||
        p.getMaxRenewCount() < 0 ||
        !positive(p.getApplicationValidityDays()) ||
        !positive(p.getDefaultBorrowDays()) ||
        !positive(p.getMaxBorrowDays()) ||
        p.getDefaultBorrowDays() > p.getMaxBorrowDays() ||
        !positive(p.getDefaultRenewDays()) ||
        !positive(p.getMaxRenewDays()) ||
        p.getDefaultRenewDays() > p.getMaxRenewDays()
      ) throw new WrongFormatException("借阅参数不完整或无效");
      parameters = p;
    }
    PolicyVersion p = new PolicyVersion();
    p.setType(type);
    p.setTitle(r.getTitle().trim());
    p.setContent(r.getContent());
    p.setParametersJson(JsonUtils.toJson(parameters));
    return p;
  }

  private boolean positive(Integer value) {
    return value != null && value > 0;
  }
}
