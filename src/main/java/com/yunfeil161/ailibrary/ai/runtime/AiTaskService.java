package com.yunfeil161.ailibrary.ai.runtime;

import com.github.pagehelper.*;
import com.yunfeil161.ailibrary.ai.dto.AiChatRequest;
import com.yunfeil161.ailibrary.ai.service.*;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 有界后台任务：令牌只保留在执行闭包，重启后绝不自动重放业务写入。 */
@Service
@RequiredArgsConstructor
public class AiTaskService {

  public record Request(String type, String question) {}

  private final AiTaskMapper mapper;
  private final AiChatService chats;
  private final PermissionAccess access;
  private final JdbcTemplate jdbc;
  private final AiRuntimeControl controls;
  private final com.yunfeil161.ailibrary.auth.service.LoginSessionService sessions;
  private final AiUsageService usageRecorder;

  @org.springframework.beans.factory.annotation.Value(
    "${library.ai.input-price-per-million:-1}"
  )
  private java.math.BigDecimal inputPrice;

  @org.springframework.beans.factory.annotation.Value(
    "${library.ai.output-price-per-million:-1}"
  )
  private java.math.BigDecimal outputPrice;

  private final ExecutorService executor = new ThreadPoolExecutor(
    2,
    2,
    0,
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(16),
    new ThreadPoolExecutor.AbortPolicy()
  );
  private final Map<String, AiGatewayClient.Stream> streams =
    new ConcurrentHashMap<>();

  @DistributedLock(keys = "'lock:resource:ai-task:member:' + #currentMemberId")
  public AiTask submit(Request request, String token) {
    return create(request, token, null);
  }

  private AiTask create(Request request, String token, AiTask parent) {
    var session = chats.authorize();
    access.require("ai-task:manage", DataScope.SELF);
    if (
      request == null ||
      !Set.of("CONSULTATION", "BUSINESS").contains(
        request.type() == null ? "" : request.type()
      ) ||
      request.question() == null ||
      request.question().isBlank() ||
      request.question().length() > 2000
    ) throw new WrongFormatException("任务类型或问题格式错误");
    if (token == null || token.isBlank()) throw new UnauthorizedException(
      "缺少业务令牌"
    );
    controls.requireEnabled(request.type());
    if (mapper.pending(session.getMemberId()) >= 5) throw new ConflictException(
      "未完成任务过多，请等待或取消"
    );
    var task = new AiTask();
    task.setTaskId(UUID.randomUUID().toString());
    task.setMemberId(session.getMemberId());
    task.setType(request.type());
    task.setQuestion(request.question());
    task.setAttempt(parent == null ? 0 : parent.getAttempt() + 1);
    task.setParentTaskId(parent == null ? null : parent.getTaskId());
    mapper.insert(task);
    // 提交后先返回任务编号。执行时重新读取当前权限和能力开关，不能沿用提交时的授权结论。
    try {
      executor.execute(() -> run(task.getTaskId(), session, token));
    } catch (RejectedExecutionException busy) {
      mapper.fail(task.getTaskId(), "QUEUE_FULL");
      throw new ServiceUnavailableException("AI任务队列繁忙，请稍后重试");
    }
    return mapper.find(task.getTaskId());
  }

  public PageResult<AiTask> query(AiTaskQuery q) {
    var session = access.require("ai-task:query", DataScope.SELF);
    DataScope scope = access.requireScope("ai-task:query");
    Long owner = scope == DataScope.ALL ? null : session.getMemberId();
    if (
      owner != null && q.getMemberId() != null && !owner.equals(q.getMemberId())
    ) throw new ForbiddenException("只能查询本人任务");
    PageHelper.startPage(
      q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage(),
      q.getPageSize() == null || q.getPageSize() < 1
        ? 10
        : Math.min(q.getPageSize(), 100)
    );
    var page = new PageInfo<>(mapper.query(owner, q));
    // 管理列表只显示任务元数据；他人的提问、回答、来源不随全局查询泄露。
    page.getList().forEach(row -> {
      if (!row.getMemberId().equals(session.getMemberId())) {
        row.setQuestion(null);
        row.setResultJson(null);
      }
    });
    return new PageResult<>(page.getTotal(), page.getList());
  }

  public AiTask cancel(String id) {
    var task = visible(id, "ai-task:manage");
    for (String previous : List.of("QUEUED", "RUNNING"))
      mapper.transition(id, previous, "CANCELLED");
    var stream = streams.remove(id);
    if (stream != null) try {
      stream.close();
    } catch (IOException ignored) {}
    var result = mapper.find(id);
    if (!result.getMemberId().equals(LoginUserContext.get().getMemberId())) {
      result.setQuestion(null);
      result.setResultJson(null);
    }
    return result;
  }

  @DistributedLock(keys = "'lock:resource:ai-task:member:' + #currentMemberId")
  public AiTask retry(String id, String token) {
    var task = visible(id, "ai-task:manage");
    if (
      !task.getMemberId().equals(LoginUserContext.get().getMemberId())
    ) throw new ForbiddenException("不能使用管理员身份重试他人的任务");
    // 网络重试复用同一个后继任务；不能对同一失败父任务不断创建并行副本。
    var existing = mapper.retryOf(id);
    if (existing != null) return existing;
    if (
      !Set.of("FAILED", "CANCELLED").contains(task.getState()) ||
      task.getAttempt() >= 3
    ) throw new ConflictException("只能重试失败或取消的任务，最多重试3次");
    return create(new Request(task.getType(), task.getQuestion()), token, task);
  }

  private AiTask visible(String id, String permission) {
    var session = access.require(permission, DataScope.SELF);
    var task = mapper.find(id);
    if (task == null) throw new NotFoundException("AI任务不存在");
    if (
      !task.getMemberId().equals(session.getMemberId()) &&
      access.requireScope(permission) != DataScope.ALL
    ) throw new ForbiddenException("没有此任务的数据权限");
    return task;
  }

  void run(String id, LoginSession session, String token) {
    if (mapper.transition(id, "QUEUED", "RUNNING") != 1) return;
    LoginUserContext.set(session);
    try {
      requireSession(session);
      var task = mapper.find(id);
      try (
        var stream = chats.chat(
          new AiChatRequest(task.getQuestion(), null, task.getType()),
          token
        )
      ) {
        streams.put(id, stream);
        var reader = new InputStreamReader(
          stream.body(),
          StandardCharsets.UTF_8
        );
        String event = null;
        StringBuilder answer = new StringBuilder();
        List<Object> sources = new ArrayList<>();
        List<Object> usage = new ArrayList<>();
        Map<String, Object> proposal = null;
        boolean done = false;
        int total = 0;
        while (true) {
          if (!"RUNNING".equals(mapper.find(id).getState())) return;
          String line = line(reader);
          if (line == null) break;
          total += line.length();
          if (total > 150000) throw new IOException("任务响应过长");
          if (line.startsWith("event: ")) event = line.substring(7);
          else if (line.startsWith("data: ")) {
            Map<String, Object> value = JsonUtils.fromJson(
              line.substring(6),
              Map.class
            );
            switch (event == null ? "" : event) {
              case "delta" -> answer.append(value.getOrDefault("text", ""));
              case "sources" -> {
                if (
                  value.get("sources") instanceof List<?> list
                ) sources.addAll(list);
              }
              case "usage" -> {
                if (value.get("calls") instanceof List<?> list) usage.addAll(
                  list
                );
              }
              case "proposal" -> proposal = value;
              case "error" -> throw new IOException("模型任务失败");
              case "done" -> done = true;
              case "meta" -> {
              }
              default -> throw new IOException("模型事件错误");
            }
            event = null;
            if (done) break;
          }
        }
        if (!done) throw new IOException("任务未完整结束");
        Long prompt = null,
          completion = null;
        if (!usage.isEmpty()) {
          prompt = 0L;
          completion = 0L;
          for (Object item : usage)
            if (item instanceof Map<?, ?> row) {
              if (row.get("promptTokens") instanceof Number n) prompt +=
                n.longValue();
              if (row.get("completionTokens") instanceof Number n) completion +=
                n.longValue();
            }
        }
        var output = new LinkedHashMap<String, Object>();
        output.put("answer", answer.toString());
        output.put("sources", sources);
        output.put("usage", usage);
        // 异步业务任务只产出提案，用户仍必须在现有草稿预览/确认入口显式确认。
        if (proposal != null) output.put("proposal", proposal);
        java.math.BigDecimal cost = null;
        if (prompt != null && completion != null) {
          boolean cloud = usage
            .stream()
            .anyMatch(
              item ->
                item instanceof Map<?, ?> row &&
                "aliyun".equals(row.get("provider"))
            );
          if (!cloud) cost = java.math.BigDecimal.ZERO;
          else if (inputPrice.signum() >= 0 && outputPrice.signum() >= 0) {
            cost = java.math.BigDecimal.ZERO;
            for (Object item : usage)
              if (
                item instanceof Map<?, ?> row &&
                "aliyun".equals(row.get("provider")) &&
                row.get("promptTokens") instanceof Number p &&
                row.get("completionTokens") instanceof Number c
              ) cost = cost.add(
                inputPrice
                  .multiply(java.math.BigDecimal.valueOf(p.longValue()))
                  .add(
                    outputPrice.multiply(
                      java.math.BigDecimal.valueOf(c.longValue())
                    )
                  )
                  .divide(
                    java.math.BigDecimal.valueOf(1000000),
                    8,
                    java.math.RoundingMode.HALF_UP
                  )
              );
          }
        }
        usageRecorder.record(id, session.getMemberId(), usage);
        requireSession(session);
        mapper.success(id, JsonUtils.toJson(output), prompt, completion, cost);
      }
    } catch (Exception failure) {
      mapper.fail(id, failure.getClass().getSimpleName());
    } finally {
      streams.remove(id);
      LoginUserContext.clear();
    }
  }

  /** 排队期间退出、角色变更或到期后，不能继续使用捕获的旧身份。 */
  private void requireSession(LoginSession expected) {
    var actual = sessions.find(expected.getJti());
    if (
      actual == null ||
      !Objects.equals(actual.getMemberId(), expected.getMemberId()) ||
      !Objects.equals(actual.getRoleId(), expected.getRoleId())
    ) throw new UnauthorizedException("任务登录会话已失效，请重新登录后重试");
  }

  private String line(Reader reader) throws IOException {
    StringBuilder value = new StringBuilder();
    int character;
    while ((character = reader.read()) != -1) {
      if (character == '\n') return value.toString();
      if (character != '\r') value.append((char) character);
      if (value.length() > 100000) throw new IOException("响应单行过长");
    }
    return value.isEmpty() ? null : value.toString();
  }

  /** 崩溃遗留任务只有过期失败，不会携带旧JWT重新执行；重试需要用户发起并重新授权。 */
  @Scheduled(
    fixedDelayString = "${library.ai.task-recovery-millis:60000}",
    initialDelay = 60000
  )
  public void recover() {
    jdbc.update(
      // 时间戳由数据库产生，租约也使用数据库时钟；不能与JVM本地时区直接比较。
      "update ai_task set state='FAILED',error_code='WORKER_INTERRUPTED',version=version+1,updated_at=current_timestamp(3) where state in ('QUEUED','RUNNING') and updated_at<timestampadd(MINUTE,-30,current_timestamp(3))"
    );
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
    streams.values().forEach(stream -> {
      try {
        stream.close();
      } catch (IOException ignored) {}
    });
  }
}
