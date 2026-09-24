package com.academicplanner.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload to confirm or cancel a proposed plan action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiActionRequest {

    @NotBlank(message = "Proposal ID is required")
    private String proposalId;

    private boolean confirmed;
}
