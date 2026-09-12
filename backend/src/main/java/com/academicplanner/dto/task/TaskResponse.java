package com.academicplanner.dto.task;

import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.entity.Task.TaskStatus;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        Long moduleId,
        String moduleName,
        String moduleCode,
        String title,
        String description,
        Double estimatedHours,
        Double remainingHours,
        TaskPriority priority,
        double priorityValue,
        TaskStatus status,
        LocalDateTime dueDateTime,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getModule() != null ? task.getModule().getId() : null,
                task.getModule() != null ? task.getModule().getName() : null,
                task.getModule() != null ? task.getModule().getCode() : null,
                task.getTitle(),
                task.getDescription(),
                task.getEstimatedHours(),
                task.getRemainingHours(),
                task.getPriority(),
                task.priorityValue(),
                task.getStatus(),
                task.getDueDateTime(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
