<template>
  <section class="unified-state" :class="`state-${state}`" :aria-busy="state === 'loading'">
    <div class="state-visual" aria-hidden="true">
      <el-icon v-if="state !== 'loading'" :size="38"><component :is="current.icon" /></el-icon>
      <span v-else class="state-spinner" />
    </div>
    <h2>{{ title || current.title }}</h2>
    <p>{{ description || current.description }}</p>
    <div v-if="$slots.default" class="state-detail"><slot /></div>
    <div v-if="retryable || actionText" class="state-actions">
      <el-button v-if="retryable" type="primary" @click="$emit('retry')">重新加载</el-button>
      <el-button v-if="actionText" :type="retryable ? 'default' : 'primary'" @click="$emit('action')">{{ actionText }}</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import { Clock, Connection, DataLine, Lock, Warning, WarningFilled } from '@element-plus/icons-vue'

export type UnifiedStateType = 'loading' | 'empty' | 'error' | 'forbidden' | 'partial' | 'timeout'

const props = withDefaults(defineProps<{
  state: UnifiedStateType
  title?: string
  description?: string
  retryable?: boolean
  actionText?: string
}>(), { title: '', description: '', retryable: false, actionText: '' })

defineEmits<{ retry: []; action: [] }>()

const definitions = {
  loading: { title: '正在加载', description: '正在获取最新数据，请稍候。', icon: markRaw(DataLine) },
  empty: { title: '暂无数据', description: '当前条件下没有可展示的内容。', icon: markRaw(DataLine) },
  error: { title: '加载失败', description: '服务暂时不可用，请稍后重试。', icon: markRaw(WarningFilled) },
  forbidden: { title: '无权访问', description: '当前账号没有查看此内容的权限。', icon: markRaw(Lock) },
  partial: { title: '部分数据不可用', description: '部分组件加载失败，其余内容仍可继续使用。', icon: markRaw(Warning) },
  timeout: { title: '请求超时', description: '处理时间超过预期，请缩小范围后重试。', icon: markRaw(Clock) },
} as const

const current = computed(() => definitions[props.state] ?? {
  title: '状态未知', description: '请刷新页面后重试。', icon: markRaw(Connection),
})
</script>
