package com.yunfeil161.ailibrary.recommendation.service;

import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.recommendation.mapper.RecommendationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 调用方已经持有成员推荐写锁并开启事务时使用。
 * 缓存删除在事务内重试；最终失败会使数据库修改回滚。
 */
@Component
@RequiredArgsConstructor
public class RecommendationState {

  public static final String CACHE_PREFIX = "recommendation:current:";
  public static final String POPULAR_ALGORITHM_VERSION = "POPULAR_V1";
  public static final String LOCK_PREFIX =
    "lock:resource:recommendation:member:";
  public static final int MAX_BATCHES = 3;
  public static final int MAX_HISTORICAL_FEEDBACK = 6;
  private final RecommendationMapper mapper;
  private final CacheClient cache;

  /** 在偏好写入前比较开关，姓名、手机及相同偏好值不应清空推荐。 */
  public boolean personalizationChanged(Long memberId, Boolean requested) {
    return (
      requested != null &&
      !java.util.Objects.equals(
        requested,
        mapper.personalizationEnabled(memberId)
      )
    );
  }

  /** 兴趣或个性化开关变化后不能继续返回原偏好生成的推荐。 */
  public void invalidate(Long memberId) {
    mapper.invalidate(memberId);
    mapper.archiveFeedback(memberId);
    trimFeedback(memberId);
    cache.deleteRequired(CACHE_PREFIX + memberId);
  }

  /** 每次生成直接删除最旧批次及其条目，不复用条目 ID。 */
  public void trim(Long memberId) {
    var batches = mapper.batches(memberId);
    for (int i = MAX_BATCHES; i < batches.size(); i++) {
      mapper.deleteItems(batches.get(i).getId());
      mapper.deleteBatch(batches.get(i).getId());
    }
    trimFeedback(memberId);
  }

  private void trimFeedback(Long memberId) {
    var ids = mapper.historicalFeedbackIds(memberId);
    for (
      int i = MAX_HISTORICAL_FEEDBACK;
      i < ids.size();
      i++
    ) mapper.deleteFeedback(ids.get(i));
  }
}
