import type { Plugin } from 'vite'

const MOCK_USER = {
  userId: 1,
  userName: 'Mock 管理员',
  token: 'mock_admin_token_2026',
  role: 'ADMIN',
}

export function dispatchMockPlugin(): Plugin {
  return {
    name: 'dispatch-mock',
    apply: 'serve',
    configureServer(server) {
      // 通用成功响应包装
      function ok(data: unknown) {
        return { success: true, code: 'OK', message: '模拟数据', data }
      }

      // 0. Auth
      server.middlewares.use('/api/admin/auth/login', (req, res) => {
        let body = ''
        req.on('data', (chunk) => { body += chunk })
        req.on('end', () => {
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify(ok({
            token: 'mock_admin_token_2026',
            requiresTotp: false,
            user: { id: 1, username: 'admin', displayName: 'Mock管理员', role: 'ADMIN', status: 'ACTIVE' },
          })))
        })
      })

      server.middlewares.use('/api/admin/auth/me', (_req, res) => {
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify(ok({ id: 1, username: 'admin', displayName: 'Mock管理员', role: 'ADMIN', status: 'ACTIVE' })))
      })

      server.middlewares.use('/api/admin/auth/logout', (_req, res) => {
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify(ok(null)))
      })

      // 1. 干预队列
      server.middlewares.use('/api/admin/dispatch/intervention-queue', (_req, res) => {
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify(ok(mockInterventionQueue())))
      })

      // 2. 工作台
      server.middlewares.use('/api/admin/dispatch/workbench', (_req, res) => {
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify(ok(mockWorkbench())))
      })

      // 3. 任务池查询
      server.middlewares.use('/api/admin/dispatch/task-pool/query', (req, res) => {
        let body = ''
        req.on('data', (chunk) => { body += chunk })
        req.on('end', () => {
          const params = JSON.parse(body || '{}')
          const poolStatus: string = params.poolStatus || 'ALL_POOL'
          const pageNo = params.pageNo || 1
          const pageSize = params.pageSize || 20
          const tasks = mockTaskPool(poolStatus)
          const total = tasks.length
          const start = (pageNo - 1) * pageSize
          const paged = tasks.slice(start, start + pageSize)
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(
            JSON.stringify(
              ok({
                records: paged,
                total,
                pageNo,
                pageSize,
                pages: Math.ceil(total / pageSize),
              })
            )
          )
        })
      })

      // 4. 批量取消
      server.middlewares.use('/api/admin/tasks/batch/cancel', (req, res) => {
        let body = ''
        req.on('data', (chunk) => { body += chunk })
        req.on('end', () => {
          const params = JSON.parse(body || '{}')
          const taskIds: number[] = params.taskIds || []
          const results = taskIds.map((id: number) => {
            const task = allMockTasks.find((t) => t.taskId === id)
            return {
              taskId: id,
              taskNo: task?.taskNo || `T${id}`,
              success: Math.random() > 0.15, // 85% 成功率
              message: Math.random() > 0.15 ? '已取消' : '取消失败：车辆已出发',
              status: 'CANCELLED',
              vehicleId: task?.vehicleId || null,
            }
          })
          const successCount = results.filter((r: { success: boolean }) => r.success).length
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          setTimeout(() => {
            res.end(
              JSON.stringify(
                ok({
                  total: results.length,
                  successCount,
                  failureCount: results.length - successCount,
                  results,
                })
              )
            )
          }, 600)
        })
      })

      // 5. 批量自动派车
      server.middlewares.use('/api/admin/tasks/batch/auto-assign', (req, res) => {
        let body = ''
        req.on('data', (chunk) => { body += chunk })
        req.on('end', () => {
          const params = JSON.parse(body || '{}')
          const taskIds: number[] = params.taskIds || []
          const results = taskIds.map((id: number) => {
            const task = allMockTasks.find((t) => t.taskId === id)
            return {
              taskId: id,
              taskNo: task?.taskNo || `T${id}`,
              success: Math.random() > 0.1,
              message: Math.random() > 0.1 ? '已分配车辆' : '派车失败：无可分配车辆',
              status: 'ASSIGNED',
              vehicleId: task?.vehicleId || 101,
              selectedVehicleCode: '京A8888',
              assignScore: 0.85,
            }
          })
          const successCount = results.filter((r: { success: boolean }) => r.success).length
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          setTimeout(() => {
            res.end(
              JSON.stringify(
                ok({
                  total: results.length,
                  successCount,
                  failureCount: results.length - successCount,
                  results,
                })
              )
            )
          }, 800)
        })
      })

      // 6. 批量改派
      server.middlewares.use('/api/admin/tasks/batch/reassign', (req, res) => {
        let body = ''
        req.on('data', (chunk) => { body += chunk })
        req.on('end', () => {
          const params = JSON.parse(body || '{}')
          const taskIds: number[] = params.taskIds || []
          const vehicleId: number = params.vehicleId || 102
          const results = taskIds.map((id: number) => {
            return {
              taskId: id,
              taskNo: allMockTasks.find((t) => t.taskId === id)?.taskNo || `T${id}`,
              success: Math.random() > 0.1,
              message: Math.random() > 0.1 ? `已改派至车辆 ${vehicleId}` : '改派失败：目标车辆繁忙',
              status: 'ASSIGNED',
              vehicleId,
            }
          })
          const successCount = results.filter((r: { success: boolean }) => r.success).length
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          setTimeout(() => {
            res.end(
              JSON.stringify(
                ok({
                  total: results.length,
                  successCount,
                  failureCount: results.length - successCount,
                  results,
                })
              )
            )
          }, 700)
        })
      })
    },
  }
}

// ------ 模拟数据 ------

const now = new Date()

const allMockTasks = [
  { taskId: 1, taskNo: 'T20260607001', orderId: 1001, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P1', routeCode: '线路A', routeName: 'A区-东门', waitMinutes: 12, openExceptionCount: 1, createdAt: subMin(25) },
  { taskId: 2, taskNo: 'T20260607002', orderId: 1002, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P1', routeCode: '线路A', routeName: 'A区-东门', waitMinutes: 8, openExceptionCount: 0, createdAt: subMin(18) },
  { taskId: 3, taskNo: 'T20260607003', orderId: 1003, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P2', routeCode: '线路B', routeName: 'B区-西门', waitMinutes: 20, openExceptionCount: 0, createdAt: subMin(30) },
  { taskId: 4, taskNo: 'T20260607004', orderId: 1004, vehicleId: 201, status: 'MANUAL_PENDING' as const, orderPriority: 'P1', routeCode: '线路A', routeName: 'A区-东门', waitMinutes: 5, openExceptionCount: 0, createdAt: subMin(10) },
  { taskId: 5, taskNo: 'T20260607005', orderId: 1005, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P3', routeCode: '线路C', routeName: 'C区-北门', waitMinutes: 45, openExceptionCount: 2, createdAt: subMin(55) },
  { taskId: 6, taskNo: 'T20260607006', orderId: 1006, vehicleId: 202, status: 'ASSIGNED' as const, orderPriority: 'P2', routeCode: '线路B', routeName: 'B区-西门', waitMinutes: 3, openExceptionCount: 0, createdAt: subMin(8) },
  { taskId: 7, taskNo: 'T20260607007', orderId: 1007, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P1', routeCode: '线路A', routeName: 'A区-东门', waitMinutes: 15, openExceptionCount: 1, createdAt: subMin(22) },
  { taskId: 8, taskNo: 'T20260607008', orderId: 1008, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P2', routeCode: '线路B', routeName: 'B区-西门', waitMinutes: 6, openExceptionCount: 0, createdAt: subMin(12) },
  { taskId: 9, taskNo: 'T20260607009', orderId: 1009, vehicleId: null, status: 'MANUAL_PENDING' as const, orderPriority: 'P1', routeCode: '线路C', routeName: 'C区-北门', waitMinutes: 18, openExceptionCount: 0, createdAt: subMin(28) },
  { taskId: 10, taskNo: 'T20260607010', orderId: 1010, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P1', routeCode: '线路A', routeName: 'A区-东门', waitMinutes: 2, openExceptionCount: 0, createdAt: subMin(5) },
  { taskId: 11, taskNo: 'T20260607011', orderId: 1011, vehicleId: null, status: 'PENDING' as const, orderPriority: 'P3', routeCode: '线路D', routeName: 'D区-南门', waitMinutes: 30, openExceptionCount: 1, createdAt: subMin(40) },
  { taskId: 12, taskNo: 'T20260607012', orderId: 1012, vehicleId: 203, status: 'ASSIGNED' as const, orderPriority: 'P1', routeCode: '线路A', routeName: 'A区-东门', waitMinutes: 1, openExceptionCount: 0, createdAt: subMin(3) },
]

function subMin(m: number): string {
  return new Date(now.getTime() - m * 60000).toISOString()
}

function mockTaskPool(poolStatus: string) {
  let tasks = [...allMockTasks]
  if (poolStatus === 'PENDING_POOL') {
    tasks = tasks.filter((t) => t.status === 'PENDING')
  } else if (poolStatus === 'MANUAL_PENDING_POOL') {
    tasks = tasks.filter((t) => t.status === 'MANUAL_PENDING')
  }
  return tasks.map((t) => ({
    ...t,
    failReasonCode: null,
    failReasonMsg: null,
    updatedAt: subMin(1),
    primaryOpenException: t.openExceptionCount && t.openExceptionCount > 0
      ? { exceptionId: t.taskId * 10, exceptionType: 'EXECUTE_TIMEOUT', exceptionMsg: t.routeCode === '线路C' ? '车辆长时间未到达取货点' : '执行超时', severity: 'HIGH', exceptionStatus: 'OPEN', occurTime: subMin(10) }
      : null,
    openExceptions: [],
    routeId: t.taskId + 100,
  }))
}

function mockInterventionQueue() {
  const pending = allMockTasks.filter((t) => t.status === 'PENDING')
  const manualPending = allMockTasks.filter((t) => t.status === 'MANUAL_PENDING')
  return {
    pendingCount: pending.length,
    manualPendingCount: manualPending.length,
    openExceptionCount: allMockTasks.filter((t) => (t.openExceptionCount ?? 0) > 0).length,
    pendingTasks: pending.map(mockTaskItem),
    manualPendingTasks: manualPending.map(mockTaskItem),
    openExceptions: [],
  }
}

function mockWorkbench() {
  return {
    intervention: mockInterventionQueue(),
    fleetMetrics: {
      assignableVehicleCount: 6,
      pluggedStandbyCount: 2,
      chargingCount: 3,
      onlineVehicleCount: 11,
    },
    parkLayout: {
      enabled: true,
      parkId: 1,
      parkCode: 'MOCK_PARK',
      parkName: '模拟测试园区',
      width: 200,
      height: 150,
      minZoom: 1,
      maxZoom: 4,
      vehicleSpeedPxPerSecond: 2,
      xFieldAlias: 'x',
      yFieldAlias: 'y',
      stations: [
        { parkId: 1, parkCode: 'MOCK_PARK', stationId: 1, stationCode: 'S01', stationName: 'A区东门', x: 50, y: 30, area: 'A区', stationType: 'ENTRY' },
        { parkId: 1, parkCode: 'MOCK_PARK', stationId: 2, stationCode: 'S02', stationName: 'B区西门', x: 150, y: 40, area: 'B区', stationType: 'ENTRY' },
        { parkId: 1, parkCode: 'MOCK_PARK', stationId: 3, stationCode: 'S03', stationName: 'C区北门', x: 100, y: 130, area: 'C区', stationType: 'ENTRY' },
        { parkId: 1, parkCode: 'MOCK_PARK', stationId: 4, stationCode: 'S04', stationName: '充电站A', x: 30, y: 100, area: 'A区', stationType: 'CHARGING' },
      ],
      parkingSpots: [],
      roadNodes: [
        { code: 'N1', x: 0, y: 0 },
        { code: 'N2', x: 200, y: 150 },
      ],
      roadSegments: [{ from: 'N1', to: 'N2' }],
    },
    vehicles: [
      { vehicleId: 101, vehicleCode: '京A1001', vehicleName: 'AGV-01', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', currentTaskId: null, currentOrderId: null, batteryLevel: 85, batteryStatus: 'NORMAL', x: 40, y: 50, heading: 0, runtimeStage: 'IDLE', targetCode: null, targetType: null, charging: false, lowBattery: false, linkMode: 'SIM', trajectory: [], geoTrajectory: [] },
      { vehicleId: 102, vehicleCode: '京A1002', vehicleName: 'AGV-02', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', currentTaskId: null, currentOrderId: null, batteryLevel: 72, batteryStatus: 'NORMAL', x: 120, y: 60, heading: 0, runtimeStage: 'IDLE', targetCode: null, targetType: null, charging: false, lowBattery: false, linkMode: 'SIM', trajectory: [], geoTrajectory: [] },
      { vehicleId: 103, vehicleCode: '京A1003', vehicleName: 'AGV-03', onlineStatus: 'ONLINE', dispatchStatus: 'BUSY', currentTaskId: 6, currentOrderId: 1006, batteryLevel: 45, batteryStatus: 'NORMAL', x: 80, y: 90, heading: 90, runtimeStage: 'GOING_PICKUP', targetCode: 'S01', targetType: 'STATION', charging: false, lowBattery: false, linkMode: 'SIM', trajectory: [], geoTrajectory: [] },
      { vehicleId: 201, vehicleCode: '京A2001', vehicleName: 'AGV-04', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', currentTaskId: null, currentOrderId: null, batteryLevel: 22, batteryStatus: 'LOW', x: 160, y: 30, heading: 0, runtimeStage: 'IDLE', targetCode: null, targetType: null, charging: false, lowBattery: true, linkMode: 'SIM', trajectory: [], geoTrajectory: [] },
      { vehicleId: 202, vehicleCode: '京A2002', vehicleName: 'AGV-05', onlineStatus: 'ONLINE', dispatchStatus: 'BUSY', currentTaskId: 6, currentOrderId: 1006, batteryLevel: 91, batteryStatus: 'NORMAL', x: 55, y: 110, heading: 180, runtimeStage: 'GOING_DROPOFF', targetCode: 'S02', targetType: 'STATION', charging: false, lowBattery: false, linkMode: 'SIM', trajectory: [], geoTrajectory: [] },
      { vehicleId: 203, vehicleCode: '京A2003', vehicleName: 'AGV-06', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', currentTaskId: null, currentOrderId: null, batteryLevel: 18, batteryStatus: 'CRITICAL', x: 20, y: 120, heading: 0, runtimeStage: 'IDLE', targetCode: null, targetType: null, charging: false, lowBattery: true, linkMode: 'SIM', trajectory: [], geoTrajectory: [] },
      { vehicleId: 301, vehicleCode: '京A3001', vehicleName: 'AGV-07', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', currentTaskId: null, currentOrderId: null, batteryLevel: 95, batteryStatus: 'NORMAL', x: 180, y: 100, heading: 0, runtimeStage: 'IDLE', targetCode: null, targetType: null, charging: false, lowBattery: false, linkMode: 'SIM', trajectory: [], geoTrajectory: [] },
    ],
  }
}

function mockTaskItem(task: typeof allMockTasks[number]) {
  return {
    ...task,
    failReasonCode: null,
    failReasonMsg: null,
    updatedAt: subMin(1),
    primaryOpenException: task.openExceptionCount && task.openExceptionCount > 0
      ? { exceptionId: task.taskId * 10, exceptionType: 'EXECUTE_TIMEOUT', exceptionMsg: '执行超时', severity: 'HIGH', exceptionStatus: 'OPEN', occurTime: subMin(10) }
      : null,
    openExceptions: [],
    routeId: task.taskId + 100,
  }
}