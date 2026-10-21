<script setup>
import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import PageSummary from "../components/PageSummary.vue";
import { api } from "../api/library";
import { session } from "../stores/session";
import { access } from "../stores/permissions";
import {
  communication,
  refreshUnread,
  resumeCommunication,
} from "../stores/communication";

const route = useRoute();
const router = useRouter();
const staff = computed(
  () =>
    session.claims.memberType === "EMPLOYEE" &&
    access.has("message:send:multiple"),
);
const tab = ref("conversations");
const conversations = ref([]),
  conversationTotal = ref(0),
  selected = ref(null);
const history = ref([]),
  historyTotal = ref(0),
  historyPage = ref(1),
  historySize = ref(20);
const messages = ref([]),
  messageTotal = ref(0),
  detail = ref(null);
const busy = reactive({
  conversations: false,
  history: false,
  messages: false,
  send: false,
  create: false,
  broadcast: false,
});
const cq = reactive({
  page: 1,
  pageSize: 10,
  id: null,
  targetMemberId: null,
  subject: "",
  status: "",
  unreadOnly: false,
});
const mq = reactive({
  page: 1,
  pageSize: 10,
  id: null,
  conversationId: null,
  senderMemberId: null,
  recipientMemberId: null,
  sendType: "",
  messageType: "",
  businessType: "",
  businessId: null,
  subject: "",
  read: "",
  afterSequence: null,
});
const conversationDates = ref([]),
  messageDates = ref([]);
const newDialog = ref(false),
  broadcastDialog = ref(false),
  renameDialog = ref(false);
const newForm = reactive({ target: "", subject: "" });
const broadcastForm = reactive({ recipients: [], subject: "", body: "" });
const draft = ref(""),
  rename = ref("");
const chatBody = ref(null);
let historyRequest = 0,
  conversationRequest = 0,
  messageRequest = 0;
let refreshTimer;
let mounted = true;
// 失败后保存请求快照及UUID；相同内容重试绝不能换成另一个幂等键。
let pendingSend, pendingBroadcast;
const retrySend = ref(false),
  retryBroadcast = ref(false);
const myId = computed(() => String(session.user?.id));
const statusLabel = computed(
  () =>
    ({
      connected: "实时提醒已连接",
      connecting: "连接中",
      reconnecting: "重连中",
      disconnected: "未连接",
      unavailable: "实时提醒不可用",
    })[communication.status],
);
// 远程联系人分页只使用后端授权结果，不能把手工编号当成名单。
const contacts = ref([]),
  contactLoading = ref(false),
  contactTotal = ref(0);
const contactQuery = reactive({ name: "", page: 1, pageSize: 20 });
let contactRequest = 0;
async function loadContacts(name = contactQuery.name, reset = false) {
  if (reset) contactQuery.page = 1;
  contactQuery.name = name;
  const request = ++contactRequest;
  contactLoading.value = true;
  try {
    const result = await api.contacts({ ...contactQuery });
    if (!mounted || request !== contactRequest) return;
    contacts.value = (result.rows || []).map((c) => ({
      id: String(c.id),
      label: `${c.name} · ${c.memberType === "EMPLOYEE" ? "馆员" : "读者"} · ${c.departmentName || "未配置部门"} · #${c.id}`,
    }));
    contactTotal.value = result.total || 0;
  } catch {
    if (request === contactRequest) {
      contacts.value = [];
      contactTotal.value = 0;
    }
    /* 接口负责鉴权及错误提示，不能回退到未授权成员名单。 */
  } finally {
    if (request === contactRequest) contactLoading.value = false;
  }
}
function searchContacts(name) {
  loadContacts(name, true);
}
watch([newDialog, broadcastDialog], ([open, broadcast]) => {
  if (open || broadcast) loadContacts("", true);
});

/** 通知只携带业务定位信息，目标聚合接口仍重新验证本人范围。 */
function openBusiness(message) {
  const routes = {
    BORROW_APPLICATION: {
      path: "/borrow",
      query: { applicationId: message.businessId },
    },
    BORROW_RECORD: { path: "/borrow", query: { recordId: message.businessId } },
    MEMBER_CHANGE: {
      path: "/member-changes",
      query: { applicationId: message.businessId },
    },
  };
  if (routes[message.businessType]) router.push(routes[message.businessType]);
}
const timestamp = (value) =>
  value ? value.replace("T", " ").slice(0, 19) : "—";
const queryDates = (dates) =>
  dates?.length === 2 ? { createdFrom: dates[0], createdTo: dates[1] } : {};
const isMine = (row) => String(row.senderMemberId) === myId.value;
const recipientLabel = (row) =>
  row.recipients
    ?.map((r) => `${r.recipientName || "成员"} #${r.recipientMemberId}`)
    .join("、") || "本人";

async function loadConversations(reset = false) {
  if (reset) cq.page = 1;
  const request = ++conversationRequest;
  busy.conversations = true;
  try {
    const result = await api.conversations({
      ...cq,
      ...queryDates(conversationDates.value),
    });
    if (!mounted || request !== conversationRequest) return;
    conversations.value = result.rows || [];
    conversationTotal.value = result.total || 0;
    const current = conversations.value.find(
      (c) => c.id === selected.value?.id,
    );
    if (current) selected.value = current;
  } catch {
    /* 请求封装负责显示服务端错误。 */
  } finally {
    if (request === conversationRequest) busy.conversations = false;
  }
}

async function loadHistory() {
  if (!selected.value) return;
  const conversationId = selected.value.id;
  const request = ++historyRequest;
  busy.history = true;
  try {
    const result = await api.messages({
      conversationId,
      box: "ALL",
      page: historyPage.value,
      pageSize: historySize.value,
    });
    if (
      !mounted ||
      request !== historyRequest ||
      selected.value?.id !== conversationId
    )
      return;
    // 后端分页按最新消息排序，当前页转为从上到下的会话时间顺序。
    history.value = [...(result.rows || [])].reverse();
    historyTotal.value = result.total || 0;
    await nextTick();
    if (
      mounted &&
      request === historyRequest &&
      selected.value?.id === conversationId
    )
      await markVisibleRead();
    if (chatBody.value && historyPage.value === 1)
      chatBody.value.scrollTop = chatBody.value.scrollHeight;
  } catch {
    /* 保留已有内容，避免网络失败时清空会话。 */
  } finally {
    if (request === historyRequest) busy.history = false;
  }
}

async function openConversation(row) {
  selected.value = row;
  draft.value = "";
  retrySend.value = false;
  pendingSend = undefined;
  history.value = [];
  historyPage.value = 1;
  await loadHistory();
}

/** 会话正文真实展示且页面可见时才标记已读，列表查询本身不修改状态。 */
async function markVisibleRead() {
  if (tab.value !== "conversations" || document.visibilityState !== "visible")
    return;
  const rows = history.value.filter((row) => !isMine(row) && !row.readAt);
  const results = await Promise.allSettled(
    rows.map(async (row) => {
      await api.readMessage(row.id);
      row.readAt = new Date().toISOString();
    }),
  );
  if (results.some((result) => result.status === "fulfilled")) {
    refreshUnread();
    loadConversations();
  }
}

async function loadMessages(reset = false) {
  if (!["INBOX", "OUTBOX"].includes(tab.value)) return;
  if (reset) mq.page = 1;
  const request = ++messageRequest;
  busy.messages = true;
  try {
    const result = await api.messages({
      ...mq,
      box: tab.value,
      ...queryDates(messageDates.value),
    });
    if (!mounted || request !== messageRequest) return;
    messages.value = result.rows || [];
    messageTotal.value = result.total || 0;
    // 全站未读数由连接管理统一同步，避免列表查询的迟到响应覆盖已读后的新数量。
    refreshUnread();
  } catch {
    /* 错误保持原有结果。 */
  } finally {
    if (request === messageRequest) busy.messages = false;
  }
}

async function viewMessage(row) {
  detail.value = row;
  await nextTick();
  if (!isMine(row) && !row.readAt) {
    try {
      await api.readMessage(row.id);
      row.readAt = new Date().toISOString();
      refreshUnread();
      loadConversations();
    } catch {
      /* 标记失败时不伪造已读。 */
    }
  }
}

function targetId(value) {
  const text = String(value).trim();
  if (!/^[1-9]\d*$/.test(text) || !Number.isSafeInteger(Number(text)))
    return null;
  return Number(text);
}

async function createConversation() {
  const id = targetId(newForm.target);
  if (!id || String(id) === myId.value)
    return ElMessage.warning("请输入有效的对方成员编号，不能选择自己");
  busy.create = true;
  try {
    const result = await api.createConversation({
      targetMemberId: id,
      subject: newForm.subject || null,
    });
    newDialog.value = false;
    await loadConversations(true);
    const row = conversations.value.find((c) => c.id === result.id) || {
      ...result,
      targetMemberId: id,
      targetName: `成员 #${id}`,
    };
    tab.value = "conversations";
    await openConversation(row);
    ElMessage.success("会话已打开");
  } catch {
    /* 服务端验证身份、账户及目标成员。 */
  } finally {
    busy.create = false;
  }
}

function sendSnapshot(body, prior) {
  const signature = JSON.stringify(body);
  return prior?.signature === signature
    ? prior
    : { signature, body: { ...body, clientMessageId: crypto.randomUUID() } };
}

async function send() {
  if (!access.has("message:send:self"))
    return ElMessage.warning("没有发送消息权限");
  if (busy.send) return;
  if (!draft.value.trim()) return ElMessage.warning("请输入消息正文");
  if (!selected.value || selected.value.status !== "OPEN")
    return ElMessage.warning("请打开一个未关闭的会话");
  pendingSend = sendSnapshot(
    { conversationId: selected.value.id, body: draft.value },
    pendingSend,
  );
  busy.send = true;
  try {
    await api.sendMessage(pendingSend.body);
    draft.value = "";
    pendingSend = undefined;
    retrySend.value = false;
    historyPage.value = 1;
    await Promise.all([loadHistory(), loadConversations()]);
  } catch {
    retrySend.value = true;
  } finally {
    busy.send = false;
  }
}

async function broadcast() {
  if (busy.broadcast) return;
  const recipients = broadcastForm.recipients.map(targetId);
  if (
    !recipients.length ||
    recipients.some((id) => !id || String(id) === myId.value)
  )
    return ElMessage.warning("请选择或输入有效的收件编号，不能包含自己");
  if (new Set(recipients).size > 50)
    return ElMessage.warning("一次广播最多选择50位收件成员");
  if (!broadcastForm.body.trim()) return ElMessage.warning("请输入广播正文");
  pendingBroadcast = sendSnapshot(
    {
      recipientIds: [...new Set(recipients)].sort((a, b) => a - b),
      subject: broadcastForm.subject || null,
      body: broadcastForm.body,
    },
    pendingBroadcast,
  );
  busy.broadcast = true;
  try {
    await api.sendMessage(pendingBroadcast.body);
    broadcastDialog.value = false;
    broadcastForm.recipients = [];
    broadcastForm.body = "";
    broadcastForm.subject = "";
    pendingBroadcast = undefined;
    retryBroadcast.value = false;
    tab.value = "OUTBOX";
    await nextTick();
    loadMessages(true);
    ElMessage.success("广播已保存并提交分发");
  } catch {
    retryBroadcast.value = true;
  } finally {
    busy.broadcast = false;
  }
}

async function updateConversation(body) {
  if (!selected.value) return;
  try {
    const updated = await api.updateConversation(selected.value.id, {
      ...body,
      expectedVersion: selected.value.version,
    });
    selected.value = { ...selected.value, ...updated };
    renameDialog.value = false;
    await loadConversations();
  } catch {
    await loadConversations();
  }
}

async function toggleConversation() {
  const status = selected.value.status === "OPEN" ? "CLOSED" : "OPEN";
  try {
    await ElMessageBox.confirm(
      status === "CLOSED"
        ? "关闭后双方均不能发送消息，确定关闭？"
        : "确定重新打开会话？",
      "会话状态",
    );
    await updateConversation({ status });
  } catch {
    /* 用户取消确认。 */
  }
}

function synchronize() {
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => {
    if (!mounted) return;
    loadConversations();
    if (tab.value === "conversations") loadHistory();
    else loadMessages();
  }, 300);
}

/** Element Plus页码输入只监听change，回车时立即提交，触发统一分页查询。 */
function commitPageJump(event) {
  if (event.target.closest(".el-pagination__jump")) {
    event.target.dispatchEvent(new Event("change", { bubbles: true }));
  }
}

watch(
  () => [communication.eventVersion, communication.syncVersion],
  synchronize,
);
watch(tab, () => {
  messageRequest++;
  mq.page = 1;
  if (tab.value !== "INBOX") mq.read = "";
  if (tab.value !== "OUTBOX") mq.recipientMemberId = null;
  if (tab.value === "conversations") loadHistory();
  else loadMessages();
});
const visible = () => {
  if (document.visibilityState === "visible") markVisibleRead();
};
onMounted(async () => {
  // 人工交接链接只提供定位ID，仍通过原聚合查询验证本人会话范围。
  if (/^[1-9][0-9]*$/.test(String(route.query.conversationId || "")))
    cq.id = Number(route.query.conversationId);
  await loadConversations();
  if (cq.id && conversations.value.length)
    await openConversation(conversations.value[0]);
  document.addEventListener("visibilitychange", visible);
});
onUnmounted(() => {
  mounted = false;
  clearTimeout(refreshTimer);
  document.removeEventListener("visibilitychange", visible);
});
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="LIBRARY COMMUNICATION"
      :title="route.meta.admin ? '客服会话' : '消息中心'"
      description="与馆员保持联系，查看咨询回复与图书馆通知。"
    />
    <section class="panel communication-panel">
      <div class="communication-toolbar">
        <span
          ><i
            :class="[
              'connection-indicator',
              { online: communication.status === 'connected' },
            ]"
          />{{ statusLabel }}
          <span class="muted">未读 {{ communication.unreadTotal }}</span></span
        >
        <div>
          <el-button
            @click="
              resumeCommunication(true);
              synchronize();
              refreshUnread();
            "
            >刷新</el-button
          ><el-button
            v-if="access.has('conversation:create:self')"
            @click="newDialog = true"
            >新建会话</el-button
          ><el-button
            v-if="staff"
            type="primary"
            @click="broadcastDialog = true"
            >发送广播</el-button
          >
        </div>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="客服会话" name="conversations">
          <div class="filters">
            <el-input
              v-model="cq.subject"
              placeholder="会话主题"
              clearable
              @keyup.enter="loadConversations(true)"
            />
            <el-input-number
              v-model="cq.id"
              :min="1"
              placeholder="会话编号"
              :controls="false"
            />
            <el-input-number
              v-model="cq.targetMemberId"
              :min="1"
              placeholder="对方编号"
              :controls="false"
            />
            <el-select v-model="cq.status" clearable placeholder="会话状态"
              ><el-option label="开放" value="OPEN" /><el-option
                label="关闭"
                value="CLOSED"
            /></el-select>
            <el-date-picker
              v-model="conversationDates"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              start-placeholder="创建起始时间"
              end-placeholder="结束时间"
            />
            <el-checkbox v-model="cq.unreadOnly">仅未读</el-checkbox
            ><el-button type="primary" @click="loadConversations(true)"
              >查询</el-button
            >
          </div>
          <div class="conversation-layout">
            <div class="conversation-list" v-loading="busy.conversations">
              <el-empty
                v-if="!conversations.length"
                description="暂无会话，可搜索馆员姓名新建咨询"
              />
              <button
                v-for="row in conversations"
                :key="row.id"
                type="button"
                :disabled="busy.send"
                :class="[
                  'conversation-card',
                  { selected: selected?.id === row.id },
                ]"
                @click="openConversation(row)"
              >
                <div>
                  <strong
                    >{{ row.targetName || "成员" }}
                    <small>#{{ row.targetMemberId }}</small></strong
                  ><el-badge
                    :value="row.unreadCount"
                    :hidden="!row.unreadCount"
                  />
                </div>
                <p>
                  {{ row.subject || "咨询会话" }}
                  <span class="muted"
                    >· {{ row.status === "OPEN" ? "开放" : "关闭" }}</span
                  >
                </p>
                <p class="message-preview">
                  {{ row.lastMessagePreview || "尚未发送消息" }}
                </p>
                <small class="muted"
                  >{{ timestamp(row.lastMessageAt || row.createdAt) }} · 会话
                  #{{ row.id }}</small
                >
              </button>
              <div class="pager">
                <PageSummary
                  :page="cq.page"
                  :page-size="cq.pageSize"
                  :total="conversationTotal"
                /><el-pagination
                  v-model:current-page="cq.page"
                  :page-size="cq.pageSize"
                  :total="conversationTotal"
                  layout="prev, pager, next"
                  @current-change="loadConversations()"
                />
              </div>
            </div>
            <div class="chat-panel">
              <el-empty v-if="!selected" description="选择会话查看消息" />
              <template v-else>
                <div class="chat-header">
                  <div>
                    <strong>{{
                      selected.targetName || `成员 #${selected.targetMemberId}`
                    }}</strong>
                    <p>{{ selected.subject || "咨询会话" }}</p>
                  </div>
                  <div>
                    <el-button
                      v-if="access.has('conversation:update:self')"
                      size="small"
                      @click="
                        rename = selected.subject || '';
                        renameDialog = true;
                      "
                      >修改主题</el-button
                    ><el-button
                      v-if="access.has('conversation:update:self')"
                      size="small"
                      @click="toggleConversation"
                      >{{
                        selected.status === "OPEN" ? "关闭会话" : "重新打开"
                      }}</el-button
                    >
                  </div>
                </div>
                <div
                  ref="chatBody"
                  class="chat-history"
                  v-loading="busy.history"
                >
                  <el-empty v-if="!history.length" description="开始一段咨询" />
                  <article
                    v-for="row in history"
                    :key="row.id"
                    :class="['chat-message', { mine: isMine(row) }]"
                  >
                    <small
                      >{{
                        isMine(row)
                          ? "我"
                          : row.senderName || `成员 #${row.senderMemberId}`
                      }}
                      · {{ timestamp(row.createdAt) }} · #{{
                        row.sequence
                      }}</small
                    >
                    <div class="message-bubble">
                      <strong v-if="row.subject">{{ row.subject }}</strong>
                      <p>{{ row.body }}</p>
                    </div>
                  </article>
                </div>
                <div class="pager">
                  <PageSummary
                    :page="historyPage"
                    :page-size="historySize"
                    :total="historyTotal"
                  /><el-pagination
                    v-model:current-page="historyPage"
                    :page-size="historySize"
                    :total="historyTotal"
                    layout="prev, pager, next"
                    @current-change="loadHistory"
                  />
                </div>
                <div class="chat-compose">
                  <el-input
                    v-model="draft"
                    type="textarea"
                    :rows="3"
                    maxlength="4000"
                    show-word-limit
                    :disabled="
                      busy.send ||
                      selected.status !== 'OPEN' ||
                      !access.has('message:send:self')
                    "
                    :placeholder="
                      selected.status === 'OPEN'
                        ? '输入消息，Ctrl + Enter 发送'
                        : '会话已关闭'
                    "
                    @keydown.ctrl.enter.prevent="send"
                  />
                  <div>
                    <small class="muted"
                      >发送失败可重试，当前消息不会重复保存。</small
                    ><el-button
                      type="primary"
                      :loading="busy.send"
                      :disabled="
                        selected.status !== 'OPEN' ||
                        !access.has('message:send:self')
                      "
                      @click="send"
                      >{{ retrySend ? "重试发送" : "发送消息" }}</el-button
                    >
                  </div>
                </div>
              </template>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="收件箱" name="INBOX" />
        <el-tab-pane label="发件箱" name="OUTBOX" />
      </el-tabs>
      <template v-if="tab !== 'conversations'">
        <div class="filters">
          <el-input
            v-model="mq.subject"
            clearable
            placeholder="消息标题"
            @keyup.enter="loadMessages(true)"
          />
          <el-input-number
            v-model="mq.id"
            :min="1"
            :controls="false"
            placeholder="消息编号"
          />
          <el-input-number
            v-model="mq.conversationId"
            :min="1"
            :controls="false"
            placeholder="会话编号"
          />
          <el-input-number
            v-model="mq.senderMemberId"
            :min="1"
            :controls="false"
            placeholder="发送人编号"
          />
          <el-input-number
            v-if="tab === 'OUTBOX'"
            v-model="mq.recipientMemberId"
            :min="1"
            :controls="false"
            placeholder="收件人编号"
          />
          <el-select v-model="mq.sendType" clearable placeholder="消息类型"
            ><el-option label="会话消息" value="DIRECT" /><el-option
              label="广播"
              value="MULTICAST"
          /></el-select>
          <el-select
            v-if="tab === 'INBOX'"
            v-model="mq.read"
            clearable
            placeholder="阅读状态"
            ><el-option label="未读" :value="false" /><el-option
              label="已读"
              :value="true"
          /></el-select>
          <el-select
            v-model="mq.messageType"
            clearable
            placeholder="聊天或通知"
          >
            <el-option label="聊天" value="CHAT" /><el-option
              label="业务通知"
              value="SYSTEM"
            />
          </el-select>
          <el-select v-model="mq.businessType" clearable placeholder="业务类型">
            <el-option label="借阅申请" value="BORROW_APPLICATION" /><el-option
              label="借阅记录"
              value="BORROW_RECORD"
            />
            <el-option label="资料与资格申请" value="MEMBER_CHANGE" />
          </el-select>
          <el-input-number
            v-model="mq.businessId"
            :controls="false"
            :min="1"
            placeholder="业务编号"
          />
          <el-input-number
            v-model="mq.afterSequence"
            :min="0"
            :controls="false"
            placeholder="大于序号（需会话ID）"
          />
          <el-date-picker
            v-model="messageDates"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="发送起始时间"
            end-placeholder="结束时间"
          />
          <el-button type="primary" @click="loadMessages(true)">查询</el-button>
        </div>
        <el-table
          :data="messages"
          v-loading="busy.messages"
          @row-click="viewMessage"
        >
          <el-table-column prop="id" label="编号" width="85" /><el-table-column
            label="类型"
            width="100"
            ><template #default="{ row }">{{
              row.messageType === "SYSTEM"
                ? "业务通知"
                : row.sendType === "MULTICAST"
                  ? "广播"
                  : "会话"
            }}</template></el-table-column
          >
          <el-table-column label="标题 / 正文" min-width="250"
            ><template #default="{ row }"
              ><strong>{{ row.subject || "无标题" }}</strong>
              <p class="message-preview">{{ row.body }}</p></template
            ></el-table-column
          >
          <el-table-column label="发送人" min-width="130"
            ><template #default="{ row }"
              >{{ row.senderName
              }}<span v-if="row.senderMemberId">
                #{{ row.senderMemberId }}</span
              ></template
            ></el-table-column
          >
          <el-table-column
            v-if="tab === 'OUTBOX'"
            label="收件人"
            min-width="160"
            ><template #default="{ row }">{{
              recipientLabel(row)
            }}</template></el-table-column
          >
          <el-table-column v-else label="状态" width="90"
            ><template #default="{ row }"
              ><el-tag :type="row.readAt ? 'info' : 'success'">{{
                row.readAt ? "已读" : "未读"
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="发送时间" min-width="170"
            ><template #default="{ row }">{{
              timestamp(row.createdAt)
            }}</template></el-table-column
          >
          <el-table-column label="操作" width="100"
            ><template #default="{ row }"
              ><el-button link type="primary" @click.stop="viewMessage(row)"
                >查看正文</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <div class="pager">
          <PageSummary
            :page="mq.page"
            :page-size="mq.pageSize"
            :total="messageTotal"
          /><el-pagination
            v-model:current-page="mq.page"
            v-model:page-size="mq.pageSize"
            :total="messageTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @keyup.enter="commitPageJump"
            @current-change="loadMessages()"
            @size-change="loadMessages(true)"
          />
        </div>
      </template>
    </section>

    <el-dialog
      v-model="newDialog"
      title="新建咨询会话"
      width="min(520px, 95vw)"
    >
      <el-form label-position="top"
        ><el-form-item :label="staff ? '选择联系人' : '选择馆员'"
          ><el-select
            v-model="newForm.target"
            filterable
            remote
            :remote-method="searchContacts"
            :loading="contactLoading"
            default-first-option
            placeholder="输入姓名查询可联系成员"
            style="width: 100%"
            ><el-option
              v-for="contact in contacts"
              :key="contact.id"
              :label="contact.label"
              :value="contact.id"
            />
            <template #footer>
              <el-pagination
                v-model:current-page="contactQuery.page"
                :page-size="contactQuery.pageSize"
                :total="contactTotal"
                layout="prev, pager, next"
                @current-change="loadContacts()"
              />
            </template> </el-select
        ></el-form-item>
        <p class="muted">
          读者仅能选择启用的人类馆员，列表由后端授权范围决定。
        </p>
        <el-form-item label="会话主题"
          ><el-input
            v-model="newForm.subject"
            maxlength="200"
            show-word-limit /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="newDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="busy.create"
          @click="createConversation"
          >打开会话</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="broadcastDialog"
      title="发送成员广播"
      width="min(620px, 95vw)"
    >
      <el-form label-position="top"
        ><el-form-item label="收件成员（最多50人）"
          ><el-select
            v-model="broadcastForm.recipients"
            multiple
            filterable
            remote
            :remote-method="searchContacts"
            :loading="contactLoading"
            default-first-option
            :multiple-limit="50"
            placeholder="输入姓名搜索，选择广播收件成员"
            style="width: 100%"
            ><el-option
              v-for="contact in contacts"
              :key="contact.id"
              :label="contact.label"
              :value="contact.id" /><template #footer>
              <el-pagination
                v-model:current-page="contactQuery.page"
                :page-size="contactQuery.pageSize"
                :total="contactTotal"
                layout="prev, pager, next"
                @current-change="loadContacts()"
              /> </template></el-select></el-form-item
        ><el-form-item label="标题"
          ><el-input
            v-model="broadcastForm.subject"
            maxlength="200"
            show-word-limit /></el-form-item
        ><el-form-item label="正文"
          ><el-input
            v-model="broadcastForm.body"
            type="textarea"
            :rows="6"
            maxlength="4000"
            show-word-limit /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="broadcastDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="busy.broadcast"
          @click="broadcast"
          >{{ retryBroadcast ? "重试发送" : "发送广播" }}</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="renameDialog"
      title="修改会话主题"
      width="min(500px, 95vw)"
      ><el-input v-model="rename" maxlength="200" show-word-limit /><template
        #footer
        ><el-button @click="renameDialog = false">取消</el-button
        ><el-button
          type="primary"
          @click="updateConversation({ subject: rename })"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      :model-value="!!detail"
      title="消息详情"
      width="min(650px, 95vw)"
      @close="detail = null"
      ><template v-if="detail"
        ><h3>{{ detail.subject || "消息" }}</h3>
        <p class="muted">
          {{ detail.senderName
          }}<span v-if="detail.senderMemberId">
            #{{ detail.senderMemberId }}</span
          >
          ·
          {{ timestamp(detail.createdAt) }}
        </p>
        <p v-if="isMine(detail)" class="muted">
          收件人：{{ recipientLabel(detail) }}
        </p>
        <div class="message-detail">{{ detail.body }}</div>
        <el-button
          v-if="detail.messageType === 'SYSTEM' && detail.businessId"
          type="primary"
          @click="openBusiness(detail)"
          >查看业务记录</el-button
        >
        <p class="muted">
          消息 #{{ detail.id }}
          <span v-if="detail.conversationId"
            >· 会话 #{{ detail.conversationId }} · 序号
            {{ detail.sequence }}</span
          >
        </p></template
      ></el-dialog
    >
  </AppShell>
</template>

<style scoped>
.communication-toolbar,
.chat-header,
.chat-compose > div,
.conversation-card > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.communication-toolbar {
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.connection-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 50%;
  background: #aaa;
}
.connection-indicator.online {
  background: #438266;
}
.muted {
  color: var(--muted);
  font-size: 12px;
}
.communication-toolbar .muted {
  margin-left: 16px;
}
.filters {
  flex-wrap: wrap;
}
.filters :deep(.el-input-number) {
  width: 145px;
}
.filters :deep(.el-date-editor) {
  max-width: 390px;
}
.conversation-layout {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 2fr);
  gap: 24px;
}
.conversation-list {
  min-width: 0;
}
.conversation-card {
  width: 100%;
  text-align: left;
  padding: 18px;
  border: 1px solid var(--line);
  background: white;
  border-radius: 10px;
  margin-bottom: 12px;
  cursor: pointer;
  color: var(--deep);
}
.conversation-card.selected {
  background: #eef5f0;
  border-color: var(--green);
}
.conversation-card p {
  margin: 10px 0;
  font-size: 13px;
}
.message-preview {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  margin: 6px 0;
  color: var(--muted);
}
.chat-panel {
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  min-width: 0;
}
.chat-header {
  padding: 16px 20px;
  background: #eef4ef;
  flex-wrap: wrap;
}
.chat-header p {
  margin: 6px 0 0;
  font-size: 13px;
}
.chat-history {
  height: 420px;
  overflow-y: auto;
  padding: 20px;
  background: #fafcf9;
}
.chat-message {
  margin-bottom: 18px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.chat-message.mine {
  align-items: flex-end;
}
.chat-message small {
  color: var(--muted);
  margin-bottom: 6px;
  font-size: 11px;
}
.message-bubble {
  max-width: 85%;
  padding: 12px 16px;
  background: white;
  border: 1px solid var(--line);
  border-radius: 4px 12px 12px;
  overflow-wrap: anywhere;
}
.mine .message-bubble {
  background: #e2eee5;
  border-radius: 12px 4px 12px 12px;
}
.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}
.message-detail {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
  padding: 20px;
  background: #f3f6f2;
  border-radius: 8px;
}
.chat-compose {
  border-top: 1px solid var(--line);
  padding: 16px;
}
.chat-compose > div {
  margin-top: 12px;
}
.chat-panel .pager {
  padding: 0 16px;
  flex-wrap: wrap;
}
@media (max-width: 900px) {
  .conversation-layout {
    grid-template-columns: 1fr;
  }
  .conversation-list {
    max-height: 350px;
    overflow: auto;
  }
  .chat-history {
    height: 350px;
  }
  .communication-panel {
    padding: 16px;
  }
}
</style>
