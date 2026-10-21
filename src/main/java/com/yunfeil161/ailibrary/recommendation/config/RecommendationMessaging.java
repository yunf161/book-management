package com.yunfeil161.ailibrary.recommendation.config;

import com.rabbitmq.client.Channel;
import com.yunfeil161.ailibrary.recommendation.service.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 预热工作队列只处理缓存；多个消费者由相同成员资源锁协调。 */
@Configuration
@ConditionalOnProperty(
  name = "library.recommendation.mq-enabled",
  havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class RecommendationMessaging {

  private final RecommendationService service;

  @Bean
  Declarables recommendationTopology() {
    DirectExchange dead = new DirectExchange(
      "library.recommendation.dead",
      true,
      false
    );
    Queue queue = QueueBuilder.durable(RecommendationWarmPublisher.QUEUE)
      .deadLetterExchange(dead.getName())
      .deadLetterRoutingKey("warm")
      .build();
    Queue deadQueue = new Queue(
      RecommendationWarmPublisher.QUEUE + ".dead",
      true
    );
    return new Declarables(
      dead,
      queue,
      deadQueue,
      BindingBuilder.bind(deadQueue).to(dead).with("warm")
    );
  }

  @RabbitListener(
    id = "recommendation-warm",
    queues = RecommendationWarmPublisher.QUEUE,
    ackMode = "MANUAL",
    concurrency = "2"
  )
  public void warm(Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        Long memberId = Long.valueOf(
          new String(message.getBody(), StandardCharsets.UTF_8)
        );
        service.warm(memberId);
        channel.basicAck(deliveryTag, false);
        return;
      } catch (Exception exception) {
        log.warn("推荐缓存预热失败：attempt={}", attempt, exception);
      }
    }
    channel.basicReject(deliveryTag, false);
  }
}
