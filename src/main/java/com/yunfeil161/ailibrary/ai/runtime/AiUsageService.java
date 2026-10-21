package com.yunfeil161.ailibrary.ai.runtime;

import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模型报告的已知用量；未回传用量/未配置单价时不伪造零费用或云账单。 */
@Service
@RequiredArgsConstructor
public class AiUsageService {

  private final JdbcTemplate jdbc;
  private final PermissionAccess access;

  @Value("${library.ai.input-price-per-million:-1}")
  private BigDecimal inputPrice;

  @Value("${library.ai.output-price-per-million:-1}")
  private BigDecimal outputPrice;

  @Transactional
  public void record(String eventId, Long member, List<?> calls) {
    if (calls.size() > 16) throw new WrongFormatException(
      "模型计量条数超过限制"
    );
    for (int index = 0; index < calls.size(); index++) {
      if (
        !(calls.get(index) instanceof Map<?, ?> row) ||
        !(row.get("provider") instanceof String provider) ||
        !Set.of("aliyun", "ollama").contains(provider) ||
        !(row.get("model") instanceof String model) ||
        model.length() > 150 ||
        !(row.get("promptTokens") instanceof Number p) ||
        !(row.get("completionTokens") instanceof Number c)
      ) continue;
      long prompt = p.longValue(),
        completion = c.longValue();
      if (
        prompt < 0 ||
        completion < 0 ||
        prompt > 100000000 ||
        completion > 100000000
      ) continue;
      BigDecimal cost = "ollama".equals(provider)
        ? BigDecimal.ZERO
        : inputPrice.signum() >= 0 && outputPrice.signum() >= 0
          ? inputPrice
              .multiply(BigDecimal.valueOf(prompt))
              .add(outputPrice.multiply(BigDecimal.valueOf(completion)))
              .divide(BigDecimal.valueOf(1000000), 8, RoundingMode.HALF_UP)
          : null;
      // 数据库唯一键兜底；同一执行事件重放不重复计数。
      jdbc.update(
        "insert into ai_model_usage(event_id,call_index,member_id,provider,model,prompt_tokens,completion_tokens,estimated_cost,created_at) select ?,?,?,?,?,?,?,?,? where not exists(select 1 from ai_model_usage where event_id=? and call_index=?)",
        eventId,
        index,
        member,
        provider,
        model,
        prompt,
        completion,
        cost,
        LocalDateTime.now(),
        eventId,
        index
      );
    }
  }

  public Map<String, Object> query(LocalDate from, LocalDate to, Long member) {
    var session = access.require("ai-task:query", DataScope.SELF);
    if (access.requireScope("ai-task:query") != DataScope.ALL) {
      if (
        member != null && !member.equals(session.getMemberId())
      ) throw new ForbiddenException("只能查看本人用量");
      member = session.getMemberId();
    }
    if (to == null) to = LocalDate.now();
    if (from == null) from = to.minusDays(29);
    if (
      from.isAfter(to) ||
      java.time.temporal.ChronoUnit.DAYS.between(from, to) > 365
    ) throw new WrongFormatException("日期范围不合法");
    String where =
      " where created_at>=? and created_at<?" +
      (member == null ? "" : " and member_id=?");
    var args = new ArrayList<Object>();
    args.add(from.atStartOfDay());
    args.add(to.plusDays(1).atStartOfDay());
    if (member != null) args.add(member);
    var rows = jdbc.queryForList(
      "select provider,model,count(*) as calls,sum(prompt_tokens) as promptTokens,sum(completion_tokens) as completionTokens,sum(estimated_cost) as knownEstimatedCost,sum(case when estimated_cost is null then 1 else 0 end) as unknownCostCalls from ai_model_usage" +
        where +
        " group by provider,model",
      args.toArray()
    );
    return Map.of(
      "from",
      from.toString(),
      "to",
      to.toString(),
      "rows",
      rows,
      "note",
      "仅统计模型已返回的用量；中断或服务未报告的调用不含在内。费用为配置单价估算，本地费用不含硬件电费。"
    );
  }
}
