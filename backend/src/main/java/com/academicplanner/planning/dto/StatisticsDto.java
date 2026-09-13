package com.academicplanner.planning.dto;

/**
 * Mirrors the Python service's Phase 2 response {@code statistics} object.
 * Adds {@code completedRequiredMinutes}/{@code unfinishedRequiredMinutes},
 * which did not exist in the Phase 1 contract.
 */
public record StatisticsDto(
                int availableMinutes,
                int plannedMinutes,
                int completedRequiredMinutes,
                int unfinishedRequiredMinutes,
                int unallocatedMinutes) {
}
