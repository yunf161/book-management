package com.yunfeil161.ailibrary.policy.dto;

import java.util.List;
import lombok.Data;

/** 准入条件；邮箱和接受当前借阅须知始终必需。 */
@Data
public class AdmissionPolicyParameters {

  public enum RequiredField {
    NAME,
    PHONE,
  }

  private Boolean requireVerifiedEmail;
  private List<RequiredField> requiredFields;
  private Integer defaultMaxBorrowCount;

  /** Unknown parameters must not be silently ignored. */
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void unknown(String field, Object value) {
    throw new com.yunfeil161.ailibrary.common.exception.WrongFormatException(
      "Unknown policy parameter: " + field
    );
  }
}
