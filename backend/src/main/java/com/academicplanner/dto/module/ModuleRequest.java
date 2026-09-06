package com.academicplanner.dto.module;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModuleRequest(
        @NotBlank(message = "Module code is required")
        @Size(max = 20, message = "Module code must be 20 characters or less")
        String code,

        @NotBlank(message = "Module name is required")
        String name,

        @NotNull(message = "Credits are required")
        @Min(value = 1, message = "Credits must be greater than 0")
        Integer credits,

        String description
) {}
