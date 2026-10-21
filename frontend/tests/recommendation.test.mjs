import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { recommendationPresentation } from "../src/modules/recommendationPresentation.js";

test("关闭个性化和空推荐都展示热门模式，重新开启恢复兴趣说明", () => {
  assert.equal(recommendationPresentation(false).title, "热门图书推荐");
  assert.equal(recommendationPresentation(undefined).title, "热门图书推荐");
  assert.ok(!recommendationPresentation(false).empty.includes("选择兴趣"));
  assert.equal(recommendationPresentation(true).title, "为你推荐");
  assert.ok(recommendationPresentation(true).description.includes("兴趣标签"));
});

// 直接加载实际API模块，核对URL、请求字段和刷新幂等头。
async function harness() {
  const calls = [];
  const http = {};
  for (const method of ["get", "post", "put", "delete"])
    http[method] = (...args) => {
      calls.push({ method, args });
      return Promise.resolve({});
    };
  const context = vm.createContext({});
  const mod = new vm.SourceTextModule(
    fs.readFileSync(new URL("../src/api/library.js", import.meta.url), "utf8"),
    { context },
  );
  await mod.link(
    () =>
      new vm.SyntheticModule(
        ["default"],
        function () {
          this.setExport("default", http);
        },
        { context },
      ),
  );
  await mod.evaluate();
  return { api: mod.namespace.api, calls };
}
test("当前推荐不传成员或历史分页参数", async () => {
  const { api, calls } = await harness();
  await api.currentRecommendations();
  assert.equal(calls[0].method, "get");
  assert.deepEqual(calls[0].args, ["/me/recommendations"]);
});
test("刷新同一次操作携带固定幂等头", async () => {
  const { api, calls } = await harness();
  await api.refreshRecommendations("fixed-request-001");
  assert.equal(calls[0].args[0], "/me/recommendations/refresh");
  assert.equal(
    calls[0].args[2].headers["Idempotency-Key"],
    "fixed-request-001",
  );
});
test("兴趣支持空列表全量清除", async () => {
  const { api, calls } = await harness();
  await api.updateInterests([]);
  assert.equal(calls[0].args[0], "/me/interests");
  assert.equal(calls[0].args[1].tagIds.length, 0);
});
test("反馈使用条目ID而不是书目ID", async () => {
  const { api, calls } = await harness();
  await api.recommendationFeedback(12, "ALREADY_READ");
  assert.equal(calls[0].args[0], "/me/recommendations/12/feedback");
  assert.equal(calls[0].args[1].kind, "ALREADY_READ");
});
test("独立书目标签更新带版本", async () => {
  const { api, calls } = await harness();
  await api.updateBookTags(21, [3, 4], 7);
  assert.equal(calls[0].args[0], "/admin/books/21/tags");
  assert.equal(calls[0].args[1].expectedVersion, 7);
});
