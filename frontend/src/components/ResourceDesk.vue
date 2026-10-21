<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import DtoFields from "./DtoFields.vue";
import PageSummary from "./PageSummary.vue";
import {
  displayValue,
  statusTone,
  progressPercentage,
} from "../modules/display";
import {
  isLockVersion,
  recordVersion,
  withoutLockVersions,
} from "../modules/recordVersions";
const props = defineProps({
  title: String,
  queryFields: Array,
  createFields: Array,
  updateFields: [Array, Function],
  list: Function,
  create: Function,
  update: Function,
  remove: Function,
  allowUpdate: Function,
  allowRemove: Function,
  defaults: Object,
  pageKey: { type: String, default: "page" },
  columns: Array,
  // 需要格式化展示的JSON快照字段；默认不改变其他模块的详情展示。
  jsonFields: { type: Array, default: () => [] },
  initialQuery: Object,
  removeLabel: { type: String, default: "删除" },
});
// 不同时携带 page 与 pageNum，确保查询参数严格匹配当前后端 DTO。
const query = reactive({ [props.pageKey]: 1, pageSize: 10 }),
  rows = ref([]),
  total = ref(0),
  busy = ref(false);
const loadError = ref(false);
const queryNotFound = ref(false);
const loadErrorMessage = ref("");
const dialog = ref(false),
  selected = ref(null),
  form = reactive({}),
  details = ref(null),
  saving = ref(false);
let generation = 0;
// 0 和 false 都是有效条件；固定的页面范围不算用户额外填写的筛选。
const filterCount = computed(
  () =>
    (props.queryFields || []).filter((field) => {
      const value = query[field.key];
      return (
        !isLockVersion(field.key) &&
        !field.disabled &&
        value !== undefined &&
        value !== null &&
        value !== "" &&
        (!Array.isArray(value) || value.length > 0) &&
        !Object.is(value, props.initialQuery?.[field.key])
      );
    }).length,
);

function resetFilters() {
  const pageSize = query.pageSize;
  const initial = { ...props.initialQuery };
  // 两种分页协议不能同时发送；清空用户条件时仍保留页面的固定查询范围。
  for (const key of ["page", "pageNum", "pageSize"]) delete initial[key];
  for (const key of Object.keys(query)) delete query[key];
  Object.assign(query, initial, { [props.pageKey]: 1, pageSize });
  return load();
}
// 人员管理等场景的可维护字段随记录状态和当前授权变化，其他模块仍接受普通数组。
const editFields = computed(() =>
  typeof props.updateFields === "function"
    ? props.updateFields(selected.value)
    : props.updateFields,
);
// 详情与编辑选择的是不同记录；动态字段必须按当前详情记录解析后再合并标签。
// 不能直接展开 updateFields：人员管理传入的是函数，会使整个详情弹窗渲染失败。
const detailFields = computed(() => [
  ...(props.queryFields || []),
  ...(props.createFields || []),
  ...((typeof props.updateFields === "function"
    ? props.updateFields(details.value)
    : props.updateFields) || []),
  ...(props.columns || []),
]);
async function load(reset = false) {
  if (reset) query[props.pageKey] = 1;
  const current = ++generation;
  busy.value = true;
  loadError.value = false;
  queryNotFound.value = false;
  loadErrorMessage.value = "";
  try {
    const result = await props.list({ ...query });
    if (current !== generation) return;
    rows.value = result?.rows || (result?.id ? [result] : []);
    total.value = result?.total ?? rows.value.length;
  } catch (error) {
    if (current === generation) {
      rows.value = [];
      total.value = 0;
      loadError.value = true;
      // 现有部分聚合查询以业务404表示无匹配；保留真实提示并允许重置条件。
      // 仅识别结构化业务404，不能将路由404、403或服务故障伪装成空列表。
      queryNotFound.value =
        error?.response?.status === 404 && error?.response?.data?.code === 404;
      loadErrorMessage.value = queryNotFound.value
        ? error.response.data.message ||
          error.response.data.msg ||
          "未找到符合条件的记录"
        : "查询失败，请重试";
    }
  } finally {
    if (current === generation) busy.value = false;
  }
}
function open(row) {
  selected.value = row || null;
  for (const key of Object.keys(form)) delete form[key];
  Object.assign(form, props.defaults || {});
  const fields = row ? editFields.value : props.createFields;
  for (const field of fields || []) {
    let value = row?.[field.key];
    if (field.key === "expectedVersion") value = row?.version;
    if (field.array && Array.isArray(value)) value = value.join(",");
    if (value !== undefined) form[field.key] = value;
  }
  dialog.value = true;
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    const payload = {};
    for (const f of (selected.value ? editFields.value : props.createFields) ||
      []) {
      // 技术版本不从表单读取，防止隐藏字段残留或被脚本篡改后覆盖新数据。
      if (isLockVersion(f.key)) {
        if (selected.value && ["version", "expectedVersion"].includes(f.key))
          payload[f.key] = recordVersion(selected.value);
        continue;
      }
      if (form[f.key] !== undefined)
        payload[f.key] = f.array
          ? String(form[f.key] || "")
              .split(/[,，]/)
              .map((s) => s.trim())
              .filter(Boolean)
          : form[f.key];
    }
    if (selected.value) await props.update(selected.value, payload);
    else await props.create(payload);
    dialog.value = false;
    ElMessage.success("保存成功");
    await load();
  } catch {
    /* HTTP层展示业务错误，保留表单供修正或刷新版本。 */
  } finally {
    saving.value = false;
  }
}
async function removeRow(row) {
  try {
    await ElMessageBox.confirm(
      `确认${props.removeLabel}这条记录？`,
      "操作确认",
    );
    await props.remove(row);
    ElMessage.success("操作成功");
    await load();
  } catch {
    /* 取消操作与后端拒绝都不伪造成功。 */
  }
}
function jump(event) {
  if (!event.target.closest(".el-pagination__jump")) return;
  const page = Number(event.target.value);
  const maxPage = Math.max(1, Math.ceil(total.value / query.pageSize));
  if (!Number.isInteger(page) || page < 1 || page > maxPage) return;
  query[props.pageKey] = page;
  load();
}
const valueText = displayValue;
// 通用元数据不一定出现在业务 DTO 中，详情仍使用面向用户的字段名称。
const detailLabels = { createdAt: "创建时间", updatedAt: "更新时间" };
function prettyJson(value) {
  try {
    return JSON.stringify(
      withoutLockVersions(
        typeof value === "string" ? JSON.parse(value) : value,
      ),
      null,
      2,
    );
  } catch {
    return displayValue(value);
  }
}
watch(
  () => props.list,
  () => load(true),
);
watch(
  () => props.initialQuery,
  (value) => {
    Object.assign(query, value || {});
    load(true);
  },
  { deep: true },
);
onMounted(() => {
  Object.assign(query, props.initialQuery || {});
  load();
});
defineExpose({ load, open, rows });
</script>
<template>
  <section class="panel resource-section" v-loading="busy" :aria-busy="busy">
    <div class="desk-head">
      <h2>{{ title }}</h2>
      <div>
        <slot name="toolbar" :reload="load" />
        <el-button :loading="busy" @click="load()">刷新</el-button
        ><el-button v-if="create" type="primary" @click="open()"
          >新增</el-button
        >
      </div>
    </div>
    <el-collapse v-if="queryFields?.length" class="resource-query"
      ><el-collapse-item name="query">
        <template #title
          ><span>查询条件（支持组合筛选）</span
          ><span v-if="filterCount" class="filter-count"
            >已填写 {{ filterCount }} 项</span
          ></template
        >
        <el-form label-position="top" @submit.prevent="load(true)">
          <DtoFields :model="query" :fields="queryFields" />
          <div class="filter-actions">
            <el-button type="primary" native-type="submit" :loading="busy"
              >查询</el-button
            >
            <el-button :disabled="!filterCount" @click="resetFilters"
              >重置条件</el-button
            >
            <small>可组合多个条件，按回车查询</small>
          </div>
        </el-form>
      </el-collapse-item></el-collapse
    >
    <el-table :data="rows" style="width: 100%">
      <template #empty>
        <el-empty
          :image-size="90"
          :description="
            loadError
              ? loadErrorMessage
              : filterCount
                ? '没有找到符合条件的记录'
                : '暂无记录'
          "
        >
          <el-button v-if="loadError && !queryNotFound" @click="load()"
            >重新查询</el-button
          >
          <el-button v-else-if="filterCount" @click="resetFilters"
            >重置条件后查询</el-button
          >
        </el-empty>
      </template>
      <el-table-column
        v-for="c in (columns || []).filter(
          (column) => !isLockVersion(column.key),
        )"
        :key="c.key"
        :prop="c.key"
        :label="c.label"
        :formatter="(_row, _column, value) => displayValue(value)"
        :show-overflow-tooltip="c.overflow"
        min-width="120"
      >
        <template #default="{ row }">
          <el-tag
            v-if="statusTone(c.key, row[c.key])"
            :type="statusTone(c.key, row[c.key])"
            effect="light"
            class="record-status"
            >{{ displayValue(row[c.key]) }}</el-tag
          >
          <el-progress
            v-else-if="progressPercentage(c.key, row[c.key]) !== null"
            :percentage="progressPercentage(c.key, row[c.key])"
            :stroke-width="7"
            class="record-progress"
          />
          <template v-else>{{ displayValue(row[c.key]) }}</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="260"
        ><template #default="{ row }">
          <el-button link @click="details = row">详情</el-button>
          <el-button
            v-if="update && (!allowUpdate || allowUpdate(row))"
            link
            type="primary"
            @click="open(row)"
            >编辑</el-button
          >
          <el-button
            v-if="remove && (!allowRemove || allowRemove(row))"
            link
            type="danger"
            @click="removeRow(row)"
            >{{ removeLabel }}</el-button
          >
          <slot name="actions" :row="row" :reload="load" /> </template
      ></el-table-column>
    </el-table>
    <p class="table-scroll-hint">表格可左右滑动，查看完整字段与操作</p>
    <div class="pager">
      <PageSummary
        :page="query[pageKey]"
        :page-size="query.pageSize"
        :total="total"
      />
      <el-pagination
        v-model:current-page="query[pageKey]"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @keyup.enter="jump"
        @current-change="load()"
        @size-change="load(true)"
      />
    </div>
    <el-dialog
      v-model="dialog"
      class="workspace-dialog"
      :title="`${selected ? '编辑' : '新增'}${title}`"
      width="min(900px,95vw)"
    >
      <el-form label-position="top"
        ><DtoFields
          :model="form"
          :fields="selected ? editFields : createFields"
      /></el-form>
      <p v-if="selected" class="muted">
        保存时自动检查资料是否被其他人修改；发生冲突请刷新记录后重试。
      </p>
      <template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      :model-value="!!details"
      class="workspace-dialog"
      title="记录详情"
      width="min(850px,95vw)"
      @close="details = null"
    >
      <p v-if="details" class="record-context">
        {{ title
        }}<span v-if="details.id != null"> · 编号 {{ details.id }}</span>
      </p>
      <el-descriptions
        class="record-details"
        :column="1"
        border
        v-if="details"
        :class="{ 'json-record-details': jsonFields.length }"
      >
        <template
          v-for="(value, key) in withoutLockVersions(details)"
          :key="key"
          ><el-descriptions-item
            v-if="!['passwordHash', 'requestHash', 'token'].includes(key)"
            :label="
              detailFields.find((f) => f.key === key)?.label ||
              detailLabels[key] ||
              key
            "
          >
            <pre v-if="jsonFields.includes(key)" class="json-detail">{{
              prettyJson(value)
            }}</pre>
            <el-tag
              v-else-if="statusTone(key, value)"
              :type="statusTone(key, value)"
              effect="light"
              class="record-status"
              >{{ valueText(value) }}</el-tag
            >
            <template v-else>{{ valueText(value) }}</template>
          </el-descriptions-item></template
        >
      </el-descriptions>
    </el-dialog>
  </section>
</template>
<style scoped>
.json-detail {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 260px;
  overflow: auto;
  margin: 0;
  font: 12px/1.6 monospace;
}
.json-record-details :deep(.el-descriptions__label) {
  width: 150px;
  min-width: 150px;
}
.desk-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.el-collapse {
  margin-bottom: 20px;
}
.pager {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
