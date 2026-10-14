<template>
  <aside
    class="app-sidebar"
    :class="{ 'is-collapsed': collapsed && !mobileOpen, 'is-mobile-open': mobileOpen }"
    aria-label="主导航"
  >
    <div class="brand">
      <div class="brand-mark" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <div v-if="!collapsed || mobileOpen" class="brand-copy">
        <strong>AI BI</strong>
        <small>智能数据分析</small>
      </div>
    </div>

    <el-scrollbar class="sidebar-scroll">
      <el-menu
        :default-active="route.path"
        :collapse="collapsed && !mobileOpen"
        :collapse-transition="false"
        router
        class="sidebar-menu"
        @select="$emit('navigate')"
      >
        <template v-for="group in groups" :key="group.label">
          <li v-if="!collapsed || mobileOpen" class="nav-group-label">{{ group.label }}</li>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.label }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>

    <button class="sidebar-collapse" type="button" :aria-label="collapsed ? '展开导航' : '收起导航'" @click="$emit('toggle')">
      <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
      <span v-if="!collapsed || mobileOpen">收起导航</span>
    </button>
  </aside>
</template>

<script setup lang="ts">
import { markRaw } from 'vue'
import { useRoute } from 'vue-router'
import {
  Bell, ChatDotRound, Connection, CreditCard, DataAnalysis, DataLine, Document, Expand, Fold, Histogram, HomeFilled, Lock, Monitor, OfficeBuilding,
  Operation, Setting, UserFilled,
} from '@element-plus/icons-vue'

defineProps<{ collapsed: boolean; mobileOpen: boolean }>()
defineEmits<{ toggle: []; navigate: [] }>()

const route = useRoute()
const advancedAnalyticsItems = import.meta.env.VITE_ANALYTICS_ENABLED === 'true'
  ? [{ path: '/advanced-analytics', label: '高级分析', icon: markRaw(DataLine) }]
  : []
const groups = [
  {
    label: '工作台',
    items: [{ path: '/workspace', label: '企业工作台', icon: markRaw(HomeFilled) }],
  },
  {
    label: '数据接入',
    items: [
      { path: '/datasources', label: '数据源', icon: markRaw(Connection) },
      { path: '/sync', label: '数据同步', icon: markRaw(Operation) },
    ],
  },
  {
    label: '数据建模',
    items: [
      { path: '/semantic', label: '数据集管理', icon: markRaw(DataLine) },
      { path: '/metrics', label: '指标管理', icon: markRaw(Histogram) },
    ],
  },
  {
    label: '分析中心',
    items: [
      { path: '/query', label: '统一查询', icon: markRaw(DataAnalysis) },
      { path: '/charts', label: '图表设计器', icon: markRaw(Histogram) },
      { path: '/dashboards', label: '看板管理', icon: markRaw(DataAnalysis) },
      { path: '/ai', label: 'AI 智能分析', icon: markRaw(ChatDotRound) },
      ...advancedAnalyticsItems,
    ],
  },
  {
    label: '组织管理',
    items: [
      { path: '/organization/company', label: '企业信息', icon: markRaw(OfficeBuilding) },
      { path: '/organization/members', label: '部门与成员', icon: markRaw(UserFilled) },
      { path: '/organization/security', label: '企业安全', icon: markRaw(Lock) },
    ],
  },
  {
    label: '商业化',
    items: [
      { path: '/commercial/reports', label: '报告与交付', icon: markRaw(Document) },
      { path: '/commercial/alerts', label: '告警中心', icon: markRaw(Bell) },
      { path: '/commercial/billing', label: '套餐与账单', icon: markRaw(CreditCard) },
    ],
  },
  {
    label: '平台管理',
    items: [{ path: '/platform/operations', label: '平台运营', icon: markRaw(Monitor) }],
  },
  {
    label: '系统',
    items: [{ path: '/design-system', label: 'UI 规范', icon: markRaw(Setting) }],
  },
]
</script>
