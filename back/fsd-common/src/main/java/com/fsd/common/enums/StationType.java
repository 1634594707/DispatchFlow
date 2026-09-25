package com.fsd.common.enums;

public enum StationType {
    PICKUP,
    DROPOFF,
    GENERAL,
    /** L1 试点充电站（ZJF-CHG-*） */
    CHARGING_STATION,
    /**
     * 换电柜（FSD-SWAP-*，W4-a）。刻意不复用 {@link #CHARGING_STATION}：
     * 两者的占用/服务时长语义不同（换电 30 s vs 充电 7,200 s），混成一个类型
     * 会让选桩逻辑对同一车队同时套两种假设 —— 即 §10.2 第 3 问那个坑。
     */
    SWAP_CABINET,
    /**
     * 任意点下单时由吸附自动登记的位置（编码 {@code GEO-<路网节点>}）。
     * 不是人工登记的作业点：没有服务位、不参与泊位容量判定，只回答"车该去哪儿"。
     */
    GEO_POINT,
    HUB,
    BUFFER,
    MOTHERSHIP
}
