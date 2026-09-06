package com.academicplanner.dto.studyplan;

/**
 * Weekly plan review summary returned after a plan period ends.
 */
public record PlanReviewResponse(
        Long planId,
        double availableHours,
        double plannedHours,
        double completedHours,
        double unfinishedHours,
        double completionRatePercent,
        String mostStudiedModule,
        String mostDelayedModule,
        int assessmentsCompleted,
        int assessmentsTotal
) {}
