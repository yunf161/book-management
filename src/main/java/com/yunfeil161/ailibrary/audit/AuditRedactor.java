package com.yunfeil161.ailibrary.audit;

import com.yunfeil161.ailibrary.common.util.JsonUtils;
import java.util.*;
import org.springframework.stereotype.Component;

/** 只允许结构化业务字段进入审计，不保存密码摘要、凭证、请求原文和自由文本正文。 */
@Component
public class AuditRedactor {

  private static final Set<String> SAFE = Set.of(
    "id",
    "member_id",
    "staff_member_id",
    "conversation_id",
    "role_id",
    "member_type",
    "name",
    "phone",
    "email",
    "code",
    "module",
    "enabled",
    "status",
    "version",
    "account_status",
    "qualification_status",
    "department_id",
    "employee_no",
    "position_name",
    "employment_status",
    "hired_on",
    "left_on",
    "borrowed_book_count",
    "permanent_borrow_limit",
    "temporary_borrow_limit",
    "temporary_quota_expires_at",
    "accepted_rule_version",
    "email_verified_at",
    "category_id",
    "parent_id",
    "title",
    "author",
    "isbn",
    "book_id",
    "copy_id",
    "application_id",
    "barcode",
    "location",
    "call_number",
    "price",
    "borrowed_at",
    "due_at",
    "returned_at",
    "overdue_days",
    "return_outcome",
    "renew_count",
    "application_type",
    "applicant_member_id",
    "target_member_id",
    "reviewer_member_id",
    "decision",
    "requested_days",
    "approved_days",
    "requested_max_borrow_count",
    "quota_duration_days",
    "requested_department_id",
    "requested_role_id",
    "requested_employment_action",
    "requested_account_action",
    "type",
    "policy_version",
    "plan_id",
    "sort_order",
    "progress",
    "source",
    "target_books",
    "created_at",
    "updated_at",
    "sequence_no",
    "priority",
    "progress_percent",
    "planned_start_date",
    "planned_finish_date",
    "started_at",
    "finished_at",
    "personalization_enabled",
    "recommendation_notifications",
    "tag_id",
    "interest_name",
    "policy_id",
    "sequence_value",
    "requested_position_name"
  );

  public Map<String, Object> row(Map<String, Object> input) {
    Map<String, Object> result = new LinkedHashMap<>();
    input.forEach((rawKey, rawValue) -> {
      String key = rawKey.toLowerCase(Locale.ROOT);
      if (key.equals("parameters_json") && rawValue != null) {
        try {
          String json =
            rawValue instanceof java.sql.Clob clob
              ? clob.getSubString(1, (int) Math.min(clob.length(), 16384))
              : String.valueOf(rawValue);
          result.put(
            "policy_rules",
            rules(JsonUtils.fromJson(json, Map.class))
          );
        } catch (Exception invalid) {
          result.put("policy_rules", "[不可解析，已省略]");
        }
        return;
      }
      if (!SAFE.contains(key)) return;
      Object value = rawValue;
      if (value != null) {
        String text = String.valueOf(value);
        if (key.equals("phone")) value =
          text.length() > 4
            ? text.substring(0, 3) + "****" + text.substring(text.length() - 4)
            : "***";
        else if (key.equals("email")) value = text.contains("@")
          ? text.substring(0, 1) + "***" + text.substring(text.indexOf('@'))
          : "***";
        else if (key.equals("name")) value = text.isEmpty()
          ? ""
          : text.substring(0, 1) + "***";
        else if (
          !(value instanceof Number) && !(value instanceof Boolean)
        ) value = text
          .replaceAll(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
            "[邮箱已隐藏]"
          )
          .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[手机号已隐藏]");
      }
      result.put(key, value);
    });
    return result;
  }

  public String json(Object value) {
    return value == null ? null : JsonUtils.toJson(value);
  }

  /** 旧表中的历史JSON也不能绕过新白名单。异常或未知结构只展示省略提示。 */
  public String stored(String value) {
    if (value == null) return null;
    try {
      return json(clean(JsonUtils.fromJson(value, Object.class), 0));
    } catch (RuntimeException invalid) {
      return "{\"notice\":\"历史快照不可解析，已省略\"}";
    }
  }

  private Object clean(Object value, int depth) {
    if (depth > 8) return "[已省略]";
    if (value instanceof Map<?, ?> source) {
      Map<String, Object> result = new LinkedHashMap<>();
      source.forEach((key, child) -> {
        String name = String.valueOf(key).toLowerCase(Locale.ROOT);
        if (
          SAFE.contains(name) &&
          !(child instanceof Map) &&
          !(child instanceof List)
        ) {
          result.putAll(row(Collections.singletonMap(name, child)));
        } else if (
          name.equals("policy_rules") && child instanceof Map<?, ?> ruleMap
        ) result.put(name, rules(ruleMap));
        else if (
          Set.of(
            "member",
            "employee_profile",
            "reader_settings",
            "reader_interest",
            "ai_librarian_profile",
            "department",
            "permission",
            "book_category",
            "book",
            "book_tag",
            "book_copy",
            "borrow_application",
            "borrow_record",
            "member_change_application",
            "reader_change_application",
            "employee_change_application",
            "reading_plan",
            "reading_plan_item",
            "policy_version",
            "policy_current",
            "ai_runtime_control",
            "ai_handoff"
          ).contains(name)
        ) {
          result.put(name, clean(child, depth + 1));
        } else if (
          name.endsWith("_truncated") && child instanceof Boolean
        ) result.put(name, child);
      });
      return result;
    }
    if (value instanceof List<?> list) return list
      .stream()
      .limit(100)
      .map(child -> clean(child, depth + 1))
      .toList();
    return "[已省略]";
  }

  /** 政策参数只保存已定义的数字、布尔及必填字段枚举，不收纳任意嵌套JSON。 */
  private Map<String, Object> rules(Map<?, ?> input) {
    Map<String, Object> result = new LinkedHashMap<>();
    input.forEach((key, value) -> {
      String name = String.valueOf(key);
      if (
        Set.of(
          "requireVerifiedEmail",
          "defaultMaxBorrowCount",
          "defaultBorrowDays",
          "maxBorrowDays",
          "maxRenewCount",
          "defaultRenewDays",
          "maxRenewDays",
          "blockBorrowWhenOverdue",
          "applicationValidityDays"
        ).contains(name) &&
        (value instanceof Number || value instanceof Boolean)
      ) result.put(name, value);
      if (
        name.equals("requiredFields") && value instanceof List<?> list
      ) result.put(
        name,
        list
          .stream()
          .map(String::valueOf)
          .filter(v -> Set.of("NAME", "PHONE").contains(v))
          .toList()
      );
    });
    return result;
  }
}
