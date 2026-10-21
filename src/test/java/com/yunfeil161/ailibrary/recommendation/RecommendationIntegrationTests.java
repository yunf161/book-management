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
  properties = "spring.datasource.url=jdbc:h2:mem:recommendation_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
class RecommendationIntegrationTests {

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
    reset(cache);
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

  /** 修改偏好后的失效快照不能继续压低新标签对应图书的排序。 */
  @Test
  void invalidatedPreferenceBatchIsNotRecentHistory() {
    service.refresh("before-preference-change");
    tags.replaceMyInterests(List.of(javaTag));
    assertTrue(mapper.recentBooks(memberId).isEmpty());
    assertTrue(
      service
        .refresh("after-preference-change")
        .getItems()
        .get(0)
        .getReason()
        .contains("Java")
    );
  }

  /** 两个成员同时提交旧DTO的同名新兴趣时，共享一个启用标签，不产生重复字典。 */
  @Test
  void concurrentLegacyNewInterestSharesOneTag() throws Exception {
    jdbc.update("insert into reader_settings(member_id) values(?)", otherId);
    String name = "新增兴趣" + UUID.randomUUID().toString().substring(0, 8);
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (Long id : List.of(memberId, otherId)) {
        futures.add(
          pool.submit(() -> {
            gate.await();
            var reader = new com.yunfeil161.ailibrary.identity.entity.Reader();
            reader.setMemberType(MemberType.READER);
            memberService.updateReader(id, reader, List.of(name), 0L);
            return null;
          })
        );
      }
      gate.countDown();
      for (var future : futures) future.get(15, TimeUnit.SECONDS);
      assertEquals(
        1,
        jdbc.queryForObject(
          "select count(*) from interest_tag where name=?",
          Integer.class,
          name
        )
      );
      assertEquals(
        2,
        jdbc.queryForObject(
          "select count(*) from reader_interest where interest_name=? and tag_id is not null",
          Integer.class,
          name
        )
      );
    } finally {
      pool.shutdownNow();
    }
  }

  /** 三种反馈各自幂等，当前条目反馈最多18条；覆盖后只留6条历史记录。 */
  @Test
  void allFeedbackKindsRemainBoundedWhenBatchRotates() {
    var current = service.refresh("all-feedback-kinds");
    for (var entry : current.getItems()) {
      for (String kind : List.of(
        "NOT_INTERESTED",
        "ALREADY_READ",
        "TOO_DIFFICULT"
      )) {
        service.feedback(entry.getItemId(), feedback(kind));
      }
    }
    assertEquals(18, mapper.feedback(memberId).size());
    service.refresh("rotate-all-feedback-kinds");
    assertEquals(6, mapper.historicalFeedbackIds(memberId).size());
  }

  private FeedbackRequest feedback(String kind) {
    var f = new FeedbackRequest();
    f.setKind(kind);
    return f;
  }

  @Test
  void coldStartSixUniqueAndCategoryDiversity() {
    assertTrue(service.current().getItems().isEmpty());
    var r = service.refresh("cold-start-001");
    assertEquals(6, r.getItems().size());
    assertEquals(
      6,
      r
        .getItems()
        .stream()
        .map(e -> e.getBook().getId())
        .distinct()
        .count()
    );
    assertEquals("RULE_V1", r.getAlgorithmVersion());
    var counts = r
      .getItems()
      .stream()
      .collect(
        java.util.stream.Collectors.groupingBy(
          e -> e.getBook().getCategoryId(),
          java.util.stream.Collectors.counting()
        )
      );
    assertTrue(
      counts
        .values()
        .stream()
        .allMatch(n -> n <= 2)
    );
  }

  @Test
  void interestsPrioritizeMatchingAndChangesInvalidate() {
    tags.replaceMyInterests(List.of(javaTag));
    var r = service.refresh("interest-001");
    assertTrue(r.getItems().get(0).getReason().contains("Java"));
    assertTrue(
      r
        .getItems()
        .get(0)
        .getBook()
        .getTags()
        .stream()
        .anyMatch(t -> t.getId().equals(javaTag))
    );
    tags.replaceMyInterests(List.of(literatureTag));
    assertTrue(service.current().getItems().isEmpty());
    tags.replaceMyInterests(List.of());
    assertTrue(tags.myInterests().isEmpty());
  }

  @Test
  void feedbackIdempotentHiddenAndExcludedOnRefresh() {
    var r = service.refresh("feedback-001");
    var item = r.getItems().get(0);
    assertEquals(
      5,
      service
        .feedback(item.getItemId(), feedback("NOT_INTERESTED"))
        .getItems()
        .size()
    );
    service.feedback(item.getItemId(), feedback("NOT_INTERESTED"));
    assertEquals(1, mapper.feedback(memberId).size());
    assertTrue(
      service
        .refresh("feedback-002")
        .getItems()
        .stream()
        .noneMatch(e -> e.getBook().getId().equals(item.getBook().getId()))
    );
  }

  @Test
  void retainsThreeBatchesAndSixHistoricalFeedback() {
    for (int round = 0; round < 5; round++) for (var item : service
      .refresh("retention-" + round)
      .getItems())
      service.feedback(item.getItemId(), feedback("TOO_DIFFICULT"));
    assertEquals(3, mapper.batches(memberId).size());
    assertEquals(6, mapper.historicalFeedbackIds(memberId).size());
    assertTrue(
      jdbc.queryForObject(
        "select count(*) from recommendation_item",
        Integer.class
      ) <= 18
    );
    assertTrue(mapper.feedback(memberId).size() <= 12);
    assertEquals(
      0,
      jdbc.queryForObject(
        "select count(*) from recommendation_item i left join recommendation_batch b on b.id=i.batch_id where b.id is null",
        Integer.class
      )
    );
  }

  @Test
  void repeatedRefreshDoesNotExposeHistory() {
    var first = service.refresh("idempotent-001");
    assertEquals(
      first.getBatchId(),
      service.refresh("idempotent-001").getBatchId()
    );
    service.refresh("idempotent-002");
    assertThrows(ConflictException.class, () ->
      service.refresh("idempotent-001")
    );
    assertEquals(2, mapper.batches(memberId).size());
  }

  @Test
  void oldAndOtherMemberFeedbackRejected() {
    var first = service.refresh("ownership-001");
    final Long oldId = first.getItems().get(0).getItemId();
    service.refresh("ownership-002");
    assertThrows(NotFoundException.class, () ->
      service.feedback(oldId, feedback("ALREADY_READ"))
    );
    final Long currentId = service.current().getItems().get(0).getItemId();
    LoginUserContext.set(
      new LoginSession(
        "other",
        otherId,
        session.getRoleId(),
        MemberType.READER,
        "READER",
        "other@example.com",
        "other"
      )
    );
    assertThrows(NotFoundException.class, () ->
      service.feedback(currentId, feedback("ALREADY_READ"))
    );
    assertTrue(service.current().getItems().isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = { "short", "bad key!", "中文请求ID" })
  void invalidRefresh(String id) {
    assertThrows(WrongFormatException.class, () -> service.refresh(id));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = { "LIKE", "already_read" })
  void invalidFeedback(String kind) {
    assertThrows(WrongFormatException.class, () ->
      service.feedback(1L, feedback(kind))
    );
  }

  @Test
  void tagValidationAndDeduplication() {
    assertThrows(WrongFormatException.class, () ->
      tags.replaceMyInterests(null)
    );
    assertThrows(WrongFormatException.class, () ->
      tags.replaceMyInterests(Arrays.asList(javaTag, null))
    );
    assertThrows(NotFoundException.class, () ->
      tags.replaceMyInterests(List.of(Long.MAX_VALUE))
    );
    jdbc.update(
      "update interest_tag set status='DISABLED' where id=?",
      javaTag
    );
    try {
      assertThrows(ConflictException.class, () ->
        tags.replaceMyInterests(List.of(javaTag))
      );
    } finally {
      jdbc.update(
        "update interest_tag set status='ACTIVE' where id=?",
        javaTag
      );
    }
    assertEquals(1, tags.replaceMyInterests(List.of(javaTag, javaTag)).size());
  }

  @Test
  void noLoginDisabledRoleAndAiRejected() {
    LoginUserContext.clear();
    assertThrows(UnauthorizedException.class, service::current);
    LoginUserContext.set(session);
    jdbc.update(
      "update role set status='DISABLED' where id=?",
      session.getRoleId()
    );
    try {
      assertThrows(ForbiddenException.class, service::current);
    } finally {
      jdbc.update(
        "update role set status='ACTIVE' where id=?",
        session.getRoleId()
      );
    }
    session.setMemberType(MemberType.AI_LIBRARIAN);
    assertThrows(ForbiddenException.class, service::current);
  }

  @Test
  void borrowedDisabledExcludedInventoryAndCoverFresh() {
    Long borrowed = bookIds.get(0);
    Long copyId = jdbc.queryForObject(
      "select id from book_copy where book_id=?",
      Long.class,
      borrowed
    );
    jdbc.update(
      "insert into borrow_record(application_id,member_id,copy_id,borrowed_at,due_at,status,approved_by) values(999,?,?,current_timestamp,dateadd('DAY',7,current_timestamp),'BORROWING',?)",
      memberId,
      copyId,
      memberId
    );
    jdbc.update("update book set status='DISABLED' where id=?", bookIds.get(1));
    var r = service.refresh("borrowed-001");
    assertTrue(
      r
        .getItems()
        .stream()
        .noneMatch(
          e ->
            e.getBook().getId().equals(borrowed) ||
            e.getBook().getId().equals(bookIds.get(1))
        )
    );
    Long shown = r.getItems().get(0).getBook().getId();
    jdbc.update(
      "update book_copy set status='MAINTENANCE' where book_id=?",
      shown
    );
    jdbc.update(
      "update book set cover_object_key='library/covers/test.png' where id=?",
      shown
    );
    var updated = service
      .current()
      .getItems()
      .stream()
      .filter(e -> e.getBook().getId().equals(shown))
      .findFirst()
      .orElseThrow()
      .getBook();
    assertEquals(0, updated.getAvailableCopies());
    assertEquals("/books/" + shown + "/cover/content", updated.getCoverUrl());
  }

  @Test
  void concurrentSameRequestCreatesOneBatch() throws Exception {
    var pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<Long>> futures = new ArrayList<>();
      for (int n = 0; n < 6; n++) futures.add(
        pool.submit(() -> {
          LoginUserContext.set(session);
          try {
            return service.refresh("concurrent-001").getBatchId();
          } finally {
            LoginUserContext.clear();
          }
        })
      );
      Set<Long> ids = new HashSet<>();
      for (var f : futures) ids.add(f.get(15, TimeUnit.SECONDS));
      assertEquals(1, ids.size());
      assertEquals(1, mapper.batches(memberId).size());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedCacheDeletionRollsBackBatchAndInterests() {
    doThrow(new ServiceUnavailableException("测试缓存故障"))
      .when(cache)
      .deleteRequired(anyString());
    assertThrows(ServiceUnavailableException.class, () ->
      service.refresh("rollback-001")
    );
    assertTrue(mapper.batches(memberId).isEmpty());
    assertThrows(ServiceUnavailableException.class, () ->
      tags.replaceMyInterests(List.of(javaTag))
    );
    assertTrue(tags.myInterests().isEmpty());
  }

  @Test
  void httpQueryRefreshFeedbackAndTagFilter() throws Exception {
    mvc
      .perform(get("/me/recommendations"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items").isEmpty());
    mvc
      .perform(
        post("/me/recommendations/refresh").header(
          "Idempotency-Key",
          "http-test-001"
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items.length()").value(6));
    mvc
      .perform(get("/interest-tags").param("code", "JAVA"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].code").value("JAVA"));
    mvc
      .perform(post("/me/recommendations/refresh"))
      .andExpect(status().isBadRequest());
    Long item = service.current().getItems().get(0).getItemId();
    mvc
      .perform(
        post("/me/recommendations/" + item + "/feedback")
          .contentType("application/json")
          .content("{\"kind\":\"ALREADY_READ\"}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items.length()").value(5));
  }
}
