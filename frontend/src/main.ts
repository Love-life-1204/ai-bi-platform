import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert, ElAvatar, ElBadge, ElBreadcrumb, ElBreadcrumbItem, ElButton, ElButtonGroup, ElCard, ElCheckbox,
  ElCollapse, ElCollapseItem, ElDatePicker, ElDialog, ElDrawer, ElDropdown, ElDropdownItem, ElDropdownMenu,
  ElForm, ElFormItem, ElIcon, ElInput, ElInputNumber, ElLoading, ElMenu, ElMenuItem, ElOption, ElOptionGroup,
  ElPagination, ElPopover, ElProgress, ElRadioButton, ElRadioGroup, ElScrollbar, ElSegmented, ElSelect, ElSlider,
  ElSwitch, ElTable, ElTableColumn, ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { configureApiSession } from './api/runtime'
import { useAppStore } from './stores/app'
import './datasources/datasources.css'
import './styles/index.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
;[
  ElAlert, ElAvatar, ElBadge, ElBreadcrumb, ElBreadcrumbItem, ElButton, ElButtonGroup, ElCard, ElCheckbox,
  ElCollapse, ElCollapseItem, ElDatePicker, ElDialog, ElDrawer, ElDropdown, ElDropdownItem, ElDropdownMenu,
  ElForm, ElFormItem, ElIcon, ElInput, ElInputNumber, ElMenu, ElMenuItem, ElOption, ElOptionGroup,
  ElPagination, ElPopover, ElProgress, ElRadioButton, ElRadioGroup, ElScrollbar, ElSegmented, ElSelect, ElSlider,
  ElSwitch, ElTable, ElTableColumn, ElTag,
].forEach(component => app.use(component))
app.use(ElLoading)

const session = useAppStore(pinia)
configureApiSession({
  refresh: () => session.refreshSession(),
  expired: () => {
    session.clearSession()
    if (router.currentRoute.value.name !== 'login') {
      void router.replace({ name: 'login', query: { reason: 'expired', redirect: router.currentRoute.value.fullPath } })
    }
  },
  forbidden: () => {
    if (router.currentRoute.value.name !== 'forbidden') void router.push({ name: 'forbidden' })
  },
})
app.mount('#app')
