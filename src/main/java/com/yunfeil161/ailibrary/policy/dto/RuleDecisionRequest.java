package com.yunfeil161.ailibrary.policy.dto;

import lombok.Data;

/** 须知必须显式接受或拒绝，接受时使用成员乐观锁。 */
@Data
public class RuleDecisionRequest {

  private String ruleVersion;
  private String decision;
  private Long expectedMemberVersion;
}
