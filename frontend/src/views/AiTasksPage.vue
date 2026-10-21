<script setup>
import { displayValue } from "../modules/display";
const formatState = (_row, _column, value) => displayValue(value);
import { onMounted, onUnmounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { aiApi } from "../api/ai";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { runtimeApi as api } from "../api/aiRuntime";
import { access } from "../stores/permissions";
import { session } from "../stores/session";
const query = reactive({ page: 1, pageSize: 10, state: "", type: "" });
const rows = ref([]),
  total = ref(0),
  result = ref(null),
  details = ref(false),
  loading = ref(false);
const form = reactive({ type: "CONSULTATION", question: "" });
const proposal = ref(null),
  action = ref(null),
  usage = ref(null),
  actionSaving = ref(false);
let timer,
  alive = true;
async function load() {
  loading.value = true;
  try {
    const data = await api.tasks(
      Object.fromEntries(Object.entries(query).filter(([, v]) => v !== "")),
    );
    if (alive) {
      rows.value = data.rows;
      total.value = data.total;
    }
  } finally {
    loading.value = false;
  }
}
async function submit() {
  await api.submit({ ...form });
  form.question = "";
  query.page = 1;
  await load();
  ElMessage.success("任务已提交，可离开页面后查看结果");
}
async function cancel(row) {
  await api.cancel(row.taskId);
  await load();
}
async function retry(row) {
  await api.retry(row.taskId);
  query.page = 1;
  await load();
}
function view(row) {
  proposal.value = null;
  action.value = null;
  try {
    const parsed = JSON.parse(row.resultJson || "{}");
    result.value = JSON.stringify(parsed, null, 2);
    proposal.value = parsed.proposal || null;
  } catch {
    result.value = "暂无结果";
  }
  details.value = true;
}
async function preview() {
  action.value = await aiApi.preview(proposal.value);
}
async function confirm() {
  await ElMessageBox.confirm(
    "请核对以上操作及参数。确认后将调用真实业务接口，原角色和业务规则仍会重新校验。",
    "确认业务办理",
  );
  actionSaving.value = true;
  try {
    action.value = await aiApi.confirm(action.value);
  } finally {
    actionSaving.value = false;
  }
}
async function reconcile() {
  const r = await api.reconcile();
  ElMessage.success(
    `已核实 ${r.resolved} 项，仍待核实 ${r.remainingUnknown} 项；没有重放业务写入`,
  );
}
onMounted(async () => {
  await load();
  usage.value = await api.usage();
  timer = setInterval(() => {
    if (rows.value.some((row) => ["QUEUED", "RUNNING"].includes(row.state)))
      load().catch(() => {});
  }, 3000);
});
onUnmounted(() => {
  alive = false;
  clearInterval(timer);
});
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="AI服务"
      title="AI任务管理"
      description="咨询和业务提案异步生成；业务执行仍需显式预览与确认。费用是配置单价估算，不是云平台账单。"
    />
    <section class="task-panel">
      <template v-if="usage"
        ><h3>模型报告用量（最近30天）</h3>
        <p>{{ usage.note }}</p>
        <el-table :data="usage.rows"
          ><el-table-column prop="provider" label="提供方" /><el-table-column
            prop="model"
            label="模型" /><el-table-column
            prop="calls"
            label="调用数" /><el-table-column
            prop="promptTokens"
            label="输入Token" /><el-table-column
            prop="completionTokens"
            label="输出Token" /><el-table-column
            prop="knownEstimatedCost"
            label="已知预估费用" /><el-table-column
            prop="unknownCostCalls"
            label="未配置单价调用数" /></el-table
      ></template>
      <el-form inline
        ><el-form-item label="类型"
          ><el-select v-model="form.type" style="width: 150px"
            ><el-option label="咨询" value="CONSULTATION" /><el-option
              label="业务提案"
              value="BUSINESS" /></el-select></el-form-item
        ><el-form-item label="问题"
          ><el-input
            v-model="form.question"
            maxlength="2000"
            placeholder="输入任务问题"
            style="width: 330px" /></el-form-item
        ><el-button
          type="primary"
          :disabled="!form.question.trim()"
          @click="submit"
          >提交后台任务</el-button
        ></el-form
      >
    </section>
    <section class="task-panel">
      <el-form inline
        ><el-form-item label="任务编号"
          ><el-input v-model="query.taskId" clearable /></el-form-item
        ><el-form-item
          v-if="access.has('ai-task:query', 'ALL')"
          label="成员编号"
          ><el-input-number
            v-model="query.memberId"
            :min="1"
            :controls="false" /></el-form-item
        ><el-form-item label="任务类型"
          ><el-select v-model="query.type" clearable style="width: 150px"
            ><el-option label="咨询" value="CONSULTATION" /><el-option
              label="业务提案"
              value="BUSINESS" /></el-select></el-form-item
        ><el-form-item label="状态"
          ><el-select v-model="query.state" clearable style="width: 170px"
            ><el-option
              v-for="s in [
                'QUEUED',
                'RUNNING',
                'SUCCEEDED',
                'FAILED',
                'CANCELLED',
              ]"
              :key="s"
              :value="s"
              :label="displayValue(s)" /></el-select></el-form-item
        ><el-button
          @click="
            query.page = 1;
            load();
          "
          >查询</el-button
        ><el-button
          v-if="access.has('ai-task:manage', 'ALL')"
          @click="reconcile"
          >核实UNKNOWN业务结果</el-button
        ></el-form
      >
      <el-table :data="rows" v-loading="loading"
        ><el-table-column
          prop="taskId"
          label="任务编号"
          show-overflow-tooltip
        /><el-table-column
          prop="memberId"
          label="成员"
          width="85"
        /><el-table-column
          prop="type"
          :formatter="formatState"
          label="类型"
        /><el-table-column
          prop="state"
          :formatter="formatState"
          label="状态"
        /><el-table-column
          prop="question"
          label="本人问题"
          show-overflow-tooltip
        /><el-table-column
          prop="attempt"
          label="重试次数"
          width="90"
        /><el-table-column
          prop="promptTokens"
          label="输入Token"
        /><el-table-column
          prop="completionTokens"
          label="输出Token"
        /><el-table-column label="预估费用"
          ><template #default="{ row }">{{
            row.estimatedCost ?? "未配置/未知"
          }}</template></el-table-column
        ><el-table-column prop="errorCode" label="错误" /><el-table-column
          label="操作"
          width="180"
          ><template #default="{ row }"
            ><el-button link @click="view(row)">结果</el-button
            ><el-button
              v-if="['QUEUED', 'RUNNING'].includes(row.state)"
              link
              @click="cancel(row)"
              >取消</el-button
            ><el-button
              v-if="
                ['FAILED', 'CANCELLED'].includes(row.state) &&
                row.attempt < 3 &&
                String(row.memberId) === String(session.claims.memberId)
              "
              link
              @click="retry(row)"
              >重试</el-button
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
    <el-dialog v-model="details" title="任务结果" width="min(900px,94vw)">
      <pre>{{ result }}</pre>
      <template v-if="proposal"
        ><el-button v-if="!action" @click="preview">生成待确认操作</el-button
        ><template v-else
          ><h4>操作预览：{{ action.title }} · {{ action.state }}</h4>
          <pre>{{ JSON.stringify(action.arguments, null, 2) }}</pre>
          <el-button
            v-if="action.state === 'PREPARED'"
            type="primary"
            :loading="actionSaving"
            @click="confirm"
            >确认执行</el-button
          ></template
        ></template
      >
      <p>
        业务提案不会自动执行。请进入业务助手或对应业务页面，核对资料后办理。
      </p></el-dialog
    ></AppShell
  >
</template>
<style scoped>
.task-panel {
  background: white;
  border: 1px solid #d7e3dc;
  border-radius: 12px;
  padding: 24px;
  margin: 20px 0;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 60vh;
  overflow: auto;
}
</style>
