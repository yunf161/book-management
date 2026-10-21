package com.yunfeil161.ailibrary.auth.dto;


import com.yunfeil161.ailibrary.common.enums.MemberType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterParam {
    private String email;
    private String password;
    private String code;
    private String name;
    /** 注册身份；当前开放 READER 和 EMPLOYEE。 */
    private MemberType memberType;
    /** 员工必填，读者不使用。 */
    private Long departmentId;
    /** 员工必填，读者不使用。 */
    private String positionName;
}
