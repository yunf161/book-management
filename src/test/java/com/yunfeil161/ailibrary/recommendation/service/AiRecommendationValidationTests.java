package com.yunfeil161.ailibrary.recommendation.service;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.recommendation.dto.AiRerankResult;
import com.yunfeil161.ailibrary.recommendation.entity.RecommendationItem;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Java必须独立把关，不信任Python服务已经校验过。 */
class AiRecommendationValidationTests {

  RecommendationGeneration.Prepared prepared(int categories) {
    List<RecommendationItem> pool = new ArrayList<>();
    Map<Long, Long> mapping = new HashMap<>();
    for (long id = 1; id <= 12; id++) {
      var item = new RecommendationItem();
      item.setBookId(id);
      item.setScore(BigDecimal.valueOf(20));
      pool.add(item);
      mapping.put(id, (id - 1) % categories);
    }
    return new RecommendationGeneration.Prepared(
      "snapshot",
      pool,
      mapping,
      Map.of(),
      null
    );
  }

  AiRerankResult result(Long... ids) {
    return new AiRerankResult(
      Arrays.stream(ids)
        .map(id -> new AiRerankResult.Choice(id, "符合标签"))
        .toList()
    );
  }

  @Test
  void validOrderKeepsRuleScore() {
    var selected = RecommendationGeneration.validate(
      prepared(3),
      result(6L, 5L, 4L, 3L, 2L, 1L)
    );
    assertEquals(6L, selected.get(0).getBookId());
    assertEquals(BigDecimal.valueOf(20), selected.get(0).getScore());
  }

  @Test
  void anyInvalidEntryRejectsWholeBatch() {
    for (var value : Arrays.asList(
      null,
      new AiRerankResult(null),
      result(1L),
      result(1L, 2L, 3L, 4L, 5L, 99L),
      result(1L, 2L, 3L, 4L, 5L, 5L),
      result(1L, 2L, 3L, 4L, 5L, null)
    )) {
      assertNull(RecommendationGeneration.validate(prepared(3), value));
    }
  }

  @Test
  void blankLongAndNullReasonsAreInvalid() {
    for (String reason : Arrays.asList(" ", "x".repeat(301), null)) {
      var choices = new ArrayList<>(result(1L, 2L, 3L, 4L, 5L, 6L).items());
      choices.set(0, new AiRerankResult.Choice(1L, reason));
      assertNull(
        RecommendationGeneration.validate(
          prepared(3),
          new AiRerankResult(choices)
        )
      );
    }
  }

  @Test
  void diversityIsMandatoryWhenEnoughCategoriesExist() {
    assertNull(
      RecommendationGeneration.validate(
        prepared(3),
        result(1L, 4L, 7L, 2L, 3L, 6L)
      )
    );
  }

  @Test
  void scarceCategoriesCanRelax() {
    assertNotNull(
      RecommendationGeneration.validate(
        prepared(1),
        result(1L, 2L, 3L, 4L, 5L, 6L)
      )
    );
  }

  @Test
  void interestCoverageCannotBeIgnored() {
    var base = prepared(3);
    var books = new ArrayList<Map<String, Object>>();
    for (long id = 1; id <= 12; id++) {
      books.add(
        Map.of("bookId", id, "tags", List.of(id > 6 ? "Java" : "文学"))
      );
    }
    var snapshot = new RecommendationGeneration.Prepared(
      base.fingerprint(),
      base.pool(),
      base.categories(),
      Map.of("interests", List.of("Java"), "candidates", books),
      null
    );
    assertNull(
      RecommendationGeneration.validate(
        snapshot,
        result(1L, 2L, 3L, 4L, 5L, 6L)
      )
    );
    assertNotNull(
      RecommendationGeneration.validate(
        snapshot,
        result(7L, 8L, 3L, 4L, 5L, 6L)
      )
    );
  }
}
