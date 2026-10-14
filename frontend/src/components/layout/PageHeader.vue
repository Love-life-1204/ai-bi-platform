<template>
  <section class="page-header">
    <el-breadcrumb separator="/" class="page-breadcrumb" aria-label="面包屑">
      <el-breadcrumb-item :to="{ path: '/' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item v-if="section">{{ section }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ title }}</el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-heading-row">
      <div class="page-heading-copy">
        <el-button v-if="showBack" class="page-back" text circle aria-label="返回" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div>
          <h1>{{ title }}</h1>
          <p v-if="description">{{ description }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="page-actions" aria-label="页面操作">
        <slot name="actions" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  section?: string
  showBack?: boolean
  backTo?: string
}>(), { description: '', section: '', showBack: false, backTo: '' })

const router = useRouter()

function goBack(): void {
  if (props.backTo) void router.push(props.backTo)
  else router.back()
}
</script>
