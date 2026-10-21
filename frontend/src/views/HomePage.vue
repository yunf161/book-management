<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import PageHeader from "../components/PageHeader.vue";
import { api } from "../api/library";
import { coverImageUrl } from "../modules/bookCover";
import { access, canEnter } from "../stores/permissions";
import { recommendationPresentation } from "../modules/recommendationPresentation";

const router = useRouter();
const loading = ref(false);
const books = ref([]);
const keyword = ref("");
const generatedAt = ref(null);
const recommendationSource = ref("");
const personalizationEnabled = ref(false);
const presentation = computed(() =>
  recommendationPresentation(personalizationEnabled.value),
);
const feedbackSaving = ref(false);

function showRecommendations(result) {
  personalizationEnabled.value = result.personalizationEnabled === true;
  generatedAt.value = result.generatedAt;
  recommendationSource.value = result.source || "";
  books.value = (result.items || []).map((item) => ({
    ...item.book,
    itemId: item.itemId,
    reason: item.reason,
  }));
}

/** 刷新属于明确写操作；不把分页查书伪装成推荐算法。 */
async function refreshRecommendations() {
  if (loading.value) return;
  loading.value = true;
  try {
    showRecommendations(await api.refreshRecommendations(crypto.randomUUID()));
  } finally {
    loading.value = false;
  }
}

async function feedback(book, kind) {
  if (feedbackSaving.value) return;
  feedbackSaving.value = true;
  try {
    showRecommendations(await api.recommendationFeedback(book.itemId, kind));
  } finally {
    feedbackSaving.value = false;
  }
}

async function loadRecommendations() {
  loading.value = true;
  try {
    if (!access.has("recommendation:query:self")) return;
    const result = await api.currentRecommendations();
    showRecommendations(result);
    // 无当前批次时只生成一次；有批次但因反馈变为空时等待用户主动刷新。
    if (!result.batchId && access.has("recommendation:refresh:self"))
      showRecommendations(
        await api.refreshRecommendations(crypto.randomUUID()),
      );
  } finally {
    loading.value = false;
  }
}

function search(topic = keyword.value) {
  router.push({ path: "/catalog", query: topic ? { keyword: topic } : {} });
}

onMounted(loadRecommendations);
</script>

<template>
  <AppShell>
    <PageHeader
      eyebrow="阅知 / 读者服务"
      title="发现下一本值得读的书"
      description="从真实馆藏出发，让每一次阅读更有方向。"
    />

    <section class="search-hero">
      <div class="hero-shelf" aria-hidden="true">
        <i>READ</i><i>DISCOVER</i><i>IMAGINE</i><i>知</i>
      </div>
      <p class="eyebrow">馆藏知识入口</p>
      <h2>你想了解什么？</h2>
      <p>搜索书名、作者，或描述你想学习的主题。</p>
      <form class="hero-search" @submit.prevent="search()">
        <el-input
          v-model="keyword"
          size="large"
          placeholder="例如：数据库入门、Java 核心技术"
        />
        <el-button type="primary" size="large" native-type="submit"
          >搜索馆藏</el-button
        >
      </form>
      <div class="topic-row">
        <span>热门主题</span>
        <button
          v-for="topic in ['Java', '数据库', '软件工程']"
          :key="topic"
          type="button"
          @click="search(topic)"
        >
          {{ topic }}
        </button>
      </div>
    </section>

    <section class="todo-strip">
      <div>
        <strong>开启你的下一段阅读旅程</strong
        ><span>管理借阅、安排阅读，也可以向馆员寻求帮助</span>
      </div>
      <div class="actions">
        <router-link v-if="canEnter('/member-changes')" to="/member-changes"
          >资格与资料申请</router-link
        ><router-link v-if="canEnter('/borrow')" to="/borrow"
          >查看我的借阅</router-link
        ><router-link v-if="canEnter('/messages')" to="/messages"
          >咨询馆员</router-link
        >
      </div>
    </section>

    <div class="section-head">
      <div>
        <h2>
          {{ presentation.title }}
          <el-tag v-if="recommendationSource" size="small">{{
            recommendationSource === "AI" ? "AI精选" : "规则推荐"
          }}</el-tag>
        </h2>
        <p>
          {{ presentation.description }}，每次最多六本；生成于
          {{ generatedAt || "—" }}
        </p>
      </div>
      <el-button
        v-if="access.has('recommendation:refresh:self')"
        :loading="loading"
        @click="refreshRecommendations"
        >换一批推荐</el-button
      >
    </div>
    <el-empty
      v-if="!loading && !books.length"
      :description="presentation.empty"
    />
    <div v-loading="loading" class="prototype-books">
      <article v-for="book in books" :key="book.id" class="prototype-book-card">
        <div class="book-top">
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
              <div class="book-cover">
                <span>{{ book.category || "馆藏" }}</span
                ><strong>{{ book.title }}</strong
                ><small>阅知馆藏</small>
              </div>
            </template>
          </el-image>
          <div v-if="!book.coverUrl" class="book-cover">
            <span>{{ book.category || book.categoryName || "馆藏" }}</span
            ><strong>{{ book.title }}</strong
            ><small>阅知馆藏</small>
          </div>
          <div>
            <h3>{{ book.title }}</h3>
            <p>{{ book.author }}</p>
            <el-tag type="success"
              >可借 {{ book.availableCopies ?? 0 }} 册</el-tag
            >
          </div>
        </div>
        <div class="reason">
          <strong>推荐理由</strong>
          <p>
            {{ book.reason }}
          </p>
        </div>
        <div v-if="access.has('recommendation:feedback:self')" class="actions">
          <el-button
            link
            :disabled="feedbackSaving"
            @click="feedback(book, 'NOT_INTERESTED')"
            >不感兴趣</el-button
          >
          <el-button
            link
            :disabled="feedbackSaving"
            @click="feedback(book, 'ALREADY_READ')"
            >已经读过</el-button
          >
          <el-button
            link
            :disabled="feedbackSaving"
            @click="feedback(book, 'TOO_DIFFICULT')"
            >难度偏高</el-button
          >
        </div>
        <footer>
          <el-button
            @click="
              router.push({ path: '/catalog', query: { keyword: book.title } })
            "
            >查看详情</el-button
          ><el-button
            v-if="access.has('borrow-application:create')"
            type="primary"
            @click="router.push('/catalog')"
            >申请借阅</el-button
          >
        </footer>
      </article>
    </div>

    <div class="section-head"><h2>服务与阅读</h2></div>
    <div class="two-col">
      <section v-if="canEnter('/reading-plan')" class="panel service-card">
        <h3>把目标变成一份阅读计划</h3>
        <p>创建书单、安排顺序，并持续记录每一本书的阅读进度。</p>
        <router-link to="/reading-plan">制定阅读计划 →</router-link>
      </section>
      <section v-if="canEnter('/messages')" class="panel service-card">
        <h3>咨询图书馆员</h3>
        <p>查找馆员、发起咨询，并查看借阅与资料审批通知。</p>
        <router-link to="/messages">进入消息中心 →</router-link>
      </section>
    </div>
  </AppShell>
</template>
