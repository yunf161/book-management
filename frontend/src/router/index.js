import { createRouter, createWebHistory } from "vue-router";
import { session } from "../stores/session";
import { rules, refreshRules } from "../stores/rules";
import { canEnter, refreshPermissions } from "../stores/permissions";
import { ElMessage } from "element-plus";
import { recoverNavigation } from "../modules/navigationRecovery";

const LoginPage = () => import("../views/LoginPage.vue");
const HomePage = () => import("../views/HomePage.vue");
const CatalogPage = () => import("../views/CatalogPage.vue");
const BorrowPage = () => import("../views/BorrowPage.vue");
const ReadingPlanPage = () => import("../views/ReadingPlanPage.vue");
const AdminBooksPage = () => import("../views/AdminBooksPage.vue");
const AdminBorrowPage = () => import("../views/AdminBorrowPage.vue");
const AdminPlansPage = () => import("../views/AdminPlansPage.vue");
const AdminResourcesPage = () => import("../views/AdminResourcesPage.vue");
const MemberChangesPage = () => import("../views/MemberChangesPage.vue");
const PortalPage = () => import("../views/PortalPage.vue");
const ProfilePage = () => import("../views/ProfilePage.vue");
const MessagesPage = () => import("../views/MessagesPage.vue");

const portal = (path, title, description, kind, admin = false) => ({
  path,
  component: ["readers", "departments", "staff", "permissions"].includes(kind)
    ? AdminResourcesPage
    : kind === "approvals"
      ? MemberChangesPage
      : PortalPage,
  meta: { title, description, kind, admin },
});

const router = createRouter({
  history: createWebHistory(),
  // 菜单切换展示新页面顶部；浏览器后退/前进则恢复原滚动位置。
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition || { top: 0, left: 0 };
  },
  routes: [
    { path: "/login", component: LoginPage, meta: { public: true } },
    {
      path: "/",
      redirect: () => (session.isSystemAdmin ? "/admin/dashboard" : "/home"),
    },
    { path: "/home", component: HomePage },
    { path: "/ai", component: () => import("../views/AiChatPage.vue") },
    { path: "/catalog", component: CatalogPage },
    { path: "/borrow", component: BorrowPage },
    { path: "/reading-plan", component: ReadingPlanPage },
    portal(
      "/activities",
      "图书馆活动",
      "发现主题阅读与馆藏活动。",
      "activities",
    ),
    { path: "/messages", component: MessagesPage },
    { path: "/profile", component: ProfilePage },
    {
      path: "/admin/operation-logs",
      component: () => import("../views/OperationLogsPage.vue"),
      meta: { admin: true },
    },
    { path: "/member-changes", component: MemberChangesPage },
    {
      path: "/admin/operations",
      component: () => import("../views/OperationsPage.vue"),
      meta: { admin: true },
    },
    {
      path: "/ai/handoffs",
      component: () => import("../views/AiHandoffsPage.vue"),
    },
    { path: "/ai/tasks", component: () => import("../views/AiTasksPage.vue") },
    {
      path: "/admin/ai-controls",
      component: () => import("../views/AiControlsPage.vue"),
      meta: { admin: true },
    },
    {
      path: "/admin/knowledge",
      component: () => import("../views/KnowledgePage.vue"),
      meta: { admin: true },
    },
    portal(
      "/admin/permissions",
      "权限管理",
      "维护操作权限定义。",
      "permissions",
      true,
    ),
    portal(
      "/admin/dashboard",
      "馆员工作台",
      "先处理需要你判断的事项。",
      "dashboard",
      true,
    ),
    { path: "/admin/books", component: AdminBooksPage, meta: { admin: true } },
    {
      path: "/admin/borrow",
      component: AdminBorrowPage,
      meta: { admin: true },
    },
    { path: "/admin/plans", component: AdminPlansPage, meta: { admin: true } },
    portal(
      "/admin/readers",
      "读者管理",
      "管理人类读者的资格、借阅与额度。",
      "readers",
      true,
    ),
    portal(
      "/admin/departments",
      "部门管理",
      "维护部门资料与员工归属。",
      "departments",
      true,
    ),
    portal(
      "/admin/staff",
      "员工管理",
      "维护员工资料、岗位与任职状态。",
      "staff",
      true,
    ),
    // 旧书签回到工作台；AI 服务改由任务、知识库和能力控制管理。
    { path: "/admin/agents", redirect: "/admin/dashboard" },
    portal(
      "/admin/approvals",
      "审批中心",
      "集中处理需要人工判断的业务。",
      "approvals",
      true,
    ),
    { path: "/admin/messages", component: MessagesPage, meta: { admin: true } },
    portal(
      "/admin/activities",
      "活动与备书",
      "分别处理活动审批和馆藏准备。",
      "activities",
      true,
    ),
    portal(
      "/admin/tasks",
      "AI 任务记录",
      "检查 AI 馆员的执行轨迹。",
      "tasks",
      true,
    ),
    portal(
      "/admin/reports",
      "运营统计",
      "查看馆藏、借阅与服务概况。",
      "reports",
      true,
    ),
    {
      path: "/admin/policies",
      component: () => import("../views/AdminPoliciesPage.vue"),
      meta: { admin: true },
    },
  ],
});

router.beforeEach(async (to) => {
  // 未开放模块保留原型路由，入口隐藏且旧链接统一回到可用页面。
  if (["activities", "tasks", "reports", "policies"].includes(to.meta.kind))
    return session.isSystemAdmin ? "/admin/dashboard" : "/home";
  if (!to.meta.public && !session.token) return "/login";
  if (session.token && !to.meta.public) {
    await refreshRules();
    if (!rules.ready || rules.value?.requiresConsent)
      return to.path === "/home" ? true : "/home";
    await refreshPermissions();
    if (!canEnter(to.path)) return "/home";
  }
  if (to.path === "/login" && session.token)
    return session.isSystemAdmin ? "/admin/dashboard" : "/home";
});

// 分包404不能表现为“菜单点击没反应”：加载最新版入口，并保留目标页面地址。
router.onError((error, to) => {
  if (!recoverNavigation(error, to?.fullPath || window.location.pathname)) {
    ElMessage.error(
      "页面加载失败，请刷新后重试；若仍失败，请检查后端和网络连接",
    );
  }
});

export default router;
