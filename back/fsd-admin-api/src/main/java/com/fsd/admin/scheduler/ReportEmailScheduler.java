package com.fsd.admin.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.dispatch.entity.ReportScheduleEntity;
import com.fsd.dispatch.mapper.ReportScheduleMapper;
import jakarta.mail.MessagingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReportEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportEmailScheduler.class);

    private final ReportScheduleMapper reportScheduleMapper;
    private final AnalyticsAdminService analyticsAdminService;
    private final JavaMailSender mailSender;

    @Value("${fsd.report.mail.from:dispatchflow@localhost}")
    private String fromAddress;

    @Value("${fsd.report.mail.enabled:false}")
    private boolean mailEnabled;

    public ReportEmailScheduler(ReportScheduleMapper reportScheduleMapper,
                                AnalyticsAdminService analyticsAdminService,
                                JavaMailSender mailSender) {
        this.reportScheduleMapper = reportScheduleMapper;
        this.analyticsAdminService = analyticsAdminService;
        this.mailSender = mailSender;
    }

    @Scheduled(fixedDelayString = "${fsd.report.mail.check-ms:60000}")
    @Transactional
    public void dispatchScheduledReports() {
        if (!mailEnabled) {
            return;
        }
        List<ReportScheduleEntity> schedules = reportScheduleMapper.selectList(
                new LambdaQueryWrapper<ReportScheduleEntity>()
                        .eq(ReportScheduleEntity::getEnabled, 1));
        LocalDateTime now = LocalDateTime.now();
        for (ReportScheduleEntity schedule : schedules) {
            if (!shouldFire(schedule, now)) {
                continue;
            }
            try {
                sendReport(schedule);
                schedule.setLastSentAt(now);
                reportScheduleMapper.updateById(schedule);
            } catch (RuntimeException | MessagingException ex) {
                log.warn("Failed to send scheduled report id={}: {}", schedule.getId(), ex.getMessage());
            }
        }
    }

    private boolean shouldFire(ReportScheduleEntity schedule, LocalDateTime now) {
        if (schedule.getCronExpression() == null || schedule.getCronExpression().isBlank()) {
            return false;
        }
        try {
            CronExpression cron = CronExpression.parse(schedule.getCronExpression());
            LocalDateTime last = schedule.getLastSentAt() == null
                    ? now.minusMinutes(2)
                    : schedule.getLastSentAt();
            LocalDateTime next = cron.next(last);
            return next != null && !next.isAfter(now);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void sendReport(ReportScheduleEntity schedule) throws MessagingException {
        byte[] pdf = analyticsAdminService.exportPdf(LocalDate.now(), schedule.getParkId());
        List<String> recipients = Arrays.stream(schedule.getRecipients().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        var message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(recipients.toArray(String[]::new));
        helper.setSubject("DispatchFlow 运营日报 " + LocalDate.now());
        helper.setText("附件为 DispatchFlow 自动生成的运营 PDF 报表。", false);
        helper.addAttachment("daily-report.pdf", () -> new java.io.ByteArrayInputStream(pdf));
        mailSender.send(message);
    }
}
