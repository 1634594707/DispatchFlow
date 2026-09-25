package com.fsd.dispatch.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link AssignmentSolver} 的内核契约（§2.3）。{@code ScenarioBenchTest} 另有一条随机矩阵对穷举的
 * 最优性回归；这里补边界与异常，确保抽成 {@code core} 后语义不变、且被生产批量入口复用前就站得住。
 */
class AssignmentSolverTest {

    private static double total(double[][] cost, int[] assign) {
        double t = 0D;
        for (int i = 0; i < assign.length; i++) {
            t += cost[i][assign[i]];
        }
        return t;
    }

    private static void assertValid(int[] assign, int rows, int cols) {
        assertEquals(rows, assign.length);
        Set<Integer> distinct = new HashSet<>();
        for (int j : assign) {
            assertTrue(j >= 0 && j < cols, "行分到了不存在的列");
            assertTrue(distinct.add(j), "同一列被两行占用");
        }
    }

    private static double bruteForceMin(double[][] cost) {
        int rows = cost.length;
        int cols = cost[0].length;
        double[] best = {Double.MAX_VALUE};
        pick(cost, rows, cols, new int[rows], new boolean[cols], 0, best);
        return best[0];
    }

    private static void pick(double[][] cost, int rows, int cols, int[] assign, boolean[] usedCol, int row,
                             double[] best) {
        if (row == rows) {
            best[0] = Math.min(best[0], total(cost, assign));
            return;
        }
        for (int j = 0; j < cols; j++) {
            if (usedCol[j]) {
                continue;
            }
            usedCol[j] = true;
            assign[row] = j;
            pick(cost, rows, cols, assign, usedCol, row + 1, best);
            usedCol[j] = false;
        }
    }

    @Test
    void classicThreeByThreeHitsOptimum() {
        double[][] cost = {{4, 1, 3}, {2, 0, 5}, {3, 2, 2}};
        int[] assign = AssignmentSolver.hungarian(cost);
        assertValid(assign, 3, 3);
        assertEquals(5D, total(cost, assign), 1e-9);
    }

    @Test
    void singleRowPicksCheapestColumn() {
        double[][] cost = {{7, 2, 9, 4}};
        int[] assign = AssignmentSolver.hungarian(cost);
        assertValid(assign, 1, 4);
        assertEquals(1, assign[0]);
    }

    @Test
    void wideRectangleIsOptimalAndMatchesBruteForce() {
        // 2 行 5 列：行少于列，撮合里"任务少于可用车"的常态
        double[][] cost = {{9, 2, 7, 8, 4}, {6, 5, 1, 3, 7}};
        int[] assign = AssignmentSolver.hungarian(cost);
        assertValid(assign, 2, 5);
        assertEquals(bruteForceMin(cost), total(cost, assign), 1e-9);
    }

    @Test
    void infeasibleCellsAreAvoidedWhenAFeasiblePairingExists() {
        // 用有限大数 1e12 标记不可行对；存在可行完美匹配时，解里不应含大数代价
        double big = 1.0E12D;
        double[][] cost = {{big, 3, 2}, {1, big, 5}, {4, 6, big}};
        int[] assign = AssignmentSolver.hungarian(cost);
        assertValid(assign, 3, 3);
        // 可行解 row0->2(2), row1->0(1), row2->1(6) = 9
        assertEquals(9D, total(cost, assign), 1e-9);
    }

    @Test
    void randomSmallMatricesAlwaysEqualBruteForce() {
        Random rng = new Random(20260922L);
        for (int trial = 0; trial < 200; trial++) {
            int rows = 1 + rng.nextInt(4);
            int cols = rows + rng.nextInt(3);
            double[][] cost = new double[rows][cols];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    cost[i][j] = rng.nextInt(1000) / 10D;
                }
            }
            int[] assign = AssignmentSolver.hungarian(cost);
            assertValid(assign, rows, cols);
            assertEquals(bruteForceMin(cost), total(cost, assign), 1e-9,
                    "非最优：" + java.util.Arrays.deepToString(cost));
        }
    }

    @Test
    void rejectsEmptyRaggedAndUnderTransposedMatrices() {
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.hungarian(new double[0][0]));
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.hungarian(new double[][]{{}, {}}));
        assertThrows(IllegalArgumentException.class,
                () -> AssignmentSolver.hungarian(new double[][]{{1, 2}, {3, 4}, {5, 6}}), "3x2 需调用方先转置");
        assertThrows(IllegalArgumentException.class,
                () -> AssignmentSolver.hungarian(new double[][]{{1, 2, 3}, {4, 5}}), "非矩形");
    }
}
