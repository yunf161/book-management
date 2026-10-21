package com.yunfeil161.ailibrary.audit;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 批处理在逐行更新处显式记录，避免一条无对象ID的批量日志丢失变更明细。 */
@Service
@RequiredArgsConstructor
public class AuditRecorder {

  private final AuditSnapshots snapshots;
  private final AuditRedactor redactor;
  private final OperationLogWriter writer;

  public Map<String, Object> before(AuditResource resource, Long id) {
    return snapshots.read(resource, id);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void changed(
    AuditResource resource,
    Long id,
    String action,
    Map<String, Object> before
  ) {
    var after = snapshots.read(resource, id);
    if (java.util.Objects.equals(before, after)) return;
    var event = writer.entry(resource.name(), id, action);
    event.setBeforeData(redactor.json(before));
    event.setAfterData(redactor.json(after));
    writer.success(event);
  }
}
