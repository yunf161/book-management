package com.yunfeil161.ailibrary.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.recommendation.dto.*;
import com.yunfeil161.ailibrary.recommendation.mapper.*;
import com.yunfeil161.ailibrary.recommendation.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 用真实H2 Mapper、事务和Redisson锁验证推荐；真实HTTP另行验证Redis及MQ。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:ai_recommendation_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
class AiRecommendationIntegrationTests {

  @Autowired
  RecommendationService service;

  @Autowired
  TagService tags;

  @Autowired
  RecommendationMapper mapper;

  @Autowired
  com.yunfeil161.ailibrary.identity.service.MemberService memberService;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  MockMvc mvc;

  @MockitoBean
  CacheClient cache;

  @MockitoBean
  AiRecommendationClient ai;

  @Autowired
  RecommendationGeneration generation;

  Long memberId, otherId, javaTag, literatureTag;
  List<Long> bookIds;
  LoginSession session;

  @BeforeEach
  void setup() {
    for (String table : List.of(
      "recommendation_feedback",
      "recommendation_item",
      "recommendation_batch",
      "book_tag",
      "reader_interest",
      "reader_settings",
      "borrow_record",
      "book_copy",
      "book",
      "book_category",
      "member"
    ))
      jdbc.update("delete from " + table);
    jdbc.update("update role set status='ACTIVE'");
    Long roleId = jdbc.queryForObject(
      "select id from role where code='READER'",
      Long.class
    );
    jdbc.update(
      "insert into member(name,role_id) values('recommend-reader',?)",
      roleId
    );
    jdbc.update(
      "insert into member(name,role_id) values('recommend-other',?)",
      roleId
    );
    memberId = jdbc.queryForObject(
      "select id from member where name='recommend-reader'",
      Long.class
    );
    otherId = jdbc.queryForObject(
      "select id from member where name='recommend-other'",
      Long.class
    );
    jdbc.update(
      "insert into reader_settings(member_id,personalization_enabled) values(?,true)",
      memberId
    );
    javaTag = jdbc.queryForObject(
      "select id from interest_tag where code='JAVA'",
      Long.class
    );
    literatureTag = jdbc.queryForObject(
      "select id from interest_tag where code='LITERATURE'",
      Long.class
    );
    bookIds = new ArrayList<>();
    for (int category = 0; category < 3; category++) {
      jdbc.update(
        "insert into book_category(code,name) values(?,?)",
        "REC-" + category,
        "推荐分类" + category
      );
      Long categoryId = jdbc.queryForObject(
        "select id from book_category where code=?",
        Long.class,
        "REC-" + category
      );
      for (int n = 0; n < 4; n++) {
        String title = "测试书" + category + n;
        jdbc.update(
          "insert into book(category_id,title,author) values(?,?,'作者')",
          categoryId,
          title
        );
        Long id = jdbc.queryForObject(
          "select id from book where title=?",
          Long.class,
          title
        );
        bookIds.add(id);
        jdbc.update(
          "insert into book_tag(book_id,tag_id) values(?,?)",
          id,
          category == 0 ? javaTag : literatureTag
        );
        jdbc.update(
          "insert into book_copy(book_id,barcode,location) values(?,?,'测试书架')",
          id,
          "REC-" + id
        );
      }
    }
    session = new LoginSession(
      "test",
      memberId,
      roleId,
      MemberType.READER,
      "READER",
      "test@example.com",
      "测试"
    );
    LoginUserContext.set(session);
    reset(cache, ai);
    when(
      cache.queryWithPassThrough(
        anyString(),
        any(),
        any(),
        any(),
        anyLong(),
        any()
      )
    ).thenAnswer(call ->
      ((Function<Object, Object>) call.getArgument(3)).apply(
        call.getArgument(1)
      )
    );
  }

  @AfterEach
  void clear() {
    LoginUserContext.clear();
  }

  /** 仅返回前六本的反序；保留每分类最多两本，不向AI提供任何身份凭证。 */
  AiRerankResult valid(Map<String, Object> input) {
    var candidates = (List<Map<String, Object>>) input.get("candidates");
    List<AiRerankResult.Choice> selected = new ArrayList<>();
    for (int i = Math.min(6, candidates.size()) - 1; i >= 0; i--) {
      selected.add(
        new AiRerankResult.Choice(
          (Long) candidates.get(i).get("bookId"),
          "AI选择：符合候选主题"
        )
      );
    }
    return new AiRerankResult(selected);
  }

  @Test
  void validRerankPersistsAndReplaysWithoutCallingModel() {
    when(ai.rerank(anyMap())).thenAnswer(call -> {
      assertFalse(
        org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
      );
      Map<String, Object> input = call.getArgument(0);
      assertEquals(Set.of("interests", "candidates"), input.keySet());
      assertEquals(12, ((List<?>) input.get("candidates")).size());
      return valid(input);
    });
    var response = service.refresh("ai-valid-replay");
    assertEquals("AI", response.getSource());
    assertEquals("AI_RERANK_V1", response.getAlgorithmVersion());
    assertEquals(6, response.getItems().size());
    assertTrue(response.getItems().get(0).getReason().startsWith("AI选择"));
    assertNotNull(response.getItems().get(0).getScore());
    assertEquals(
      response.getBatchId(),
      service.refresh("ai-valid-replay").getBatchId()
    );
    verify(ai, times(1)).rerank(anyMap());
    verify(cache).deleteRequired("recommendation:current:" + memberId);
  }

  /** 使用真实Mapper验证关闭开关的整条刷新链路，而非仅验证算法布尔参数。 */
  @Test
  void disabledPreferenceReturnsPopularRulesWithoutModel() throws Exception {
    tags.replaceMyInterests(List.of(javaTag));
    jdbc.update(
      "update reader_settings set personalization_enabled=false where member_id=?",
      memberId
    );
    var prepared = generation.prepare(memberId, "popular-prepare");
    assertTrue(((List<?>) prepared.input().get("interests")).isEmpty());
    assertFalse((Boolean) prepared.input().get("personalizationEnabled"));
    var result = service.refresh("popular-no-model");
    assertFalse(result.isPersonalizationEnabled());
    assertEquals(
      RecommendationState.POPULAR_ALGORITHM_VERSION,
      result.getAlgorithmVersion()
    );
    assertEquals("RULE", result.getSource());
    assertEquals(6, result.getItems().size());
    assertTrue(
      result
        .getItems()
        .stream()
        .noneMatch(i -> i.getReason().contains("兴趣"))
    );
    verifyNoInteractions(ai);
    mvc
      .perform(get("/me/recommendations"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.personalizationEnabled").value(false));
  }

  @Test
  void closingPreferenceDuringModelCallRejectsModelResult() {
    when(ai.rerank(anyMap())).thenAnswer(call -> {
      jdbc.update(
        "update reader_settings set personalization_enabled=false where member_id=?",
        memberId
      );
      return valid(call.getArgument(0));
    });
    var result = service.refresh("popular-in-flight");
    assertFalse(result.isPersonalizationEnabled());
    assertEquals("RULE", result.getSource());
    assertTrue(
      result
        .getItems()
        .stream()
        .noneMatch(i -> i.getReason().contains("兴趣"))
    );
  }

  @Test
  void disabledMemberDoesNotSeeLegacyPersonalizedBatch() {
    service.refresh("legacy-personalized");
    // 模拟升级前已经关闭偏好但遗留推荐快照的情况。
    jdbc.update(
      "update reader_settings set personalization_enabled=false where member_id=?",
      memberId
    );
    var current = service.current();
    assertFalse(current.isPersonalizationEnabled());
    assertNull(current.getBatchId());
    assertTrue(current.getItems().isEmpty());
  }

  /** 走人员HTTP入口，验证开关修改会失效数据库批次和推荐缓存，并可再次开启。 */
  @Test
  void preferenceHttpToggleInvalidatesCurrentAndRestoresPersonalizedMode()
    throws Exception {
    service.refresh("toggle-initial");
    reset(ai);
    mvc
      .perform(
        put("/members/" + memberId)
          .contentType("application/json")
          .content(
            "{\"memberType\":\"READER\",\"personalizationEnabled\":false,\"expectedVersion\":0}"
          )
      )
      .andExpect(status().isOk());
    assertNull(service.current().getBatchId());
    assertFalse(service.current().isPersonalizationEnabled());
    var popular = service.refresh("toggle-disabled");
    assertEquals(
      RecommendationState.POPULAR_ALGORITHM_VERSION,
      popular.getAlgorithmVersion()
    );
    verifyNoInteractions(ai);
    mvc
      .perform(
        put("/members/" + memberId)
          .contentType("application/json")
          .content(
            "{\"memberType\":\"READER\",\"personalizationEnabled\":true,\"expectedVersion\":1}"
          )
      )
      .andExpect(status().isOk());
    assertNull(service.current().getBatchId());
    assertTrue(service.current().isPersonalizationEnabled());
    when(ai.rerank(anyMap())).thenAnswer(call -> valid(call.getArgument(0)));
    assertEquals("AI", service.refresh("toggle-enabled").getSource());
  }

  @Test
  void invalidIdsFallBackWholeBatch() {
    when(ai.rerank(anyMap())).thenReturn(
      new AiRerankResult(
        java.util.Collections.nCopies(
          6,
          new AiRerankResult.Choice(999999L, "错误ID")
        )
      )
    );
    var response = service.refresh("ai-invalid-id");
    assertEquals("RULE", response.getSource());
    assertEquals(6, response.getItems().size());
    assertTrue(
      response
        .getItems()
        .stream()
        .noneMatch(i -> i.getReason().equals("错误ID"))
    );
  }

  @Test
  void unavailableModelPreservesExistingRuleBehavior() {
    var response = service.refresh("ai-unavailable");
    assertEquals("RULE", response.getSource());
    assertEquals(6, response.getItems().size());
  }

  /** 馆藏不足六本时全部推荐；没有可用候选时不调用模型。 */
  @Test
  void insufficientAndEmptyCandidatesRemainValid() {
    jdbc.update(
      "update book set status='DISABLED' where id<>?",
      bookIds.get(0)
    );
    when(ai.rerank(anyMap())).thenAnswer(call -> valid(call.getArgument(0)));
    var one = service.refresh("ai-single-candidate");
    assertEquals("AI", one.getSource());
    assertEquals(1, one.getItems().size());
    jdbc.update("update book set status='DISABLED'");
    reset(ai);
    var empty = service.refresh("ai-no-candidate");
    assertEquals("RULE", empty.getSource());
    assertTrue(empty.getItems().isEmpty());
    verifyNoInteractions(ai);
  }

  @Test
  void changingInterestsDuringModelCallUsesFreshRules() {
    when(ai.rerank(anyMap())).thenAnswer(call -> {
      tags.replaceMyInterests(List.of(javaTag));
      return valid(call.getArgument(0));
    });
    var result = service.refresh("ai-changing-interests");
    assertEquals("RULE", result.getSource());
    assertTrue(result.getItems().get(0).getReason().contains("Java"));
  }

  @Test
  void disablingCandidateDuringInferenceNeverSavesIt() {
    when(ai.rerank(anyMap())).thenAnswer(call -> {
      var result = valid(call.getArgument(0));
      jdbc.update(
        "update book set status='DISABLED' where id=?",
        result.items().get(0).bookId()
      );
      return result;
    });
    var result = service.refresh("ai-changing-book");
    assertEquals("RULE", result.getSource());
    assertEquals(6, result.getItems().size());
    assertTrue(
      result
        .getItems()
        .stream()
        .allMatch(i -> i.getBook().getStatus().name().equals("ACTIVE"))
    );
  }

  @Test
  void roleDisabledDuringInferenceCannotPublish() {
    when(ai.rerank(anyMap())).thenAnswer(call -> {
      jdbc.update(
        "update role set status='DISABLED' where id=?",
        session.getRoleId()
      );
      return valid(call.getArgument(0));
    });
    try {
      assertThrows(ForbiddenException.class, () ->
        service.refresh("ai-revoked-role")
      );
      assertNull(mapper.current(memberId));
    } finally {
      jdbc.update(
        "update role set status='ACTIVE' where id=?",
        session.getRoleId()
      );
    }
  }

  @Test
  void cacheFailureRollsBackEntireAiBatch() {
    when(ai.rerank(anyMap())).thenAnswer(call -> valid(call.getArgument(0)));
    doThrow(new ServiceUnavailableException("测试删除失败"))
      .when(cache)
      .deleteRequired(anyString());
    assertThrows(ServiceUnavailableException.class, () ->
      service.refresh("ai-cache-rollback")
    );
    assertNull(mapper.current(memberId));
    assertEquals(
      0,
      jdbc.queryForObject(
        "select count(*) from recommendation_item",
        Integer.class
      )
    );
  }

  @Test
  void modelWaitDoesNotBlockCurrentOrInterestWriteAndSameRequestCommitsOnce()
    throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(ai.rerank(anyMap())).thenAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(8, TimeUnit.SECONDS));
      return valid(call.getArgument(0));
    });
    var workers = Executors.newFixedThreadPool(2);
    try {
      Callable<RecommendationResponse> task = () -> {
        LoginUserContext.set(session);
        try {
          return service.refresh("ai-concurrent-request");
        } finally {
          LoginUserContext.clear();
        }
      };
      var first = workers.submit(task);
      var second = workers.submit(task);
      assertTrue(entered.await(8, TimeUnit.SECONDS));
      assertTimeout(java.time.Duration.ofSeconds(2), () -> {
        service.current();
        tags.replaceMyInterests(List.of(javaTag));
      });
      release.countDown();
      var a = first.get(12, TimeUnit.SECONDS);
      var b = second.get(12, TimeUnit.SECONDS);
      assertEquals(a.getBatchId(), b.getBatchId());
      assertEquals("RULE", a.getSource());
      assertEquals(1, mapper.batches(memberId).size());
    } finally {
      release.countDown();
      workers.shutdownNow();
    }
  }

  @Test
  void mockHttpReturnsAiAndCurrentQueryKeepsResponseShape() throws Exception {
    when(ai.rerank(anyMap())).thenAnswer(call -> valid(call.getArgument(0)));
    mvc
      .perform(
        post("/me/recommendations/refresh").header(
          "Idempotency-Key",
          "ai-mock-http"
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.source").value("AI"))
      .andExpect(jsonPath("$.data.items.length()").value(6));
    mvc
      .perform(get("/me/recommendations"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.source").value("AI"));
  }
}
