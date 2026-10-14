<template>
	<div class="sync-workspace-page">
		<section class="sync-hero">
			<div>
				<span class="eyebrow">INGESTION CONTROL</span>
				<h2>导入与同步中心</h2>
				<p>配置文件导入和数据库同步，并以服务端任务状态、批次对账与质量结果作为最终依据。</p>
			</div>
			<div class="sync-hero-stats">
				<span
					><strong>{{ jobs.length }}</strong
					><small>同步任务</small></span
				>
				<span
					><strong>{{ activeTaskCount }}</strong
					><small>执行中</small></span
				>
				<span
					><strong>{{ failedTaskCount }}</strong
					><small>待处理失败</small></span
				>
			</div>
		</section>

		<nav class="sync-section-tabs" aria-label="导入与同步页面">
			<button
				type="button"
				:class="{ active: activeSection === 'import' }"
				@click="activeSection = 'import'"
			>
				文件导入
			</button>
			<button
				type="button"
				:class="{ active: activeSection === 'sync' }"
				@click="activeSection = 'sync'"
			>
				数据库同步
			</button>
		</nav>

		<UnifiedState v-if="loading" state="loading" description="正在加载数据集、数据源与任务状态。" />
		<UnifiedState
			v-else-if="loadError && !hasAnyData"
			state="error"
			:description="loadError"
			retryable
			@retry="load"
		/>
		<template v-else>
			<el-alert v-if="loadError" type="warning" :title="loadError" show-icon :closable="false" />

			<template v-if="activeSection === 'import'">
				<section class="workbench-card import-builder-card">
					<header class="card-heading">
						<div>
							<span class="eyebrow">CSV / EXCEL</span>
							<h3>新建文件导入</h3>
							<p>上传后先检查样例和字段映射，提交只代表进入队列。</p>
						</div>
						<div class="import-steps">
							<span :class="{ active: importStep >= 1 }">1 上传</span><i /><span
								:class="{ active: importStep >= 2 }"
								>2 映射</span
							><i /><span :class="{ active: importStep >= 3 }">3 确认</span>
						</div>
					</header>

					<div
						class="file-import-dropzone"
						:class="{ selected: selectedFile, uploaded: uploadedFile }"
					>
						<input
							ref="fileInput"
							type="file"
							accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
							@change="selectFile"
						/>
						<el-icon :size="32"><UploadFilled /></el-icon>
						<div>
							<strong>{{ selectedFile?.name ?? '选择 CSV 或 Excel 文件' }}</strong
							><small>{{
								selectedFile ? formatBytes(selectedFile.size) : '支持 .csv、.xlsx，最大 100 MB'
							}}</small>
						</div>
						<el-button @click="fileInput?.click()">选择文件</el-button>
						<el-button
							type="primary"
							:disabled="!selectedFile || uploading"
							:loading="uploading"
							@click="uploadAndPreview"
							>上传并预览</el-button
						>
					</div>
					<div v-if="uploading || uploadedFile" class="upload-feedback">
						<el-progress
							:percentage="uploadProgress"
							:status="uploadedFile ? 'success' : undefined"
						/>
						<span>{{
							uploadedFile
								? `安全校验通过 · ${uploadedFile.fileName}`
								: '正在上传并解析文件，请勿关闭页面'
						}}</span>
					</div>

					<template v-if="filePreview">
						<div class="preview-toolbar">
							<div>
								<span class="eyebrow">CONTENT PREVIEW</span>
								<h3>{{ filePreview.sheetName || uploadedFile?.fileName }}</h3>
								<p>
									抽样 {{ filePreview.sampledRows }} 行{{
										filePreview.truncated ? '，以下为截断预览' : ''
									}}
								</p>
							</div>
							<el-tag effect="plain">{{ filePreview.columns.length }} 个字段</el-tag>
						</div>
						<div class="sample-table-wrap">
							<el-table
								:data="filePreview.rows"
								stripe
								max-height="260"
								empty-text="文件没有可预览的数据行"
							>
								<el-table-column
									v-for="column in filePreview.columns.slice(0, 10)"
									:key="column.name"
									:prop="column.name"
									:label="column.name"
									min-width="140"
									show-overflow-tooltip
								/>
							</el-table>
						</div>

						<div class="mapping-heading">
							<div>
								<h3>字段映射与类型确认</h3>
								<p>唯一键将作为文件导入的稳定业务键；日期字段需确认解析格式。</p>
							</div>
						</div>
						<div class="mapping-table-wrap">
							<el-table :data="importMappings" row-key="sourceField" stripe>
								<el-table-column prop="sourceField" label="源字段" min-width="150"
									><template #default="{ row }"
										><strong>{{ row.sourceField }}</strong
										><small class="column-sample">{{
											row.samples.join(' / ') || '无样例'
										}}</small></template
									></el-table-column
								>
								<el-table-column label="目标字段" min-width="160"
									><template #default="{ row }"
										><el-input v-model.trim="row.targetField" maxlength="128" /></template
								></el-table-column>
								<el-table-column label="目标类型" width="150"
									><template #default="{ row }"
										><el-select v-model="row.targetType"
											><el-option
												v-for="type in mappingTypes"
												:key="type"
												:label="type"
												:value="type" /></el-select></template
								></el-table-column>
								<el-table-column label="日期格式" min-width="170"
									><template #default="{ row }"
										><el-select
											v-if="row.targetType === 'DATE' || row.targetType === 'DATETIME'"
											v-model="row.datePattern"
											allow-create
											filterable
											placeholder="选择或输入格式"
											><el-option
												v-for="pattern in row.datePatterns"
												:key="pattern"
												:label="pattern"
												:value="pattern" /></el-select
										><span v-else>—</span></template
									></el-table-column
								>
								<el-table-column label="必填" width="80" align="center"
									><template #default="{ row }"><el-switch v-model="row.required" /></template
								></el-table-column>
								<el-table-column label="唯一键" width="90" align="center"
									><template #default="{ row }"
										><input
											v-model="importUniqueKey"
											type="radio"
											name="import-unique-key"
											:value="row.sourceField"
											:aria-label="`${row.sourceField} 作为唯一键`" /></template
								></el-table-column>
							</el-table>
						</div>

						<div class="import-confirm-grid">
							<el-form-item label="目标数据集"
								><el-select
									v-model="importDatasetId"
									filterable
									placeholder="选择当前工作空间数据集"
									><el-option
										v-for="dataset in datasets"
										:key="dataset.id"
										:label="`${dataset.name} · ${dataset.code}`"
										:value="dataset.id" /></el-select
							></el-form-item>
							<el-form-item label="导入模式"
								><el-select v-model="importMode"
									><el-option label="严格导入：任一异常行则整体失败" value="STRICT" /><el-option
										label="容错导入：跳过异常行并生成报告"
										value="TOLERANT" /></el-select
							></el-form-item>
							<div class="import-submit-panel">
								<span>提交后状态从“排队中”开始，只有服务端完成校验和发布后才显示成功。</span
								><el-button
									type="primary"
									:loading="submittingImport"
									:disabled="!importReady"
									@click="submitImport"
									>提交导入任务</el-button
								>
							</div>
						</div>
					</template>
				</section>

				<section class="workbench-card task-list-card">
					<header class="card-heading">
						<div>
							<span class="eyebrow">IMPORT JOBS</span>
							<h3>导入记录</h3>
						</div>
						<el-button :loading="refreshingTasks" @click="refreshTasks()"
							><el-icon><Refresh /></el-icon>刷新状态</el-button
						>
					</header>
					<UnifiedState
						v-if="!imports.length"
						state="empty"
						title="暂无导入记录"
						description="上传 CSV 或 Excel 并确认字段映射后，任务会显示在这里。"
					/>
					<el-table v-else :data="imports" stripe>
						<el-table-column label="任务" min-width="150"
							><template #default="{ row }"
								><strong>#{{ row.id }}</strong
								><small class="table-subtext">{{ datasetName(row.datasetId) }}</small></template
							></el-table-column
						>
						<el-table-column label="状态" width="120"
							><template #default="{ row }"
								><el-tag :type="taskStatusTone(row.status)" effect="light">{{
									taskStatusLabel(row.status)
								}}</el-tag></template
							></el-table-column
						>
						<el-table-column label="进度" min-width="210"
							><template #default="{ row }"
								><el-progress
									:percentage="rowProgress(row.totalRows, row.validRows, row.errorRows, row.status)"
									:status="
										row.status === 'FAILED'
											? 'exception'
											: row.status === 'SUCCESS'
												? 'success'
												: undefined
									"
								/><small class="table-subtext"
									>有效 {{ row.validRows }} / 异常 {{ row.errorRows }} / 总计
									{{ row.totalRows || '待统计' }}</small
								></template
							></el-table-column
						>
						<el-table-column
							prop="errorSummary"
							label="结果说明"
							min-width="240"
							show-overflow-tooltip
							><template #default="{ row }">{{
								row.errorSummary || (isTaskActive(row.status) ? '等待服务端执行结果' : '—')
							}}</template></el-table-column
						>
						<el-table-column label="更新时间" width="170"
							><template #default="{ row }">{{
								formatDate(row.updatedAt)
							}}</template></el-table-column
						>
						<el-table-column label="操作" width="150" align="right"
							><template #default="{ row }"
								><el-button link type="primary" @click="showImport(row)">详情</el-button
								><el-button
									v-if="isTaskActive(row.status)"
									link
									type="danger"
									@click="cancelImport(row)"
									>取消</el-button
								></template
							></el-table-column
						>
					</el-table>
				</section>
			</template>

			<template v-else>
				<section class="workbench-card task-list-card">
					<header class="card-heading">
						<div>
							<span class="eyebrow">SYNC JOBS</span>
							<h3>同步任务</h3>
							<p>全量重建或按更新时间字段增量读取。</p>
						</div>
						<el-button type="primary" :icon="Plus" @click="openCreateJob">新建同步任务</el-button>
					</header>
					<UnifiedState
						v-if="!jobs.length"
						state="empty"
						title="暂无同步任务"
						description="从已验证的数据源和目标数据集创建第一条同步任务。"
						action-text="新建同步任务"
						@action="openCreateJob"
					/>
					<el-table v-else :data="jobRows" stripe>
						<el-table-column label="任务" min-width="170"
							><template #default="{ row }"
								><strong>{{ row.jobCode }}</strong
								><small class="table-subtext">{{ row.datasourceName }}</small></template
							></el-table-column
						>
						<el-table-column prop="sourceObject" label="来源" min-width="170" />
						<el-table-column prop="datasetName" label="目标数据集" min-width="160" />
						<el-table-column label="模式" width="100"
							><template #default="{ row }"
								><el-tag effect="plain">{{
									row.syncMode === 'FULL' ? '全量' : '增量'
								}}</el-tag></template
							></el-table-column
						>
						<el-table-column label="最近运行" min-width="180"
							><template #default="{ row }"
								><span>{{ row.lastRunAt ? formatDate(row.lastRunAt) : '尚未运行' }}</span
								><small class="table-subtext">{{
									row.lastRunStatus ? taskStatusLabel(row.lastRunStatus) : '—'
								}}</small></template
							></el-table-column
						>
						<el-table-column label="状态" width="100"
							><template #default="{ row }"
								><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{
									row.status === 'ENABLED' ? '已启用' : '已暂停'
								}}</el-tag></template
							></el-table-column
						>
						<el-table-column label="操作" width="270" align="right"
							><template #default="{ row }"
								><el-button
									link
									type="primary"
									:disabled="row.status !== 'ENABLED'"
									@click="runNow(row)"
									>立即运行</el-button
								><el-button link @click="openEditJob(row)">编辑</el-button
								><el-button
									link
									:type="row.status === 'ENABLED' ? 'warning' : 'success'"
									@click="toggleJob(row)"
									>{{ row.status === 'ENABLED' ? '暂停' : '启用' }}</el-button
								></template
							></el-table-column
						>
					</el-table>
				</section>

				<section class="workbench-card task-list-card">
					<header class="card-heading">
						<div>
							<span class="eyebrow">RUN HISTORY</span>
							<h3>同步记录</h3>
							<p>展示真实批次进度、质量门禁、异常行与失败原因。</p>
						</div>
						<el-button :loading="refreshingTasks" @click="refreshTasks()"
							><el-icon><Refresh /></el-icon>刷新状态</el-button
						>
					</header>
					<UnifiedState
						v-if="!runs.length"
						state="empty"
						title="暂无同步记录"
						description="启用任务并立即运行后，服务端执行状态会显示在这里。"
					/>
					<el-table v-else :data="runs" stripe>
						<el-table-column label="运行" width="150"
							><template #default="{ row }"
								><strong>#{{ row.id }}</strong
								><small class="table-subtext">第 {{ row.attemptNo }} 次尝试</small></template
							></el-table-column
						>
						<el-table-column label="任务" min-width="150"
							><template #default="{ row }">{{ jobName(row.syncJobId) }}</template></el-table-column
						>
						<el-table-column label="状态" width="130"
							><template #default="{ row }"
								><el-tag :type="taskStatusTone(row.status)">{{
									taskStatusLabel(row.status, row.deadLetter)
								}}</el-tag></template
							></el-table-column
						>
						<el-table-column label="批次进度" min-width="210"
							><template #default="{ row }"
								><template v-if="row.batch"
									><strong>{{ row.batch.validRows }} / {{ row.batch.expectedRows }} 行</strong
									><small class="table-subtext"
										>异常 {{ row.batch.rejectedRows }} · 去重
										{{ row.batch.deduplicatedRows }}</small
									></template
								><span v-else>等待 Worker 分配批次</span></template
							></el-table-column
						>
						<el-table-column label="数据质量" width="130"
							><template #default="{ row }"
								><el-tag
									:type="
										row.batch?.qualityStatus === 'PASSED'
											? 'success'
											: row.batch?.qualityStatus
												? 'warning'
												: 'info'
									"
									effect="plain"
									>{{ row.batch?.qualityStatus ?? 'PENDING' }}</el-tag
								></template
							></el-table-column
						>
						<el-table-column label="失败原因" min-width="220" show-overflow-tooltip
							><template #default="{ row }">{{
								row.errorSummary || row.batch?.errorReport || '—'
							}}</template></el-table-column
						>
						<el-table-column label="操作" width="210" align="right"
							><template #default="{ row }"
								><el-button link type="primary" @click="showRun(row)">详情</el-button
								><el-button
									v-if="isTaskActive(row.status)"
									link
									type="danger"
									@click="cancelRun(row)"
									>取消</el-button
								><el-button
									v-if="row.status === 'FAILED'"
									link
									type="warning"
									@click="retryRun(row)"
									>失败重试</el-button
								></template
							></el-table-column
						>
					</el-table>
				</section>
			</template>
		</template>

		<CommonDialog
			v-model="jobDialogOpen"
			:title="editingJob ? '编辑同步任务' : '新建同步任务'"
			width="960px"
			:confirm-loading="savingJob"
			confirm-text="保存任务"
			@confirm="saveJob"
		>
			<el-form label-position="top" class="sync-job-form">
				<div class="sync-job-grid">
					<el-form-item label="数据源"
						><el-select
							v-model="syncForm.datasourceId"
							:disabled="Boolean(editingJob)"
							filterable
							@change="loadSourceObjects(true)"
							><el-option
								v-for="source in activeDatasources"
								:key="source.id"
								:label="source.name"
								:value="source.id" /></el-select
					></el-form-item>
					<el-form-item label="目标数据集"
						><el-select v-model="syncForm.datasetId" :disabled="Boolean(editingJob)" filterable
							><el-option
								v-for="dataset in datasets"
								:key="dataset.id"
								:label="`${dataset.name} · ${dataset.code}`"
								:value="dataset.id" /></el-select
					></el-form-item>
					<el-form-item label="任务编码"
						><el-input
							v-model.trim="syncForm.jobCode"
							:disabled="Boolean(editingJob)"
							maxlength="64"
							placeholder="orders_incremental"
					/></el-form-item>
					<el-form-item label="同步模式"
						><el-select v-model="syncForm.syncMode"
							><el-option label="全量同步" value="FULL" /><el-option
								label="增量同步"
								value="INCREMENTAL" /></el-select
					></el-form-item>
					<el-form-item label="源表"
						><el-select
							v-model="sourceObjectKey"
							filterable
							placeholder="请先采集数据源元数据"
							@change="selectSourceObject"
							><el-option
								v-for="object in sourceObjects"
								:key="objectKey(object)"
								:label="objectKey(object)"
								:value="objectKey(object)" /></el-select
					></el-form-item>
					<el-form-item label="稳定主键"
						><el-select v-model="syncForm.primaryKeyField" filterable allow-create
							><el-option
								v-for="mapping in syncMappings"
								:key="mapping.sourceField"
								:label="mapping.sourceField"
								:value="mapping.sourceField" /></el-select
					></el-form-item>
					<el-form-item v-if="syncForm.syncMode === 'INCREMENTAL'" label="更新时间字段"
						><el-select v-model="syncForm.updatedAtField" filterable allow-create
							><el-option
								v-for="mapping in syncMappings"
								:key="mapping.sourceField"
								:label="mapping.sourceField"
								:value="mapping.sourceField" /></el-select
					></el-form-item>
					<el-form-item label="金额字段（可选）"
						><el-select v-model="syncForm.amountField" clearable filterable allow-create
							><el-option
								v-for="mapping in syncMappings"
								:key="mapping.sourceField"
								:label="mapping.sourceField"
								:value="mapping.sourceField" /></el-select
					></el-form-item>
					<el-form-item label="批次大小"
						><el-input v-model.number="syncForm.batchSize" type="number" min="100" max="10000"
					/></el-form-item>
					<el-form-item label="Cron 表达式（可选）"
						><el-input v-model.trim="syncForm.scheduleExpression" placeholder="0 */15 * * * *"
					/></el-form-item>
					<el-form-item label="时区"
						><el-input v-model.trim="syncForm.timezone" placeholder="Asia/Shanghai"
					/></el-form-item>
				</div>
				<label v-if="!editingJob" class="sync-enabled-control"
					><el-switch v-model="syncForm.enabled" /><span
						><strong>保存后立即启用</strong
						><small>启用不代表已经运行，可保存后手动触发。</small></span
					></label
				>
				<div class="mapping-heading">
					<div>
						<h3>同步字段映射</h3>
						<p>映射由元数据自动生成，可调整目标字段、类型和日期格式。</p>
					</div>
				</div>
				<el-table :data="syncMappings" stripe max-height="320">
					<el-table-column prop="sourceField" label="源字段" min-width="150" />
					<el-table-column label="目标字段" min-width="160"
						><template #default="{ row }"><el-input v-model.trim="row.targetField" /></template
					></el-table-column>
					<el-table-column label="目标类型" width="150"
						><template #default="{ row }"
							><el-select v-model="row.targetType"
								><el-option
									v-for="type in mappingTypes"
									:key="type"
									:label="type"
									:value="type" /></el-select></template
					></el-table-column>
					<el-table-column label="日期格式" min-width="160"
						><template #default="{ row }"
							><el-input
								v-if="row.targetType === 'DATE' || row.targetType === 'DATETIME'"
								v-model.trim="row.datePattern"
								placeholder="yyyy-MM-dd"
							/><span v-else>—</span></template
						></el-table-column
					>
					<el-table-column label="必填" width="80" align="center"
						><template #default="{ row }"><el-switch v-model="row.required" /></template
					></el-table-column>
					<el-table-column label="操作" width="80" align="right"
						><template #default="{ $index }"
							><el-button link type="danger" @click="syncMappings.splice($index, 1)"
								>移除</el-button
							></template
						></el-table-column
					>
				</el-table>
			</el-form>
		</CommonDialog>

		<CommonDialog v-model="importDetailOpen" title="导入任务详情" width="700px">
			<div v-if="selectedImport" class="task-detail-panel">
				<div class="task-detail-summary">
					<el-tag :type="taskStatusTone(selectedImport.status)" size="large">{{
						taskStatusLabel(selectedImport.status)
					}}</el-tag
					><span>任务 #{{ selectedImport.id }}</span>
				</div>
				<dl>
					<div>
						<dt>目标数据集</dt>
						<dd>{{ datasetName(selectedImport.datasetId) }}</dd>
					</div>
					<div>
						<dt>总行数</dt>
						<dd>{{ selectedImport.totalRows }}</dd>
					</div>
					<div>
						<dt>有效行</dt>
						<dd>{{ selectedImport.validRows }}</dd>
					</div>
					<div>
						<dt>错误行</dt>
						<dd>{{ selectedImport.errorRows }}</dd>
					</div>
					<div>
						<dt>错误报告</dt>
						<dd>{{ selectedImport.errorFileId ? `文件 #${selectedImport.errorFileId}` : '—' }}</dd>
					</div>
					<div>
						<dt>更新时间</dt>
						<dd>{{ formatDate(selectedImport.updatedAt) }}</dd>
					</div>
				</dl>
				<pre>{{
					selectedImport.errorSummary ||
					(isTaskActive(selectedImport.status) ? '服务端仍在执行，请刷新状态。' : '没有错误详情')
				}}</pre>
			</div>
			<template #footer
				><el-button type="primary" @click="importDetailOpen = false">关闭</el-button></template
			>
		</CommonDialog>

		<CommonDialog v-model="runDetailOpen" title="同步运行详情" width="760px">
			<div v-if="selectedRun" class="task-detail-panel">
				<div class="task-detail-summary">
					<el-tag :type="taskStatusTone(selectedRun.status)" size="large">{{
						taskStatusLabel(selectedRun.status, selectedRun.deadLetter)
					}}</el-tag
					><span>运行 #{{ selectedRun.id }} · {{ jobName(selectedRun.syncJobId) }}</span>
				</div>
				<dl>
					<div>
						<dt>开始时间</dt>
						<dd>{{ selectedRun.startedAt ? formatDate(selectedRun.startedAt) : '等待执行' }}</dd>
					</div>
					<div>
						<dt>完成时间</dt>
						<dd>{{ selectedRun.finishedAt ? formatDate(selectedRun.finishedAt) : '—' }}</dd>
					</div>
					<div>
						<dt>质量状态</dt>
						<dd>{{ selectedRun.batch?.qualityStatus ?? 'PENDING' }}</dd>
					</div>
					<div>
						<dt>错误行</dt>
						<dd>{{ selectedRun.batch?.rejectedRows ?? 0 }}</dd>
					</div>
					<div>
						<dt>行数对账</dt>
						<dd>
							{{
								selectedRun.batch
									? `${selectedRun.batch.expectedRows} → ${selectedRun.batch.receivedRows}`
									: '—'
							}}
						</dd>
					</div>
					<div>
						<dt>金额对账</dt>
						<dd>
							{{
								selectedRun.batch
									? `${selectedRun.batch.sourceAmount} → ${selectedRun.batch.targetAmount}`
									: '—'
							}}
						</dd>
					</div>
					<div>
						<dt>业务键对账</dt>
						<dd>
							{{
								selectedRun.batch
									? `${selectedRun.batch.sourceBusinessKeys} → ${selectedRun.batch.targetBusinessKeys}`
									: '—'
							}}
						</dd>
					</div>
					<div>
						<dt>重试次数</dt>
						<dd>{{ selectedRun.retryCount }}</dd>
					</div>
				</dl>
				<pre>{{
					selectedRun.errorSummary ||
					selectedRun.batch?.errorReport ||
					(isTaskActive(selectedRun.status) ? '服务端仍在执行，请刷新状态。' : '没有失败详情')
				}}</pre>
			</div>
			<template #footer
				><el-button type="primary" @click="runDetailOpen = false">关闭</el-button></template
			>
		</CommonDialog>
	</div>
</template>

<script setup lang="ts">
import { resourceId } from '../../api/resource-id'
import type { Id } from '../../api/resource-id'
	import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
	import { ElMessage, ElMessageBox } from 'element-plus';
	import { Plus, Refresh, UploadFilled } from '@element-plus/icons-vue';
	import CommonDialog from '../../components/common/CommonDialog.vue';
	import UnifiedState from '../../components/common/UnifiedState.vue';
	import { datasourceApi, semanticApi, syncApi } from '../../api/runtime';
	import type {
		Datasource,
		FileData,
		FilePreviewData,
		SourceObject,
	} from '../../datasources/types';
	import type { Dataset } from '../../semantic/types';
	import { isTaskActive, rowProgress, taskStatusLabel, taskStatusTone } from '../../sync/status';
	import type { FieldMapping, ImportJob, SyncJob, SyncRun, TaskStatus } from '../../sync/types';
	import { useConfirm } from '../../composables/useConfirm';
	import { useAppStore } from '../../stores/app';

	type MappingType = FieldMapping['targetType'];
	interface EditableMapping extends FieldMapping {
		datePatterns: string[];
		samples: string[];
	}
	interface JobRow extends SyncJob {
		datasourceName: string;
		datasetName: string;
		lastRunAt?: string;
		lastRunStatus?: TaskStatus;
	}

	const app = useAppStore();
	const { confirm } = useConfirm();
	const activeSection = ref<'import' | 'sync'>('import');
	const loading = ref(true);
	const loadError = ref('');
	const refreshingTasks = ref(false);
	const jobs = ref<SyncJob[]>([]);
	const runs = ref<SyncRun[]>([]);
	const imports = ref<ImportJob[]>([]);
	const datasources = ref<Datasource[]>([]);
	const datasets = ref<Dataset[]>([]);

	const fileInput = ref<HTMLInputElement>();
	const selectedFile = ref<File>();
	const uploadedFile = ref<FileData>();
	const filePreview = ref<FilePreviewData>();
	const uploading = ref(false);
	const uploadProgress = ref(0);
	const importMappings = ref<EditableMapping[]>([]);
	const importUniqueKey = ref('');
	const importDatasetId = ref<Id>();
	const importMode = ref<'STRICT' | 'TOLERANT'>('TOLERANT');
	const submittingImport = ref(false);
	const selectedImport = ref<ImportJob>();
	const importDetailOpen = ref(false);

	const jobDialogOpen = ref(false);
	const editingJob = ref<SyncJob>();
	const savingJob = ref(false);
	const sourceObjects = ref<SourceObject[]>([]);
	const sourceObjectKey = ref('');
	const syncMappings = ref<EditableMapping[]>([]);
	const selectedRun = ref<SyncRun>();
	const runDetailOpen = ref(false);
	const syncForm = reactive({
		datasourceId: undefined as Id | undefined,
		datasetId: undefined as Id | undefined,
		jobCode: '',
		syncMode: 'FULL' as 'FULL' | 'INCREMENTAL',
		sourceSchema: '',
		sourceTable: '',
		primaryKeyField: '',
		updatedAtField: '',
		amountField: '',
		batchSize: 1000,
		scheduleExpression: '',
		timezone: 'Asia/Shanghai',
		enabled: true,
	});
	let pollTimer: ReturnType<typeof setInterval> | undefined;

	const mappingTypes: MappingType[] = ['STRING', 'LONG', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'];
	const activeDatasources = computed(() =>
		datasources.value.filter((item) => item.status === 'ACTIVE'),
	);
	const activeTaskCount = computed(
		() => [...imports.value, ...runs.value].filter((item) => isTaskActive(item.status)).length,
	);
	const failedTaskCount = computed(
		() =>
			[...imports.value, ...runs.value].filter(
				(item) => item.status === 'FAILED' || item.status === 'TIMEOUT',
			).length,
	);
	const hasAnyData = computed(
		() =>
			jobs.value.length +
				runs.value.length +
				imports.value.length +
				datasources.value.length +
				datasets.value.length >
			0,
	);
	const importStep = computed(() =>
		filePreview.value ? (importDatasetId.value && importUniqueKey.value ? 3 : 2) : 1,
	);
	const importReady = computed(() =>
		Boolean(
			uploadedFile.value &&
			importDatasetId.value &&
			importUniqueKey.value &&
			importMappings.value.length &&
			importMappings.value.every(
				(item) =>
					item.targetField &&
					(!(item.targetType === 'DATE' || item.targetType === 'DATETIME') || item.datePattern),
			),
		),
	);
	const jobRows = computed<JobRow[]>(() =>
		jobs.value.map((job) => {
			const lastRun = runs.value
				.filter((run) => run.syncJobId === job.id)
				.sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt))[0];
			return {
				...job,
				datasourceName:
					datasources.value.find((item) => item.id === job.datasourceId)?.name ??
					`数据源 ${job.datasourceId}`,
				datasetName: datasetName(job.datasetId),
				...(lastRun
					? {
							lastRunAt: lastRun.finishedAt ?? lastRun.startedAt ?? lastRun.createdAt,
							lastRunStatus: lastRun.status,
						}
					: {}),
			};
		}),
	);

	async function load(): Promise<void> {
		loading.value = true;
		loadError.value = '';
		const results = await Promise.allSettled([
			syncApi.jobs(),
			syncApi.runs(),
			syncApi.imports(),
			datasourceApi.list(app.workspaceId),
			semanticApi.datasets(app.workspaceId),
		]);
		const [jobResult, runResult, importResult, sourceResult, datasetResult] = results;
		if (sourceResult.status === 'fulfilled') datasources.value = sourceResult.value;
		if (datasetResult.status === 'fulfilled') datasets.value = datasetResult.value;
		if (jobResult.status === 'fulfilled') jobs.value = workspaceJobs(jobResult.value);
		if (runResult.status === 'fulfilled') runs.value = workspaceRuns(runResult.value, jobs.value);
		if (importResult.status === 'fulfilled') imports.value = workspaceImports(importResult.value);
		const failed = results.find((result) => result.status === 'rejected');
		if (failed?.status === 'rejected')
			loadError.value = failed.reason instanceof Error ? failed.reason.message : '部分数据加载失败';
		loading.value = false;
	}

	async function refreshTasks(showError = true): Promise<void> {
		if (refreshingTasks.value) return;
		refreshingTasks.value = true;
		try {
			const [newImports, newRuns, newJobs] = await Promise.all([
				syncApi.imports(),
				syncApi.runs(),
				syncApi.jobs(),
			]);
			jobs.value = workspaceJobs(newJobs);
			runs.value = workspaceRuns(newRuns, jobs.value);
			imports.value = workspaceImports(newImports);
			if (selectedImport.value)
				selectedImport.value = imports.value.find((item) => item.id === selectedImport.value?.id);
			if (selectedRun.value)
				selectedRun.value = runs.value.find((item) => item.id === selectedRun.value?.id);
		} catch (error) {
			if (showError) ElMessage.error(error instanceof Error ? error.message : '任务状态刷新失败');
		} finally {
			refreshingTasks.value = false;
		}
	}

	function selectFile(event: Event): void {
		selectedFile.value = (event.target as HTMLInputElement).files?.[0];
		uploadedFile.value = undefined;
		filePreview.value = undefined;
		importMappings.value = [];
		importUniqueKey.value = '';
		uploadProgress.value = 0;
	}

	async function uploadAndPreview(): Promise<void> {
		const file = selectedFile.value;
		if (!file) return;
		if (!/\.(csv|xlsx)$/i.test(file.name)) {
			ElMessage.warning('仅支持 CSV 或 XLSX 文件');
			return;
		}
		if (file.size > 100 * 1024 * 1024) {
			ElMessage.warning('文件不能超过 100 MB');
			return;
		}
		uploading.value = true;
		uploadProgress.value = 15;
		try {
			uploadedFile.value = await datasourceApi.uploadFile(file, app.workspaceId);
			uploadProgress.value = 65;
			filePreview.value = await datasourceApi.previewFile(uploadedFile.value.id);
			importMappings.value = filePreview.value.columns.map((column) => ({
				sourceField: column.name,
				targetField: column.name,
				targetType: normalizeType(column.inferredType),
				...(column.datePatterns[0] ? { datePattern: column.datePatterns[0] } : {}),
				required: !column.nullable,
				datePatterns: column.datePatterns,
				samples: column.samples,
			}));
			importUniqueKey.value = importMappings.value[0]?.sourceField ?? '';
			uploadProgress.value = 100;
			ElMessage.success('文件上传完成，请确认字段映射后提交');
		} catch (error) {
			uploadProgress.value = 0;
			ElMessage.error(error instanceof Error ? error.message : '文件上传或解析失败');
		} finally {
			uploading.value = false;
		}
	}

	async function submitImport(): Promise<void> {
		if (
			!importReady.value ||
			!uploadedFile.value ||
			!importDatasetId.value ||
			!app.currentMember?.id
		) {
			ElMessage.warning('请完成目标数据集、字段映射和唯一键配置');
			return;
		}
		const unique = importUniqueKey.value;
		const mappings = importMappings.value
			.slice()
			.sort(
				(left, right) => Number(right.sourceField === unique) - Number(left.sourceField === unique),
			)
			.map(toFieldMapping);
		submittingImport.value = true;
		try {
			const task = await syncApi.createImport({
				workspaceId: app.workspaceId,
				datasetId: importDatasetId.value,
				requesterMemberId: app.currentMember.id,
				objectFileId: uploadedFile.value.id,
				mappings,
				strictMode: importMode.value === 'STRICT',
			});
			imports.value = [task, ...imports.value.filter((item) => item.id !== task.id)];
			ElMessage.info(`导入任务 #${task.id} 已提交，当前状态：${taskStatusLabel(task.status)}`);
			await refreshTasks(false);
		} catch (error) {
			ElMessage.error(error instanceof Error ? error.message : '导入任务提交失败');
		} finally {
			submittingImport.value = false;
		}
	}

	async function cancelImport(job: ImportJob): Promise<void> {
		if (
			!(await confirm(`确定取消导入任务 #${job.id} 吗？已经进入终态的任务不会被更改。`, {
				title: '取消导入任务',
				confirmText: '确认取消',
				danger: true,
			}))
		)
			return;
		await syncApi.cancelImport(job.id);
		await refreshTasks(false);
		ElMessage.success('取消请求已由服务端确认');
	}

	function showImport(job: ImportJob): void {
		selectedImport.value = job;
		importDetailOpen.value = true;
	}

	function resetSyncForm(): void {
		Object.assign(syncForm, {
			datasourceId: undefined,
			datasetId: undefined,
			jobCode: '',
			syncMode: 'FULL',
			sourceSchema: '',
			sourceTable: '',
			primaryKeyField: '',
			updatedAtField: '',
			amountField: '',
			batchSize: 1000,
			scheduleExpression: '',
			timezone: 'Asia/Shanghai',
			enabled: true,
		});
		sourceObjects.value = [];
		sourceObjectKey.value = '';
		syncMappings.value = [];
	}

	async function openCreateJob(): Promise<void> {
		editingJob.value = undefined;
		resetSyncForm();
		const selection = readDatasourceSelection();
		if (selection) {
			syncForm.datasourceId = selection.datasourceId;
			await loadSourceObjects(false);
			const preferred = selection.tables.find((value) =>
				sourceObjects.value.some((object) => objectKey(object) === value),
			);
			if (preferred) {
				sourceObjectKey.value = preferred;
				selectSourceObject(preferred);
			}
		}
		jobDialogOpen.value = true;
	}

	async function openEditJob(row: JobRow): Promise<void> {
		const job = jobs.value.find((item) => item.id === row.id);
		if (!job) return;
		editingJob.value = job;
		const [sourceSchema = '', ...tableParts] = job.sourceObject.split('.');
		Object.assign(syncForm, {
			datasourceId: job.datasourceId,
			datasetId: job.datasetId,
			jobCode: job.jobCode,
			syncMode: job.syncMode,
			sourceSchema,
			sourceTable: tableParts.join('.'),
			primaryKeyField: job.primaryKeyField,
			updatedAtField: job.updatedAtField ?? '',
			amountField: job.amountField ?? '',
			batchSize: job.batchSize,
			scheduleExpression: job.scheduleExpression ?? '',
			timezone: job.timezone,
			enabled: job.status === 'ENABLED',
		});
		sourceObjectKey.value = job.sourceObject;
		syncMappings.value = job.mappings.map((mapping) => ({
			...mapping,
			datePatterns: mapping.datePattern ? [mapping.datePattern] : [],
			samples: [],
		}));
		await loadSourceObjects(false);
		jobDialogOpen.value = true;
	}

	async function loadSourceObjects(resetMapping: boolean): Promise<void> {
		sourceObjects.value = [];
		if (!syncForm.datasourceId) return;
		try {
			const snapshots = await datasourceApi.metadataSnapshots(syncForm.datasourceId);
			sourceObjects.value = snapshots.at(-1)?.objects ?? [];
			if (resetMapping) {
				sourceObjectKey.value = '';
				syncForm.sourceSchema = '';
				syncForm.sourceTable = '';
				syncMappings.value = [];
			}
		} catch (error) {
			ElMessage.error(error instanceof Error ? error.message : '数据源元数据加载失败');
		}
	}

	function selectSourceObject(key: string): void {
		const object = sourceObjects.value.find((item) => objectKey(item) === key);
		if (!object) return;
		syncForm.sourceSchema = object.schema;
		syncForm.sourceTable = object.name;
		syncMappings.value = object.columns.map((column) => {
			const targetType = normalizeType(column.dataType);
			const datePattern =
				targetType === 'DATE'
					? 'yyyy-MM-dd'
					: targetType === 'DATETIME'
						? "yyyy-MM-dd'T'HH:mm:ss"
						: undefined;
			return {
				sourceField: column.name,
				targetField: column.name,
				targetType,
				...(datePattern ? { datePattern } : {}),
				required: !column.nullable,
				datePatterns: [],
				samples: [],
			};
		});
		syncForm.primaryKeyField =
			object.columns.find((column) => column.primaryKey)?.name ?? object.columns[0]?.name ?? '';
	}

	async function saveJob(): Promise<void> {
		const message = validateSyncForm();
		if (message) {
			ElMessage.warning(message);
			return;
		}
		savingJob.value = true;
		try {
			const common = {
				syncMode: syncForm.syncMode,
				sourceSchema: syncForm.sourceSchema,
				sourceTable: syncForm.sourceTable,
				primaryKeyField: syncForm.primaryKeyField,
				...(syncForm.updatedAtField ? { updatedAtField: syncForm.updatedAtField } : {}),
				...(syncForm.amountField ? { amountField: syncForm.amountField } : {}),
				mappings: syncMappings.value.map(toFieldMapping),
				batchSize: Number(syncForm.batchSize),
				...(syncForm.scheduleExpression ? { scheduleExpression: syncForm.scheduleExpression } : {}),
				timezone: syncForm.timezone,
			};
			if (editingJob.value)
				await syncApi.updateJob(editingJob.value.id, {
					...common,
					lockVersion: editingJob.value.lockVersion,
				});
			else
				await syncApi.createJob({
					...common,
					datasourceId: syncForm.datasourceId!,
					datasetId: syncForm.datasetId!,
					jobCode: syncForm.jobCode,
					enabled: syncForm.enabled,
				});
			jobDialogOpen.value = false;
			await refreshTasks(false);
			ElMessage.success(editingJob.value ? '同步任务已更新' : '同步任务已创建');
		} catch (error) {
			ElMessage.error(error instanceof Error ? error.message : '同步任务保存失败');
		} finally {
			savingJob.value = false;
		}
	}

	async function runNow(row: JobRow): Promise<void> {
		const accepted = await syncApi.run(row.id);
		ElMessage.info(
			`运行 #${accepted.taskId} 已提交，当前状态：${accepted.status === 'PENDING' ? '排队中' : accepted.status}`,
		);
		await refreshTasks(false);
	}

	async function toggleJob(row: JobRow): Promise<void> {
		const job = jobs.value.find((item) => item.id === row.id);
		if (!job) return;
		await syncApi.setEnabled(job, job.status !== 'ENABLED');
		await refreshTasks(false);
		ElMessage.success(job.status === 'ENABLED' ? '任务已暂停' : '任务已启用');
	}

	async function cancelRun(run: SyncRun): Promise<void> {
		if (
			!(await confirm(`确定取消同步运行 #${run.id} 吗？已发布的批次不会回滚。`, {
				title: '取消同步运行',
				confirmText: '确认取消',
				danger: true,
			}))
		)
			return;
		await syncApi.cancelRun(run.id);
		await refreshTasks(false);
		ElMessage.success('取消请求已由服务端确认');
	}

	async function retryRun(run: SyncRun): Promise<void> {
		const result = await ElMessageBox.prompt(
			'请输入失败重试原因，服务端会创建新的运行记录。',
			'失败重试',
			{
				confirmButtonText: '提交重试',
				cancelButtonText: '取消',
				inputPattern: /\S+/,
				inputErrorMessage: '请输入重试原因',
			},
		).catch(() => undefined);
		if (!result?.value.trim()) return;
		const accepted = await syncApi.compensate(run, result.value.trim());
		ElMessage.info(`补偿运行 #${accepted.taskId} 已提交，当前状态：排队中`);
		await refreshTasks(false);
	}

	async function showRun(run: SyncRun): Promise<void> {
		try {
			selectedRun.value = await syncApi.runDetail(run.id);
		} catch {
			selectedRun.value = run;
		}
		runDetailOpen.value = true;
	}

	function validateSyncForm(): string | undefined {
		if (!syncForm.datasourceId || !syncForm.datasetId || !syncForm.jobCode.trim())
			return '请选择数据源、目标数据集并填写任务编码';
		if (!syncForm.sourceSchema || !syncForm.sourceTable || !syncForm.primaryKeyField)
			return '请选择源表和稳定主键';
		if (syncForm.syncMode === 'INCREMENTAL' && !syncForm.updatedAtField)
			return '增量同步必须配置更新时间字段';
		if (syncForm.batchSize < 100 || syncForm.batchSize > 10000)
			return '批次大小必须在 100 到 10000 之间';
		if (
			!syncForm.timezone ||
			!syncMappings.value.length ||
			syncMappings.value.some((item) => !item.targetField)
		)
			return '请完整配置时区和字段映射';
		return undefined;
	}

	function toFieldMapping(mapping: EditableMapping): FieldMapping {
		return {
			sourceField: mapping.sourceField,
			targetField: mapping.targetField,
			targetType: mapping.targetType,
			...(mapping.datePattern ? { datePattern: mapping.datePattern } : {}),
			required: mapping.required,
		};
	}

	function normalizeType(value: string): MappingType {
		const normalized = value.toUpperCase();
		if (/BOOL|BIT/.test(normalized)) return 'BOOLEAN';
		if (/DATE|TIME/.test(normalized)) return normalized === 'DATE' ? 'DATE' : 'DATETIME';
		if (/DECIMAL|NUMERIC|DOUBLE|FLOAT|MONEY/.test(normalized)) return 'DECIMAL';
		if (/INT|LONG|BIGINT|SMALLINT/.test(normalized)) return 'LONG';
		return 'STRING';
	}

	function readDatasourceSelection(): { datasourceId: Id; tables: string[] } | undefined {
		const raw = localStorage.getItem(`aibi.datasourceSelection.${app.tenantId}.${app.workspaceId}`);
		if (!raw) return undefined;
		try {
			const value = JSON.parse(raw) as { datasourceId?: unknown; tables?: unknown };
			if (
				(typeof value.datasourceId === 'string' || typeof value.datasourceId === 'number') &&
				Array.isArray(value.tables) &&
				value.tables.every((item) => typeof item === 'string')
			)
				return { datasourceId: resourceId(value.datasourceId), tables: value.tables };
		} catch {
			/* Ignore stale selection; the user can select a source manually. */
		}
		return undefined;
	}

	function objectKey(object: SourceObject): string {
		return `${object.schema}.${object.name}`;
	}
	function workspaceJobs(values: SyncJob[]): SyncJob[] {
		const sourceIds = new Set(datasources.value.map((item) => item.id));
		const datasetIds = new Set(datasets.value.map((item) => item.id));
		return values.filter(
			(item) => sourceIds.has(item.datasourceId) && datasetIds.has(item.datasetId),
		);
	}
	function workspaceRuns(values: SyncRun[], visibleJobs: SyncJob[]): SyncRun[] {
		const jobIds = new Set(visibleJobs.map((item) => item.id));
		return values.filter((item) => jobIds.has(item.syncJobId));
	}
	function workspaceImports(values: ImportJob[]): ImportJob[] {
		const datasetIds = new Set(datasets.value.map((item) => item.id));
		return values.filter((item) => datasetIds.has(item.datasetId));
	}
	function datasetName(id: Id): string {
		return datasets.value.find((item) => item.id === id)?.name ?? `数据集 ${id}`;
	}
	function jobName(id: Id): string {
		return jobs.value.find((item) => item.id === id)?.jobCode ?? `任务 ${id}`;
	}
	function formatDate(value: string): string {
		return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(
			new Date(value),
		);
	}
	function formatBytes(value: number): string {
		return value < 1024 * 1024
			? `${(value / 1024).toFixed(1)} KB`
			: `${(value / 1024 / 1024).toFixed(1)} MB`;
	}

	onMounted(() => {
		void load();
		pollTimer = setInterval(() => {
			if (activeTaskCount.value > 0) void refreshTasks(false);
		}, 4000);
	});
	onBeforeUnmount(() => {
		if (pollTimer) clearInterval(pollTimer);
	});
</script>
