package com.academicplanner.planning.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One declared available time window, as sent to the Python planning service.
 */
public record AvailabilityWindowDto(
                LocalDate date,
                LocalTime startTime,
                LocalTime endTime) {
}
