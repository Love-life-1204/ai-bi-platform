<template>
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    :label-width="labelWidth"
    :label-position="labelPosition"
    class="common-form"
    status-icon
    @submit.prevent
  >
    <slot />
    <div v-if="$slots.actions" class="common-form-actions"><slot name="actions" /></div>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

withDefaults(defineProps<{
  model: Record<string, unknown>
  rules?: FormRules
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
}>(), { rules: () => ({}), labelWidth: '112px', labelPosition: 'right' })

const formRef = ref<FormInstance>()

async function validate(): Promise<boolean> {
  return formRef.value?.validate().catch(() => false) ?? false
}

function resetFields(): void {
  formRef.value?.resetFields()
}

defineExpose({ validate, resetFields })
</script>
