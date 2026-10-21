<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import PageSummary from "../components/PageSummary.vue";
import { api } from "../api/library";
import { coverImageUrl } from "../modules/bookCover";
import { displayValue } from "../modules/display";
import { access } from "../stores/permissions";

const tab = ref("books");
const coverSaving = ref(false);

/** 使用书目当前版本；成功后重新查询以同步封面和版本。 */
async function uploadCover(row, event) {
  const file = event.target.files?.[0];
  if (!file || coverSaving.value) return;
  coverSaving.value = true;
  try {
    await api.uploadBookCover(row.id, file, row.version, !!row.coverObjectKey);
    ElMessage.success("封面已更新");
    await load();
  } finally {
    event.target.value = "";
    coverSaving.value = false;
  }
}

async function deleteCover(row) {
  if (coverSaving.value) return;
  await ElMessageBox.confirm(`删除《${row.title}》的封面？`, "删除封面");
  coverSaving.value = true;
  try {
    await api.deleteBookCover(row.id, row.version);
    ElMessage.success("封面已删除");
    await load();
  } finally {
    coverSaving.value = false;
  }
}
const operationAllowed = (action) =>
  access.has(
    `${tab.value === "books" ? "book" : tab.value === "categories" ? "book-category" : "book-copy"}:${action}`,
  );
const rows = ref([]);
const categories = ref([]);
const tags = ref([]);
const bookOptions = ref([]);
const total = ref(0);
const loading = ref(false);
const dialog = ref(false);
const mode = ref("create");
const editing = ref(null);
const saving = ref(false);

const bookQuery = reactive({
  page: 1,
  pageSize: 10,
  id: null,
  categoryId: null,
  keyword: "",
  title: "",
  subtitle: "",
  author: "",
  isbn: "",
  publisher: "",
  publishedDate: "",
  edition: "",
  language: "",
  description: "",
  coverObjectKey: "",
  status: "",
  version: null,
  createdAt: "",
  updatedAt: "",
  availableOnly: null,
});
const categoryQuery = reactive({
  page: 1,
  pageSize: 10,
  id: null,
  code: "",
  name: "",
  parentId: null,
  description: "",
  status: "",
  version: null,
  createdAt: "",
  updatedAt: "",
  rootOnly: null,
});
const copyQuery = reactive({
  page: 1,
  pageSize: 10,
  id: null,
  bookId: null,
  barcode: "",
  callNumber: "",
  location: "",
  status: "",
  acquiredAt: "",
  price: null,
  conditionNote: "",
  version: null,
  createdAt: "",
  updatedAt: "",
});
const query = computed(() =>
  tab.value === "books"
    ? bookQuery
    : tab.value === "categories"
      ? categoryQuery
      : copyQuery,
);

const bookForm = reactive({
  tagIds: [],
  categoryId: null,
  title: "",
  subtitle: "",
  author: "",
  isbn: "",
  publisher: "",
  publishedDate: "",
  edition: "",
  language: "zh-CN",
  description: "",
  coverObjectKey: "",
  status: "ACTIVE",
});
const categoryForm = reactive({
  code: "",
  name: "",
  parentId: null,
  description: "",
  status: "ACTIVE",
});
const copyForm = reactive({
  bookId: null,
  barcode: "",
  callNumber: "",
  location: "",
  status: "AVAILABLE",
  acquiredAt: "",
  price: null,
  conditionNote: "",
});

const copyStatuses = [
  "AVAILABLE",
  "BORROWED",
  "RESERVED",
  "MAINTENANCE",
  "LOST",
  "DISCARDED",
];
const categoryName = (id) =>
  categories.value.find((item) => item.id === id)?.name || "—";
// 副本手工修改只提供允许的状态，服务端仍重新验证转换。
const editableCopyStatuses = computed(() => {
  const current = editing.value?.status;
  const transitions = {
    AVAILABLE: ["MAINTENANCE", "LOST", "DISCARDED"],
    MAINTENANCE: ["AVAILABLE", "LOST", "DISCARDED"],
    LOST: ["AVAILABLE", "DISCARDED"],
  };
  return current ? [current, ...(transitions[current] || [])] : ["AVAILABLE"];
});

async function loadOptions() {
  const [categoryResult, bookResult, tagResult] = await Promise.all([
    api.categories({ page: 1, pageSize: 100 }),
    api.books({ page: 1, pageSize: 100 }),
    api.interestTags(),
  ]);
  categories.value = categoryResult.rows || [];
  bookOptions.value = bookResult.rows || [];
  tags.value = tagResult;
}

async function load() {
  loading.value = true;
  try {
    const result =
      tab.value === "books"
        ? await api.books(bookQuery)
        : tab.value === "categories"
          ? await api.categories(categoryQuery)
          : await api.copies(copyQuery);
    rows.value = result.rows || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

function resetForms() {
  Object.assign(bookForm, {
    tagIds: [],
    categoryId: null,
    title: "",
    subtitle: "",
    author: "",
    isbn: "",
    publisher: "",
    publishedDate: "",
    edition: "",
    language: "zh-CN",
    description: "",
    coverObjectKey: "",
    status: "ACTIVE",
  });
  Object.assign(categoryForm, {
    code: "",
    name: "",
    parentId: null,
    description: "",
    status: "ACTIVE",
  });
  Object.assign(copyForm, {
    bookId: null,
    barcode: "",
    callNumber: "",
    location: "",
    status: "AVAILABLE",
    acquiredAt: "",
    price: null,
    conditionNote: "",
  });
}

function create() {
  resetForms();
  mode.value = "create";
  editing.value = null;
  dialog.value = true;
}

function edit(row) {
  resetForms();
  mode.value = "edit";
  editing.value = row;
  // 查询附带的库存、名称和时间不属于修改 DTO，不复制到表单。
  const form =
    tab.value === "books"
      ? bookForm
      : tab.value === "categories"
        ? categoryForm
        : copyForm;
  for (const key of Object.keys(form))
    if (row[key] !== undefined) form[key] = row[key];
  if (tab.value === "books")
    bookForm.tagIds = (row.tags || []).map((tag) => tag.id);
  dialog.value = true;
}

async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (tab.value === "books") {
      const body = { ...bookForm };
      if (mode.value === "create") {
        delete body.status;
        await api.createBook(body);
      } else {
        body.expectedVersion = editing.value.version;
        await api.updateBook(editing.value.id, body);
      }
    } else if (tab.value === "categories") {
      if (mode.value === "create") {
        const { status, ...body } = categoryForm;
        await api.createCategory(body);
      } else {
        await api.updateCategory(editing.value.id, {
          name: categoryForm.name,
          parentId: categoryForm.parentId,
          description: categoryForm.description,
          status: categoryForm.status,
          expectedVersion: editing.value.version,
        });
      }
    } else {
      const { bookId, ...body } = copyForm;
      if (mode.value === "create") {
        delete body.status;
        await api.createCopy(bookId, body);
      } else {
        body.expectedVersion = editing.value.version;
        await api.updateCopy(editing.value.id, body);
      }
    }
    ElMessage.success("保存成功");
    dialog.value = false;
    await Promise.all([load(), loadOptions()]);
  } catch {
    // 保存失败保留表单，供用户修正或刷新版本。
  } finally {
    saving.value = false;
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${row.title || row.name || row.barcode}”吗？`,
      "删除确认",
      { type: "warning" },
    );
    if (tab.value === "books") await api.deleteBook(row.id);
    else if (tab.value === "categories") await api.deleteCategory(row.id);
    else await api.deleteCopy(row.id);
    ElMessage.success("删除成功");
    await Promise.all([load(), loadOptions()]);
  } catch {
    // 取消确认或服务端拒绝时不显示成功。
  }
}

function search() {
  query.value.page = 1;
  load();
}
function changePage(page) {
  query.value.page = page;
  load();
}
function changePageSize(size) {
  query.value.pageSize = size;
  query.value.page = 1;
  load();
}
function jumpPage(event) {
  const page = Number(event.target.value);
  const maxPage = Math.max(1, Math.ceil(total.value / query.value.pageSize));
  if (!Number.isInteger(page) || page < 1 || page > maxPage) return;
  changePage(page);
}
async function changeTab() {
  query.value.page = 1;
  await load();
}

onMounted(async () => {
  await loadOptions();
  await load();
});
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="COLLECTION"
      title="馆藏管理"
      description="维护图书分类、出版信息与实体副本，让每一本馆藏都有清晰的归属。"
    >
      <el-button
        v-if="operationAllowed('create')"
        type="primary"
        @click="create"
        >新增{{
          tab === "books" ? "书目" : tab === "categories" ? "分类" : "副本"
        }}</el-button
      >
    </PageHeader>
    <section class="panel">
      <el-tabs v-model="tab" @tab-change="changeTab">
        <el-tab-pane label="图书书目" name="books" /><el-tab-pane
          label="图书分类"
          name="categories"
        /><el-tab-pane label="馆藏副本" name="copies" />
      </el-tabs>

      <div v-if="tab === 'books'" class="filters full-filters">
        <el-input-number
          v-model="bookQuery.id"
          :min="1"
          placeholder="书目ID"
        /><el-select
          v-model="bookQuery.categoryId"
          clearable
          filterable
          placeholder="分类"
          ><el-option
            v-for="item in categories"
            :key="item.id"
            :label="`${item.id} · ${item.name}`"
            :value="item.id"
        /></el-select>
        <el-input
          v-model="bookQuery.keyword"
          clearable
          placeholder="综合关键词"
        /><el-input
          v-model="bookQuery.title"
          clearable
          placeholder="书名"
        /><el-input
          v-model="bookQuery.author"
          clearable
          placeholder="作者"
        /><el-input
          v-model="bookQuery.isbn"
          clearable
          placeholder="ISBN"
        /><el-input
          v-model="bookQuery.publisher"
          clearable
          placeholder="出版社"
        /><el-input v-model="bookQuery.language" clearable placeholder="语言" />
        <el-select v-model="bookQuery.status" clearable placeholder="状态"
          ><el-option label="启用" value="ACTIVE" /><el-option
            label="停用"
            value="DISABLED"
        /></el-select>
        <el-select
          v-model="bookQuery.availableOnly"
          clearable
          placeholder="可借库存"
          ><el-option label="仅有可借副本" :value="true" /><el-option
            label="不限库存"
            :value="false" /></el-select
        ><el-button type="primary" @click="search">查询</el-button>
      </div>
      <div v-else-if="tab === 'categories'" class="filters full-filters">
        <el-input-number
          v-model="categoryQuery.id"
          :min="1"
          placeholder="分类ID"
        /><el-input
          v-model="categoryQuery.code"
          clearable
          placeholder="分类编码"
        /><el-input
          v-model="categoryQuery.name"
          clearable
          placeholder="分类名称"
        /><el-select
          v-model="categoryQuery.parentId"
          clearable
          filterable
          placeholder="父分类"
          ><el-option
            v-for="item in categories"
            :key="item.id"
            :label="`${item.id} · ${item.name}`"
            :value="item.id" /></el-select
        ><el-select v-model="categoryQuery.status" clearable placeholder="状态"
          ><el-option label="启用" value="ACTIVE" /><el-option
            label="停用"
            value="DISABLED" /></el-select
        ><el-select
          v-model="categoryQuery.rootOnly"
          clearable
          placeholder="层级"
          ><el-option label="仅顶级分类" :value="true" /><el-option
            label="全部层级"
            :value="false" /></el-select
        ><el-button type="primary" @click="search">查询</el-button>
      </div>
      <div v-else class="filters full-filters">
        <el-input-number
          v-model="copyQuery.id"
          :min="1"
          placeholder="副本ID"
        /><el-select
          v-model="copyQuery.bookId"
          clearable
          filterable
          placeholder="所属书目"
          ><el-option
            v-for="item in bookOptions"
            :key="item.id"
            :label="`${item.id} · ${item.title}`"
            :value="item.id" /></el-select
        ><el-input
          v-model="copyQuery.barcode"
          clearable
          placeholder="馆藏条码"
        /><el-input
          v-model="copyQuery.callNumber"
          clearable
          placeholder="索书号"
        /><el-input
          v-model="copyQuery.location"
          clearable
          placeholder="馆藏位置"
        /><el-select v-model="copyQuery.status" clearable placeholder="副本状态"
          ><el-option
            v-for="status in copyStatuses"
            :key="status"
            :label="displayValue(status)"
            :value="status" /></el-select
        ><el-date-picker
          v-model="copyQuery.acquiredAt"
          value-format="YYYY-MM-DD"
          placeholder="入馆日期"
        /><el-input-number
          v-model="copyQuery.price"
          :min="0"
          placeholder="采购价格"
        /><el-input
          v-model="copyQuery.conditionNote"
          clearable
          placeholder="书况说明"
        /><el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-collapse class="advanced-query">
        <el-collapse-item title="更多筛选条件" name="advanced">
          <div v-if="tab === 'books'" class="filters full-filters">
            <el-input
              v-model="bookQuery.subtitle"
              clearable
              placeholder="副标题"
            /><el-date-picker
              v-model="bookQuery.publishedDate"
              value-format="YYYY-MM-DD"
              placeholder="出版日期"
            /><el-input
              v-model="bookQuery.edition"
              clearable
              placeholder="版本"
            /><el-input
              v-model="bookQuery.description"
              clearable
              placeholder="简介"
            /><el-input
              v-model="bookQuery.coverObjectKey"
              clearable
              placeholder="封面对象路径"
            /><el-input-number
              v-model="bookQuery.version"
              :min="0"
              placeholder="版本号"
            /><el-date-picker
              v-model="bookQuery.createdAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="创建时间"
            /><el-date-picker
              v-model="bookQuery.updatedAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="更新时间"
            />
          </div>
          <div v-else-if="tab === 'categories'" class="filters full-filters">
            <el-input
              v-model="categoryQuery.description"
              clearable
              placeholder="分类说明"
            /><el-input-number
              v-model="categoryQuery.version"
              :min="0"
              placeholder="版本号"
            /><el-date-picker
              v-model="categoryQuery.createdAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="创建时间"
            /><el-date-picker
              v-model="categoryQuery.updatedAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="更新时间"
            />
          </div>
          <div v-else class="filters full-filters">
            <el-input-number
              v-model="copyQuery.version"
              :min="0"
              placeholder="版本号"
            /><el-date-picker
              v-model="copyQuery.createdAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="创建时间"
            /><el-date-picker
              v-model="copyQuery.updatedAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="更新时间"
            />
          </div>
        </el-collapse-item>
      </el-collapse>

      <el-table v-loading="loading" :data="rows" stripe>
        <template v-if="tab === 'books'">
          <el-table-column label="封面" width="120">
            <template #default="{ row }">
              <el-image
                v-if="row.coverUrl"
                :key="row.version"
                :src="coverImageUrl(row.coverUrl)"
                :alt="row.title + '封面'"
                fit="cover"
                style="width: 55px; height: 75px"
              >
                <template #error
                  ><span class="cover-unavailable">封面暂不可用</span></template
                >
              </el-image>
              <span v-else>未设置</span>
              <div v-if="access.has('book:update')">
                <label style="cursor: pointer; color: var(--el-color-primary)">
                  {{ row.coverObjectKey ? "替换" : "上传" }}
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/gif"
                    :disabled="coverSaving"
                    style="display: none"
                    @change="uploadCover(row, $event)"
                  />
                </label>
                <el-button
                  v-if="row.coverObjectKey"
                  link
                  type="danger"
                  :disabled="coverSaving"
                  @click="deleteCover(row)"
                  >删除</el-button
                >
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="id" label="ID" width="70" /><el-table-column
            prop="title"
            label="书名"
            min-width="160"
          /><el-table-column
            prop="subtitle"
            label="副标题"
            min-width="130"
          /><el-table-column
            prop="category"
            label="分类"
            min-width="130"
          /><el-table-column
            prop="author"
            label="作者"
            min-width="120"
          /><el-table-column
            prop="isbn"
            label="ISBN"
            min-width="130"
          /><el-table-column
            prop="publisher"
            label="出版社"
            min-width="120"
          /><el-table-column
            prop="publishedDate"
            label="出版日期"
            width="110"
          /><el-table-column
            prop="edition"
            label="版本"
            width="90"
          /><el-table-column
            prop="language"
            label="语言"
            width="90"
          /><el-table-column
            prop="totalCopies"
            label="总副本"
            width="85"
          /><el-table-column
            prop="availableCopies"
            label="可借"
            width="75"
          /><el-table-column
            prop="status"
            label="状态"
            width="95"
          /><el-table-column prop="version" label="版本号" width="75" />
        </template>
        <template v-else-if="tab === 'categories'">
          <el-table-column prop="id" label="ID" width="70" /><el-table-column
            prop="code"
            label="编码"
          /><el-table-column prop="name" label="名称" /><el-table-column
            label="父分类"
            ><template #default="{ row }">{{
              row.parentId
                ? `${row.parentId} · ${categoryName(row.parentId)}`
                : "顶级分类"
            }}</template></el-table-column
          ><el-table-column
            prop="description"
            label="说明"
            min-width="180"
          /><el-table-column prop="status" label="状态" /><el-table-column
            prop="version"
            label="版本号"
          />
        </template>
        <template v-else>
          <el-table-column prop="id" label="ID" width="70" /><el-table-column
            prop="bookTitle"
            label="书名"
            min-width="160"
          /><el-table-column
            prop="bookId"
            label="书目ID"
            width="80"
          /><el-table-column
            prop="barcode"
            label="馆藏条码"
            min-width="130"
          /><el-table-column prop="callNumber" label="索书号" /><el-table-column
            prop="location"
            label="位置"
          /><el-table-column
            prop="status"
            label="状态"
            min-width="110"
          /><el-table-column
            prop="acquiredAt"
            label="入馆日期"
          /><el-table-column prop="price" label="价格" /><el-table-column
            prop="conditionNote"
            label="书况"
            min-width="140"
          /><el-table-column prop="version" label="版本号" />
        </template>
        <el-table-column label="操作" fixed="right" width="125"
          ><template #default="{ row }"
            ><el-button
              v-if="operationAllowed('update')"
              link
              @click="edit(row)"
              >编辑</el-button
            ><el-button
              v-if="operationAllowed('delete')"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <div class="pager">
        <PageSummary
          :page="query.page"
          :page-size="query.pageSize"
          :total="total"
        />
        <el-pagination
          :current-page="query.page"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @keyup.enter="jumpPage"
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialog"
      :title="mode === 'create' ? '新增记录' : '编辑记录'"
      width="720px"
    >
      <el-form v-if="tab === 'books'" label-position="top" class="form-grid">
        <el-form-item label="分类"
          ><el-select v-model="bookForm.categoryId" filterable
            ><el-option
              v-for="item in categories"
              :key="item.id"
              :disabled="item.status !== 'ACTIVE'"
              :label="`${item.id} · ${item.name}`"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="推荐标签"
          ><el-select v-model="bookForm.tagIds" multiple filterable clearable>
            <el-option
              v-for="tag in tags"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            /> </el-select></el-form-item
        ><el-form-item label="书名"
          ><el-input v-model="bookForm.title" /></el-form-item
        ><el-form-item label="副标题"
          ><el-input v-model="bookForm.subtitle" /></el-form-item
        ><el-form-item label="作者"
          ><el-input v-model="bookForm.author" /></el-form-item
        ><el-form-item label="ISBN"
          ><el-input v-model="bookForm.isbn" /></el-form-item
        ><el-form-item label="出版社"
          ><el-input v-model="bookForm.publisher" /></el-form-item
        ><el-form-item label="出版日期"
          ><el-date-picker
            v-model="bookForm.publishedDate"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="版本"
          ><el-input v-model="bookForm.edition" /></el-form-item
        ><el-form-item label="语言"
          ><el-input v-model="bookForm.language" /></el-form-item
        ><el-form-item v-if="mode === 'edit'" label="状态"
          ><el-select v-model="bookForm.status"
            ><el-option label="启用" value="ACTIVE" /><el-option
              label="停用"
              value="DISABLED" /></el-select></el-form-item
        ><el-form-item label="封面对象路径" class="span-2"
          ><el-input
            v-model="bookForm.coverObjectKey"
            readonly
            placeholder="创建图书后通过封面接口上传" /></el-form-item
        ><el-form-item label="简介" class="span-2"
          ><el-input v-model="bookForm.description" type="textarea" :rows="4"
        /></el-form-item>
      </el-form>
      <el-form
        v-else-if="tab === 'categories'"
        label-position="top"
        class="form-grid"
        ><el-form-item label="编码"
          ><el-input
            v-model="categoryForm.code"
            :disabled="mode === 'edit'" /></el-form-item
        ><el-form-item label="名称"
          ><el-input v-model="categoryForm.name" /></el-form-item
        ><el-form-item label="父分类"
          ><el-select v-model="categoryForm.parentId" clearable filterable
            ><el-option
              v-for="item in categories"
              :key="item.id"
              :disabled="item.id === editing?.id || item.status !== 'ACTIVE'"
              :label="`${item.id} · ${item.name}`"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item v-if="mode === 'edit'" label="状态"
          ><el-select v-model="categoryForm.status"
            ><el-option label="启用" value="ACTIVE" /><el-option
              label="停用"
              value="DISABLED" /></el-select></el-form-item
        ><el-form-item label="说明" class="span-2"
          ><el-input
            v-model="categoryForm.description"
            type="textarea"
            :rows="4" /></el-form-item
      ></el-form>
      <el-form v-else label-position="top" class="form-grid"
        ><el-form-item label="所属书目"
          ><el-select
            v-model="copyForm.bookId"
            filterable
            :disabled="mode === 'edit'"
            ><el-option
              v-for="item in bookOptions"
              :key="item.id"
              :label="`${item.id} · ${item.title}`"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="馆藏条码"
          ><el-input v-model="copyForm.barcode" /></el-form-item
        ><el-form-item label="索书号"
          ><el-input v-model="copyForm.callNumber" /></el-form-item
        ><el-form-item label="馆藏位置"
          ><el-input v-model="copyForm.location" /></el-form-item
        ><el-form-item v-if="mode === 'edit'" label="状态"
          ><el-select v-model="copyForm.status"
            ><el-option
              v-for="status in editableCopyStatuses"
              :key="status"
              :label="displayValue(status)"
              :value="status" /></el-select></el-form-item
        ><el-form-item label="入馆日期"
          ><el-date-picker
            v-model="copyForm.acquiredAt"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="采购价格"
          ><el-input-number
            v-model="copyForm.price"
            :min="0"
            :precision="2" /></el-form-item
        ><el-form-item label="实体状况" class="span-2"
          ><el-input
            v-model="copyForm.conditionNote"
            type="textarea"
            :rows="3" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </AppShell>
</template>
