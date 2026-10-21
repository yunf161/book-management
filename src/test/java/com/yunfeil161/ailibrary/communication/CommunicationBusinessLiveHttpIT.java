package com.yunfeil161.ailibrary.communication;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.book.borrow.service.BorrowOverdueService;
import com.yunfeil161.ailibrary.book.borrow.service.impl.BorrowApplicationExpirationService;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 复用真实网络测试设施，覆盖联系人以及业务通知从提交到已读的完整流程。 */
class CommunicationBusinessLiveHttpIT extends CommunicationLiveHttpIT {
  @Autowired BorrowOverdueService overdue;
  @Autowired BorrowApplicationExpirationService expiration;

  @Test
  void businessNotificationsClosedLoop() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Long category = null;
    Long book = null;
    Long copy = null;
    try {
      Identity reader = member("READER", "READER");
      Identity staff = member("EMPLOYEE", "LIBRARIAN");
      Identity outsider = member("READER", "READER");
      jdbc.update(
          "update member set email_verified_at=current_timestamp(3) where id=?", reader.id());
      // 联系人接口返回摘要，读者即使指定READER也只能查询馆员。
      String contacts =
          call(
              "读者查询馆员联系人",
              "GET",
              "/members?scene=CONTACT&id=" + staff.id() + "&memberType=READER",
              reader.token(),
              null,
              200,
              true);
      assertEquals(1, number(contacts, "$.data.total"));
      assertFalse(contacts.contains("passwordHash"));
      assertFalse(contacts.contains("roleId"));
      assertEquals(
          0,
          number(
              call(
                  "读者不能查询其他读者",
                  "GET",
                  "/members?scene=CONTACT&id=" + outsider.id(),
                  reader.token(),
                  null,
                  200,
                  true),
              "$.data.total"));
      assertEquals(
          1,
          number(
              call(
                  "馆员查询读者联系人",
                  "GET",
                  "/members?scene=CONTACT&id=" + reader.id(),
                  staff.token(),
                  null,
                  200,
                  true),
              "$.data.total"));

      long qualification =
          number(
              call(
                  "申请读者资格",
                  "POST",
                  "/member-change-applications",
                  reader.token(),
                  Map.of(
                      "applicationType",
                      "READER_QUALIFICATION",
                      "reason",
                      "通知闭环测试",
                      "acceptedRuleVersion",
                      "BORROWING-1",
                      "expectedMemberVersion",
                      0),
                  200,
                  true),
              "$.data.id");
      call(
          "馆员审批资格并保存通知",
          "POST",
          "/admin/member-change-applications/" + qualification + "/decision",
          staff.token(),
          Map.of("decision", "APPROVE", "reason", "资格审核通过", "expectedVersion", 0),
          200,
          true);
      notification(reader, outsider, "MEMBER_CHANGE", qualification, "APPROVED");

      jdbc.update(
          "insert into book_category(code,name,status,version) values(?,?,'ACTIVE',0)",
          "NOTIFY-" + suffix,
          "通知测试分类" + suffix);
      category =
          jdbc.queryForObject(
              "select id from book_category where code=?", Long.class, "NOTIFY-" + suffix);
      jdbc.update(
          "insert into book(category_id,title,author,isbn,status,language,version)"
              + " values(?,?,?,?,'ACTIVE','zh-CN',0)",
          category,
          "通知测试图书" + suffix,
          "作者",
          "NOTIFY-" + suffix);
      book =
          jdbc.queryForObject("select id from book where isbn=?", Long.class, "NOTIFY-" + suffix);
      jdbc.update(
          "insert into book_copy(book_id,barcode,location,status,version)"
              + " values(?,?,?,'AVAILABLE',0)",
          book,
          "NOTIFY-" + suffix,
          "测试书架");
      copy =
          jdbc.queryForObject(
              "select id from book_copy where barcode=?", Long.class, "NOTIFY-" + suffix);

      long application = apply(reader, book);
      call(
          "批准借阅并生成通知",
          "POST",
          "/admin/borrow-applications/" + application + "/approve",
          staff.token(),
          Map.of("approvedDays", 7, "copyId", copy, "expectedVersion", 0),
          200,
          true);
      notification(reader, outsider, "BORROW_APPLICATION", application, "APPROVED");
      long record =
          number(
              call(
                  "读者查询借阅记录",
                  "GET",
                  "/borrow-records?bookId=" + book,
                  reader.token(),
                  null,
                  200,
                  true),
              "$.data.rows[0].id");
      long renewal =
          number(
              call(
                  "提交续借申请",
                  "POST",
                  "/borrow-applications",
                  reader.token(),
                  Map.of("applicationType", "RENEW", "borrowRecordId", record, "requestedDays", 7),
                  200,
                  true),
              "$.data.id");
      call(
          "批准续借并生成通知",
          "POST",
          "/admin/borrow-applications/" + renewal + "/approve",
          staff.token(),
          Map.of("approvedDays", 7, "expectedVersion", 0),
          200,
          true);
      notification(reader, outsider, "BORROW_APPLICATION", renewal, "APPROVED");

      // 使用过去时间扫描，仅本测试的应还时间改为更早，避免修改真实业务记录。
      LocalDateTime historical = LocalDateTime.of(2001, 1, 1, 0, 0);
      jdbc.update(
          "update borrow_record set borrowed_at=?,due_at=? where id=?",
          historical.minusDays(2),
          historical.minusHours(1),
          record);
      assertEquals(1, overdue.synchronizeOverdueRecords(historical));
      assertEquals(0, overdue.synchronizeOverdueRecords(historical));
      notification(reader, outsider, "BORROW_RECORD", record, "OVERDUE");
      call(
          "逾期图书归还",
          "POST",
          "/admin/borrow-records/" + record + "/return",
          staff.token(),
          Map.of("expectedVersion", 2, "returnConditionNote", "完好"),
          200,
          true);

      long rejected = apply(reader, book);
      call(
          "拒绝借阅并生成通知",
          "POST",
          "/admin/borrow-applications/" + rejected + "/reject",
          staff.token(),
          Map.of("expectedVersion", 0, "decisionRemark", "测试拒绝"),
          200,
          true);
      notification(reader, outsider, "BORROW_APPLICATION", rejected, "REJECTED");
      long expired = apply(reader, book);
      jdbc.update(
          "update borrow_application set submitted_at=?,expires_at=? where id=?",
          historical.minusDays(2),
          historical.minusHours(1),
          expired);
      expiration.expire(expired, 0L, historical);
      expiration.expire(expired, 0L, historical);
      notification(reader, outsider, "BORROW_APPLICATION", expired, "EXPIRED");

      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
      Long pending;
      do {
        pending =
            jdbc.queryForObject(
                "select count(*) from outbox_event where recipient_member_id=? and"
                    + " status<>'PUBLISHED'",
                Long.class,
                reader.id());
        if (pending == 0) break;
        Thread.sleep(200);
      } while (System.nanoTime() < deadline);
      assertEquals(0L, pending);
      report.add(
          Map.of("case", "六种业务通知经真实RabbitMQ确认发布", "notifications", 6, "pendingOutbox", pending));
    } finally {
      // 清理只匹配本次随机创建的成员，系统通知不能按sender=NULL整体删除。
      for (long id : members) {
        sessions.deleteAllForMember(id);
        jdbc.update("delete from outbox_event where recipient_member_id=?", id);
        List<Long> messages =
            jdbc.queryForList(
                "select message_id from message_recipient where recipient_member_id=?",
                Long.class,
                id);
        for (long message : messages) {
          jdbc.update("delete from message_recipient where message_id=?", message);
          jdbc.update("delete from message where id=?", message);
        }
        jdbc.update("delete from member_change_application where applicant_member_id=?", id);
        jdbc.update("delete from borrow_record where member_id=?", id);
        jdbc.update("delete from borrow_application where member_id=?", id);
        jdbc.update("delete from member where id=?", id);
      }
      if (copy != null) jdbc.update("delete from book_copy where id=?", copy);
      if (book != null) jdbc.update("delete from book where id=?", book);
      if (category != null) jdbc.update("delete from book_category where id=?", category);
      Files.createDirectories(Path.of("target/communication"));
      Files.writeString(
          Path.of("target/communication/business-http-results.json"),
          com.yunfeil161.ailibrary.common.util.JsonUtils.toJson(report));
    }
  }

  long apply(Identity reader, long book) throws Exception {
    return number(
        call(
            "提交借阅申请",
            "POST",
            "/borrow-applications",
            reader.token(),
            Map.of("bookId", book, "requestedDays", 7),
            200,
            true),
        "$.data.id");
  }

  /** 每种通知都验证本人可查询、他人不可见及已读后的未读查询。 */
  void notification(Identity recipient, Identity other, String type, long businessId, String label)
      throws Exception {
    String path =
        "/messages?box=INBOX&messageType=SYSTEM&businessType=" + type + "&businessId=" + businessId;
    String response = call(label + "系统通知查询", "GET", path, recipient.token(), null, 200, true);
    assertEquals(1, number(response, "$.data.total"));
    long message = number(response, "$.data.rows[0].id");
    assertEquals("系统通知", com.jayway.jsonpath.JsonPath.read(response, "$.data.rows[0].senderName"));
    assertEquals(
        0,
        number(
            call(label + "通知不可越权查询", "GET", path, other.token(), null, 200, true), "$.data.total"));
    call(
        label + "通知不可越权已读",
        "POST",
        "/messages/" + message + "/read",
        other.token(),
        null,
        403,
        true);
    call(
        label + "通知标记已读",
        "POST",
        "/messages/" + message + "/read",
        recipient.token(),
        null,
        200,
        true);
    assertEquals(
        0,
        number(
            call(
                label + "已读通知不再算未读",
                "GET",
                path + "&read=false",
                recipient.token(),
                null,
                200,
                true),
            "$.data.total"));
  }
}
