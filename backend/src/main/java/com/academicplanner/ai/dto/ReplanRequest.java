package com.academicplanner.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured request for triggering an AI-driven adaptive replan.
 * Sent from the AI provider service to the PlanningTool when the student
 * asks to reschedule or regenerate upcoming sessions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplanRequest {

    /**
     * The reason/context for the replan (used for logging and conflict explanation).
     */
    private String reason;

    /**
     * Optional: list of plan item IDs the student wants to reschedule or skip.
     * If empty, a full re-evaluation of all remaining PLANNED items is attempted.
     */
    private List<Long> targetItemIds;

    /**
     * Optional: additional study minutes the student wants to add to the remaining week.
     */
    private Integer additionalMinutesRequested;
}
