package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.github.pagehelper.*;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.entity.*;
import com.yunfeil161.ailibrary.book.borrow.enums.*;
import com.yunfeil161.ailibrary.book.borrow.mapper.*;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowApplicationService;
import com.yunfeil161.ailibrary.book.catalog.entity.Book;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.enums.*;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.identity.entity.Member;
import java.time.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 借阅申请业务实现，负责申请资格校验、审批、拒绝和取消。
 */
@Service
@RequiredArgsConstructor
public class BorrowApplicationServiceImpl implements BorrowApplicationService {

  /** 第一版默认值，保留兼容旧调用；运行时以当前政策的maxRenewCount为准。 */
  public static final int MAX_RENEW_COUNT = 3;

  private final com.yunfeil161.ailibrary.policy.service.PolicyState policies;
  private final BorrowAccess access;
  private final BorrowApplicationMapper apps;
  private final BorrowRecordMapper records;
  private final BorrowApplicationExpirationService expirationService;
  private final BorrowMemberMapper members;
  private final BookMapper books;
  private final BookCopyMapper copies;
  private final CacheClient cacheClient;
  private final PendingBorrowApplicationGuard pendingGuard;
  private final com.yunfeil161.ailibrary.communication.notification.service.BusinessNotifications notifications;

  @Override
  @DistributedLock(
    keys = "@borrowResourceLockKeys.create(#currentMemberId, #r)"
  )
  @Transactional
  @Audited(resource = AuditResource.BORROW_APPLICATION, id = "")
  public BorrowApplication create(BorrowApplicationCreateRequest r) {
    // SELF 数据范围保证申请只能归属于当前登录成员。
    LoginSession s = access.require(
      PermissionCodes.BORROW_APPLICATION_CREATE,
      DataScope.SELF
    );
    if (r == null) throw new WrongFormatException("申请不能为空");
    policies.lockPolicies();
    policies.requireAccepted(s.getMemberId());
    var policy = policies.borrowing();
    boolean renew = r.getApplicationType() == BorrowApplicationType.RENEW;
    if (r.getRequestedDays() == null) r.setRequestedDays(
      renew ? policy.getDefaultRenewDays() : policy.getDefaultBorrowDays()
    );
    int maximum = renew ? policy.getMaxRenewDays() : policy.getMaxBorrowDays();
    if (
      r.getRequestedDays() < 1 || r.getRequestedDays() > maximum
    ) throw new WrongFormatException("申请天数必须在1至" + maximum + "天之间");
    BorrowApplicationType type =
      r.getApplicationType() == null
        ? BorrowApplicationType.BORROW
        : r.getApplicationType();
    Member m = requireEligible(s.getMemberId());
    validateBorrowCapacity(m);
    if (
      m.getMemberType() ==
      com.yunfeil161.ailibrary.common.enums.MemberType.AI_LIBRARIAN
    ) throw new ForbiddenException("AI馆员不能借书");
    LocalDateTime now = LocalDateTime.now();
    if (
      Boolean.TRUE.equals(policy.getBlockBorrowWhenOverdue()) &&
      records.countOverdue(m.getId(), now) > 0
    ) throw new ConflictException("存在未归还逾期记录");
    return type == BorrowApplicationType.RENEW
      ? createRenewApplication(r, m, now)
      : createBorrowApplication(r, m, now);
  }

  private BorrowApplication createBorrowApplication(
    BorrowApplicationCreateRequest r,
    Member member,
    LocalDateTime now
  ) {
    if (r.getBookId() == null) throw new WrongFormatException("图书ID不能为空");
    if (r.getBorrowRecordId() != null) throw new WrongFormatException(
      "普通借阅申请不能指定borrowRecordId"
    );
    if (member.getBorrowedBookCount() >= member.getEffectiveBorrowLimit()) {
      throw new ConflictException("借阅额度已满");
    }
    Book b = books.findById(r.getBookId());
    if (b == null) throw new NotFoundException("图书不存在");
    if (b.getStatus() != CatalogStatus.ACTIVE) throw new ConflictException(
      "图书已停用"
    );
    if (
      records.countOpenByMemberBook(member.getId(), b.getId()) > 0
    ) throw new ConflictException("该图书尚未归还");
    if (
      copies.findAvailableByBookId(b.getId()) == null
    ) throw new ConflictException("当前没有可借副本");
    // 申请有效期读取当前政策，初始政策为七天。
    BorrowApplication a = new BorrowApplication();
    a.setApplicationType(BorrowApplicationType.BORROW);
    a.setMemberId(member.getId());
    a.setBookId(b.getId());
    a.setRequestedDays(r.getRequestedDays());
    a.setReason(r.getReason());
    a.setStatus(BorrowApplicationStatus.PENDING);
    a.setSubmittedAt(now);
    a.setExpiresAt(
      now.plusDays(policies.borrowing().getApplicationValidityDays())
    );
    a.setVersion(0L);
    snapshot(a);
    insertWithPendingReservation(a);
    cacheClient.deleteRequired(
      BookCacheKeys.BORROW_APPLICATION_BY_ID + a.getId()
    );
    return apps.findById(a.getId());
  }

  private BorrowApplication createRenewApplication(
    BorrowApplicationCreateRequest request,
    Member member,
    LocalDateTime now
  ) {
    if (request.getBookId() != null) throw new WrongFormatException(
      "续借申请不能指定bookId，请使用借阅记录确定图书"
    );
    if (request.getBorrowRecordId() == null) throw new WrongFormatException(
      "续借申请的borrowRecordId不能为空"
    );
    BorrowRecord record = records.findById(request.getBorrowRecordId());
    if (record == null) throw new NotFoundException("借阅记录不存在");
    if (
      !member.getId().equals(record.getMemberId())
    ) throw new ForbiddenException("只能申请续借自己的借阅记录");
    validateRenewable(record, now);
    BookCopy copy = copies.findById(record.getCopyId());
    if (copy == null) throw new NotFoundException("借阅副本不存在");
    Book book = books.findById(copy.getBookId());
    if (book == null) throw new NotFoundException("图书不存在");

    BorrowApplication application = new BorrowApplication();
    application.setApplicationType(BorrowApplicationType.RENEW);
    application.setMemberId(member.getId());
    application.setBookId(book.getId());
    application.setBorrowRecordId(record.getId());
    application.setRequestedDays(request.getRequestedDays());
    application.setReason(request.getReason());
    application.setStatus(BorrowApplicationStatus.PENDING);
    application.setSubmittedAt(now);
    application.setExpiresAt(
      record
        .getDueAt()
        .isBefore(
          now.plusDays(policies.borrowing().getApplicationValidityDays())
        )
        ? record.getDueAt()
        : now.plusDays(policies.borrowing().getApplicationValidityDays())
    );
    application.setVersion(0L);
    snapshot(application);
    insertWithPendingReservation(application);
    cacheClient.deleteRequired(
      BookCacheKeys.BORROW_APPLICATION_BY_ID + application.getId()
    );
    return apps.findById(application.getId());
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:borrow-application:' + (#q != null && #q.id != null ? #q.id : 'admin-query')",
    mode = LockMode.READ
  )
  @Transactional
  public PageResult<BorrowApplicationDetailVO> queryMine(
    BorrowApplicationQueryRequest q
  ) {
    LoginSession s = access.require(
      PermissionCodes.BORROW_APPLICATION_QUERY,
      DataScope.SELF
    );
    if (q == null) q = new BorrowApplicationQueryRequest();
    q.setMemberId(s.getMemberId());
    return query(q);
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:borrow-application:' + (#q != null && #q.id != null ? #q.id : 'admin-query')",
    mode = LockMode.READ
  )
  @Transactional
  public PageResult<BorrowApplicationDetailVO> queryAdmin(
    BorrowApplicationQueryRequest q
  ) {
    access.require(PermissionCodes.BORROW_APPLICATION_QUERY, DataScope.ALL);
    if (q == null) q = new BorrowApplicationQueryRequest();
    return query(q);
  }

  private PageResult<BorrowApplicationDetailVO> query(
    BorrowApplicationQueryRequest q
  ) {
    if (isIdOnlyQuery(q)) {
      BorrowApplicationDetailVO row = cacheClient.queryWithPassThrough(
        BookCacheKeys.BORROW_APPLICATION_BY_ID,
        q.getId(),
        BorrowApplicationDetailVO.class,
        ignored -> findOne(q),
        BookCacheKeys.BORROW_TTL_MINUTES,
        TimeUnit.MINUTES
      );
      /*
       * 查询接口只读取数据。到期状态由定时任务批量落库，避免在持有读锁时
       * 升级为写锁，也避免一个GET请求产生数据库写入和缓存失效副作用。
       */
      if (
        row == null ||
        (q.getMemberId() != null && !q.getMemberId().equals(row.getMemberId()))
      ) {
        throw new NotFoundException("借阅申请不存在");
      }
      return new PageResult<>(1L, List.of(row));
    }
    PageHelper.startPage(page(q.getPage()), size(q.getPageSize()));
    List<BorrowApplicationDetailVO> rows = apps.find(q);
    if (q.getId() != null && rows.isEmpty()) throw new NotFoundException(
      "借阅申请不存在"
    );
    PageInfo<BorrowApplicationDetailVO> p = new PageInfo<>(rows);
    return new PageResult<>(p.getTotal(), p.getList());
  }

  @Override
  @DistributedLock(keys = "'lock:resource:borrow-application:' + #id")
  @Transactional
  @Audited(resource = AuditResource.BORROW_APPLICATION, id = "#id")
  public BorrowApplication cancel(Long id, BorrowApplicationCancelRequest r) {
    LoginSession s = access.require(
      PermissionCodes.BORROW_APPLICATION_CANCEL,
      DataScope.SELF
    );
    if (
      r == null || r.getExpectedVersion() == null
    ) throw new WrongFormatException("expectedVersion不能为空");
    BorrowApplication a = requirePending(id);
    if (!a.getMemberId().equals(s.getMemberId())) throw new ForbiddenException(
      "只能取消自己的申请"
    );
    expireIfNeeded(a);
    if (
      apps.cancel(
        id,
        s.getMemberId(),
        r.getExpectedVersion(),
        LocalDateTime.now()
      ) != 1
    ) throw new ConflictException("申请状态或版本已变化");
    pendingGuard.clear(a);
    cacheClient.deleteRequired(BookCacheKeys.BORROW_APPLICATION_BY_ID + id);
    return apps.findById(id);
  }

  @Override
  @DistributedLock(keys = "@borrowResourceLockKeys.approval(#id, #r)")
  @Transactional
  @Audited(resource = AuditResource.BORROW_APPLICATION, id = "#id")
  public BorrowApplication approve(Long id, BorrowApplicationApproveRequest r) {
    LoginSession s = access.require(
      PermissionCodes.BORROW_APPLICATION_APPROVE,
      DataScope.ALL
    );
    if (
      r == null || r.getExpectedVersion() == null || r.getExpectedVersion() < 0
    ) throw new WrongFormatException("批准天数和expectedVersion不合法");
    policies.lockPolicies();
    BorrowApplication a = requirePending(id);
    policies.verifyBorrowSnapshot(
      a.getBorrowingPolicyId(),
      a.getBorrowingPolicyVersion()
    );
    policies.requireAccepted(a.getMemberId());
    var policy = policies.borrowing();
    if (r.getApprovedDays() == null) r.setApprovedDays(a.getRequestedDays());
    int maximum =
      a.getApplicationType() == BorrowApplicationType.RENEW
        ? policy.getMaxRenewDays()
        : policy.getMaxBorrowDays();
    if (
      r.getApprovedDays() < 1 || r.getApprovedDays() > maximum
    ) throw new WrongFormatException("批准天数超过政策范围");
    expireIfNeeded(a);
    if (
      !a.getVersion().equals(r.getExpectedVersion())
    ) throw new ConflictException("申请版本已过期");
    if (a.getApplicationType() == BorrowApplicationType.RENEW) {
      return approveRenewApplication(a, r, s);
    }
    Member m = requireEligible(a.getMemberId());
    validateBorrowCapacity(m);
    if (
      m.getBorrowedBookCount() >= m.getEffectiveBorrowLimit() ||
      (Boolean.TRUE.equals(policy.getBlockBorrowWhenOverdue()) &&
        records.countOverdue(m.getId(), LocalDateTime.now()) > 0)
    ) throw new ConflictException("申请人当前不满足借阅条件");
    Book b = books.findById(a.getBookId());
    if (
      b == null || b.getStatus() != CatalogStatus.ACTIVE
    ) throw new ConflictException("图书不可借");
    // 未指定副本时自动选择可借副本；指定时仍校验归属和状态。
    BookCopy c =
      r.getCopyId() == null
        ? copies.findAvailableByBookId(b.getId())
        : copies.findById(r.getCopyId());
    if (
      c == null ||
      !c.getBookId().equals(b.getId()) ||
      c.getStatus() != BookCopyStatus.AVAILABLE
    ) throw new ConflictException("指定副本不可借");
    // 副本、借阅记录、成员借阅数和申请状态必须在同一事务中全部成功。
    if (
      copies.borrow(c.getId(), b.getId(), c.getVersion()) != 1
    ) throw new ConflictException("副本已被占用");
    LocalDateTime now = LocalDateTime.now();
    BorrowRecord rec = new BorrowRecord();
    rec.setApplicationId(a.getId());
    rec.setMemberId(m.getId());
    rec.setCopyId(c.getId());
    rec.setBorrowedAt(now);
    rec.setDueAt(now.plusDays(r.getApprovedDays()));
    rec.setStatus(BorrowRecordStatus.BORROWING);
    rec.setOverdueDays(0);
    rec.setApprovedBy(s.getMemberId());
    rec.setRenewCount(0);
    rec.setVersion(0L);
    if (
      records.insert(rec) != 1 ||
      members.incrementBorrowed(m.getId(), m.getVersion(), now) != 1 ||
      apps.approve(
        id,
        a.getVersion(),
        s.getMemberId(),
        c.getId(),
        r.getApprovedDays(),
        r.getDecisionRemark(),
        now
      ) != 1
    ) throw new ConflictException("审批过程中数据已变化");
    cacheClient.deleteRequired(BookCacheKeys.COPY_BY_ID + c.getId());
    cacheClient.deleteRequired(BookCacheKeys.BOOK_BY_ID + b.getId());
    cacheClient.deleteRequired(BookCacheKeys.BORROW_APPLICATION_BY_ID + id);
    cacheClient.deleteRequired(BookCacheKeys.BORROW_RECORD_BY_ID + rec.getId());
    // 已借数量影响人员展示和额度判断，借阅成功必须同时失效成员缓存。
    cacheClient.deleteRequired("member:reader:" + m.getId());
    cacheClient.deleteRequired("member:employee:" + m.getId());
    pendingGuard.clear(a);
    // 通知、收件关系和Outbox加入当前审批事务。
    notifications.borrowDecision(apps.findById(id), rec.getDueAt());
    return apps.findById(id);
  }

  private BorrowApplication approveRenewApplication(
    BorrowApplication application,
    BorrowApplicationApproveRequest request,
    LoginSession processor
  ) {
    if (request.getCopyId() != null) throw new WrongFormatException(
      "续借审批不能重新指定副本"
    );
    BorrowRecord record = records.findById(application.getBorrowRecordId());
    if (record == null) throw new NotFoundException("续借对应的借阅记录不存在");
    if (!application.getMemberId().equals(record.getMemberId())) {
      throw new IllegalStateException("续借申请与借阅记录的成员不一致");
    }
    BookCopy copy = copies.findById(record.getCopyId());
    if (copy == null) throw new NotFoundException("续借对应的图书副本不存在");
    if (!application.getBookId().equals(copy.getBookId())) {
      throw new IllegalStateException("续借申请与借阅记录对应的图书不一致");
    }
    LocalDateTime now = LocalDateTime.now();
    requireEligible(record.getMemberId());
    validateRenewable(record, now);
    if (
      records.renew(
        record.getId(),
        record.getVersion(),
        record.getDueAt().plusDays(request.getApprovedDays()),
        now
      ) != 1
    ) throw new ConflictException("借阅记录状态或版本已变化");
    if (
      apps.approve(
        application.getId(),
        application.getVersion(),
        processor.getMemberId(),
        null,
        request.getApprovedDays(),
        request.getDecisionRemark(),
        now
      ) != 1
    ) throw new ConflictException("续借审批过程中申请状态已变化");
    cacheClient.deleteRequired(
      BookCacheKeys.BORROW_RECORD_BY_ID + record.getId()
    );
    cacheClient.deleteRequired(
      BookCacheKeys.BORROW_APPLICATION_BY_ID + application.getId()
    );
    pendingGuard.clear(application);
    notifications.borrowDecision(
      apps.findById(application.getId()),
      record.getDueAt().plusDays(request.getApprovedDays())
    );
    return apps.findById(application.getId());
  }

  /** 在申请主表中保存政策快照，审批不能静默使用不同版本。 */
  private void snapshot(BorrowApplication application) {
    var current = policies.current("BORROWING");
    application.setBorrowingPolicyId(current.getId());
    application.setBorrowingPolicyVersion(current.getPolicyVersion());
  }

  private void validateRenewable(BorrowRecord record, LocalDateTime now) {
    if (
      record.getStatus() != BorrowRecordStatus.BORROWING ||
      record.getReturnedAt() != null
    ) throw new ConflictException("当前借阅记录不能续借");
    if (!record.getDueAt().isAfter(now)) throw new ConflictException(
      "逾期记录不能续借"
    );
    int renewCount =
      record.getRenewCount() == null ? 0 : record.getRenewCount();
    if (
      renewCount >= policies.borrowing().getMaxRenewCount()
    ) throw new ConflictException(
      "该借阅记录最多续借" + policies.borrowing().getMaxRenewCount() + "次"
    );
  }

  @Override
  @DistributedLock(keys = "'lock:resource:borrow-application:' + #id")
  @Transactional
  @Audited(resource = AuditResource.BORROW_APPLICATION, id = "#id")
  public BorrowApplication reject(Long id, BorrowApplicationRejectRequest r) {
    LoginSession s = access.require(
      PermissionCodes.BORROW_APPLICATION_REJECT,
      DataScope.ALL
    );
    if (
      r == null ||
      r.getExpectedVersion() == null ||
      r.getDecisionRemark() == null ||
      r.getDecisionRemark().isBlank()
    ) throw new WrongFormatException("拒绝原因和expectedVersion不能为空");
    BorrowApplication a = requirePending(id);
    expireIfNeeded(a);
    if (
      apps.reject(
        id,
        r.getExpectedVersion(),
        s.getMemberId(),
        r.getDecisionRemark(),
        LocalDateTime.now()
      ) != 1
    ) throw new ConflictException("申请状态或版本已变化");
    pendingGuard.clear(a);
    cacheClient.deleteRequired(BookCacheKeys.BORROW_APPLICATION_BY_ID + id);
    notifications.borrowDecision(apps.findById(id), null);
    return apps.findById(id);
  }

  private Member requireEligible(Long id) {
    Member m = members.findById(id);
    if (m == null) throw new NotFoundException("成员不存在");
    if (
      m.getAccountStatus() != AccountStatus.ACTIVE
    ) throw new ConflictException("账号未启用");
    if (
      m.getQualificationStatus() != QualificationStatus.APPROVED
    ) throw new ConflictException("借阅资格未通过");
    return m;
  }

  /**
   * Redis先用Lua完成重复申请判断和占位，数据库事务失败时再按占位令牌撤销。
   * 数据库插入成功后，占位值会替换为真实申请ID并一直保留到申请离开PENDING。
   */
  private void insertWithPendingReservation(BorrowApplication application) {
    PendingBorrowApplicationGuard.Reservation reservation =
      pendingGuard.reserve(application);
    try {
      if (apps.insert(application) != 1) {
        throw new IllegalStateException("新增借阅申请失败");
      }
      pendingGuard.confirm(reservation, application.getId());
    } catch (RuntimeException exception) {
      pendingGuard.release(reservation);
      throw exception;
    }
  }

  /** 防止历史脏数据绕过额度边界，并避免空值比较导致异常。 */
  private void validateBorrowCapacity(Member member) {
    if (
      member.getBorrowedBookCount() == null ||
      member.getPermanentBorrowLimit() == null ||
      member.getBorrowedBookCount() < 0 ||
      member.getPermanentBorrowLimit() < 0 ||
      (member.getTemporaryBorrowLimit() != null &&
        member.getTemporaryBorrowLimit() < 0) ||
      (member.getTemporaryBorrowLimit() == null) !=
        (member.getTemporaryQuotaExpiresAt() == null)
    ) {
      throw new IllegalStateException("成员借阅额度数据不合法");
    }
    // 临时额度到期后已借数量可以超过长期额度，属于合法状态；继续借阅由额度检查拒绝。
  }

  private BorrowApplication requirePending(Long id) {
    if (id == null) throw new WrongFormatException("申请ID不能为空");
    BorrowApplication a = apps.findById(id);
    if (a == null) throw new NotFoundException("借阅申请不存在");
    if (
      a.getStatus() != BorrowApplicationStatus.PENDING
    ) throw new ConflictException("申请不是待审批状态");
    return a;
  }

  private void expireIfNeeded(BorrowApplication a) {
    if (!a.getExpiresAt().isAfter(LocalDateTime.now())) {
      expirationService.expire(a.getId(), a.getVersion(), LocalDateTime.now());
      throw new ConflictException("借阅申请已过期");
    }
  }

  private int page(Integer v) {
    return v == null || v < 1 ? 1 : v;
  }

  private int size(Integer v) {
    return v == null || v < 1 ? 10 : Math.min(v, 100);
  }

  private BorrowApplicationDetailVO findOne(
    BorrowApplicationQueryRequest query
  ) {
    List<BorrowApplicationDetailVO> rows = apps.find(query);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private boolean isIdOnlyQuery(BorrowApplicationQueryRequest q) {
    return (
      q.getId() != null &&
      q.getApplicationType() == null &&
      q.getBorrowRecordId() == null &&
      q.getStatus() == null &&
      q.getMemberName() == null &&
      q.getBookId() == null &&
      q.getBookTitle() == null &&
      q.getIsbn() == null &&
      q.getSubmittedFrom() == null &&
      q.getSubmittedTo() == null &&
      q.getProcessedBy() == null
    );
  }
}
