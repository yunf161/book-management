package com.yunfeil161.ailibrary.communication.event.service;

import com.rabbitmq.client.Channel;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.communication.common.cache.UnreadIndex;
import com.yunfeil161.ailibrary.communication.event.entity.MessageCreatedEvent;
import com.yunfeil161.ailibrary.communication.event.mapper.OutboxMapper;
import com.yunfeil161.ailibrary.communication.websocket.NotificationSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Fanout将同一收件事件送往两个职责独立的工作队列；同队列可增加消费者。 */
@Configuration
@ConditionalOnProperty(name = "library.communication.mq-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CommunicationMessaging {
  public static final String EXCHANGE = "library.communication.events";
  public static final String INDEX_QUEUE = "library.communication.index";
  public static final String NOTICE_QUEUE = "library.communication.notice";
  private final RabbitTemplate rabbit;
  private final OutboxMapper outbox;
  private final UnreadIndex index;
  private final NotificationSocket sockets;

  @Bean
  Declarables communicationTopology() {
    FanoutExchange exchange = new FanoutExchange(EXCHANGE, true, false);
    DirectExchange dead = new DirectExchange(EXCHANGE + ".dead", true, false);
    Queue indexQueue =
        QueueBuilder.durable(INDEX_QUEUE)
            .deadLetterExchange(dead.getName())
            .deadLetterRoutingKey("index")
            .build();
    Queue noticeQueue =
        QueueBuilder.durable(NOTICE_QUEUE)
            .deadLetterExchange(dead.getName())
            .deadLetterRoutingKey("notice")
            .build();
    Queue indexDead = new Queue(INDEX_QUEUE + ".dead", true);
    Queue noticeDead = new Queue(NOTICE_QUEUE + ".dead", true);
    return new Declarables(
        exchange,
        dead,
        indexQueue,
        noticeQueue,
        indexDead,
        noticeDead,
        BindingBuilder.bind(indexQueue).to(exchange),
        BindingBuilder.bind(noticeQueue).to(exchange),
        BindingBuilder.bind(indexDead).to(dead).with("index"),
        BindingBuilder.bind(noticeDead).to(dead).with("notice"));
  }

  /** 发布确认后才标记PUBLISHED；租约可在进程崩溃后重新领取。 */
  @Scheduled(fixedDelayString = "${library.communication.publish-delay:1000}")
  public void publishPending() {
    for (var event : outbox.pending()) {
      String lease = UUID.randomUUID().toString();
      if (outbox.claim(event.getId(), lease) != 1) continue;
      try {
        CorrelationData correlation = new CorrelationData(event.getEventId());
        var message =
            MessageBuilder.withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.getEventId())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbit.send(EXCHANGE, "", message, correlation);
        var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
          throw new IllegalStateException("事件未被确认或没有路由");
        }
        outbox.published(event.getId(), lease);
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        outbox.failed(
            event.getId(),
            lease,
            e.getClass().getSimpleName(),
            LocalDateTime.now().plusSeconds(10));
        log.warn("通信事件发布失败：eventId={}", event.getEventId(), e);
      }
    }
  }

  @RabbitListener(
      id = "communication-index",
      queues = INDEX_QUEUE,
      ackMode = "MANUAL",
      concurrency = "2")
  public void index(Message message, Channel channel) throws java.io.IOException {
    consume(message, channel, true);
  }

  @RabbitListener(
      id = "communication-notice",
      queues = NOTICE_QUEUE,
      ackMode = "MANUAL",
      concurrency = "2")
  public void notice(Message message, Channel channel) throws java.io.IOException {
    consume(message, channel, false);
  }

  /** 有限本地重试，失败转死信；客户端用eventId去重可能重复的实时提醒。 */
  private void consume(Message message, Channel channel, boolean updateIndex)
      throws java.io.IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        java.util.Map<String, Object> envelope = JsonUtils.fromJson(payload,
            new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        if ("policy.published".equals(envelope.get("eventType"))) {
          // 索引队列无需处理政策；提醒队列独立推送，仍使用原有确认和重试机制。
          if (!updateIndex && "BORROWING".equals(envelope.get("type"))) {
            envelope.put("eventType", "rules.updated");
            sockets.rulesUpdated(envelope);
          }
          channel.basicAck(tag, false);
          return;
        }
        var event =
            JsonUtils.fromJson(
                new String(message.getBody(), StandardCharsets.UTF_8), MessageCreatedEvent.class);
        if (!"message.created".equals(event.getEventType())
            || !Integer.valueOf(1).equals(event.getSchemaVersion())
            || event.getEventId() == null
            || event.getMessageId() == null
            || event.getRecipientMemberId() == null) {
          throw new IllegalArgumentException("不支持的通信事件");
        }
        if (updateIndex) {
          if (sockets.online(event.getRecipientMemberId()))
            index.add(event.getRecipientMemberId(), event.getMessageId());
        } else {
          sockets.notify(event);
        }
        channel.basicAck(tag, false);
        return;
      } catch (Exception e) {
        log.warn(
            "通信消费失败：messageId={}, attempt={}, index={}",
            message.getMessageProperties().getMessageId(),
            attempt,
            updateIndex,
            e);
      }
    }
    channel.basicReject(tag, false);
  }
}
