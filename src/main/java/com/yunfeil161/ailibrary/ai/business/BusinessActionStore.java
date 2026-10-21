package com.yunfeil161.ailibrary.ai.business;

import com.yunfeil161.ailibrary.common.exception.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 独立Bean确保事务真正生效；不在事务中等待模型或回调业务HTTP。 */
@Service
@RequiredArgsConstructor
public class BusinessActionStore {

  private final BusinessActionMapper mapper;

  public record Claim(boolean acquired, BusinessAction action) {}

  @Transactional
  public BusinessAction prepare(BusinessAction action) {
    var existing = mapper.reusable(
      action.getMemberId(),
      action.getRequestHash(),
      LocalDateTime.now()
    );
    if (existing != null) {
      if ("PREPARED".equals(existing.getState())) return existing;
      throw new ConflictException(
        "相同操作存在待核实的执行结果，请先查询业务状态，不能重复提交"
      );
    }
    if (
      mapper.pending(action.getMemberId(), LocalDateTime.now()) >= 20
    ) throw new ConflictException("待确认操作过多，请先处理已有草稿或等待过期");
    if (mapper.insert(action) != 1) throw new IllegalStateException(
      "保存AI操作草稿失败"
    );
    return require(action.getActionId(), action.getMemberId());
  }

  public BusinessAction require(String id, Long memberId) {
    var action = mapper.find(id, memberId);
    if (action == null) throw new NotFoundException("操作不存在或不属于本人");
    return action;
  }

  @Transactional
  public Claim claim(String id, Long memberId, String hash) {
    var action = require(id, memberId);
    if (!action.getRequestHash().equals(hash)) throw new ConflictException(
      "确认内容与操作草稿不一致，请重新预览"
    );
    // SUCCEEDED重放结果；EXECUTING/UNKNOWN等状态绝不重新提交业务写操作。
    if (!"PREPARED".equals(action.getState())) return new Claim(false, action);
    if (
      !action.getExpiresAt().isAfter(LocalDateTime.now())
    ) throw new ConflictException("操作草稿已过期，请重新生成");
    if (
      mapper.transition(
        id,
        memberId,
        "PREPARED",
        "EXECUTING",
        action.getVersion(),
        LocalDateTime.now()
      ) != 1
    ) throw new ConflictException("操作状态已变化");
    return new Claim(true, require(id, memberId));
  }

  @Transactional
  public BusinessAction finish(
    String id,
    Long memberId,
    String state,
    int code,
    String result
  ) {
    if (
      mapper.finish(id, memberId, state, code, result, LocalDateTime.now()) != 1
    ) throw new ConflictException("操作执行状态已变化，请查询实际业务结果");
    return require(id, memberId);
  }

  @Transactional
  public BusinessAction cancel(String id, Long memberId) {
    var action = require(id, memberId);
    if ("CANCELLED".equals(action.getState())) return action;
    if (!"PREPARED".equals(action.getState())) throw new ConflictException(
      "当前操作不能取消，已开始的业务无法通过取消草稿撤销"
    );
    if (
      mapper.transition(
        id,
        memberId,
        "PREPARED",
        "CANCELLED",
        action.getVersion(),
        LocalDateTime.now()
      ) != 1
    ) throw new ConflictException("操作状态已变化");
    return require(id, memberId);
  }
}
