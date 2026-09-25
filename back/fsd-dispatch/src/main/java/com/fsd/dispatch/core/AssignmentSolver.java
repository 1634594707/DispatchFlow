package com.fsd.dispatch.core;

import java.util.Arrays;

/**
 * 矩形指派问题的匈牙利求解器（§2.3「成本矩阵 + 匈牙利」撮合内核）。
 *
 * <p>纯函数、无 Spring 依赖，落 {@code com.fsd.dispatch.core} 由 {@code DecisionCorePurityTest} 守住。
 * 原先只活在 {@code sim.ScenarioBench} 里（实验台私有），这里抽出来是为了让「批量撮合」这个能力
 * 有一个离线可回放、可单测、可被将来生产批量入口直接复用的单一实现，而不是仿真器和生产各写一份。
 *
 * <p>实现取自 e-maxx 的势函数版本，返回每行分到的列下标。
 *
 * <p><b>入参约定</b>：{@code cost} 非空、各行等长，且 {@code rows <= cols}（行多于列时由调用方先转置，
 * 使"被分配的一方"始终是列）。不可行对用<b>有限大数</b>（如 {@code 1e12}）挡，而不是 {@code POSITIVE_INFINITY} ——
 * 无限会让"行多列少"时的可行度判定失真，也拿不到"这行根本没配上"的信号；求解后由调用方按阈值剔除。
 */
public final class AssignmentSolver {

    private AssignmentSolver() {
    }

    /**
     * @param cost {@code n × m}（{@code n <= m}）代价矩阵，越小越优
     * @return 长度 {@code n} 的数组，{@code result[i]} 为第 i 行分到的列下标；每列至多被一行占用
     * @throws IllegalArgumentException 空矩阵、行长不齐，或 {@code rows > cols}
     */
    public static int[] hungarian(double[][] cost) {
        if (cost.length == 0 || cost[0].length == 0) {
            throw new IllegalArgumentException("cost must be non-empty");
        }
        int n = cost.length;
        int m = cost[0].length;
        if (n > m) {
            throw new IllegalArgumentException("rows must be <= cols (transpose before calling): " + n + "x" + m);
        }
        for (double[] row : cost) {
            if (row.length != m) {
                throw new IllegalArgumentException("cost must be rectangular");
            }
        }

        double[] u = new double[n + 1];
        double[] v = new double[m + 1];
        int[] p = new int[m + 1];
        int[] way = new int[m + 1];
        Arrays.fill(p, 0);
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[m + 1];
            boolean[] used = new boolean[m + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            do {
                used[j0] = true;
                int i0 = p[j0];
                int j1 = -1;
                double delta = Double.POSITIVE_INFINITY;
                for (int j = 1; j <= m; j++) {
                    if (used[j]) {
                        continue;
                    }
                    double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                if (j1 < 0) {
                    break;
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] answer = new int[n];
        for (int j = 1; j <= m; j++) {
            if (p[j] != 0) {
                answer[p[j] - 1] = j - 1;
            }
        }
        return answer;
    }
}
