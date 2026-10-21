package com.yunfeil161.ailibrary.policy.dto;

import lombok.Getter;
import lombok.Setter;

/** null表示明确断言该类型尚无当前政策；缺省属性不能当作null处理。 */
@Getter
public class PolicyPublishRequest {

  @Setter
  private Long expectedVersion;

  private Long expectedCurrentPolicyId;

  @com.fasterxml.jackson.annotation.JsonIgnore
  private boolean currentPolicyIdProvided;

  public void setExpectedCurrentPolicyId(Long value) {
    expectedCurrentPolicyId = value;
    currentPolicyIdProvided = true;
  }
}
