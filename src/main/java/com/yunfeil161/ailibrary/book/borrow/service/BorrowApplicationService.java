package com.yunfeil161.ailibrary.book.borrow.service;

import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.entity.*;
import com.yunfeil161.ailibrary.common.api.PageResult;

/**
 * 借阅申请业务接口。
 */
public interface BorrowApplicationService {
    /** 创建当前成员的借阅申请。 */
    BorrowApplication create(BorrowApplicationCreateRequest r);

    PageResult<BorrowApplicationDetailVO> queryMine(BorrowApplicationQueryRequest q);

    /** 按管理端条件查询数据。 */
    PageResult<BorrowApplicationDetailVO> queryAdmin(BorrowApplicationQueryRequest q);

    BorrowApplication cancel(Long id, BorrowApplicationCancelRequest r);

    /** 按状态和版本批准申请。 */
    BorrowApplication approve(Long id, BorrowApplicationApproveRequest r);

    BorrowApplication reject(Long id, BorrowApplicationRejectRequest r);
}
