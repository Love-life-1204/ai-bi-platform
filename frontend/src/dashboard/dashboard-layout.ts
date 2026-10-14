import type { QueryResult } from '../query/types'
import type { ChartSpec, DashboardWidget, GridPosition, WidgetResult } from './types'

export const DASHBOARD_COLUMNS = 12

export function clampPosition(position: GridPosition): GridPosition {
  const w = Math.min(DASHBOARD_COLUMNS, Math.max(2, Math.round(position.w)))
  const h = Math.max(2, Math.round(position.h))
  return {
    x: Math.min(DASHBOARD_COLUMNS - w, Math.max(0, Math.round(position.x))),
    y: Math.max(0, Math.round(position.y)),
    w,
    h,
  }
}

export function movePosition(position: GridPosition, x: number, y: number): GridPosition {
  return clampPosition({ ...position, x, y })
}

export function resizePosition(
  position: GridPosition,
  widthDelta: number,
  heightDelta: number,
): GridPosition {
  return clampPosition({ ...position, w: position.w + widthDelta, h: position.h + heightDelta })
}

export function nextPosition(widgets: DashboardWidget[]): GridPosition {
  const bottom = widgets.reduce((value, widget) => Math.max(
    value,
    widget.position.y + widget.position.h,
  ), 0)
  return { x: 0, y: bottom, w: 6, h: 4 }
}

export interface ResolvedWidgetState {
  loading: false
  error: string
  retryable: boolean
  result?: QueryResult
  spec?: ChartSpec
}

export function resolveWidgetResult(value?: WidgetResult): ResolvedWidgetState {
  if (value?.status === 'SUCCEEDED' && value.result && value.chartSpec) {
    return { loading: false, error: '', retryable: false, result: value.result, spec: value.chartSpec }
  }
  return {
    loading: false,
    error: value?.error?.message ?? '图表未返回结果',
    retryable: value?.error?.retryable ?? false,
    ...(value?.chartSpec ? { spec: value.chartSpec } : {}),
  }
}
