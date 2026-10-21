package com.yunfeil161.ailibrary.ai.runtime;

import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 直接读取权威开关，不缓存；模型规划、提交及确认执行分别复核。 */
@Service
@RequiredArgsConstructor
public class AiRuntimeControl {

  private final JdbcTemplate jdbc;
  private final PermissionAccess access;
  private final com.yunfeil161.ailibrary.audit.OperationLogWriter audit;

  public boolean enabled(String code) {
    return (
      jdbc.queryForObject(
        "select count(*) from ai_runtime_control where code in ('GLOBAL',?) and enabled=true",
        Integer.class,
        code
      ) == 2
    );
  }

  public void requireEnabled(String code) {
    if (!enabled(code)) throw new ServiceUnavailableException(
      "AI能力已暂停，请使用普通业务页面或联系馆员"
    );
  }

  public List<Map<String, Object>> query() {
    access.require("ai-control:manage", DataScope.ALL);
    return jdbc.queryForList(
      "select code,enabled,version,updated_at from ai_runtime_control order by code"
    );
  }

  @Transactional
  public Map<String, Object> update(
    String code,
    Boolean enabled,
    Long expectedVersion
  ) {
    access.require("ai-control:manage", DataScope.ALL);
    if (
      !Set.of("GLOBAL", "CONSULTATION", "BUSINESS", "RECOMMENDATION").contains(
        code
      ) ||
      enabled == null ||
      expectedVersion == null ||
      expectedVersion < 0
    ) throw new WrongFormatException("能力编码、状态和版本必须有效");
    var before = jdbc.queryForMap(
      "select code,enabled,version from ai_runtime_control where code=?",
      code
    );
    if (
      jdbc.update(
        "update ai_runtime_control set enabled=?,version=version+1,updated_at=current_timestamp(3) where code=? and version=?",
        enabled,
        code,
        expectedVersion
      ) != 1
    ) throw new ConflictException("能力配置已变化，请刷新");
    var after = jdbc.queryForMap(
      "select code,enabled,version,updated_at from ai_runtime_control where code=?",
      code
    );
    var entry = audit.entry("AI_CONTROL", null, "updateAiControl");
    entry.setBeforeData(
      com.yunfeil161.ailibrary.common.util.JsonUtils.toJson(
        Map.of("ai_runtime_control", List.of(before))
      )
    );
    entry.setAfterData(
      com.yunfeil161.ailibrary.common.util.JsonUtils.toJson(
        Map.of(
          "ai_runtime_control",
          List.of(
            Map.of(
              "code",
              code,
              "enabled",
              enabled,
              "version",
              expectedVersion + 1
            )
          )
        )
      )
    );
    audit.success(entry);
    return after;
  }
}
