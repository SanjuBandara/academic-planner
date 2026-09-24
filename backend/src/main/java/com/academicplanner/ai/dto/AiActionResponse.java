package com.academicplanner.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned after confirming or canceling a plan action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiActionResponse {

    private boolean success;
    private String message;
    private Object result;
}
