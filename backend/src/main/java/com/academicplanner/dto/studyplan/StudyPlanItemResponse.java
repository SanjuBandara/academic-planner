package com.academicplanner.dto.studyplan;

import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record StudyPlanItemResponse(
        Long id,
        Long studyPlanId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long moduleId,
        String moduleCode,
        String moduleName,
        Long assessmentId,
        String assessmentTitle,
        Long taskId,
        String taskTitle,
        double plannedHours,
        double actualHours,
        String notes,
        ItemStatus status,
        Double priorityScore,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StudyPlanItemResponse from(StudyPlanItem item) {
        return new StudyPlanItemResponse(
                item.getId(),
                item.getStudyPlan().getId(),
                item.getDate(),
                item.getStartTime(),
                item.getEndTime(),
                item.getModule() != null ? item.getModule().getId() : null,
                item.getModule() != null ? item.getModule().getCode() : null,
                item.getModule() != null ? item.getModule().getName() : null,
                item.getAssessment() != null ? item.getAssessment().getId() : null,
                item.getAssessment() != null ? item.getAssessment().getTitle() : null,
                item.getTask() != null ? item.getTask().getId() : null,
                item.getTask() != null ? item.getTask().getTitle() : null,
                item.getPlannedHours(),
                item.getActualHours(),
                item.getNotes(),
                item.getStatus(),
                item.getPriorityScore(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
