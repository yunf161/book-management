package com.yunfeil161.ailibrary.communication.contact.service;

import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.enums.*;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.communication.contact.dto.ContactBrief;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.identity.dto.MemberQueryRequest;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 联系人场景复用成员查询入口，但不复用完整个人资料的返回对象。 */
@Service
@RequiredArgsConstructor
public class ContactService {
  private final MemberMapper members;
  private final PermissionAccess permissions;

  public PageResult<ContactBrief> query(MemberQueryRequest input) {
    var context = context();
    // 只接受联系人筛选白名单，忽略邮箱、角色、账号状态等人员管理条件。
    var q = new MemberQueryRequest();
    q.setId(input.getId());
    q.setName(input.getName());
    q.setDepartmentId(input.getDepartmentId());
    q.setMemberType(context.reader ? MemberType.EMPLOYEE : input.getMemberType());
    q.setPage(input.getPage());
    q.setPageSize(input.getPageSize());
    com.yunfeil161.ailibrary.communication.common.CommunicationValidation.id(q.getId());
    com.yunfeil161.ailibrary.communication.common.CommunicationValidation.id(q.getDepartmentId());
    if (q.getName() != null && q.getName().length() > 80) {
      throw new WrongFormatException("姓名查询条件不能超过80个字符");
    }
    com.yunfeil161.ailibrary.communication.common.CommunicationValidation.page(q);
    var rows = members.findContacts(q, context.memberId, context.departmentId);
    return new PageResult<>(new PageInfo<>(rows).getTotal(), rows);
  }

  /** 发送前再次使用同一SQL范围判断，联系人列表不是永久授权。 */
  public void requireTarget(Long sender, Long target) {
    var context = context();
    if (!sender.equals(context.memberId)) throw new ForbiddenException("发送身份不一致");
    var q = new MemberQueryRequest();
    q.setId(target);
    if (context.reader) q.setMemberType(MemberType.EMPLOYEE);
    if (members.findContacts(q, sender, context.departmentId).isEmpty()) {
      throw new ForbiddenException("目标成员不在可联系范围内");
    }
  }

  private Context context() {
    var session = permissions.require("contact:query");
    var member = members.findMemberById(session.getMemberId());
    if (member == null
        || member.getAccountStatus() != AccountStatus.ACTIVE
        || member.getMemberType() == MemberType.AI_LIBRARIAN) {
      throw new ForbiddenException("当前成员不能使用通信");
    }
    boolean reader = member.getMemberType() == MemberType.READER;
    DataScope scope = permissions.requireScope("contact:query");
    Long department = null;
    if (!reader && scope != DataScope.ALL) {
      if (scope != DataScope.DEPARTMENT) throw new ForbiddenException("没有联系人数据范围");
      department = members.findDepartmentByMemberId(member.getId());
      if (department == null) throw new ForbiddenException("未配置所属部门");
    }
    return new Context(member.getId(), reader, department);
  }

  private record Context(Long memberId, boolean reader, Long departmentId) {}
}
