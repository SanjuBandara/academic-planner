package com.academicplanner.planning.dto;

import java.time.LocalDateTime;

/**
 * One schedulable activity, as sent to the Python planning service.
 *
 * <p>
 * <b>Phase 2 contract</b> (breaking change from Phase 1): the Python
 * service now takes {@code remainingHours} directly (no work-unit/
 * productivity conversion), a numeric {@code priority} (1-5) instead of
 * HIGH/MEDIUM/LOW importance strings, {@code credits} as a plain double,
 * and {@code moduleId} as a String (module code, not a numeric DB id) —
 * matching the Python service's {@code app/models/request.py::ActivityIn}.
 */
public record ActivityDto(
                String id,
                String title,
                String activityType, // e.g. ASSESSMENT_PREP, TASK, SELF_STUDY, LECTURE...
                String moduleId, // nullable — module code, e.g. "DSA"
                Double credits, // nullable
                LocalDateTime deadline, // nullable
                double remainingHours,
                int priority // 1 (low) .. 5 (high)
) {
}
