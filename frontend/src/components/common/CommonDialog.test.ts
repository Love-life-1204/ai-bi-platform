// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'
import CommonDialog from './CommonDialog.vue'

const DialogStub = defineComponent({
  template: '<section><slot /><footer><slot name="footer" /></footer></section>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
})

describe('CommonDialog', () => {
  it('supports Ctrl/Cmd+Enter confirmation and blocks it while pending', async () => {
    const wrapper = mount(CommonDialog, {
      props: { modelValue: true, title: '测试对话框' },
      slots: { default: '<input aria-label="名称" />' },
      global: { stubs: { ElDialog: DialogStub, ElButton: ButtonStub } },
    })

    await wrapper.get('.common-dialog-content').trigger('keydown', { key: 'Enter', ctrlKey: true })
    expect(wrapper.emitted('confirm')).toHaveLength(1)

    await wrapper.setProps({ confirmLoading: true })
    await wrapper.get('.common-dialog-content').trigger('keydown', { key: 'Enter', metaKey: true })
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })
})
