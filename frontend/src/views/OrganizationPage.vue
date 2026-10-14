<template>
  <div class="organization-shell">
    <section class="organization-summary compact-summary">
      <div><span class="eyebrow">ORGANIZATION</span><h2>组织与成员</h2><p>{{ members.length }} 名成员 · {{ departmentCount }} 个部门 · {{ roles.length }} 个角色</p></div>
      <div class="organization-actions"><el-button @click="load">刷新</el-button><el-button type="primary" :icon="Plus" @click="inviteOpen = true">邀请成员</el-button></div>
    </section>

    <UnifiedState v-if="loading" state="loading" description="正在加载部门、成员和授权状态。" />
    <UnifiedState v-else-if="errorMessage && !members.length" state="error" :description="errorMessage" retryable @retry="load" />
    <template v-else>
      <el-alert v-if="errorMessage" class="workspace-partial" type="warning" :title="errorMessage" show-icon :closable="false" />
      <div class="organization-grid">
        <section class="workbench-card department-card">
          <header><div><span class="eyebrow">DEPARTMENTS</span><h3>部门结构</h3></div><el-tag effect="plain">{{ departmentCount }}</el-tag></header>
          <div v-if="flatDepartments.length" class="department-tree">
            <div v-for="department in flatDepartments" :key="department.id" class="department-node" :style="{ paddingLeft: `${14 + department.level * 22}px` }">
              <el-icon><Folder /></el-icon><span><strong>{{ department.departmentName }}</strong><small>{{ department.departmentCode }}</small></span>
            </div>
          </div>
          <UnifiedState v-else state="empty" title="尚未建立部门" description="部门接口已接入，创建部门后会在这里显示层级结构。" />
        </section>

        <section class="workbench-card member-card">
          <header><div><span class="eyebrow">MEMBERS</span><h3>企业成员</h3></div><el-input v-model="keyword" clearable placeholder="搜索成员" style="width: 220px" /></header>
          <CommonTable :rows="memberRows" :columns="columns" :loading="loadingRoles" :actions-width="150">
            <template #cell-memberName="{ row }">
              <div class="member-identity"><span>{{ String(row.memberName).slice(0, 1) }}</span><div><strong>{{ row.memberName }}</strong><small>成员 ID {{ row.id }}</small></div></div>
            </template>
            <template #cell-roleSummary="{ value }"><span class="role-summary">{{ value }}</span></template>
            <template #cell-memberStatus="{ row }"><el-tag :type="row.memberStatus === 'ACTIVE' ? 'success' : 'info'" effect="light" size="small">{{ row.memberStatus === 'ACTIVE' ? '已授权' : '已停用' }}</el-tag></template>
            <template #actions="{ row }"><el-button link type="primary" :disabled="row.memberStatus !== 'ACTIVE'" @click="openRoleDialog(row)">分配角色</el-button></template>
          </CommonTable>
        </section>
      </div>
    </template>

    <CommonDialog v-model="inviteOpen" title="邀请企业成员" :confirm-loading="submittingInvite" confirm-text="创建邀请" @confirm="submitInvite">
      <el-form label-position="top">
        <el-form-item label="邮箱（必填）"><el-input v-model.trim="inviteForm.email" placeholder="name@company.com" /></el-form-item>
        <div class="dialog-grid">
          <el-form-item label="初始角色"><el-select v-model="inviteForm.roleId" clearable placeholder="暂不分配"><el-option v-for="role in activeRoles" :key="role.id" :label="role.roleName" :value="role.id" /></el-select></el-form-item>
          <el-form-item label="工作空间"><el-select v-model="inviteForm.workspaceId" clearable placeholder="企业范围"><el-option v-for="workspace in app.workspaces" :key="workspace.id" :label="workspace.workspaceName" :value="workspace.id" /></el-select></el-form-item>
          <el-form-item label="有效时长"><el-select v-model="inviteForm.expiresInHours"><el-option label="24 小时" :value="24" /><el-option label="72 小时" :value="72" /><el-option label="7 天" :value="168" /></el-select></el-form-item>
        </div>
      </el-form>
    </CommonDialog>

    <CommonDialog v-model="roleOpen" title="分配成员角色" :confirm-loading="assigningRole" confirm-text="确认授权" @confirm="assignRole">
      <p class="dialog-intro">为 <strong>{{ selectedMember?.memberName }}</strong> 分配角色。企业范围角色适用于全部已授权工作空间。</p>
      <el-form label-position="top">
        <el-form-item label="角色"><el-select v-model="roleForm.roleId" placeholder="请选择角色"><el-option v-for="role in activeRoles" :key="role.id" :label="`${role.roleName}${role.builtin ? ' · 内置' : ''}`" :value="role.id" /></el-select></el-form-item>
        <el-form-item label="授权范围"><el-select v-model="roleForm.workspaceId" clearable placeholder="企业范围"><el-option v-for="workspace in app.workspaces" :key="workspace.id" :label="workspace.workspaceName" :value="workspace.id" /></el-select></el-form-item>
      </el-form>
    </CommonDialog>

    <CommonDialog v-model="tokenOpen" title="邀请已创建" confirm-text="完成" @confirm="tokenOpen = false">
      <el-alert title="邀请令牌仅显示这一次，请通过安全渠道发送给受邀人。" type="success" show-icon :closable="false" />
      <div class="invite-token"><code>{{ createdInvitation?.inviteToken }}</code><el-button @click="copyToken">复制令牌</el-button></div>
      <p class="token-expiry">有效期至 {{ createdInvitation ? formatDate(createdInvitation.expiresAt) : '—' }}</p>
    </CommonDialog>
  </div>
</template>

<script setup lang="ts">
import type { Id } from '../api/resource-id'
import { resourceId } from '../api/resource-id'
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Folder, Plus } from '@element-plus/icons-vue'
import CommonDialog from '../components/common/CommonDialog.vue'
import CommonTable, { type TableColumn } from '../components/common/CommonTable.vue'
import UnifiedState from '../components/common/UnifiedState.vue'
import { organizationApi } from '../api/runtime'
import type { MemberContext } from '../auth/types'
import type { DepartmentData, InvitationData, MemberRoleData, RoleData } from '../api/organization'
import { useAppStore } from '../stores/app'

type FlatDepartment = DepartmentData & { level: number }
const app = useAppStore()
const loading = ref(true)
const loadingRoles = ref(false)
const errorMessage = ref('')
const keyword = ref('')
const members = ref<MemberContext[]>([])
const departments = ref<DepartmentData[]>([])
const roles = ref<RoleData[]>([])
const memberRoles = ref<Record<Id, MemberRoleData[]>>({})
const inviteOpen = ref(false)
const roleOpen = ref(false)
const tokenOpen = ref(false)
const submittingInvite = ref(false)
const assigningRole = ref(false)
const selectedMember = ref<MemberContext>()
const createdInvitation = ref<InvitationData>()
const inviteForm = reactive<{ email: string; roleId?: Id; workspaceId?: Id; expiresInHours: number }>({ email: '', expiresInHours: 72 })
const roleForm = reactive<{ roleId?: Id; workspaceId?: Id }>({})
const columns: TableColumn[] = [
  { key: 'memberName', label: '成员', minWidth: 190 }, { key: 'roleSummary', label: '角色与范围', minWidth: 220 },
  { key: 'joinedAtText', label: '加入时间', width: 150 }, { key: 'memberStatus', label: '授权状态', width: 110 },
]

const activeRoles = computed(() => roles.value.filter(role => role.roleStatus === 'ACTIVE'))
const flatDepartments = computed(() => flattenDepartments(departments.value))
const departmentCount = computed(() => flatDepartments.value.length)
const memberRows = computed<Record<string, unknown>[]>(() => members.value
  .filter(member => !keyword.value || member.memberName.toLowerCase().includes(keyword.value.toLowerCase()))
  .map(member => ({ ...member, joinedAtText: formatDate(member.joinedAt), roleSummary: summarizeRoles(member.id) })))

function flattenDepartments(values: DepartmentData[], level = 0): FlatDepartment[] {
  return values.flatMap(item => [{ ...item, level }, ...flattenDepartments(item.children ?? [], level + 1)])
}
function summarizeRoles(memberId: Id): string {
  const values = memberRoles.value[memberId] ?? []
  if (!values.length) return '未分配角色'
  return values.map(item => `${item.roleCode}${item.workspaceId ? ' · 工作空间' : ' · 企业' }${item.effective ? '' : '（已失效）'}`).join('，')
}
async function load(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  const tenantId = resourceId(app.tenantId)
  const results = await Promise.allSettled([organizationApi.members(tenantId), organizationApi.departments(tenantId), organizationApi.roles(tenantId)])
  if (results[0]?.status === 'fulfilled') members.value = results[0].value
  if (results[1]?.status === 'fulfilled') departments.value = results[1].value
  if (results[2]?.status === 'fulfilled') roles.value = results[2].value
  const failures = ['成员', '部门', '角色'].filter((_, index) => results[index]?.status === 'rejected')
  if (failures.length) errorMessage.value = `${failures.join('、')}数据加载失败`
  loading.value = false
  if (members.value.length) await loadMemberRoles(tenantId)
}
async function loadMemberRoles(tenantId: Id): Promise<void> {
  loadingRoles.value = true
  const results = await Promise.allSettled(members.value.map(member => organizationApi.memberRoles(tenantId, member.id)))
  const values: Record<Id, MemberRoleData[]> = {}
  results.forEach((result, index) => { if (result.status === 'fulfilled') values[members.value[index]!.id] = result.value })
  memberRoles.value = values; loadingRoles.value = false
}
function openRoleDialog(row: Record<string, unknown>): void {
  selectedMember.value = members.value.find(item => item.id === resourceId(row.id))
  Object.assign(roleForm, { roleId: undefined, workspaceId: undefined }); roleOpen.value = true
}
async function assignRole(): Promise<void> {
  if (!selectedMember.value || !roleForm.roleId) { ElMessage.warning('请选择角色'); return }
  assigningRole.value = true
  try {
    await organizationApi.assignRole(resourceId(app.tenantId), selectedMember.value.id, {
      roleId: roleForm.roleId,
      ...(roleForm.workspaceId === undefined ? {} : { workspaceId: roleForm.workspaceId }),
      memberLockVersion: selectedMember.value.lockVersion,
    })
    roleOpen.value = false; await load(); ElMessage.success('角色已分配，成员权限版本已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '角色分配失败') }
  finally { assigningRole.value = false }
}
async function submitInvite(): Promise<void> {
  if (!/^\S+@\S+\.\S+$/.test(inviteForm.email)) { ElMessage.warning('请输入有效邮箱'); return }
  submittingInvite.value = true
  try {
    createdInvitation.value = await organizationApi.invite(resourceId(app.tenantId), { ...inviteForm })
    inviteOpen.value = false; tokenOpen.value = true; Object.assign(inviteForm, { email: '', roleId: undefined, workspaceId: undefined, expiresInHours: 72 })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '邀请创建失败') }
  finally { submittingInvite.value = false }
}
async function copyToken(): Promise<void> {
  if (!createdInvitation.value?.inviteToken) return
  await navigator.clipboard.writeText(createdInvitation.value.inviteToken); ElMessage.success('邀请令牌已复制')
}
function formatDate(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }

load()
</script>
