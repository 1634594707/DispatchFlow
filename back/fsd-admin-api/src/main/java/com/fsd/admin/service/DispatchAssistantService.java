package com.fsd.admin.service;

import com.fsd.admin.vo.AdminAssistantBriefingResponse;
import com.fsd.admin.vo.AdminAssistantDecisionExplanationResponse;
import com.fsd.admin.vo.AdminAssistantDigestResponse;
import java.time.LocalDate;

/**
 * P2-1：只读调度助手。
 *
 * <p>四条边界（面试路线图 P2-1）：
 * <ol>
 *   <li>查询站点压力、车辆状态、实验指标和异常 case——数据全部复用既有分析服务（同口径，不另立第二套统计）；</li>
 *   <li>解释决策快照中的候选漏斗和评分项——解释文本由快照字段确定性生成；</li>
 *   <li>生成异常摘要和日报——复用 daily summary 与异常分析；</li>
 *   <li><b>禁止直接执行派车、重派、阈值修改等确定性操作</b>——本服务没有写方法，控制器只暴露 GET；
 *       将来的对话层（Spring AI）也只能消费这里的只读结果。</li>
 * </ol>
 */
public interface DispatchAssistantService {

    AdminAssistantBriefingResponse getBriefing(String period, Long parkId);

    AdminAssistantDecisionExplanationResponse explainDecision(Long orderId);

    AdminAssistantDigestResponse getDigest(LocalDate date, Long parkId);
}
