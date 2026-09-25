package com.fsd.dispatch.service;

import java.time.LocalDateTime;

public interface DispatchPauseControlService {

    boolean isDispatchPaused(Long parkId);

    /**
     * 置暂停/恢复，并记下"谁、为什么"。
     *
     * @param reason   暂停**必须**给原因（紧急停止没有原因等于事后无法复盘）；恢复可空，
     *                 空则保留上一条原因，让这一行仍然解释得清刚结束的那段暂停
     * @param operator 操作人，必须由服务端从会话取得 —— 不得从请求体透传，否则审计列可被伪造
     */
    void setDispatchPaused(Long parkId, boolean paused, String reason, String operator);

    boolean isGlobalDispatchPaused();

    /** 生效中的暂停是哪一档、谁下的、因为什么。 */
    PauseState pauseState(Long parkId);

    record PauseState(boolean paused, String reason, String pausedBy, LocalDateTime pausedAt) {
    }
}
