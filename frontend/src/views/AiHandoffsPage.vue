<script setup>
import { displayValue } from "../modules/display";
const formatState = (_row, _column, value) => displayValue(value);
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { handoffApi as api } from "../api/aiHandoff";
import { session } from "../stores/session";
import { access } from "../stores/permissions";
const router = useRouter(),
  rows = ref([]),
  total = ref(0);
const query = reactive({ page: 1, pageSize: 10, status: "" });
async function load() {
  const data = await api.query(
    Object.fromEntries(Object.entries(query).filter(([, v]) => v !== "")),
  );
  rows.value = data.rows;
  total.value = data.total;
}
async function decide(row, decision) {
  await api.decide(row.id, { decision, expectedVersion: row.version });
  await load();
}
onMounted(load);
</script>
<template>
  <AppShell
    ><PageHeader
      eyebrow="人工服务"
      title="AI转人工交接"
      description="等待指定馆员接管。接管后原AI会话停答，继续使用现有消息中心沟通。" /><el-form
      inline
      ><el-form-item label="交接编号"
        ><el-input-number
          v-model="query.id"
          :min="1"
          :controls="false" /></el-form-item
      ><el-form-item label="申请人编号"
        ><el-input-number
          v-model="query.memberId"
          :min="1"
          :controls="false" /></el-form-item
      ><el-form-item label="馆员编号"
        ><el-input-number
          v-model="query.staffMemberId"
          :min="1"
          :controls="false" /></el-form-item
      ><el-form-item label="状态"
        ><el-select v-model="query.status" clearable style="width: 180px"
          ><el-option
            v-for="s in ['WAITING', 'ACCEPTED', 'CLOSED']"
            :key="s"
            :value="s"
            :label="displayValue(s)" /></el-select></el-form-item
      ><el-button
        @click="
          query.page = 1;
          load();
        "
        >查询</el-button
      ></el-form
    ><el-table :data="rows"
      ><el-table-column prop="id" label="编号" /><el-table-column
        prop="memberId"
        label="申请人"
      /><el-table-column
        prop="staffMemberId"
        label="指定馆员"
      /><el-table-column
        prop="status"
        :formatter="formatState"
        label="状态"
      /><el-table-column
        prop="summary"
        label="用户确认的摘要"
        show-overflow-tooltip
      /><el-table-column label="操作" width="250"
        ><template #default="{ row }"
          ><el-button
            v-if="
              row.status === 'WAITING' &&
              String(row.staffMemberId) === String(session.claims.memberId) &&
              access.has('ai-handoff:accept', 'SELF')
            "
            link
            @click="decide(row, 'ACCEPT')"
            >接管</el-button
          ><el-button
            v-if="
              [row.memberId, row.staffMemberId]
                .map(String)
                .includes(String(session.claims.memberId))
            "
            link
            @click="
              router.push('/messages?conversationId=' + row.conversationId)
            "
            >进入私聊</el-button
          ><el-button
            v-if="
              row.status !== 'CLOSED' &&
              [row.memberId, row.staffMemberId]
                .map(String)
                .includes(String(session.claims.memberId))
            "
            link
            @click="decide(row, 'CLOSE')"
            >结束交接</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="query.page"
      :page-size="query.pageSize"
      :total="total"
      layout="total,prev,pager,next,jumper"
      @current-change="load"
  /></AppShell>
</template>
