package com.rca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rca.embedding.OpenAiClient;
import com.rca.kafka.AlertEvent;
import com.rca.model.Alert;
import com.rca.model.RcaReport;
import com.rca.rag.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * Builds the LLM prompt and calls OpenAI to generate a structured RCA report.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RcaGenerationService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.chat-model}")
    private String chatModel;

    private static final String SYSTEM_PROMPT = """
        You are an expert Site Reliability Engineer specialising in incident root cause analysis.
        
        When given an alert and relevant context (past incidents and runbooks), generate a structured RCA report with the following sections:
        
        1. ROOT_CAUSE: The most likely root cause based on available signals and past patterns.
        2. IMPACT: What systems, users or SLAs are affected.
        3. TIMELINE: A plausible sequence of events leading to the incident.
        4. FIX_APPLIED: Recommended immediate remediation steps.
        5. PREVENTION: Long-term prevention measures to avoid recurrence.
        
        Respond ONLY in valid JSON matching this schema:
        {
          "rootCause": "string",
          "impact": "string",
          "timeline": "string",
          "fixApplied": "string",
          "prevention": "string"
        }
        
        Be concise but specific. Reference runbook procedures where relevant.
        """;

    public RcaReport generate(Alert alert, AlertEvent event, RetrievalResult retrieval) {
        String userPrompt = buildUserPrompt(event, retrieval.getContext());
        log.debug("Calling LLM for RCA generation, alert id={}", alert.getId());

        String rawResponse = openAiClient.chat(SYSTEM_PROMPT, userPrompt);
        log.debug("LLM raw response length: {}", rawResponse.length());

        RcaReport report = parseAndBuildReport(rawResponse, alert, retrieval);
        log.info("RCA generated for alert id={}", alert.getId());
        return report;
    }

    private String buildUserPrompt(AlertEvent event, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## CURRENT ALERT\n");
        sb.append("Service: ").append(event.getService()).append("\n");
        sb.append("Alert: ").append(event.getAlertName()).append("\n");
        sb.append("Severity: ").append(event.getSeverity()).append("\n");

        if (event.getMetricName() != null) {
            sb.append("Metric: ").append(event.getMetricName()).append("\n");
            sb.append("Threshold: ").append(event.getThreshold()).append("\n");
            sb.append("Current Value: ").append(event.getCurrentValue()).append("\n");
        }

        if (event.getEnvironment() != null) {
            sb.append("Environment: ").append(event.getEnvironment()).append("\n");
        }

        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("\n## CONTEXT FROM KNOWLEDGE BASE\n");
            sb.append(ragContext);
        }

        sb.append("\n\nGenerate the RCA report in JSON format as specified.");
        return sb.toString();
    }

    private RcaReport parseAndBuildReport(String rawResponse, Alert alert, RetrievalResult retrieval) {
        String rootCause = null;
        String impact = null;
        String timeline = null;
        String fixApplied = null;
        String prevention = null;

        try {
            // Strip markdown code fences if present
            String json = rawResponse.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```[a-z]*\\n?", "").trim();
            }

            @SuppressWarnings("unchecked")
            Map<String, String> parsed = objectMapper.readValue(json, Map.class);
            rootCause  = parsed.get("rootCause");
            impact     = parsed.get("impact");
            timeline   = parsed.get("timeline");
            fixApplied = parsed.get("fixApplied");
            prevention = parsed.get("prevention");

        } catch (Exception e) {
            log.warn("Could not parse LLM JSON response, storing raw output. Error: {}", e.getMessage());
            // Fallback: store raw response as full_report
        }

        // Build sources_used JSON
        String sourcesUsed = null;
        try {
            sourcesUsed = objectMapper.writeValueAsString(Map.of(
                "incidents", retrieval.getIncidentIds(),
                "runbooks",  retrieval.getRunbookIds()
            ));
        } catch (Exception ignored) {}

        String fullReport = (rootCause != null)
            ? String.format("ROOT CAUSE:\n%s\n\nIMPACT:\n%s\n\nTIMELINE:\n%s\n\nFIX APPLIED:\n%s\n\nPREVENTION:\n%s",
                rootCause, impact, timeline, fixApplied, prevention)
            : rawResponse;

        return RcaReport.builder()
            .alert(alert)
            .rootCause(rootCause)
            .impact(impact)
            .timeline(timeline)
            .fixApplied(fixApplied)
            .prevention(prevention)
            .fullReport(fullReport)
            .modelUsed(chatModel)
            .sourcesUsed(sourcesUsed)
            .status(RcaReport.RcaStatus.DRAFT)
            .build();
    }
}
