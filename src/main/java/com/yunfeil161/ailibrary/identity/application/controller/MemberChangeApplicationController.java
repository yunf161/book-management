package com.yunfeil161.ailibrary.identity.application.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCancelRequest;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCreateRequest;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeDecisionRequest;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeQueryRequest;
import com.yunfeil161.ailibrary.identity.application.service.MemberChangeApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 聚合成员变更申请入口；查询仍通过一个全字段接口组合条件。 */
@RestController
@RequiredArgsConstructor
public class MemberChangeApplicationController {

  private final MemberChangeApplicationService service;
  private final PermissionAccess permissionAccess;

  @PostMapping("/member-change-applications")
  public Result create(@RequestBody MemberChangeCreateRequest request) {
    permissionAccess.require(
      PermissionCodes.MEMBER_CHANGE_CREATE_SELF,
      DataScope.SELF
    );
    return Result.success(service.create(request));
  }

  @GetMapping("/member-change-applications")
  public Result queryMine(@ModelAttribute MemberChangeQueryRequest query) {
    permissionAccess.require(
      PermissionCodes.MEMBER_CHANGE_QUERY_SELF,
      DataScope.SELF
    );
    return Result.success(service.queryMine(query));
  }

  @PostMapping("/member-change-applications/{id}/cancel")
  public Result cancel(
    @PathVariable Long id,
    @RequestBody MemberChangeCancelRequest request
  ) {
    permissionAccess.require(
      PermissionCodes.MEMBER_CHANGE_CANCEL_SELF,
      DataScope.SELF
    );
    return Result.success(service.cancel(id, request == null ? null : request.getExpectedVersion()));
  }

  @GetMapping("/admin/member-change-applications")
  public Result queryForReview(@ModelAttribute MemberChangeQueryRequest query) {
    return Result.success(service.queryForReview(query));
  }

  @PostMapping("/admin/member-change-applications/{id}/decision")
  public Result decide(
    @PathVariable Long id,
    @RequestBody MemberChangeDecisionRequest request
  ) {
    return Result.success(service.decide(id, request));
  }
}
