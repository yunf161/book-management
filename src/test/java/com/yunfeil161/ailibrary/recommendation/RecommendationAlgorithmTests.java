package com.yunfeil161.ailibrary.recommendation;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.recommendation.entity.*;
import com.yunfeil161.ailibrary.recommendation.service.RuleRecommendationAlgorithm;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 规则权重、反馈窗口、冷启动与不足六本时的算法边界。 */
class RecommendationAlgorithmTests {

  /** 扩大候选池不改变原先六本规则降级结果，且只保留最多二十本。 */
  @Test
  void largerPoolPreservesRuleFallbackAndLimit() {
    var candidates = new ArrayList<RecommendationCandidate>();
    for (long id = 1; id <= 30; id++) candidates.add(book(id, id, 1, 0));
    var original = select(candidates, List.of(), true);
    var pool = algorithm.selectCandidates(
      candidates,
      Map.of(),
      Set.of(),
      Set.of(),
      Set.of(),
      List.of(),
      true,
      1L,
      "algorithm-key",
      now,
      20
    );
    assertEquals(20, pool.size());
    assertEquals(
      original.stream().map(RecommendationItem::getBookId).toList(),
      pool.subList(0, 6).stream().map(RecommendationItem::getBookId).toList()
    );
  }

  final LocalDateTime now = LocalDateTime.of(2026, 10, 11, 12, 0);
  final RuleRecommendationAlgorithm algorithm =
    new RuleRecommendationAlgorithm();

  private RecommendationCandidate book(
    long id,
    long popularity,
    long available,
    int age
  ) {
    var b = new RecommendationCandidate();
    b.setId(id);
    b.setCategoryId(id);
    b.setPopularity(popularity);
    b.setAvailableCopies(available);
    b.setCreatedAt(now.minusDays(age));
    return b;
  }

  private RecommendationFeedback feedback(long id, String kind, int age) {
    var f = new RecommendationFeedback();
    f.setBookId(id);
    f.setKind(kind);
    f.setCreatedAt(now.minusDays(age));
    return f;
  }

  private List<RecommendationItem> select(
    List<RecommendationCandidate> books,
    List<RecommendationFeedback> feedback,
    boolean personalized
  ) {
    return algorithm.select(
      books,
      Map.of(),
      Set.of(),
      Set.of(),
      Set.of(),
      feedback,
      personalized,
      1L,
      "algorithm-key",
      now
    );
  }

  @Test
  void exactScoreForPopularNewAvailableColdStart() {
    assertEquals(
      30.0,
      select(List.of(book(1, 10, 1, 0)), List.of(), true)
        .get(0)
        .getScore()
        .doubleValue(),
      0.0001
    );
  }

  @Test
  void matchingTagsHaveSeventyPointWeight() {
    var tag = new InterestTag();
    tag.setId(10L);
    tag.setName("Java");
    var selected = algorithm.select(
      List.of(book(1, 0, 1, 0)),
      Map.of(1L, List.of(tag)),
      Set.of(10L),
      Set.of(),
      Set.of(),
      List.of(),
      true,
      1L,
      "test-key",
      now
    );
    assertEquals(85.0, selected.get(0).getScore().doubleValue(), 0.0001);
    assertTrue(selected.get(0).getReason().contains("Java"));
  }

  /** 即使调用者传入兴趣，关闭开关也不能改变分数、理由与排序。 */
  @Test
  void disabledPersonalizationIgnoresTagsAndHistory() {
    var tag = new InterestTag();
    tag.setId(10L);
    tag.setName("Java");
    var selected = algorithm.select(
      List.of(book(1, 100, 1, 0), book(2, 0, 0, 90)),
      Map.of(2L, List.of(tag)),
      Set.of(10L),
      Set.of(),
      Set.of(1L),
      List.of(feedback(1, "ALREADY_READ", 1)),
      false,
      1L,
      "popular-test",
      now
    );
    assertEquals(1L, selected.get(0).getBookId());
    assertEquals(30.0, selected.get(0).getScore().doubleValue(), 0.0001);
    assertEquals(0.0, selected.get(1).getScore().doubleValue(), 0.0001);
    assertTrue(
      selected.stream().noneMatch(i -> i.getReason().contains("兴趣"))
    );
  }

  @Test
  void dislikeExpiresAtNinetyDays() {
    assertTrue(
      select(
        List.of(book(1, 0, 1, 0)),
        List.of(feedback(1, "NOT_INTERESTED", 89)),
        true
      ).isEmpty()
    );
    assertEquals(
      1,
      select(
        List.of(book(1, 0, 1, 0)),
        List.of(feedback(1, "NOT_INTERESTED", 90)),
        true
      ).size()
    );
  }

  @Test
  void difficultPenaltyExpiresAtThirtyDays() {
    assertEquals(
      0.0,
      select(
        List.of(book(1, 0, 1, 0)),
        List.of(feedback(1, "TOO_DIFFICULT", 29)),
        true
      )
        .get(0)
        .getScore()
        .doubleValue()
    );
    assertEquals(
      15.0,
      select(
        List.of(book(1, 0, 1, 0)),
        List.of(feedback(1, "TOO_DIFFICULT", 30)),
        true
      )
        .get(0)
        .getScore()
        .doubleValue()
    );
  }

  @Test
  void readExclusionDependsOnRetainedFeedbackAndPreference() {
    var books = List.of(book(1, 0, 1, 0));
    var feedback = List.of(feedback(1, "ALREADY_READ", 365));
    assertTrue(select(books, feedback, true).isEmpty());
    assertEquals(1, select(books, feedback, false).size());
  }

  @Test
  void scarceCatalogReturnsAvailableCountAndStableOrder() {
    var books = List.of(book(1, 0, 1, 0), book(2, 0, 1, 0), book(3, 0, 1, 0));
    var first = select(books, List.of(), true)
      .stream()
      .map(RecommendationItem::getBookId)
      .toList();
    assertEquals(3, first.size());
    assertEquals(
      first,
      select(books, List.of(), true)
        .stream()
        .map(RecommendationItem::getBookId)
        .toList()
    );
  }

  @Test
  void recentBooksAreAvoidedBeforeRepeating() {
    var selected = algorithm.select(
      List.of(book(1, 100, 1, 0), book(2, 0, 0, 90)),
      Map.of(),
      Set.of(),
      Set.of(),
      Set.of(1L),
      List.of(),
      true,
      1L,
      "test-key",
      now
    );
    assertEquals(2L, selected.get(0).getBookId());
  }
}
