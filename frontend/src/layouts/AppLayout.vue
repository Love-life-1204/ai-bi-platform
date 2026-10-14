<template>
  <div class="admin-layout" :class="{ 'sidebar-is-collapsed': app.sidebarCollapsed }">
    <AppSidebar
      :collapsed="app.sidebarCollapsed"
      :mobile-open="app.mobileSidebarOpen"
      @toggle="app.toggleSidebar"
      @navigate="app.setMobileSidebar(false)"
    />
    <button
      v-if="app.mobileSidebarOpen"
      class="sidebar-scrim"
      type="button"
      aria-label="关闭导航"
      @click="app.setMobileSidebar(false)"
    />
    <div class="layout-main">
      <AppTopbar
        :tenant-id="app.tenantId"
        :tenants="app.tenants"
        :workspace-id="app.workspaceId"
        :workspaces="app.workspaces"
        :user-name="app.userName"
        :user-email="app.userEmail"
        :user-initial="app.userInitial"
        :notification-count="app.notificationCount"
        @open-menu="app.setMobileSidebar(true)"
        @switch-tenant="switchTenant"
        @switch-workspace="switchWorkspace"
        @user-command="handleUserCommand"
      />
      <main class="layout-content" aria-live="polite">
        <PageHeader :title="pageTitle" :description="pageDescription" :section="pageSection" />
        <RouterView v-slot="{ Component }">
          <Transition name="page-fade" mode="out-in">
            <component :is="Component" :key="`${route.fullPath}:${app.tenantId}:${app.workspaceId}:${app.contextVersion}`" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Id } from '../api/resource-id'
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppSidebar from '../components/layout/AppSidebar.vue'
import AppTopbar from '../components/layout/AppTopbar.vue'
import PageHeader from '../components/layout/PageHeader.vue'
import { useAppStore } from '../stores/app'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const pageTitle = computed(() => String(route.meta.title ?? '工作台'))
const pageDescription = computed(() => String(route.meta.description ?? ''))
const pageSection = computed(() => String(route.meta.section ?? ''))

async function switchTenant(id: string): Promise<void> {
  if (id === app.tenantId) return
  try {
    await app.switchTenant(id)
    await router.replace('/workspace')
    ElMessage.success('企业上下文已安全切换')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '企业切换失败')
  }
}

async function switchWorkspace(id: Id): Promise<void> {
  try {
    app.switchWorkspace(id)
    await router.replace('/workspace')
    ElMessage.success('工作空间已切换')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工作空间切换失败')
  }
}

async function handleUserCommand(command: string): Promise<void> {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', {
        confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning',
      })
      app.clearSession()
      await router.push('/login')
    } catch {
      // User cancelled.
    }
    return
  }
  ElMessage.info(command === 'profile' ? '个人资料功能将在身份模块接入' : '偏好设置功能正在建设中')
}
</script>
