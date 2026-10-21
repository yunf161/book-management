package com.yunfeil161.ailibrary.recommendation.dto;

import lombok.Data;

/** 当前推荐的反馈：NOT_INTERESTED、ALREADY_READ 或 TOO_DIFFICULT。 */
@Data
public class FeedbackRequest {

  private String kind;
}
