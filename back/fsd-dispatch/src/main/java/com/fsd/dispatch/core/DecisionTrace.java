package com.fsd.dispatch.core;

import java.util.List;

/**
 * 一次选车决策的过程记录，用于在决策返回后写出可证明的快照（§7.3）。
 *
 * <p>每一层过滤留下通过数，是因为「派单失败原因分布」必须能区分：无车可派 / 遥测过期 /
 * SOC 不足 / 全链路 SOC 不足 / 路网不可达 / MAPF 冲突，这些在单一 failReason 里会被压平。
 *
 * <p>策略档案只留标量（profileId / 类型 / 分桶 / 臂），不把带 Spring 注解的配置 bean 带进
 * {@code core} 包 —— 否则 §7.1 的"内核可离线回放或换语言移植"就不成立。
 */
public final class DecisionTrace {

    private Long parkId;
    private Long profileId;
    private String profileType;
    private Integer grayPercent;
    private int grayBucket;
    private boolean productionSide = true;
    private int candidateTotal;
    private int freshTelemetry;
    private int socEligible;
    private int socChainEligible;
    private int reachable;
    private List<RankedCandidate> ranked = List.of();
    private String roadGraphVersion;
    private String matchAlgorithm = "GREEDY";
    private String policyId = "RULE";
    private String policyVersion = "rule-v1";

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public Long getParkId() {
        return parkId;
    }

    public void setParkId(Long parkId) {
        this.parkId = parkId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public String getProfileType() {
        return profileType;
    }

    public void setProfileType(String profileType) {
        this.profileType = profileType;
    }

    public Integer getGrayPercent() {
        return grayPercent;
    }

    public void setGrayPercent(Integer grayPercent) {
        this.grayPercent = grayPercent;
    }

    public int getGrayBucket() {
        return grayBucket;
    }

    public void setGrayBucket(int grayBucket) {
        this.grayBucket = grayBucket;
    }

    public boolean isProductionSide() {
        return productionSide;
    }

    public void setProductionSide(boolean productionSide) {
        this.productionSide = productionSide;
    }

    public int getCandidateTotal() {
        return candidateTotal;
    }

    public void setCandidateTotal(int candidateTotal) {
        this.candidateTotal = candidateTotal;
    }

    public int getFreshTelemetry() {
        return freshTelemetry;
    }

    public void setFreshTelemetry(int freshTelemetry) {
        this.freshTelemetry = freshTelemetry;
    }

    public int getSocEligible() {
        return socEligible;
    }

    public void setSocEligible(int socEligible) {
        this.socEligible = socEligible;
    }

    public int getSocChainEligible() {
        return socChainEligible;
    }

    public void setSocChainEligible(int socChainEligible) {
        this.socChainEligible = socChainEligible;
    }

    public int getReachable() {
        return reachable;
    }

    public void setReachable(int reachable) {
        this.reachable = reachable;
    }

    public List<RankedCandidate> getRanked() {
        return ranked;
    }

    public void setRanked(List<RankedCandidate> ranked) {
        this.ranked = ranked == null ? List.of() : List.copyOf(ranked);
    }

    public String getRoadGraphVersion() {
        return roadGraphVersion;
    }

    public void setRoadGraphVersion(String roadGraphVersion) {
        this.roadGraphVersion = roadGraphVersion;
    }

    public String getMatchAlgorithm() {
        return matchAlgorithm;
    }

    public void setMatchAlgorithm(String matchAlgorithm) {
        this.matchAlgorithm = matchAlgorithm;
    }
}
