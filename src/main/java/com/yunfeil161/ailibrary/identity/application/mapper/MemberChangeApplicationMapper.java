package com.yunfeil161.ailibrary.identity.application.mapper;

import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeQueryRequest;
import com.yunfeil161.ailibrary.identity.application.entity.MemberChangeApplication;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeDecision;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeStatus;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberChangeApplicationMapper {
  int insertApplication(
    @Param("application") MemberChangeApplication application
  );

  int insertReaderDetail(
    @Param("application") MemberChangeApplication application
  );

  int insertEmployeeDetail(
    @Param("application") MemberChangeApplication application
  );

  /**
   * 串行化同一成员的申请创建过程，避免两个并发请求同时通过待审批数量检查。
   */
  Long lockMember(@Param("memberId") Long memberId);

  MemberChangeApplication findById(@Param("id") Long id);

  MemberChangeApplication findByIdForUpdate(@Param("id") Long id);

  List<MemberChangeApplication> query(
    @Param("query") MemberChangeQueryRequest query,
    @Param("readerOnly") boolean readerOnly
  );

  int countPending(
    @Param("targetMemberId") Long targetMemberId,
    @Param("applicationType") MemberChangeType applicationType
  );

  /** 只扫描待审且已到期的主键，明细和当前状态在逐条事务内重新读取。 */
  List<Long> findExpiredIds(
    @Param("now") LocalDateTime now,
    @Param("limit") int limit
  );

  int expire(
    @Param("id") Long id,
    @Param("expectedVersion") Long expectedVersion,
    @Param("now") LocalDateTime now
  );

  int countOtherPendingQualifications(
    @Param("memberId") Long memberId,
    @Param("excludeId") Long excludeId
  );

  /** 到期只恢复资格状态，不回写申请快照里的旧个人资料或旧须知版本。 */
  int restoreExpiredQualification(
    @Param("memberId") Long memberId,
    @Param("status") QualificationStatus status,
    @Param("expectedVersion") Long expectedVersion
  );

  int cancel(
    @Param("id") Long id,
    @Param("applicantMemberId") Long applicantMemberId,
    @Param("expectedVersion") Long expectedVersion,
    @Param("now") LocalDateTime now
  );

  int decide(
    @Param("id") Long id,
    @Param("decision") MemberChangeDecision decision,
    @Param("status") MemberChangeStatus status,
    @Param("reason") String reason,
    @Param("reviewerMemberId") Long reviewerMemberId,
    @Param("reviewerRoleId") Long reviewerRoleId,
    @Param("expectedVersion") Long expectedVersion,
    @Param("now") LocalDateTime now
  );

  int updateQualification(
    @Param("memberId") Long memberId,
    @Param("fromStatus") QualificationStatus fromStatus,
    @Param("toStatus") QualificationStatus toStatus,
    @Param("acceptedRuleVersion") String acceptedRuleVersion,
    @Param("expectedVersion") Long expectedVersion
  );

  int updateQuota(
    @Param("memberId") Long memberId,
    @Param("borrowLimit") Integer borrowLimit,
    @Param("expiresAt") java.time.LocalDateTime expiresAt,
    @Param("expectedVersion") Long expectedVersion
  );

  int updateAccountStatus(
    @Param("memberId") Long memberId,
    @Param("fromStatus") AccountStatus fromStatus,
    @Param("toStatus") AccountStatus toStatus,
    @Param("expectedVersion") Long expectedVersion
  );

  int updateEmployeeProfile(
    @Param("memberId") Long memberId,
    @Param("departmentId") Long departmentId,
    @Param("positionName") String positionName,
    @Param("expectedVersion") Long expectedVersion
  );

  int updateEmployment(
    @Param("memberId") Long memberId,
    @Param("fromStatus") EmploymentStatus fromStatus,
    @Param("toStatus") EmploymentStatus toStatus,
    @Param("leftOn") LocalDate leftOn,
    @Param("expectedVersion") Long expectedVersion
  );

  int updateRole(
    @Param("memberId") Long memberId,
    @Param("roleId") Long roleId,
    @Param("expectedVersion") Long expectedVersion
  );

  Integer countDepartment(@Param("departmentId") Long departmentId);

  Integer countEmployeeProfile(@Param("memberId") Long memberId);

  Long findEmployeeProfileVersion(@Param("memberId") Long memberId);
}
