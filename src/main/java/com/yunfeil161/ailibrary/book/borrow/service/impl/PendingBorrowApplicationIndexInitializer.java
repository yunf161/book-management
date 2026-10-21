package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowApplicationMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 应用启动后根据数据库中仍有效的待审批申请恢复Redis重复申请索引。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingBorrowApplicationIndexInitializer {

  private final BorrowApplicationMapper applicationMapper;
  private final PendingBorrowApplicationGuard guard;

  @EventListener(ApplicationReadyEvent.class)
  public void restorePendingApplications() {
    var applications = applicationMapper.findPendingActive(LocalDateTime.now());
    applications.forEach(guard::restore);
    if (!applications.isEmpty()) {
      log.info("恢复待审批借阅申请Redis索引：count={}", applications.size());
    }
  }
}
