<template>
  <main class="auth-page">
    <section class="auth-brand-panel" aria-label="产品介绍">
      <div class="auth-brand-content">
        <div class="auth-logo-row">
          <div class="brand-mark" aria-hidden="true"><span /><span /><span /></div>
          <strong>AI BI</strong>
        </div>
        <p class="auth-kicker">ENTERPRISE ANALYTICS</p>
        <h1>让数据成为<br>每个决策的依据</h1>
        <p class="auth-brand-description">
          在统一、可信的企业数据空间中，连接数据、定义指标，并通过 AI 获得可追溯的分析结论。
        </p>
        <div class="auth-proof-grid">
          <article><strong>统一口径</strong><span>指标与语义集中治理</span></article>
          <article><strong>权限隔离</strong><span>企业与成员上下文分离</span></article>
          <article><strong>可追溯</strong><span>从结论回看查询证据</span></article>
        </div>
      </div>
      <p class="auth-security-note">企业级多租户安全 · 所有业务权限由服务端最终校验</p>
    </section>

    <section class="auth-form-panel">
      <div class="auth-form-wrap">
        <div class="auth-mobile-brand">
          <div class="brand-mark" aria-hidden="true"><span /><span /><span /></div>
          <strong>AI BI</strong>
        </div>
        <p class="eyebrow">欢迎回来</p>
        <h2>登录企业工作台</h2>
        <p class="auth-form-description">使用企业账号继续访问已授权的数据与分析资产。</p>

        <el-alert
          v-if="errorMessage"
          class="auth-alert"
          :title="errorMessage"
          type="error"
          show-icon
          :closable="true"
          @close="errorMessage = ''"
        />
        <el-alert
          v-else-if="route.query.reason === 'expired'"
          class="auth-alert"
          title="登录状态已过期，请重新登录"
          type="warning"
          show-icon
          :closable="false"
        />

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model.trim="form.email"
              size="large"
              autocomplete="username"
              inputmode="email"
              placeholder="name@company.com"
              :prefix-icon="Message"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入登录密码"
              :prefix-icon="Lock"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="企业" prop="tenantId">
            <el-select
              v-model="form.tenantId"
              size="large"
              filterable
              allow-create
              clearable
              default-first-option
              placeholder="可选：选择企业或输入企业 ID"
              aria-label="企业选择"
            >
              <el-option
                v-for="tenant in app.tenants"
                :key="tenant.id"
                :value="tenant.id"
                :label="tenant.code ? `${tenant.name} · ${tenant.code}` : tenant.name"
              />
            </el-select>
            <p class="auth-field-help">留空时自动进入最近使用或首个可用企业。</p>
          </el-form-item>
          <el-button
            class="auth-submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="submitting"
            :disabled="submitting"
          >
            {{ submitting ? '正在验证身份…' : '登录工作台' }}
          </el-button>
        </el-form>

        <div class="auth-help">
          <span>无法登录？</span>
          <span>请联系企业管理员确认成员状态</span>
        </div>
      </div>
      <p class="auth-footer">© 2026 AI BI Platform</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Lock, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { ApiClientError } from '../api/client'
import { useAppStore } from '../stores/app'

const app = useAppStore()
const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({ email: '', password: '', tenantId: '' })
const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度应为 8–72 个字符', trigger: 'blur' },
  ],
  tenantId: [{ pattern: /^$|^[1-9]\d*$/, message: '企业 ID 必须为正整数', trigger: 'change' }],
}

function loginError(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.status === 401) return '邮箱或密码错误，请检查后重试'
    if (error.status === 429) return '尝试次数过多，请稍后再试'
    return `${error.message}${error.traceId ? `（追踪号：${error.traceId}）` : ''}`
  }
  return error instanceof Error ? error.message : '登录失败，请稍后重试'
}

async function submit(): Promise<void> {
  if (submitting.value || !formRef.value) return
  errorMessage.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await app.login(form)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      && !route.query.redirect.startsWith('//') ? route.query.redirect : '/workspace'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = loginError(error)
  } finally {
    submitting.value = false
  }
}
</script>
