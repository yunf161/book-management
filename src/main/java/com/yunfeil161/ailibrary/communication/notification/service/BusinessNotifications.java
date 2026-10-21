package com.yunfeil161.ailibrary.communication.notification.service;

import com.yunfeil161.ailibrary.book.borrow.entity.*;
import com.yunfeil161.ailibrary.communication.message.entity.CommunicationMessage;
import com.yunfeil161.ailibrary.communication.message.mapper.MessageMapper;
import com.yunfeil161.ailibrary.communication.message.service.MessageWriter;
import com.yunfeil161.ailibrary.identity.application.entity.MemberChangeApplication;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 内部业务通知入口；必须加入业务事务，不允许先提交业务再独立保存消息。 */
@Service
@RequiredArgsConstructor
public class BusinessNotifications {

  private final MessageMapper messages;
  private final MessageWriter writer;

  @Transactional(propagation = Propagation.MANDATORY)
  public void borrowDecision(
    BorrowApplication application,
    LocalDateTime dueAt
  ) {
    String label = application.getApplicationType().name().equals("RENEW")
      ? "续借"
      : "借阅";
    String result = application.getStatus().name().equals("APPROVED")
      ? "通过"
      : "拒绝";
    send(
      "borrow-application:" + application.getId() + ":decision",
      application.getMemberId(),
      "BORROW_APPLICATION",
      application.getId(),
      label + "申请已" + result,
      label +
        "申请 #" +
        application.getId() +
        " 已" +
        result +
        (dueAt == null ? "" : "，应还时间：" + dueAt) +
        (application.getDecisionRemark() == null
          ? ""
          : "。审批说明：" + application.getDecisionRemark())
    );
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void expired(BorrowApplication application) {
    send(
      "borrow-application:" + application.getId() + ":expired",
      application.getMemberId(),
      "BORROW_APPLICATION",
      application.getId(),
      "借阅申请已过期",
      "申请 #" + application.getId() + " 已超过待审批有效期，需要时请重新提交。"
    );
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void overdue(BorrowRecord record, String title) {
    send(
      "borrow-record:" + record.getId() + ":overdue",
      record.getMemberId(),
      "BORROW_RECORD",
      record.getId(),
      "图书逾期提醒",
      "《" +
        title +
        "》已逾期，应还时间：" +
        record.getDueAt() +
        "，请及时归还。"
    );
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void memberDecision(MemberChangeApplication application) {
    String result = application.getStatus().name().equals("APPROVED")
      ? "通过"
      : "拒绝";
    send(
      "member-change:" + application.getId() + ":decision",
      application.getApplicantMemberId(),
      "MEMBER_CHANGE",
      application.getId(),
      "资料或资格申请已" + result,
      "申请 #" +
        application.getId() +
        "（" +
        application.getApplicationType() +
        "）已" +
        result +
        (application.getDecisionReason() == null
          ? ""
          : "。审批说明：" + application.getDecisionReason())
    );
  }

  /** 到期结果与申请状态在同一事务落库，稳定业务键防止重复通知。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void memberExpired(MemberChangeApplication application) {
    send(
      "member-change:" + application.getId() + ":expired",
      application.getApplicantMemberId(),
      "MEMBER_CHANGE",
      application.getId(),
      "资料或资格申请已过期",
      "申请 #" +
        application.getId() +
        "（" +
        application.getApplicationType() +
        "）已超过审批有效期，未执行申请中的变更。需要时请刷新个人资料后重新提交。"
    );
  }

  /** 稳定业务键及数据库唯一约束去重；自动通知不伪造任何人类发送者。 */
  private void send(
    String key,
    Long recipient,
    String type,
    Long businessId,
    String subject,
    String body
  ) {
    if (messages.findNotification(key) != null) return;
    var message = new CommunicationMessage();
    message.setMessageType("SYSTEM");
    message.setBusinessType(type);
    message.setBusinessId(businessId);
    message.setNotificationKey(key);
    message.setSendType("MULTICAST");
    message.setSubject(subject);
    message.setBody(body);
    message.setClientMessageId(UUID.randomUUID().toString());
    message.setRequestHash("0".repeat(64));
    message.setRecipientCount(1);
    writer.write(message, List.of(recipient));
  }
}
