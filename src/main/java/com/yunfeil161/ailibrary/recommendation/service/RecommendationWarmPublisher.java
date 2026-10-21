package com.yunfeil161.ailibrary.recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 缓存预热可以丢失，查询有数据库兜底，因此不占用可靠业务 Outbox。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationWarmPublisher {

  public static final String QUEUE = "library.recommendation.warm";
  private final RabbitTemplate rabbit;

  @Value("${library.recommendation.mq-enabled:false}")
  private boolean enabled;

  public void request(Long memberId) {
    if (!enabled || memberId == null) return;
    try {
      rabbit.convertAndSend("", QUEUE, memberId.toString());
    } catch (Exception exception) {
      log.warn(
        "推荐缓存预热发布失败，后续查询将回源数据库：memberId={}",
        memberId,
        exception
      );
    }
  }
}
