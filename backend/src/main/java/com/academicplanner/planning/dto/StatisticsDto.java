package com.academicplanner.planning.dto;

public record StatisticsDto(
                int availableMinutes,
                int plannedMinutes,
                int unallocatedMinutes) {
}
