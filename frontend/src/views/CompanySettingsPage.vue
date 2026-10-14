<template>
  <div class="organization-shell company-settings">
    <UnifiedState v-if="loading" state="loading" description="正在读取企业基本信息。" />
    <UnifiedState v-else-if="errorMessage && !tenant" state="error" :description="errorMessage" retryable @retry="load" />
    <template v-else-if="tenant">
      <section class="organization-summary">
        <div class="company-monogram">{{ tenant.tenantName.slice(0, 1) }}</div>
        <div><span class="eyebrow">ENTERPRISE PROFILE</span><h2>{{ tenant.tenantName }}</h2><p>{{ tenant.tenantCode }} · 企业 ID {{ tenant.id }}</p></div>
        <el-tag :type="tenant.tenantStatus === 'ACTIVE' ? 'success' : 'warning'" effect="light">{{ tenant.tenantStatus === 'ACTIVE' ? '正常' : tenant.tenantStatus }}</el-tag>
      </section>

      <section class="workbench-card settings-card">
        <header><div><span class="eyebrow">BASIC INFORMATION</span><h3>基本信息</h3></div><span class="settings-hint">企业编码不可修改</span></header>
        <el-alert v-if="errorMessage" class="workspace-partial" type="error" :title="errorMessage" show-icon @close="errorMessage = ''" />
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="settings-form">
          <div class="settings-form-grid">
            <el-form-item label="企业名称" prop="tenantName"><el-input v-model.trim="form.tenantName" maxlength="200" show-word-limit /></el-form-item>
            <el-form-item label="企业编码"><el-input :model-value="tenant.tenantCode" disabled /></el-form-item>
            <el-form-item label="默认时区" prop="timezone">
              <el-select v-model="form.timezone" filterable><el-option v-for="item in timezones" :key="item" :label="item" :value="item" /></el-select>
            </el-form-item>
            <el-form-item label="默认币种" prop="defaultCurrency">
              <el-select v-model="form.defaultCurrency"><el-option v-for="currency in currencies" :key="currency.value" :label="currency.label" :value="currency.value" /></el-select>
            </el-form-item>
          </div>
          <div class="settings-metadata">
            <span>创建时间：{{ formatDate(tenant.createdAt) }}</span><span>最近更新：{{ formatDate(tenant.updatedAt) }}</span><span>权限版本：{{ tenant.permissionVersion }}</span>
          </div>
          <div class="settings-actions"><el-button @click="reset">重置</el-button><el-button type="primary" :loading="saving" @click="save">保存更改</el-button></div>
        </el-form>
      </section>

      <section class="security-note-card">
        <el-icon :size="22"><Lock /></el-icon>
        <div><strong>企业安全边界</strong><p>企业信息、成员、工作空间与业务数据均以租户上下文隔离；页面隐藏不代表授权，所有写操作仍由服务端校验。</p></div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { resourceId } from '../api/resource-id'
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import UnifiedState from '../components/common/UnifiedState.vue'
import { organizationApi } from '../api/runtime'
import type { TenantData } from '../auth/types'
import { useAppStore } from '../stores/app'

const app = useAppStore()
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const tenant = ref<TenantData>()
const formRef = ref<FormInstance>()
const form = reactive({ tenantName: '', timezone: 'Asia/Shanghai', defaultCurrency: 'CNY' })
const timezones = ['Asia/Shanghai', 'Asia/Hong_Kong', 'Asia/Singapore', 'Asia/Tokyo', 'Europe/London', 'America/New_York', 'UTC']
const currencies = [{ value: 'CNY', label: 'CNY · 人民币' }, { value: 'USD', label: 'USD · 美元' }, { value: 'EUR', label: 'EUR · 欧元' }, { value: 'JPY', label: 'JPY · 日元' }, { value: 'HKD', label: 'HKD · 港币' }]
const rules: FormRules<typeof form> = {
  tenantName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  timezone: [{ required: true, message: '请选择默认时区', trigger: 'change' }],
  defaultCurrency: [{ required: true, pattern: /^[A-Z]{3}$/, message: '请选择 3 位币种代码', trigger: 'change' }],
}

function reset(): void {
  if (!tenant.value) return
  Object.assign(form, { tenantName: tenant.value.tenantName, timezone: tenant.value.timezone, defaultCurrency: tenant.value.defaultCurrency })
  formRef.value?.clearValidate()
}
async function load(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  try { tenant.value = await organizationApi.tenant(resourceId(app.tenantId)); reset() }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '企业信息加载失败' }
  finally { loading.value = false }
}
async function save(): Promise<void> {
  if (!tenant.value || saving.value || !await formRef.value?.validate().catch(() => false)) return
  saving.value = true; errorMessage.value = ''
  try {
    tenant.value = await organizationApi.updateTenant(tenant.value.id, { ...form, lockVersion: tenant.value.lockVersion })
    const option = app.tenants.find(item => item.id === app.tenantId)
    if (option) option.name = tenant.value.tenantName
    reset(); ElMessage.success('企业信息已更新')
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '保存失败' }
  finally { saving.value = false }
}
function formatDate(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }

watch(() => app.tenantId, load)
load()
</script>
