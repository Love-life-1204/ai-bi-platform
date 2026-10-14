<template>
  <section class="surface-card forbidden-page">
    <UnifiedState
      state="forbidden"
      title="当前成员无权访问"
      description="前端已停止当前操作；最终授权结果始终由 Spring Boot 权限策略决定。"
      action-text="返回工作台"
      @action="router.push('/datasources')"
    />
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import UnifiedState from '../components/common/UnifiedState.vue'

const router = useRouter()
</script>
