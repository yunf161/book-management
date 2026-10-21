<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import PageSummary from "../components/PageSummary.vue";
import { api } from "../api/library";
import { coverImageUrl } from "../modules/bookCover";
import { access } from "../stores/permissions";

const route = useRoute();

const loading = ref(false),
  rows = ref([]),
  total = ref(0),
  dialog = ref(false),
  selected = ref(null);
const query = reactive({
  page: 1,
  pageSize: 12,
  keyword: String(route.query.keyword || ""),
  categoryId: "",
  status: "ACTIVE",
  availableOnly: true,
});
const applyForm = reactive({ requestedDays: 30, reason: "" });
async function load() {
  loading.value = true;
  try {
    const r = await api.books(query);
    rows.value = r.rows || [];
    total.value = r.total || 0;
  } finally {
    loading.value = false;
  }
}
function openApply(book) {
  selected.value = book;
  applyForm.requestedDays = 30;
  applyForm.reason = "";
  dialog.value = true;
}
async function apply() {
  await api.createApplication({ bookId: selected.value.id, ...applyForm });
  ElMessage.success("借阅申请已提交");
  dialog.value = false;
}
onMounted(load);
</script>
<template>
  <AppShell>
    <PageHeader
      eyebrow="DISCOVER"
      title="馆藏检索"
      description="按书名、作者、ISBN 或出版社寻找下一本书。"
    />
    <section class="panel">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="书名 / 作者 / ISBN"
          @keyup.enter="
            query.page = 1;
            load();
          "
        />
        <el-select v-model="query.status" clearable placeholder="书目状态"
          ><el-option label="启用" value="ACTIVE" /><el-option
            label="停用"
            value="DISABLED"
        /></el-select>
        <el-checkbox v-model="query.availableOnly">仅看可借</el-checkbox>
        <el-button
          type="primary"
          @click="
            query.page = 1;
            load();
          "
          >查询</el-button
        >
      </div>
      <div v-loading="loading" class="book-grid">
        <article
          v-for="book in rows"
          :key="book.id"
          class="book-card catalog-card"
        >
          <el-image
            v-if="book.coverUrl"
            :key="book.version"
            :src="coverImageUrl(book.coverUrl)"
            :alt="book.title + '封面'"
            fit="cover"
            style="
              width: 130px;
              height: 180px;
              flex-shrink: 0;
              border-radius: 8px;
            "
          >
            <template #error>
              <div class="book-cover compact">
                <span>{{ book.category || book.categoryName || "馆藏" }}</span>
                <strong>{{ book.title }}</strong
                ><small>阅知馆藏</small>
              </div>
            </template>
          </el-image>
          <div v-if="!book.coverUrl" class="book-cover compact">
            <span>{{ book.category || book.categoryName || "未分类" }}</span
            ><strong>{{ book.title }}</strong
            ><small>阅知馆藏</small>
          </div>
          <div class="catalog-copy">
            <span class="category">{{
              book.category || book.categoryName || "未分类"
            }}</span>
            <h3>{{ book.title }}</h3>
            <p>{{ book.author }}</p>
            <p>{{ book.publisher || "出版社未录入" }}</p>
            <p>ISBN {{ book.isbn || "—" }}</p>
          </div>
          <footer>
            <span class="stock"
              >可借 {{ book.availableCopies ?? 0 }} / 馆藏
              {{ book.totalCopies ?? 0 }}</span
            ><el-button
              type="primary"
              plain
              size="small"
              :disabled="
                !book.availableCopies ||
                !access.has('borrow-application:create')
              "
              @click="openApply(book)"
              >{{
                access.has("borrow-application:create")
                  ? "申请借阅"
                  : "暂无借阅操作权限"
              }}</el-button
            >
          </footer>
        </article>
      </div>
      <div v-if="!loading && !rows.length" class="empty">
        没有找到符合条件的图书
      </div>
      <div class="pager">
        <PageSummary
          :page="query.page"
          :page-size="query.pageSize"
          :total="total"
        />
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          layout="total, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </section>
    <el-dialog v-model="dialog" title="申请借阅" width="460px"
      ><p>《{{ selected?.title }}》</p>
      <el-form label-position="top"
        ><el-form-item label="借阅天数"
          ><el-input-number
            v-model="applyForm.requestedDays"
            :min="1"
            :max="365" /></el-form-item
        ><el-form-item label="申请说明"
          ><el-input
            v-model="applyForm.reason"
            type="textarea"
            :rows="3" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="apply">提交申请</el-button></template
      ></el-dialog
    >
  </AppShell>
</template>
