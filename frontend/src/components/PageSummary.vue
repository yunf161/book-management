<script setup>
import { computed } from "vue";

const props = defineProps({
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  total: { type: Number, required: true },
});
const totalPages = computed(() => Math.ceil(props.total / props.pageSize));
const currentPage = computed(() =>
  props.total ? Math.min(props.page, totalPages.value) : 0,
);
</script>

<template>
  <span class="page-summary"
    >第 {{ currentPage }} 页 / 共 {{ totalPages }} 页</span
  >
</template>
