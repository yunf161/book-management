package com.yunfeil161.ailibrary.auth.dto;

import com.yunfeil161.ailibrary.common.enums.MemberType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class LoginInfo {
    /** 未接受当前须知时，前端先显示确认弹窗再加载受保护页面。 */
    private boolean requiresRuleConsent;
    private String currentRuleVersion;
        private String email;
        private Long id;
        private String token;
        private String name;
        private MemberType memberType;
        private Long roleId;
        private String roleCode;
    }


