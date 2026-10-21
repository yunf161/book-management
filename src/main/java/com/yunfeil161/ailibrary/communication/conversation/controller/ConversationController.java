package com.yunfeil161.ailibrary.communication.conversation.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.communication.conversation.dto.*;
import com.yunfeil161.ailibrary.communication.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 会话聚合接口；权限和事务统一放在Service。 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {
  private final ConversationService service;

  @PostMapping
  public Result create(@RequestBody ConversationCreateRequest q) {
    return Result.success(service.create(q));
  }

  @GetMapping
  public Result query(@ModelAttribute ConversationQueryRequest q) {
    return Result.success(service.query(q));
  }

  @PutMapping("/{id}")
  public Result update(@PathVariable Long id, @RequestBody ConversationUpdateRequest q) {
    return Result.success(service.update(id, q));
  }
}
