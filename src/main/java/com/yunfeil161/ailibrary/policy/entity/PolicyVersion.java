package com.yunfeil161.ailibrary.policy.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 政策版本；正文和参数在发布后不可修改，历史记录用于申请追溯。 */
@Data
public class PolicyVersion {

  private Long id;
  private String type;
  private String title;
  private String content;
  private String parametersJson;
  private String status;
  private Long policyVersion;
  private Long version;
  private Long publishedBy;
  private LocalDateTime publishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
