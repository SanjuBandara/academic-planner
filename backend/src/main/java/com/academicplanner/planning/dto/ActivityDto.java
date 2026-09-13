package com.academicplanner.planning.dto;

import java.time.LocalDateTime;

/**
 * One schedulable activity, as sent to the Python planning service.
 *
 * <p>
 * This is the "planning model" shape (Section 16 of the spec) — it is
 * deliberately NOT the JPA {@code Assessment}/{@code Task} entity shape.
 * {@link com.academicplanner.planning.client.PlanningRequestMapper} is
 * responsible for converting domain entities into this DTO.
 */
public record ActivityDto(
                String id,
                String type, // EXAM, PROJECT, ASSIGNMENT, QUIZ, REPORT, PRESENTATION, TASK, OTHER...
                String title,
                Long moduleId,
                Integer moduleCredits,
                LocalDateTime deadline, // nullable
                double remainingWorkUnits,
                String importance, // HIGH, MEDIUM, LOW
                String userPriority, // nullable — explicit student override
                Double productivityUnitsPerHour // nullable — activity-specific override
) {
}
