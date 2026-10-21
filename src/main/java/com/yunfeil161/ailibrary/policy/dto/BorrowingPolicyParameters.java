package com.yunfeil161.ailibrary.policy.dto;

import lombok.Data;

/** 只影响新申请和审批；不重算已有借阅记录的到期时间。 */
@Data
public class BorrowingPolicyParameters {

  private Integer defaultBorrowDays;
  private Integer maxBorrowDays;
  private Integer maxRenewCount;
  private Integer defaultRenewDays;
  private Integer maxRenewDays;
  private Boolean blockBorrowWhenOverdue;
  private Integer applicationValidityDays;

  /** Unknown parameters must not be silently ignored. */
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void unknown(String field, Object value) {
    throw new com.yunfeil161.ailibrary.common.exception.WrongFormatException(
      "Unknown policy parameter: " + field
    );
  }
}
