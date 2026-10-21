package com.yunfeil161.ailibrary.identity.quota.service;

import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import com.yunfeil161.ailibrary.identity.quota.mapper.TemporaryQuotaMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 时间判断决定生效额度，任务只负责清理字段与缓存，不承担正确性兜底。 */
@Service
@RequiredArgsConstructor
public class TemporaryQuotaService {

  private final TemporaryQuotaMapper quotas;
  private final MemberMapper members;
  private final CacheClient cache;

  @Transactional
  @DistributedLock(keys = "'lock:resource:member:' + #id")
  @Audited(resource = AuditResource.MEMBER, id = "#id")
  public boolean clearExpired(Long id, LocalDateTime now) {
    var member = members.findMemberById(id);
    if (member == null) return false;
    int rows = quotas.clearExpired(id, member.getVersion(), now);
    if (rows == 0) return false;
    if (rows != 1) throw new IllegalStateException(
      "临时额度清理影响了多条成员记录"
    );
    // 与人员按ID查询使用同一成员资源键；删除失败会使数据库事务回滚并稍后重试。
    cache.deleteRequired("member:reader:" + id);
    cache.deleteRequired("member:employee:" + id);
    cache.deleteRequired("member:ai-librarian:" + id);
    return true;
  }
}
