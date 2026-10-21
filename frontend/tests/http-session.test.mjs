import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { SourceTextModule, SyntheticModule, createContext } from "node:vm";
import { recoverNavigation } from "../src/modules/navigationRecovery.js";

test("旧路由分包失败后保留目标路径并最多恢复一次", () => {
  const values = new Map(),
    assigned = [];
  const browser = {
    location: {
      origin: "http://127.0.0.1:18080",
      assign: (path) => assigned.push(path),
    },
    sessionStorage: {
      getItem: (key) => values.get(key),
      setItem: (key, value) => values.set(key, value),
    },
  };
  const error = new Error(
    "Failed to fetch dynamically imported module: /assets/old.js",
  );
  assert.equal(
    recoverNavigation(error, "/ai/tasks?page=2#results", browser),
    true,
  );
  assert.deepEqual(assigned, ["/ai/tasks?page=2#results"]);
  assert.equal(recoverNavigation(error, "/ai/tasks", browser), false);
  assert.equal(assigned.length, 1);
});

test("普通业务错误和外部地址不能触发页面跳转", () => {
  const browser = {
    location: {
      origin: "http://127.0.0.1:18080",
      assign: () => assert.fail("不应跳转"),
    },
  };
  assert.equal(
    recoverNavigation(
      new Error("403 Forbidden"),
      "/admin/ai-controls",
      browser,
    ),
    false,
  );
  assert.equal(
    recoverNavigation(
      new Error("Importing a module script failed"),
      "https://example.com/",
      browser,
    ),
    false,
  );
});

test("浏览器存储不可用时不进行不可控的循环刷新", () => {
  const browser = {
    location: {
      origin: "http://127.0.0.1:18080",
      assign: () => assert.fail("不应跳转"),
    },
    sessionStorage: {
      getItem: () => {
        throw new Error("Storage disabled");
      },
    },
  };
  assert.equal(
    recoverNavigation(
      new Error("Unable to preload CSS"),
      "/admin/plans",
      browser,
    ),
    false,
  );
});

async function harness() {
  let request, success, failure;
  const notices = [],
    events = [];
  const session = {
    token: "old",
    user: {},
    claims: { memberId: 1, roleId: 2 },
    saveToken(token) {
      this.token = token;
    },
    clear() {
      this.token = "";
      this.user = null;
    },
  };
  const client = {
    interceptors: {
      request: {
        use(fn) {
          request = fn;
        },
      },
      response: {
        use(ok, fail) {
          success = ok;
          failure = fail;
        },
      },
    },
  };
  const context = createContext({
    console,
    window: {
      dispatchEvent(e) {
        events.push(e);
      },
    },
    Event: class {},
  });
  const module = new SourceTextModule(
    await readFile(new URL("../src/api/http.js", import.meta.url), "utf8"),
    { context },
  );
  const deps = {
    axios: { default: { create: () => client } },
    "element-plus": { ElMessage: { error: (x) => notices.push(x) } },
    "../stores/session": { session },
  };
  await module.link(
    (name) =>
      new SyntheticModule(
        Object.keys(deps[name]),
        function () {
          for (const [key, value] of Object.entries(deps[name]))
            this.setExport(key, value);
        },
        { context },
      ),
  );
  await module.evaluate();
  return { session, request, success, failure, notices, events };
}

test("切换账号后迟到的成功响应不能返回旧数据或恢复旧JWT", async () => {
  const h = await harness(),
    config = h.request({ headers: {} });
  h.session.user = {};
  h.session.claims = { memberId: 9, roleId: 3 };
  h.session.token = "new";
  await assert.rejects(
    h.success({
      config,
      headers: { "x-refreshed-token": "old-rotated" },
      data: { data: "old-private-data" },
    }),
    (e) => e.code === "STALE_SESSION",
  );
  assert.equal(h.session.token, "new");
});

test("旧请求401或规则失效不能注销新登录实例或弹窗", async () => {
  const h = await harness(),
    config = h.request({ headers: {} });
  // 即使成员和角色相同，重新登录后的user引用也不同。
  h.session.user = {};
  h.session.token = "new-login";
  await assert.rejects(
    h.failure({
      config,
      response: {
        status: 401,
        headers: { "x-error-code": "RULE_CONSENT_REQUIRED" },
      },
    }),
    (e) => e.code === "STALE_SESSION",
  );
  assert.equal(h.session.token, "new-login");
  assert.equal(h.events.length, 0);
  assert.equal(h.notices.length, 0);
});

test("同一登录实例的并发JWT轮换仍接受，当前401仍正常退出", async () => {
  const h = await harness(),
    a = h.request({ headers: {} }),
    b = h.request({ headers: {} });
  h.success({
    config: a,
    headers: { "x-refreshed-token": "rotated" },
    data: { data: 1 },
  });
  assert.equal(h.success({ config: b, headers: {}, data: { data: 2 } }), 2);
  await assert.rejects(
    h.failure({
      config: b,
      response: { status: 401, headers: {}, data: { msg: "expired" } },
    }),
  );
  assert.equal(h.session.token, "");
});

test("损坏浏览器存储和禁用存储都不阻止登录页启动", async () => {
  for (const disabled of [false, true]) {
    const context = createContext({
      localStorage: {
        getItem() {
          if (disabled) throw Error("disabled");
          return "bad-json";
        },
        removeItem() {
          if (disabled) throw Error("disabled");
        },
      },
    });
    const module = new SourceTextModule(
      await readFile(
        new URL("../src/stores/session.js", import.meta.url),
        "utf8",
      ),
      { context },
    );
    await module.link(
      () =>
        new SyntheticModule(
          ["reactive"],
          function () {
            this.setExport("reactive", (x) => x);
          },
          { context },
        ),
    );
    await module.evaluate();
    assert.equal(module.namespace.session.token, "");
  }
});
