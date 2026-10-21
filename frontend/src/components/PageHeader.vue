<script setup>
defineProps({ eyebrow: String, title: String, description: String });
</script>
<template>
  <header class="page-header">
    <div>
      <small>{{ eyebrow }}</small>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <slot />
  </header>
</template>
