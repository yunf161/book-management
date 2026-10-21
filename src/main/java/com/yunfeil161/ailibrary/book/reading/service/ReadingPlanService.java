package com.yunfeil161.ailibrary.book.reading.service;

import com.yunfeil161.ailibrary.book.reading.dto.ReadingItemCreateRequest;
import com.yunfeil161.ailibrary.book.reading.dto.ReadingItemQueryRequest;
import com.yunfeil161.ailibrary.book.reading.dto.ReadingItemUpdateRequest;
import com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanCreateRequest;
import com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanQueryRequest;
import com.yunfeil161.ailibrary.book.reading.dto.ReadingPlanUpdateRequest;
import com.yunfeil161.ailibrary.book.reading.entity.ReadingPlan;
import com.yunfeil161.ailibrary.book.reading.entity.ReadingPlanItem;
import com.yunfeil161.ailibrary.common.api.PageResult;

/** 阅读计划和计划条目的读者端、管理端业务入口。 */
public interface ReadingPlanService {
  /** 当前成员创建自己的阅读计划。 */
  ReadingPlan createMine(ReadingPlanCreateRequest r);

  /** 馆员在授权范围内为指定成员创建阅读计划。 */
  ReadingPlan createAdmin(ReadingPlanCreateRequest r);

  /** 查询当前成员的阅读计划。 */
  ReadingPlan getMine();

  /** 馆员按聚合条件查询可见的阅读计划。 */
  PageResult<ReadingPlan> queryAdmin(ReadingPlanQueryRequest q);

  ReadingPlan updateMine(ReadingPlanUpdateRequest r);

  ReadingPlan updateAdmin(Long memberId, ReadingPlanUpdateRequest r);

  String deleteMine(Long version);

  String deleteAdmin(Long memberId, Long version);

  /** 当前成员为自己的计划添加条目。 */
  ReadingPlanItem addMine(ReadingItemCreateRequest r);

  /** 馆员为指定成员的计划添加条目。 */
  ReadingPlanItem addAdmin(Long memberId, ReadingItemCreateRequest r);

  /** 查询当前成员计划中的条目。 */
  PageResult<ReadingPlanItem> queryMineItems(ReadingItemQueryRequest q);

  /** 馆员按聚合条件查询权限范围内的条目。 */
  PageResult<ReadingPlanItem> queryAdminItems(ReadingItemQueryRequest q);

  ReadingPlanItem updateMineItem(Long bookId, ReadingItemUpdateRequest r);

  ReadingPlanItem updateAdminItem(
    Long memberId,
    Long bookId,
    ReadingItemUpdateRequest r
  );

  String deleteMineItem(Long bookId, Long itemVersion, Long planVersion);

  String deleteAdminItem(
    Long memberId,
    Long bookId,
    Long itemVersion,
    Long planVersion
  );
}
