package com.academicplanner.ai.dto;

import com.academicplanner.ai.model.AiAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Structured modification request extracted by AI or requested by the user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanModificationRequest {

    private AiAction action;
    private Long activityId;
    private String activityName;
    private String moduleCode;
    private Integer additionalMinutes;
    private Integer targetMinutes;
    private LocalDate startDate;
    private LocalDate endDate;
}
