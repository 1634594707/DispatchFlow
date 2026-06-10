package com.fsd.admin.util;

import com.fsd.admin.vo.AdminTrajectoryDwellResponse;
import com.fsd.admin.vo.AdminTrajectoryPointResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TrajectoryDwellDetector {

    private static final double DEFAULT_RADIUS = 15.0;
    private static final long DEFAULT_MIN_MINUTES = 3;

    private TrajectoryDwellDetector() {
    }

    public static List<AdminTrajectoryDwellResponse> detect(List<AdminTrajectoryPointResponse> points) {
        return detect(points, DEFAULT_MIN_MINUTES, DEFAULT_RADIUS);
    }

    public static List<AdminTrajectoryDwellResponse> detect(List<AdminTrajectoryPointResponse> points,
                                                            long minDurationMinutes,
                                                            double maxRadius) {
        List<AdminTrajectoryDwellResponse> dwells = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return dwells;
        }
        int startIdx = 0;
        for (int i = 1; i < points.size(); i++) {
            AdminTrajectoryPointResponse anchor = points.get(startIdx);
            AdminTrajectoryPointResponse current = points.get(i);
            if (anchor.getTs() == null || current.getTs() == null
                    || anchor.getX() == null || anchor.getY() == null
                    || current.getX() == null || current.getY() == null) {
                startIdx = i;
                continue;
            }
            double dist = Math.hypot(current.getX() - anchor.getX(), current.getY() - anchor.getY());
            long minutes = Duration.between(anchor.getTs(), current.getTs()).toMinutes();
            if (dist > maxRadius) {
                if (minutes >= minDurationMinutes) {
                    dwells.add(AdminTrajectoryDwellResponse.builder()
                            .startTime(anchor.getTs())
                            .endTime(current.getTs())
                            .x(anchor.getX())
                            .y(anchor.getY())
                            .durationMinutes(minutes)
                            .build());
                }
                startIdx = i;
            }
        }
        return dwells;
    }
}
