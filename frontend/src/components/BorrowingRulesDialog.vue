<script setup>
import { computed } from "vue";
import { rules, refreshRules, decideRules } from "../stores/rules";
import { session } from "../stores/session";
const blocked = computed(
  () =>
    !!session.token &&
    session.claims.memberType !== "AI_LIBRARIAN" &&
    (!rules.ready || rules.value?.requiresConsent),
);
async function accept() {
  try {
    await decideRules("ACCEPT");
    window.dispatchEvent(new Event("library-rules-accepted"));
  } catch {
    await refreshRules();
  }
}
async function decline() {
  await decideRules("DECLINE");
}
</script>

<template>
  <el-dialog
    :model-value="blocked"
    title="请阅读并确认借阅须知"
    width="min(650px, 94vw)"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <div v-if="rules.value">
      <p>{{ rules.value.title }} · {{ rules.value.ruleVersion }}</p>
      <div class="rules-content">{{ rules.value.content }}</div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="默认借阅天数">{{
          rules.value.parameters.defaultBorrowDays
        }}</el-descriptions-item>
        <el-descriptions-item label="最长借阅天数">{{
          rules.value.parameters.maxBorrowDays
        }}</el-descriptions-item>
        <el-descriptions-item label="最多续借次数">{{
          rules.value.parameters.maxRenewCount
        }}</el-descriptions-item>
        <el-descriptions-item label="默认续借天数">{{
          rules.value.parameters.defaultRenewDays
        }}</el-descriptions-item>
        <el-descriptions-item label="最长续借天数">{{
          rules.value.parameters.maxRenewDays
        }}</el-descriptions-item>
        <el-descriptions-item label="申请有效天数">{{
          rules.value.parameters.applicationValidityDays
        }}</el-descriptions-item>
        <el-descriptions-item label="逾期限制借阅">{{
          rules.value.parameters.blockBorrowWhenOverdue ? "是" : "否"
        }}</el-descriptions-item>
      </el-descriptions>
    </div>
    <p v-else>{{ rules.error || "正在加载借阅须知…" }}</p>
    <template #footer>
      <el-button v-if="rules.error" @click="refreshRules">重试加载</el-button>
      <el-button :disabled="!rules.value || rules.loading" @click="decline"
        >拒绝并退出全部设备</el-button
      >
      <el-button
        type="primary"
        :disabled="!rules.value || rules.loading"
        @click="accept"
        >已阅读并接受</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped>
.rules-content {
  white-space: pre-wrap;
  max-height: 260px;
  overflow: auto;
  line-height: 1.8;
  margin-bottom: 20px;
}
</style>
