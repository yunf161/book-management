<script setup>
// 仅供开发服务器的视觉验收：不登录、不请求后端、不写业务数据。
import { onMounted, ref } from "vue";
import ResourceDesk from "../../src/components/ResourceDesk.vue";
import PageHeader from "../../src/components/PageHeader.vue";
const desk = ref(null);
const rows = [
  { id: 1, name: "阅读与思考", status: "READING", progressPercent: 35 },
  { id: 2, name: "已完成的阅读", status: "COMPLETED", progressPercent: 100 },
  { id: 3, name: "尚未开始", status: "PLANNED", progressPercent: 0 },
  { id: 4, name: "待处理的逾期", status: "OVERDUE", progressPercent: null },
];
const fields = Array.from({ length: 16 }, (_, i) => ({
  key: `field${i}`,
  label: `长表单测试字段 ${i + 1}`,
}));
onMounted(() => {
  if (new URLSearchParams(location.search).has("dialog")) desk.value.open();
  // 导出尺寸证据，配合 headless 浏览器截图验证，不触碰桌面窗口。
  setTimeout(() => {
    const footer = document.querySelector(
      ".workspace-dialog .el-dialog__footer",
    );
    const body = document.querySelector(".workspace-dialog .el-dialog__body");
    document.body.dataset.visualAudit = JSON.stringify({
      viewport: innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      statusTags: document.querySelectorAll(".record-status").length,
      progressBars: document.querySelectorAll(".record-progress").length,
      footerVisible: footer
        ? footer.getBoundingClientRect().bottom <= innerHeight
        : null,
      formScrollable: body ? body.scrollHeight > body.clientHeight : null,
    });
  }, 1800);
});
</script>
<template>
  <main class="content" style="max-width: 1200px; margin: auto">
    <PageHeader
      eyebrow="隔离视觉样例"
      title="业务记录与阅读进度"
      description="状态、进度与长表单展示验收；不代表真实业务数据。"
    />
    <ResourceDesk
      ref="desk"
      title="阅读记录"
      :columns="[
        { key: 'name', label: '名称' },
        { key: 'status', label: '状态' },
        { key: 'progressPercent', label: '阅读进度' },
      ]"
      :query-fields="[{ key: 'name', label: '名称' }]"
      :create-fields="fields"
      :list="async () => ({ rows, total: rows.length })"
      :create="async () => {}"
    />
  </main>
</template>
