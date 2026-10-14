# AI BI 前端 UI 规范 V1.0

更新日期：2026-10-11  
适用范围：企业后台、数据建模、查询、图表看板及后续 AI 分析页面。

## 1. 视觉方向

整体采用“深色导航 + 浅色工作区”的企业数据产品风格。界面强调信息密度、可扫描性和长时间使用舒适度，不使用装饰性大面积渐变。

| 类别 | 令牌 | 色值 | 用途 |
|---|---|---|---|
| 主色 | `--color-primary` | `#3156D3` | 主按钮、选中导航、链接、焦点 |
| 主色悬停 | `--color-primary-hover` | `#2748BA` | 主交互悬停 |
| 主色浅底 | `--color-primary-soft` | `#EDF1FF` | 选中背景、辅助按钮 |
| 辅助青 | `--color-accent-teal` | `#0F9F8F` | 成功趋势、数据接入 |
| 辅助紫 | `--color-accent-violet` | `#7456C8` | AI、用户标识、次级强调 |
| 页面背景 | `--color-bg` | `#F4F6FA` | 主内容区 |
| 内容表面 | `--color-surface` | `#FFFFFF` | 卡片、表格、弹窗 |
| 侧栏 | `--color-sidebar` | `#121A2D` | 主导航背景 |
| 正文 | `--color-text` | `#172033` | 标题和正文 |
| 次级文字 | `--color-text-secondary` | `#647089` | 描述、辅助信息 |
| 边框 | `--color-border` | `#DFE4EC` | 表格、卡片、输入框 |
| 危险 | `--color-danger` | `#C1372B` | 删除、错误、超时 |

阴影分为 `xs/sm/md` 三档，仅用于层级表达；普通表格和卡片默认使用 `xs`，浮层及关键表单使用 `sm/md`。

## 2. 字体与尺寸

- 字体族：`Inter, PingFang SC, Microsoft YaHei, system-ui`。
- 正文基准：14px / 1.5。
- 辅助说明：11–13px。
- 小标题：16–19px。
- 页面标题：24–30px，使用响应式 `clamp()`。
- 数字指标使用等宽数字特性，代码/JSON 使用系统等宽字体。

## 3. 间距、圆角与断点

- 间距序列：4、8、12、16、24、32px。
- 圆角：小控件 6px、普通容器 10px、卡片 14px、强调容器 18px。
- 桌面侧栏：248px；折叠：76px；顶部栏：64px。
- 响应式断点：`<640px` 手机、`640–899px` 平板、`900–1199px` 小桌面、`≥1200px` 标准桌面、内容最大宽度 1440px。
- `<900px` 时侧栏改为遮罩抽屉；`<640px` 时用户详情收起、搜索栏和分页纵向排列。

## 4. 页面结构

所有受保护页面统一使用 `AppLayout`：

1. `AppSidebar`：分组导航、路由高亮、桌面折叠、移动端抽屉。
2. `AppTopbar`：企业选择、通知、用户头像、个人入口和退出确认。
3. `PageHeader`：面包屑、页面标题、说明、可选返回按钮和操作插槽。
4. `layout-content`：路由内容区，最大宽度 1440px。

页面不得自行重新实现侧栏、顶部栏、面包屑或页面级间距。

## 5. 公共组件

| 组件 | 文件 | 约束 |
|---|---|---|
| `CommonTable` | `src/components/common/CommonTable.vue` | 统一表头、空值、选择列、操作列和自定义单元格 |
| `CommonPagination` | `src/components/common/CommonPagination.vue` | 统一总数、页码、每页数量和跳页 |
| `CommonSearch` | `src/components/common/CommonSearch.vue` | 统一关键词、筛选插槽、查询和重置 |
| `CommonForm` | `src/components/common/CommonForm.vue` | 统一 Element Plus 表单布局、验证和操作区 |
| `CommonDialog` | `src/components/common/CommonDialog.vue` | 禁止误点遮罩关闭，统一确认/取消和 Loading |
| `useConfirm` | `src/composables/useConfirm.ts` | 普通/危险确认框，统一文案和返回值 |
| `UnifiedState` | `src/components/common/UnifiedState.vue` | `loading/empty/error/forbidden/partial/timeout` 六类状态 |

公共组件交互可在 `/design-system` 路由集中查看。新页面优先组合这些组件，仅在业务差异明确时新增扩展组件。

## 6. 可访问性与交互规则

- 键盘焦点必须可见；图标按钮必须提供 `aria-label`。
- 用户开启“减少动态效果”时关闭非必要过渡和动画。
- 删除、退出等不可逆或敏感操作必须二次确认。
- 加载失败不能造成整页白屏；能局部展示时使用 `partial`，权限失败使用 `forbidden`，网络超时使用 `timeout`。
- 移动端不依赖 hover；表格允许横向滚动，操作按钮保留可点击尺寸。
