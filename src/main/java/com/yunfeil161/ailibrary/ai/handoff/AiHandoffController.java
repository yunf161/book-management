package com.yunfeil161.ailibrary.ai.handoff;

import com.yunfeil161.ailibrary.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 交接查询聚合，不新增分散的本人/馆员列表接口。 */
@RestController
@RequestMapping("/ai/handoffs")
@RequiredArgsConstructor
public class AiHandoffController {

  private final AiHandoffService service;

  public record Decision(String decision, Long expectedVersion) {}

  @GetMapping
  public Result query(@ModelAttribute AiHandoffQuery q) {
    return Result.success(service.query(q));
  }

  @PostMapping
  public Result create(@RequestBody AiHandoffService.Request q) {
    return Result.success(service.create(q));
  }

  @PostMapping("/{id}/decision")
  public Result decide(@PathVariable Long id, @RequestBody Decision q) {
    return Result.success(
      service.decide(id, q.decision(), q.expectedVersion())
    );
  }
}
