/**
 * 叠石桥家纺产业带地理锚点（找家纺无人快递短驳场景）
 * 与后端 fsd.park.geo 配置保持一致
 */
export { ZJF_PILOT_GEO as TEXTILE_PARK_GEO } from './zjfPilotGeo'
import { ZJF_PILOT_GEO } from './zjfPilotGeo'

/**
 * 园区 schematic x/y → GCJ-02 的那条换算**已随 §7.6 删除**：
 * 它曾被 4 处"坐标缺失兜底"调用（`maps/parkGeoMapLayers` 与 `composables/useDeliveryGeo` 各两份），
 * 等于把像素当成真实经纬度画到高德底图上。示意模式要像素请直接画在 Leaflet 画布上（`Tracking.vue`
 * 的 schematic 图层用的就是原始 x/y），地理图层只认真实坐标，缺了就报"位置未知"。
 *
 * <p>§6.3 里 `composables/useDeliveryGeo.ts` 整个文件已删除：它 9 个导出只有 1 个还有人用
 * （L0 覆盖圈，且与 `parkGeoMapLayers` 里那份逐字节相同），其余是"看着像契约、其实没人调"的副本。
 * 图层构造的唯一入口现在是 `@/maps`（→ `parkGeoMapLayers.ts`）。
 */

export function defaultMapCenter(): [number, number] {
  return [ZJF_PILOT_GEO.anchorLng, ZJF_PILOT_GEO.anchorLat]
}
