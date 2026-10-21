package com.yunfeil161.ailibrary.auth.session;

import com.yunfeil161.ailibrary.common.enums.MemberType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 中保存的登录会话信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginSession {
    /** JWT ID，同时也是 Redis 登录会话键的一部分。 */
    private String jti;
    private Long memberId;
    private Long roleId;
    private MemberType memberType;
    /** 稳定角色编码，用于客户端展示分流；服务端鉴权仍以 roleId 查询授权。 */
    private String roleCode;
    private String email;
    private String name;

}
