<script setup>
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import ResourceDesk from "../components/ResourceDesk.vue";
import DtoFields from "../components/DtoFields.vue";
import { dtoFields as fields } from "../modules/dtoFields";
import { api } from "../api/library";
import { access } from "../stores/permissions";
import { recordVersion } from "../modules/recordVersions";
const route = useRoute(),
  admin = computed(() => route.path.startsWith("/admin"));
const tab = ref(route.query.recordId ? "records" : "applications"),
  desk = ref(null),
  dialog = ref(false),
  mode = ref(""),
  selected = ref(null),
  form = reactive({});
const submitting = ref(false);
// 两个聚合查询各自鉴权；只授予其中一项时不请求另一项接口。
const canQueryApplications = computed(() =>
  access.has("borrow-application:query", admin.value ? "ALL" : "SELF"),
);
const canQueryRecords = computed(() =>
  access.has("borrow-record:query", admin.value ? "ALL" : "SELF"),
);
watch(
  [canQueryApplications, canQueryRecords],
  () => {
    if (
      tab.value === "applications" &&
      !canQueryApplications.value &&
      canQueryRecords.value
    )
      tab.value = "records";
    if (
      tab.value === "records" &&
      !canQueryRecords.value &&
      canQueryApplications.value
    )
      tab.value = "applications";
  },
  { immediate: true },
);
const apps = (q) => api.applications(admin.value, q),
  records = (q) => api.records(admin.value, q);
const ownFields = (f) =>
  f.filter(
    (x) =>
      admin.value || !["memberId", "memberName", "processedBy"].includes(x.key),
  );
const columnsApps = [
  { key: "id", label: "编号" },
  { key: "memberName", label: "申请人" },
  { key: "bookTitle", label: "图书" },
  { key: "applicationType", label: "借阅/续借" },
  { key: "requestedDays", label: "天数" },
  { key: "status", label: "状态" },
  { key: "version", label: "版本" },
];
const columnsRecords = [
  { key: "id", label: "编号" },
  { key: "memberName", label: "读者" },
  { key: "title", label: "图书" },
  { key: "barcode", label: "条码" },
  { key: "dueAt", label: "应还时间" },
  { key: "status", label: "状态" },
  { key: "renewCount", label: "续借次数" },
  { key: "returnOutcome", label: "归还/结案结果" },
  { key: "returnConditionNote", label: "归还书况" },
  { key: "currentOverdueDays", label: "逾期天数" },
  { key: "version", label: "版本" },
];
const operationFields = computed(
  () =>
    ({
      create: [
        {
          key: "applicationType",
          label: "申请类型",
          options: ["BORROW", "RENEW"],
        },
        { key: "bookId", label: "图书编号", type: "number" },
        { key: "borrowRecordId", label: "续借记录编号", type: "number" },
        { key: "requestedDays", label: "申请天数", type: "number" },
        { key: "reason", label: "申请原因", type: "textarea" },
      ],
      renew: [
        { key: "requestedDays", label: "续借天数", type: "number" },
        { key: "reason", label: "申请原因", type: "textarea" },
      ],
      approve: [
        {
          key: "copyId",
          label: "指定副本编号（留空自动选择）",
          type: "number",
        },
        { key: "approvedDays", label: "批准天数", type: "number" },
        { key: "decisionRemark", label: "审批说明", type: "textarea" },
        { key: "expectedVersion", label: "申请版本", type: "number" },
      ],
      reject: [
        { key: "decisionRemark", label: "拒绝原因", type: "textarea" },
        { key: "expectedVersion", label: "申请版本", type: "number" },
      ],
      return: [
        {
          key: "returnOutcome",
          label: "归还/结案结果",
          options: ["NORMAL", "DAMAGED", "LOST"],
        },
        { key: "returnConditionNote", label: "归还书况", type: "textarea" },
        { key: "expectedVersion", label: "借阅记录版本", type: "number" },
      ],
      barcode: [
        { key: "barcode", label: "馆藏条码" },
        {
          key: "returnOutcome",
          label: "归还/结案结果",
          options: ["NORMAL", "DAMAGED", "LOST"],
        },
        { key: "returnConditionNote", label: "归还书况", type: "textarea" },
      ],
    })[mode.value] || [],
);
function open(action, row) {
  mode.value = action;
  selected.value = row || null;
  for (const key of Object.keys(form)) delete form[key];
  Object.assign(form, {
    applicationType: "BORROW",
    requestedDays: 7,
    approvedDays: row?.requestedDays || 7,
    expectedVersion: row?.version,
    returnOutcome: "NORMAL",
  });
  dialog.value = true;
}
async function perform() {
  if (submitting.value) return;
  try {
    if (["approve", "reject", "return", "barcode"].includes(mode.value))
      await ElMessageBox.confirm("确认提交本次审批或归还操作？", "操作确认");
    submitting.value = true;
    const body = {};
    for (const field of operationFields.value)
      if (form[field.key] !== undefined && form[field.key] !== "")
        body[field.key] = form[field.key];
    if (["approve", "reject", "return"].includes(mode.value))
      body.expectedVersion = recordVersion(selected.value);
    if (mode.value === "create") {
      if (body.applicationType === "RENEW") delete body.bookId;
      else delete body.borrowRecordId;
      await api.createApplication(body);
    }
    if (mode.value === "renew")
      await api.createApplication({
        ...body,
        applicationType: "RENEW",
        borrowRecordId: selected.value.id,
      });
    if (mode.value === "approve") {
      if (selected.value.applicationType === "RENEW") delete body.copyId;
      await api.approveApplication(selected.value.id, body);
    }
    if (mode.value === "reject")
      await api.rejectApplication(selected.value.id, body);
    if (mode.value === "return") await api.returnBook(selected.value.id, body);
    if (mode.value === "barcode") {
      const barcode = body.barcode;
      delete body.barcode;
      await api.returnBarcode(barcode, body);
    }
    dialog.value = false;
    ElMessage.success("操作成功");
    await desk.value.load();
  } catch {
    /* 失败时保留请求信息，不展示虚假成功。 */
  } finally {
    submitting.value = false;
  }
}
async function cancel(row) {
  try {
    await ElMessageBox.confirm("确认取消申请？");
    await api.cancelApplication(row.id, row.version);
    await desk.value.load();
  } catch {}
}
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="借阅服务"
      :title="admin ? '借阅审批与归还' : '我的借阅'"
      description="借阅与续借使用同一申请流程，查询保持聚合接口。"
    />
    <el-tabs v-model="tab"
      ><el-tab-pane
        v-if="canQueryApplications"
        label="借阅与续借申请"
        name="applications" /><el-tab-pane
        v-if="canQueryRecords"
        label="借阅记录"
        name="records"
    /></el-tabs>
    <ResourceDesk
      v-if="tab === 'applications' && canQueryApplications"
      key="applications"
      ref="desk"
      title="借阅申请"
      :list="apps"
      :columns="columnsApps"
      :query-fields="ownFields(fields.BorrowApplicationQueryRequest)"
      :initial-query="{
        id: route.query.applicationId
          ? Number(route.query.applicationId)
          : undefined,
      }"
    >
      <template #toolbar
        ><el-button
          v-if="!admin && access.has('borrow-application:create')"
          type="primary"
          @click="open('create')"
          >提交申请</el-button
        ></template
      >
      <template #actions="{ row }"
        ><template v-if="row.status === 'PENDING'">
          <el-button
            v-if="!admin && access.has('borrow-application:cancel', 'SELF')"
            link
            type="danger"
            @click="cancel(row)"
            >取消申请</el-button
          >
          <template v-else-if="admin"
            ><el-button
              v-if="access.has('borrow-application:approve', 'ALL')"
              link
              type="success"
              @click="open('approve', row)"
              >批准</el-button
            ><el-button
              v-if="access.has('borrow-application:reject', 'ALL')"
              link
              type="danger"
              @click="open('reject', row)"
              >拒绝</el-button
            ></template
          >
        </template></template
      >
    </ResourceDesk>
    <ResourceDesk
      v-else-if="canQueryRecords"
      key="records"
      ref="desk"
      title="借阅记录"
      :list="records"
      :columns="columnsRecords"
      :query-fields="ownFields(fields.BorrowRecordQueryRequest)"
      :initial-query="{
        id: route.query.recordId ? Number(route.query.recordId) : undefined,
      }"
    >
      <template #toolbar
        ><el-button
          v-if="admin && access.has('borrow-record:return', 'ALL')"
          type="primary"
          @click="open('barcode')"
          >按条码归还</el-button
        ></template
      >
      <template #actions="{ row }"
        ><el-button
          v-if="
            admin &&
            access.has('borrow-record:return', 'ALL') &&
            ['BORROWING', 'OVERDUE'].includes(row.status)
          "
          link
          type="primary"
          @click="open('return', row)"
          >办理归还</el-button
        >
        <el-button
          v-if="
            !admin &&
            access.has('borrow-application:create') &&
            row.status === 'BORROWING' &&
            row.renewCount < 3
          "
          link
          type="primary"
          @click="open('renew', row)"
          >申请续借</el-button
        ></template
      >
    </ResourceDesk>
    <el-dialog
      class="workspace-dialog"
      v-model="dialog"
      title="借阅业务办理"
      width="min(750px,95vw)"
      ><el-form label-position="top"
        ><DtoFields :model="form" :fields="operationFields"
      /></el-form>
      <template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="submitting" @click="perform"
          >提交</el-button
        ></template
      >
    </el-dialog>
  </AppShell>
</template>
