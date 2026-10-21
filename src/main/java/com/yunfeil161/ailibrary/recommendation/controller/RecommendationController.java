package com.yunfeil161.ailibrary.recommendation.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.recommendation.dto.*;
import com.yunfeil161.ailibrary.recommendation.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 只提供当前推荐，不增加历史批次查询或任意成员查询接口。 */
@RestController
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendationService recommendations;
  private final TagService tags;

  @GetMapping("/interest-tags")
  public Result tags(
    @RequestParam(required = false) Long id,
    @RequestParam(required = false) String code,
    @RequestParam(required = false) String name
  ) {
    return Result.success(tags.query(id, code, name));
  }

  @GetMapping("/me/interests")
  public Result interests() {
    return Result.success(tags.myInterests());
  }

  @PutMapping("/me/interests")
  public Result interests(@RequestBody TagsRequest request) {
    if (request == null) throw new WrongFormatException("tagIds不能为空");
    return Result.success(tags.replaceMyInterests(request.getTagIds()));
  }

  @PutMapping("/admin/books/{bookId}/tags")
  public Result bookTags(
    @PathVariable Long bookId,
    @RequestBody BookTagsRequest request
  ) {
    return Result.success(
      tags.replaceBookTags(
        bookId,
        request.getTagIds(),
        request.getExpectedVersion()
      )
    );
  }

  @GetMapping("/me/recommendations")
  public Result current() {
    return Result.success(recommendations.current());
  }

  @PostMapping("/me/recommendations/refresh")
  public Result refresh(
    @RequestHeader(value = "Idempotency-Key", required = false) String requestId
  ) {
    return Result.success(recommendations.refresh(requestId));
  }

  @PostMapping("/me/recommendations/{itemId}/feedback")
  public Result feedback(
    @PathVariable Long itemId,
    @RequestBody FeedbackRequest request
  ) {
    return Result.success(recommendations.feedback(itemId, request));
  }
}
