package com.yunfeil161.ailibrary.identity.service;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.identity.dto.MemberWriteRequest;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

/** 管理员/馆员的直接维护白名单；审批权限不等于任意修改成员全部属性。 */
@Component
@RequiredArgsConstructor
public class MemberMaintenanceAccess {

  private final MemberMapper members;
  private final PermissionAccess permissions;

  private static final Set<String> FIELDS = Set.of(
    "class",
    "memberType",
    "expectedVersion",
    "accountStatus",
    "qualificationStatus",
    "permanentBorrowLimit"
  );

  public void authorize(Long id, MemberWriteRequest request) {
    permissions.require(PermissionCodes.MEMBER_QUERY, DataScope.ALL);
    if (LoginUserContext.get().getMemberType() != MemberType.EMPLOYEE) {
      throw new ForbiddenException("只有人类工作人员可以维护他人资料");
    }
    var current = members.findMemberById(id);
    if (current == null) throw new NotFoundException("成员不存在");
    if (current.getMemberType() != request.getMemberType()) {
      throw new WrongFormatException("memberType 与现有成员类型不匹配");
    }
    // 工号、人事、部门、角色和个人资料不能借维护额度的接口一并修改。
    var properties = new BeanWrapperImpl(request);
    for (var property : properties.getPropertyDescriptors()) {
      String key = property.getName();
      if (!FIELDS.contains(key) && properties.getPropertyValue(key) != null) {
        throw new ForbiddenException(
          "字段不允许直接维护：" + key + "，请使用本人资料或申请审批流程"
        );
      }
    }
    if (current.getMemberType() == MemberType.EMPLOYEE) {
      // 员工账户与读者分开授权，馆员的读者维护权限不能用于修改员工。
      permissions.require(
        PermissionCodes.EMPLOYEE_PROFILE_CHANGE_APPROVE,
        DataScope.ALL
      );
    }
    if (request.getPermanentBorrowLimit() != null) {
      permissions.require(
        PermissionCodes.BORROW_QUOTA_CHANGE_APPROVE,
        DataScope.ALL
      );
    }
    if (request.getAccountStatus() != null) {
      permissions.require(
        PermissionCodes.MEMBER_ACCOUNT_CHANGE_APPROVE,
        DataScope.ALL
      );
    }
    if (request.getQualificationStatus() != null) {
      permissions.require(
        PermissionCodes.READER_QUALIFICATION_APPROVE,
        DataScope.ALL
      );
      var previous = current.getQualificationStatus();
      var next = request.getQualificationStatus();
      // 只维护已获批资格的暂停/恢复，不能将未申请、待审或拒绝直接变成通过。
      if (
        !Set.of(
          QualificationStatus.APPROVED,
          QualificationStatus.SUSPENDED
        ).contains(previous) ||
        !Set.of(
          QualificationStatus.APPROVED,
          QualificationStatus.SUSPENDED
        ).contains(next)
      ) {
        throw new ConflictException(
          "首次借阅资格必须申请审批，直接维护仅支持已通过资格的暂停或恢复"
        );
      }
    }
    if (
      request.getPermanentBorrowLimit() == null &&
      request.getAccountStatus() == null &&
      request.getQualificationStatus() == null
    ) {
      throw new WrongFormatException("至少提供一个有权限维护的字段");
    }
  }
}
