package com.yunfeil161.ailibrary.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rabbitmq.client.Channel;
import com.yunfeil161.ailibrary.recommendation.config.RecommendationMessaging;
import com.yunfeil161.ailibrary.recommendation.service.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 缓存预热工作队列：有限重试、独立ACK和发布失败不影响业务成功。 */
class RecommendationMessagingTests {

  private Message message(String body) {
    var properties = new MessageProperties();
    properties.setDeliveryTag(7L);
    return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
  }

  @Test
  void successfulWarmAcknowledgesOnce() throws Exception {
    var service = mock(RecommendationService.class);
    var channel = mock(Channel.class);
    new RecommendationMessaging(service).warm(message("12"), channel);
    verify(service).warm(12L);
    verify(channel).basicAck(7L, false);
    verify(channel, never()).basicReject(anyLong(), anyBoolean());
  }

  @Test
  void threeFailuresEnterDeadLetter() throws Exception {
    var service = mock(RecommendationService.class);
    var channel = mock(Channel.class);
    doThrow(new IllegalStateException("模拟预热失败")).when(service).warm(12L);
    new RecommendationMessaging(service).warm(message("12"), channel);
    verify(service, times(3)).warm(12L);
    verify(channel).basicReject(7L, false);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void malformedPayloadRejectedWithoutDatabaseAccess() throws Exception {
    var service = mock(RecommendationService.class);
    var channel = mock(Channel.class);
    new RecommendationMessaging(service).warm(message("invalid"), channel);
    verifyNoInteractions(service);
    verify(channel).basicReject(7L, false);
  }

  @Test
  void failedPublishDoesNotBreakSuccessfulLoginOrRefresh() {
    var rabbit = mock(RabbitTemplate.class);
    var publisher = new RecommendationWarmPublisher(rabbit);
    ReflectionTestUtils.setField(publisher, "enabled", true);
    doThrow(new IllegalStateException("模拟MQ不可用"))
      .when(rabbit)
      .convertAndSend("", RecommendationWarmPublisher.QUEUE, "12");
    assertDoesNotThrow(() -> publisher.request(12L));
  }
}
