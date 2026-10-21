package com.yunfeil161.ailibrary.recommendation.service;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.*;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.*;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.recommendation.dto.*;
import com.yunfeil161.ailibrary.recommendation.entity.*;
import com.yunfeil161.ailibrary.recommendation.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前推荐查询、显式刷新和反馈。请求不接受目标成员，始终从会话获取归属。 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

  private final RecommendationMapper mapper;
  private final TagMapper tags;
  private final BookMapper books;
  private final PermissionAccess access;
  private final CacheClient cache;
  private final RecommendationState state;
  private final RecommendationGeneration generation;
  private final AiRecommendationClient ai;

  @DistributedLock(
    keys = "'lock:resource:recommendation:member:' + #currentMemberId",
    mode = LockMode.READ
  )
  public RecommendationResponse current() {
    Long memberId = human(PermissionCodes.RECOMMENDATION_QUERY).getMemberId();
    RecommendationSnapshot snapshot = cache.queryWithPassThrough(
      RecommendationState.CACHE_PREFIX,
      memberId,
      RecommendationSnapshot.class,
      this::snapshot,
      30L,
      TimeUnit.MINUTES
    );
    // Redis TTL有随机抖动，批次有效性仍以数据库快照的到期时间为准。
    return materialize(memberId, snapshot);
  }

  /** 模型调用发生在两个短事务之间，避免推理耗时阻塞反馈、查询和偏好修改。 */
  public RecommendationResponse refresh(String requestId) {
    Long memberId = human(PermissionCodes.RECOMMENDATION_REFRESH).getMemberId();
    if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{8,64}")) {
      throw new WrongFormatException(
        "Idempotency-Key须为8到64位字母、数字、下划线或短横线"
      );
    }
    var prepared = generation.prepare(memberId, requestId);
    if (prepared.existing() != null) return materialize(
      memberId,
      prepared.existing()
    );
    var decision =
      prepared.pool().isEmpty() ||
      !Boolean.TRUE.equals(prepared.input().get("personalizationEnabled"))
        ? null
        : ai.rerank(
            Map.of(
              "interests",
              prepared.input().get("interests"),
              "candidates",
              prepared.input().get("candidates")
            )
          );
    return materialize(
      memberId,
      generation.publish(memberId, requestId, prepared, decision)
    );
  }

  @Transactional
  @DistributedLock(
    keys = "'lock:resource:recommendation:member:' + #currentMemberId"
  )
  public RecommendationResponse feedback(Long itemId, FeedbackRequest request) {
    Long memberId = human(
      PermissionCodes.RECOMMENDATION_FEEDBACK
    ).getMemberId();
    if (
      itemId == null ||
      itemId <= 0 ||
      request == null ||
      request.getKind() == null ||
      !Set.of("NOT_INTERESTED", "ALREADY_READ", "TOO_DIFFICULT").contains(
        request.getKind()
      )
    ) {
      throw new WrongFormatException("推荐条目ID或反馈类型错误");
    }
    var snapshot = snapshot(memberId);
    if (snapshot == null) throw new NotFoundException("当前推荐不存在或已过期");
    var item = snapshot
      .getItems()
      .stream()
      .filter(i -> i.getId().equals(itemId))
      .findFirst()
      .orElseThrow(() ->
        new NotFoundException("推荐条目不存在或不属于本人当前批次")
      );
    boolean repeated = mapper
      .feedback(memberId)
      .stream()
      .anyMatch(
        f ->
          f.getItemId().equals(itemId) && f.getKind().equals(request.getKind())
      );
    if (!repeated) {
      var feedback = new RecommendationFeedback();
      feedback.setMemberId(memberId);
      feedback.setBatchId(snapshot.getBatch().getId());
      feedback.setItemId(itemId);
      feedback.setBookId(item.getBookId());
      feedback.setKind(request.getKind());
      feedback.setCreatedAt(LocalDateTime.now());
      if (mapper.insertFeedback(feedback) != 1) throw new IllegalStateException(
        "保存推荐反馈失败"
      );
    }
    cache.deleteRequired(RecommendationState.CACHE_PREFIX + memberId);
    return materialize(memberId, snapshot);
  }

  /** 消费者在同一资源写锁下读取最新批次，不信任排队事件中的历史批次数据。 */
  @DistributedLock(keys = "'lock:resource:recommendation:member:' + #memberId")
  public void warm(Long memberId) {
    if (memberId == null || memberId <= 0) throw new IllegalArgumentException(
      "无效预热成员"
    );
    RecommendationSnapshot latest = snapshot(memberId);
    if (latest == null) cache.delete(
      RecommendationState.CACHE_PREFIX + memberId
    );
    else cache.set(
      RecommendationState.CACHE_PREFIX + memberId,
      latest,
      30L,
      TimeUnit.MINUTES
    );
  }

  private RecommendationSnapshot snapshot(Long memberId) {
    var batch = mapper.current(memberId);
    if (
      batch == null || !batch.getExpiresAt().isAfter(LocalDateTime.now())
    ) return null;
    var snapshot = new RecommendationSnapshot();
    snapshot.setBatch(batch);
    snapshot.setItems(mapper.items(batch.getId()));
    return snapshot;
  }

  private RecommendationResponse materialize(
    Long memberId,
    RecommendationSnapshot snapshot
  ) {
    var result = new RecommendationResponse();
    // 空批次也返回真实模式，页面首次生成前即可正确展示热门/个性化文案。
    result.setPersonalizationEnabled(
      Boolean.TRUE.equals(mapper.personalizationEnabled(memberId))
    );
    if (
      snapshot == null ||
      !snapshot.getBatch().getExpiresAt().isAfter(LocalDateTime.now())
    ) return result;
    var batch = snapshot.getBatch();
    // 升级前关闭开关生成的批次可能仍使用过兴趣；不展示旧缓存，交由页面生成新版热门批次。
    if (
      !result.isPersonalizationEnabled() &&
      !RecommendationState.POPULAR_ALGORITHM_VERSION.equals(
        batch.getAlgorithmVersion()
      )
    ) return result;
    result.setBatchId(batch.getId());
    result.setSource(batch.getSource());
    result.setAlgorithmVersion(batch.getAlgorithmVersion());
    result.setGeneratedAt(batch.getGeneratedAt());
    result.setExpiresAt(batch.getExpiresAt());
    Set<Long> hidden = mapper
      .feedback(memberId)
      .stream()
      .filter(f -> f.getBatchId().equals(batch.getId()))
      .map(RecommendationFeedback::getItemId)
      .collect(Collectors.toSet());
    Set<Long> borrowed = new HashSet<>(mapper.borrowedBooks(memberId));
    for (var item : snapshot.getItems()) {
      if (
        hidden.contains(item.getId()) || borrowed.contains(item.getBookId())
      ) continue;
      var book = books.findDetailById(item.getBookId());
      if (book == null || book.getStatus() != CatalogStatus.ACTIVE) continue;
      book.setTags(tags.bookTags(book.getId()));
      var entry = new RecommendationResponse.Entry();
      entry.setItemId(item.getId());
      entry.setRank(item.getRank());
      entry.setScore(item.getScore());
      entry.setReason(item.getReason());
      entry.setBook(book);
      result.getItems().add(entry);
    }
    return result;
  }

  private LoginSession human(String permission) {
    LoginSession session = access.require(permission, DataScope.SELF);
    if (
      session.getMemberType() == MemberType.AI_LIBRARIAN
    ) throw new ForbiddenException("AI馆员暂不使用推荐功能");
    return session;
  }
}
