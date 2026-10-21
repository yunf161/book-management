package com.yunfeil161.ailibrary.operations;

import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 日期区间指标与当前库存分开表达，不把当前库存误称为历史快照。 */
@Service
@RequiredArgsConstructor
public class OperationsReportService {

  private final JdbcTemplate jdbc;
  private final PermissionAccess access;

  public Map<String, Object> overview(LocalDate from, LocalDate to) {
    access.require("operations:query", DataScope.ALL);
    if (to == null) to = LocalDate.now();
    if (from == null) from = to.minusDays(29);
    if (
      from.isAfter(to) ||
      java.time.temporal.ChronoUnit.DAYS.between(from, to) > 365
    ) throw new WrongFormatException("日期范围不能倒置或超过366天");
    var start = from.atStartOfDay();
    var end = to.plusDays(1).atStartOfDay();
    var totals = new LinkedHashMap<String, Object>();
    totals.put(
      "borrowed",
      jdbc.queryForObject(
        "select count(*) from borrow_record where borrowed_at>=? and borrowed_at<?",
        Long.class,
        start,
        end
      )
    );
    totals.put(
      "returned",
      jdbc.queryForObject(
        "select count(*) from borrow_record where returned_at>=? and returned_at<?",
        Long.class,
        start,
        end
      )
    );
    totals.put(
      "newReaders",
      jdbc.queryForObject(
        "select count(*) from member where member_type='READER' and created_at>=? and created_at<?",
        Long.class,
        start,
        end
      )
    );
    totals.put(
      "currentOverdue",
      jdbc.queryForObject(
        // due_at由借阅Service按JVM本地时间写入，不能与可能采用UTC的数据库时钟混比。
        "select count(*) from borrow_record where returned_at is null and due_at<? and status in ('BORROWING','OVERDUE')",
        Long.class,
        java.time.LocalDateTime.now()
      )
    );
    totals.put(
      "currentBooks",
      jdbc.queryForObject(
        "select count(*) from book where status='ACTIVE'",
        Long.class
      )
    );
    totals.put(
      "currentCopies",
      jdbc.queryForObject(
        "select count(*) from book_copy where status<>'DISCARDED'",
        Long.class
      )
    );
    totals.put(
      "currentAvailable",
      jdbc.queryForObject(
        "select count(*) from book_copy where status='AVAILABLE'",
        Long.class
      )
    );
    var raw = jdbc.queryForList(
      "select cast(borrowed_at as date) as report_day,count(*) as amount from borrow_record where borrowed_at>=? and borrowed_at<? group by cast(borrowed_at as date) order by report_day",
      start,
      end
    );
    var counts = new HashMap<String, Long>();
    for (var row : raw)
      counts.put(
        row.get("report_day").toString(),
        ((Number) row.get("amount")).longValue()
      );
    var days = new ArrayList<Map<String, Object>>();
    for (var day = from; !day.isAfter(to); day = day.plusDays(1)) days.add(
      Map.of(
        "day",
        day.toString(),
        "amount",
        counts.getOrDefault(day.toString(), 0L)
      )
    );
    var stock = jdbc.queryForList(
      "select status,count(*) as amount from book_copy group by status order by status"
    );
    return Map.of(
      "from",
      from.toString(),
      "to",
      to.toString(),
      "totals",
      totals,
      "dailyBorrowing",
      days,
      "currentCopyStatuses",
      stock
    );
  }
}
