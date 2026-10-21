package com.yunfeil161.ailibrary.identity.quota.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 到期清理仅处理临时字段，永远不覆盖长期额度或已借数量。 */
@Mapper
public interface TemporaryQuotaMapper {
  @Select(
    "select id from member where temporary_quota_expires_at <= #{now} order by temporary_quota_expires_at,id limit 100"
  )
  List<Long> findExpired(@Param("now") LocalDateTime now);

  /** 到期条件和版本在更新时重新检查，避免误清除刚审批的新临时额度。 */
  @Update(
    "update member set temporary_borrow_limit=null, temporary_quota_expires_at=null, " +
      "version=version+1,updated_at=current_timestamp(3) " +
      "where id=#{id} and version=#{version} and temporary_quota_expires_at <= #{now}"
  )
  int clearExpired(
    @Param("id") Long id,
    @Param("version") Long version,
    @Param("now") LocalDateTime now
  );
}
