<script setup>
import { onMounted, onBeforeUnmount, ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { api } from "../api/library";
import { handoffApi } from "../api/aiHandoff";
import { ElMessage } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { aiApi, streamAiChat } from "../api/ai";
import { session } from "../stores/session";
import { access } from "../stores/permissions";
import { withoutLockVersions } from "../modules/recordVersions";

const task = ref("CONSULTATION");
// 示例只填入输入框，不自动调用模型或提交业务；发送与确认仍由用户主动操作。
const openingQuestions = {
  CONSULTATION: [
    "你好，请介绍一下自己",
    "推荐一本 Java 入门书",
    "查询我当前的借阅额度",
  ],
  BUSINESS: [
    "我想申请借一本书",
    "帮我整理一份阅读计划",
    "我想提交资料变更申请",
  ],
};
const storageKey = () => `ai-chat:${session.claims.memberId}:${task.value}`;
const conversationId = ref(sessionStorage.getItem(storageKey()));
const actions = ref([]),
  skills = ref([]),
  actionSaving = ref(false);
const messages = ref([]),
  sources = ref([]),
  question = ref("");
const pending = ref(false),
  error = ref("");
let controller;
const router = useRouter(),
  handoffDialog = ref(false),
  contacts = ref([]),
  handoffBusy = ref(false);
const handoff = reactive({ staffMemberId: null, summary: "" });
async function openHandoff() {
  const data = await api.contacts({
    memberType: "EMPLOYEE",
    page: 1,
    pageSize: 100,
  });
  contacts.value = data.rows;
  handoff.summary = messages.value
    .filter((m) => !m.pending)
    .slice(-4)
    .map((m) => (m.role === "user" ? "本人：" : "AI：") + m.content)
    .join("\n")
    .slice(0, 2000);
  handoffDialog.value = true;
}
async function requestHandoff() {
  handoffBusy.value = true;
  try {
    await handoffApi.create({
      staffMemberId: handoff.staffMemberId,
      summary: handoff.summary,
      aiConversationId: conversationId.value,
    });
    handoffDialog.value = false;
    ElMessage.success("已发送交接摘要，等待指定馆员接管");
    router.push("/ai/handoffs");
  } finally {
    handoffBusy.value = false;
  }
}

async function send() {
  if (pending.value || !question.value.trim()) return;
  const content = question.value.trim();
  if (content.length > 2000) {
    error.value = "问题不能超过2000字";
    return;
  }
  question.value = "";
  error.value = "";
  pending.value = true;
  sources.value = [];
  const draft = { role: "assistant", content: "", pending: true };
  messages.value.push({ role: "user", content }, draft);
  const index = messages.value.length - 1;
  const proposed = [];
  controller = new AbortController();
  try {
    await streamAiChat(
      {
        question: content,
        task: task.value,
        ...(conversationId.value
          ? { conversationId: conversationId.value }
          : {}),
      },
      (event, value) => {
        if (event === "meta") {
          conversationId.value = value.conversationId;
          sessionStorage.setItem(storageKey(), value.conversationId);
        }
        if (event === "delta") messages.value[index].content += value.text;
        if (event === "sources") sources.value = value.sources || [];
        if (event === "action") proposed.push(value);
        if (event === "done") messages.value[index].pending = false;
      },
      controller.signal,
    );
    if (proposed.length) {
      actions.value = [
        ...proposed,
        ...actions.value.filter(
          (item) => !proposed.some((next) => next.actionId === item.actionId),
        ),
      ];
      await loadActions().catch(() => {
        error.value = "草稿已生成，但操作列表刷新失败，请稍后刷新操作状态";
      });
    }
  } catch (failure) {
    // 断流、取消和引用校验失败均移除临时回答，不保留看似成功的半轮内容。
    messages.value.splice(index, 1);
    sources.value = [];
    error.value =
      failure.name === "AbortError"
        ? "已停止，本轮回答未确认"
        : failure.message;
  } finally {
    pending.value = false;
  }
}

async function clear() {
  if (pending.value) return;
  try {
    if (conversationId.value) await aiApi.clear(conversationId.value);
  } catch (failure) {
    if (failure.response?.status !== 404) {
      error.value = "清空失败，请稍后重试";
      return;
    }
  }
  sessionStorage.removeItem(storageKey());
  conversationId.value = null;
  messages.value = [];
  sources.value = [];
  error.value = "";
}

async function loadActions() {
  if (task.value !== "BUSINESS") return;
  actions.value = (await aiApi.actions({ page: 1, pageSize: 10 })).rows || [];
}

async function executeAction(action, cancel = false) {
  if (actionSaving.value) return;
  actionSaving.value = true;
  error.value = "";
  try {
    if (cancel) await aiApi.cancel(action);
    else await aiApi.confirm(action);
    await loadActions();
  } catch (failure) {
    error.value =
      "确认请求未完成，请先刷新操作状态核实结果，不要重新生成相同操作";
    await loadActions().catch(() => {});
  } finally {
    actionSaving.value = false;
  }
}

async function loadConversation() {
  if (!conversationId.value) return;
  pending.value = true;
  try {
    const result = await aiApi.conversation(conversationId.value);
    messages.value = result.messages || [];
    sources.value = result.sources || [];
  } catch {
    sessionStorage.removeItem(storageKey());
    conversationId.value = null;
    error.value = "临时会话已失效或不可访问，可以重新提问";
  } finally {
    pending.value = false;
  }
}

async function switchTask(next) {
  if (pending.value || actionSaving.value || next === task.value) return;
  task.value = next;
  conversationId.value = sessionStorage.getItem(storageKey());
  messages.value = [];
  sources.value = [];
  actions.value = [];
  skills.value = [];
  error.value = "";
  if (next === "BUSINESS") {
    try {
      skills.value = await aiApi.skills();
      await loadActions();
    } catch {
      error.value = "业务助手权限或服务暂不可用";
    }
  }
  await loadConversation();
}

onMounted(loadConversation);
onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="阅知 / AI咨询"
      :title="
        task === 'BUSINESS' ? '业务助手 · 确认后办理' : '和 AI 馆员聊一聊'
      "
      description="查询真实馆藏、当前政策、本人借阅和额度；通用知识可使用已审核资料。"
    />
    <section class="ai-panel">
      <div class="actions agent-switch" aria-label="智能体选择">
        <el-button
          :type="task === 'CONSULTATION' ? 'primary' : 'default'"
          :disabled="pending || actionSaving"
          @click="switchTask('CONSULTATION')"
          >咨询助手</el-button
        >
        <el-button
          v-if="access.has('ai:business:self', 'SELF')"
          :type="task === 'BUSINESS' ? 'primary' : 'default'"
          :disabled="pending || actionSaving"
          @click="switchTask('BUSINESS')"
          >业务助手</el-button
        >
      </div>
      <div class="section-head">
        <p>
          {{
            task === "BUSINESS"
              ? "一个业务智能体，按授权Skill办理；请先核对操作草稿，点击确认后才执行。"
              : "仅提供查询与建议，不能代替审批或执行借还操作。"
          }}
        </p>
        <el-button :disabled="pending" @click="clear">新对话</el-button>
        <el-button
          v-if="access.has('ai-handoff:create', 'SELF')"
          :disabled="pending || !conversationId"
          @click="openHandoff"
          >转人工</el-button
        >
      </div>
      <section v-if="!messages.length" class="ai-welcome">
        <span class="ai-welcome-seal" aria-hidden="true">阅</span>
        <h2>
          {{
            task === "BUSINESS"
              ? "把想法变成下一步行动"
              : "今天，想从哪里开始？"
          }}
        </h2>
        <p>
          {{
            task === "BUSINESS"
              ? "告诉我你想办理的事，我会先整理操作草稿，由你确认。"
              : "聊聊阅读、寻找一本好书，或了解你的图书馆服务。"
          }}
        </p>
        <div
          class="ai-opening-questions"
          role="group"
          aria-label="示例问题，点击后填入输入框"
        >
          <el-button
            v-for="prompt in openingQuestions[task]"
            :key="prompt"
            @click="question = prompt"
            >{{ prompt }}</el-button
          >
        </div>
      </section>
      <div aria-live="polite" class="ai-messages">
        <article
          v-for="(message, index) in messages"
          :key="index"
          :class="['ai-message', message.role]"
        >
          <strong>{{ message.role === "user" ? "我" : "AI 馆员" }}</strong>
          <p>{{ message.content || "正在查询资料…" }}</p>
          <small v-if="message.pending">生成中，尚未确认为有效答案</small>
        </article>
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="warning"
        :closable="false"
        show-icon
      />
      <section v-if="task === 'BUSINESS'" class="ai-actions">
        <h3>可用Skill</h3>
        <div class="skill-tags">
          <el-tag v-for="skill in skills" :key="skill.code" size="small">{{
            skill.title
          }}</el-tag>
        </div>
        <div class="section-head">
          <h3>本人操作草稿与执行结果</h3>
          <el-button :disabled="pending || actionSaving" @click="loadActions"
            >刷新操作状态</el-button
          >
        </div>
        <el-empty
          v-if="!actions.length"
          description="还没有操作草稿，描述你想办理的业务即可"
        />
        <article
          v-for="action in actions"
          :key="action.actionId"
          class="action-card"
        >
          <strong>{{ action.title }}</strong
          ><el-tag size="small">{{
            {
              PREPARED: "待确认",
              EXECUTING: "执行中/待核实",
              SUCCEEDED: "执行成功",
              FAILED: "业务拒绝",
              UNKNOWN: "结果待核实",
              CANCELLED: "已取消",
              EXPIRED: "已过期",
            }[action.state] || action.state
          }}</el-tag>
          <small
            >操作编号：{{ action.actionId }}；有效至
            {{ action.expiresAt }}</small
          >
          <pre>{{
            JSON.stringify(withoutLockVersions(action.arguments), null, 2)
          }}</pre>
          <p>{{ action.notice }}</p>
          <div v-if="action.state === 'PREPARED'" class="actions">
            <el-button
              type="primary"
              :disabled="pending || actionSaving"
              @click="executeAction(action)"
              >确认执行</el-button
            >
            <el-button
              :disabled="pending || actionSaving"
              @click="executeAction(action, true)"
              >取消草稿</el-button
            >
          </div>
          <details v-if="action.result">
            <summary>查看实际业务结果</summary>
            <pre>{{
              JSON.stringify(withoutLockVersions(action.result), null, 2)
            }}</pre>
          </details>
        </article>
      </section>
      <section v-if="!pending && sources.length" class="ai-sources">
        <h3>本轮查询来源</h3>
        <details v-for="source in sources" :key="source.source_id">
          <summary>[{{ source.source_id }}] {{ source.tool }}</summary>
          <pre>{{
            JSON.stringify(withoutLockVersions(source.data), null, 2)
          }}</pre>
        </details>
      </section>
      <form @submit.prevent="send" class="ai-composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          :maxlength="2000"
          show-word-limit
          :disabled="pending"
          placeholder="请输入你的问题，例如：当前借阅政策允许借多久？"
          aria-label="咨询问题"
        />
        <div class="actions">
          <el-button v-if="pending" @click="controller?.abort()"
            >停止回答</el-button
          >
          <el-button
            type="primary"
            native-type="submit"
            :loading="pending"
            :disabled="!question.trim() || pending"
            >发送</el-button
          >
        </div>
      </form>
      <p class="ai-note">
        会话临时保留约30分钟；服务重启或容量回收后会失效。回答以本轮来源和业务接口为准。
      </p>
    </section>
    <el-dialog
      v-model="handoffDialog"
      title="确认转人工摘要"
      width="min(700px,94vw)"
      ><p>仅将下面经你确认的摘要发送给指定馆员，不发送内部提示词或工具原文。</p>
      <el-form label-width="100px"
        ><el-form-item label="接收馆员"
          ><el-select v-model="handoff.staffMemberId" filterable
            ><el-option
              v-for="person in contacts"
              :key="person.id"
              :value="person.id"
              :label="
                person.id + ' · ' + person.name
              " /></el-select></el-form-item
        ><el-form-item label="交接摘要"
          ><el-input
            v-model="handoff.summary"
            type="textarea"
            :rows="10"
            maxlength="2000"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button @click="handoffDialog = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!handoff.staffMemberId || !handoff.summary.trim()"
          :loading="handoffBusy"
          @click="requestHandoff"
          >确认发送并请求接管</el-button
        ></template
      ></el-dialog
    >
  </AppShell>
</template>

<style scoped>
.ai-panel {
  background: #fff;
  border: 1px solid #dbe4df;
  border-radius: 14px;
  padding: 24px;
  max-width: 1050px;
  margin: 0 auto;
}
.agent-switch {
  margin-bottom: 18px;
}
.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 20px;
}
.action-card {
  border: 1px solid #dbe4df;
  padding: 16px;
  border-radius: 10px;
  margin: 12px 0;
}
.action-card strong {
  margin-right: 12px;
}
.action-card small {
  display: block;
  color: #667b72;
  margin: 8px 0;
}
.action-card pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 300px;
  overflow: auto;
  font-size: 12px;
}
.ai-messages {
  display: grid;
  gap: 18px;
  margin: 22px 0;
}
.ai-message {
  padding: 18px 20px;
  border-radius: 12px;
  background: #f1f6f2;
  overflow-wrap: anywhere;
}
.ai-message.user {
  background: #e3eee8;
  margin-left: 12%;
}
.ai-message.assistant {
  margin-right: 6%;
}
.ai-message p {
  white-space: pre-wrap;
  line-height: 1.8;
}
.ai-message small,
.ai-note {
  color: #667b72;
}
.ai-sources {
  border-top: 1px solid #e3eae5;
  margin: 20px 0;
  padding-top: 12px;
}
.ai-sources summary {
  cursor: pointer;
  padding: 8px 0;
}
.ai-sources pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
}
.ai-composer .actions {
  justify-content: flex-end;
  margin-top: 12px;
}
.ai-note {
  margin: 18px 0 0;
  font-size: 12px;
}
@media (max-width: 640px) {
  .ai-panel {
    padding: 14px;
  }
  .ai-message.user,
  .ai-message.assistant {
    margin: 0;
  }
}
</style>
