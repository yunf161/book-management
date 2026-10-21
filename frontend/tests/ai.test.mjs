import test from "node:test";
import assert from "node:assert/strict";
import { readAiEvents } from "../src/modules/aiStream.js";

test("流式用量事件与现有回答协议兼容", async () => {
  const seen = [];
  await readAiEvents(
    response(
      'event: usage\ndata: {"calls":[{"provider":"ollama","promptTokens":10,"completionTokens":20}]}\n\nevent: done\ndata: {}\n\n',
    ),
    (event, value) => seen.push([event, value]),
  );
  assert.equal(seen[0][0], "usage");
  assert.equal(seen[0][1].calls[0].promptTokens, 10);
});

function response(text, size = 7) {
  const raw = new TextEncoder().encode(text);
  return new Response(
    new ReadableStream({
      start(controller) {
        for (let n = 0; n < raw.length; n += size)
          controller.enqueue(raw.slice(n, n + size));
        controller.close();
      },
    }),
  );
}
test("SSE中文跨网络包仍正确，只有done才确认成功", async () => {
  const events = [];
  await readAiEvents(
    response(
      'event: delta\ndata: {"text":"图书结果"}\n\nevent: done\ndata: {}\n\n',
      1,
    ),
    (event, data) => events.push([event, data]),
  );
  assert.equal(events[0][1].text, "图书结果");
  assert.equal(events.at(-1)[0], "done");
});
test("断流不能把半轮回答当成功", async () => {
  await assert.rejects(
    readAiEvents(
      response('event: delta\ndata: {"text":"部分答案"}\n\n'),
      () => {},
    ),
    /提前中断/,
  );
});
test("error事件拒绝，保留状态供界面处理", async () => {
  await assert.rejects(
    readAiEvents(
      response('event: error\ndata: {"code":403,"message":"权限不足"}\n\n'),
      () => {},
    ),
    (error) => error.status === 403,
  );
});
test("未知事件和畸形JSON都拒绝", async () => {
  for (const text of [
    "event: script\ndata: {}\n\n",
    "event: delta\ndata: invalid\n\n",
  ]) {
    await assert.rejects(readAiEvents(response(text), () => {}));
  }
});
test("来源对象不执行内容，只交给页面文本展示", async () => {
  let sources;
  await readAiEvents(
    response(
      'event: sources\ndata: {"sources":[{"tool":"search_books","data":"<script>bad</script>"}]}\n\nevent: done\ndata: {}\n\n',
    ),
    (event, value) => {
      if (event === "sources") sources = value.sources;
    },
  );
  assert.equal(sources[0].data, "<script>bad</script>");
});

test("CRLF跨包仍可解析业务草稿，草稿本身不会调用确认接口", async () => {
  const events = [];
  const action = {
    actionId: "draft-1",
    requestHash: "hash",
    skill: "plan_create",
    state: "PREPARED",
  };
  await readAiEvents(
    response(
      `event: action\r\ndata: ${JSON.stringify(action)}\r\n\r\nevent: done\r\ndata: {}\r\n\r\n`,
      1,
    ),
    (name, value) => events.push([name, value]),
  );
  assert.deepEqual(
    events.map(([name]) => name),
    ["action", "done"],
  );
  assert.equal(events[0][1].state, "PREPARED");
});

test("畸形业务草稿、空对象及错误来源不能当成正常回答", async () => {
  for (const [name, value] of [
    ["action", {}],
    [
      "action",
      { actionId: "x", requestHash: "x", skill: "x", state: "SUCCEEDED" },
    ],
    ["delta", null],
    ["delta", { text: 1 }],
    ["sources", { sources: {} }],
  ]) {
    await assert.rejects(
      readAiEvents(
        response(
          `event: ${name}\ndata: ${JSON.stringify(value)}\n\nevent: done\ndata: {}\n\n`,
        ),
        () => {},
      ),
    );
  }
});
