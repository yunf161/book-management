package com.yunfeil161.ailibrary.communication.event.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rabbitmq.client.Channel;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.communication.common.cache.UnreadIndex;
import com.yunfeil161.ailibrary.communication.event.entity.*;
import com.yunfeil161.ailibrary.communication.event.mapper.OutboxMapper;
import com.yunfeil161.ailibrary.communication.websocket.NotificationSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** 单元测试补齐消息确认、离线跳过以及失败进入死信的路径。 */
class CommunicationMessagingTests {
  RabbitTemplate rabbit = mock(RabbitTemplate.class);
  OutboxMapper outbox = mock(OutboxMapper.class);
  UnreadIndex index = mock(UnreadIndex.class);
  NotificationSocket sockets = mock(NotificationSocket.class);
  CommunicationMessaging messaging = new CommunicationMessaging(rabbit, outbox, index, sockets);

  Message event() {
    var event = new MessageCreatedEvent();
    event.setEventId("unit-event");
    event.setEventType("message.created");
    event.setSchemaVersion(1);
    event.setMessageId(1L);
    event.setRecipientMemberId(2L);
    MessageProperties properties = new MessageProperties();
    properties.setDeliveryTag(7L);
    return new Message(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8), properties);
  }

  @Test
  void onlineIndexAndIndependentNotificationAcknowledge() throws Exception {
    Channel channel = mock(Channel.class);
    when(sockets.online(2L)).thenReturn(true);
    messaging.index(event(), channel);
    messaging.notice(event(), channel);
    verify(index).add(2L, 1L);
    verify(sockets).notify(any(MessageCreatedEvent.class));
    verify(channel, times(2)).basicAck(7L, false);
  }

  @Test
  void offlineIndexSkipsAndFailedNotificationGoesToDeadLetter() throws Exception {
    Channel channel = mock(Channel.class);
    messaging.index(event(), channel);
    verifyNoInteractions(index);
    verify(channel).basicAck(7L, false);
    doThrow(new java.io.IOException("test failure")).when(sockets).notify(any());
    messaging.notice(event(), channel);
    verify(sockets, times(3)).notify(any());
    verify(channel).basicReject(7L, false);
  }

  @Test
  void publishedOnlyAfterBrokerConfirmation() {
    OutboxEvent event = new OutboxEvent();
    event.setId(1L);
    event.setEventId("outbox-unit");
    event.setPayloadJson("{}");
    when(outbox.pending()).thenReturn(List.of(event));
    when(outbox.claim(eq(1L), anyString())).thenReturn(1);
    doAnswer(
            invocation -> {
              CorrelationData correlation = invocation.getArgument(3);
              correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
              return null;
            })
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    messaging.publishPending();
    verify(outbox).published(eq(1L), anyString());
    verify(outbox, never()).failed(anyLong(), anyString(), anyString(), any());
  }
}
