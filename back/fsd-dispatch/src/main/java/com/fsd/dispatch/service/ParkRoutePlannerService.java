package com.fsd.dispatch.service;

import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.List;

public interface ParkRoutePlannerService {

    List<ParkPointResponse> buildRoute(BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY);
}
