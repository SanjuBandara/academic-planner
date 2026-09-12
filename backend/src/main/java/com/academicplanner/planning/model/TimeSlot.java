package com.academicplanner.planning.model;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Represents an explicit, optional time window for study on a given day.
 */
public record TimeSlot(
        LocalTime startTime,
        LocalTime endTime
) {
    public double durationHours() {
        if (startTime == null || endTime == null) return 0.0;
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return Math.max(0.0, minutes / 60.0);
    }
}
