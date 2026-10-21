package com.yunfeil161.ailibrary.recommendation.service;

import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.*;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.config.security.permission.*;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.recommendation.dto.*;
import com.yunfeil161.ailibrary.recommendation.entity.*;
import com.yunfeil161.ailibrary.recommendation.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

/**
 * 将准备和提交拆成两个短事务。外部模型在两者之间执行，不持有数据库事务或资源锁。
 * 提交阶段重新读取规则输入，期间偏好、反馈、库存或当前批次变化时整批采用最新规则结果。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationGeneration {

  private final RecommendationMapper mapper;
  private final TagMapper tags;
  private final RuleRecommendationAlgorithm algorithm;
  private final RecommendationState state;
  private final CacheClient cache;
  private final RecommendationWarmPublisher publisher;
  private final PermissionAccess access;

  public record Prepared(
    String fingerprint,
    List<RecommendationItem> pool,
    Map<Long, Long> categories,
    Map<String, Object> input,
    RecommendationSnapshot existing
  ) {}

  @Transactional(readOnly = true)
  @DistributedLock(
    keys = "'lock:resource:recommendation:member:' + #memberId",
    mode = LockMode.READ
  )
  public Prepared prepare(Long memberId, String requestId) {
    authorize(memberId);
    RecommendationSnapshot existing = replay(memberId, requestId);
    if (existing != null) return new Prepared(
      null,
      List.of(),
      Map.of(),
      Map.of(),
      existing
    );
    return collect(memberId, requestId);
  }

  @Transactional
  @DistributedLock(keys = "'lock:resource:recommendation:member:' + #memberId")
  public RecommendationSnapshot publish(
    Long memberId,
    String requestId,
    Prepared prepared,
    AiRerankResult ai
  ) {
    // 模型执行期间角色可能停用或撤销授权，不能沿用调用前的授权结果。
    authorize(memberId);
    RecommendationSnapshot existing = replay(memberId, requestId);
    if (existing != null) return existing;
    Prepared latest = collect(memberId, requestId);
    boolean unchanged = Objects.equals(
      prepared.fingerprint(),
      latest.fingerprint()
    );
    // 提交时再次确认开关；推理期间关闭个性化也不能发布AI结果。
    List<RecommendationItem> selected =
      unchanged &&
      Boolean.TRUE.equals(latest.input().get("personalizationEnabled"))
        ? validate(latest, ai)
        : null;
    boolean useAi = selected != null;
    if (!useAi) {
      selected = new ArrayList<>(
        latest.pool().subList(0, Math.min(6, latest.pool().size()))
      );
      if (ai != null) log.warn(
        "AI推荐校验未通过或输入已变化，整批回退规则推荐：memberId={}",
        memberId
      );
    }
    mapper.archive(memberId);
    mapper.archiveFeedback(memberId);
    LocalDateTime now = LocalDateTime.now();
    RecommendationBatch batch = new RecommendationBatch();
    batch.setMemberId(memberId);
    batch.setRequestId(requestId);
    batch.setSource(useAi ? "AI" : "RULE");
    batch.setAlgorithmVersion(
      !Boolean.TRUE.equals(latest.input().get("personalizationEnabled"))
        ? RecommendationState.POPULAR_ALGORITHM_VERSION
        : useAi
          ? "AI_RERANK_V1"
          : "RULE_V1"
    );
    batch.setStatus("CURRENT");
    batch.setGeneratedAt(now);
    batch.setExpiresAt(now.plusHours(24));
    if (mapper.insertBatch(batch) != 1) throw new IllegalStateException(
      "保存推荐批次失败"
    );
    for (int i = 0; i < selected.size(); i++) {
      var item = selected.get(i);
      item.setRank(i + 1);
      item.setBatchId(batch.getId());
      if (mapper.insertItem(item) != 1) throw new IllegalStateException(
        "保存推荐条目失败"
      );
    }
    state.trim(memberId);
    // 沿用同一成员写锁及事务内删除重试；失败则回滚整个批次。
    cache.deleteRequired(RecommendationState.CACHE_PREFIX + memberId);
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          publisher.request(memberId);
        }
      }
    );
    var result = new RecommendationSnapshot();
    result.setBatch(batch);
    result.setItems(selected);
    return result;
  }

  private void authorize(Long memberId) {
    var session = access.require(
      PermissionCodes.RECOMMENDATION_REFRESH,
      DataScope.SELF
    );
    if (
      !Objects.equals(session.getMemberId(), memberId) ||
      session.getMemberType() == MemberType.AI_LIBRARIAN
    ) {
      throw new ForbiddenException("不能为其他成员生成推荐");
    }
  }

  /** 重复请求只重放当前批次，不开放历史推荐查询，也不重复调用模型。 */
  private RecommendationSnapshot replay(Long memberId, String requestId) {
    var batch = mapper.byRequest(memberId, requestId);
    if (batch == null) return null;
    if (
      !"CURRENT".equals(batch.getStatus()) ||
      !batch.getExpiresAt().isAfter(LocalDateTime.now())
    ) {
      throw new ConflictException(
        "该刷新请求已不再是当前批次，请使用新的Idempotency-Key"
      );
    }
    var snapshot = new RecommendationSnapshot();
    snapshot.setBatch(batch);
    snapshot.setItems(mapper.items(batch.getId()));
    return snapshot;
  }

  private Prepared collect(Long memberId, String requestId) {
    var now = LocalDateTime.now();
    boolean personalized = Boolean.TRUE.equals(
      mapper.personalizationEnabled(memberId)
    );
    var candidates = mapper.candidates(
      memberId,
      now.minusDays(30),
      personalized
    );
    Map<Long, List<InterestTag>> bookTags = new TreeMap<>();
    candidates.forEach(c -> bookTags.put(c.getId(), tags.bookTags(c.getId())));
    // 关闭后不读取兴趣、历史推荐与历史反馈；借出记录仍用于避免推荐已借图书。
    var memberTags = personalized
      ? tags.memberTags(memberId)
      : List.<InterestTag>of();
    Set<Long> interests = memberTags
      .stream()
      .map(InterestTag::getId)
      .collect(Collectors.toCollection(TreeSet::new));
    Set<Long> borrowed = new TreeSet<>(mapper.borrowedBooks(memberId));
    Set<Long> recent = personalized
      ? new TreeSet<>(mapper.recentBooks(memberId))
      : Set.of();
    var feedback = personalized
      ? mapper.feedback(memberId)
      : List.<RecommendationFeedback>of();
    var pool = algorithm.selectCandidates(
      candidates,
      bookTags,
      interests,
      borrowed,
      recent,
      feedback,
      personalized,
      memberId,
      requestId,
      now,
      20
    );
    Map<Long, RecommendationCandidate> byId = candidates
      .stream()
      .collect(Collectors.toMap(RecommendationCandidate::getId, c -> c));
    Map<Long, Long> categories = new TreeMap<>();
    List<Map<String, Object>> publicBooks = new ArrayList<>();
    for (var item : pool) {
      var candidate = byId.get(item.getBookId());
      categories.put(item.getBookId(), candidate.getCategoryId());
      Map<String, Object> book = new LinkedHashMap<>();
      book.put("bookId", item.getBookId());
      book.put("categoryId", candidate.getCategoryId());
      book.put("title", candidate.getTitle());
      book.put("author", candidate.getAuthor());
      String description = candidate.getDescription();
      book.put(
        "description",
        description == null
          ? ""
          : description.substring(0, Math.min(500, description.length()))
      );
      book.put(
        "tags",
        bookTags
          .get(candidate.getId())
          .stream()
          .map(InterestTag::getName)
          .sorted()
          .toList()
      );
      book.put("ruleScore", item.getScore());
      publicBooks.add(book);
    }
    Map<String, Object> input = Map.of(
      "personalizationEnabled",
      personalized,
      "interests",
      memberTags.stream().map(InterestTag::getName).sorted().toList(),
      "candidates",
      publicBooks
    );
    // 完整特征快照仅留在当前请求内，不写Redis、不发给模型。排除当前时刻，避免必然失配。
    List<Object> signature = Arrays.asList(
      candidates,
      bookTags,
      interests,
      borrowed,
      recent,
      feedback,
      personalized,
      mapper.current(memberId),
      input
    );
    return new Prepared(
      JsonUtils.toJson(signature),
      pool,
      categories,
      input,
      null
    );
  }

  /** 任一条非法就拒绝整批，绝不混用AI结果和规则补位。 */
  static List<RecommendationItem> validate(
    Prepared prepared,
    AiRerankResult ai
  ) {
    int size = Math.min(6, prepared.pool().size());
    if (
      ai == null || ai.items() == null || ai.items().size() != size || size == 0
    ) return null;
    Map<Long, RecommendationItem> legal = prepared
      .pool()
      .stream()
      .collect(Collectors.toMap(RecommendationItem::getBookId, i -> i));
    Set<Long> seen = new HashSet<>();
    Map<Long, Long> counts = new HashMap<>();
    List<RecommendationItem> result = new ArrayList<>();
    for (var choice : ai.items()) {
      if (
        choice == null ||
        !legal.containsKey(choice.bookId()) ||
        !seen.add(choice.bookId()) ||
        choice.reason() == null ||
        choice.reason().isBlank() ||
        choice.reason().length() > 300
      ) return null;
      counts.merge(prepared.categories().get(choice.bookId()), 1L, Long::sum);
      result.add(legal.get(choice.bookId()));
    }
    Map<Long, Long> capacity = prepared
      .categories()
      .values()
      .stream()
      .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
    long diverseCapacity = capacity
      .values()
      .stream()
      .mapToLong(c -> Math.min(2, c))
      .sum();
    if (diverseCapacity >= size) {
      if (
        counts
          .values()
          .stream()
          .anyMatch(c -> c > 2)
      ) return null;
    } else {
      // 分类不足时允许放宽，但仍须先覆盖各分类至多两本的基础份额。
      for (var entry : capacity.entrySet()) {
        if (
          counts.getOrDefault(entry.getKey(), 0L) <
          Math.min(2L, entry.getValue())
        ) return null;
      }
    }
    // 兴趣候选充足时至少覆盖两本，避免“JSON合法但完全偏离兴趣”的模型结果。
    Set<?> interests = new HashSet<>(
      (List<?>) prepared.input().getOrDefault("interests", List.of())
    );
    Set<Long> matching = new HashSet<>();
    for (Object value : (List<?>) prepared
      .input()
      .getOrDefault("candidates", List.of())) {
      Map<?, ?> book = (Map<?, ?>) value;
      if (((List<?>) book.get("tags")).stream().anyMatch(interests::contains)) {
        matching.add(((Number) book.get("bookId")).longValue());
      }
    }
    if (
      seen.stream().filter(matching::contains).count() <
      Math.min(2, Math.min(size, matching.size()))
    ) return null;
    for (int i = 0; i < result.size(); i++) result
      .get(i)
      .setReason(ai.items().get(i).reason().strip());
    return result;
  }
}
