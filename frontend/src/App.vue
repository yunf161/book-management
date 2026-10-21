<script setup>
import { onMounted, onUnmounted, watch } from "vue";
import { useRouter } from "vue-router";
import { session } from "./stores/session";
import { rules, refreshRules, clearRules } from "./stores/rules";
import BorrowingRulesDialog from "./components/BorrowingRulesDialog.vue";
import {
  access,
  canEnter,
  refreshPermissions,
  clearPermissions,
} from "./stores/permissions";
import {
  startCommunication,
  stopCommunication,
  resumeCommunication,
} from "./stores/communication";

const router = useRouter();
let communicationToken = "";
let communicationRestricted;
async function initialize() {
  await refreshRules();
  if (!session.token) return;
  const restricted = !rules.ready || !!rules.value?.requiresConsent;
  // 周期核对不重复建立连接；令牌轮换或受限状态变化时才切换通道。
  if (
    communicationToken !== session.token ||
    communicationRestricted !== restricted
  ) {
    communicationToken = session.token;
    communicationRestricted = restricted;
    startCommunication({ restricted });
  } else {
    resumeCommunication();
  }
  if (rules.ready && !rules.value?.requiresConsent) {
    await refreshPermissions();
  } else {
    clearPermissions();
  }
}
async function policyChanged() {
  stopCommunication();
  communicationToken = "";
  clearPermissions();
  rules.ready = false;
  await initialize();
}
let permissionTimer;
watch(
  () => `${session.claims.memberId}:${session.claims.roleId}`,
  () => {
    clearPermissions();
    clearRules();
  },
);
// 包含JWT轮换：旧JTI失效前用新票据重新建连接。
watch(
  () => session.token,
  (token) => {
    if (token) {
      initialize();
    } else {
      clearRules();
      clearPermissions();
      stopCommunication();
      if (!router.currentRoute.value.meta.public) router.replace("/login");
    }
  },
  { immediate: true },
);

const visible = () => {
  if (document.visibilityState === "visible") {
    initialize();
  }
};
watch(
  () => access.grants,
  () => {
    const route = router.currentRoute.value;
    // 初次路由尚未完成时由守卫处理，避免摘要更新不断取消正在进行的导航。
    if (
      route.matched.length &&
      access.ready &&
      !route.meta.public &&
      !canEnter(route.path)
    )
      router.replace("/home");
  },
);
onMounted(() => {
  document.addEventListener("visibilitychange", visible);
  // 网络恢复后立即补查离线消息，而不是等待下一次退避重连。
  window.addEventListener("online", visible);
  window.addEventListener("library-rules-updated", policyChanged);
  window.addEventListener("library-rules-accepted", initialize);
  permissionTimer = setInterval(() => {
    if (session.token && document.visibilityState === "visible") initialize();
  }, 30000);
});
onUnmounted(() => {
  document.removeEventListener("visibilitychange", visible);
  window.removeEventListener("online", visible);
  window.removeEventListener("library-rules-updated", policyChanged);
  window.removeEventListener("library-rules-accepted", initialize);
  clearInterval(permissionTimer);
  stopCommunication();
});
</script>
<template>
  <router-view
    v-if="!session.token || (rules.ready && !rules.value?.requiresConsent)"
  /><BorrowingRulesDialog />
</template>
