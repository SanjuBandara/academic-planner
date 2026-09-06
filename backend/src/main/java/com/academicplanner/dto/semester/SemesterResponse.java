package com.academicplanner.dto.semester;

import com.academicplanner.entity.Semester;
import com.academicplanner.entity.Semester.SemesterStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SemesterResponse(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        SemesterStatus status,
        int moduleCount,
        int totalCredits,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SemesterResponse from(Semester semester) {
        int moduleCount = semester.getModules() != null ? semester.getModules().size() : 0;
        int totalCredits = semester.getModules() != null
                ? semester.getModules().stream().mapToInt(m -> m.getCredits()).sum()
                : 0;
        return new SemesterResponse(
                semester.getId(),
                semester.getName(),
                semester.getStartDate(),
                semester.getEndDate(),
                semester.getStatus(),
                moduleCount,
                totalCredits,
                semester.getCreatedAt(),
                semester.getUpdatedAt()
        );
    }
}
