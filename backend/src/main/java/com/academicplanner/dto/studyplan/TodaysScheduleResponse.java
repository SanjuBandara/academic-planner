package com.academicplanner.dto.studyplan;

import java.time.LocalDate;
import java.util.List;

public record TodaysScheduleResponse(
        LocalDate date,
        int totalPlannedMinutes,
        double totalPlannedHours,
        List<SessionSummary> sessions
) {
    public record SessionSummary(
            Long id,
            String sourceId,
            String title,
            String activityType,
            String moduleCode,
            String moduleName,
            String startTime,
            String endTime,
            int durationMinutes,
            String status
    ) {}
}
