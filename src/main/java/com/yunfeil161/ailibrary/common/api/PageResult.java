package com.yunfeil161.ailibrary.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data //自动生成getter和setter方法
@AllArgsConstructor
@NoArgsConstructor
public class PageResult <T>{

    private Long total; //总记录数
    private List<T> rows; //当前页的数据
}
