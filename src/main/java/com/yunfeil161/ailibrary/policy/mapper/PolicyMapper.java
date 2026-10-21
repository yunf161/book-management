package com.yunfeil161.ailibrary.policy.mapper;

import com.yunfeil161.ailibrary.policy.dto.PolicyQueryRequest;
import com.yunfeil161.ailibrary.policy.entity.PolicyVersion;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 生效指针独立于历史版本；始终存在的指针行可协调首次发布。 */
@Mapper
public interface PolicyMapper {
  PolicyVersion find(Long id);
  PolicyVersion current(String type);
  /** 事务内使用当前读，避免MySQL可重复读快照仍返回发布前的政策。 */
  PolicyVersion currentLocked(String type);
  Long lockCurrent(String type);
  List<PolicyVersion> query(PolicyQueryRequest query);
  int insert(PolicyVersion value);
  int update(PolicyVersion value);
  int retire(Long id);
  int activate(
    @Param("id") Long id,
    @Param("expectedVersion") Long version,
    @Param("policyVersion") Long policyVersion,
    @Param("memberId") Long memberId
  );
  int point(
    @Param("type") String type,
    @Param("id") Long id,
    @Param("sequence") Long sequence
  );
  Long sequence(String type);

  @Update(
    "update member set accepted_rule_version=#{rule},version=version+1,updated_at=current_timestamp(3) " +
      "where id=#{id} and version=#{version}"
  )
  int accept(
    @Param("id") Long id,
    @Param("rule") String rule,
    @Param("version") Long version
  );

  @Update(
    "update member set permanent_borrow_limit=#{quota},updated_at=current_timestamp(3) where id=#{id}"
  )
  int initialQuota(@Param("id") Long id, @Param("quota") Integer quota);
}
