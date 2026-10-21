package com.yunfeil161.ailibrary.book.reading.service.impl;

import com.github.pagehelper.*;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.book.catalog.entity.Book;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.book.reading.dto.*;
import com.yunfeil161.ailibrary.book.reading.entity.*;
import com.yunfeil161.ailibrary.book.reading.enums.*;
import com.yunfeil161.ailibrary.book.reading.mapper.*;
import com.yunfeil161.ailibrary.book.reading.service.ReadingPlanService;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.identity.entity.Member;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 阅读计划及计划条目的业务实现。
 *
 * 普通成员入口只能操作当前登录成员的数据；管理端入口在权限和数据范围校验后，
 * 复用相同的计划、条目、状态流转、乐观锁及安全重排规则。
 */
@Service
@RequiredArgsConstructor
public class ReadingPlanServiceImpl implements ReadingPlanService {

  /** 重排时使用的临时正数偏移量，避免触发计划内顺序唯一索引冲突。 */
  private static final int OFFSET = 1_000_000;

  private final ReadingPlanAccess access;
  private final ReadingPlanMapper plans;
  private final ReadingPlanItemMapper items;
  private final MemberMapper members;
  private final BookMapper books;
  private final CacheClient cacheClient;

  @Override
  @DistributedLock(keys = "'lock:resource:reading-plan:' + #currentMemberId")
  @Transactional
  @Audited(
    resource = AuditResource.READING_PLAN_MEMBER,
    id = "#currentMemberId"
  )
  public ReadingPlan createMine(ReadingPlanCreateRequest r) {
    // 普通接口不信任请求中的 memberId，始终使用当前登录成员。
    return create(access.self("create").getMemberId(), r);
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:reading-plan:' + (#r != null ? #r.memberId : 'invalid')"
  )
  @Transactional
  @Audited(resource = AuditResource.READING_PLAN_MEMBER, id = "#r.memberId")
  public ReadingPlan createAdmin(ReadingPlanCreateRequest r) {
    // 先执行权限检查，避免无权用户通过参数错误探测管理端业务规则。
    Long targetMemberId = r == null ? null : r.getMemberId();
    access.admin("create", targetMemberId);
    if (targetMemberId == null) {
      throw new WrongFormatException("memberId不能为空");
    }
    return create(targetMemberId, r);
  }

  private ReadingPlan create(Long memberId, ReadingPlanCreateRequest r) {
    // 两种创建入口共用成员状态、字段格式和一人一计划约束。
    validateMember(memberId);
    validatePlan(r);

    if (plans.findByMemberId(memberId) != null) {
      throw new ConflictException("该成员已存在阅读计划");
    }
    ReadingPlan p = new ReadingPlan();
    p.setMemberId(memberId);
    p.setName(r.getName().trim());
    p.setDescription(r.getDescription());
    p.setTargetBooks(r.getTargetBooks());
    p.setStartDate(r.getStartDate());
    p.setEndDate(r.getEndDate());
    p.setStatus(
      r.getStatus() == null ? ReadingPlanStatus.DRAFT : r.getStatus()
    );
    if (
      p.getStatus() != ReadingPlanStatus.DRAFT &&
      p.getStatus() != ReadingPlanStatus.ACTIVE
    ) {
      throw new WrongFormatException("首次创建只允许DRAFT或ACTIVE");
    }
    if (plans.insert(p) != 1) {
      throw new IllegalStateException("新增阅读计划失败");
    }
    evictPlan(memberId);
    return plans.findByMemberId(memberId);
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:reading-plan:' + #currentMemberId",
    mode = LockMode.READ
  )
  public ReadingPlan getMine() {
    // 本人查询直接使用登录上下文中的 memberId，不接受前端指定所有者。
    return requirePlan(access.self("query").getMemberId(), false);
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:reading-plan:admin-query'",
    mode = LockMode.READ
  )
  public PageResult<ReadingPlan> queryAdmin(ReadingPlanQueryRequest q) {
    // scopeMember 将 ALL/SELF 数据范围转换为 Mapper 的成员过滤条件。
    LoginSession s = access.adminQuery();
    if (q == null) {
      q = new ReadingPlanQueryRequest();
    }
    PageHelper.startPage(page(q.getPageNum()), size(q.getPageSize()));
    List<ReadingPlan> list = plans.find(q, access.scopeMember(s));
    PageInfo<ReadingPlan> p = new PageInfo<>(list);
    return new PageResult<>(p.getTotal(), p.getList());
  }

  @Override
  @DistributedLock(keys = "'lock:resource:reading-plan:' + #currentMemberId")
  @Transactional
  @Audited(
    resource = AuditResource.READING_PLAN_MEMBER,
    id = "#currentMemberId"
  )
  public ReadingPlan updateMine(ReadingPlanUpdateRequest r) {
    // 本人修改仍复用统一的状态流转和乐观锁逻辑。
    return update(access.self("update").getMemberId(), r);
  }

  @Override
  @DistributedLock(keys = "'lock:resource:reading-plan:' + #id")
  @Transactional
  @Audited(resource = AuditResource.READING_PLAN_MEMBER, id = "#id")
  public ReadingPlan updateAdmin(Long id, ReadingPlanUpdateRequest r) {
    // 管理端先校验对路径 memberId 的数据权限，不允许修改计划所有者。
    access.admin("update", id);
    return update(id, r);
  }

  private ReadingPlan update(Long memberId, ReadingPlanUpdateRequest r) {
    // 加锁读取可以让状态检查、条目完成情况和版本更新保持一致。
    ReadingPlan old = requirePlan(memberId, true);
    validatePlan(r);
    if (r.getVersion() == null || r.getVersion() < 0) {
      throw new WrongFormatException("version不能为空且不能小于0");
    }
    if (!old.getVersion().equals(r.getVersion())) {
      throw new ConflictException("阅读计划版本已过期");
    }
    validatePlanTransition(old, r.getStatus());
    if (r.getStatus() == ReadingPlanStatus.COMPLETED) {
      // targetBooks是完成目标，不是书单容量上限。允许超额阅读，完成时必须达标。
      // 使用本次请求的目标值，避免修改目标并同时完成时按旧目标错误放行。
      if (
        items.countStatus(old.getId(), "COMPLETED") < r.getTargetBooks() ||
        items.countStatus(old.getId(), "PLANNED") > 0 ||
        items.countStatus(old.getId(), "READING") > 0
      ) {
        throw new ConflictException(
          "已完成书目未达到阅读目标，或仍有待读、在读条目"
        );
      }
    }
    if (plans.update(memberId, r) != 1) {
      throw new ConflictException("阅读计划版本已变化");
    }
    evictPlan(memberId);
    return plans.findByMemberId(memberId);
  }

  @Override
  @DistributedLock(keys = "'lock:resource:reading-plan:' + #currentMemberId")
  @Transactional
  @Audited(
    resource = AuditResource.READING_PLAN_MEMBER,
    id = "#currentMemberId"
  )
  public String deleteMine(Long v) {
    // 删除入口只允许当前成员删除自己的计划。
    return delete(access.self("delete").getMemberId(), v);
  }

  @Override
  @DistributedLock(keys = "'lock:resource:reading-plan:' + #id")
  @Transactional
  @Audited(resource = AuditResource.READING_PLAN_MEMBER, id = "#id")
  public String deleteAdmin(Long id, Long v) {
    // 管理端删除前检查当前角色对目标成员的数据范围。
    access.admin("delete", id);
    return delete(id, v);
  }

  private String delete(Long memberId, Long v) {
    // 条目和计划必须在同一事务中删除，任一步失败都会整体回滚。
    ReadingPlan p = requirePlan(memberId, true);
    if (v == null || !v.equals(p.getVersion())) {
      throw new ConflictException("阅读计划版本已过期");
    }
    List<ReadingPlanItem> itemsForEviction = items.findByPlanForUpdate(
      p.getId()
    );
    items.deleteByPlan(p.getId());
    if (plans.delete(memberId, v) != 1) {
      throw new ConflictException("阅读计划版本已变化");
    }
    evictPlan(memberId);
    evictItems(memberId, itemsForEviction);
    return p.getName();
  }

  @Override
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + (#r != null ? #r.bookId : 'invalid')",
      "'lock:resource:reading-plan:' + #currentMemberId",
    }
  )
  @Transactional
  @Audited(
    resource = AuditResource.READING_PLAN_MEMBER,
    id = "#currentMemberId"
  )
  public ReadingPlanItem addMine(ReadingItemCreateRequest r) {
    // 普通成员新增的条目来源固定为 USER，忽略前端伪造的 source。
    return add(access.self("create").getMemberId(), r, ReadingItemSource.USER);
  }

  @Override
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + (#r != null ? #r.bookId : 'invalid')",
      "'lock:resource:reading-plan:' + #id",
    }
  )
  @Transactional
  @Audited(resource = AuditResource.READING_PLAN_MEMBER, id = "#id")
  public ReadingPlanItem addAdmin(Long id, ReadingItemCreateRequest r) {
    // 管理端人工添加的条目来源固定为 LIBRARIAN。
    access.admin("update", id);
    return add(id, r, ReadingItemSource.LIBRARIAN);
  }

  private ReadingPlanItem add(
    Long memberId,
    ReadingItemCreateRequest r,
    ReadingItemSource source
  ) {
    // 条目写操作同时校验计划版本，防止基于过期的条目顺序进行修改。
    if (
      r == null || r.getBookId() == null || r.getExpectedPlanVersion() == null
    ) {
      throw new WrongFormatException("bookId和expectedPlanVersion不能为空");
    }
    ReadingPlan p = requirePlan(memberId, true);
    checkPlanVersion(p, r.getExpectedPlanVersion());
    Book b = books.findById(r.getBookId());
    if (b == null) {
      throw new NotFoundException("图书不存在");
    }
    if (b.getStatus() != CatalogStatus.ACTIVE) {
      throw new ConflictException("图书已停用");
    }
    if (items.findOne(p.getId(), r.getBookId()) != null) {
      throw new ConflictException("计划中已存在该图书");
    }
    //查询当前阅读计划下的全部条目，并使用 FOR UPDATE 锁住这些条目
    List<ReadingPlanItem> all = items.findByPlanForUpdate(p.getId());
    // 锁定全部条目后，才能可靠地计算插入位置和连续顺序。
    int pos = r.getSequenceNo() == null ? all.size() + 1 : r.getSequenceNo();
    if (pos < 1 || pos > all.size() + 1) {
      throw new WrongFormatException("sequenceNo超出范围");
    }
    for (ReadingPlanItem x : all) {
      // 先移入不会冲突的正数区间，再写入最终的 1...n 顺序。
      items.moveToOffset(x.getId(), OFFSET);
    }
    ReadingPlanItem n = new ReadingPlanItem();
    n.setPlanId(p.getId());
    n.setBookId(r.getBookId());
    n.setSequenceNo(pos);
    n.setPriority(r.getPriority() == null ? 0 : r.getPriority());
    n.setStatus(ReadingItemStatus.PLANNED);
    n.setProgressPercent(0);
    n.setSource(source);
    n.setPlannedStartDate(r.getPlannedStartDate());
    n.setPlannedFinishDate(r.getPlannedFinishDate());
    validateDates(n.getPlannedStartDate(), n.getPlannedFinishDate());
    n.setNote(r.getNote());
    if (items.insert(n) != 1) {
      throw new IllegalStateException("新增阅读条目失败");
    }
    int seq = 1;
    for (ReadingPlanItem x : all) {
      if (seq == pos) {
        seq++;
      }
      items.writeSequence(x.getId(), seq++);
    }
    touch(p);
    evictPlan(memberId);
    evictItems(memberId, all);
    cacheClient.deleteRequired(
      BookCacheKeys.readingItemKey(memberId, n.getBookId())
    );
    return items.findOne(p.getId(), n.getBookId());
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:reading-plan:' + #currentMemberId",
    mode = LockMode.READ
  )
  public PageResult<ReadingPlanItem> queryMineItems(ReadingItemQueryRequest q) {
    // 先根据当前成员找到唯一计划，再限定 planId 查询条目。
    Long id = access.self("query").getMemberId();
    ReadingPlan p = requirePlan(id, false);
    if (q == null) {
      q = new ReadingItemQueryRequest();
    }
    // 本人接口的数据边界只能由登录会话决定，不接受请求参数扩大或缩小成员范围。
    q.setMemberId(null);
    q.setMemberKeyword(null);
    if (isBookOnlyItemQuery(q)) {
      ReadingPlanItem item = cachedItem(id, p.getId(), q.getBookId());
      return item == null
        ? new PageResult<>(0L, List.of())
        : new PageResult<>(1L, List.of(item));
    }
    return queryItems(q, p.getId(), null);
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:reading-plan:admin-query'",
    mode = LockMode.READ
  )
  public PageResult<ReadingPlanItem> queryAdminItems(
    ReadingItemQueryRequest q
  ) {
    // 管理端使用同一个聚合接口查询权限范围内所有计划的条目。
    LoginSession s = access.adminQuery();
    Long scope = access.scopeMember(s);
    if (
      q != null &&
      q.getMemberId() != null &&
      q.getBookId() != null &&
      (scope == null || scope.equals(q.getMemberId())) &&
      isBookOnlyItemQuery(q)
    ) {
      ReadingPlan p = requirePlan(q.getMemberId(), false);
      ReadingPlanItem item = cachedItem(
        q.getMemberId(),
        p.getId(),
        q.getBookId()
      );
      return item == null
        ? new PageResult<>(0L, List.of())
        : new PageResult<>(1L, List.of(item));
    }
    return queryItems(q, null, scope);
  }

  private PageResult<ReadingPlanItem> queryItems(
    ReadingItemQueryRequest q,
    Long planId,
    Long scope
  ) {
    // 将前端排序枚举转换为固定列名，避免把任意字符串拼接进 SQL。
    if (q == null) {
      q = new ReadingItemQueryRequest();
    }
    String by =
      q.getOrderBy() == null ? "SEQUENCE" : q.getOrderBy().toUpperCase();
    //防sql注入
    q.setSortColumn(
      switch (by) {
        case "PRIORITY" -> "priority";
        case "CREATED_TIME" -> "created_at";
        case "SEQUENCE" -> "sequence_no";
        default -> throw new WrongFormatException("orderBy不合法");
      }
    );
    String dir =
      q.getOrderDirection() == null
        ? "ASC"
        : q.getOrderDirection().toUpperCase();
    if (!dir.equals("ASC") && !dir.equals("DESC")) {
      throw new WrongFormatException("orderDirection不合法");
    }
    q.setSortDirection(dir);
    PageHelper.startPage(page(q.getPageNum()), size(q.getPageSize()));
    List<ReadingPlanItem> list = items.find(q, planId, scope);
    PageInfo<ReadingPlanItem> p = new PageInfo<>(list);
    return new PageResult<>(p.getTotal(), p.getList());
  }

  @Override
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + #bookId",
      "'lock:resource:reading-plan:' + #currentMemberId",
      "'lock:resource:reading-plan-item:' + #currentMemberId + ':' + #bookId",
    }
  )
  @Transactional
  @Audited(
    resource = AuditResource.READING_PLAN_MEMBER,
    id = "#currentMemberId"
  )
  public ReadingPlanItem updateMineItem(
    Long bookId,
    ReadingItemUpdateRequest r
  ) {
    // 使用当前成员和 bookId 定位本人计划中的唯一条目。
    return updateItem(access.self("update").getMemberId(), bookId, r);
  }

  @Override
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + #bookId",
      "'lock:resource:reading-plan:' + #id",
      "'lock:resource:reading-plan-item:' + #id + ':' + #bookId",
    }
  )
  @Transactional
  @Audited(resource = AuditResource.READING_PLAN_MEMBER, id = "#id")
  public ReadingPlanItem updateAdminItem(
    Long id,
    Long bookId,
    ReadingItemUpdateRequest r
  ) {
    // 管理端先校验目标成员，再进入与本人接口相同的更新流程。
    access.admin("update", id);
    return updateItem(id, bookId, r);
  }

  private ReadingPlanItem updateItem(
    Long memberId,
    Long bookId,
    ReadingItemUpdateRequest r
  ) {
    // 主表版本和条目版本必须同时匹配，避免并发重排覆盖其他修改。
    if (
      r == null || r.getVersion() == null || r.getExpectedPlanVersion() == null
    ) {
      throw new WrongFormatException("version和expectedPlanVersion不能为空");
    }
    /*
     * 条目重排会连续修改多行。乐观锁可以在事务末尾发现版本冲突并通过回滚
     * 保证最终数据正确，但失败请求可能已经执行了多条无效SQL，并与另一个
     * 事务交叉竞争条目行锁和唯一索引。这里先用 FOR UPDATE 锁定计划主记录，
     * 让同一计划的写请求排队；等待者获得锁后会读取最新version，并在真正
     * 修改条目前因版本过期而退出，从而减少无效更新和死锁概率。
     */
    ReadingPlan p = requirePlan(memberId, true);
    checkPlanVersion(p, r.getExpectedPlanVersion());
    List<ReadingPlanItem> all = items.findByPlanForUpdate(p.getId());
    ReadingPlanItem current = all
      .stream()
      .filter(x -> x.getBookId().equals(bookId))
      .findFirst()
      .orElseThrow(() -> new NotFoundException("阅读条目不存在"));
    if (!current.getVersion().equals(r.getVersion())) {
      throw new ConflictException("阅读条目版本已过期");
    }
    int target =
      r.getSequenceNo() == null ? current.getSequenceNo() : r.getSequenceNo();
    if (target < 1 || target > all.size()) {
      throw new WrongFormatException("sequenceNo超出范围");
    }
    applyItemUpdate(current, r);
    List<ReadingPlanItem> ordered = new ArrayList<>(all);
    /*
     * findByPlanForUpdate 已按 sequence_no 升序返回全部条目。这里复制列表，
     * 删除当前条目的旧位置，再插入目标位置。业务序号从1开始，而List下标
     * 从0开始，所以目标业务序号target对应下标target - 1。
     *
     * 例如原顺序[A, B, C, D]，将D移动到第2位：先remove(D)得到
     * [A, B, C]，再add(1, D)得到[A, D, B, C]。sequence_no只表示
     * 展示顺序，不作为条目的稳定身份标识。
     */
    ordered.remove(current);
    ordered.add(target - 1, current);
    if (target != current.getSequenceNo()) {
      /*
       * 数据库存在(plan_id, sequence_no)唯一索引，不能直接把D写成2，
       * 因为B此时仍占用2。先将全部旧序号增加OFFSET，临时移动到
       * 1000001...100000n，释放正常的1...n区间；随后按照ordered列表
       * 从1开始连续写回。整个过程处于同一事务，失败时临时序号也会回滚。
       */
      for (ReadingPlanItem x : all) {
        items.moveToOffset(x.getId(), OFFSET);
      }
      int seq = 1;
      for (ReadingPlanItem x : ordered) {
        // ordered中的集合位置就是最终顺序，因此依次写入1、2、3……。
        x.setSequenceNo(seq++);
        if (x == current) {
          if (items.updateFields(x) != 1) {
            throw new ConflictException("阅读条目版本已变化");
          }
        } else {
          items.writeSequence(x.getId(), x.getSequenceNo());
        }
      }
    } else if (items.updateFields(current) != 1) {
      throw new ConflictException("阅读条目版本已变化");
    }
    touch(p);
    evictPlan(memberId);
    evictItems(memberId, all);
    return items.findOne(p.getId(), bookId);
  }

  @Override
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + #b",
      "'lock:resource:reading-plan:' + #currentMemberId",
      "'lock:resource:reading-plan-item:' + #currentMemberId + ':' + #b",
    }
  )
  @Transactional
  @Audited(
    resource = AuditResource.READING_PLAN_MEMBER,
    id = "#currentMemberId"
  )
  public String deleteMineItem(Long b, Long v, Long pv) {
    // 本人删除同时携带条目版本和计划版本。
    return deleteItem(access.self("delete").getMemberId(), b, v, pv);
  }

  @Override
  @DistributedLock(
    keys = {
      "'lock:resource:book:' + #b",
      "'lock:resource:reading-plan:' + #id",
      "'lock:resource:reading-plan-item:' + #id + ':' + #b",
    }
  )
  @Transactional
  @Audited(resource = AuditResource.READING_PLAN_MEMBER, id = "#id")
  public String deleteAdminItem(Long id, Long b, Long v, Long pv) {
    // 管理端删除前先检查对目标成员的删除权限和数据范围。
    access.admin("delete", id);
    return deleteItem(id, b, v, pv);
  }

  private String deleteItem(Long memberId, Long bookId, Long v, Long pv) {
    // 锁定计划及全部条目，保证删除和后续顺序补齐是一个原子操作。
    ReadingPlan p = requirePlan(memberId, true);
    checkPlanVersion(p, pv);
    List<ReadingPlanItem> all = items.findByPlanForUpdate(p.getId());
    ReadingPlanItem old = all
      .stream()
      .filter(x -> x.getBookId().equals(bookId))
      .findFirst()
      .orElseThrow(() -> new NotFoundException("阅读条目不存在"));
    if (v == null || !v.equals(old.getVersion())) {
      throw new ConflictException("阅读条目版本已过期");
    }
    if (items.deleteOne(old.getId(), v) != 1) {
      throw new ConflictException("阅读条目版本已变化");
    }
    all.remove(old);
    // 删除后将剩余条目先移到临时区，再恢复为连续的 1...n。
    for (ReadingPlanItem x : all) {
      items.moveToOffset(x.getId(), OFFSET);
    }
    int seq = 1;
    for (ReadingPlanItem x : all) {
      items.writeSequence(x.getId(), seq++);
    }
    touch(p);
    evictPlan(memberId);
    cacheClient.deleteRequired(BookCacheKeys.readingItemKey(memberId, bookId));
    evictItems(memberId, all);
    return old.getBookName();
  }

  private void applyItemUpdate(ReadingPlanItem x, ReadingItemUpdateRequest r) {
    // Service 统一协调状态、进度和实际时间，避免产生互相矛盾的数据。
    ReadingItemStatus old = x.getStatus();
    ReadingItemStatus next = r.getStatus() == null ? old : r.getStatus();
    Integer progress =
      r.getProgressPercent() == null
        ? x.getProgressPercent()
        : r.getProgressPercent();
    // 所有状态都必须遵守统一进度边界，避免ABANDONED等状态保存非法数值。
    if (progress == null || progress < 0 || progress > 100) {
      throw new WrongFormatException("阅读进度必须在0至100之间");
    }
    if (progress == 100) {
      // 进度达到100时自动完成，不依赖前端额外传入 COMPLETED。
      next = ReadingItemStatus.COMPLETED;
    }
    if (next != old && !allowed(old, next)) {
      throw new ConflictException("不允许的阅读条目状态转换");
    }
    if (next == ReadingItemStatus.PLANNED && progress != 0) {
      throw new WrongFormatException("PLANNED进度必须为0");
    }
    if (next == ReadingItemStatus.READING && (progress < 1 || progress > 99)) {
      throw new WrongFormatException("READING进度必须为1至99");
    }
    if (next == ReadingItemStatus.COMPLETED) {
      // 完成状态强制使用100%进度，并记录首次完成时间。
      progress = 100;
      if (x.getFinishedAt() == null) {
        x.setFinishedAt(LocalDateTime.now());
      }
    }
    if (next == ReadingItemStatus.READING && x.getStartedAt() == null) {
      // 只有首次进入阅读中状态时才记录开始时间。
      x.setStartedAt(LocalDateTime.now());
    }
    x.setStatus(next);
    x.setProgressPercent(progress);
    x.setPriority(r.getPriority() == null ? x.getPriority() : r.getPriority());
    x.setPlannedStartDate(r.getPlannedStartDate());
    x.setPlannedFinishDate(r.getPlannedFinishDate());
    validateDates(x.getPlannedStartDate(), x.getPlannedFinishDate());
    x.setNote(r.getNote());
  }

  private boolean allowed(ReadingItemStatus a, ReadingItemStatus b) {
    // 终态不允许恢复，READING 也不能退回 PLANNED。
    if (a == b) {
      return true;
    }
    return (
      (a == ReadingItemStatus.PLANNED &&
        (b == ReadingItemStatus.READING || b == ReadingItemStatus.ABANDONED)) ||
      (a == ReadingItemStatus.READING &&
        (b == ReadingItemStatus.COMPLETED || b == ReadingItemStatus.ABANDONED))
    );
  }

  private void validateMember(Long id) {
    // 阅读计划所有者必须存在、启用并且不能是AI馆员。
    Member m = members.findMemberById(id);
    if (m == null) {
      throw new NotFoundException("成员不存在");
    }
    if (
      m.getMemberType() ==
      com.yunfeil161.ailibrary.common.enums.MemberType.AI_LIBRARIAN
    ) {
      throw new ForbiddenException("AI馆员不能拥有个人阅读计划");
    }
    if (m.getAccountStatus() != AccountStatus.ACTIVE) {
      throw new ConflictException("成员账号未启用");
    }
  }

  private void validatePlan(ReadingPlanCreateRequest r) {
    // 创建计划最少需要非空名称和大于0的目标书数。
    if (
      r == null ||
      r.getName() == null ||
      r.getName().isBlank() ||
      r.getTargetBooks() == null ||
      r.getTargetBooks() <= 0
    ) {
      throw new WrongFormatException("名称不能为空且targetBooks必须大于0");
    }
    validateDates(r.getStartDate(), r.getEndDate());
  }

  private void validatePlan(ReadingPlanUpdateRequest r) {
    // 当前 PUT 使用完整更新语义，因此核心字段必须全部提供。
    if (
      r == null ||
      r.getName() == null ||
      r.getName().isBlank() ||
      r.getTargetBooks() == null ||
      r.getTargetBooks() <= 0 ||
      r.getStatus() == null
    ) {
      throw new WrongFormatException("计划修改字段不完整");
    }
    validateDates(r.getStartDate(), r.getEndDate());
  }

  private void validateDates(LocalDate a, LocalDate b) {
    // 日期允许同时为空，但同时存在时结束日期不能早于开始日期。
    if (a != null && b != null && b.isBefore(a)) {
      throw new WrongFormatException("结束日期不能早于开始日期");
    }
  }

  private void validatePlanTransition(ReadingPlan p, ReadingPlanStatus n) {
    // 只允许需求定义的单向状态流转，COMPLETED 是不可恢复的终态。
    ReadingPlanStatus o = p.getStatus();
    if (n == o) {
      return;
    }
    boolean ok =
      (o == ReadingPlanStatus.DRAFT &&
        (n == ReadingPlanStatus.ACTIVE || n == ReadingPlanStatus.PAUSED)) ||
      (o == ReadingPlanStatus.ACTIVE &&
        (n == ReadingPlanStatus.PAUSED || n == ReadingPlanStatus.COMPLETED)) ||
      (o == ReadingPlanStatus.PAUSED &&
        (n == ReadingPlanStatus.ACTIVE || n == ReadingPlanStatus.COMPLETED));
    if (!ok) {
      throw new ConflictException("不允许的阅读计划状态转换");
    }
  }

  private ReadingPlan requirePlan(Long memberId, boolean lock) {
    /*
     * 写操作使用 FOR UPDATE，锁由数据库在当前 @Transactional 事务提交或
     * 回滚时释放。它负责串行化同一计划的多行操作；version乐观锁仍负责
     * 判断客户端携带的数据是否过期。普通单行原子更新通常只需要乐观锁，
     * 纯查询则既不需要加锁，也不会调用带 FOR UPDATE 的Mapper方法。
     */
    if (memberId == null) {
      throw new WrongFormatException("memberId不能为空");
    }
    ReadingPlan p = lock
      ? plans.findByMemberIdForUpdate(memberId)
      : cacheClient.queryWithPassThrough(
          BookCacheKeys.READING_PLAN_BY_MEMBER,
          memberId,
          ReadingPlan.class,
          plans::findByMemberId,
          BookCacheKeys.READING_PLAN_TTL_MINUTES,
          TimeUnit.MINUTES
        );
    if (p == null) {
      throw new NotFoundException("阅读计划不存在");
    }
    return p;
  }

  private void checkPlanVersion(ReadingPlan p, Long v) {
    // 条目增删改也必须检查主表版本，因为这些操作会改变计划整体状态。
    if (v == null || !p.getVersion().equals(v)) {
      throw new ConflictException("阅读计划版本已过期");
    }
  }

  private void touch(ReadingPlan p) {
    // 条目变化后递增计划版本，使客户端能够感知计划内容已经改变。
    if (plans.touch(p.getId(), p.getVersion()) != 1) {
      throw new ConflictException("阅读计划版本已变化");
    }
  }

  private ReadingPlanItem cachedItem(Long memberId, Long planId, Long bookId) {
    String suffix = memberId + ":" + bookId;
    return cacheClient.queryWithPassThrough(
      BookCacheKeys.READING_ITEM_BY_MEMBER_BOOK,
      suffix,
      ReadingPlanItem.class,
      ignored -> items.findOne(planId, bookId),
      BookCacheKeys.READING_ITEM_TTL_MINUTES,
      TimeUnit.MINUTES
    );
  }

  private boolean isBookOnlyItemQuery(ReadingItemQueryRequest q) {
    return (
      q.getBookId() != null &&
      q.getMemberKeyword() == null &&
      q.getBookKeyword() == null &&
      q.getStatus() == null &&
      q.getSource() == null &&
      q.getOrderBy() == null &&
      q.getOrderDirection() == null
    );
  }

  private void evictPlan(Long memberId) {
    cacheClient.deleteRequired(BookCacheKeys.READING_PLAN_BY_MEMBER + memberId);
  }

  private void evictItems(Long memberId, Collection<ReadingPlanItem> values) {
    for (ReadingPlanItem item : values) {
      cacheClient.deleteRequired(
        BookCacheKeys.readingItemKey(memberId, item.getBookId())
      );
    }
  }

  private int page(Integer n) {
    // 页码缺失或非法时统一回退到第一页。
    return n == null || n < 1 ? 1 : n;
  }

  private int size(Integer n) {
    // 默认每页10条，并限制单次最多查询100条。
    return n == null || n < 1 ? 10 : Math.min(n, 100);
  }
}
