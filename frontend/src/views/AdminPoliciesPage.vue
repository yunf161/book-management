<script setup>
import { onMounted, reactive, ref } from "vue";
import AppShell from "../components/AppShell.vue";
import http from "../api/http";
import { access } from "../stores/permissions";
const rows = ref([]),
  total = ref(0),
  opened = ref(false),
  current = ref(null);
const query = reactive({
  page: 1,
  pageSize: 10,
  id: null,
  type: "",
  status: "",
  title: "",
  policyVersion: null,
});
const form = reactive({});
const borrowing = () => ({
  defaultBorrowDays: 30,
  maxBorrowDays: 60,
  maxRenewCount: 3,
  defaultRenewDays: 30,
  maxRenewDays: 60,
  blockBorrowWhenOverdue: true,
  applicationValidityDays: 7,
});
async function load() {
  const params = Object.fromEntries(
    Object.entries(query).filter(([, v]) => v !== "" && v != null),
  );
  const result = await http.get("/admin/policies", { params });
  rows.value = result.rows;
  total.value = result.total;
}
function edit(row) {
  current.value = row || null;
  Object.keys(form).forEach((k) => delete form[k]);
  Object.assign(
    form,
    row
      ? {
          type: row.type,
          title: row.title,
          content: row.content,
          expectedVersion: row.version,
          [row.type === "ADMISSION"
            ? "admissionParameters"
            : "borrowingParameters"]: JSON.parse(
            JSON.stringify(row.parameters),
          ),
        }
      : {
          type: "BORROWING",
          title: "",
          content: "",
          borrowingParameters: borrowing(),
        },
  );
  opened.value = true;
}
function changeType() {
  delete form.admissionParameters;
  delete form.borrowingParameters;
  if (form.type === "ADMISSION")
    form.admissionParameters = {
      requireVerifiedEmail: true,
      requiredFields: ["NAME"],
      defaultMaxBorrowCount: 5,
    };
  else form.borrowingParameters = borrowing();
}
async function save() {
  if (current.value)
    await http.put("/admin/policies/" + current.value.id, form);
  else await http.post("/admin/policies", form);
  opened.value = false;
  await load();
}
async function publish(row) {
  const active = await http.get("/policies/current", {
    params: { type: row.type },
  });
  await http.post("/admin/policies/" + row.id + "/publish", {
    expectedVersion: row.version,
    expectedCurrentPolicyId: active.id,
  });
  await load();
}
const labels = {
  defaultBorrowDays: "默认借阅天数",
  maxBorrowDays: "最长借阅天数",
  maxRenewCount: "最多续借次数",
  defaultRenewDays: "默认续借天数",
  maxRenewDays: "最长续借天数",
  applicationValidityDays: "申请有效天数",
};
onMounted(load);
</script>
<template>
  <AppShell>
    <h1>政策版本管理</h1>
    <p>发布后保留历史版本。新借阅须知发布后，人类成员需要重新明确确认。</p>
    <el-form inline>
      <el-form-item label="ID"
        ><el-input-number v-model="query.id" :min="1"
      /></el-form-item>
      <el-form-item label="标题"
        ><el-input v-model="query.title"
      /></el-form-item>
      <el-form-item label="类型"
        ><el-select v-model="query.type" clearable style="width: 150px"
          ><el-option label="准入" value="ADMISSION" /><el-option
            label="借阅"
            value="BORROWING" /></el-select
      ></el-form-item>
      <el-form-item label="状态"
        ><el-select v-model="query.status" clearable style="width: 150px"
          ><el-option
            v-for="s in ['DRAFT', 'ACTIVE', 'RETIRED']"
            :key="s"
            :label="s"
            :value="s" /></el-select
      ></el-form-item>
      <el-form-item label="发布版本"
        ><el-input-number v-model="query.policyVersion" :min="1"
      /></el-form-item>
      <el-button
        @click="
          query.page = 1;
          load();
        "
        >查询</el-button
      >
      <el-button
        v-if="access.has('policy:create', 'ALL')"
        type="primary"
        @click="edit()"
        >新建草稿</el-button
      >
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="id" label="ID" /><el-table-column
        prop="type"
        label="类型"
      />
      <el-table-column prop="title" label="标题" /><el-table-column
        prop="status"
        label="状态"
      />
      <el-table-column prop="policyVersion" label="发布版本" /><el-table-column
        prop="version"
        label="资料版本"
      />
      <el-table-column prop="publishedAt" label="发布时间" />
      <el-table-column label="操作"
        ><template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT' && access.has('policy:update', 'ALL')"
            @click="edit(row)"
            >编辑</el-button
          >
          <el-popconfirm
            v-if="row.status === 'DRAFT' && access.has('policy:publish', 'ALL')"
            title="确认发布此政策？"
            @confirm="publish(row)"
          >
            <template #reference
              ><el-button type="primary">发布</el-button></template
            >
          </el-popconfirm>
          <el-popover trigger="click" width="450"
            ><div style="white-space: pre-wrap">{{ row.content }}</div>
            <pre>{{ row.parameters }}</pre>
            <template #reference
              ><el-button>查看正文与参数</el-button></template
            ></el-popover
          >
        </template></el-table-column
      >
    </el-table>
    <p>
      第 {{ query.page }} 页 / 共
      {{ Math.max(1, Math.ceil(total / query.pageSize)) }} 页，共 {{ total }} 条
    </p>
    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="sizes,prev,pager,next,jumper"
      @current-change="load"
      @size-change="
        query.page = 1;
        load();
      "
    />
    <el-dialog v-model="opened" title="政策草稿" width="min(700px, 94vw)">
      <el-form label-width="130px">
        <el-form-item label="类型"
          ><el-select
            v-model="form.type"
            :disabled="!!current"
            @change="changeType"
            ><el-option value="ADMISSION" label="准入" /><el-option
              value="BORROWING"
              label="借阅" /></el-select
        ></el-form-item>
        <el-form-item label="标题"
          ><el-input v-model="form.title" maxlength="200"
        /></el-form-item>
        <el-form-item label="正文"
          ><el-input v-model="form.content" type="textarea" :rows="8"
        /></el-form-item>
        <template v-if="form.borrowingParameters">
          <el-form-item v-for="(label, key) in labels" :key="key" :label="label"
            ><el-input-number
              v-model="form.borrowingParameters[key]"
              :min="key === 'maxRenewCount' ? 0 : 1"
          /></el-form-item>
          <el-form-item label="逾期限制借阅"
            ><el-switch
              v-model="form.borrowingParameters.blockBorrowWhenOverdue"
          /></el-form-item>
        </template>
        <template v-if="form.admissionParameters">
          <el-form-item label="邮箱须验证"
            ><el-switch v-model="form.admissionParameters.requireVerifiedEmail"
          /></el-form-item>
          <el-form-item label="必填资料"
            ><el-checkbox-group
              v-model="form.admissionParameters.requiredFields"
              ><el-checkbox value="NAME">姓名</el-checkbox
              ><el-checkbox value="PHONE"
                >手机号</el-checkbox
              ></el-checkbox-group
            ></el-form-item
          >
          <el-form-item label="初始借阅额度"
            ><el-input-number
              v-model="form.admissionParameters.defaultMaxBorrowCount"
              :min="0"
          /></el-form-item>
        </template>
      </el-form>
      <template #footer
        ><el-button @click="opened = false">取消</el-button
        ><el-button type="primary" @click="save">保存草稿</el-button></template
      >
    </el-dialog>
  </AppShell>
</template>
