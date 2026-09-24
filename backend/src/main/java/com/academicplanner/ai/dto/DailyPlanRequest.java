package com.academicplanner.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Structured request for generating or adjusting a daily plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPlanRequest {

    private LocalDate date;
    private List<Long> activityIds;
    private List<AvailabilitySlotDto> availability;
    private Integer maximumStudyMinutes;
    private Boolean includeBreaks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilitySlotDto {
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
