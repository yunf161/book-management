import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { SourceTextModule, SyntheticModule, createContext } from "node:vm";
import { parse, compileScript, compileTemplate } from "@vue/compiler-sfc";
import * as Vue from "vue";
import { renderToString } from "@vue/server-renderer";
import {
  displayValue,
  statusTone,
  progressPercentage,
} from "../src/modules/display.js";
import * as recordVersions from "../src/modules/recordVersions.js";
import { dtoFields } from "../src/modules/dtoFields.js";

// 编译并真正渲染共享组件，防止详情中的函数/数组类型错误被纯字段测试漏掉。
async function renderDetails(record, updateFields, options = {}) {
  const filename = "ResourceDesk.vue";
  const source = await readFile(
    new URL("../src/components/ResourceDesk.vue", import.meta.url),
    "utf8",
  );
  const { descriptor } = parse(source, { filename });
  const script = compileScript(descriptor, { id: "resource-desk-test" });
  const template = compileTemplate({
    source: descriptor.template.content,
    filename,
    id: "resource-desk-test",
    compilerOptions: { bindingMetadata: script.bindings },
  });
  assert.deepEqual(template.errors, []);
  const context = createContext({ console });
  const stub = Vue.defineComponent({
    inheritAttrs: false,
    setup:
      (_, { attrs, slots }) =>
      () =>
        Vue.h("div", { class: attrs.class }, [
          attrs.label,
          slots.default?.({ row: record }),
        ]),
  });
  function module(exports) {
    return new SyntheticModule(
      Object.keys(exports),
      function () {
        for (const [name, value] of Object.entries(exports))
          this.setExport(name, value);
      },
      { context },
    );
  }
  const dependencies = {
    vue: module(Vue),
    "element-plus": module({ ElMessage: { success() {} }, ElMessageBox: {} }),
    "./DtoFields.vue": module({ default: stub }),
    "./PageSummary.vue": module({ default: stub }),
    "../modules/display": module({
      displayValue,
      statusTone,
      progressPercentage,
    }),
    "../modules/recordVersions": module(recordVersions),
  };
  const compiled = new SourceTextModule(
    `${script.content.replace("export default", "const component =")}
     ${template.code}
     component.render = render;
     export default component;`,
    { context },
  );
  await compiled.link((name) => {
    assert.ok(dependencies[name], `未处理的依赖：${name}`);
    return dependencies[name];
  });
  await compiled.evaluate();
  const component = compiled.namespace.default;
  const originalSetup = component.setup;
  component.setup = (props, ctx) => {
    const state = originalSetup(props, ctx);
    state.details.value = record;
    options.capture?.(state);
    return state;
  };
  const app = Vue.createSSRApp(component, {
    title: "员工管理",
    queryFields: options.queryFields || [{ key: "name", label: "姓名" }],
    createFields: [{ key: "employeeNo", label: "工号" }],
    updateFields,
    columns: [{ key: "departmentId", label: "部门编号" }],
    list: options.list || (async () => ({ rows: [], total: 0 })),
    update: options.update,
    initialQuery: options.initialQuery,
    pageKey: options.pageKey,
  });
  for (const name of [
    "ElButton",
    "ElEmpty",
    "ElCollapse",
    "ElCollapseItem",
    "ElForm",
    "ElTable",
    "ElTableColumn",
    "ElTag",
    "ElProgress",
    "ElPagination",
    "ElDialog",
    "ElDescriptions",
    "ElDescriptionsItem",
  ])
    app.component(name, stub);
  app.directive("loading", {});
  return renderToString(app);
}

test("员工与馆员详情支持动态编辑字段，仍完整显示只读资料", async () => {
  const row = {
    id: 2,
    name: "馆员甲",
    employeeNo: "EMP-002",
    departmentId: 7,
    permanentBorrowLimit: 8,
    passwordHash: "must-not-render",
  };
  const html = await renderDetails(row, (record) => {
    // 详情必须使用详情记录，而非尚未打开的编辑记录。
    return record?.id === 2
      ? [{ key: "permanentBorrowLimit", label: "长期借阅额度" }]
      : [];
  });
  for (const value of ["馆员甲", "EMP-002", "工号", "部门编号", "长期借阅额度"])
    assert.ok(html.includes(value), value);
  assert.ok(!html.includes("must-not-render"));
});

test("重置筛选保留固定成员范围和每页条数，并且只发送正确分页协议", async () => {
  let state;
  const requests = [];
  const initial = { memberId: 7, page: 9 };
  await renderDetails({}, [], {
    initialQuery: initial,
    pageKey: "pageNum",
    list: async (query) => {
      requests.push(query);
      return { rows: [], total: 0 };
    },
    capture: (value) => {
      state = value;
    },
  });
  Object.assign(state.query, {
    memberId: 7,
    name: "旧查询",
    pageNum: 4,
    pageSize: 50,
  });
  await state.resetFilters();
  assert.deepEqual(JSON.parse(JSON.stringify(requests)), [
    { memberId: 7, pageNum: 1, pageSize: 50 },
  ]);
  assert.deepEqual(
    initial,
    { memberId: 7, page: 9 },
    "不能修改父组件传入的固定范围",
  );
});

test("筛选提示计入0和false，忽略空值、固定范围及隐藏版本字段", async () => {
  let state;
  await renderDetails({}, [], {
    queryFields: [
      "count",
      "enabled",
      "memberId",
      "name",
      "tags",
      "expectedVersion",
    ].map((key) => ({ key, label: key })),
    initialQuery: { memberId: 7 },
    capture: (value) => {
      state = value;
    },
  });
  Object.assign(state.query, {
    count: 0,
    enabled: false,
    memberId: 7,
    name: "",
    tags: [],
    expectedVersion: 42,
  });
  assert.equal(state.filterCount.value, 2);
});

test("重置后的查询先完成时，较晚返回的旧筛选结果不能覆盖新列表", async () => {
  let state;
  const pending = [];
  await renderDetails({}, [], {
    list: () => new Promise((resolve) => pending.push(resolve)),
    capture: (value) => {
      state = value;
    },
  });
  state.query.name = "旧查询";
  const oldRequest = state.load(true);
  const resetRequest = state.resetFilters();
  pending[1]({ rows: [{ id: 2, name: "重置结果" }], total: 1 });
  await resetRequest;
  pending[0]({ rows: [{ id: 1, name: "旧结果" }], total: 1 });
  await oldRequest;
  assert.equal(state.rows.value[0].name, "重置结果");
  assert.equal(state.busy.value, false);
  assert.equal(state.query.name, undefined);
});

test("部门和权限详情继续兼容普通字段数组", async () => {
  const html = await renderDetails(
    { name: "借阅部", description: "负责借还书" },
    [{ key: "description", label: "说明" }],
  );
  assert.ok(html.includes("借阅部"));
  assert.ok(html.includes("负责借还书"));
  assert.ok(html.includes("说明"));
});

test("无编辑权限的记录仍能查看详情", async () => {
  const html = await renderDetails(
    { name: "只读员工", employeeNo: "EMP-003" },
    () => [],
  );
  assert.ok(html.includes("只读员工"));
  assert.ok(html.includes("EMP-003"));
});

test("详情隐藏所有乐观锁版本，仍保留用户已接受的规则版本", async () => {
  const html = await renderDetails(
    {
      name: "版本隐藏测试",
      version: 987654,
      expectedMemberVersion: 987655,
      expectedProfileVersion: 987656,
      acceptedRuleVersion: "BORROWING-2",
    },
    [{ key: "expectedVersion", label: "申请版本" }],
  );
  assert.ok(!html.includes("987654"));
  assert.ok(!html.includes("987655"));
  assert.ok(!html.includes("987656"));
  assert.ok(html.includes("BORROWING-2"));
});

test("全部DTO表单隐藏乐观锁输入，保留规则接受版本的业务含义", () => {
  for (const [name, fields] of Object.entries(dtoFields))
    assert.ok(
      recordVersions
        .visibleFields(fields)
        .every((field) => !recordVersions.isLockVersion(field.key)),
      name,
    );
  const visible = recordVersions.visibleFields([
    { key: "expectedMemberVersion" },
    { key: "expectedProfileVersion" },
    { key: "expectedPlanVersion" },
    { key: "expectedVersion" },
    { key: "version" },
    { key: "acceptedRuleVersion" },
    { key: "policyVersion" },
  ]);
  assert.deepEqual(
    visible.map((field) => field.key),
    ["acceptedRuleVersion", "policyVersion"],
  );
});

test("自动版本携带0与递增版本，缺失版本时拒绝伪造默认值", () => {
  assert.equal(recordVersions.recordVersion({ version: 0 }), 0);
  assert.equal(recordVersions.recordVersion({ version: 12 }), 12);
  for (const version of [undefined, null, -1, "0", 1.5])
    assert.throws(() => recordVersions.recordVersion({ version }));
});

test("实际编辑提交自动携带查询版本，不接受表单中的伪造版本", async () => {
  let state;
  let submitted;
  const row = { id: 10, name: "计划名称", version: 7 };
  await renderDetails(
    row,
    [{ key: "name" }, { key: "version" }, { key: "expectedVersion" }],
    {
      capture: (value) => {
        state = value;
      },
      update: async (_, body) => {
        submitted = body;
      },
    },
  );
  state.open(row);
  state.form.name = "更新名称";
  state.form.version = 999;
  state.form.expectedVersion = 999;
  await state.save();
  assert.equal(submitted.name, "更新名称");
  assert.equal(submitted.version, 7);
  assert.equal(submitted.expectedVersion, 7);
});

test("业务无匹配404保留提示，403、503和路由404仍显示查询失败", async () => {
  let state;
  let response;
  await renderDetails({}, [], {
    list: async () => {
      throw { response };
    },
    capture: (value) => {
      state = value;
    },
  });
  response = { status: 404, data: { code: 404, msg: "未找到部门信息" } };
  await state.load();
  assert.equal(state.queryNotFound.value, true);
  assert.equal(state.loadErrorMessage.value, "未找到部门信息");
  for (response of [
    { status: 403, data: { code: 403 } },
    { status: 503, data: { code: 503 } },
    { status: 404, data: "<html>route missing</html>" },
  ]) {
    await state.load();
    assert.equal(state.loadError.value, true);
    assert.equal(state.queryNotFound.value, false);
    assert.equal(state.loadErrorMessage.value, "查询失败，请重试");
  }
});

test("状态颜色只作用于已知状态字段，未知状态与普通文字保持原样", () => {
  assert.equal(statusTone("status", "OVERDUE"), "danger");
  assert.equal(statusTone("qualificationStatus", "APPROVED"), "success");
  assert.equal(statusTone("returnOutcome", "DAMAGED"), "danger");
  for (const [key, value] of [
    ["title", "ACTIVE"],
    ["status", "FUTURE_STATUS"],
    ["status", null],
    ["status", "toString"],
  ])
    assert.equal(statusTone(key, value), null);
  assert.equal(displayValue("FUTURE_STATUS"), "FUTURE_STATUS");
});

test("人员DTO不再展示旧AI服务身份和专属档案字段", () => {
  for (const name of ["MemberQueryRequest", "MemberWriteRequest"]) {
    assert.deepEqual(
      dtoFields[name].find((field) => field.key === "memberType").options,
      ["READER", "EMPLOYEE"],
    );
    assert.ok(
      !dtoFields[name].some((field) =>
        ["serviceCode", "ownerMemberId", "aiLibrarianProfileId"].includes(
          field.key,
        ),
      ),
    );
  }
});

test("阅读进度保留0和100，缺失与异常值不能被伪装成有效百分比", () => {
  for (const value of [0, 35.5, 100])
    assert.equal(progressPercentage("progressPercent", value), value);
  for (const value of [null, undefined, "50", -1, 101, NaN, Infinity])
    assert.equal(progressPercentage("progressPercent", value), null);
  assert.equal(progressPercentage("targetBooks", 12), null);
});

test("真实详情模板保留状态文字且继续隐藏内部版本", async () => {
  const html = await renderDetails(
    { id: 1, status: "OVERDUE", version: 987654, note: "待归还" },
    [],
  );
  assert.ok(html.includes("record-status"));
  assert.ok(html.includes("已逾期"));
  assert.ok(html.includes("待归还"));
  assert.ok(!html.includes("987654"));
});
