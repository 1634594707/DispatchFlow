package com.fsd.dispatch.service;

import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;

public interface ParkPilotCommandService {

    ParkOrderCreateResponse createParkOrder(ParkOrderCreateRequest request);
}
