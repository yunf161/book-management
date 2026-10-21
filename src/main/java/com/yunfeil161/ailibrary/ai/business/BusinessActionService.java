package com.yunfeil161.ailibrary.ai.business;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.config.security.permission.*;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 模型只提出草稿，确认端点才执行一次业务调用。先持久化EXECUTING，再调用原业务接口。
 * 网络中断和5xx均记为UNKNOWN，不能以“失败重试”为由重复借阅、审批或新增资料。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessActionService {

  public record Preview(String skill, Map<String, Object> arguments) {}

  public record Confirmation(String requestHash) {}

  public record Execution(Map<String, Object> action, String refreshedToken) {}

  private final PermissionAccess access;
  private final PermissionQueryService permissions;
  private final BusinessSkillRegistry registry;
  private final BusinessActionStore store;
  private final BusinessActionMapper mapper;
  private final BusinessHttpClient http;
  private final com.yunfeil161.ailibrary.ai.runtime.AiRuntimeControl controls;

  public LoginSession authorize() {
    var session = access.require(
      PermissionCodes.AI_BUSINESS_SELF,
      DataScope.SELF
    );
    if (
      session.getMemberType() == MemberType.AI_LIBRARIAN
    ) throw new ForbiddenException("AI馆员不使用人类业务操作入口");
    return session;
  }

  /** 只向规划模型开放当前角色确实具有权限的Skill，确认时还会重新鉴权。 */
  public List<Map<String, Object>> skills() {
    var session = authorize();
    List<Map<String, Object>> result = new ArrayList<>();
    for (var skill : registry.all()) {
      var scope = permissions.findDataScope(
        session.getRoleId(),
        skill.permission()
      );
      boolean allowed =
        scope.isPresent() &&
        (skill.scope() == DataScope.NONE ||
          scope.get() == DataScope.ALL ||
          scope.get() == skill.scope());
      if (allowed) result.add(
        Map.of(
          "code",
          skill.code(),
          "title",
          skill.title(),
          "tool",
          skill.tool()
        )
      );
    }
    return result;
  }

  @DistributedLock(
    keys = "'lock:resource:ai-action:member:' + #currentMemberId"
  )
  public Map<String, Object> preview(Preview request) {
    controls.requireEnabled("BUSINESS");
    var session = authorize();
    if (request == null) throw new WrongFormatException("缺少操作草稿");
    var skill = registry.require(request.skill());
    access.require(skill.permission(), skill.scope());
    var arguments = registry.validate(skill, request.arguments());
    var now = LocalDateTime.now();
    var action = new BusinessAction();
    action.setActionId(UUID.randomUUID().toString());
    action.setMemberId(session.getMemberId());
    action.setSkillCode(skill.code());
    action.setTitle(skill.title());
    action.setRequestJson(JsonUtils.toJson(arguments));
    action.setRequestHash(
      hash(
        session.getMemberId() +
          "\n" +
          skill.code() +
          "\n" +
          action.getRequestJson()
      )
    );
    action.setExpiresAt(now.plusMinutes(10));
    action.setCreatedAt(now);
    action.setUpdatedAt(now);
    return view(store.prepare(action));
  }

  @DistributedLock(keys = "'lock:resource:ai-action:' + #actionId")
  public Execution confirm(
    String actionId,
    Confirmation request,
    String token
  ) {
    controls.requireEnabled("BUSINESS");
    var session = authorize();
    validateId(actionId);
    if (
      request == null ||
      request.requestHash() == null ||
      !request.requestHash().matches("[a-f0-9]{64}")
    ) throw new WrongFormatException("缺少有效的操作内容校验值");
    if (
      token == null ||
      token.isBlank() ||
      token.contains("\r") ||
      token.contains("\n")
    ) throw new UnauthorizedException("缺少业务令牌");
    var original = store.require(actionId, session.getMemberId());
    var skill = registry.require(original.getSkillCode());
    access.require(skill.permission(), skill.scope());
    var arguments = registry.validate(
      skill,
      JsonUtils.fromJson(original.getRequestJson(), Map.class)
    );
    var claim = store.claim(
      actionId,
      session.getMemberId(),
      request.requestHash()
    );
    if (!claim.acquired()) return new Execution(view(claim.action()), null);
    // 此处没有数据库事务；EXECUTING已提交，服务崩溃后也不会自动再次执行。
    BusinessHttpClient.Outcome outcome;
    com.yunfeil161.ailibrary.ai.runtime.AiActionCorrelation.set(actionId);
    try {
      outcome = http.execute(
        skill,
        registry.path(skill, arguments),
        registry.body(skill, arguments),
        token
      );
    } finally {
      com.yunfeil161.ailibrary.ai.runtime.AiActionCorrelation.clear();
    }
    // 审计日志只保留操作编号和状态，不记录JWT、用户输入或业务响应正文。
    log.info(
      "AI业务操作完成：actionId={}, skill={}, known={}, code={}",
      actionId,
      skill.code(),
      outcome.known(),
      outcome.code()
    );
    String state = !outcome.known()
      ? "UNKNOWN"
      : outcome.code() == 200
        ? "SUCCEEDED"
        : "FAILED";
    try {
      return new Execution(
        view(
          store.finish(
            actionId,
            session.getMemberId(),
            state,
            outcome.code(),
            JsonUtils.toJson(outcome.response())
          )
        ),
        outcome.refreshedToken()
      );
    } catch (RuntimeException exception) {
      log.error("AI业务操作结果保存失败：actionId={}", actionId, exception);
      throw new ServiceUnavailableException(
        "业务可能已经执行，但结果记录失败；请先核实业务状态，不要重新提交"
      );
    }
  }

  @DistributedLock(keys = "'lock:resource:ai-action:' + #actionId")
  public Map<String, Object> cancel(String actionId) {
    var session = authorize();
    validateId(actionId);
    return view(store.cancel(actionId, session.getMemberId()));
  }

  /** 单个ID和组合条件保留在同一个本人分页查询入口，不提供任意成员查询参数。 */
  public PageResult<Map<String, Object>> query(
    String actionId,
    String state,
    PageQuery page
  ) {
    var session = authorize();
    if (actionId != null) validateId(actionId);
    if (
      state != null &&
      !Set.of(
        "PREPARED",
        "EXECUTING",
        "SUCCEEDED",
        "FAILED",
        "UNKNOWN",
        "CANCELLED",
        "EXPIRED"
      ).contains(state)
    ) throw new WrongFormatException("操作状态错误");
    int number =
      page == null || page.getPage() == null || page.getPage() < 1
        ? 1
        : page.getPage();
    int size =
      page == null || page.getPageSize() == null || page.getPageSize() < 1
        ? 10
        : Math.min(50, page.getPageSize());
    PageHelper.startPage(number, size);
    var rows = mapper.query(session.getMemberId(), actionId, state);
    long total = new PageInfo<>(rows).getTotal();
    return new PageResult<>(total, rows.stream().map(this::view).toList());
  }

  public Map<String, Object> view(BusinessAction action) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("actionId", action.getActionId());
    result.put("skill", action.getSkillCode());
    result.put("title", action.getTitle());
    result.put(
      "arguments",
      JsonUtils.fromJson(action.getRequestJson(), Map.class)
    );
    result.put("requestHash", action.getRequestHash());
    result.put(
      "state",
      "PREPARED".equals(action.getState()) &&
        !action.getExpiresAt().isAfter(LocalDateTime.now())
        ? "EXPIRED"
        : action.getState()
    );
    result.put("expiresAt", action.getExpiresAt());
    result.put("createdAt", action.getCreatedAt());
    result.put("resultCode", action.getResultCode());
    result.put(
      "result",
      action.getResultJson() == null
        ? null
        : JsonUtils.fromJson(action.getResultJson(), Map.class)
    );
    result.put(
      "notice",
      "UNKNOWN".equals(action.getState()) ||
        "EXECUTING".equals(action.getState())
        ? "结果待核实，请查询实际业务状态，系统不会自动重复执行"
        : "PREPARED".equals(action.getState())
          ? "仅为操作草稿，确认前不会执行"
          : "以业务接口的实际结果为准"
    );
    return result;
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void validateId(String id) {
    try {
      if (
        !UUID.fromString(id).toString().equals(id)
      ) throw new IllegalArgumentException();
    } catch (RuntimeException exception) {
      throw new WrongFormatException("操作ID格式错误");
    }
  }
}
