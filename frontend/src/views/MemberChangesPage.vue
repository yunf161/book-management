<script setup>
import { computed, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import ResourceDesk from "../components/ResourceDesk.vue";
import DtoFields from "../components/DtoFields.vue";
import { dtoFields as fields } from "../modules/dtoFields";
import { api } from "../api/library";
import { rules } from "../stores/rules";
import { access } from "../stores/permissions";
import { recordVersion } from "../modules/recordVersions";
const route = useRoute(),
  admin = computed(() => route.path.startsWith("/admin"));
const desk = ref(null),
  decisionDialog = ref(false),
  selected = ref(null),
  decision = reactive({});
const submitting = ref(false);
const approvalCode = {
  READER_QUALIFICATION: "reader-qualification:approve",
  READER_QUALIFICATION_RESTORE: "reader-qualification:approve",
  BORROW_QUOTA_CHANGE: "borrow-quota-change:approve",
  ACCOUNT_REACTIVATION: "member-account-change:approve",
  EMPLOYEE_PROFILE_CHANGE: "employee-profile-change:approve",
  EMPLOYEE_TRANSFER: "employee-profile-change:approve",
  EMPLOYEE_SUSPENSION: "employee-status-change:approve",
  EMPLOYEE_RESIGNATION: "employee-status-change:approve",
  EMPLOYEE_REINSTATEMENT: "employee-status-change:approve",
  EMPLOYEE_ROLE_CHANGE: "employee-role-change:approve",
};
const list = (q) => api.memberChanges(admin.value, q);
const ownCreate = async (body) => {
  // 新申请的资料快照版本由后端在成员锁内取得，用户不需要知道内部版本。
  return api.createMemberChange({
    ...body,
    ...(body.applicationType === "READER_QUALIFICATION"
      ? { acceptedRuleVersion: rules.value.ruleVersion }
      : {}),
  });
};
async function cancel(row) {
  try {
    await ElMessageBox.confirm("确认取消本人申请？");
    await api.cancelMemberChange(row.id, row.version);
    await desk.value.load();
  } catch {}
}
function openDecision(row) {
  selected.value = row;
  Object.assign(decision, {
    decision: "APPROVE",
    reason: "",
    expectedVersion: row.version,
  });
  decisionDialog.value = true;
}
async function decide() {
  if (submitting.value) return;
  try {
    await ElMessageBox.confirm(
      "确认提交本次资料审批？审批通过后会更新成员资料。",
      "审批确认",
    );
    submitting.value = true;
    await api.decideMemberChange(selected.value.id, {
      ...decision,
      expectedVersion: recordVersion(selected.value),
    });
    decisionDialog.value = false;
    ElMessage.success("审批已完成");
    await desk.value.load();
  } catch {
    // 取消或失败时保留表单，等待用户修正。
  } finally {
    submitting.value = false;
  }
}
const columns = [
  { key: "id", label: "编号" },
  { key: "applicationNo", label: "申请单号" },
  { key: "applicantMemberId", label: "申请人" },
  { key: "applicationType", label: "申请类型" },
  { key: "status", label: "状态" },
  { key: "reason", label: "申请原因" },
  { key: "currentMaxBorrowCount", label: "提交时生效额度" },
  { key: "requestedMaxBorrowCount", label: "申请总额度" },
  { key: "quotaDurationDays", label: "临时天数（空为长期）" },
  { key: "expiresAt", label: "到期时间" },
  { key: "decidedAt", label: "审批时间" },
  { key: "version", label: "版本" },
];
// 同类申请只携带相应业务字段，防止表单保留其他类型的残余字段。
const requests = {
  READER_QUALIFICATION: ["acceptedRuleVersion"],
  READER_QUALIFICATION_RESTORE: [],
  BORROW_QUOTA_CHANGE: ["requestedMaxBorrowCount", "quotaDurationDays"],
  ACCOUNT_REACTIVATION: ["requestedAccountAction", "expectedProfileVersion"],
  EMPLOYEE_PROFILE_CHANGE: [
    "requestedDepartmentId",
    "requestedPositionName",
    "expectedProfileVersion",
  ],
  EMPLOYEE_TRANSFER: [
    "requestedDepartmentId",
    "requestedPositionName",
    "expectedProfileVersion",
  ],
  EMPLOYEE_SUSPENSION: ["requestedEmploymentAction", "expectedProfileVersion"],
  EMPLOYEE_RESIGNATION: ["requestedEmploymentAction", "expectedProfileVersion"],
  EMPLOYEE_REINSTATEMENT: [
    "requestedEmploymentAction",
    "requestedRoleId",
    "expectedProfileVersion",
  ],
  EMPLOYEE_ROLE_CHANGE: ["requestedRoleId", "expectedProfileVersion"],
};
const create = async (body) => {
  const allowed = [
    "applicationType",
    "reason",
    ...(requests[body.applicationType] || []).filter(
      (key) => key !== "expectedProfileVersion",
    ),
  ];
  return ownCreate(
    Object.fromEntries(
      Object.entries(body).filter(([key]) => allowed.includes(key)),
    ),
  );
};
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="账号申请"
      :title="admin ? '资格与资料审批' : '我的资格与资料申请'"
      description="普通资料直接修改；额度申请填写总额度，有效天数留空为长期调整，填写后从审批通过开始计时，到期使用长期额度。"
    />
    <ResourceDesk
      ref="desk"
      title="成员变更申请"
      :columns="columns"
      :query-fields="fields.MemberChangeQueryRequest"
      :list="list"
      :initial-query="{
        id: route.query.applicationId
          ? Number(route.query.applicationId)
          : undefined,
      }"
      :create="
        !admin && access.has('member-change:create:self') ? create : undefined
      "
      :create-fields="
        fields.MemberChangeCreateRequest.filter(
          (field) => field.key !== 'acceptedRuleVersion',
        )
      "
      :defaults="{ applicationType: 'READER_QUALIFICATION' }"
    >
      <template #actions="{ row }"
        ><el-button
          v-if="
            row.status === 'PENDING' &&
            admin &&
            access.has(approvalCode[row.applicationType], 'ALL')
          "
          link
          type="primary"
          @click="openDecision(row)"
          >审批</el-button
        >
        <el-button
          v-if="
            row.status === 'PENDING' &&
            !admin &&
            access.has('member-change:cancel:self')
          "
          link
          type="danger"
          @click="cancel(row)"
          >取消申请</el-button
        ></template
      >
    </ResourceDesk>
    <el-dialog
      class="workspace-dialog"
      v-model="decisionDialog"
      title="审批申请"
      width="min(600px,95vw)"
      ><el-form label-position="top">
        <DtoFields
          :model="decision"
          :fields="[
            {
              key: 'decision',
              label: '审批结果',
              options: [
                { value: 'APPROVE', label: '通过' },
                { value: 'REJECT', label: '拒绝' },
              ],
            },
            { key: 'reason', label: '审批说明', type: 'textarea' },
            { key: 'expectedVersion', label: '申请版本', type: 'number' },
          ]"
        /> </el-form
      ><template #footer
        ><el-button @click="decisionDialog = false">取消</el-button
        ><el-button type="primary" :loading="submitting" @click="decide"
          >提交审批</el-button
        ></template
      ></el-dialog
    >
  </AppShell>
</template>
