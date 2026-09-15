package com.academicplanner.dto.studyplan;

import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlan.PlanStatus;
import com.academicplanner.entity.StudyPlan.PlanType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StudyPlanResponse(
        Long id,
        PlanType type,
        LocalDate startDate,
        LocalDate endDate,
        PlanStatus status,
        Double totalAvailableHours,
        Double totalPlannedHours,
        List<StudyPlanItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Solver status returned by the Python CP-SAT service: OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN. */
        String solverStatus,
        /** Human-readable scheduling warnings from the Python service (e.g. activities that could not be fully scheduled). */
        List<String> warnings
) {
    public static StudyPlanResponse from(StudyPlan plan) {
        return from(plan, null, null);
    }

    public static StudyPlanResponse from(StudyPlan plan, String solverStatus, List<String> warnings) {
        List<StudyPlanItemResponse> itemDtos = plan.getItems() == null ? List.of() :
                plan.getItems().stream().map(StudyPlanItemResponse::from).toList();
        return new StudyPlanResponse(
                plan.getId(),
                plan.getType(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getStatus(),
                plan.getTotalAvailableHours(),
                plan.getTotalPlannedHours(),
                itemDtos,
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                solverStatus,
                warnings != null ? warnings : List.of()
        );
    }

    /** Summary variant without items (for list views). */
    public static StudyPlanResponse summary(StudyPlan plan) {
        return new StudyPlanResponse(
                plan.getId(),
                plan.getType(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getStatus(),
                plan.getTotalAvailableHours(),
                plan.getTotalPlannedHours(),
                List.of(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                null,
                List.of()
        );
    }
}
