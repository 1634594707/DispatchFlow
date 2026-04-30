package com.fsd.common.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageResponse<T> {

    private long total;

    private long pageNo;

    private long pageSize;

    private List<T> records;
}
