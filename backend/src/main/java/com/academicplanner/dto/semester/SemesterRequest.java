package com.academicplanner.dto.semester;

import com.academicplanner.entity.Semester.SemesterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SemesterRequest(
        @NotBlank(message = "Semester name is required")
        String name,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        SemesterStatus status
) {}
