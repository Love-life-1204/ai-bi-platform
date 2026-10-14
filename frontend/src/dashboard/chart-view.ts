import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
import type { QueryResult } from '../query/types'
import type { ChartSpec } from './types'
import { validateChartBinding } from './types'
import { boundedPreviewRows, buildPivotMatrix, numericValue } from './chart-preview'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent,
  TitleComponent, TooltipComponent, CanvasRenderer])

export interface ChartViewActions {
  onFilter?: (field: string, value: unknown) => void
  onDrill?: (field: string, value: unknown, row: Record<string, unknown>) => void
  onRetry?: () => void
}

export class ChartView {
  private instance: echarts.ECharts | undefined
  private resizeObserver: ResizeObserver | undefined

  constructor(private readonly root: HTMLElement, private readonly actions: ChartViewActions = {}) {}

  loading(): void {
    this.disposeChart()
    this.root.replaceChildren(this.state('loading', '正在加载图表…'))
  }

  error(message: string, retryable = false): void {
    this.disposeChart()
    const state = this.state('error', message)
    if (retryable && this.actions.onRetry) {
      const retry = document.createElement('button')
      retry.type = 'button'; retry.className = 'button ghost'; retry.textContent = '重试'
      retry.addEventListener('click', this.actions.onRetry)
      state.append(retry)
    }
    this.root.replaceChildren(state)
  }

  render(spec: ChartSpec, result: QueryResult): void {
    this.disposeChart()
    if (result.rows.length === 0) {
      this.root.replaceChildren(this.state('empty', spec.emptyText ?? '当前筛选下暂无数据'))
      return
    }
    const binding = validateChartBinding(spec, result.columns)
    if (!binding.valid) {
      this.error(binding.errors.join('；'))
      return
    }
    if (spec.type === 'KPI') this.renderKpi(spec, result)
    else if (spec.type === 'TABLE') this.renderTable(spec, result)
    else if (spec.type === 'PIVOT_TABLE') this.renderPivot(spec, result)
    else this.renderEcharts(spec, result)
  }

  destroy(): void { this.disposeChart() }

  private renderKpi(spec: ChartSpec, result: QueryResult): void {
    const field = spec.valueFields[0]!
    const value = result.rows[0]?.[field]
    const card = document.createElement('div'); card.className = 'chart-kpi'
    const label = document.createElement('span'); label.textContent = spec.title ?? this.label(result, field)
    const number = document.createElement('strong'); number.textContent = this.format(value, spec.valueFormat, spec.decimalPlaces, spec.unit)
    card.append(label, number)
    this.root.replaceChildren(card)
  }

  private renderTable(spec: ChartSpec, result: QueryResult): void {
    const fields = spec.detailColumns ?? result.columns.map(column => column.key)
    const wrapper = document.createElement('div'); wrapper.className = 'chart-table-wrap'
    const table = document.createElement('table'); table.className = 'ds-table chart-table'
    const head = table.createTHead().insertRow()
    fields.forEach(field => {
      const cell = document.createElement('th'); cell.textContent = this.label(result, field); head.append(cell)
    })
    const body = table.createTBody()
    const bounded = boundedPreviewRows(result.rows)
    bounded.rows.forEach(value => {
      const row = body.insertRow()
      fields.forEach(field => { const cell = row.insertCell(); cell.textContent = String(value[field] ?? '') })
      const drillField = spec.drill?.dimensionField
      if (spec.drill?.enabled && drillField) {
        row.tabIndex = 0; row.className = 'drillable'
        row.addEventListener('click', () => this.actions.onDrill?.(drillField, value[drillField], value))
      }
    })
    wrapper.append(table)
    if (bounded.truncated) wrapper.prepend(this.limitNotice(result.rows.length))
    this.root.replaceChildren(wrapper)
  }

  private renderPivot(spec: ChartSpec, result: QueryResult): void {
    const rowField = spec.categoryField!
    const columnField = spec.seriesNameField!
    const valueField = spec.valueFields[0]!
    const bounded = boundedPreviewRows(result.rows)
    const matrix = buildPivotMatrix(bounded.rows, rowField, columnField, valueField)
    const wrapper = document.createElement('div'); wrapper.className = 'chart-table-wrap'
    const table = document.createElement('table'); table.className = 'ds-table chart-table pivot-table'
    const head = table.createTHead().insertRow()
    const corner = document.createElement('th'); corner.textContent = this.label(result, rowField); head.append(corner)
    matrix.columns.forEach(column => { const cell = document.createElement('th'); cell.textContent = column; head.append(cell) })
    const body = table.createTBody()
    matrix.rows.forEach(item => {
      const row = body.insertRow(); const label = row.insertCell(); label.textContent = item.key
      matrix.columns.forEach(column => {
        const cell = row.insertCell()
        cell.textContent = this.format(item.values[column], spec.valueFormat, spec.decimalPlaces, spec.unit)
      })
    })
    wrapper.append(table)
    if (bounded.truncated) wrapper.prepend(this.limitNotice(result.rows.length))
    this.root.replaceChildren(wrapper)
  }

  private renderEcharts(spec: ChartSpec, result: QueryResult): void {
    const canvas = document.createElement('div'); canvas.className = 'chart-canvas'
    if (result.rows.length > 1_000) {
      const shell = document.createElement('div'); shell.className = 'chart-echarts-shell'
      shell.append(this.limitNotice(result.rows.length), canvas)
      this.root.replaceChildren(shell)
    } else {
      this.root.replaceChildren(canvas)
    }
    this.instance = echarts.init(canvas, undefined, { renderer: 'canvas' })
    this.instance.setOption(this.option(spec, result), true)
    this.instance.on('click', (params: { name?: string }) => {
      if (spec.categoryField && params.name !== undefined) {
        this.actions.onFilter?.(spec.categoryField, params.name)
        if (spec.drill?.enabled) {
          const row = result.rows.find(value => String(value[spec.categoryField!]) === params.name)
          if (row) this.actions.onDrill?.(spec.drill.dimensionField, params.name, row)
        }
      }
    })
    this.resizeObserver = new ResizeObserver(() => this.instance?.resize())
    this.resizeObserver.observe(canvas)
  }

  private option(spec: ChartSpec, result: QueryResult): EChartsCoreOption {
    const category = spec.categoryField!
    const bounded = boundedPreviewRows(result.rows)
    const rows = bounded.rows
    const categories = rows.map(row => String(row[category] ?? ''))
    const common = {
      animationDuration: 250,
      title: spec.title ? { text: spec.title, left: 8, top: 4, textStyle: { fontSize: 14 } } : undefined,
      tooltip: { trigger: spec.type === 'PIE' ? 'item' : 'axis' },
      legend: spec.legend === false ? undefined : { bottom: 0 },
    }
    if (spec.type === 'PIE') {
      const field = spec.valueFields[0]!
      return {
        ...common,
        series: [{
          type: 'pie', radius: ['35%', '68%'],
          label: { show: spec.labels !== false },
          data: rows.map(row => ({ name: String(row[category] ?? ''), value: this.number(row[field]) })),
        }],
      }
    }
    return {
      ...common,
      grid: { left: 52, right: 24, top: spec.title ? 48 : 24, bottom: 52, containLabel: true },
      xAxis: { type: 'category', name: spec.xAxisTitle || undefined, data: categories, axisLabel: { hideOverlap: true } },
      yAxis: { type: 'value', name: spec.yAxisTitle || spec.unit || undefined },
      series: spec.valueFields.map(field => ({
        name: this.label(result, field),
        type: spec.type === 'LINE' ? 'line' : 'bar',
        stack: spec.type === 'STACKED_BAR' ? 'total' : undefined,
        smooth: spec.type === 'LINE',
        emphasis: { focus: 'series' },
        data: rows.map(row => this.number(row[field])),
      })),
    }
  }

  private label(result: QueryResult, key: string): string {
    return result.columns.find(column => column.key === key)?.label ?? key
  }

  private number(value: unknown): number | null {
    return numericValue(value)
  }

  private format(value: unknown, format: ChartSpec['valueFormat'], decimalPlaces = 2, unit = ''): string {
    const numeric = this.number(value)
    if (numeric === null) return '—'
    if (format === 'CURRENCY') return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: unit || 'CNY', minimumFractionDigits: decimalPlaces, maximumFractionDigits: decimalPlaces }).format(numeric)
    if (format === 'PERCENT') return new Intl.NumberFormat('zh-CN', { style: 'percent', minimumFractionDigits: decimalPlaces, maximumFractionDigits: decimalPlaces }).format(numeric)
    return `${new Intl.NumberFormat('zh-CN', { minimumFractionDigits: decimalPlaces, maximumFractionDigits: decimalPlaces }).format(numeric)}${unit ? ` ${unit}` : ''}`
  }

  private limitNotice(total: number): HTMLElement {
    const note = document.createElement('div'); note.className = 'chart-limit-notice'
    note.textContent = `结果共 ${total.toLocaleString('zh-CN')} 行，预览已均匀抽样至 1,000 行。保存的查询限制不受影响。`
    return note
  }

  private state(kind: string, message: string): HTMLElement {
    const state = document.createElement('div'); state.className = `chart-state ${kind}`
    const text = document.createElement('span'); text.textContent = message; state.append(text)
    return state
  }

  private disposeChart(): void {
    this.resizeObserver?.disconnect(); this.resizeObserver = undefined
    this.instance?.dispose(); this.instance = undefined
  }
}
