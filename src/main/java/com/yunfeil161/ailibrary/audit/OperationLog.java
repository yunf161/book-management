package com.yunfeil161.ailibrary.audit;

import java.time.LocalDateTime;
import lombok.Data;

/** 只追加的脱敏操作记录，沿用member_operation_log存储。 */
@Data
public class OperationLog {

  private Long id;
  private String eventId;
  private String actorKind;
  private Long actorMemberId;
  private String actorMemberType;
  private Long actorRoleId;
  private String targetType;
  private Long targetId;
  private String actionCode;
  private String reason;
  private String beforeData;
  private String afterData;
  private String resultStatus;
  private String errorCode;
  private LocalDateTime createdAt;
}
