package com.academicplanner.ai.provider;

import com.academicplanner.ai.model.AiContext;
import com.academicplanner.ai.model.AiIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pluggable abstraction for LLM providers (e.g. OpenAI, Ollama, Anthropic).
 * Keeps provider-specific HTTP and payload logic outside business services.
 */
public interface AiProvider {

    String getProviderName();

    boolean isAvailable();

    AiProviderResponse generateResponse(
            String systemPrompt,
            String userMessage,
            AiContext context
    );

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AiProviderResponse {
        private String message;
        private AiIntent intent;
        private boolean actionRequired;
        private Object action;
        private Object planPreview;
    }
}
