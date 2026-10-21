package com.yunfeil161.ailibrary.recommendation.entity;

import lombok.Data;

/** 兴趣与书目共用的标签字典；关联表保存标签 ID。 */
@Data
public class InterestTag {

  private Long id;
  private String code;
  private String name;
  private String status;
}
