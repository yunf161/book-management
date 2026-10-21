package com.yunfeil161.ailibrary.ai.business;

import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Java掌握唯一的可执行Skill白名单。模型只能提供函数名与参数，不能提供URL、HTTP方法或身份。
 * Schema同时用于模型规划、前端展示和Java二次校验，实际业务条件仍由原接口检查。
 */
@Component
public class BusinessSkillRegistry {

  public record Skill(
    String code,
    String title,
    String permission,
    DataScope scope,
    String method,
    String path,
    String targetField,
    Map<String, Object> properties,
    List<String> required
  ) {
    public Map<String, Object> tool() {
      return Map.of(
        "type",
        "function",
        "function",
        Map.of(
          "name",
          code,
          "description",
          title +
            "。仅生成待确认操作，不代表已执行；内部版本只能来自本轮真实查询，不得要求用户填写。",
          "parameters",
          Map.of(
            "type",
            "object",
            "properties",
            properties,
            "required",
            required,
            "additionalProperties",
            false
          )
        )
      );
    }
  }

  private final Map<String, Skill> skills = new LinkedHashMap<>();

  public BusinessSkillRegistry() {
    add(
      "borrow_apply",
      "提交本人借阅申请",
      PermissionCodes.BORROW_APPLICATION_CREATE,
      DataScope.SELF,
      "POST",
      "/borrow-applications",
      null,
      fields(
        "bookId",
        id(),
        "requestedDays",
        integer(1, 3650),
        "reason",
        text(500)
      ),
      "bookId",
      "requestedDays"
    );
    add(
      "renew_apply",
      "提交本人续借申请",
      PermissionCodes.BORROW_APPLICATION_CREATE,
      DataScope.SELF,
      "POST",
      "/borrow-applications",
      null,
      fields(
        "borrowRecordId",
        id(),
        "requestedDays",
        integer(1, 3650),
        "reason",
        text(500)
      ),
      "borrowRecordId",
      "requestedDays"
    );
    add(
      "borrow_cancel",
      "取消本人待审批借阅申请",
      PermissionCodes.BORROW_APPLICATION_CANCEL,
      DataScope.SELF,
      "POST",
      "/borrow-applications/{id}/cancel",
      "applicationId",
      fields("applicationId", id(), "expectedVersion", version()),
      "applicationId",
      "expectedVersion"
    );
    add(
      "plan_create",
      "新建本人阅读计划",
      PermissionCodes.READING_PLAN_CREATE_SELF,
      DataScope.SELF,
      "POST",
      "/reading-plans",
      null,
      fields(
        "name",
        text(80),
        "description",
        text(1000),
        "targetBooks",
        integer(1, 10000),
        "startDate",
        date(),
        "endDate",
        date(),
        "status",
        enumeration("DRAFT", "ACTIVE")
      ),
      "name",
      "targetBooks"
    );
    add(
      "plan_update",
      "修改本人阅读计划",
      PermissionCodes.READING_PLAN_UPDATE_SELF,
      DataScope.SELF,
      "PUT",
      "/reading-plans",
      null,
      fields(
        "name",
        text(80),
        "description",
        text(1000),
        "targetBooks",
        integer(1, 10000),
        "startDate",
        date(),
        "endDate",
        date(),
        "status",
        enumeration("DRAFT", "ACTIVE", "PAUSED", "COMPLETED"),
        "version",
        version()
      ),
      "name",
      "targetBooks",
      "status",
      "version"
    );
    add(
      "plan_add_item",
      "向本人阅读计划添加图书",
      // 与原ReadingPlanService.addMine的create权限保持一致。
      PermissionCodes.READING_PLAN_CREATE_SELF,
      DataScope.SELF,
      "POST",
      "/reading-plans/items",
      null,
      fields(
        "bookId",
        id(),
        "expectedPlanVersion",
        version(),
        "sequenceNo",
        integer(1, 10000),
        "priority",
        integer(1, 5),
        "plannedStartDate",
        date(),
        "plannedFinishDate",
        date(),
        "note",
        text(1000)
      ),
      "bookId",
      "expectedPlanVersion"
    );
    add(
      "member_change_apply",
      "提交本人资料或资格变更申请",
      PermissionCodes.MEMBER_CHANGE_CREATE_SELF,
      DataScope.SELF,
      "POST",
      "/member-change-applications",
      null,
      fields(
        "applicationType",
        enumeration(
          "READER_QUALIFICATION",
          "READER_QUALIFICATION_RESTORE",
          "BORROW_QUOTA_CHANGE",
          "ACCOUNT_REACTIVATION",
          "EMPLOYEE_PROFILE_CHANGE",
          "EMPLOYEE_TRANSFER",
          "EMPLOYEE_SUSPENSION",
          "EMPLOYEE_RESIGNATION",
          "EMPLOYEE_REINSTATEMENT",
          "EMPLOYEE_ROLE_CHANGE"
        ),
        "reason",
        text(500),
        "expectedMemberVersion",
        version(),
        "expectedProfileVersion",
        version(),
        "acceptedRuleVersion",
        text(64),
        "requestedMaxBorrowCount",
        integer(0, 10000),
        "quotaDurationDays",
        integer(1, 3650),
        "requestedDepartmentId",
        id(),
        "requestedPositionName",
        text(80),
        "requestedRoleId",
        id(),
        "requestedEmploymentAction",
        enumeration("TRANSFER", "SUSPEND", "RESIGN", "REINSTATE"),
        "requestedAccountAction",
        enumeration("REACTIVATE")
      ),
      "applicationType",
      "reason"
    );
    add(
      "borrow_approve",
      "馆员批准借阅或续借申请",
      PermissionCodes.BORROW_APPLICATION_APPROVE,
      DataScope.ALL,
      "POST",
      "/admin/borrow-applications/{id}/approve",
      "applicationId",
      fields(
        "applicationId",
        id(),
        "expectedVersion",
        version(),
        "copyId",
        id(),
        "approvedDays",
        integer(1, 3650),
        "decisionRemark",
        text(500)
      ),
      "applicationId",
      "expectedVersion",
      "approvedDays"
    );
    add(
      "borrow_reject",
      "馆员拒绝借阅或续借申请",
      PermissionCodes.BORROW_APPLICATION_REJECT,
      DataScope.ALL,
      "POST",
      "/admin/borrow-applications/{id}/reject",
      "applicationId",
      fields(
        "applicationId",
        id(),
        "expectedVersion",
        version(),
        "decisionRemark",
        text(500)
      ),
      "applicationId",
      "expectedVersion",
      "decisionRemark"
    );
    add(
      "return_record",
      "馆员办理图书归还",
      PermissionCodes.BORROW_RECORD_RETURN,
      DataScope.ALL,
      "POST",
      "/admin/borrow-records/{id}/return",
      "recordId",
      fields(
        "recordId",
        id(),
        "expectedVersion",
        version(),
        "returnConditionNote",
        text(500)
      ),
      "recordId",
      "expectedVersion"
    );
    add(
      "category_create",
      "馆员新增图书分类",
      PermissionCodes.BOOK_CATEGORY_CREATE,
      DataScope.NONE,
      "POST",
      "/admin/book-categories",
      null,
      fields(
        "code",
        text(32),
        "name",
        text(80),
        "parentId",
        id(),
        "description",
        text(500)
      ),
      "code",
      "name"
    );
    add(
      "book_create",
      "馆员新增图书书目",
      PermissionCodes.BOOK_CREATE,
      DataScope.NONE,
      "POST",
      "/admin/books",
      null,
      fields(
        "categoryId",
        id(),
        "title",
        text(200),
        "subtitle",
        text(200),
        "author",
        text(200),
        "isbn",
        text(20),
        "publisher",
        text(120),
        "publishedDate",
        date(),
        "edition",
        text(40),
        "language",
        text(32),
        "description",
        text(10000),
        "coverObjectKey",
        text(500)
      ),
      "categoryId",
      "title",
      "author"
    );
    add(
      "copy_create",
      "馆员新增图书副本",
      PermissionCodes.BOOK_COPY_CREATE,
      DataScope.NONE,
      "POST",
      "/admin/books/{id}/copies",
      "bookId",
      fields(
        "bookId",
        id(),
        "barcode",
        text(64),
        "callNumber",
        text(64),
        "location",
        text(120),
        "acquiredAt",
        date(),
        "price",
        Map.of("type", "number", "minimum", 0, "maximum", 99999999.99),
        "conditionNote",
        text(500)
      ),
      "bookId",
      "barcode",
      "location"
    );
  }

  public Collection<Skill> all() {
    return Collections.unmodifiableCollection(skills.values());
  }

  public Skill require(String code) {
    Skill skill = skills.get(code);
    if (skill == null) throw new WrongFormatException("未开放该业务Skill");
    return skill;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> validate(
    Skill skill,
    Map<String, Object> arguments
  ) {
    if (
      arguments == null ||
      arguments.size() > 30 ||
      !skill.properties().keySet().containsAll(arguments.keySet()) ||
      !arguments.keySet().containsAll(skill.required())
    ) throw new WrongFormatException("Skill参数缺失或包含未开放字段");
    var clean = new TreeMap<String, Object>();
    for (var entry : arguments.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      var rule = (Map<String, Object>) skill.properties().get(key);
      String type = (String) rule.get("type");
      boolean valid = switch (type) {
        case "integer" -> value instanceof Integer || value instanceof Long;
        case "number" -> value instanceof Number &&
          Double.isFinite(((Number) value).doubleValue());
        case "string" -> value instanceof String text &&
          !text.isBlank() &&
          text.length() <= ((Number) rule.get("maxLength")).intValue();
        default -> false;
      };
      if (!valid) throw new WrongFormatException("Skill参数类型错误：" + key);
      if (
        value instanceof Number n &&
        (n.doubleValue() <
          ((Number) rule.getOrDefault("minimum", 0)).doubleValue() ||
          n.doubleValue() >
            (
              (Number) rule.getOrDefault("maximum", Long.MAX_VALUE)
            ).doubleValue())
      ) {
        throw new WrongFormatException("Skill数值参数超出范围：" + key);
      }
      if (
        rule.containsKey("enum") &&
        !((List<?>) rule.get("enum")).contains(value)
      ) throw new WrongFormatException("Skill枚举值错误：" + key);
      if ("date".equals(rule.get("format"))) {
        try {
          LocalDate.parse((String) value);
        } catch (RuntimeException exception) {
          throw new WrongFormatException("Skill日期格式错误：" + key);
        }
      }
      clean.put(key, value instanceof String text ? text.strip() : value);
    }
    if (
      "plan_update".equals(skill.code()) && clean.size() == 1
    ) throw new WrongFormatException("至少提供一个计划修改字段");
    return clean;
  }

  /** 路径参数只从校验后的正整数取值，不将模型提供的字符串拼接到URL。 */
  public String path(Skill skill, Map<String, Object> arguments) {
    return skill.targetField() == null
      ? skill.path()
      : skill
          .path()
          .replace("{id}", arguments.get(skill.targetField()).toString());
  }

  public Map<String, Object> body(Skill skill, Map<String, Object> arguments) {
    var body = new TreeMap<>(arguments);
    if (skill.targetField() != null) body.remove(skill.targetField());
    if ("borrow_apply".equals(skill.code())) body.put(
      "applicationType",
      "BORROW"
    );
    if ("renew_apply".equals(skill.code())) body.put(
      "applicationType",
      "RENEW"
    );
    if ("plan_add_item".equals(skill.code())) body.put("source", "AI");
    return body;
  }

  private void add(
    String code,
    String title,
    String permission,
    DataScope scope,
    String method,
    String path,
    String target,
    Map<String, Object> properties,
    String... required
  ) {
    skills.put(
      code,
      new Skill(
        code,
        title,
        permission,
        scope,
        method,
        path,
        target,
        Collections.unmodifiableMap(properties),
        List.of(required)
      )
    );
  }

  private static Map<String, Object> fields(Object... pairs) {
    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) result.put(
      (String) pairs[i],
      pairs[i + 1]
    );
    return result;
  }

  private static Map<String, Object> id() {
    return integer(1, Long.MAX_VALUE);
  }

  private static Map<String, Object> version() {
    return integer(0, Long.MAX_VALUE);
  }

  private static Map<String, Object> integer(long min, long max) {
    return Map.of("type", "integer", "minimum", min, "maximum", max);
  }

  private static Map<String, Object> text(int max) {
    return Map.of("type", "string", "minLength", 1, "maxLength", max);
  }

  private static Map<String, Object> date() {
    return Map.of(
      "type",
      "string",
      "format",
      "date",
      "minLength",
      10,
      "maxLength",
      10
    );
  }

  private static Map<String, Object> enumeration(String... values) {
    return Map.of(
      "type",
      "string",
      "minLength",
      1,
      "maxLength",
      64,
      "enum",
      List.of(values)
    );
  }
}
