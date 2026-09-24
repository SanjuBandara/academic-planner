package com.academicplanner.ai.dto;

import com.academicplanner.ai.model.AiIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard structured response from the AI Planning Assistant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String message;
    private AiIntent intent;
    private boolean actionRequired;
    private Object action;
    private Object planPreview;
}
