<template>
  <div class="design-system-page">
    <section class="demo-card token-card">
      <div class="demo-card-heading">
        <div><span class="eyebrow">FOUNDATIONS</span><h2>视觉令牌</h2></div>
        <el-tag effect="plain">v1.0</el-tag>
      </div>
      <div class="color-grid">
        <div v-for="color in colors" :key="color.name" class="color-item">
          <span :style="{ background: color.value }" />
          <strong>{{ color.name }}</strong><small>{{ color.value }}</small>
        </div>
      </div>
      <div class="token-row">
        <span>字体：Inter / PingFang SC / Microsoft YaHei</span>
        <span>间距：4 / 8 / 12 / 16 / 24 / 32</span>
        <span>圆角：6 / 10 / 14 / 18</span>
      </div>
    </section>

    <section class="demo-card">
      <div class="demo-card-heading"><div><span class="eyebrow">DATA DISPLAY</span><h2>搜索、表格与分页</h2></div></div>
      <CommonSearch v-model="keyword" @search="noop" @reset="keyword = ''">
        <el-form-item><el-select placeholder="状态" style="width: 140px"><el-option label="全部" value="all" /></el-select></el-form-item>
        <template #actions><el-button :icon="Plus" @click="dialogOpen = true">新增</el-button></template>
      </CommonSearch>
      <CommonTable :rows="filteredRows" :columns="columns" selectable>
        <template #cell-status="{ value }"><el-tag type="success" effect="light">{{ value }}</el-tag></template>
        <template #actions><el-button link type="primary">查看</el-button><el-button link type="danger" @click="removeExample">删除</el-button></template>
      </CommonTable>
      <CommonPagination v-model:page="page" v-model:page-size="pageSize" :total="42" />
    </section>

    <section class="demo-card">
      <div class="demo-card-heading"><div><span class="eyebrow">FEEDBACK</span><h2>统一状态</h2></div></div>
      <el-segmented v-model="activeState" :options="stateOptions" />
      <UnifiedState :state="activeState" retryable action-text="返回列表" @retry="noop" />
    </section>

    <CommonDialog v-model="dialogOpen" title="新增示例" @confirm="dialogOpen = false">
      <CommonForm ref="form" :model="formModel">
        <el-form-item label="名称" prop="name"><el-input v-model="formModel.name" placeholder="请输入名称" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="formModel.description" type="textarea" :rows="3" /></el-form-item>
      </CommonForm>
    </CommonDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import CommonDialog from '../components/common/CommonDialog.vue'
import CommonForm from '../components/common/CommonForm.vue'
import CommonPagination from '../components/common/CommonPagination.vue'
import CommonSearch from '../components/common/CommonSearch.vue'
import CommonTable, { type TableColumn } from '../components/common/CommonTable.vue'
import UnifiedState, { type UnifiedStateType } from '../components/common/UnifiedState.vue'
import { useConfirm } from '../composables/useConfirm'

const colors = [
  { name: '主色', value: '#3156D3' }, { name: '辅助青', value: '#0F9F8F' },
  { name: '辅助紫', value: '#7456C8' }, { name: '背景', value: '#F4F6FA' },
  { name: '边框', value: '#DFE4EC' }, { name: '正文', value: '#172033' },
]
const columns: TableColumn[] = [
  { key: 'name', label: '名称', minWidth: 180 },
  { key: 'owner', label: '负责人', width: 140 },
  { key: 'updatedAt', label: '更新时间', width: 180 },
  { key: 'status', label: '状态', width: 110 },
]
const rows: Record<string, unknown>[] = [
  { id: 1, name: '销售经营看板', owner: '李明', updatedAt: '2026-10-11 18:30', status: '运行中' },
  { id: 2, name: '渠道转化分析', owner: '周颖', updatedAt: '2026-10-11 17:20', status: '运行中' },
]
const keyword = ref('')
const filteredRows = computed(() => rows.filter(row => String(row.name).includes(keyword.value)))
const page = ref(1)
const pageSize = ref(10)
const dialogOpen = ref(false)
const formModel = reactive<Record<string, unknown>>({ name: '', description: '' })
const activeState = ref<UnifiedStateType>('empty')
const stateOptions: Array<{ label: string; value: UnifiedStateType }> = [
  { label: 'Loading', value: 'loading' }, { label: 'Empty', value: 'empty' },
  { label: 'Error', value: 'error' }, { label: 'Forbidden', value: 'forbidden' },
  { label: 'Partial', value: 'partial' }, { label: 'Timeout', value: 'timeout' },
]
const { confirm } = useConfirm()

function noop(): void {}
async function removeExample(): Promise<void> {
  if (await confirm('删除后无法恢复，确定继续吗？', { title: '删除确认', danger: true, confirmText: '删除' })) {
    ElMessage.success('确认框交互正常')
  }
}
</script>
