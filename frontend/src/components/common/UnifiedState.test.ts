// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'
import UnifiedState from './UnifiedState.vue'

describe('UnifiedState', () => {
  it.each([
    ['loading', '正在加载'],
    ['empty', '暂无数据'],
    ['error', '加载失败'],
    ['forbidden', '无权访问'],
    ['partial', '部分数据不可用'],
    ['timeout', '请求超时'],
  ] as const)('renders %s state consistently', (state, title) => {
    const wrapper = mount(UnifiedState, {
      props: { state },
      global: { stubs: { ElIcon: true, ElButton: true } },
    })
    expect(wrapper.text()).toContain(title)
    expect(wrapper.classes()).toContain(`state-${state}`)
  })

  it('emits retry from the shared action', async () => {
    const ButtonStub = defineComponent({
      emits: ['click'],
      template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
    })
    const wrapper = mount(UnifiedState, {
      props: { state: 'error', retryable: true },
      global: { stubs: { ElIcon: true, ElButton: ButtonStub } },
    })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
