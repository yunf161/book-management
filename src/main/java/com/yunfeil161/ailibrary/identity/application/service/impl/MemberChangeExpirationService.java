package com.yunfeil161.ailibrary.identity.application.service.impl;

import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.communication.notification.service.BusinessNotifications;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeStatus;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import com.yunfeil161.ailibrary.identity.application.mapper.MemberChangeApplicationMapper;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 每份申请独立提交到期结果，状态、资格恢复、通知及缓存失效共同成功或回滚。 */
@Service
@RequiredArgsConstructor
public class MemberChangeExpirationService {

  private final MemberChangeApplicationMapper applications;
  private final MemberMapper members;
  private final CacheClient cache;
  private final BusinessNotifications notifications;

  public List<Long> findExpiredIds(LocalDateTime now, int limit) {
    return applications.findExpiredIds(now, limit);
  }

  /**
   * 与审批/取消复用政策、成员和申请资源键，锁持有到数据库事务结束。
   * 扫描不持锁；取得成员锁后重新读取申请，避免旧候选覆盖已审批/取消的结果。
   */
  @Transactional
  @DistributedLock(keys = "@memberChangeLockKeys.forApplication(#id)")
  @Audited(resource = AuditResource.MEMBER_CHANGE, id = "#id")
  public boolean expire(Long id, LocalDateTime now) {
    var found = applications.findById(id);
    if (found == null) return false;
    if (applications.lockMember(found.getTargetMemberId()) == null) {
      throw new IllegalStateException("过期申请关联的成员不存在");
    }
    var application = applications.findByIdForUpdate(id);
    if (
      application == null ||
      application.getStatus() != MemberChangeStatus.PENDING ||
      application.getExpiresAt() == null ||
      application.getExpiresAt().isAfter(now)
    ) {
      return false;
    }
    if (applications.expire(id, application.getVersion(), now) != 1) {
      throw new IllegalStateException("过期申请状态或版本发生变化");
    }

    var type = application.getApplicationType();
    if (
      type == MemberChangeType.READER_QUALIFICATION ||
      type == MemberChangeType.READER_QUALIFICATION_RESTORE
    ) {
      var member = members.findMemberById(application.getTargetMemberId());
      // 普通资料已修改也应能结束到期申请，因此使用当前成员版本。
      // 人工已改变资格或已有另一份待审资格时，不回写旧申请的状态快照。
      if (
        member.getQualificationStatus() == QualificationStatus.PENDING &&
        applications.countOtherPendingQualifications(member.getId(), id) == 0
      ) {
        var previous = application.getCurrentQualificationStatus();
        boolean valid =
          type == MemberChangeType.READER_QUALIFICATION_RESTORE
            ? previous == QualificationStatus.SUSPENDED
            : previous == QualificationStatus.NOT_APPLIED ||
              previous == QualificationStatus.REJECTED;
        if (!valid) throw new IllegalStateException(
          "过期资格申请的原状态快照无效"
        );
        if (
          applications.restoreExpiredQualification(
            member.getId(),
            previous,
            member.getVersion()
          ) != 1
        ) {
          throw new IllegalStateException("恢复过期申请资格状态失败");
        }
      }
    }

    // 与人员ID读取使用同一成员资源锁。缓存删除失败须回滚，下一轮重试。
    cache.deleteRequired("member:reader:" + application.getTargetMemberId());
    cache.deleteRequired("member:employee:" + application.getTargetMemberId());
    notifications.memberExpired(application);
    return true;
  }
}
