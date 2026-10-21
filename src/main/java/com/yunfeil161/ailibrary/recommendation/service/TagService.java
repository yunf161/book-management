package com.yunfeil161.ailibrary.recommendation.service;

import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.*;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.*;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import com.yunfeil161.ailibrary.recommendation.entity.InterestTag;
import com.yunfeil161.ailibrary.recommendation.mapper.TagMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 人员与书目使用同一标签字典；新接口只接收标签 ID。 */
@Service
@RequiredArgsConstructor
public class TagService {

  private final TagMapper mapper;
  private final MemberMapper members;
  private final BookMapper books;
  private final CacheClient cache;
  private final PermissionAccess access;
  private final RecommendationState state;

  public List<InterestTag> query(Long id, String code, String name) {
    return mapper.find(id, code, name);
  }

  public List<InterestTag> myInterests() {
    return mapper.memberTags(
      human(PermissionCodes.RECOMMENDATION_QUERY).getMemberId()
    );
  }

  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:member:' + #currentMemberId",
      "'lock:resource:recommendation:member:' + #currentMemberId",
    }
  )
  @Audited(resource = AuditResource.MEMBER, id = "#currentMemberId")
  public List<InterestTag> replaceMyInterests(List<Long> ids) {
    LoginSession session = human(PermissionCodes.INTERESTS_UPDATE);
    Long memberId = session.getMemberId();
    if (members.findMemberById(memberId) == null) throw new NotFoundException(
      "成员不存在"
    );
    List<InterestTag> tags = validate(ids);
    mapper.deleteMemberTags(memberId);
    tags.forEach(t -> mapper.insertMemberTag(memberId, t));
    state.invalidate(memberId);
    // 原人员查询仍返回兼容的兴趣名称，因此一并删除人员按 ID 缓存。
    cache.deleteRequired("member:reader:" + memberId);
    return tags;
  }

  @Transactional
  @DistributedLock(keys = "'lock:resource:book:' + #bookId")
  @Audited(resource = AuditResource.BOOK, id = "#bookId")
  public List<InterestTag> replaceBookTags(
    Long bookId,
    List<Long> ids,
    Long expectedVersion
  ) {
    access.require(PermissionCodes.BOOK_UPDATE);
    var book = books.findById(bookId);
    if (book == null) throw new NotFoundException("图书不存在");
    if (
      expectedVersion == null || expectedVersion < 0
    ) throw new WrongFormatException("expectedVersion不能为空");
    var request =
      new com.yunfeil161.ailibrary.book.catalog.dto.BookUpdateRequest();
    request.setExpectedVersion(expectedVersion);
    if (books.update(bookId, request) != 1) throw new ConflictException(
      "图书版本已过期"
    );
    applyBookTags(bookId, ids);
    cache.deleteRequired(BookCacheKeys.BOOK_BY_ID + bookId);
    return mapper.bookTags(bookId);
  }

  /** 书目新增/更新已经持锁并开启事务，此方法只负责验证与写关联。 */
  public void applyBookTags(Long bookId, List<Long> ids) {
    List<InterestTag> tags = validate(ids);
    mapper.deleteBookTags(bookId);
    tags.forEach(t -> mapper.insertBookTag(bookId, t.getId()));
  }

  public List<InterestTag> bookTags(Long bookId) {
    return mapper.bookTags(bookId);
  }

  private List<InterestTag> validate(List<Long> ids) {
    if (
      ids == null ||
      ids.size() > 20 ||
      ids.stream().anyMatch(id -> id == null || id <= 0)
    ) {
      throw new WrongFormatException("tagIds必须是最多20个有效标签ID的列表");
    }
    List<InterestTag> tags = new ArrayList<>();
    for (Long id : new LinkedHashSet<>(ids)) {
      InterestTag tag = mapper.findById(id);
      if (tag == null) throw new NotFoundException("标签不存在：" + id);
      if (!"ACTIVE".equals(tag.getStatus())) throw new ConflictException(
        "标签已停用：" + id
      );
      tags.add(tag);
    }
    return tags;
  }

  /** 兼容旧人员 DTO 的文本兴趣，同时为旧数据建立可查询的标签关联。 */
  public void replaceLegacyInterests(Long memberId, List<String> names) {
    if (names == null) return;
    mapper.deleteMemberTags(memberId);
    for (String name : names
      .stream()
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(v -> !v.isBlank())
      .distinct()
      .toList()) {
      if (name.length() > 40) throw new WrongFormatException(
        "兴趣名称最多40个字符"
      );
      InterestTag tag = mapper.findByName(name);
      if (tag == null) {
        tag = new InterestTag();
        tag.setName(name);
        tag.setCode(legacyCode(name));
        // 唯一键UPSERT取得该字典行写锁，再用当前读读取真实行；避免重复插入的共享锁升级死锁。
        // MySQL默认REPEATABLE READ
        // 下，普通快照读可能看不到另一个事务刚提交的同名标签。
        mapper.insert(tag);
        tag = mapper.findByNameCurrent(name);
        if (tag == null) throw new IllegalStateException("兴趣标签初始化失败");
      }
      if (!"ACTIVE".equals(tag.getStatus())) throw new ConflictException(
        "兴趣标签已停用"
      );
      mapper.insertMemberTag(memberId, tag);
    }
    state.invalidate(memberId);
  }

  private String legacyCode(String name) {
    try {
      return (
        "LEGACY_" +
        HexFormat.of()
          .formatHex(
            MessageDigest.getInstance("SHA-256").digest(
              name.getBytes(StandardCharsets.UTF_8)
            )
          )
          .substring(0, 16)
          .toUpperCase(Locale.ROOT)
      );
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private LoginSession human(String permission) {
    LoginSession session = access.require(permission, DataScope.SELF);
    if (
      session.getMemberType() == MemberType.AI_LIBRARIAN
    ) throw new ForbiddenException("AI馆员暂不使用推荐功能");
    return session;
  }
}
