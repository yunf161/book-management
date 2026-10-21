package com.yunfeil161.ailibrary.audit;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 在业务事务和既有资源锁内读取前后快照；关联行仅按不可变主键关联查询。 */
@Component
@RequiredArgsConstructor
public class AuditSnapshots {

  private final JdbcTemplate jdbc;
  private final AuditRedactor redactor;

  /** 条码入口在原资源锁内解析当前记录，只用于定位审计对象。 */
  public Long recordByBarcode(String barcode) {
    if (barcode == null) return null;
    var ids = jdbc.queryForList(
      "select r.id from borrow_record r join book_copy c on c.id=r.copy_id where c.barcode=? and r.returned_at is null and r.status in ('BORROWING','OVERDUE') order by r.id desc limit 1",
      Long.class,
      barcode.trim()
    );
    return ids.isEmpty() ? null : ids.get(0);
  }

  public Map<String, Object> read(AuditResource resource, Long id) {
    if (id == null || id <= 0) return null;
    String column =
      resource == AuditResource.READING_PLAN_MEMBER ? "member_id" : "id";
    var rows = jdbc.queryForList(
      "select * from " + resource.table + " where " + column + "=?",
      id
    );
    if (rows.isEmpty()) return null;
    var data = new LinkedHashMap<String, Object>();
    data.put(resource.table, rows.stream().map(redactor::row).toList());
    if (resource == AuditResource.MEMBER) {
      related(data, "employee_profile", "member_id", id);
      related(data, "reader_settings", "member_id", id);
      related(data, "reader_interest", "member_id", id);
    } else if (resource == AuditResource.BORROW_RECORD) {
      var row = rows.get(0);
      related(
        data,
        "member",
        "id",
        ((Number) row.get("member_id")).longValue()
      );
      related(
        data,
        "book_copy",
        "id",
        ((Number) row.get("copy_id")).longValue()
      );
    } else if (resource == AuditResource.MEMBER_CHANGE) {
      related(data, "reader_change_application", "application_id", id);
      related(data, "employee_change_application", "application_id", id);
      related(
        data,
        "member",
        "id",
        ((Number) rows.get(0).get("target_member_id")).longValue()
      );
      related(
        data,
        "employee_profile",
        "member_id",
        ((Number) rows.get(0).get("target_member_id")).longValue()
      );
    } else if (resource == AuditResource.READING_PLAN_MEMBER) {
      related(
        data,
        "reading_plan_item",
        "plan_id",
        ((Number) rows.get(0).get("id")).longValue()
      );
    } else if (resource == AuditResource.BORROW_APPLICATION) {
      related(data, "borrow_record", "application_id", id);
      related(
        data,
        "member",
        "id",
        ((Number) rows.get(0).get("member_id")).longValue()
      );
      if (
        rows.get(0).get("borrow_record_id") instanceof Number recordId
      ) related(data, "borrow_record", "id", recordId.longValue());
    } else if (resource == AuditResource.POLICY) {
      data.put(
        "policy_current",
        jdbc
          .queryForList(
            "select * from policy_current where type=?",
            rows.get(0).get("type")
          )
          .stream()
          .map(redactor::row)
          .toList()
      );
    } else if (resource == AuditResource.BOOK) {
      related(data, "book_tag", "book_id", id);
    }
    return data;
  }

  private void related(
    Map<String, Object> data,
    String table,
    String column,
    Long id
  ) {
    // 只读取固定业务关联，不接受客户端SQL。条目快照限制大小并标记截断。
    var rows = jdbc.queryForList(
      "select * from " +
        table +
        " where " +
        column +
        "=? order by " +
        column +
        " limit 101",
      id
    );
    data.put(table, rows.stream().limit(100).map(redactor::row).toList());
    if (rows.size() > 100) data.put(table + "_truncated", true);
  }
}
