<script setup>
import { computed, onMounted, reactive } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { api } from "../api/library";
import { session } from "../stores/session";
import { access, canEnter } from "../stores/permissions";

const route = useRoute();
const router = useRouter();
const kind = computed(() => route.meta.kind);
const dashboard = reactive({ borrow: 0, books: 0, plans: 0 });

const cards = computed(() => {
  const content = {
    dashboard: [
      ["待处理借阅", "进入借阅与归还查看", dashboard.borrow, "/admin/borrow"],
      ["馆藏维护", "管理书目、分类与副本", dashboard.books, "/admin/books"],
      ["阅读计划", "查询读者计划与条目", dashboard.plans, "/admin/plans"],
      ["读者管理", "查询与维护读者资料", "进入", "/admin/readers"],
      ["部门管理", "维护部门与员工归属", "进入", "/admin/departments"],
      ["员工管理", "查询与维护员工档案", "进入", "/admin/staff"],
      ["资料审批", "审批资格、额度和任职变更", "进入", "/admin/approvals"],
      ["权限管理", "维护操作权限定义", "进入", "/admin/permissions"],
      ["AI 馆员资料", "管理服务身份资料", "进入", "/admin/agents"],
    ],
    profile: [
      ["读者信息", session.user?.name || "当前读者"],
      ["登录邮箱", session.user?.email || "—"],
      ["借阅资格", "可在人员管理中维护"],
      ["阅读偏好", "偏好接口待后端接入"],
    ],
    messages: [
      ["业务通知", "通知接口待后端接入"],
      ["AI 馆员", "会话接口待后端接入"],
    ],
    activities: [
      ["主题阅读活动", "活动与备书模块待后端接入"],
      ["我的报名", "报名接口待后端接入"],
    ],
    readers: [
      ["人员检索", "人员管理接口已存在，专用管理视图待接入"],
      ["资格与额度", "通过人员管理维护"],
    ],
    departments: [
      ["部门资料", "部门接口已存在，管理表格待接入"],
      ["员工归属", "按 departmentId 关联"],
    ],
    staff: [
      ["员工档案", "人员管理接口已存在"],
      ["岗位与状态", "专用管理视图待接入"],
    ],
    agents: [
      ["AI 馆员状态", "人员资料接口已存在"],
      ["能力授权", "等待 AI 能力模块"],
    ],
    approvals: [
      ["借阅申请", "请在借阅与归还页面办理"],
      ["其他审批", "对应业务模块待接入"],
    ],
    tasks: [
      ["任务轨迹", "AI 任务接口待后端接入"],
      ["执行结果", "不会使用演示数据伪造"],
    ],
    reports: [
      ["馆藏统计", "统计接口待后端接入"],
      ["借阅趋势", "统计接口待后端接入"],
    ],
    policies: [
      ["借阅天数", "策略管理接口待后端接入"],
      ["逾期规则", "当前由后端业务规则执行"],
    ],
  };
  const result = content[kind.value] || [];
  return result.filter((card) => !card[3] || canEnter(card[3]));
});

/** 工作台数字来自各业务聚合查询，卡片直接进入相应工作页面。 */
async function loadDashboard() {
  if (kind.value !== "dashboard") return;
  const results = await Promise.allSettled([
    access.has("borrow-application:query", "ALL")
      ? api.applications(true, { page: 1, pageSize: 1, status: "PENDING" })
      : Promise.resolve({ total: 0 }),
    api.books({ page: 1, pageSize: 1 }),
    canEnter("/admin/plans")
      ? api.adminPlans({ pageNum: 1, pageSize: 1 })
      : Promise.resolve({ total: 0 }),
  ]);
  dashboard.borrow =
    results[0].status === "fulfilled" ? results[0].value.total || 0 : 0;
  dashboard.books =
    results[1].status === "fulfilled" ? results[1].value.total || 0 : 0;
  dashboard.plans =
    results[2].status === "fulfilled" ? results[2].value.total || 0 : 0;
}

onMounted(loadDashboard);
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="阅知 / 服务中心"
      :title="route.meta.title"
      :description="route.meta.description"
    />
    <div v-if="kind === 'dashboard'" class="summary-grid">
      <article
        v-for="card in cards"
        :key="card[0]"
        class="summary-card clickable"
        role="link"
        tabindex="0"
        @click="router.push(card[3])"
        @keyup.enter="router.push(card[3])"
      >
        <small>{{ card[0] }}</small
        ><b>{{ card[2] }}</b>
        <p>{{ card[1] }} →</p>
      </article>
    </div>
    <div v-else class="two-col portal-grid">
      <section v-for="card in cards" :key="card[0]" class="panel">
        <p class="eyebrow">功能区域</p>
        <h2>{{ card[0] }}</h2>
        <p class="muted">{{ card[1] }}</p>
        <el-tag type="info">待后端接入</el-tag>
      </section>
    </div>
    <section v-if="kind !== 'dashboard'" class="panel pending-panel">
      <div class="pending-icon">阅</div>
      <div>
        <h2>页面结构已按原型保留</h2>
        <p>
          该模块目前没有对应的完整后端接口。页面不会使用浏览器本地假数据模拟成功操作，接口完成后可直接在此接入。
        </p>
      </div>
    </section>
  </AppShell>
</template>
