<script setup>
import { onMounted, ref } from "vue";
import { ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { runtimeApi as api } from "../api/aiRuntime";
const rows = ref([]),
  saving = ref(false);
const names = {
  GLOBAL: "AI全局服务",
  CONSULTATION: "咨询助手",
  BUSINESS: "业务助手与确认执行",
  RECOMMENDATION: "AI推荐重排",
};
async function load() {
  rows.value = await api.controls();
}
async function change(row) {
  await ElMessageBox.confirm(
    `${row.enabled ? "暂停" : "恢复"}${names[row.code]}？已提交业务不会回滚，恢复后不会自动重放旧任务。`,
    "能力控制",
  );
  saving.value = true;
  try {
    await api.update(row.code, {
      enabled: !row.enabled,
      expectedVersion: row.version,
    });
    await load();
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="AI管理"
      title="AI能力控制"
      description="开关直接作用于实际调用链。业务助手确认写入前重新校验，推荐暂停时自动使用规则算法。"
    /><el-alert
      title="全局暂停优先于独立开关。恢复全局不会改变各能力原先的设置，也不会自动重试任务。"
      type="info"
      :closable="false"
    /><el-table :data="rows"
      ><el-table-column label="能力"
        ><template #default="{ row }">{{
          names[row.code]
        }}</template></el-table-column
      ><el-table-column label="状态"
        ><template #default="{ row }">{{
          row.enabled ? "启用" : "暂停"
        }}</template></el-table-column
      ><el-table-column prop="version" label="版本" /><el-table-column
        label="操作"
        ><template #default="{ row }"
          ><el-button :loading="saving" @click="change(row)">{{
            row.enabled ? "暂停" : "恢复"
          }}</el-button></template
        ></el-table-column
      ></el-table
    ></AppShell
  >
</template>
