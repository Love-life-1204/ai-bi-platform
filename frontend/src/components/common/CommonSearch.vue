<template>
  <el-form class="common-search" inline @submit.prevent="$emit('search')">
    <el-form-item class="search-keyword">
      <el-input
        :model-value="modelValue"
        :placeholder="placeholder"
        clearable
        :prefix-icon="Search"
        @update:model-value="updateModelValue"
        @keyup.enter="$emit('search')"
        @clear="$emit('search')"
      />
    </el-form-item>
    <slot />
    <el-form-item class="search-actions">
      <el-button type="primary" :loading="loading" native-type="submit">查询</el-button>
      <el-button @click="$emit('reset')">重置</el-button>
      <slot name="actions" />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

withDefaults(defineProps<{ modelValue: string; placeholder?: string; loading?: boolean }>(), {
  placeholder: '请输入关键词', loading: false,
})
const emit = defineEmits<{
  'update:model-value': [value: string]
  search: []
  reset: []
}>()

function updateModelValue(value: string | number): void {
  emit('update:model-value', String(value))
}
</script>
