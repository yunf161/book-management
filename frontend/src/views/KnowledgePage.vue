<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { knowledgeApi as api } from "../api/knowledge";
import { access } from "../stores/permissions";
import { validateKnowledgeFile } from "../modules/knowledgeImport";

const query = reactive({
  id: "",
  page: 1,
  pageSize: 10,
  title: "",
  kind: "",
  status: "",
});
const rows = ref([]),
  total = ref(0),
  state = ref(null),
  loading = ref(false);
const dialog = ref(false),
  editing = ref(null),
  saving = ref(false),
  hits = ref([]),
  searching = ref(false);
const search = reactive({ question: "", kind: "", topK: 4 });
const form = reactive({
  title: "",
  source: "",
  kind: "general",
  version: "v1",
  status: "ACTIVE",
  content: "",
  expectedVersion: 0,
  importMetadata: null,
});
const importing = ref(false),
  importInfo = ref(null);
let importGeneration = 0,
  parsedContent = "";
watch(dialog, (value) => {
  if (!value) {
    ++importGeneration;
    importing.value = false;
  }
});
watch(
  () => form.content,
  (value) => {
    if (form.importMetadata && value !== parsedContent) {
      form.importMetadata = null;
      if (importInfo.value)
        importInfo.value.warnings.push(
          "正文已手动修改，原文件的来源定位已清除",
        );
    }
  },
);
let timer,
  alive = true;
const job = computed(() => state.value?.latestJob);
const busy = computed(() => ["QUEUED", "RUNNING"].includes(job.value?.status));
const percent = computed(() =>
  job.value?.status === "SUCCEEDED"
    ? 100
    : job.value?.total
      ? Math.floor((job.value.completed * 100) / job.value.total)
      : 0,
);
const statusText = {
  QUEUED: "等待执行",
  RUNNING: "正在索引",
  SUCCEEDED: "已完成",
  FAILED: "失败，旧索引继续生效",
};

async function load() {
  loading.value = true;
  try {
    const result = await api.documents(
      Object.fromEntries(
        Object.entries(query).filter(([, value]) => value !== ""),
      ),
    );
    if (alive) {
      rows.value = result.rows;
      total.value = result.total;
    }
  } finally {
    loading.value = false;
  }
}
async function status() {
  const value = await api.status();
  if (alive) state.value = value;
}
async function open(row) {
  const doc = row ? await api.document(row.id) : null;
  editing.value = doc?.id || null;
  ++importGeneration;
  importInfo.value = null;
  parsedContent = doc?.content || "";
  Object.assign(
    form,
    {
      title: "",
      source: "",
      kind: "general",
      version: "v1",
      status: "ACTIVE",
      content: "",
      expectedVersion: 0,
      importMetadata: null,
    },
    doc || {},
  );
  form.expectedVersion = doc?.versionNumber || 0;
  dialog.value = true;
}
async function fileChanged(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  const generation = ++importGeneration;
  try {
    validateKnowledgeFile(file);
  } catch (error) {
    ElMessage.error(error.message);
    return;
  }
  importing.value = true;
  try {
    const preview = await api.preview(file);
    // 更换文件或关闭弹窗后，旧解析响应不得覆盖当前正文。
    if (generation !== importGeneration || !dialog.value) return;
    parsedContent = preview.content;
    form.content = preview.content;
    form.importMetadata = preview.importMetadata;
    importInfo.value = preview;
    if (!form.title) form.title = file.name.replace(/\.[^.]+$/, "");
  } catch {
    // HTTP层已展示具体解析错误；不覆盖原正文，也不把半成品当成成功预览。
  } finally {
    if (generation === importGeneration) importing.value = false;
  }
}
async function save() {
  if (saving.value || importing.value) return;
  saving.value = true;
  try {
    // 只发送契约字段，不把详情返回的MD5/标记等属性混入写请求。
    const body = Object.fromEntries(
      ["title", "source", "kind", "version", "status", "content"].map((key) => [
        key,
        form[key],
      ]),
    );
    if (editing.value) body.expectedVersion = form.expectedVersion;
    if (form.importMetadata) body.importMetadata = form.importMetadata;
    await api.save(editing.value, body);
    dialog.value = false;
    ElMessage.success("文档已保存，重建索引后生效");
    await Promise.all([load(), status()]);
  } finally {
    saving.value = false;
  }
}
async function rebuild() {
  await ElMessageBox.confirm(
    "按当前目录重建索引，完成前继续使用旧索引。归档资料将在新索引中停止检索。",
    "重建索引",
  );
  await status();
  await api.rebuild({
    expectedRevision: state.value.revision,
    expectedGeneration: state.value.snapshot.generation,
  });
  await status();
}
async function testSearch() {
  searching.value = true;
  try {
    hits.value = await api.search({
      question: search.question,
      topK: search.topK,
      ...(search.kind ? { kind: search.kind } : {}),
    });
  } finally {
    searching.value = false;
  }
}
onMounted(async () => {
  await Promise.all([load(), status()]);
  // 完成/失败后停止主动轮询；离开页面销毁定时器，避免后台请求泄漏。
  timer = setInterval(() => {
    if (busy.value) status().catch(() => {});
  }, 2000);
});
onUnmounted(() => {
  alive = false;
  clearInterval(timer);
});
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="AI管理"
      title="知识库管理"
      description="维护知识正文、查看索引进度、重建并检索验证。实时馆藏及政策判定仍以业务接口为准。"
    />
    <section class="knowledge-panel" v-if="state">
      <div class="toolbar">
        <strong
          >目录版本 {{ state.revision }} / 已索引版本
          {{ state.indexedRevision }}</strong
        ><span>当前 {{ state.snapshot.chunks }} 个片段</span
        ><el-button @click="status">刷新状态</el-button
        ><el-button
          v-if="access.has('knowledge:rebuild', 'ALL')"
          type="primary"
          :disabled="busy"
          @click="rebuild"
          >重建索引</el-button
        >
      </div>
      <el-alert
        v-if="state.revision !== state.indexedRevision"
        title="目录存在未发布修改，需要重建索引后才会用于AI回答。"
        type="warning"
        :closable="false"
      />
      <template v-if="job"
        ><p>
          最近任务：{{ statusText[job.status] }} · {{ job.completed }}/{{
            job.total
          }}
          片段
        </p>
        <el-progress
          :percentage="percent"
          :status="
            job.status === 'FAILED'
              ? 'exception'
              : job.status === 'SUCCEEDED'
                ? 'success'
                : undefined
          "
        />
        <p v-if="job.error">{{ job.error }}</p></template
      >
    </section>
    <section class="knowledge-panel">
      <el-form
        inline
        @submit.prevent="
          query.page = 1;
          load();
        "
        ><el-form-item label="文档编号"
          ><el-input v-model="query.id" clearable /></el-form-item
        ><el-form-item label="标题"
          ><el-input v-model="query.title" clearable /></el-form-item
        ><el-form-item label="类型"
          ><el-select v-model="query.kind" clearable style="width: 150px"
            ><el-option label="通用知识" value="general" /><el-option
              label="书目介绍"
              value="book_intro" /><el-option
              label="政策（受控）"
              value="policy" /></el-select></el-form-item
        ><el-form-item label="状态"
          ><el-select v-model="query.status" clearable style="width: 140px"
            ><el-option
              v-for="s in ['ACTIVE', 'DRAFT', 'ARCHIVED']"
              :key="s"
              :value="s"
              :label="s" /></el-select></el-form-item
        ><el-button native-type="submit">查询</el-button
        ><el-button
          v-if="access.has('knowledge:manage', 'ALL')"
          type="primary"
          @click="open()"
          >导入文档</el-button
        ></el-form
      >
      <el-table :data="rows" v-loading="loading"
        ><el-table-column prop="title" label="标题" /><el-table-column
          prop="kind"
          label="类型"
        /><el-table-column prop="version" label="内容版本" /><el-table-column
          prop="status"
          label="状态"
        /><el-table-column prop="characters" label="字符数" /><el-table-column
          prop="contentMd5"
          label="MD5"
          show-overflow-tooltip
        /><el-table-column
          prop="source"
          label="来源"
          show-overflow-tooltip
        /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button link @click="open(row)">{{
              row.managed && access.has("knowledge:manage", "ALL")
                ? "编辑"
                : "查看"
            }}</el-button></template
          ></el-table-column
        ></el-table
      >
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total,sizes,prev,pager,next,jumper"
        @current-change="load"
        @size-change="
          query.page = 1;
          load();
        "
      />
      <p>
        第 {{ query.page }} 页 / 共
        {{ Math.max(1, Math.ceil(total / query.pageSize)) }} 页
      </p>
    </section>
    <section class="knowledge-panel">
      <h3>检索验证（当前生效索引）</h3>
      <el-input
        v-model="search.question"
        placeholder="输入要检索的问题"
        maxlength="4000"
      />
      <div class="toolbar">
        <el-input-number v-model="search.topK" :min="1" :max="20" /><el-button
          :loading="searching"
          :disabled="!search.question.trim()"
          @click="testSearch"
          >检索</el-button
        >
      </div>
      <p v-if="!hits.length">暂无匹配结果</p>
      <article v-for="hit in hits" :key="hit.chunk_id">
        <strong>{{ hit.metadata.title }} · 相似度 {{ hit.similarity }}</strong>
        <p class="snippet">{{ hit.text }}</p>
        <small
          >{{ hit.metadata.source }} · {{ hit.metadata.version
          }}<template v-if="hit.metadata.source_locations">
            · {{ hit.metadata.source_locations }}</template
          ></small
        >
      </article>
    </section>
    <el-dialog
      v-model="dialog"
      :title="editing ? '知识文档' : '导入知识文档'"
      width="min(820px,94vw)"
      ><el-form label-width="100px"
        ><el-form-item label="标题"
          ><el-input v-model="form.title" maxlength="200" /></el-form-item
        ><el-form-item label="来源"
          ><el-input
            v-model="form.source"
            maxlength="500"
            placeholder="资料来源说明，不会请求该地址" /></el-form-item
        ><el-form-item label="类型"
          ><el-select v-model="form.kind"
            ><el-option label="通用知识" value="general" /><el-option
              label="书目介绍"
              value="book_intro" /></el-select></el-form-item
        ><el-form-item label="内容版本"
          ><el-input v-model="form.version" maxlength="64" /></el-form-item
        ><el-form-item label="状态"
          ><el-select v-model="form.status"
            ><el-option
              v-for="s in ['ACTIVE', 'DRAFT', 'ARCHIVED']"
              :key="s"
              :value="s" /></el-select></el-form-item
        ><el-form-item label="导入文件"
          ><input
            type="file"
            accept=".md,.txt,.csv,.json,.pdf"
            :disabled="
              importing ||
              !access.has('knowledge:manage', 'ALL') ||
              form.kind === 'policy'
            "
            @change="fileChanged"
        /></el-form-item>
        <p class="muted">
          支持UTF-8
          MD/TXT/CSV/JSON及文本型PDF。PDF最大4MiB、100页；其他文件最大600000字节，提取正文最多20万字符。扫描件需先OCR。
        </p>
        <p v-if="importing">正在解析文件，请稍候……</p>
        <el-alert
          v-if="importInfo"
          type="info"
          :closable="false"
          :title="`${importInfo.fileName}：已提取${importInfo.characters}字符，${importInfo.units}个来源单元；核对正文后点击保存`" />
        <el-alert
          v-for="warning in importInfo?.warnings || []"
          :key="warning"
          type="warning"
          :closable="false"
          :title="warning" />
        <el-form-item label="正文预览"
          ><el-input
            v-model="form.content"
            type="textarea"
            :rows="14"
            maxlength="200000"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">关闭</el-button
        ><el-button
          v-if="access.has('knowledge:manage', 'ALL') && form.kind !== 'policy'"
          type="primary"
          :loading="saving"
          :disabled="importing"
          @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </AppShell>
</template>

<style scoped>
.knowledge-panel {
  background: white;
  border: 1px solid #d7e3dc;
  border-radius: 12px;
  padding: 24px;
  margin: 20px 0;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin: 12px 0;
}
.snippet {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
article {
  border-top: 1px solid #d7e3dc;
  padding: 18px 0;
}
</style>
