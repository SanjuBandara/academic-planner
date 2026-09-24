package com.academicplanner.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of plan feasibility check or proposed modification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanModificationResult {

    private String proposalId;

    /**
     * FEASIBLE, PARTIALLY_FEASIBLE, INFEASIBLE, REQUIRES_CONFIRMATION
     */
    private String status;

    private Integer requestedMinutes;
    private Integer allocatedMinutes;
    private String reason;

    @Builder.Default
    private List<String> affectedActivities = new ArrayList<>();

    @Builder.Default
    private List<Object> newPlan = new ArrayList<>();
}
