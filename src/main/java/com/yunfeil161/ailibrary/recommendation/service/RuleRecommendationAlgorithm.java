package com.yunfeil161.ailibrary.recommendation.service;

import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.recommendation.entity.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * RULE_V1：标签 Dice 70分、近30天借阅热度15分、90天新书10分、库存5分。
 * 无兴趣时自然退化为热门、新书和分类多样性，不依赖任何模型服务。
 */
@Component
public class RuleRecommendationAlgorithm {

  public static final int RECOMMENDATION_SIZE = 6;

  public List<RecommendationItem> select(
    List<RecommendationCandidate> candidates,
    Map<Long, List<InterestTag>> tags,
    Set<Long> interests,
    Set<Long> borrowed,
    Set<Long> recent,
    List<RecommendationFeedback> feedback,
    boolean personalization,
    Long memberId,
    String requestId,
    LocalDateTime now
  ) {
    return selectCandidates(
      candidates,
      tags,
      interests,
      borrowed,
      recent,
      feedback,
      personalization,
      memberId,
      requestId,
      now,
      RECOMMENDATION_SIZE
    );
  }

  /** 同一规则产生更大的合法候选池；前六本仍是完整的规则降级结果。 */
  public List<RecommendationItem> selectCandidates(
    List<RecommendationCandidate> candidates,
    Map<Long, List<InterestTag>> tags,
    Set<Long> interests,
    Set<Long> borrowed,
    Set<Long> recent,
    List<RecommendationFeedback> feedback,
    boolean personalization,
    Long memberId,
    String requestId,
    LocalDateTime now,
    int limit
  ) {
    long maxPopularity = candidates
      .stream()
      .mapToLong(c -> c.getPopularity())
      .max()
      .orElse(0);
    List<Scored> ranked = new ArrayList<>();
    for (RecommendationCandidate candidate : candidates) {
      if (borrowed.contains(candidate.getId())) continue;
      List<InterestTag> bookTags = tags.getOrDefault(
        candidate.getId(),
        List.of()
      );
      List<String> matched = bookTags
        .stream()
        .filter(t -> personalization && interests.contains(t.getId()))
        .map(InterestTag::getName)
        .toList();
      double similarity =
        interests.isEmpty() || bookTags.isEmpty()
          ? 0
          : (2.0 * matched.size()) / (interests.size() + bookTags.size());
      boolean excluded = false;
      double penalty = 0;
      // 关闭个性化时不使用历史或历史反馈；当前条目反馈仍会立即隐藏。
      if (personalization) {
        for (RecommendationFeedback f : feedback) {
          if (!candidate.getId().equals(f.getBookId())) continue;
          long days = ChronoUnit.DAYS.between(f.getCreatedAt(), now);
          if (
            "ALREADY_READ".equals(f.getKind()) ||
            ("NOT_INTERESTED".equals(f.getKind()) && days < 90)
          ) excluded = true;
          if ("TOO_DIFFICULT".equals(f.getKind()) && days < 30) penalty = 35;
        }
      }
      if (excluded) continue;
      double popularity =
        maxPopularity == 0
          ? 0
          : Math.log1p(candidate.getPopularity()) / Math.log1p(maxPopularity);
      double novelty = Math.max(
        0,
        1 -
          Math.max(0, ChronoUnit.DAYS.between(candidate.getCreatedAt(), now)) /
            90.0
      );
      double score = Math.max(
        0,
        70 * similarity +
          15 * popularity +
          10 * novelty +
          (candidate.getAvailableCopies() > 0 ? 5 : 0) -
          penalty
      );
      RecommendationItem item = new RecommendationItem();
      item.setBookId(candidate.getId());
      item.setScore(
        BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP)
      );
      item.setReason(
        matched.isEmpty()
          ? "根据馆藏热度、新书与分类多样性推荐"
          : "符合你选择的兴趣：" + String.join("、", matched)
      );
      item.setEvidenceJson(
        JsonUtils.toJson(
          Map.of(
            "tagSimilarity",
            similarity,
            "popularity",
            candidate.getPopularity(),
            "novelty",
            novelty,
            "available",
            candidate.getAvailableCopies() > 0,
            "feedbackPenalty",
            penalty
          )
        )
      );
      // 相同分数按请求种子稳定打散；同一幂等请求的结果不会随机漂移。
      ranked.add(
        new Scored(
          candidate.getCategoryId(),
          item,
          personalization && recent.contains(candidate.getId()),
          Objects.hash(memberId, requestId, candidate.getId())
        )
      );
    }
    ranked.sort(
      Comparator.comparing(Scored::recent)
        .thenComparing(
          (Scored s) -> s.item().getScore(),
          Comparator.reverseOrder()
        )
        .thenComparingInt(Scored::tie)
        .thenComparing(s -> s.item().getBookId())
    );
    List<RecommendationItem> selected = new ArrayList<>();
    Map<Long, Integer> categoryCounts = new HashMap<>();
    Set<Long> selectedIds = new HashSet<>();
    // 第一遍每分类最多两本；不足六本时放宽分类限制，不回填已借或被排除的书。
    for (boolean relax : new boolean[] { false, true }) {
      for (Scored scored : ranked) {
        if (selected.size() == limit) break;
        if (selectedIds.contains(scored.item().getBookId())) continue;
        if (
          !relax && categoryCounts.getOrDefault(scored.categoryId(), 0) >= 2
        ) continue;
        selected.add(scored.item());
        selectedIds.add(scored.item().getBookId());
        categoryCounts.merge(scored.categoryId(), 1, Integer::sum);
      }
    }
    for (int i = 0; i < selected.size(); i++) selected.get(i).setRank(i + 1);
    return selected;
  }

  private record Scored(
    Long categoryId,
    RecommendationItem item,
    boolean recent,
    int tie
  ) {}
}
