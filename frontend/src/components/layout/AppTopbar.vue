<template>
  <header class="app-topbar">
    <div class="topbar-leading">
      <el-button class="mobile-menu-button" text circle aria-label="打开导航" @click="$emit('open-menu')">
        <el-icon :size="20"><Menu /></el-icon>
      </el-button>
      <div class="tenant-switcher">
        <span class="topbar-label">当前企业</span>
        <el-select
          :model-value="tenantId"
          :loading="switchingTenant"
          :disabled="switchingTenant"
          aria-label="切换企业"
          class="tenant-select"
          @change="switchTenant"
        >
          <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.name" :value="tenant.id" />
        </el-select>
        <span class="topbar-divider" aria-hidden="true" />
        <span class="topbar-label">工作空间</span>
        <el-select
          :model-value="workspaceId"
          :disabled="switchingTenant || workspaces.length === 0"
          aria-label="切换工作空间"
          class="workspace-select"
          @change="switchWorkspace"
        >
          <el-option
            v-for="workspace in workspaces"
            :key="workspace.id"
            :label="workspace.workspaceName"
            :value="workspace.id"
          />
        </el-select>
      </div>
    </div>

    <div class="topbar-actions">
      <el-popover placement="bottom-end" :width="320" trigger="click">
        <template #reference>
          <el-badge :value="notificationCount" :hidden="notificationCount === 0" class="notification-badge">
            <el-button text circle aria-label="通知">
              <el-icon :size="19"><Bell /></el-icon>
            </el-button>
          </el-badge>
        </template>
        <div class="notification-panel">
          <strong>通知中心</strong>
          <p>暂无新通知</p>
        </div>
      </el-popover>

      <el-dropdown trigger="click" @command="handleUserCommand">
        <button class="user-trigger" type="button" aria-label="用户菜单">
          <el-avatar :size="34" class="user-avatar">{{ userInitial }}</el-avatar>
          <span class="user-summary">
            <strong>{{ userName }}</strong>
            <small>{{ userEmail }}</small>
          </span>
          <el-icon><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile"><el-icon><User /></el-icon>个人资料</el-dropdown-item>
            <el-dropdown-item command="preferences"><el-icon><Setting /></el-icon>偏好设置</el-dropdown-item>
            <el-dropdown-item command="logout" divided><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { resourceId } from '../../api/resource-id'
import type { Id } from '../../api/resource-id'
import { ArrowDown, Bell, Menu, Setting, SwitchButton, User } from '@element-plus/icons-vue'
import type { TenantOption } from '../../stores/app'
import type { WorkspaceData } from '../../auth/types'

defineProps<{
  tenantId: string
  tenants: TenantOption[]
  workspaceId: Id
  workspaces: WorkspaceData[]
  userName: string
  userEmail: string
  userInitial: string
  notificationCount: number
  switchingTenant?: boolean
}>()

const emit = defineEmits<{
  'open-menu': []
  'switch-tenant': [id: string]
  'switch-workspace': [id: Id]
  'user-command': [command: string]
}>()

function switchTenant(value: string | number): void {
  emit('switch-tenant', resourceId(value))
}

function switchWorkspace(value: string | number): void {
  emit('switch-workspace', resourceId(value))
}

function handleUserCommand(command: string | number): void {
  emit('user-command', String(command))
}
</script>
