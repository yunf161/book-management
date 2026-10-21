package com.yunfeil161.ailibrary.book.reading.service.impl;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 阅读计划模块内部的权限与数据范围检查器。 */
@Component
@RequiredArgsConstructor
class ReadingPlanAccess {

  private final PermissionAccess permissionAccess;

  /**
   * 校验本人阅读计划接口。成员ID始终取自登录会话，不能由前端指定。
   */
  LoginSession self(String action) {
    LoginSession session = permissionAccess.require(
      selfCode(action),
      DataScope.SELF
    );
    if (session.getMemberType() == com.yunfeil161.ailibrary.common.enums.MemberType.AI_LIBRARIAN) {
      throw new ForbiddenException("AI馆员不能使用阅读计划");
    }
    return session;
  }

  /**
   * 校验管理端对指定成员的写操作。ALL可操作任意成员，SELF仅可操作自己。
   */
  LoginSession admin(String action, Long target) {
    String code = adminCode(action);
    LoginSession session = permissionAccess.require(code);
    DataScope scope = permissionAccess.requireScope(code);
    if (
      scope == DataScope.ALL ||
      (scope == DataScope.SELF && session.getMemberId().equals(target))
    ) {
      return session;
    }
    throw new ForbiddenException("没有该成员的数据权限");
  }

  /** 校验管理端聚合查询入口；是否可访问完全由当前角色授权决定。 */
  LoginSession adminQuery() {
    return permissionAccess.require(
      PermissionCodes.READING_PLAN_QUERY
    );
  }

  /**
   * 把数据范围转换为Mapper查询条件：ALL返回null，SELF返回当前成员ID。
   */
  Long scopeMember(LoginSession session) {
    DataScope scope = permissionAccess.requireScope(
      PermissionCodes.READING_PLAN_QUERY
    );
    if (scope == DataScope.ALL) return null;
    if (scope == DataScope.SELF) return session.getMemberId();
    throw new ForbiddenException("没有可用的数据范围");
  }

  /** 将受控的动作名映射为本人权限常量，避免拼接任意权限编码。 */
  private String selfCode(String action) {
    return switch (action) {
      case "query" -> PermissionCodes.READING_PLAN_QUERY_SELF;
      case "create" -> PermissionCodes.READING_PLAN_CREATE_SELF;
      case "update" -> PermissionCodes.READING_PLAN_UPDATE_SELF;
      case "delete" -> PermissionCodes.READING_PLAN_DELETE_SELF;
      default -> throw new WrongFormatException("不支持的阅读计划操作");
    };
  }

  /** 将受控的动作名映射为管理端权限常量。 */
  private String adminCode(String action) {
    return switch (action) {
      case "query" -> PermissionCodes.READING_PLAN_QUERY;
      case "create" -> PermissionCodes.READING_PLAN_CREATE;
      case "update" -> PermissionCodes.READING_PLAN_UPDATE;
      case "delete" -> PermissionCodes.READING_PLAN_DELETE;
      default -> throw new WrongFormatException("不支持的阅读计划操作");
    };
  }

}

