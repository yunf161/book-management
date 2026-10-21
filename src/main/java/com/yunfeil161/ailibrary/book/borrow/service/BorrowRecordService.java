package com.yunfeil161.ailibrary.book.borrow.service;

import com.yunfeil161.ailibrary.book.borrow.dto.*;
import com.yunfeil161.ailibrary.book.borrow.entity.*;
import com.yunfeil161.ailibrary.common.api.PageResult;

/**
 * 借阅记录业务接口。
 */
public interface BorrowRecordService {
    /** 查询当前成员自己的数据。 */
    PageResult<BorrowRecordDetailVO> queryMine(BorrowRecordQueryRequest q);

    PageResult<BorrowRecordDetailVO> queryAdmin(BorrowRecordQueryRequest q);

    /** 根据借阅记录办理归还。 */
    BorrowRecord returnByRecord(Long id, BorrowReturnRequest r);

    BorrowRecord returnByBarcode(String barcode, BorrowReturnRequest r);
}
