package com.yunfeil161.ailibrary.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.ai.service.*;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 真实权限查询、参数校验和SSE控制器集成；内部模型HTTP另外独立测试。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:ai_chat_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
class AiChatIntegrationTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @MockitoBean
  AiGatewayClient gateway;

  LoginSession session;
  final String id = "da4c31f5-668c-424b-bf73-9b313d3561e0";

  @BeforeEach
  void setup() {
    Long role = jdbc.queryForObject(
      "select id from role where code='READER'",
      Long.class
    );
    jdbc.update("update role set status='ACTIVE'");
    session = new LoginSession(
      "test",
      123L,
      role,
      MemberType.READER,
      "READER",
      "test@example.test",
      "测试"
    );
    LoginUserContext.set(session);
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  void stream(String content) {
    var connection = mock(HttpURLConnection.class);
    when(
      gateway.chat(anyLong(), anyString(), anyString(), nullable(String.class))
    ).thenReturn(
      new AiGatewayClient.Stream(
        connection,
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
      )
    );
  }

  @Test
  void streamUsesTrustedMemberAndLeavesNoTransaction() throws Exception {
    stream(
      "event: meta\ndata: {\"conversationId\":\"" +
        id +
        "\"}\n\nevent: delta\ndata: {\"text\":\"查询结果[T1]\"}\n\nevent: done\ndata: {}\n\n"
    );
    var result = mvc
      .perform(
        post("/ai/chat")
          .header("Authorization", "Bearer test-token")
          .contentType("application/json")
          .content("{\"question\":\"查询图书\",\"memberId\":999}")
      )
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
      .andReturn();
    assertTrue(
      result.getResponse().getContentAsString().contains("event: done")
    );
    verify(gateway).chat(eq(123L), eq("test-token"), eq("查询图书"), isNull());
  }

  @Test
  void blankAndLongQuestionsAre400() throws Exception {
    for (String question : List.of(" ", "x".repeat(2001))) {
      mvc
        .perform(
          post("/ai/chat")
            .header("Authorization", "Bearer token")
            .contentType("application/json")
            .content(
              com.yunfeil161.ailibrary.common.util.JsonUtils.toJson(
                Map.of("question", question)
              )
            )
        )
        .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(gateway);
  }

  @Test
  void missingLoginAndInvalidConversationAreRejected() throws Exception {
    LoginUserContext.clear();
    mvc
      .perform(get("/ai/conversations/" + id))
      .andExpect(status().isUnauthorized());
    LoginUserContext.set(session);
    mvc
      .perform(get("/ai/conversations/bad-id"))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(gateway);
  }

  @Test
  void roleDisabledIsForbidden() throws Exception {
    jdbc.update(
      "update role set status='DISABLED' where id=?",
      session.getRoleId()
    );
    try {
      mvc
        .perform(get("/ai/conversations/" + id))
        .andExpect(status().isForbidden());
    } finally {
      jdbc.update(
        "update role set status='ACTIVE' where id=?",
        session.getRoleId()
      );
    }
    verifyNoInteractions(gateway);
  }

  @Test
  void noneScopeCannotUseChat() throws Exception {
    jdbc.update(
      "update role_permission set data_scope='NONE' where role_id=? and permission_id=(select id from permission where code='ai:chat:self')",
      session.getRoleId()
    );
    try {
      mvc
        .perform(get("/ai/conversations/" + id))
        .andExpect(status().isForbidden());
    } finally {
      jdbc.update(
        "update role_permission set data_scope='SELF' where role_id=? and permission_id=(select id from permission where code='ai:chat:self')",
        session.getRoleId()
      );
    }
    verifyNoInteractions(gateway);
  }

  @Test
  void aiMemberCannotUseHumanChat() throws Exception {
    session.setMemberType(MemberType.AI_LIBRARIAN);
    mvc
      .perform(get("/ai/conversations/" + id))
      .andExpect(status().isForbidden());
    verifyNoInteractions(gateway);
  }

  @Test
  void historyAndDeleteAlwaysUseCurrentMember() throws Exception {
    when(gateway.conversation(123L, id, false)).thenReturn(
      Map.of("conversationId", id, "messages", List.of())
    );
    when(gateway.conversation(123L, id, true)).thenReturn(
      Map.of("deleted", true)
    );
    mvc
      .perform(get("/ai/conversations/" + id))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.conversationId").value(id));
    mvc
      .perform(delete("/ai/conversations/" + id))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.deleted").value(true));
  }

  @Test
  void upstreamUnavailableKeeps503Result() throws Exception {
    when(gateway.chat(anyLong(), anyString(), anyString(), isNull())).thenThrow(
      new ServiceUnavailableException("AI暂不可用")
    );
    mvc
      .perform(
        post("/ai/chat")
          .header("Authorization", "Bearer token")
          .contentType("application/json")
          .content("{\"question\":\"查询\"}")
      )
      .andExpect(status().isServiceUnavailable())
      .andExpect(jsonPath("$.code").value(503));
  }

  @Test
  void incompleteStreamAndUnknownEventBecomeErrorNotDone() throws Exception {
    for (String input : List.of(
      "event: delta\ndata: {\"text\":\"部分答案\"}\n\n",
      "event: script\ndata: {}\n\n"
    )) {
      stream(input);
      var response = mvc
        .perform(
          post("/ai/chat")
            .header("Authorization", "Bearer token")
            .contentType("application/json")
            .content("{\"question\":\"查询\"}")
        )
        .andReturn()
        .getResponse()
        .getContentAsString();
      assertTrue(response.contains("event: error"));
      assertFalse(response.contains("event: done"));
    }
  }

  @Test
  void businessProposalBecomesOwnedPreparedActionWithoutExecutingBusiness()
    throws Exception {
    String source =
      "event: proposal\ndata: {\"skill\":\"plan_create\",\"arguments\":{\"name\":\"流式草稿\",\"targetBooks\":3}}\n\nevent: done\ndata: {}\n\n";
    when(
      gateway.business(anyLong(), anyString(), anyString(), isNull())
    ).thenReturn(
      new AiGatewayClient.Stream(
        mock(HttpURLConnection.class),
        new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))
      )
    );
    var response = mvc
      .perform(
        post("/ai/chat")
          .header("Authorization", "Bearer token")
          .contentType("application/json")
          .content("{\"question\":\"创建计划\",\"task\":\"BUSINESS\"}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    assertTrue(response.contains("event: action"));
    assertTrue(response.contains("PREPARED"));
    assertTrue(response.contains("event: done"));
    assertFalse(response.contains("event: proposal"));
    assertEquals(
      0,
      jdbc.queryForObject(
        "select count(*) from reading_plan where member_id=123",
        Integer.class
      )
    );
    assertEquals(
      1,
      jdbc.queryForObject(
        "select count(*) from ai_business_action where member_id=123 and state='PREPARED'",
        Integer.class
      )
    );
  }

  @Test
  void consultationRejectsUpstreamWriteProposal() throws Exception {
    stream(
      "event: proposal\ndata: {\"skill\":\"plan_create\",\"arguments\":{\"name\":\"非法草稿\",\"targetBooks\":3}}\n\nevent: done\ndata: {}\n\n"
    );
    var response = mvc
      .perform(
        post("/ai/chat")
          .header("Authorization", "Bearer token")
          .contentType("application/json")
          .content("{\"question\":\"查询\"}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    assertTrue(response.contains("event: error"));
    assertFalse(response.contains("event: action"));
    assertFalse(response.contains("event: done"));
  }
}
