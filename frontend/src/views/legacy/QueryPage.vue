<template>
  <section ref="root" class="legacy-page" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { queryApi } from '../../api/runtime'
import { QueryPage } from '../../query/query-page'

const root = ref<HTMLElement>()
onMounted(() => {
  if (root.value) new QueryPage(root.value, queryApi).render()
})
</script>
