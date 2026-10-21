package com.yunfeil161.ailibrary.identity.application.dto;

import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeDecision;
import lombok.Data;

/** 审批申请；expectedVersion针对申请记录本身。 */
@Data
public class MemberChangeDecisionRequest {
  private MemberChangeDecision decision;
  private String reason;
  private Long expectedVersion;
}
