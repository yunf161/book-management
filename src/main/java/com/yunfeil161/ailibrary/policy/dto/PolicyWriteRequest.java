package com.yunfeil161.ailibrary.policy.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import lombok.Data;

/** 创建和全量替换草稿；更新时type不得改变。 */
@Data
public class PolicyWriteRequest {

  private String type;
  private String title;
  private String content;
  private AdmissionPolicyParameters admissionParameters;
  private BorrowingPolicyParameters borrowingParameters;
  private Long expectedVersion;

  /** 政策参数拼写错误不能被悄悄忽略。 */
  @JsonAnySetter
  public void unknown(String field, Object value) {
    throw new WrongFormatException("未声明的政策字段：" + field);
  }
}
