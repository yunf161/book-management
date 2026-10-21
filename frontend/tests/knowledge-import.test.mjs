import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { SourceTextModule, SyntheticModule, createContext } from "node:vm";
import { parse, compileScript } from "@vue/compiler-sfc";
import * as Vue from "vue";
import { validateKnowledgeFile } from "../src/modules/knowledgeImport.js";

test("知识库文件选择支持五种格式，分别执行PDF与文本大小限制", () => {
  for (const name of ["a.md", "a.txt", "a.csv", "a.json", "a.PDF"])
    assert.ok(validateKnowledgeFile({ name, size: 1 }));
  for (const file of [
    { name: "a.docx", size: 1 },
    { name: "a.csv", size: 600001 },
    { name: "a.pdf", size: 4 * 1024 * 1024 + 1 },
    { name: "a.json", size: 0 },
  ])
    assert.throws(() => validateKnowledgeFile(file));
  assert.equal(
    validateKnowledgeFile({ name: "a.pdf", size: 1024 * 1024 }),
    "pdf",
  );
});

async function pageHarness(preview) {
  let state, submitted;
  const source = await readFile(
    new URL("../src/views/KnowledgePage.vue", import.meta.url),
    "utf8",
  );
  const { descriptor } = parse(source);
  const code = compileScript(descriptor, { id: "knowledge-test" }).content;
  const api = {
    preview,
    document: async () => null,
    save: async (id, body) => {
      submitted = body;
    },
    documents: async () => ({ rows: [], total: 0 }),
    status: async () => ({}),
  };
  const context = createContext({
    console,
    setInterval: () => 0,
    clearInterval: () => {},
  });
  const module = (exports) =>
    new SyntheticModule(
      Object.keys(exports),
      function () {
        for (const [key, value] of Object.entries(exports))
          this.setExport(key, value);
      },
      { context },
    );
  const dependencies = {
    vue: module(Vue),
    "element-plus": module({
      ElMessage: { error() {}, success() {} },
      ElMessageBox: {},
    }),
    "../components/AppShell.vue": module({ default: {} }),
    "../components/PageHeader.vue": module({ default: {} }),
    "../api/knowledge": module({ knowledgeApi: api }),
    "../stores/permissions": module({ access: { has: () => true } }),
    "../modules/knowledgeImport": module({ validateKnowledgeFile }),
  };
  const script = new SourceTextModule(code, { context });
  await script.link((name) => dependencies[name]);
  await script.evaluate();
  const component = script.namespace.default;
  const setup = component.setup;
  component.setup = (props, ctx) => {
    state = setup(props, ctx);
    return state;
  };
  component.render = () => Vue.h("div");
  // 使用无DOM渲染器真正挂载组件，保留watch生命周期；SSR不会运行普通watch。
  const renderer = Vue.createRenderer({
    createElement: () => ({}),
    createText: () => ({}),
    createComment: () => ({}),
    insert() {},
    remove() {},
    setText() {},
    setElementText() {},
    patchProp() {},
    parentNode: () => null,
    nextSibling: () => null,
  });
  const app = renderer.createApp(component);
  app.mount({});
  await state.open();
  return { state, submitted: () => submitted, unmount: () => app.unmount() };
}

test("页面解析预览后保存正文及来源，正文编辑会清除过期来源位置", async (t) => {
  const h = await pageHarness(async () => ({
    fileName: "a.csv",
    content: "[CSV 第2行]\n书名: Java",
    characters: 20,
    units: 1,
    warnings: [],
    importMetadata: { format: "csv", sections: [{ locator: "第2行" }] },
  }));
  t.after(h.unmount);
  await h.state.fileChanged({
    target: { files: [{ name: "a.csv", size: 10 }] },
  });
  await Vue.nextTick();
  assert.equal(h.state.form.importMetadata.format, "csv");
  await h.state.save();
  assert.equal(h.submitted().importMetadata.sections[0].locator, "第2行");
  h.state.form.content = "修改后的正文";
  await Vue.nextTick();
  assert.equal(h.state.form.importMetadata, null);
});

test("关闭窗口后迟到的解析结果不能写入表单", async (t) => {
  let resolve;
  const h = await pageHarness(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  t.after(h.unmount);
  const pending = h.state.fileChanged({
    target: { files: [{ name: "a.pdf", size: 10 }] },
  });
  h.state.dialog.value = false;
  await Vue.nextTick();
  resolve({ content: "不得覆盖", importMetadata: {}, warnings: [] });
  await pending;
  assert.equal(h.state.form.content, "");
});

test("解析未完成不能保存，解析失败保持原正文", async (t) => {
  let reject;
  const h = await pageHarness(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      }),
  );
  t.after(h.unmount);
  h.state.form.content = "原正文";
  const pending = h.state.fileChanged({
    target: { files: [{ name: "a.pdf", size: 10 }] },
  });
  await h.state.save();
  assert.equal(h.submitted(), undefined);
  reject(new Error("PDF损坏"));
  await pending;
  assert.equal(h.state.form.content, "原正文");
});
