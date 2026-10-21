<script setup>
import { displayValue } from "../modules/display";
const formatState = (_row, _column, value) => displayValue(value);
import { onMounted, onUnmounted, reactive, ref, computed } from "vue";
import { ElMessage } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import DtoFields from "../components/DtoFields.vue";
import { operationsApi as api } from "../api/operations";
import { access } from "../stores/permissions";
const range = reactive({ from: "", to: "" }),
  report = ref(null),
  jobs = ref([]),
  total = ref(0),
  file = ref(null),
  exportQuery = reactive({});
const query = reactive({ page: 1, pageSize: 10 }),
  sending = ref(false);
let timer,
  alive = true;
const labels = {
  borrowed: "区间借阅量",
  returned: "区间归还量",
  newReaders: "区间新增读者",
  currentOverdue: "当前逾期",
  currentBooks: "当前启用书目",
  currentCopies: "当前在馆副本（含借出）",
  currentAvailable: "当前可借副本",
};
const bookLabels = {
  id: "图书编号",
  categoryId: "分类编号",
  version: "版本",
  keyword: "综合关键词",
  title: "书名",
  subtitle: "副标题",
  author: "作者",
  isbn: "ISBN",
  publisher: "出版社",
  edition: "版次",
  language: "语言",
  description: "简介",
  coverObjectKey: "封面对象路径",
};
const fields = [
  ...["id", "categoryId", "version"].map((key) => ({
    key,
    label: bookLabels[key],
    type: "number",
  })),
  ...[
    "keyword",
    "title",
    "subtitle",
    "author",
    "isbn",
    "publisher",
    "edition",
    "language",
    "description",
    "coverObjectKey",
  ].map((key) => ({ key, label: bookLabels[key] })),
  { key: "publishedDate", label: "出版日期", type: "date" },
  { key: "status", label: "状态", options: ["ACTIVE", "DISABLED"] },
  { key: "createdAt", label: "创建时间", type: "datetime" },
  { key: "updatedAt", label: "更新时间", type: "datetime" },
  {
    key: "availableOnly",
    label: "仅可借",
    options: [
      { value: true, label: "是" },
      { value: false, label: "否" },
    ],
  },
];
const maxDay = computed(() =>
  Math.max(1, ...(report.value?.dailyBorrowing || []).map((row) => row.amount)),
);
async function overview() {
  const value = await api.overview(
    Object.fromEntries(Object.entries(range).filter(([, v]) => v)),
  );
  if (alive) report.value = value;
}
async function load() {
  const result = await api.jobs({ ...query });
  if (alive) {
    jobs.value = result.rows;
    total.value = result.total;
  }
}
async function download(blob, name) {
  const url = URL.createObjectURL(blob),
    link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function upload() {
  if (!file.value) return;
  sending.value = true;
  try {
    await api.upload(file.value);
    await load();
    ElMessage.success("已提交导入作业；相同文件重复上传不会重复新增");
  } finally {
    sending.value = false;
  }
}
async function exportBooks() {
  await api.export(
    Object.fromEntries(
      Object.entries(exportQuery).filter(
        ([, v]) => v !== "" && v !== null && v !== undefined,
      ),
    ),
  );
  await load();
}
onMounted(async () => {
  await Promise.all([overview(), load()]);
  timer = setInterval(() => {
    if (jobs.value.some((job) => ["QUEUED", "RUNNING"].includes(job.state)))
      load().catch(() => {});
  }, 2000);
});
onUnmounted(() => {
  alive = false;
  clearInterval(timer);
});
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="运营与批量处理"
      title="运营统计与馆藏导入导出"
      description="统计不包含个人敏感资料。导入每行独立事务，成功行保留、失败行提供Excel报告。"
    />
    <section class="operation-panel">
      <el-form inline
        ><el-form-item label="开始日期"
          ><el-date-picker
            v-model="range.from"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="结束日期"
          ><el-date-picker
            v-model="range.to"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-button @click="overview">查询统计</el-button></el-form
      ><template v-if="report"
        ><p>
          {{ report.from }} 至
          {{ report.to }}（含首尾日期）；“当前”指标不受日期筛选影响。
        </p>
        <div class="summary-grid">
          <article v-for="(value, key) in report.totals" :key="key">
            <span>{{ labels[key] }}</span
            ><strong>{{ value }}</strong>
          </article>
        </div>
        <h3>每日借阅量</h3>
        <div class="bar-chart" aria-label="每日借阅量图表">
          <div
            v-for="row in report.dailyBorrowing"
            :key="row.day"
            class="bar"
            :title="row.day + '：' + row.amount"
          >
            <span
              :style="{
                height: Math.max(2, (row.amount / maxDay) * 120) + 'px',
              }"
            ></span
            ><small>{{ row.day.slice(5) }}<br />{{ row.amount }}</small>
          </div>
        </div>
        <h3>当前副本状态</h3>
        <el-table :data="report.currentCopyStatuses"
          ><el-table-column
            prop="status"
            :formatter="formatState"
            label="状态" /><el-table-column
            prop="amount"
            label="数量" /></el-table
      ></template>
    </section>
    <section
      class="operation-panel"
      v-if="access.has('operations:import', 'ALL')"
    >
      <h3>Excel馆藏导入</h3>
      <p>
        xlsx，最多2MB、1000行、一张工作表；ISBN和条码请设为文本。日期用YYYY-MM-DD。有barcode时必须填写location；同ISBN的多行可增加不同副本。
      </p>
      <el-button
        @click="
          api
            .template()
            .then((blob) => download(blob, 'book-import-template.xlsx'))
        "
        >下载模板</el-button
      ><input
        type="file"
        accept=".xlsx"
        @change="(event) => (file = event.target.files?.[0])"
      /><el-button :disabled="!file" :loading="sending" @click="upload"
        >提交导入</el-button
      >
    </section>
    <section
      class="operation-panel"
      v-if="access.has('operations:export', 'ALL')"
    >
      <h3>馆藏导出筛选</h3>
      <el-form label-position="top"
        ><DtoFields :model="exportQuery" :fields="fields" /></el-form
      ><el-button @click="exportBooks">生成Excel（最多10000条）</el-button>
    </section>
    <section class="operation-panel">
      <h3>本人的导入导出作业</h3>
      <el-form inline
        ><el-form-item label="作业编号"
          ><el-input v-model="query.jobId" clearable /></el-form-item
        ><el-form-item label="作业类型"
          ><el-select v-model="query.type" clearable style="width: 130px"
            ><el-option label="导入" value="IMPORT" /><el-option
              label="导出"
              value="EXPORT" /></el-select></el-form-item
        ><el-form-item label="作业状态"
          ><el-select v-model="query.state" clearable style="width: 150px"
            ><el-option
              v-for="state in [
                'QUEUED',
                'RUNNING',
                'SUCCEEDED',
                'PARTIAL',
                'FAILED',
              ]"
              :key="state"
              :value="state"
              :label="displayValue(state)" /></el-select></el-form-item
      ></el-form>
      <el-button @click="load">刷新</el-button
      ><el-table :data="jobs"
        ><el-table-column
          prop="jobId"
          label="作业编号"
          show-overflow-tooltip
        /><el-table-column
          prop="type"
          :formatter="formatState"
          label="类型"
        /><el-table-column
          prop="state"
          :formatter="formatState"
          label="状态"
        /><el-table-column label="进度"
          ><template #default="{ row }"
            >{{ row.completedRows }} / {{ row.totalRows }}</template
          ></el-table-column
        ><el-table-column prop="successRows" label="成功" /><el-table-column
          prop="failedRows"
          label="失败"
        /><el-table-column prop="errorCode" label="错误" /><el-table-column
          label="文件"
          ><template #default="{ row }"
            ><el-button
              v-if="['SUCCEEDED', 'PARTIAL'].includes(row.state)"
              link
              @click="
                api
                  .file(row.jobId)
                  .then((blob) => download(blob, 'operation-report.xlsx'))
              "
              >下载{{
                row.type === "IMPORT" ? "逐行报告" : "导出结果"
              }}</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-model:current-page="query.page"
        :page-size="query.pageSize"
        :total="total"
        layout="total,prev,pager,next,jumper"
        @current-change="load"
      />
    </section>
  </AppShell>
</template>
<style scoped>
.operation-panel {
  background: white;
  border: 1px solid #d7e3dc;
  border-radius: 12px;
  padding: 24px;
  margin: 20px 0;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
}
.summary-grid article {
  background: #eff5ef;
  border-radius: 8px;
  padding: 16px;
}
.summary-grid strong {
  display: block;
  font-size: 30px;
  margin-top: 12px;
}
.bar-chart {
  display: flex;
  align-items: end;
  gap: 10px;
  overflow-x: auto;
  padding: 10px 0;
}
.bar {
  min-width: 48px;
  text-align: center;
}
.bar span {
  display: block;
  width: 26px;
  background: #245c4f;
  margin: auto;
}
.bar small {
  display: block;
  margin-top: 8px;
}
</style>
