package com.rca.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.rca.model.Alert;
import com.rca.model.RcaReport;
import com.rca.repository.RcaReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends RCA reports to a Slack channel as formatted messages.
 * Stores the Slack message timestamp (ts) on the report for future threading.
 */
@Slf4j
@Service
public class SlackNotificationService {

    private final MethodsClient slackClient;
    private final RcaReportRepository rcaReportRepository;
    private final String channel;

    public SlackNotificationService(
        @Value("${slack.bot-token}") String botToken,
        @Value("${slack.channel}") String channel,
        RcaReportRepository rcaReportRepository
    ) {
        this.slackClient = Slack.getInstance().methods(botToken);
        this.channel = channel;
        this.rcaReportRepository = rcaReportRepository;
    }

    public void postRca(RcaReport report, Alert alert) {
        String text = buildSlackMessage(report, alert);

        try {
            ChatPostMessageResponse response = slackClient.chatPostMessage(
                ChatPostMessageRequest.builder()
                    .channel(channel)
                    .text(text)
                    .build()
            );

            if (response.isOk()) {
                // Persist the Slack ts so we can thread follow-ups
                report.setSlackTs(response.getTs());
                rcaReportRepository.save(report);
                log.info("Slack notification sent for RCA id={} ts={}", report.getId(), response.getTs());
            } else {
                log.warn("Slack API returned error: {}", response.getError());
            }

        } catch (Exception e) {
            // Non-fatal: RCA is already persisted; notification failure shouldn't break the pipeline
            log.error("Failed to send Slack notification for RCA id={}: {}", report.getId(), e.getMessage(), e);
        }
    }

    private String buildSlackMessage(RcaReport report, Alert alert) {
        return String.format("""
            :rotating_light: *RCA Generated — %s* (%s)
            *Service:* `%s`  |  *Severity:* %s  |  *Environment:* %s

            *Root Cause:*
            %s

            *Impact:*
            %s

            *Recommended Fix:*
            %s

            *Prevention:*
            %s

            _Report ID: %s — Review and update in the RCA dashboard._
            """,
            alert.getAlertName(),
            alert.getSeverity(),
            alert.getService(),
            alert.getSeverity(),
            alert.getEnvironment(),
            orUnknown(report.getRootCause()),
            orUnknown(report.getImpact()),
            orUnknown(report.getFixApplied()),
            orUnknown(report.getPrevention()),
            report.getId()
        );
    }

    private String orUnknown(String value) {
        return (value != null && !value.isBlank()) ? value : "_Not determined_";
    }
}
