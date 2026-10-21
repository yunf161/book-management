package com.yunfeil161.ailibrary.book.borrow.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationCancelRequest;
import com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationQueryRequest;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowApplicationMapper;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowMemberMapper;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowRecordMapper;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 防止取消申请重新错误复用查询权限。 */
class BorrowCancellationPermissionTests {

  @Test
  void ownQueryRequiresQueryPermission() {
    BorrowAccess access = mock(BorrowAccess.class);
    BorrowApplicationMapper applications = mock(BorrowApplicationMapper.class);
    when(
      access.require(PermissionCodes.BORROW_APPLICATION_QUERY, DataScope.SELF)
    ).thenReturn(mock(LoginSession.class));
    when(applications.find(org.mockito.ArgumentMatchers.any())).thenReturn(
      List.of()
    );
    BorrowApplicationServiceImpl service = service(access, applications);

    service.queryMine(new BorrowApplicationQueryRequest());
    verify(access).require(
      PermissionCodes.BORROW_APPLICATION_QUERY,
      DataScope.SELF
    );
  }

  @Test
  void cancelRequiresDedicatedCancelPermission() {
    BorrowAccess access = mock(BorrowAccess.class);
    when(
      access.require(PermissionCodes.BORROW_APPLICATION_CANCEL, DataScope.SELF)
    ).thenReturn(mock(LoginSession.class));

    BorrowApplicationServiceImpl service = service(
      access,
      mock(BorrowApplicationMapper.class)
    );

    assertThrows(
      WrongFormatException.class,
      () -> service.cancel(1L, new BorrowApplicationCancelRequest())
    );
    verify(access).require(
      PermissionCodes.BORROW_APPLICATION_CANCEL,
      DataScope.SELF
    );
  }

  private BorrowApplicationServiceImpl service(
    BorrowAccess access,
    BorrowApplicationMapper applications
  ) {
    return new BorrowApplicationServiceImpl(
      mock(com.yunfeil161.ailibrary.policy.service.PolicyState.class),
      access,
      applications,
      mock(BorrowRecordMapper.class),
      mock(BorrowApplicationExpirationService.class),
      mock(BorrowMemberMapper.class),
      mock(BookMapper.class),
      mock(BookCopyMapper.class),
      mock(CacheClient.class),
      mock(PendingBorrowApplicationGuard.class),
      mock(com.yunfeil161.ailibrary.communication.notification.service.BusinessNotifications.class)
    );
  }
}
