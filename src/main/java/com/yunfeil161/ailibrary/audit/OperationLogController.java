package com.yunfeil161.ailibrary.audit;

import com.yunfeil161.ailibrary.common.api.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 仅授权查询，不提供审计修改、删除或公开查询入口。 */
@RestController
@RequiredArgsConstructor
public class OperationLogController {

  private final OperationLogQueryService service;

  @GetMapping("/admin/operation-logs")
  public Result query(
    @ModelAttribute OperationLogQuery query,
    HttpServletResponse response
  ) {
    response.setHeader("Cache-Control", "no-store");
    return Result.success(service.query(query));
  }
}
