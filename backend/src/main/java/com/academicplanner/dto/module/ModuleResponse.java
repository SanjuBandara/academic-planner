package com.academicplanner.dto.module;

import com.academicplanner.entity.Module;

import java.time.LocalDateTime;

public record ModuleResponse(
        Long id,
        Long semesterId,
        String semesterName,
        String code,
        String name,
        int credits,
        String description,
        int assessmentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ModuleResponse from(Module module) {
        int assessmentCount = module.getAssessments() != null ? module.getAssessments().size() : 0;
        return new ModuleResponse(
                module.getId(),
                module.getSemester().getId(),
                module.getSemester().getName(),
                module.getCode(),
                module.getName(),
                module.getCredits(),
                module.getDescription(),
                assessmentCount,
                module.getCreatedAt(),
                module.getUpdatedAt()
        );
    }
}
