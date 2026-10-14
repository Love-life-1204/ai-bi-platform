<template>
  <section class="advanced-page">
    <el-card shadow="never" class="advanced-config">
      <template #header><strong>预测试点配置</strong></template>
      <el-alert title="仅使用已授权的发布指标；预测区间是统计估计，不是业务承诺。" type="info" :closable="false" />
      <el-form label-position="top" @submit.prevent="runForecast">
        <div class="form-grid">
          <el-form-item label="指标版本 ID"><el-input v-model="metricVersionId" inputmode="numeric" /></el-form-item>
          <el-form-item label="预测点数"><el-input-number v-model="horizon" :min="1" :max="365" /></el-form-item>
          <el-form-item label="训练窗口"><el-input-number v-model="trainingSize" :min="3" :max="5000" /></el-form-item>
          <el-form-item label="回测窗口"><el-input-number v-model="validationSize" :min="1" :max="1000" /></el-form-item>
          <el-form-item label="季节长度（0 为关闭）"><el-input-number v-model="seasonLength" :min="0" :max="365" /></el-form-item>
          <el-form-item label="置信水平">
            <el-select v-model="confidenceLevel"><el-option label="90%" :value="0.9" /><el-option label="95%" :value="0.95" /><el-option label="99%" :value="0.99" /></el-select>
          </el-form-item>
        </div>
        <el-form-item label="时间序列 JSON">
          <el-input v-model="pointsJson" type="textarea" :rows="9" placeholder='[{"timestamp":"2026-01-01T00:00:00Z","value":100}]' />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading">运行回测与预测</el-button>
      </el-form>
    </el-card>

    <el-card v-if="result" shadow="never" class="advanced-result">
      <template #header><strong>预测与区间</strong><small>{{ result.model }}</small></template>
      <div class="metric-grid">
        <div><span>MAE</span><strong>{{ number(result.backtest.mae) }}</strong></div>
        <div><span>RMSE</span><strong>{{ number(result.backtest.rmse) }}</strong></div>
        <div><span>MAPE</span><strong>{{ result.backtest.mape == null ? '—' : `${number(result.backtest.mape)}%` }}</strong></div>
        <div><span>区间覆盖率</span><strong>{{ number(result.backtest.intervalCoverage * 100) }}%</strong></div>
      </div>
      <div ref="chartHost" class="forecast-chart" role="img" aria-label="历史值、预测值和预测区间上下界" />
      <el-table :data="result.forecast" stripe>
        <el-table-column prop="timestamp" label="时间" min-width="210" />
        <el-table-column label="预测值"><template #default="{ row }">{{ number(row.predicted) }}</template></el-table-column>
        <el-table-column label="下界"><template #default="{ row }">{{ number(row.lower) }}</template></el-table-column>
        <el-table-column label="上界"><template #default="{ row }">{{ number(row.upper) }}</template></el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import type { Id } from '../api/resource-id'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { nextTick, onUnmounted, ref } from 'vue'
import { analyticsApi } from '../api/runtime'
import type { ForecastResult, TimePoint } from '../analytics/types'

const metricVersionId = ref<Id>('')
const horizon = ref(7)
const trainingSize = ref(7)
const validationSize = ref(3)
const seasonLength = ref(0)
const confidenceLevel = ref(0.95)
const loading = ref(false)
const result = ref<ForecastResult>()
const chartHost = ref<HTMLElement>()
let chart: echarts.ECharts | undefined

echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const sampleStart = Date.parse('2026-09-01T00:00:00Z')
const pointsJson = ref(JSON.stringify(Array.from({ length: 14 }, (_, index) => ({
  timestamp: new Date(sampleStart + index * 86_400_000).toISOString(),
  value: 100 + index * 5,
})), null, 2))

function parsePoints(): TimePoint[] {
  const value: unknown = JSON.parse(pointsJson.value)
  if (!Array.isArray(value) || !value.length) throw new Error('时间序列必须是非空 JSON 数组')
  return value.map(item => {
    if (typeof item !== 'object' || item === null) throw new Error('每个时间点必须是对象')
    const point = item as Record<string, unknown>
    if (typeof point.timestamp !== 'string' || typeof point.value !== 'number' || !Number.isFinite(point.value)) {
      throw new Error('每个时间点必须包含 ISO timestamp 和有限数值 value')
    }
    return { timestamp: point.timestamp, value: point.value }
  })
}

async function runForecast(): Promise<void> {
  try {
    loading.value = true
    const points = parsePoints()
    result.value = await analyticsApi.forecast({
      metricVersionId: metricVersionId.value, points, horizon: horizon.value,
      seasonLength: seasonLength.value, trainingSize: trainingSize.value,
      validationSize: validationSize.value, confidenceLevel: confidenceLevel.value,
    })
    await nextTick()
    renderChart(points, result.value)
  } catch (error) {
    if (error instanceof SyntaxError || (error instanceof Error && error.message.includes('时间点'))) {
      ElMessage.error(error.message)
    }
  } finally {
    loading.value = false
  }
}

function renderChart(history: TimePoint[], forecast: ForecastResult): void {
  if (!chartHost.value) return
  chart ??= echarts.init(chartHost.value)
  const future = forecast.forecast
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['历史值', '预测值', '区间下界', '区间上界'] },
    grid: { left: 48, right: 24, top: 48, bottom: 52 },
    xAxis: { type: 'time' }, yAxis: { type: 'value', scale: true },
    series: [
      { name: '历史值', type: 'line', showSymbol: false, data: history.map(item => [item.timestamp, item.value]) },
      { name: '预测值', type: 'line', showSymbol: true, data: future.map(item => [item.timestamp, item.predicted]), lineStyle: { width: 3 } },
      { name: '区间下界', type: 'line', showSymbol: false, data: future.map(item => [item.timestamp, item.lower]), lineStyle: { type: 'dashed', opacity: 0.7 } },
      { name: '区间上界', type: 'line', showSymbol: false, data: future.map(item => [item.timestamp, item.upper]), lineStyle: { type: 'dashed', opacity: 0.7 } },
    ],
  })
}

function number(value: number): string { return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value) }
onUnmounted(() => chart?.dispose())
</script>

<style scoped>
.advanced-page { display: grid; gap: 20px; }
.advanced-config, .advanced-result { border-radius: 14px; }
.advanced-config :deep(.el-alert) { margin-bottom: 20px; }
.advanced-result :deep(.el-card__header) { display: flex; justify-content: space-between; align-items: center; }
.advanced-result small { color: var(--color-text-secondary, #64748b); }
.form-grid, .metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 16px; }
.metric-grid { margin-bottom: 20px; gap: 12px; }
.metric-grid div { padding: 16px; border: 1px solid #dfe4ec; border-radius: 10px; background: #f8fafc; }
.metric-grid span, .metric-grid strong { display: block; }
.metric-grid span { color: #64748b; font-size: 13px; }
.metric-grid strong { margin-top: 6px; font-size: 24px; }
.forecast-chart { width: 100%; height: 380px; margin-bottom: 20px; }
@media (max-width: 900px) { .form-grid, .metric-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 600px) { .form-grid, .metric-grid { grid-template-columns: 1fr; } }
</style>
