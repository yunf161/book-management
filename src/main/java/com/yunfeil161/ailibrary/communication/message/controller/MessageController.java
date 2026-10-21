package com.yunfeil161.ailibrary.communication.message.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.communication.message.dto.*;
import com.yunfeil161.ailibrary.communication.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 消息聚合接口；查询不会隐式标记已读。 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {
  private final MessageService service;

  @PostMapping
  public Result send(@RequestBody MessageSendRequest request) {
    return Result.success(service.send(request));
  }

  @GetMapping
  public Result query(@ModelAttribute MessageQueryRequest request) {
    return Result.success(service.query(request));
  }

  @PostMapping("/{id}/read")
  public Result read(@PathVariable Long id) {
    service.read(id);
    return Result.success();
  }
}
