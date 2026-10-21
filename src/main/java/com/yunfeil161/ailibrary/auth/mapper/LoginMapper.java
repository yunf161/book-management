package com.yunfeil161.ailibrary.auth.mapper;


import com.yunfeil161.ailibrary.identity.entity.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginMapper {

    /**
     * 根据邮箱精确查询用于登录校验的成员信息。
     *
     * @param email 登录邮箱
     * @return 匹配的成员；没有匹配记录时返回 null
     */
    Member findByEmail(@Param("email") String email);
}
