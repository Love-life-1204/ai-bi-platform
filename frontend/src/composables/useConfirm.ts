import { ElMessageBox } from 'element-plus'

export interface ConfirmOptions {
  title?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

export function useConfirm() {
  async function confirm(message: string, options: ConfirmOptions = {}): Promise<boolean> {
    try {
      await ElMessageBox.confirm(message, options.title ?? '请确认', {
        confirmButtonText: options.confirmText ?? '确定',
        cancelButtonText: options.cancelText ?? '取消',
        confirmButtonClass: options.danger ? 'el-button--danger' : '',
        type: options.danger ? 'warning' : 'info',
        autofocus: false,
      })
      return true
    } catch {
      return false
    }
  }

  return { confirm }
}
