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
import { access } from "../stores/permissions";
import { recordVersion } from "../modules/recordVersions";
const route = useRoute(),
  admin = computed(() => route.path.startsWith("/admin"));
const tab = ref("plans"),
  desk = ref(null),
  itemDialog = ref(false),
  itemRow = ref(null),
  owner = ref(null),
  itemForm = reactive({});
const saving = ref(false);
const canPlan = (action, memberId) =>
  admin.value
    ? access.forMember(`reading-plan:${action}`, memberId)
    : access.has(`reading-plan:${action}:self`, "SELF");
const planList = (q) =>
  admin.value ? api.adminPlans(q) : api.myPlan({ silent: true });
const itemList = (q) =>
  admin.value ? api.adminPlanItems(q) : api.myPlanItems(q);
const planCreate = (b) =>
  admin.value ? api.createAdminPlan(b) : api.createMyPlan(b);
const planUpdate = (r, b) =>
  admin.value ? api.updateAdminPlan(r.memberId, b) : api.updateMyPlan(b);
const planDelete = (r) =>
  admin.value
    ? api.deleteAdminPlan(r.memberId, r.version)
    : api.deleteMyPlan(r.version);
async function parentPlan(memberId) {
  if (!admin.value) return api.myPlan();
  const result = await api.adminPlans({ memberId, pageNum: 1, pageSize: 1 });
  if (!result.rows?.[0]) throw new Error("没有查询到对应计划");
  return result.rows[0];
}
async function openItem(plan, row = null) {
  try {
    const current = await parentPlan(plan.memberId);
    owner.value = current.memberId;
    itemRow.value = row;
    for (const key of Object.keys(itemForm)) delete itemForm[key];
    Object.assign(
      itemForm,
      row || {
        sequenceNo: 1,
        priority: 1,
        status: "PLANNED",
        source: admin.value ? "LIBRARIAN" : "USER",
      },
    );
    itemForm.expectedPlanVersion = current.version;
    if (row) itemForm.version = recordVersion(row);
    itemDialog.value = true;
  } catch {
    /* 查询失败时不打开没有可信版本的修改窗口。 */
  }
}
async function saveItem() {
  if (saving.value) return;
  saving.value = true;
  try {
    const body = {};
    for (const f of itemRow.value
      ? fields.ReadingItemUpdateRequest
      : fields.ReadingItemCreateRequest) {
      if (itemForm[f.key] !== undefined)
        body[f.key] = itemForm[f.key] === "" ? null : itemForm[f.key];
    }
    if (itemRow.value) {
      if (admin.value)
        await api.updateAdminPlanItem(owner.value, itemRow.value.bookId, body);
      else await api.updateMyPlanItem(itemRow.value.bookId, body);
    } else if (admin.value) await api.addAdminPlanItem(owner.value, body);
    else await api.addMyPlanItem(body);
    itemDialog.value = false;
    ElMessage.success("阅读条目已保存");
    await desk.value.load();
  } catch {
    /* 保留当前窗口，冲突时可取消并刷新最新记录。 */
  } finally {
    saving.value = false;
  }
}
async function deleteItem(row) {
  const plan = await parentPlan(row.memberId);
  if (admin.value)
    return api.deleteAdminPlanItem(
      row.memberId,
      row.bookId,
      row.version,
      plan.version,
    );
  return api.deleteMyPlanItem(row.bookId, row.version, plan.version);
}
const planColumns = [
  { key: "memberId", label: "成员编号" },
  { key: "memberName", label: "成员" },
  { key: "name", label: "计划名称" },
  { key: "targetBooks", label: "目标完成书目数" },
  { key: "status", label: "状态" },
  { key: "version", label: "版本" },
];
const itemColumns = [
  { key: "memberId", label: "成员编号" },
  { key: "bookName", label: "图书" },
  { key: "sequenceNo", label: "顺序" },
  { key: "status", label: "状态" },
  { key: "progressPercent", label: "进度" },
  { key: "source", label: "来源" },
  { key: "version", label: "版本" },
];
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="阅读计划"
      :title="admin ? '阅读计划管理' : '我的阅读计划'"
      description="目标是希望完成的书目数量，不限制书单容量。达到目标且无待读、在读条目后才能完成计划。"
    />
    <el-tabs v-model="tab"
      ><el-tab-pane label="计划" name="plans" /><el-tab-pane
        label="阅读条目"
        name="items"
    /></el-tabs>
    <ResourceDesk
      v-if="tab === 'plans'"
      key="plans"
      ref="desk"
      title="阅读计划"
      page-key="pageNum"
      :columns="planColumns"
      :query-fields="
        admin
          ? fields.ReadingPlanQueryRequest.filter(
              (f) => !['pageNum', 'pageSize'].includes(f.key),
            )
          : []
      "
      :create-fields="
        fields.ReadingPlanCreateRequest.filter(
          (f) => admin || f.key !== 'memberId',
        )
      "
      :update-fields="fields.ReadingPlanUpdateRequest"
      :list="planList"
      :create="
        (admin ? access.has('reading-plan:create', 'ALL') : canPlan('create'))
          ? planCreate
          : undefined
      "
      :update="planUpdate"
      :remove="planDelete"
      :allow-update="(row) => canPlan('update', row.memberId)"
      :allow-remove="(row) => canPlan('delete', row.memberId)"
      :defaults="{ targetBooks: 12, status: 'ACTIVE' }"
    >
      <template #actions="{ row }"
        ><el-button
          v-if="canPlan('create', row.memberId)"
          link
          type="success"
          @click="openItem(row)"
          >添加图书</el-button
        ></template
      >
    </ResourceDesk>
    <ResourceDesk
      v-else
      key="items"
      ref="desk"
      title="阅读条目"
      page-key="pageNum"
      :columns="itemColumns"
      :query-fields="
        fields.ReadingItemQueryRequest.filter(
          (f) =>
            !['pageNum', 'pageSize'].includes(f.key) &&
            (admin || !['memberId', 'memberKeyword'].includes(f.key)),
        )
      "
      :list="itemList"
      :remove="deleteItem"
      :allow-remove="(row) => canPlan('delete', row.memberId)"
    >
      <template #actions="{ row }"
        ><el-button
          v-if="canPlan('update', row.memberId)"
          link
          type="primary"
          @click="openItem(row, row)"
          >修改进度与顺序</el-button
        ></template
      >
    </ResourceDesk>
    <el-dialog
      v-model="itemDialog"
      class="workspace-dialog"
      :title="itemRow ? '修改阅读条目' : '添加阅读条目'"
      width="min(850px,95vw)"
    >
      <el-form label-position="top"
        ><DtoFields
          :model="itemForm"
          :fields="
            itemRow
              ? fields.ReadingItemUpdateRequest
              : fields.ReadingItemCreateRequest
          "
      /></el-form>
      <template #footer
        ><el-button @click="itemDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveItem"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </AppShell>
</template>
