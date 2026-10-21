package com.yunfeil161.ailibrary.ai.runtime;

import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 只依据同事务审计凭据核实结果，永不自动重放UNKNOWN写操作。 */
@Service
@RequiredArgsConstructor
public class AiActionReconciler {

  private final JdbcTemplate jdbc;
  private final PermissionAccess access;

  public Map<String, Object> manual() {
    access.require("ai-task:manage", DataScope.ALL);
    return reconcile();
  }

  @Scheduled(
    fixedDelayString = "${library.ai.reconcile-millis:60000}",
    initialDelay = 60000
  )
  public void scheduled() {
    reconcile();
  }

  public Map<String, Object> reconcile() {
    jdbc.update(
      "update ai_business_action set state='UNKNOWN',version=version+1,updated_at=current_timestamp(3) where state='EXECUTING' and updated_at<?",
      LocalDateTime.now().minusMinutes(2)
    );
    int resolved = 0;
    for (var action : jdbc.queryForList(
      "select a.action_id,a.member_id,a.version from ai_business_action a where a.state='UNKNOWN' and exists(select 1 from member_operation_log l where l.event_id=a.action_id and l.actor_member_id=a.member_id and l.result_status in ('SUCCESS','FAILURE')) order by a.id limit 100"
    )) {
      var receipts = jdbc.queryForList(
        "select target_type,target_id,result_status from member_operation_log where event_id=? and actor_member_id=? order by case when result_status='SUCCESS' then 0 else 1 end,id desc limit 1",
        action.get("action_id"),
        action.get("member_id")
      );
      if (receipts.isEmpty()) continue;
      var receipt = receipts.get(0);
      boolean success = "SUCCESS".equals(receipt.get("result_status"));
      String result = JsonUtils.toJson(
        Map.of(
          "code",
          success ? 200 : 409,
          "msg",
          success
            ? "已依据事务审计核实执行成功"
            : "已依据审计核实业务拒绝，未自动重试",
          "data",
          receipt
        )
      );
      resolved += jdbc.update(
        "update ai_business_action set state=?,result_code=?,result_json=?,version=version+1,updated_at=current_timestamp(3) where action_id=? and state='UNKNOWN' and version=?",
        success ? "SUCCEEDED" : "FAILED",
        success ? 200 : 409,
        result,
        action.get("action_id"),
        action.get("version")
      );
    }
    return Map.of(
      "resolved",
      resolved,
      "remainingUnknown",
      jdbc.queryForObject(
        "select count(*) from ai_business_action where state='UNKNOWN'",
        Long.class
      )
    );
  }
}
