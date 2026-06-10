<template>
  <div class="workbench-page">
    <header class="workbench-header">
      <div class="header-top">
        <div class="header-main">
          <h1 class="page-title">调度工作台</h1>
          <p class="page-sub">找家纺 · 叠石桥 L1 · 任务池 · 园区态势 · 异常处置</p>
        </div>
        <div class="header-actions">
          <a-switch
            v-if="authStore.isAdmin"
            v-model:checked="dispatchPaused"
            checked-children="暂停派单"
            un-checked-children="正常派单"
            @change="onDispatchPauseChange"
          />
          <a-button
            v-if="authStore.canWrite"
            type="primary"
            @click="createOrderModalOpen = true"
          >
            创建短驳订单
          </a-button>
          <a-button :loading="store.loading" @click="refreshAll">
            <ReloadOutlined /> 刷新 <span class="kbd-hint">R</span>
          </a-button>
        </div>
      </div>
      <!-- V5-T4: 全文搜索 -->
      <div class="header-search">
        <a-auto-complete
          v-model:value="searchKeyword"
          :options="searchOptions"
          style="width: 320px"
          placeholder="搜索订单号、站名、车号、备注…"
          @search="onSearchInput"
          @select="onSearchSelect"
        >
          <template #option="{ item }">
            <div class="global-search-option">
              <span class="search-option-group">{{ item.group }}</span>
              <span class="search-option-label">{{ item.label }}</span>
              <span v-if="item.hint" class="search-option-hint">{{ item.hint }}</span>
            </div>
          </template>
        </a-auto-complete>
        <a-tooltip title="Ctrl+K 打开命令面板">
          <SearchOutlined class="search-panel-trigger" @click="toggleCommandPalette" />
        </a-tooltip>
      </div>
      <!-- 调度风险带：合并派车预测、低电、拥堵 -->
      <div v-if="riskBandItems.length > 0" class="ops-risk-band">
        <button
          v-for="item in riskBandItems"
          :key="item.key"
          type="button"
          class="risk-band-item"
          :class="`risk-${item.level}`"
          @click="item.action?.()"
        >
          <span class="risk-dot"></span>
          <span class="risk-text">{{ item.message }}</span>
          <span v-if="item.action" class="risk-action-hint">查看 →</span>
        </button>
      </div>
      <div class="header-bottom">
        <div class="header-metrics">
          <div class="metric">
            <span class="metric-value">{{ store.pendingCount }}</span>
            <span class="metric-label">待派单</span>
          </div>
          <div class="metric metric-warn">
            <span class="metric-value">{{ store.manualPendingCount }}</span>
            <span class="metric-label">人工待处理</span>
          </div>
          <div class="metric metric-danger">
            <span class="metric-value">{{ store.openExceptionCount }}</span>
            <span class="metric-label">OPEN 异常</span>
          </div>
          <div class="metric-divider"></div>
          <div class="metric metric-success">
            <span class="metric-value">{{ store.assignableVehicleCount }}</span>
            <span class="metric-label">可派车</span>
          </div>
          <div class="metric metric-info">
            <span class="metric-value">{{ store.pluggedStandbyCount }}</span>
            <span class="metric-label">插枪待命</span>
          </div>
          <div class="metric metric-charging">
            <span class="metric-value">{{ store.chargingCount }}</span>
            <span class="metric-label">充电中</span>
          </div>
          <div class="metric metric-online">
            <span class="metric-value">{{ store.onlineVehicleCount }}</span>
            <span class="metric-label">在线车辆</span>
          </div>
        </div>
        <span class="shortcut-hint">快捷键：R 刷新 · A 自动派车 · M 手动派车 · ↑↓ 切换任务</span>
      </div>
    </header>

    <a-alert
      v-if="!authStore.canWrite"
      type="info"
      show-icon
      class="viewer-readonly-banner"
      message="当前为只读账号（VIEWER），无法执行派车、改派或异常处置操作"
    />

    <SkeletonLoader v-if="showSkeleton" preset="workbench" />
    <div v-else class="workbench-grid">
      <!-- 左：任务池（核心面板，优先渲染） -->
      <section class="panel panel-tasks">
        <div class="panel-head">
          <div class="panel-head-title">
            <h2>任务池</h2>
            <span class="panel-order-hint">默认按优先级 · 拖动仅本机偏好</span>
            <a-button
              v-if="hasManualTaskOrder"
              type="link"
              size="small"
              class="reset-order-link"
              @click="resetTaskOrder"
            >
              恢复服务端排序
            </a-button>
          </div>
          <div class="panel-head-toolbar">
            <div class="filter-tabs">
              <button
                v-for="tab in taskTabs"
                :key="tab.key"
                class="filter-tab"
                :class="{ active: store.taskFilter === tab.key }"
                @click="onTaskTabChange(tab.key)"
              >
                {{ tab.label }}
                <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
              </button>
            </div>
            <a-select
              v-model:value="routeFilter"
              allow-clear
              placeholder="按线路筛选"
              :options="routeOptions"
              class="route-filter"
              size="small"
            />
            <router-link to="/vertical/hub" class="hub-link">母港分流</router-link>
          </div>
        </div>

        <!-- V5-W6/W7: 筛选栏 + 视图模板 -->
        <div class="filter-bar">
          <div class="filter-row">
            <a-select v-model:value="filterPriority" allow-clear placeholder="优先级" class="filter-select" size="small" @change="activeViewName = null">
              <a-select-option value="P1">P1 紧急</a-select-option>
              <a-select-option value="P2">P2 普通</a-select-option>
              <a-select-option value="P3">P3 低优先级</a-select-option>
            </a-select>
            <a-select v-model:value="filterWaitMin" allow-clear placeholder="等待时间" class="filter-select" size="small" @change="activeViewName = null">
              <a-select-option :value="5">≥ 5 分钟</a-select-option>
              <a-select-option :value="10">≥ 10 分钟</a-select-option>
              <a-select-option :value="30">≥ 30 分钟</a-select-option>
            </a-select>
            <a-select v-model:value="filterVehicleAssigned" allow-clear placeholder="派车状态" class="filter-select" size="small" @change="activeViewName = null">
              <a-select-option value="unassigned">未派车</a-select-option>
              <a-select-option value="assigned">已派车</a-select-option>
            </a-select>
            <label class="filter-check">
              <input v-model="filterOpenExceptionOnly" type="checkbox" @change="activeViewName = null" />
              仅异常
            </label>
            <a-button size="small" type="link" :disabled="!hasActiveFilters" @click="openSaveViewModal">保存视图</a-button>
            <a-button size="small" type="link" :disabled="!hasActiveFilters" @click="clearAllFilters">清除筛选</a-button>
          </div>
          <div class="filter-templates">
            <span class="filter-templates-label">模板：</span>
            <button
              v-for="tmpl in quickFilterTemplates"
              :key="tmpl.name"
              class="filter-chip"
              :class="{ active: activeViewName === tmpl.name }"
              @click="applySavedView(tmpl)"
            >
              {{ tmpl.name }}
            </button>
            <template v-for="view in savedViews" :key="view.name">
              <button
                class="filter-chip saved"
                :class="{ active: activeViewName === view.name }"
                @click="applySavedView(view)"
              >
                {{ view.name }}
                <span class="filter-chip-del" @click.stop="deleteSavedView(view.name)">&times;</span>
              </button>
            </template>
            <span v-if="savedViews.length === 0 && quickFilterTemplates.every(t => activeViewName !== t.name)" class="filter-templates-hint">暂无</span>
          </div>
        </div>
        <div v-if="authStore.canWrite && selectedTaskIds.length > 0" class="batch-toolbar batch-toolbar-sticky">
          <span class="batch-hint">已选 {{ selectedTaskIds.length }} 项</span>
          <div class="batch-groups">
            <span v-for="g in selectedRouteGroups" :key="g.routeCode" class="batch-group-tag route-tag">
              {{ g.routeCode }} ×{{ g.count }}
            </span>
            <span v-for="g in selectedPriorityGroups" :key="g.priority" class="batch-group-tag priority-tag">
              {{ g.priority }} ×{{ g.count }}
            </span>
          </div>
          <div class="batch-actions">
            <a-button size="small" type="primary" :loading="batchLoading" @click="handleBatchAutoPreConfirm">
              批量自动派车
            </a-button>
            <a-button size="small" :loading="batchLoading" @click="openBatchReassign">批量改派</a-button>
            <a-button size="small" danger :loading="batchLoading" @click="handleBatchCancelPreConfirm">批量取消</a-button>
            <a-button size="small" type="link" @click="selectSameRoute">选同线路</a-button>
            <a-button size="small" type="link" @click="selectSamePriority">选同优先级</a-button>
            <a-button size="small" type="link" @click="clearSelection">清空</a-button>
          </div>
        </div>
        <a-spin :spinning="store.loading || store.poolLoading">
          <div class="task-list">
            <article
              v-for="task in filteredTaskPool"
              :key="task.taskId"
              class="task-card"
              :class="{ selected: store.selectedTaskId === task.taskId, checked: selectedTaskIds.includes(task.taskId) }"
              draggable="true"
              @dragstart="onTaskDragStart(task.taskId)"
              @dragover.prevent
              @drop="onTaskDrop(task.taskId)"
              @click="store.selectTask(task.taskId)"
            >
              <label v-if="authStore.canWrite" class="task-check" @click.stop>
                <input
                  type="checkbox"
                  :checked="selectedTaskIds.includes(task.taskId)"
                  @change="toggleTaskSelection(task.taskId)"
                />
              </label>
              <div class="task-card-head">
                <span class="task-no">{{ task.taskNo }}</span>
                <span v-if="task.orderPriority" class="priority-badge" :class="`priority-${task.orderPriority}`">
                  {{ task.orderPriority }}
                </span>
                <span v-if="task.routeCode" class="route-badge">{{ task.routeCode }}</span>
                <StatusBadge :status="task.status" type="task" />
              </div>
              <div class="task-meta">
                <span>订单 #{{ task.orderId }}</span>
                <span v-if="task.waitMinutes != null" class="wait-badge">等待 {{ task.waitMinutes }} 分</span>
                <span v-if="task.openExceptionCount" class="exc-badge">
                  {{ task.openExceptionCount }} 异常
                </span>
              </div>
              <p v-if="taskFailLabel(task)" class="task-reason">{{ taskFailLabel(task) }}</p>
              <div v-if="task.failReasonCode" class="task-fail-detail">
                <ul v-if="taskFailSuggestions(task).length" class="fail-suggestions">
                  <li v-for="(s, i) in taskFailSuggestions(task)" :key="i">{{ s }}</li>
                </ul>
                <div class="fail-links">
                  <router-link
                    v-for="link in taskFailLinks(task)"
                    :key="link.path"
                    :to="link.path"
                    class="fail-link"
                  >
                    {{ link.label }}
                  </router-link>
                </div>
              </div>
              <div v-if="authStore.canWrite" class="task-actions" @click.stop>
                <a-button
                  size="small"
                  type="primary"
                  :loading="actionLoading === `auto-${task.taskId}`"
                  @click="handleAutoAssign(task)"
                >
                  {{ task.status === TaskStatus.MANUAL_PENDING ? '重新自动派车' : '自动派车' }}
                </a-button>
                <a-button size="small" @click="openManualModal(task)">手动派车</a-button>
                <a-button size="small" @click="handleBumpPriority(task)">紧急插队</a-button>
                <a-button type="link" size="small" @click="router.push(`/tasks/${task.taskId}`)">
                  详情
                </a-button>
              </div>
            </article>
            <EmptyState v-if="!store.loading && !store.poolLoading && filteredTaskPool.length === 0" description="暂无待处理任务" />
          </div>
          <div v-if="store.poolHasMore" class="pool-load-more">
            <a-button block :loading="store.poolLoading" @click="store.loadMoreTasks">
              加载更多（{{ filteredTaskPool.length }} / {{ store.poolTotal }}）
            </a-button>
          </div>
        </a-spin>
      </section>

      <!-- 中：主工作区（选中任务详情 + 态势地图） -->
      <section v-if="deferredPanels" class="panel panel-workspace">
        <div class="panel-head">
          <div class="panel-head-title">
            <h2>{{ selectedTask ? '当前任务' : '主工作区' }}</h2>
            <span class="panel-hint">
              {{ selectedTask ? selectedTask.taskNo : '点击左侧任务查看详情与关联车辆' }}
            </span>
          </div>
        </div>
        <div v-if="selectedTask" class="task-detail">
          <div class="task-detail-head">
            <span class="task-no">{{ selectedTask.taskNo }}</span>
            <StatusBadge :status="selectedTask.status" type="task" />
            <span v-if="selectedTask.orderPriority" class="priority-badge" :class="`priority-${selectedTask.orderPriority}`">
              {{ selectedTask.orderPriority }}
            </span>
            <span v-if="selectedTask.routeCode" class="route-badge">{{ selectedTask.routeCode }}</span>
          </div>
          <div class="task-detail-meta">
            <span>订单 #{{ selectedTask.orderId }}</span>
            <span v-if="selectedTask.waitMinutes != null">等待 {{ selectedTask.waitMinutes }} 分</span>
            <span v-if="selectedTask.openExceptionCount" class="exc-badge">{{ selectedTask.openExceptionCount }} 异常</span>
          </div>
          <p v-if="taskFailLabel(selectedTask)" class="task-reason">{{ taskFailLabel(selectedTask) }}</p>
          <div v-if="authStore.canWrite" class="task-actions">
            <a-button
              size="small"
              type="primary"
              :loading="actionLoading === `auto-${selectedTask.taskId}`"
              @click="handleAutoAssign(selectedTask)"
            >
              {{ selectedTask.status === TaskStatus.MANUAL_PENDING ? '重新自动派车' : '自动派车' }}
            </a-button>
            <a-button size="small" @click="openManualModal(selectedTask)">手动派车</a-button>
            <a-button size="small" @click="handleBumpPriority(selectedTask)">紧急插队</a-button>
            <a-button type="link" size="small" @click="router.push(`/tasks/${selectedTask.taskId}`)">详情页</a-button>
          </div>
        </div>
        <div v-else class="task-detail-empty">
          <p>从任务池选择一项，在此查看派车详情与园区态势</p>
        </div>
        <div class="workspace-map">
          <div class="workspace-map-head">
            <span>园区态势</span>
            <span class="panel-hint">选中任务高亮关联车辆</span>
          </div>
          <ParkMiniMap
            :layout="parkLayout"
            :vehicles="parkVehicles"
            :highlight-task-id="store.selectedTaskId"
          />
        </div>
      </section>

      <!-- 右：辅助栏（默认收起异常队列） -->
      <aside v-if="deferredPanels" class="aux-sidebar" :class="{ collapsed: auxCollapsed }">
        <button
          type="button"
          class="aux-toggle"
          :title="auxCollapsed ? '展开辅助栏' : '收起辅助栏'"
          @click="auxCollapsed = !auxCollapsed"
        >
          <span v-if="auxCollapsed" class="aux-badge">{{ store.openExceptionCount }}</span>
          {{ auxCollapsed ? '异常' : '收起' }}
        </button>
        <section v-if="!auxCollapsed" class="panel panel-exceptions">
        <div class="panel-head">
          <h2>异常队列</h2>
          <span class="panel-hint">OPEN · 快捷处置</span>
        </div>
        <a-spin :spinning="store.loading">
          <div class="exception-list">
            <article
              v-for="item in store.openExceptions"
              :key="item.id"
              class="exception-card"
              :class="{ selected: store.selectedExceptionId === item.id }"
              @click="store.selectException(item.id)"
            >
              <div class="exception-card-head">
                <span class="exc-type">{{ getExceptionLabel(item.exceptionType) }}</span>
                <span class="exc-time">{{ formatTime(item.occurTime) }}</span>
              </div>
              <p class="exc-msg">{{ item.exceptionMsg }}</p>
              <div class="exc-link">
                任务 {{ item.taskNo || `#${item.taskId}` }}
                <StatusBadge v-if="item.taskStatus" :status="item.taskStatus" type="task" />
              </div>
              <div v-if="authStore.canWrite" class="exception-actions" @click.stop>
                <a-button
                  size="small"
                  type="primary"
                  :loading="actionLoading === `reassign-${item.id}`"
                  @click="handleExceptionReassign(item)"
                >
                  重新派车
                </a-button>
                <a-button
                  v-if="authStore.isAdmin"
                  size="small"
                  :loading="actionLoading === `field-${item.id}`"
                  @click="handleAssignFieldOps(item)"
                >
                  指派现场
                </a-button>
                <a-button
                  size="small"
                  danger
                  :loading="actionLoading === `fail-${item.id}`"
                  @click="handleExceptionResolve(item, 'MARK_FAILED')"
                >
                  标记失败
                </a-button>
                <a-button
                  size="small"
                  :loading="actionLoading === `close-${item.id}`"
                  @click="handleExceptionResolve(item, 'CLOSE')"
                >
                  关闭
                </a-button>
              </div>
            </article>
            <EmptyState v-if="!store.loading && store.openExceptions.length === 0" description="暂无 OPEN 异常" />
          </div>
        </a-spin>
        </section>
      </aside>
    </div>

    <a-modal
      v-model:open="manualModalVisible"
      title="手动派车"
      ok-text="确认派车"
      :confirm-loading="manualLoading"
      @ok="submitManualAssign"
    >
      <a-form layout="vertical">
        <a-form-item label="任务">
          <a-input :value="manualTask?.taskNo" disabled />
        </a-form-item>
        <div v-if="manualTask?.orderPriority || manualTask?.routeCode" class="manual-task-scope">
          <span class="batch-group-label">影响范围：</span>
          <span v-if="manualTask?.orderPriority" class="batch-group-tag priority-tag">{{ manualTask.orderPriority }}</span>
          <span v-if="manualTask?.routeCode" class="batch-group-tag route-tag">{{ manualTask.routeCode }}</span>
        </div>
        <a-form-item label="选择车辆" required>
          <a-select
            v-model:value="manualForm.vehicleId"
            placeholder="在线且空闲的车辆"
            show-search
            :loading="vehiclesLoading"
            :filter-option="filterVehicle"
          >
            <a-select-option
              v-for="v in assignableVehicles"
              :key="v.vehicleId"
              :value="v.vehicleId"
            >
              <span class="vehicle-option">
                <span>{{ v.vehicleCode }} · {{ v.batteryLevel }}% · {{ dispatchLabel(v.dispatchStatus) }}</span>
                <span class="vehicle-soc" :class="socColorClass(v.batteryLevel)">{{ v.batteryLevel }}%</span>
                <span v-if="(v.batteryLevel ?? 0) < 30" class="low-soc-badge">低电!</span>
              </span>
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="manualForm.remark" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="batchReassignVisible"
      title="批量改派"
      ok-text="确认改派"
      :confirm-loading="batchLoading"
      @ok="submitBatchReassign"
    >
      <p class="batch-modal-hint">将对 {{ selectedTaskIds.length }} 个任务改派到同一车辆</p>
      <div v-if="selectedVehicleGroups.length" class="batch-vehicle-groups">
        <span class="batch-group-label">当前所属车辆：</span>
        <span v-for="g in selectedVehicleGroups" :key="g.vehicleCode" class="batch-group-tag vehicle-tag">
          {{ g.vehicleCode }} ×{{ g.count }}
        </span>
      </div>
      <a-form layout="vertical">
        <a-form-item label="选择目标车辆" required>
          <a-select
            v-model:value="batchReassignVehicleId"
            placeholder="在线且空闲的车辆"
            :loading="vehiclesLoading"
            :options="assignableVehicleOptions"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- V5-W3: 批量取消二次确认 -->
    <a-modal
      v-model:open="batchCancelConfirmVisible"
      title="确认批量取消"
      ok-text="确认取消"
      cancel-text="再想想"
      :confirm-loading="batchLoading"
      @ok="executeBatchCancel"
    >
      <div class="confirm-body">
        <a-alert type="warning" show-icon message="此操作不可撤回，取消后车辆将释放，关联异常自动关闭" class="confirm-alert" />
        <p class="confirm-summary">将对以下 <strong>{{ selectedTaskIds.length }}</strong> 个任务执行批量取消：</p>
        <div class="confirm-task-list">
          <div v-for="task in selectedTaskPreview" :key="task.taskId" class="confirm-task-item">
            <span class="confirm-task-no">{{ task.taskNo }}</span>
            <StatusBadge :status="task.status" type="task" />
            <span v-if="task.routeCode" class="route-badge">{{ task.routeCode }}</span>
            <span v-if="task.orderPriority" class="priority-badge" :class="`priority-${task.orderPriority}`">{{ task.orderPriority }}</span>
          </div>
          <div v-if="selectedTaskIds.length > 10" class="confirm-task-more">等共 {{ selectedTaskIds.length }} 项</div>
        </div>
      </div>
    </a-modal>

    <!-- V5-W4: 批量自动派车二次确认 -->
    <a-modal
      v-model:open="batchAutoConfirmVisible"
      title="确认批量自动派车"
      ok-text="确认派车"
      cancel-text="再想想"
      :confirm-loading="batchLoading"
      @ok="executeBatchAuto"
    >
      <div class="confirm-body">
        <p class="confirm-summary">将为以下 <strong>{{ selectedTaskIds.length }}</strong> 个任务自动分配最优车辆：</p>
        <div class="confirm-groups">
          <span v-for="g in selectedRouteGroups" :key="g.routeCode" class="batch-group-tag route-tag">
            {{ g.routeCode }} ×{{ g.count }}
          </span>
          <span v-for="g in selectedPriorityGroups" :key="g.priority" class="batch-group-tag priority-tag">
            {{ g.priority }} ×{{ g.count }}
          </span>
        </div>
        <div class="confirm-task-list">
          <div v-for="task in selectedTaskPreview" :key="task.taskId" class="confirm-task-item">
            <span class="confirm-task-no">{{ task.taskNo }}</span>
            <span v-if="task.routeCode" class="route-badge">{{ task.routeCode }}</span>
            <span v-if="task.orderPriority" class="priority-badge" :class="`priority-${task.orderPriority}`">{{ task.orderPriority }}</span>
          </div>
          <div v-if="selectedTaskIds.length > 10" class="confirm-task-more">等共 {{ selectedTaskIds.length }} 项</div>
        </div>
      </div>
    </a-modal>

    <!-- V5-W4: 批量改派二次确认 -->
    <a-modal
      v-model:open="batchReassignConfirmVisible"
      title="确认批量改派"
      ok-text="确认改派"
      cancel-text="再想想"
      :confirm-loading="batchLoading"
      @ok="executeBatchReassign"
    >
      <div class="confirm-body">
        <a-alert type="warning" show-icon message="改派后原车辆将释放" class="confirm-alert" />
        <p class="confirm-summary">将 <strong>{{ selectedTaskIds.length }}</strong> 个任务改派至：</p>
        <div class="confirm-target-vehicle">
          <TagOutlined /> {{ targetVehicleLabel }}
        </div>
        <div v-if="selectedVehicleGroups.length" class="confirm-current-vehicles">
          <span class="batch-group-label">当前所属车辆：</span>
          <span v-for="g in selectedVehicleGroups" :key="g.vehicleCode" class="batch-group-tag vehicle-tag">
            {{ g.vehicleCode }} ×{{ g.count }}
          </span>
        </div>
      </div>
    </a-modal>

    <a-modal
      v-model:open="batchResultVisible"
      title="批量操作结果"
      :footer="null"
      width="560px"
    >
      <p class="batch-result-summary">
        成功 {{ batchResult?.successCount ?? 0 }} / 失败 {{ batchResult?.failureCount ?? 0 }}（共 {{ batchResult?.total ?? 0 }}）
      </p>
      <a-table
        size="small"
        :pagination="false"
        row-key="taskId"
        :data-source="batchResult?.results || []"
        :columns="batchResultColumns"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'success'">
            <a-tag :color="record.success ? 'success' : 'error'">{{ record.success ? '成功' : '失败' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'detail'">
            <div v-if="!record.success" class="batch-fail-detail">
              <strong>{{ record.reasonMessage || record.message }}</strong>
              <ul v-if="record.suggestions?.length">
                <li v-for="(s, i) in record.suggestions" :key="i">{{ s }}</li>
              </ul>
            </div>
            <span v-else>{{ record.message || '-' }}</span>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- V5-W6: 保存筛选视图 -->
    <a-modal
      v-model:open="saveViewModalVisible"
      title="保存筛选视图"
      ok-text="保存"
      @ok="confirmSaveView"
    >
      <a-input v-model:value="saveViewName" placeholder="输入视图名称，如「早高峰 P1」" @keyup.enter="confirmSaveView" />
      <p class="save-view-hint">将保存当前筛选条件（优先级、等待时间、线路等），可在上方模板栏快速切换</p>
    </a-modal>

    <ParkDeliveryOrderModal
      v-model:open="createOrderModalOpen"
      :park-id="parkScope.selectedParkId"
      :prefill="pendingReorderData"
      @created="refreshAll"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWorkbenchShortcuts } from '@/composables/useWorkbenchShortcuts'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, TagOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ParkMiniMap from '@/components/workbench/ParkMiniMap.vue'
import ParkDeliveryOrderModal from '@/components/park/ParkDeliveryOrderModal.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import { useWorkbenchStore } from '@/stores/workbench'
import { useAuthStore } from '@/stores/auth'
import { useParkScopeStore } from '@/stores/parkScope'
import { queryVehicles } from '@/api/vehicle'
import { fetchTrafficSummary } from '@/api/traffic'
import { fetchDispatchPauseStatus, setDispatchPause } from '@/api/dispatchPause'
import { batchAutoAssign, batchCancelTasks, batchReassignTasks, batchUnassignTasks, bumpTaskPriority } from '@/api/task'
import { globalSearch } from '@/api/search'
import type { GlobalSearchItem } from '@/types/phase10'
import { fetchRoutes } from '@/api/vertical'
import { assignFieldOps } from '@/api/fieldOps'
import { fetchUsers } from '@/api/auth'
import { useChargingAwareness } from '@/composables/useChargingAwareness'
import { useDispatchPrediction } from '@/composables/useDispatchPrediction'
import type { DispatchRoute } from '@/api/vertical'
import { exceptionTypeMap } from '@/constants/statusMap'
import {
  explainFromAssignResponse,
  failActionLinks,
  failReasonLabel,
  normalizeFailCode,
} from '@/constants/dispatchFail'
import type { BatchTaskResult } from '@/types/operateLog'
import type { TrafficSummary } from '@/types/traffic'
import { DispatchStatus, TaskStatus } from '@/constants/enums'
import type { TaskAdminListItem } from '@/types/task'
import type { ExceptionAdminListItem } from '@/types/exception'

const router = useRouter()
const route = useRoute()
const store = useWorkbenchStore()
const authStore = useAuthStore()
const parkScope = useParkScopeStore()
const workbenchShortcutsEnabled = computed(() => route.path === '/workbench')
const trafficSummary = ref<TrafficSummary | null>(null)
const dispatchPaused = ref(false)
const draggingTaskId = ref<number | null>(null)
const createOrderModalOpen = ref(false)
const hasManualTaskOrder = computed(() => store.manualTaskOrder.length > 0)

const parkLayout = computed(() => store.parkLayout)
const parkVehicles = computed(() => store.parkVehicles)
interface ManualVehicleOption {
  vehicleId: number
  vehicleCode: string
  vehicleName: string
  batteryLevel: number
  dispatchStatus: string
}

const assignableVehicles = ref<ManualVehicleOption[]>([])
const actionLoading = ref<string | null>(null)
const manualModalVisible = ref(false)
const manualLoading = ref(false)
const vehiclesLoading = ref(false)
const manualTask = ref<TaskAdminListItem | null>(null)
const manualForm = reactive({ vehicleId: undefined as number | undefined, remark: '' })
const selectedTaskIds = ref<number[]>([])
const batchLoading = ref(false)
const batchReassignVisible = ref(false)
const batchReassignVehicleId = ref<number | undefined>()
const batchResultVisible = ref(false)
const batchResult = ref<BatchTaskResult | null>(null)
const routeFilter = ref<number | undefined>()
const routeOptions = ref<{ label: string; value: number }[]>([])

// V5-E4: 充电感知
const {
  fleetLowSocWarning,
  lowSocCount,
  lowSocVehicleCodes,
  checkVehicleSocBeforeAssign,
} = useChargingAwareness(assignableVehicles)

// V5-N4: 派车预测
const dispatchPrediction = useDispatchPrediction()

// 二次确认状态
const batchCancelConfirmVisible = ref(false)
const batchAutoConfirmVisible = ref(false)
const batchReassignConfirmVisible = ref(false)

// ── V5-W5 操作撤销 ──
type BatchOpType = 'AUTO_ASSIGN' | 'CANCEL' | 'REASSIGN'
interface LastBatchOp {
  type: BatchOpType
  taskIds: number[]
  vehicleId?: number
  originalVehicles?: Map<number, number | null> // taskId -> vehicleId (for reassign undo)
  label: string
  timestamp: number
}
const lastBatchOp = ref<LastBatchOp | null>(null)
const undoTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

function showUndoNotification(op: LastBatchOp) {
  // Cancel operations cannot be undone - show plain success message without undo button
  if (op.type === 'CANCEL') {
    message.success(`${op.label} 成功`)
    return
  }
  // Clear previous timeout
  if (undoTimeout.value) clearTimeout(undoTimeout.value)
  lastBatchOp.value = op
  const key = `undo-${Date.now()}`
  message.success({
    content: () =>
      h('div', { class: 'undo-notify' }, [
        h('span', `${op.label} 成功`),
        h('a-button', {
          size: 'small',
          type: 'primary',
          style: 'margin-left: 12px',
          onClick: () => executeUndo(key),
        }, '撤销'),
        h('span', { style: 'margin-left: 8px; font-size: 11px; color: #8c9bab' }, '30s'),
      ]),
    key,
    duration: 30,
  })
  undoTimeout.value = setTimeout(() => {
    if (lastBatchOp.value === op) lastBatchOp.value = null
  }, 30000)
}

async function executeUndo(messageKey: string) {
  const op = lastBatchOp.value
  if (!op) {
    message.warning('无可撤销的操作')
    return
  }
  message.destroy(messageKey)
  lastBatchOp.value = null
  if (undoTimeout.value) clearTimeout(undoTimeout.value)

  batchLoading.value = true
  try {
    if (op.type === 'AUTO_ASSIGN') {
      // Undo auto assign = cancel the tasks
      await batchCancelTasks([...op.taskIds], '撤销自动派车')
      message.success(`已撤销自动派车（${op.taskIds.length} 项）`)
    } else if (op.type === 'REASSIGN' && op.originalVehicles) {
      const tasksByVehicle = new Map<number, number[]>()
      const unassignedTaskIds: number[] = []
      for (const taskId of op.taskIds) {
        const originalVehicleId = op.originalVehicles.get(taskId)
        if (originalVehicleId == null) {
          unassignedTaskIds.push(taskId)
        } else {
          const taskIds = tasksByVehicle.get(originalVehicleId) || []
          taskIds.push(taskId)
          tasksByVehicle.set(originalVehicleId, taskIds)
        }
      }
      for (const [vehicleId, taskIds] of tasksByVehicle.entries()) {
        await batchReassignTasks(taskIds, vehicleId, '撤销改派')
      }
      if (unassignedTaskIds.length > 0) {
        await batchUnassignTasks(unassignedTaskIds, '撤销改派')
      }
      message.success(`已撤销改派（${op.taskIds.length} 项）`)
    } else if (op.type === 'CANCEL') {
      message.warning('取消失败的操作无法撤销')
      return
    }
    await refreshAll()
  } catch {
    // interceptor handles
  } finally {
    batchLoading.value = false
  }
}

// ── V5-W6/W7 筛选视图与模板 ──
const filterPriority = ref<string | undefined>()
const filterWaitMin = ref<number | undefined>()
const filterHasException = ref<boolean | undefined>()
const filterOpenExceptionOnly = ref(false)
const filterVehicleAssigned = ref<string | undefined>() // 'assigned' | 'unassigned' | undefined

// V5-T4: 全文搜索
const searchKeyword = ref('')
const searchResults = ref<GlobalSearchItem[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null
const searchOptions = computed(() =>
  searchResults.value.map((item) => ({
    value: `${item.type}-${item.id}`,
    label: item.title,
    hint: item.subtitle || item.code,
    group: item.type,
    item,
  }))
)

async function onSearchInput(query: string) {
  if (searchTimer) clearTimeout(searchTimer)
  if (query.trim().length < 2) {
    searchResults.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      const res = await globalSearch(query, 10)
      searchResults.value = res.data.items || []
    } catch {
      searchResults.value = []
    }
  }, 280)
}

function onSearchSelect(_value: string, option: Record<string, unknown>) {
  const hit = (option as { item: GlobalSearchItem }).item
  if (hit?.routePath) {
    router.push(hit.routePath)
  }
  searchKeyword.value = ''
  searchResults.value = []
}

function toggleCommandPalette() {
  const event = new KeyboardEvent('keydown', { metaKey: true, ctrlKey: true, key: 'k', bubbles: true })
  window.dispatchEvent(event)
}

// V5-T5: 再来一单
const pendingReorderData = ref<{ pickupStationId: number; dropoffStationId: number } | null>(null)

// V5-T3: 骨架屏 + 分片加载
const showSkeleton = computed(() => store.loading && store.poolTotal === 0)
const deferredPanels = ref(false)
const auxCollapsed = ref(true)

interface RiskBandItem {
  key: string
  level: 'critical' | 'warning'
  message: string
  action?: () => void
}

const riskBandItems = computed((): RiskBandItem[] => {
  const items: RiskBandItem[] = []
  const prediction = dispatchPrediction.dispatchPredictionAlert.value
  if (dispatchPrediction.dispatchRiskLevel.value !== 'safe' && prediction?.message) {
    items.push({
      key: 'dispatch-prediction',
      level: dispatchPrediction.dispatchRiskLevel.value === 'critical' ? 'critical' : 'warning',
      message: prediction.message,
    })
  }
  if (fleetLowSocWarning.value) {
    items.push({
      key: 'low-soc',
      level: 'critical',
      message: `全车队低电：${lowSocCount.value} 台 SOC < 30%（${lowSocVehicleCodes.value.join('、')}）`,
      action: () => { void router.push('/energy/charging-report') },
    })
  }
  if (trafficSummary.value && (trafficSummary.value.maxCongestionLevel >= 2 || trafficSummary.value.pausedZoneCount > 0)) {
    const t = trafficSummary.value
    const parts = [`拥堵 L${t.maxCongestionLevel}`]
    if (t.highCongestionSegmentCount > 0) parts.push(`${t.highCongestionSegmentCount} 条高拥堵`)
    if (t.pausedZoneCount > 0) parts.push(`${t.pausedZoneCount} 个管制区`)
    items.push({
      key: 'traffic',
      level: t.maxCongestionLevel >= 3 ? 'critical' : 'warning',
      message: parts.join(' · '),
      action: () => { void router.push('/infrastructure/traffic') },
    })
  }
  return items
})

// Saved views
interface SavedView {
  name: string
  priority?: string
  waitMin?: number
  hasException?: boolean
  openExceptionOnly?: boolean
  vehicleAssigned?: string
  routeId?: number
  taskFilter?: string
  builtin?: boolean
}
const SAVED_VIEWS_KEY = 'fsd_workbench_saved_views'
const savedViews = ref<SavedView[]>(loadSavedViews())
const activeViewName = ref<string | null>(null)
const saveViewModalVisible = ref(false)
const saveViewName = ref('')

function loadSavedViews(): SavedView[] {
  try {
    const raw = localStorage.getItem(SAVED_VIEWS_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}
function persistSavedViews() {
  localStorage.setItem(SAVED_VIEWS_KEY, JSON.stringify(savedViews.value))
}

const quickFilterTemplates: SavedView[] = [
  { name: '待处理紧急', builtin: true, priority: 'P1', waitMin: 10 },
  { name: '等待充电', builtin: true, openExceptionOnly: true },
  { name: '长超时', builtin: true, waitMin: 30 },
]

function applySavedView(view: SavedView) {
  filterPriority.value = view.priority
  filterWaitMin.value = view.waitMin
  filterHasException.value = view.hasException
  filterOpenExceptionOnly.value = view.openExceptionOnly ?? false
  filterVehicleAssigned.value = view.vehicleAssigned
  if (view.routeId !== undefined) routeFilter.value = view.routeId
  if (view.taskFilter) {
    const key = view.taskFilter as 'ALL' | 'PENDING' | 'MANUAL_PENDING'
    if (key !== store.taskFilter) {
      store.taskFilter = key
      void store.fetchTaskPool()
    }
  }
  activeViewName.value = view.name
}

function openSaveViewModal() {
  saveViewName.value = ''
  saveViewModalVisible.value = true
}

function confirmSaveView() {
  const name = saveViewName.value.trim()
  if (!name) { message.warning('请输入视图名称'); return }
  const existing = savedViews.value.findIndex((v) => v.name === name)
  const view: SavedView = {
    name,
    priority: filterPriority.value,
    waitMin: filterWaitMin.value,
    hasException: filterHasException.value,
    openExceptionOnly: filterOpenExceptionOnly.value,
    vehicleAssigned: filterVehicleAssigned.value,
    routeId: routeFilter.value,
    taskFilter: store.taskFilter === 'ALL' ? undefined : store.taskFilter,
  }
  if (existing >= 0) {
    savedViews.value[existing] = view
  } else {
    savedViews.value.push(view)
  }
  persistSavedViews()
  saveViewModalVisible.value = false
  activeViewName.value = name
  message.success(`视图「${name}」已保存`)
}

function deleteSavedView(name: string) {
  savedViews.value = savedViews.value.filter((v) => v.name !== name)
  persistSavedViews()
  if (activeViewName.value === name) activeViewName.value = null
}

function clearAllFilters() {
  filterPriority.value = undefined
  filterWaitMin.value = undefined
  filterHasException.value = undefined
  filterOpenExceptionOnly.value = false
  filterVehicleAssigned.value = undefined
  routeFilter.value = undefined
  activeViewName.value = null
}

const filteredTaskPool = computed(() => {
  let tasks = store.taskPool
  // Route filter
  if (routeFilter.value) {
    tasks = tasks.filter((t) => t.routeId === routeFilter.value)
  }
  // Priority filter
  if (filterPriority.value) {
    tasks = tasks.filter((t) => t.orderPriority === filterPriority.value)
  }
  // Wait time filter
  if (filterWaitMin.value != null) {
    tasks = tasks.filter((t) => (t.waitMinutes ?? 0) >= filterWaitMin.value!)
  }
  // Has exception filter
  if (filterHasException.value !== undefined) {
    tasks = tasks.filter((t) => filterHasException.value ? (t.openExceptionCount ?? 0) > 0 : (t.openExceptionCount ?? 0) === 0)
  }
  // Open exception only filter
  if (filterOpenExceptionOnly.value) {
    tasks = tasks.filter((t) => (t.openExceptionCount ?? 0) > 0)
  }
  // Vehicle assigned filter
  if (filterVehicleAssigned.value === 'assigned') {
    tasks = tasks.filter((t) => t.vehicleId != null)
  } else if (filterVehicleAssigned.value === 'unassigned') {
    tasks = tasks.filter((t) => t.vehicleId == null)
  }
  return tasks
})

const selectedTask = computed(() => {
  if (!store.selectedTaskId) return null
  return filteredTaskPool.value.find((t) => t.taskId === store.selectedTaskId)
    || store.taskPool.find((t) => t.taskId === store.selectedTaskId)
    || null
})

const hasActiveFilters = computed(() =>
  !!filterPriority.value ||
  filterWaitMin.value != null ||
  filterHasException.value !== undefined ||
  filterOpenExceptionOnly.value ||
  !!filterVehicleAssigned.value ||
  !!routeFilter.value
)

const batchResultColumns = [
  { title: '任务', dataIndex: 'taskNo', key: 'taskNo', width: 120 },
  { title: '结果', key: 'success', width: 72 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '说明', key: 'detail' },
]

const selectedRouteGroups = computed(() => {
  const groups: Record<string, { routeCode: string; count: number }> = {}
  for (const id of selectedTaskIds.value) {
    const task = store.taskPool.find((t) => t.taskId === id)
    if (!task) continue
    const key = task.routeCode || `route-${task.routeId}`
    if (!groups[key]) {
      groups[key] = { routeCode: task.routeCode || `线路#${task.routeId}`, count: 0 }
    }
    groups[key].count++
  }
  return Object.values(groups).sort((a, b) => b.count - a.count)
})

const selectedPriorityGroups = computed(() => {
  const groups: Record<string, { priority: string; count: number }> = {}
  for (const id of selectedTaskIds.value) {
    const task = store.taskPool.find((t) => t.taskId === id)
    if (!task) continue
    const key = task.orderPriority || 'UNSET'
    if (!groups[key]) {
      groups[key] = { priority: task.orderPriority || '未设置', count: 0 }
    }
    groups[key].count++
  }
  return Object.values(groups).sort((a, b) => b.count - a.count)
})

const selectedVehicleGroups = computed(() => {
  const groups: Record<string, { vehicleId: number | null; vehicleCode: string; count: number }> = {}
  for (const id of selectedTaskIds.value) {
    const task = store.taskPool.find((t) => t.taskId === id)
    if (!task) continue
    const key = task.vehicleId != null ? `v-${task.vehicleId}` : 'unassigned'
    if (!groups[key]) {
      const vehicle = store.parkVehicles.find((v) => v.vehicleId === task.vehicleId)
      groups[key] = {
        vehicleId: task.vehicleId,
        vehicleCode: task.vehicleId != null ? (vehicle?.vehicleCode || `#${task.vehicleId}`) : '未派车',
        count: 0,
      }
    }
    groups[key].count++
  }
  return Object.values(groups).sort((a, b) => b.count - a.count)
})

const selectedTaskPreview = computed(() => {
  const ids = selectedTaskIds.value.slice(0, 10)
  return ids.map((id) => store.taskPool.find((t) => t.taskId === id)).filter(Boolean) as TaskAdminListItem[]
})

const congestionBarClass = computed(() => {
  const level = trafficSummary.value?.maxCongestionLevel ?? 0
  if (level >= 3) return 'congestion-critical'
  if (level >= 2) return 'congestion-warn'
  return 'congestion-ok'
})

const assignableVehicleOptions = computed(() =>
  assignableVehicles.value.map((v) => ({
    label: `${v.vehicleCode} · ${v.batteryLevel}%`,
    value: v.vehicleId,
  }))
)

const targetVehicleLabel = computed(() => {
  if (!batchReassignVehicleId.value) return ''
  const v = assignableVehicles.value.find((x) => x.vehicleId === batchReassignVehicleId.value)
  return v ? `${v.vehicleCode} · ${v.batteryLevel}% · ${dispatchLabel(v.dispatchStatus)}` : ''
})

const taskTabs = computed(() => [
  { key: 'ALL' as const, label: '全部', count: store.interventionTotal },
  { key: 'PENDING' as const, label: '待派单', count: store.pendingCount },
  { key: 'MANUAL_PENDING' as const, label: '人工', count: store.manualPendingCount },
])

function onTaskTabChange(key: 'ALL' | 'PENDING' | 'MANUAL_PENDING') {
  store.taskFilter = key
  void store.fetchTaskPool()
}

function formatTime(value: string) {
  return dayjs(value).format('MM-DD HH:mm')
}

function getExceptionLabel(type: string) {
  return exceptionTypeMap[type as keyof typeof exceptionTypeMap]?.label || type
}

function dispatchLabel(status: string) {
  if (status === DispatchStatus.IDLE) return '空闲'
  if (status === DispatchStatus.BUSY) return '忙碌'
  return '不可用'
}

function socColorClass(soc: number): string {
  if (soc < 20) return 'soc-critical'
  if (soc < 50) return 'soc-warn'
  return 'soc-ok'
}

function taskFailLabel(task: TaskAdminListItem) {
  return failReasonLabel(task.failReasonCode, task.failReasonMsg)
}

function taskFailSuggestions(task: TaskAdminListItem) {
  const code = normalizeFailCode(task.failReasonCode)
  const defaults: Record<string, string[]> = {
    NO_IDLE_VEHICLE: ['检查在线空闲车辆', '尝试手动派车'],
    LOW_BATTERY: ['引导车辆充电', '降低 SOC 阈值或等待'],
    ROUTE_BLOCKED: ['检查禁用路段与管制区', '确认取货点可达'],
    HUB_CAPACITY_FULL: ['枢纽容量已满（预留）'],
  }
  return defaults[code] || []
}

function taskFailLinks(task: TaskAdminListItem) {
  return failActionLinks(task.failReasonCode)
}

function filterVehicle(input: string, option: { value: number }) {
  const v = assignableVehicles.value.find((item) => item.vehicleId === option.value)
  if (!v) return false
  const label = `${v.vehicleCode} ${v.vehicleName}`.toLowerCase()
  return label.includes(input.toLowerCase())
}

function vehiclesFromWorkbench(): ManualVehicleOption[] {
  return store.parkVehicles
    .filter((v) => v.onlineStatus === 'ONLINE' && v.dispatchStatus === DispatchStatus.IDLE)
    .map((v) => ({
      vehicleId: v.vehicleId,
      vehicleCode: v.vehicleCode,
      vehicleName: v.vehicleName,
      batteryLevel: v.batteryLevel,
      dispatchStatus: v.dispatchStatus,
    }))
}

async function loadAssignableVehicles() {
  const fromWorkbench = vehiclesFromWorkbench()
  if (fromWorkbench.length > 0) {
    assignableVehicles.value = fromWorkbench
    return
  }
  vehiclesLoading.value = true
  try {
    const res = await queryVehicles({
      onlineStatus: 'ONLINE' as any,
      dispatchStatus: DispatchStatus.IDLE,
      pageNo: 1,
      pageSize: 100,
    })
    assignableVehicles.value = (res.data.records || []).map((v) => ({
      vehicleId: v.vehicleId,
      vehicleCode: v.vehicleCode,
      vehicleName: v.vehicleName,
      batteryLevel: v.batteryLevel ?? 0,
      dispatchStatus: v.dispatchStatus,
    }))
  } catch {
    assignableVehicles.value = []
  } finally {
    vehiclesLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([store.fetchQueue(), loadTrafficSummary()])
}

async function loadTrafficSummary() {
  const parkId = parkScope.resolveLayoutParkId()
  if (!parkId) {
    trafficSummary.value = null
    return
  }
  try {
    const res = await fetchTrafficSummary(parkId)
    trafficSummary.value = res.data
  } catch {
    trafficSummary.value = null
  }
}

async function handleAutoAssign(task: TaskAdminListItem) {
  actionLoading.value = `auto-${task.taskId}`
  try {
    const result = await store.dispatchAuto(task.taskId)
    if (result.status === TaskStatus.ASSIGNED) {
      const explanation = result.assignExplanation
      const vehicleCode = result.selectedVehicleCode
      const score = result.assignScore
      let msg = '自动派车成功'
      if (vehicleCode) msg += ` · ${vehicleCode}`
      if (explanation) msg += `：${explanation}`
      else if (score != null) msg += `（评分 ${score.toFixed(1)}）`
      message.success(msg, 5)
    } else {
      const explained = explainFromAssignResponse(result)
      message.error(`${explained.reasonMessage}${explained.suggestions.length ? ' · ' + explained.suggestions[0] : ''}`, 6)
    }
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

function clearSelection() {
  selectedTaskIds.value = []
}

function toggleTaskSelection(taskId: number) {
  const idx = selectedTaskIds.value.indexOf(taskId)
  if (idx >= 0) {
    selectedTaskIds.value.splice(idx, 1)
  } else {
    selectedTaskIds.value.push(taskId)
  }
}

function selectSameRoute() {
  const routeCounts: Record<string, number> = {}
  for (const id of selectedTaskIds.value) {
    const task = store.taskPool.find((t) => t.taskId === id)
    if (!task || !task.routeCode) continue
    routeCounts[task.routeCode] = (routeCounts[task.routeCode] || 0) + 1
  }
  let bestRoute = ''
  let bestCount = 0
  for (const [route, count] of Object.entries(routeCounts)) {
    if (count > bestCount) {
      bestCount = count
      bestRoute = route
    }
  }
  if (!bestRoute) return
  selectedTaskIds.value = store.taskPool
    .filter((t) => t.routeCode === bestRoute)
    .map((t) => t.taskId)
}

function selectSamePriority() {
  const priorityCounts: Record<string, number> = {}
  for (const id of selectedTaskIds.value) {
    const task = store.taskPool.find((t) => t.taskId === id)
    if (!task || !task.orderPriority) continue
    priorityCounts[task.orderPriority] = (priorityCounts[task.orderPriority] || 0) + 1
  }
  let bestPriority = ''
  let bestCount = 0
  for (const [priority, count] of Object.entries(priorityCounts)) {
    if (count > bestCount) {
      bestCount = count
      bestPriority = priority
    }
  }
  if (!bestPriority) return
  selectedTaskIds.value = store.taskPool
    .filter((t) => t.orderPriority === bestPriority)
    .map((t) => t.taskId)
}

function handleBatchAutoPreConfirm() {
  if (selectedTaskIds.value.length === 0) return
  batchAutoConfirmVisible.value = true
}

async function executeBatchAuto() {
  batchLoading.value = true
  batchAutoConfirmVisible.value = false
  try {
    const taskIds = [...selectedTaskIds.value]
    const res = await batchAutoAssign(taskIds)
    batchResult.value = res.data
    batchResultVisible.value = true
    clearSelection()
    showUndoNotification({
      type: 'AUTO_ASSIGN',
      taskIds,
      label: '批量自动派车',
      timestamp: Date.now(),
    })
    await refreshAll()
  } finally {
    batchLoading.value = false
  }
}

function handleBatchCancelPreConfirm() {
  if (selectedTaskIds.value.length === 0) return
  batchCancelConfirmVisible.value = true
}

async function executeBatchCancel() {
  batchLoading.value = true
  batchCancelConfirmVisible.value = false
  try {
    const taskIds = [...selectedTaskIds.value]
    const res = await batchCancelTasks(taskIds)
    batchResult.value = res.data
    batchResultVisible.value = true
    clearSelection()
    showUndoNotification({
      type: 'CANCEL',
      taskIds,
      label: '批量取消',
      timestamp: Date.now(),
    })
    await refreshAll()
  } finally {
    batchLoading.value = false
  }
}

function openBatchReassign() {
  batchReassignVehicleId.value = undefined
  loadAssignableVehicles()
  batchReassignVisible.value = true
}

async function submitBatchReassign() {
  if (!batchReassignVehicleId.value || selectedTaskIds.value.length === 0) {
    message.warning('请选择车辆')
    return
  }
  batchReassignVisible.value = false
  batchReassignConfirmVisible.value = true
}

async function executeBatchReassign() {
  if (!batchReassignVehicleId.value) return
  batchLoading.value = true
  batchReassignConfirmVisible.value = false
  try {
    const taskIds = [...selectedTaskIds.value]
    // Capture original vehicles for undo
    const originalVehicles = new Map<number, number | null>()
    for (const id of taskIds) {
      const task = store.taskPool.find((t) => t.taskId === id)
      originalVehicles.set(id, task?.vehicleId ?? null)
    }
    const res = await batchReassignTasks(taskIds, batchReassignVehicleId.value)
    batchResult.value = res.data
    batchResultVisible.value = true
    clearSelection()
    showUndoNotification({
      type: 'REASSIGN',
      taskIds,
      vehicleId: batchReassignVehicleId.value,
      originalVehicles,
      label: '批量改派',
      timestamp: Date.now(),
    })
    await refreshAll()
  } finally {
    batchLoading.value = false
  }
}

function openManualModal(task: TaskAdminListItem) {
  manualTask.value = task
  manualForm.vehicleId = undefined
  manualForm.remark = ''
  manualModalVisible.value = true
  loadAssignableVehicles()
}

async function submitManualAssign() {
  if (!manualTask.value || !manualForm.vehicleId) {
    message.warning('请选择车辆')
    return
  }
  // V5-E4: 低电派单警告
  const socCheck = checkVehicleSocBeforeAssign(manualForm.vehicleId)
  if (socCheck?.recommendReturnToCharge) {
    message.warn(socCheck.message, 4)
  }
  manualLoading.value = true
  try {
    const result = await store.dispatchManual(
      manualTask.value.taskId,
      manualForm.vehicleId,
      manualForm.remark
    )
    if (result.status === TaskStatus.ASSIGNED) {
      message.success('手动派车成功')
      manualModalVisible.value = false
    } else {
      message.warning(result.message || '派车未成功')
    }
  } catch {
    // interceptor handles
  } finally {
    manualLoading.value = false
  }
}

async function handleExceptionReassign(item: ExceptionAdminListItem) {
  if (!item.taskId) return
  actionLoading.value = `reassign-${item.id}`
  try {
    const result = await store.dispatchAuto(item.taskId)
    if (result.status === TaskStatus.ASSIGNED) {
      const explanation = result.assignExplanation
      const vehicleCode = result.selectedVehicleCode
      let msg = '重新派车成功，异常已自动关闭'
      if (vehicleCode) msg += ` · ${vehicleCode}`
      if (explanation) msg += `：${explanation}`
      message.success(msg, 5)
    } else {
      const explained = explainFromAssignResponse(result)
      message.error(explained.reasonMessage || '重新派车失败', 5)
    }
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

async function handleAssignFieldOps(item: ExceptionAdminListItem) {
  actionLoading.value = `field-${item.id}`
  try {
    const users = (await fetchUsers()).data.filter((u) => u.role === 'FIELD_OPS' && u.status === 'ACTIVE')
    if (users.length === 0) {
      message.warning('暂无 FIELD_OPS 用户，请先在用户管理创建')
      return
    }
    const assignee = users[0]
    await assignFieldOps(item.id, assignee.id, '工作台指派')
    message.success(`已指派给 ${assignee.displayName || assignee.username}`)
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

async function handleBumpPriority(task: TaskAdminListItem) {
  actionLoading.value = `bump-${task.taskId}`
  try {
    await bumpTaskPriority(task.taskId)
    message.success(`已将 ${task.taskNo} 提升至 P0 优先级`)
    await refreshAll()
  } finally {
    actionLoading.value = null
  }
}

function onTaskDragStart(taskId: number) {
  draggingTaskId.value = taskId
}

function resetTaskOrder() {
  store.clearManualTaskOrder()
  message.success('已恢复服务端默认排序')
}

function onTaskDrop(targetTaskId: number) {
  if (draggingTaskId.value == null || draggingTaskId.value === targetTaskId) return
  const ids = store.taskPool.map((t) => t.taskId)
  const from = ids.indexOf(draggingTaskId.value)
  const to = ids.indexOf(targetTaskId)
  if (from < 0 || to < 0) return
  ids.splice(from, 1)
  ids.splice(to, 0, draggingTaskId.value)
  store.reorderTasks(ids)
  draggingTaskId.value = null
}

async function handleExceptionResolve(item: ExceptionAdminListItem, action: 'MARK_FAILED' | 'CLOSE') {
  actionLoading.value = `${action === 'MARK_FAILED' ? 'fail' : 'close'}-${item.id}`
  try {
    await store.resolveOpenException(item.id, {
      resolverId: 'admin',
      resolverName: '调度员',
      action,
      remark: action === 'MARK_FAILED' ? '工作台标记失败' : '工作台关闭异常',
    })
    message.success(action === 'MARK_FAILED' ? '已标记失败' : '异常已关闭')
  } catch {
    // interceptor handles
  } finally {
    actionLoading.value = null
  }
}

function moveTaskSelection(delta: number) {
  const pool = store.taskPool
  if (pool.length === 0) return
  const currentIndex = pool.findIndex((t) => t.taskId === store.selectedTaskId)
  const nextIndex = currentIndex < 0
    ? (delta > 0 ? 0 : pool.length - 1)
    : (currentIndex + delta + pool.length) % pool.length
  store.selectTask(pool[nextIndex].taskId)
}

useWorkbenchShortcuts(workbenchShortcutsEnabled, {
  refresh: refreshAll,
  autoAssignSelected: async () => {
    const taskId = store.selectedTaskId
    const task = store.taskPool.find((t) => t.taskId === taskId)
    if (task) {
      await handleAutoAssign(task)
    }
  },
  openManualAssign: () => {
    const task = store.taskPool.find((t) => t.taskId === store.selectedTaskId)
    if (task) openManualModal(task)
  },
  moveSelection: moveTaskSelection,
})

async function loadDispatchPause() {
  if (!authStore.isAdmin) return
  const res = await fetchDispatchPauseStatus(parkScope.selectedParkId ?? undefined)
  dispatchPaused.value = res.data.globalPaused || res.data.parkPaused
}

async function onDispatchPauseChange(checked: boolean) {
  await setDispatchPause(parkScope.selectedParkId ?? null, checked)
  message.success(checked ? '已暂停新派单' : '已恢复新派单')
}

onMounted(() => {
  refreshAll()
  void loadDispatchPause()
  void loadRouteOptions()
  // V5-T3: 次要面板延迟渲染
  requestAnimationFrame(() => { deferredPanels.value = true })
  // V5-T5: 再来一单 → 打开创建订单弹窗
  if (route.query.reorder === '1') {
    createOrderModalOpen.value = true
  }
})

async function loadRouteOptions() {
  try {
    const res = await fetchRoutes(parkScope.selectedParkId ?? undefined)
    routeOptions.value = res.data.map((r: DispatchRoute) => ({ label: r.routeName, value: r.id }))
  } catch {
    routeOptions.value = []
  }
}

watch(() => parkScope.scopeVersion, () => {
  loadTrafficSummary()
  void loadDispatchPause()
  void loadRouteOptions()
  void store.fetchQueue()
})
</script>

<style scoped lang="less">
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 112px);
}

.shortcut-hint {
  font-size: 12px;
  color: #8c9bab;
  white-space: nowrap;
}

.kbd-hint {
  margin-left: 4px;
  font-size: 10px;
  border: 1px solid #d7e0e8;
  border-radius: 4px;
  padding: 0 4px;
}

.workbench-header {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 24px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(0, 119, 182, 0.12) 0%, rgba(13, 17, 23, 0.95) 55%);
  border: 1px solid var(--fsd-border);
}

.header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

/* V5-T4: 全文搜索 */
.header-search {
  display: flex;
  align-items: center;
  gap: 8px;
}

.global-search-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}

.search-option-group {
  font-size: 10px;
  color: #8c9bab;
  background: rgba(140, 155, 171, 0.12);
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  text-transform: uppercase;
}

.search-option-label {
  font-weight: 500;
  font-size: 13px;
}

.search-option-hint {
  font-size: 11px;
  color: #8c9bab;
  margin-left: auto;
}

.search-panel-trigger {
  font-size: 16px;
  color: #8c9bab;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #eaf4ff;
  }
}

.viewer-readonly-banner {
  margin-bottom: 16px;
}

.congestion-bar {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  border: 1px solid var(--fsd-border);
  white-space: nowrap;

  &.congestion-ok {
    color: #00e676;
    background: rgba(0, 230, 118, 0.08);
  }

  &.congestion-warn {
    color: #ffb703;
    background: rgba(255, 183, 3, 0.1);
  }

  &.congestion-critical {
    color: #ff3d71;
    background: rgba(255, 61, 113, 0.12);
  }
}

/* 调度风险带 */
.ops-risk-band {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.risk-band-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  border: none;
  cursor: default;
  text-align: left;

  &.risk-warning {
    background: rgba(255, 183, 3, 0.1);
    border: 1px solid rgba(255, 183, 3, 0.25);
    color: var(--fsd-warning);
  }

  &.risk-critical {
    background: rgba(255, 61, 113, 0.12);
    border: 1px solid rgba(255, 61, 113, 0.3);
    color: var(--fsd-error);
    cursor: pointer;
  }
}

.risk-action-hint {
  margin-left: 4px;
  font-size: 11px;
  opacity: 0.85;
}

/* V5-N4: 派车预测风险条（legacy, kept for reference） */
.dispatch-risk-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;

  &.risk-warning {
    background: rgba(255, 183, 3, 0.1);
    border: 1px solid rgba(255, 183, 3, 0.25);
    color: #ffb703;
  }

  &.risk-critical {
    background: rgba(255, 61, 113, 0.12);
    border: 1px solid rgba(255, 61, 113, 0.3);
    color: #ff3d71;
  }
}

.risk-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.risk-warning .risk-dot {
  background: #ffb703;
  box-shadow: 0 0 8px rgba(255, 183, 3, 0.4);
}

.risk-critical .risk-dot {
  background: #ff3d71;
  box-shadow: 0 0 8px rgba(255, 61, 113, 0.4);
  animation: risk-pulse 1.2s ease-in-out infinite;
}

@keyframes risk-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.85); }
}

.panel-order-hint {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  white-space: nowrap;
}

.task-fail-detail {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 61, 113, 0.06);
  border: 1px dashed rgba(255, 61, 113, 0.25);
}

.fail-suggestions {
  margin: 0 0 6px;
  padding-left: 18px;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.fail-links {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.fail-link {
  font-size: 12px;
  color: var(--fsd-accent);
}

.batch-fail-detail {
  font-size: 12px;

  ul {
    margin: 4px 0 0;
    padding-left: 16px;
    color: var(--fsd-text-secondary);
  }
}

.header-main {
  flex: 1;
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--fsd-text-primary);
}

.page-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--fsd-text-tertiary);
}

.header-metrics {
  display: flex;
  gap: 20px;
}

.metric {
  text-align: center;
  min-width: 72px;

  .metric-value {
    display: block;
    font-size: 24px;
    font-weight: 700;
    font-family: 'JetBrains Mono', monospace;
    color: var(--fsd-accent);
    line-height: 1.1;
  }

  .metric-label {
    font-size: 11px;
    color: var(--fsd-text-tertiary);
    letter-spacing: 0.04em;
  }

  &.metric-warn .metric-value {
    color: var(--fsd-warning);
  }

  &.metric-danger .metric-value {
    color: var(--fsd-error);
  }

  &.metric-success .metric-value {
    color: var(--fsd-success);
  }

  &.metric-info .metric-value {
    color: var(--fsd-info, #1890ff);
  }

  &.metric-charging .metric-value {
    color: #722ed1;
  }

  &.metric-online .metric-value {
    color: #52c41a;
  }
}

.metric-divider {
  width: 1px;
  height: 36px;
  background: var(--fsd-border);
  margin: 0 8px;
}

.workbench-grid {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(300px, 380px) minmax(0, 1fr) auto;
  gap: 16px;
  min-height: 520px;
  align-items: stretch;
}

.panel-workspace {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.task-detail {
  padding: 14px 16px;
  border-bottom: 1px solid var(--fsd-border);
}

.task-detail-empty {
  padding: 24px 16px;
  text-align: center;
  color: var(--fsd-text-tertiary);
  font-size: 13px;
  border-bottom: 1px solid var(--fsd-border);
}

.task-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.task-detail-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--fsd-text-secondary);
  margin-bottom: 10px;
}

.workspace-map {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 200px;
  padding: 12px;
}

.workspace-map-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fsd-text-primary);
}

.aux-sidebar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 300px;
  max-width: 340px;
  transition: min-width 0.2s ease;

  &.collapsed {
    min-width: 48px;
    max-width: 48px;
  }
}

.aux-toggle {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  padding: 12px 8px;
  border-radius: 10px;
  border: 1px solid var(--fsd-border);
  background: rgba(22, 27, 34, 0.8);
  color: var(--fsd-text-secondary);
  font-size: 12px;
  cursor: pointer;
  position: relative;

  .aux-badge {
    position: absolute;
    top: 4px;
    right: 2px;
    min-width: 18px;
    padding: 0 4px;
    border-radius: 999px;
    background: var(--fsd-error);
    color: #fff;
    font-size: 10px;
    font-weight: 700;
    writing-mode: horizontal-tb;
  }
}

.aux-sidebar:not(.collapsed) .aux-toggle {
  writing-mode: horizontal-tb;
  text-orientation: unset;
  align-self: flex-end;
  padding: 4px 12px;
}

.batch-toolbar-sticky {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 8px 12px;
  background: rgba(13, 17, 23, 0.92);
  border-bottom: 1px solid var(--fsd-border);
  backdrop-filter: blur(6px);
}

.panel {
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  border: 1px solid var(--fsd-border);
  background: rgba(22, 27, 34, 0.6);
  overflow: hidden;
}

.panel-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--fsd-border);

  h2 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--fsd-text-primary);
    white-space: nowrap;
  }
}

.panel-head-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.panel-head-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.route-filter {
  width: 148px;
  flex-shrink: 0;
}

.panel-hint {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}

.filter-tabs {
  display: flex;
  gap: 4px;
}

.hub-link {
  margin-left: auto;
  font-size: 12px;
  color: var(--fsd-accent);
  text-decoration: none;
  white-space: nowrap;
}

.route-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(0, 180, 216, 0.15);
  color: #00b4d8;
}

.filter-tab {
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--fsd-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: var(--fsd-bg-hover);
  }

  &.active {
    border-color: rgba(0, 180, 216, 0.35);
    background: rgba(0, 180, 216, 0.1);
    color: var(--fsd-accent);
  }
}

.tab-count {
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
}

.pool-load-more {
  margin-top: 12px;
}

.task-list,
.exception-list {
  flex: 1;
  overflow: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 260px);
}

.task-card {
  position: relative;
  padding: 12px 12px 12px 36px;
  border-radius: 10px;
  border: 1px solid var(--fsd-border);
  background: rgba(13, 17, 23, 0.5);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: rgba(0, 180, 216, 0.25);
  }

  &.selected,
  &.checked {
    border-color: rgba(0, 180, 216, 0.5);
    box-shadow: 0 0 0 1px rgba(0, 180, 216, 0.15);
  }
}

.exception-card {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--fsd-border);
  background: rgba(13, 17, 23, 0.5);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: rgba(0, 180, 216, 0.25);
  }

  &.selected {
    border-color: rgba(0, 180, 216, 0.5);
    box-shadow: 0 0 0 1px rgba(0, 180, 216, 0.15);
  }
}

.task-check {
  position: absolute;
  left: 10px;
  top: 14px;
}

.batch-toolbar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 12px 10px;
}

.batch-hint {
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.batch-groups {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.batch-group-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  font-family: 'JetBrains Mono', monospace;
}

.route-tag {
  background: rgba(0, 180, 216, 0.12);
  color: #00b4d8;
}

.priority-tag {
  background: rgba(255, 176, 32, 0.12);
  color: #ffb020;
}

.vehicle-tag {
  background: rgba(114, 46, 209, 0.12);
  color: #722ed1;
}

.batch-vehicle-groups {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.batch-group-label {
  font-size: 12px;
  color: var(--fsd-text-secondary);
  white-space: nowrap;
}

.batch-modal-hint,
.batch-result-summary {
  margin-bottom: 12px;
  color: var(--fsd-text-secondary);
}

/* 二次确认弹窗 */
.confirm-body {
  padding: 4px 0;
}

.confirm-alert {
  margin-bottom: 14px;
}

.confirm-summary {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--fsd-text-primary);
}

.confirm-groups {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.confirm-target-vehicle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  margin-bottom: 10px;
  border-radius: 8px;
  background: rgba(0, 180, 216, 0.08);
  border: 1px solid rgba(0, 180, 216, 0.2);
  font-size: 13px;
  font-weight: 600;
  color: var(--fsd-accent);
}

.confirm-current-vehicles {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.confirm-task-list {
  max-height: 280px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.confirm-task-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  background: rgba(13, 17, 23, 0.3);
  font-size: 12px;
}

.confirm-task-no {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
  color: var(--fsd-text-primary);
  min-width: 80px;
}

.confirm-task-more {
  padding: 6px 8px;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  text-align: center;
}

/* 手动派车影响范围 */
.manual-task-scope {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: -8px 0 12px;
  padding: 0 8px;
}

.vehicle-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.vehicle-soc {
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
}

.soc-critical { color: #ff3d71; }
.soc-warn { color: #ffb020; }
.soc-ok { color: #00e676; }

/* V5-E4: 低电角标 */
.low-soc-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: #ff3d71;
  animation: pulse-soc 1.5s ease-in-out infinite;
}

@keyframes pulse-soc {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.low-soc-banner {
  margin-bottom: 16px;
}

/* ── V5-W6/W7: 筛选栏与视图模板 ── */
.filter-bar {
  padding: 10px 14px;
  border-bottom: 1px solid var(--fsd-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-select {
  width: 120px;
}

.filter-check {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--fsd-text-secondary);
  cursor: pointer;
  user-select: none;

  input { accent-color: var(--fsd-accent); }
}

.filter-templates {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;

  .filter-templates-label {
    font-size: 11px;
    color: var(--fsd-text-tertiary);
    white-space: nowrap;
  }

  .filter-templates-hint {
    font-size: 11px;
    color: var(--fsd-text-tertiary);
  }
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid var(--fsd-border);
  background: transparent;
  color: var(--fsd-text-secondary);
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: var(--fsd-accent);
    color: var(--fsd-accent);
    background: rgba(0, 119, 182, 0.06);
  }

  &.active {
    border-color: var(--fsd-accent);
    color: #fff;
    background: var(--fsd-accent);
  }

  &.saved {
    border-style: dashed;
  }
}

.filter-chip-del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  font-size: 12px;
  line-height: 1;
  color: var(--fsd-text-tertiary);

  &:hover {
    color: var(--fsd-error);
    background: rgba(255, 61, 113, 0.1);
  }
}

/* ── V5-W6: 保存视图弹窗 ── */
.save-view-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
}

/* ── V5-W5: 撤销通知 ── */
.undo-notify {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.task-card-head,
.exception-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.task-no {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--fsd-text-primary);
  font-weight: 600;
}

.priority-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  letter-spacing: 0.04em;
}

.priority-P0 { background: rgba(255, 61, 113, 0.2); color: #ff3d71; }
.priority-P1 { background: rgba(255, 176, 32, 0.2); color: #ffb020; }
.priority-P2 { background: rgba(0, 180, 216, 0.15); color: #00b4d8; }
.priority-P3 { background: rgba(160, 160, 160, 0.15); color: #a0a0a0; }

.wait-badge {
  font-size: 11px;
  color: var(--fsd-warning);
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.exc-badge {
  color: var(--fsd-error);
  font-size: 11px;
}

.task-reason,
.exc-msg {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  line-height: 1.45;
}

.task-actions,
.exception-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.exc-type {
  font-size: 12px;
  font-weight: 600;
  color: var(--fsd-warning);
}

.exc-time {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--fsd-text-tertiary);
}

.exc-link {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--fsd-text-secondary);
}

.panel-map {
  min-height: 360px;

  :deep(.park-mini-map) {
    flex: 1;
    margin: 12px;
    min-height: 0;
  }
}

@media (max-width: 1366px) {
  .header-metrics .metric:nth-child(n+5) {
    display: none;
  }
}

@media (max-width: 1200px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .aux-sidebar {
    min-width: 100%;
    max-width: 100%;

    &.collapsed {
      min-width: 100%;
      max-width: 100%;
    }
  }

  .aux-toggle {
    writing-mode: horizontal-tb;
    text-orientation: unset;
    width: 100%;
  }

  .pool-load-more {
    margin-top: 12px;
  }

  .task-list,
  .exception-list {
    max-height: 360px;
  }
}

@media (max-width: 1024px) {
  .workspace-map {
    min-height: 160px;
  }
}
</style>
