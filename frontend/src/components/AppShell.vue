<script setup>
import { computed } from "vue";
import { useRouter } from "vue-router";
import { api } from "../api/library";
import { session } from "../stores/session";
import { communication } from "../stores/communication";
import { canEnter } from "../stores/permissions";
import {
  Reading,
  House,
  Collection,
  User,
  ChatDotRound,
  Setting,
  Files,
  DataAnalysis,
  MagicStick,
  Checked,
} from "@element-plus/icons-vue";

const router = useRouter();
const readerNavigation = [
  ["/home", "图书馆首页"],
  ["/ai", "AI咨询"],
  ["/ai/tasks", "AI任务"],
  ["/ai/handoffs", "人工交接"],
  ["/catalog", "图书检索"],
  ["/borrow", "我的借阅"],
  ["/reading-plan", "阅读计划"],
  ["/messages", "消息中心"],
  ["/profile", "个人中心"],
  ["/member-changes", "资料申请"],
];
const staffNavigation = [
  ["/admin/dashboard", "工作台"],
  ["/ai", "AI咨询"],
  ["/admin/books", "馆藏管理"],
  ["/admin/borrow", "借阅与归还"],
  ["/admin/readers", "读者管理"],
  ["/admin/departments", "部门管理"],
  ["/admin/permissions", "权限管理"],
  ["/admin/operation-logs", "操作审计"],
  ["/admin/knowledge", "知识库管理"],
  ["/admin/operations", "运营与批量处理"],
  ["/ai/tasks", "AI任务管理"],
  ["/admin/ai-controls", "AI能力控制"],
  ["/ai/handoffs", "AI人工交接"],
  ["/admin/policies", "政策管理"],
  ["/admin/staff", "员工管理"],
  ["/admin/approvals", "审批中心"],
  ["/admin/messages", "客服会话"],
  ["/admin/plans", "阅读计划"],
];
const navigation = computed(() =>
  session.isSystemAdmin
    ? staffNavigation.filter((item) => canEnter(item[0]))
    : canEnter("/admin/dashboard")
      ? [...readerNavigation, ["/admin/dashboard", "馆员工作台"]]
      : readerNavigation,
);
const visibleNavigation = computed(() =>
  navigation.value.filter((item) => canEnter(item[0])),
);

// 分组只影响导航展示；入口仍使用原有 canEnter 权限结果。
const navigationGroups = computed(() => {
  const groups = [
    [
      "日常工作",
      [
        "/admin/dashboard",
        "/admin/books",
        "/admin/borrow",
        "/admin/approvals",
        "/admin/plans",
      ],
    ],
    [
      "成员与沟通",
      [
        "/admin/readers",
        "/admin/staff",
        "/admin/departments",
        "/admin/messages",
      ],
    ],
    [
      "智能服务",
      [
        "/ai",
        "/ai/tasks",
        "/ai/handoffs",
        "/admin/knowledge",
        "/admin/ai-controls",
      ],
    ],
    [
      "运营与设置",
      [
        "/admin/operations",
        "/admin/operation-logs",
        "/admin/policies",
        "/admin/permissions",
      ],
    ],
  ];
  return groups
    .map(([label, paths]) => ({
      label,
      items: visibleNavigation.value.filter(([path]) => paths.includes(path)),
    }))
    .filter((group) => group.items.length);
});

function navigationIcon(path) {
  if (path.endsWith("dashboard")) return House;
  if (path.includes("books")) return Collection;
  if (path.includes("borrow") || path.includes("plans")) return Reading;
  if (path.includes("approvals")) return Checked;
  if (path.includes("messages") || path.includes("handoffs"))
    return ChatDotRound;
  if (
    path.includes("readers") ||
    path.includes("staff") ||
    path.includes("agents")
  )
    return User;
  if (path.includes("operations")) return DataAnalysis;
  if (
    path.includes("knowledge") ||
    path.includes("logs") ||
    path.includes("policies")
  )
    return Files;
  if (path.startsWith("/ai")) return MagicStick;
  return Setting;
}

async function logout() {
  try {
    await api.logout();
  } finally {
    session.clear();
    router.push("/login");
  }
}
</script>

<template>
  <div class="site-shell">
    <a class="skip-link" href="#main-content">跳至主要内容</a>
    <header class="topbar">
      <router-link
        class="brand"
        :to="session.isSystemAdmin ? '/admin/dashboard' : '/home'"
      >
        <span class="brandmark">阅</span>
        <span class="brand-copy"
          ><strong>阅知图书馆</strong
          ><small>LIBRARY WITH AN AI LIBRARIAN</small></span
        >
      </router-link>
      <div class="user-label">
        <span class="workspace-badge">{{
          session.isSystemAdmin ? "管理工作空间" : "阅读与探索"
        }}</span>
        <router-link
          v-if="
            canEnter(session.isSystemAdmin ? '/admin/messages' : '/messages')
          "
          class="quiet-link"
          :to="session.isSystemAdmin ? '/admin/messages' : '/messages'"
        >
          {{ session.isSystemAdmin ? "客服工作台" : "咨询馆员" }}
        </router-link>
        <button class="quiet-button" type="button" @click="logout">
          退出登录
        </button>
        <span class="avatar">{{
          (session.user?.name || "阅").slice(0, 1)
        }}</span>
        <span class="user-name">{{ session.user?.name || "当前用户" }}</span>
      </div>
    </header>

    <template v-if="!session.isSystemAdmin">
      <nav class="reader-nav" aria-label="读者导航">
        <router-link
          v-for="item in visibleNavigation"
          :key="item[0]"
          :to="item[0]"
        >
          {{ item[1]
          }}<span
            v-if="item[0].endsWith('/messages') && communication.unreadTotal"
            class="unread-count"
            >{{ Math.min(communication.unreadTotal, 99)
            }}{{ communication.unreadTotal > 99 ? "+" : "" }}</span
          >
        </router-link>
      </nav>
      <main id="main-content" class="content" tabindex="-1"><slot /></main>
    </template>

    <div v-else class="admin-layout">
      <aside class="admin-sidebar">
        <small>工作空间 / WORKSPACE</small>
        <nav aria-label="管理导航">
          <section
            v-for="group in navigationGroups"
            :key="group.label"
            class="navigation-group"
          >
            <h2>{{ group.label }}</h2>
            <router-link
              v-for="item in group.items"
              :key="item[0]"
              :to="item[0]"
              ><el-icon aria-hidden="true"
                ><component :is="navigationIcon(item[0])" /></el-icon
              ><span>{{ item[1] }}</span
              ><span
                v-if="
                  item[0].endsWith('/messages') && communication.unreadTotal
                "
                class="unread-count"
                >{{ Math.min(communication.unreadTotal, 99)
                }}{{ communication.unreadTotal > 99 ? "+" : "" }}</span
              ></router-link
            >
          </section>
        </nav>
      </aside>
      <main id="main-content" class="content" tabindex="-1"><slot /></main>
    </div>
    <footer class="footer-note">
      <span>阅知 · AI 馆员智慧图书馆</span><span>在书页之间，发现新的可能</span>
    </footer>
  </div>
</template>
