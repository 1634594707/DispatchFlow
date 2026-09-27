package com.fsd.dispatch.core;

/**
 * 灰度稳定分桶（§7.2）：同一键恒定落在同一侧，不用随机数——当初灰度重掷缺陷的根因就是引了随机。
 *
 * <p>生产路由（{@code policy.DecisionPolicyRouter}）与离线实验台（{@code sim.ScenarioBench}）
 * 必须走**同一份实现**，"同一单重放必落同一侧"才有跨生产/实验的同一含义，所以它落在纯函数内核，
 * 由 {@code DecisionCorePurityTest} 守住无外部依赖。
 */
public final class GrayBuckets {

    private GrayBuckets() {
    }

    /**
     * @param bucketKey 稳定分桶键（订单 id / 订单号）；空键一律 0 号桶（保守侧 = 在位策略）
     * @return 0..99 的桶号
     */
    public static int of(String bucketKey) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return 0;
        }
        return Math.floorMod(bucketKey.hashCode(), 100);
    }
}
