import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { SourceTextModule, SyntheticModule, createContext } from "node:vm";
import { maintenanceFields } from "../src/modules/memberMaintenance.js";

function maintenanceAccess(grants) {
  return { memberId: 99, has: (code, scope) => grants[code] === scope };
}

test("馆员可维护读者额度和已有资格，不可管理账号或员工", () => {
  const access = maintenanceAccess({
    "member:query": "ALL",
    "borrow-quota-change:approve": "ALL",
    "reader-qualification:approve": "ALL",
  });
  assert.deepEqual(
    maintenanceFields(access, "READER", {
      id: 1,
      qualificationStatus: "APPROVED",
    }),
    ["permanentBorrowLimit", "qualificationStatus"],
  );
  assert.deepEqual(
    maintenanceFields(access, "READER", {
      id: 1,
      qualificationStatus: "NOT_APPLIED",
    }),
    ["permanentBorrowLimit"],
  );
  assert.deepEqual(maintenanceFields(access, "EMPLOYEE", { id: 1 }), []);
  assert.deepEqual(maintenanceFields(access, "READER", { id: 99 }), []);
});

test("管理员按专项授权显示员工维护字段，不展示人事角色和凭据编辑", () => {
  const access = maintenanceAccess({
    "member:query": "ALL",
    "employee-profile-change:approve": "ALL",
    "borrow-quota-change:approve": "ALL",
    "member-account-change:approve": "ALL",
    "reader-qualification:approve": "ALL",
  });
  assert.deepEqual(
    maintenanceFields(access, "EMPLOYEE", {
      id: 1,
      qualificationStatus: "SUSPENDED",
    }),
    ["permanentBorrowLimit", "accountStatus", "qualificationStatus"],
  );
});

test("通用成员管理权限不能替代字段权限，SELF授权不能用于他人维护", () => {
  assert.deepEqual(
    maintenanceFields(
      maintenanceAccess({ "member:query": "ALL", "member:update": "ALL" }),
      "READER",
      { id: 1 },
    ),
    [],
  );
  assert.deepEqual(
    maintenanceFields(
      maintenanceAccess({
        "member:query": "ALL",
        "borrow-quota-change:approve": "SELF",
      }),
      "READER",
      { id: 1 },
    ),
    [],
  );
});

test("操作审计入口默认隐藏，SELF范围也不能进入", async () => {
  const h = await harness(async () => ({
    memberId: 1,
    permissions: [{ code: "operation-log:query", dataScope: "SELF" }],
  }));
  assert.equal(h.state.canEnter("/admin/operation-logs"), false);
  await h.state.refreshPermissions();
  assert.equal(h.state.canEnter("/admin/operation-logs"), false);
});

test("操作审计入口仅显式ALL授权可见", async () => {
  const h = await harness(async () => ({
    memberId: 1,
    permissions: [{ code: "operation-log:query", dataScope: "ALL" }],
  }));
  await h.state.refreshPermissions();
  assert.equal(h.state.canEnter("/admin/operation-logs"), true);
  h.state.clearPermissions();
  assert.equal(h.state.canEnter("/admin/operation-logs"), false);
});

async function harness(query) {
  const session = {
    token: "test",
    claims: { memberId: 1, memberType: "EMPLOYEE" },
  };
  const context = createContext({ console });
  const module = new SourceTextModule(
    await readFile(
      new URL("../src/stores/permissions.js", import.meta.url),
      "utf8",
    ),
    { context },
  );
  const dependencies = {
    vue: { reactive: (value) => value },
    "../api/library": { api: { currentPermissions: query } },
    "./session": { session },
  };
  await module.link(
    (name) =>
      new SyntheticModule(
        Object.keys(dependencies[name]),
        function () {
          for (const [key, value] of Object.entries(dependencies[name]))
            this.setExport(key, value);
        },
        { context },
      ),
  );
  await module.evaluate();
  return { state: module.namespace, session };
}

test("人员查询授权不再开放旧AI成员页面，AI任务入口仍独立授权", async () => {
  const h = await harness(async () => ({
    memberId: 1,
    permissions: [
      { code: "member:query", dataScope: "ALL" },
      { code: "ai-task:query", dataScope: "SELF" },
    ],
  }));
  await h.state.refreshPermissions();
  assert.equal(h.state.canEnter("/admin/staff"), true);
  assert.equal(h.state.canEnter("/admin/agents"), false);
  assert.equal(h.state.canEnter("/ai/tasks"), true);
});

test("未同步与退出后的按钮默认关闭", async () => {
  const h = await harness(async () => ({
    memberId: 1,
    permissions: [{ code: "book:create", dataScope: "NONE" }],
  }));
  assert.equal(h.state.access.has("book:create"), false);
  await h.state.refreshPermissions();
  assert.equal(h.state.access.has("book:create"), true);
  h.session.token = "";
  h.state.clearPermissions();
  assert.equal(h.state.access.has("book:create"), false);
});

test("SELF权限只能显示本人操作，NONE不能用于ALL数据查询", async () => {
  const h = await harness(async () => ({
    memberId: 1,
    permissions: [
      { code: "member:update", dataScope: "SELF" },
      { code: "department:create", dataScope: "NONE" },
    ],
  }));
  await h.state.refreshPermissions();
  assert.equal(h.state.access.forMember("member:update", 1), true);
  assert.equal(h.state.access.forMember("member:update", 2), false);
  assert.equal(h.state.access.has("department:create"), true);
  assert.equal(h.state.access.has("department:create", "ALL"), false);
});

test("撤销授权后导航和按钮同步消失，不依赖角色编码", async () => {
  let permissions = [
    { code: "permission:query", dataScope: "ALL" },
    { code: "permission:create", dataScope: "ALL" },
  ];
  const h = await harness(async () => ({ memberId: 1, permissions }));
  await h.state.refreshPermissions();
  assert.equal(h.state.canEnter("/admin/permissions"), true);
  permissions = [];
  await h.state.refreshPermissions();
  assert.equal(h.state.canEnter("/admin/permissions"), false);
  assert.equal(h.state.access.has("permission:create"), false);
});

test("并发请求合并，身份切换后的旧响应不能恢复旧授权", async () => {
  let resolve,
    calls = 0;
  const h = await harness(() => {
    calls++;
    return new Promise((r) => (resolve = r));
  });
  const a = h.state.refreshPermissions(),
    b = h.state.refreshPermissions();
  await Promise.resolve();
  assert.equal(calls, 1);
  h.state.clearPermissions();
  h.session.claims.memberId = 2;
  resolve({
    memberId: 1,
    permissions: [{ code: "permission:create", dataScope: "ALL" }],
  });
  await Promise.all([a, b]);
  assert.equal(h.state.access.has("permission:create"), false);
});

test("同一成员切换角色时丢弃旧角色的迟到响应", async () => {
  let resolve;
  const h = await harness(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  h.session.claims.roleId = 10;
  const pending = h.state.refreshPermissions();
  await Promise.resolve();
  h.session.claims.roleId = 20;
  resolve({
    memberId: 1,
    roleId: 10,
    permissions: [{ code: "permission:create", dataScope: "ALL" }],
  });
  await pending;
  assert.equal(h.state.access.has("permission:create"), false);
  assert.equal(h.state.access.ready, false);
});

test("查询失败关闭旧授权，恢复后可重新同步", async () => {
  let fail = false;
  const h = await harness(async () => {
    if (fail) throw new Error("network");
    return {
      memberId: 1,
      permissions: [{ code: "book:create", dataScope: "NONE" }],
    };
  });
  await h.state.refreshPermissions();
  fail = true;
  await h.state.refreshPermissions();
  assert.equal(h.state.access.has("book:create"), false);
  fail = false;
  await h.state.refreshPermissions();
  assert.equal(h.state.access.has("book:create"), true);
});

test("知识库入口只对拥有ALL查询权限的角色开放", async () => {
  const h = await harness(async () => ({
    memberId: 1,
    permissions: [{ code: "knowledge:query", dataScope: "SELF" }],
  }));
  await h.state.refreshPermissions();
  assert.equal(h.state.canEnter("/admin/knowledge"), false);
  h.state.access.grants = [{ code: "knowledge:query", dataScope: "ALL" }];
  assert.equal(h.state.canEnter("/admin/knowledge"), true);
  h.state.clearPermissions();
  assert.equal(h.state.canEnter("/admin/knowledge"), false);
});
