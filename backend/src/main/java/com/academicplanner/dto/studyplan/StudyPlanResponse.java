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
        LocalDateTime updatedAt
) {
    public static StudyPlanResponse from(StudyPlan plan) {
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
                plan.getUpdatedAt()
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
                plan.getUpdatedAt()
        );
    }
}
