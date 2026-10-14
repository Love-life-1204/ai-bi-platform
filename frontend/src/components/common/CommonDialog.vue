<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
    class="common-dialog"
    @update:model-value="updateModelValue"
    @closed="$emit('closed')"
  >
    <div class="common-dialog-content" @keydown.ctrl.enter.exact.prevent="confirmFromKeyboard" @keydown.meta.enter.exact.prevent="confirmFromKeyboard">
      <slot />
    </div>
    <template #footer>
      <slot name="footer">
        <el-button @click="$emit('update:model-value', false)">取消</el-button>
        <el-button type="primary" :loading="confirmLoading" :disabled="confirmDisabled" @click="$emit('confirm')">{{ confirmText }}</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  width?: string
  confirmText?: string
  confirmLoading?: boolean
  confirmDisabled?: boolean
}>(), { width: '560px', confirmText: '确定', confirmLoading: false, confirmDisabled: false })

const emit = defineEmits<{
  'update:model-value': [value: boolean]
  confirm: []
  closed: []
}>()

function updateModelValue(value: boolean): void {
  emit('update:model-value', value)
}

function confirmFromKeyboard(): void {
  if (!props.confirmLoading && !props.confirmDisabled) emit('confirm')
}
</script>
