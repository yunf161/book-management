package com.yunfeil161.ailibrary.identity;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yunfeil161.ailibrary.identity.application.service.impl.MemberChangeExpirationScheduler;
import com.yunfeil161.ailibrary.identity.application.service.impl.MemberChangeExpirationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 单份坏数据回滚后，其他到期申请仍应继续处理。 */
class MemberChangeExpirationSchedulerTests {

  @Test
  void failureDoesNotBlockRemainingApplications() {
    var service = mock(MemberChangeExpirationService.class);
    when(service.findExpiredIds(any(LocalDateTime.class), eq(100))).thenReturn(
      List.of(1L, 2L)
    );
    when(service.expire(eq(1L), any())).thenThrow(
      new IllegalStateException("测试失败")
    );
    when(service.expire(eq(2L), any())).thenReturn(true);
    new MemberChangeExpirationScheduler(service).synchronize();
    verify(service).expire(eq(2L), any());
  }
}
