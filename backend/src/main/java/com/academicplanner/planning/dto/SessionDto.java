package com.academicplanner.planning.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One concrete scheduled session returned by the Python planning service.
 */
public record SessionDto(
                String activityId,
                LocalDate date,
                LocalTime startTime,
                LocalTime endTime,
                int durationMinutes) {
}
