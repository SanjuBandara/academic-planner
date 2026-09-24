package com.academicplanner.ai.provider;

import com.academicplanner.ai.config.AiConfig;
import com.academicplanner.ai.model.AiContext;
import com.academicplanner.ai.model.AiIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI (and OpenAI-compatible, e.g. OpenRouter / vLLM / Ollama) implementation of AiProvider.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiProvider implements AiProvider {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    @Qualifier("aiRestClient")
    private final RestClient aiRestClient;

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return aiConfig.isEnabled() && aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank();
    }

    @Override
    public AiProviderResponse generateResponse(String systemPrompt, String userMessage, AiContext context) {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI provider is not configured or disabled.");
        }

        try {
            String contextJson = objectMapper.writeValueAsString(context);

            String fullSystemPrompt = systemPrompt + "\n\n"
                    + "=== CURRENT AUTHENTICATED STUDENT CONTEXT ===\n"
                    + contextJson + "\n"
                    + "=== INSTRUCTIONS ===\n"
                    + "Respond ONLY with a valid JSON object matching this schema:\n"
                    + "{\n"
                    + "  \"message\": \"Detailed, friendly, and helpful explanation to the student\",\n"
                    + "  \"intent\": \"One of: GENERAL_PLAN_QUESTION, GET_TODAY_PLAN, GET_WEEK_PLAN, GET_UPCOMING_ASSESSMENTS, GET_TASKS, GET_AVAILABLE_TIME, CREATE_DAILY_PLAN, MODIFY_PLAN, REPLAN, MARK_ACTIVITY_COMPLETED, UNKNOWN\",\n"
                    + "  \"actionRequired\": false,\n"
                    + "  \"action\": null,\n"
                    + "  \"planPreview\": null\n"
                    + "}\n";

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", fullSystemPrompt));
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2);
            requestBody.put("response_format", Map.of("type", "json_object"));

            String payload = objectMapper.writeValueAsString(requestBody);

            log.info("[OpenAiProvider] Sending request to OpenAI API with model: {}", aiConfig.getModel());

            String rawResponse = aiRestClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiConfig.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new RuntimeException("Empty response from AI provider");
            }

            JsonNode rootNode = objectMapper.readTree(rawResponse);
            JsonNode choices = rootNode.path("choices");
            if (choices.isEmpty()) {
                throw new RuntimeException("No choices returned from AI provider");
            }

            String content = choices.get(0).path("message").path("content").asText();
            JsonNode structuredJson = objectMapper.readTree(content);

            String replyMessage = structuredJson.path("message").asText();
            String intentStr = structuredJson.path("intent").asText("GENERAL_PLAN_QUESTION");
            boolean actionRequired = structuredJson.path("actionRequired").asBoolean(false);
            JsonNode actionNode = structuredJson.get("action");
            JsonNode previewNode = structuredJson.get("planPreview");

            AiIntent intent;
            try {
                intent = AiIntent.valueOf(intentStr.toUpperCase().trim());
            } catch (Exception e) {
                intent = AiIntent.GENERAL_PLAN_QUESTION;
            }

            return AiProviderResponse.builder()
                    .message(replyMessage)
                    .intent(intent)
                    .actionRequired(actionRequired)
                    .action(actionNode != null && !actionNode.isNull() ? actionNode : null)
                    .planPreview(previewNode != null && !previewNode.isNull() ? previewNode : null)
                    .build();

        } catch (Exception e) {
            log.error("[OpenAiProvider] Error communicating with LLM API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate response from OpenAI provider: " + e.getMessage(), e);
        }
    }
}
