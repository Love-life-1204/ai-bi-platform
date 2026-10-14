import { createRouter, createWebHistory, type RouterHistory, type RouteRecordRaw } from 'vue-router'
import { useAppStore } from '../stores/app'
import { apiClient } from '../api/runtime'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginPlaceholderPage.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/workspace' },
      {
        path: 'workspace',
        name: 'workspace-home',
        component: () => import('../views/WorkspaceHomePage.vue'),
        meta: { title: '企业工作台', description: '查看当前工作空间的授权指标、最近看板与任务状态', section: '工作台' },
      },
      {
        path: 'datasources',
        name: 'datasources',
        component: () => import('../views/legacy/DatasourcePage.vue'),
        meta: { title: '数据源', description: '管理只读数据库连接与元数据采集', section: '数据接入' },
      },
      {
        path: 'sync',
        name: 'sync',
        component: () => import('../views/legacy/SyncPage.vue'),
        meta: { title: '数据同步', description: '任务配置、批次进度、质量门禁与失败补偿', section: '数据接入' },
      },
      {
        path: 'semantic',
        name: 'semantic',
        component: () => import('../views/legacy/SemanticPage.vue'),
        meta: { title: '数据集管理', description: '统一维护字段口径、关联、权限预览和数据集版本', section: '数据建模' },
      },
      {
        path: 'metrics',
        alias: '/semantic/metrics',
        name: 'metrics',
        component: () => import('../views/legacy/MetricPage.vue'),
        meta: { title: '指标管理', description: '配置受控业务公式、试算口径并发布不可变指标版本', section: '数据建模' },
      },
      {
        path: 'query',
        name: 'query',
        component: () => import('../views/legacy/QueryPage.vue'),
        meta: { title: '统一查询', description: '基于已发布数据集与指标执行受控查询', section: '分析中心' },
      },
      {
        path: 'charts',
        name: 'charts',
        component: () => import('../views/ChartDesignerPage.vue'),
        meta: { title: '图表设计器', description: '选择已发布指标，配置查询参数并实时预览图表版本', section: '分析中心' },
      },
      {
        path: 'dashboards',
        name: 'dashboards',
        component: () => import('../views/DashboardManagementPage.vue'),
        meta: { title: '看板管理', description: '编排可筛选、可联动且按成员权限查询的业务看板', section: '分析中心' },
      },
      {
        path: 'ai',
        name: 'ai-analysis',
        component: () => import('../views/AiAnalysisPage.vue'),
        meta: { title: 'AI 智能分析', description: '用自然语言分析已授权指标，并查看图表与查询证据', section: '分析中心' },
      },
      {
        path: 'advanced-analytics',
        name: 'advanced-analytics',
        component: () => import('../views/AdvancedAnalyticsPage.vue'),
        meta: { title: '高级分析', description: '异常检测、预测回测与预测区间', section: '分析中心' },
      },
      {
        path: 'organization/company',
        name: 'company-settings',
        component: () => import('../views/CompanySettingsPage.vue'),
        meta: { title: '企业信息', description: '维护企业名称、时区和默认币种', section: '组织管理' },
      },
      {
        path: 'organization/members',
        name: 'organization-members',
        component: () => import('../views/OrganizationPage.vue'),
        meta: { title: '组织与成员', description: '查看部门成员，邀请成员并分配角色权限', section: '组织管理' },
      },
      {
        path: 'commercial/reports',
        name: 'commercial-reports',
        component: () => import('../views/ReportsPage.vue'),
        meta: { title: '报告与交付', description: '管理授权报告、异步导出和定时订阅', section: '商业化' },
      },
      {
        path: 'commercial/alerts',
        name: 'commercial-alerts',
        component: () => import('../views/AlertsPage.vue'),
        meta: { title: '告警中心', description: '配置业务告警并跟踪确认、恢复与抑制事件', section: '商业化' },
      },
      {
        path: 'commercial/billing',
        name: 'commercial-billing',
        component: () => import('../views/BillingPage.vue'),
        meta: { title: '套餐与账单', description: '查看套餐、配额、用量及服务端确认的订单状态', section: '商业化' },
      },
      {
        path: 'organization/security',
        name: 'enterprise-security',
        component: () => import('../views/EnterpriseSecurityPage.vue'),
        meta: { title: '企业安全', description: '管理 MFA、OIDC SSO 和模型供应商数据策略', section: '组织管理' },
      },
      {
        path: 'platform/operations',
        name: 'platform-operations',
        component: () => import('../views/PlatformOperationsPage.vue'),
        meta: { title: '平台运营', description: '查看租户、资源、告警和服务健康汇总', section: '平台管理' },
      },
      {
        path: 'design-system',
        name: 'design-system',
        component: () => import('../views/DesignSystemPage.vue'),
        meta: { title: 'UI 规范', description: '公共组件、交互状态与视觉令牌', section: '系统' },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('../views/ForbiddenPage.vue'),
        meta: { title: '无权访问', description: '当前成员没有执行此操作的业务权限', section: '系统' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/workspace' },
]

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const appRouter = createRouter({
    history,
    routes,
    scrollBehavior: () => ({ top: 0 }),
  })

  appRouter.beforeEach(async (to, from) => {
    if (from.name && to.fullPath !== from.fullPath) apiClient.abortAll('页面已切换')
    const app = useAppStore()
    if (to.name === 'login') {
      return await app.ensureAuthenticated() ? { path: '/workspace', replace: true } : true
    }
    if (!to.meta.requiresAuth) return true
    if (await app.ensureAuthenticated()) return true
    return { name: 'login', query: { redirect: to.fullPath }, replace: true }
  })

  appRouter.afterEach(to => {
    const title = typeof to.meta.title === 'string' ? to.meta.title : '工作台'
    document.title = `${title} · AI BI`
  })
  return appRouter
}

export const router = createAppRouter()
