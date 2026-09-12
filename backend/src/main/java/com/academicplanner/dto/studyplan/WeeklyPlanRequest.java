package com.academicplanner.dto.studyplan;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

/**
 * Request body for generating a weekly study plan.
 * Supports both:
 * 1. Simple availability map (DayOfWeek -> availableHours)
 * 2. Detailed dailyAvailability map with optional time slots
 */
public record WeeklyPlanRequest(
        @NotNull(message = "Start date is required")
        LocalDate startDate,

        /** Key: DayOfWeek name (MONDAY…SUNDAY), Value: available hours that day. */
        Map<String, Double> availability,

        /** Key: DayOfWeek name (MONDAY…SUNDAY), Value: detailed availability including optional time slots. */
        Map<String, DailyAvailabilityRequest> dailyAvailability
) {}
