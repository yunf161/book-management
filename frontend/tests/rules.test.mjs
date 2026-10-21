import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { SourceTextModule, SyntheticModule, createContext } from "node:vm";

async function harness(get, post = async () => ({})) {
  const session = {
    token: "test",
    claims: { memberId: 1, memberType: "READER" },
    clear() {
      this.token = "";
    },
  };
  const context = createContext({ console });
  const module = new SourceTextModule(
    await readFile(new URL("../src/stores/rules.js", import.meta.url), "utf8"),
    { context },
  );
  const dependencies = {
    vue: { reactive: (value) => value },
    "../api/http": { default: { get, post } },
    "./session": { session },
  };
  await module.link(
    (name) =>
      new SyntheticModule(
        Object.keys(dependencies[name]),
        function () {
          for (const [k, v] of Object.entries(dependencies[name]))
            this.setExport(k, v);
        },
        { context },
      ),
  );
  await module.evaluate();
  return { state: module.namespace, session };
}

test("未确认规则保持受限状态，接受后才解除", async () => {
  const h = await harness(
    async () => ({
      requiresConsent: true,
      ruleVersion: "BORROWING-1",
      memberVersion: 0,
    }),
    async () => ({
      requiresConsent: false,
      ruleVersion: "BORROWING-1",
      memberVersion: 1,
    }),
  );
  await h.state.refreshRules();
  assert.equal(h.state.rules.value.requiresConsent, true);
  await h.state.decideRules("ACCEPT");
  assert.equal(h.state.rules.value.requiresConsent, false);
});
test("拒绝规则清除本地会话", async () => {
  const h = await harness(async () => ({
    requiresConsent: true,
    ruleVersion: "BORROWING-1",
    memberVersion: 0,
  }));
  await h.state.refreshRules();
  await h.state.decideRules("DECLINE");
  assert.equal(h.session.token, "");
});
test("规则查询失败时关闭业务入口", async () => {
  const h = await harness(async () => {
    throw new Error("network");
  });
  await h.state.refreshRules();
  assert.equal(h.state.rules.ready, false);
  assert.ok(h.state.rules.error);
});
test("账号切换不能接收旧账号规则响应", async () => {
  let resolve;
  const h = await harness(
    () =>
      new Promise((r) => {
        resolve = r;
      }),
  );
  const pending = h.state.refreshRules();
  h.session.claims.memberId = 2;
  resolve({ requiresConsent: false });
  await pending;
  assert.equal(h.state.rules.ready, false);
});

test("old failed request cannot close a new identity's ready state", async () => {
  let reject;
  let count = 0;
  const h = await harness(() =>
    ++count === 1
      ? new Promise((_, r) => {
          reject = r;
        })
      : Promise.resolve({ requiresConsent: false }),
  );
  const old = h.state.refreshRules();
  h.state.clearRules();
  h.session.claims.memberId = 2;
  await h.state.refreshRules();
  reject(new Error("old network failure"));
  await old;
  assert.equal(h.state.rules.ready, true);
});
