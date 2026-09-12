package com.academicplanner.dto.studyplan;

import java.util.List;

/**
 * Detailed availability information for a specific day:
 * availableHours is primary; timeSlots are optional.
 */
public record DailyAvailabilityRequest(
        Double availableHours,
        List<TimeSlotRequest> timeSlots
) {}
