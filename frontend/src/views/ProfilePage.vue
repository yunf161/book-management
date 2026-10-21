<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { useRoute } from "vue-router";
import { api } from "../api/library";
import { session } from "../stores/session";
import { access } from "../stores/permissions";
import { displayValue } from "../modules/display";

import { effectiveBorrowLimit } from "../modules/borrowQuota";

const quotaNow = ref(Date.now());
let quotaTimer;
// 到期展示不依赖用户刷新页面；服务器仍负责最终额度校验。
onMounted(() => {
  quotaTimer = setInterval(() => {
    quotaNow.value = Date.now();
  }, 1000);
});
onUnmounted(() => clearInterval(quotaTimer));
const loading = ref(false);
const route = useRoute();
const application = ref(null);
async function loadNotificationApplication() {
  application.value = null;
  if (!route.query.applicationId) return;
  try {
    const result = await api.myMemberChanges({
      id: route.query.applicationId,
      page: 1,
      pageSize: 1,
    });
    application.value = result.rows?.[0] || null;
  } catch {
    /* 本人申请查询接口执行权限与归属检查。 */
  }
}
watch(() => route.query.applicationId, loadNotificationApplication);
onMounted(loadNotificationApplication);
const tags = ref([]);
const selectedTagIds = ref([]);
const interestSaving = ref(false);

async function saveInterests() {
  interestSaving.value = true;
  try {
    await api.updateInterests(selectedTagIds.value);
    ElMessage.success("兴趣已保存，下次进入首页将生成新的推荐");
    await load();
  } finally {
    interestSaving.value = false;
  }
}

const editing = ref(false);
const member = ref(null);
const form = reactive({});
const memberType = computed(() => session.claims.memberType);
const memberId = computed(() => Number(session.claims.memberId));

async function load() {
  loading.value = true;
  try {
    const result = await api.myMember({
      id: memberId.value,
      memberType: memberType.value,
      page: 1,
      pageSize: 1,
    });
    member.value = result.rows?.[0] || null;
    Object.assign(form, member.value || {});
    if (access.has("recommendation:query:self")) {
      const [dictionary, selected] = await Promise.all([
        api.interestTags(),
        api.myInterests(),
      ]);
      tags.value = dictionary;
      selectedTagIds.value = selected.map((tag) => tag.id);
    }
  } finally {
    loading.value = false;
  }
}

/** MemberWriteRequest 采用选择性更新；版本号用于阻止覆盖其他请求的新数据。 */
async function save() {
  // 本人资料接口只接受姓名、手机号和读者偏好；管理字段通过申请审批维护。
  const body = {
    memberType: member.value.memberType,
    name: form.name,
    phone: form.phone,
    expectedVersion: member.value.version,
  };
  if (memberType.value === "READER") {
    body.personalizationEnabled = form.personalizationEnabled;
  }
  await api.updateMyMember(memberId.value, body);
  ElMessage.success("个人资料已更新");
  editing.value = false;
  await load();
}

onMounted(load);
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="阅知 / 个人中心"
      title="个人资料"
      description="查看账号、借阅资格、额度与阅读偏好。"
      ><el-button @click="$router.push('/member-changes')"
        >资格与资料申请</el-button
      ><el-button
        v-if="
          member &&
          !session.isSystemAdmin &&
          access.forMember('member:update', member.id)
        "
        type="primary"
        @click="editing = !editing"
        >{{ editing ? "取消编辑" : "编辑资料" }}</el-button
      ></PageHeader
    >
    <section v-if="application" class="panel profile-application">
      <h3>
        申请 #{{ application.id }} · {{ displayValue(application.status) }}
      </h3>
      <p>申请类型：{{ displayValue(application.applicationType) }}</p>
      <p>审批说明：{{ application.decisionReason || "无" }}</p>
    </section>
    <section
      v-if="access.has('interests:update:self')"
      class="panel profile-interests"
    >
      <h3>兴趣标签</h3>
      <p>用于规则推荐；清空选择可恢复热门与新书推荐。</p>
      <el-select
        v-model="selectedTagIds"
        multiple
        filterable
        clearable
        placeholder="选择感兴趣的标签"
      >
        <el-option
          v-for="tag in tags"
          :key="tag.id"
          :label="tag.name"
          :value="tag.id"
        />
      </el-select>
      <el-button type="primary" :loading="interestSaving" @click="saveInterests"
        >保存兴趣</el-button
      >
    </section>
    <div v-loading="loading" class="profile-layout">
      <section class="panel profile-card">
        <div class="profile-avatar">
          {{ (member?.name || "阅").slice(0, 1) }}
        </div>
        <h2>{{ member?.name || "—" }}</h2>
        <p>{{ member?.email || "—" }}</p>
        <div class="profile-tags">
          <el-tag
            :type="member?.accountStatus === 'ACTIVE' ? 'success' : 'warning'"
            >{{ displayValue(member?.accountStatus) }}</el-tag
          ><el-tag
            :type="
              member?.qualificationStatus === 'APPROVED' ? 'success' : 'info'
            "
            >{{ displayValue(member?.qualificationStatus) }}</el-tag
          >
        </div>
        <dl>
          <dt>成员 ID</dt>
          <dd>{{ member?.id }}</dd>
          <dt>人员类型</dt>
          <dd>{{ displayValue(member?.memberType) }}</dd>
          <dt>角色 ID</dt>
          <dd>{{ member?.roleId }}</dd>
          <dt>创建时间</dt>
          <dd>{{ member?.createdAt }}</dd>
          <dt>更新时间</dt>
          <dd>{{ member?.updatedAt }}</dd>
        </dl>
      </section>
      <section class="panel">
        <el-form v-if="member" label-position="top" class="form-grid">
          <el-form-item label="姓名"
            ><el-input v-model="form.name" :disabled="!editing" /></el-form-item
          ><el-form-item label="邮箱"
            ><el-input v-model="form.email" disabled /></el-form-item
          ><el-form-item label="手机"
            ><el-input
              v-model="form.phone"
              :disabled="!editing" /></el-form-item
          ><el-form-item label="邮箱验证时间"
            ><el-input v-model="form.emailVerifiedAt" disabled /></el-form-item
          ><el-form-item label="账号状态"
            ><el-input
              :model-value="displayValue(form.accountStatus)"
              disabled /></el-form-item
          ><el-form-item label="借阅资格"
            ><el-input
              :model-value="displayValue(form.qualificationStatus)"
              disabled /></el-form-item
          ><el-form-item label="当前借阅数量"
            ><el-input-number
              v-model="form.borrowedBookCount"
              disabled /></el-form-item
          ><el-form-item label="长期借阅额度"
            ><el-input-number
              v-model="form.permanentBorrowLimit"
              disabled /></el-form-item
          ><el-form-item label="临时借阅总额度"
            ><el-input-number
              v-model="form.temporaryBorrowLimit"
              disabled /></el-form-item
          ><el-form-item label="临时额度到期时间"
            ><el-input
              v-model="form.temporaryQuotaExpiresAt"
              disabled /></el-form-item
          ><el-form-item label="当前生效额度"
            ><el-input
              :model-value="
                String(effectiveBorrowLimit(member, quotaNow) ?? '—')
              "
              disabled /></el-form-item
          ><el-form-item label="已接受规则版本"
            ><el-input v-model="form.acceptedRuleVersion" disabled
          /></el-form-item>
          <template v-if="memberType === 'READER'"
            ><el-form-item label="阅读兴趣" class="span-2"
              ><el-input
                v-model="form.interests"
                :disabled="!editing"
                placeholder="在下方选择兴趣标签"
                readonly /></el-form-item
            ><el-form-item label="个性化推荐"
              ><el-switch
                v-model="form.personalizationEnabled"
                :disabled="!editing" /></el-form-item
          ></template>
          <template v-else-if="memberType === 'EMPLOYEE'"
            ><el-form-item label="员工工号"
              ><el-input v-model="form.employeeNo" disabled /></el-form-item
            ><el-form-item label="部门 ID"
              ><el-input-number
                v-model="form.departmentId"
                disabled /></el-form-item
            ><el-form-item label="岗位"
              ><el-input v-model="form.positionName" disabled /></el-form-item
            ><el-form-item label="任职状态"
              ><el-input
                :model-value="displayValue(form.employmentStatus)"
                disabled /></el-form-item
            ><el-form-item label="入职日期"
              ><el-input v-model="form.hiredOn" disabled /></el-form-item
            ><el-form-item label="离职日期"
              ><el-input v-model="form.leftOn" disabled /></el-form-item
          ></template>
          <template v-else
            ><el-form-item label="服务编码"
              ><el-input v-model="form.serviceCode" disabled /></el-form-item
            ><el-form-item label="负责人 ID"
              ><el-input-number
                v-model="form.ownerMemberId"
                disabled /></el-form-item
            ><el-form-item label="AI 状态"
              ><el-input v-model="form.status" disabled /></el-form-item
            ><el-form-item label="服务说明" class="span-2"
              ><el-input
                v-model="form.description"
                type="textarea"
                :disabled="!editing" /></el-form-item
          ></template>
        </el-form>
        <div v-if="editing" class="profile-actions">
          <el-button @click="editing = false">取消</el-button
          ><el-button type="primary" @click="save">保存修改</el-button>
        </div>
        <el-empty
          v-if="!loading && !member"
          description="没有查询到当前成员资料"
        />
      </section>
    </div>
  </AppShell>
</template>
