package com.yunfeil161.ailibrary.identity.controller;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.identity.dto.MemberQueryRequest;
import com.yunfeil161.ailibrary.identity.dto.MemberWriteRequest;
import com.yunfeil161.ailibrary.identity.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供读者和员工查询；AI 智能体作为服务运行，不再作为成员维护。 */
@RestController
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;
  private final PermissionAccess permissionAccess;
  private final com.yunfeil161.ailibrary.identity.service.MemberMaintenanceAccess maintenanceAccess;
  private final com.yunfeil161.ailibrary.communication.contact.service.ContactService contacts;

  /**
   * 按成员领域类型选择对应扩展表；操作权限由角色授权校验。
   *
   * @param query 统一的成员查询参数
   * @return 对应成员类型的分页结果
   */
  @GetMapping({ "/members", "/admin/members" })
  public Result getMember(@ModelAttribute MemberQueryRequest query) {
    if (query.getScene() != null) {
      if (!"CONTACT".equals(query.getScene())) throw new WrongFormatException(
        "不支持的查询场景"
      );
      return Result.success(contacts.query(query));
    }
    LoginSession session = permissionAccess.require(
      PermissionCodes.MEMBER_QUERY
    );
    DataScope scope = permissionAccess.requireScope(
      PermissionCodes.MEMBER_QUERY
    );
    if (scope == DataScope.SELF) {
      query.setId(session.getMemberId());
      query.setMemberType(session.getMemberType());
    } else if (scope != DataScope.ALL) {
      throw new ForbiddenException("没有成员查询数据权限");
    }
    if (query.getMemberType() == null) {
      throw new WrongFormatException("memberType 不能为空");
    }

    return switch (query.getMemberType()) {
      case READER -> Result.success(
        memberService.getReaders(
          query.toReader(),
          query.getPage(),
          query.getPageSize()
        )
      );
      case EMPLOYEE -> Result.success(
        memberService.getEmployees(
          query.toEmployee(),
          query.getPage(),
          query.getPageSize()
        )
      );
      case AI_LIBRARIAN -> throw retiredAiIdentity();
    };
  }

  @PostMapping({ "/members", "/admin/members" })
  public Result addMember(@RequestBody MemberWriteRequest request) {
    permissionAccess.require(PermissionCodes.MEMBER_CREATE, DataScope.ALL);
    requireMemberType(request);
    return switch (request.getMemberType()) {
      case READER -> Result.success(
        memberService.addReader(request.toReader(), request.getInterests())
      );
      case EMPLOYEE -> Result.success(
        memberService.addEmployee(request.toEmployee())
      );
      case AI_LIBRARIAN -> throw retiredAiIdentity();
    };
  }

  @PutMapping({ "/members/{id}", "/admin/members/{id}" })
  public Result updateMember(
    @PathVariable Long id,
    @RequestBody MemberWriteRequest request
  ) {
    LoginSession session = permissionAccess.require(
      PermissionCodes.MEMBER_UPDATE
    );
    DataScope scope = permissionAccess.requireScope(
      PermissionCodes.MEMBER_UPDATE
    );
    if (scope != DataScope.SELF && scope != DataScope.ALL) {
      throw new ForbiddenException("没有成员修改数据权限");
    }
    if (
      scope == DataScope.SELF &&
      !session.getMemberId().equals(id) &&
      (request == null || request.getMemberType() == null)
    ) {
      throw new ForbiddenException("只能维护有专项授权的成员字段");
    }
    requireMemberType(request);
    if (
      request.getMemberType() ==
      com.yunfeil161.ailibrary.common.enums.MemberType.AI_LIBRARIAN
    ) {
      throw retiredAiIdentity();
    }
    if (!session.getMemberId().equals(id)) {
      // 通用member:update的SELF不授权代改；另行逐字段校验专项ALL权限。
      maintenanceAccess.authorize(id, request);
    } else {
      // 本人修改时，领域类型必须以可信登录会话为准，不能由客户端切换扩展表。
      request.setMemberType(session.getMemberType());
      // SELF 只允许修改普通个人资料，忽略客户端伪造的角色和管理字段。
      request.retainSelfEditableFields();
    }
    return switch (request.getMemberType()) {
      case READER -> Result.success(
        memberService.updateReader(
          id,
          request.toReader(),
          request.getInterests(),
          request.getExpectedVersion()
        )
      );
      case EMPLOYEE -> Result.success(
        memberService.updateEmployee(
          id,
          request.toEmployee(),
          request.getExpectedVersion()
        )
      );
      case AI_LIBRARIAN -> throw retiredAiIdentity();
    };
  }

  @DeleteMapping({ "/members/{id}", "/admin/members/{id}" })
  public Result deleteMember(
    @PathVariable Long id,
    @RequestParam com.yunfeil161.ailibrary.common.enums.MemberType memberType
  ) {
    permissionAccess.require(PermissionCodes.MEMBER_DELETE, DataScope.ALL);
    if (
      memberType ==
      com.yunfeil161.ailibrary.common.enums.MemberType.AI_LIBRARIAN
    ) {
      throw retiredAiIdentity();
    }
    // DELETE仍为逻辑停用，但必须拥有账号管理权限，不能仅凭通用删除权限。
    MemberWriteRequest request = new MemberWriteRequest();
    request.setMemberType(memberType);
    request.setAccountStatus(
      com.yunfeil161.ailibrary.common.enums.AccountStatus.DISABLED
    );
    maintenanceAccess.authorize(id, request);
    return switch (memberType) {
      case READER -> Result.success(memberService.deleteReader(id));
      case EMPLOYEE -> Result.success(memberService.deleteEmployee(id));
      case AI_LIBRARIAN -> throw retiredAiIdentity();
    };
  }

  private void requireMemberType(MemberWriteRequest request) {
    if (request == null || request.getMemberType() == null) {
      throw new WrongFormatException("memberType 不能为空");
    }
  }

  /** 兼容旧请求的明确错误，不再路由到已删除的扩展表。 */
  private WrongFormatException retiredAiIdentity() {
    return new WrongFormatException(
      "AI服务身份已退出人员管理，请使用AI任务和能力控制"
    );
  }
}
