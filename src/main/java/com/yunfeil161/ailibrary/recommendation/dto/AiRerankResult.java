package com.yunfeil161.ailibrary.recommendation.dto;

import java.util.List;

/** 模型只选择候选书并解释，不产生可与规则分混淆的模型分数。 */
public record AiRerankResult(List<Choice> items) {
  public record Choice(Long bookId, String reason) {}
}
