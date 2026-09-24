package com.academicplanner.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured model containing authenticated student context gathered
 * from the database for the AI assistant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContext {

    private LocalDate currentDate;
    private LocalTime currentTime;
    private String studentEmail;
    private Long activePlanId;
    private String planStatus;

    @Builder.Default
    private List<SessionSummary> todayPlan = new ArrayList<>();

    @Builder.Default
    private List<SessionSummary> weekPlan = new ArrayList<>();

    @Builder.Default
    private List<AssessmentSummary> upcomingAssessments = new ArrayList<>();

    @Builder.Default
    private List<TaskSummary> tasks = new ArrayList<>();

    @Builder.Default
    private List<AvailabilitySummary> availability = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSummary {
        private Long id;
        private String date;
        private String startTime;
        private String endTime;
        private String moduleCode;
        private String moduleName;
        private String activityLabel;
        private String activityType;
        private double plannedHours;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssessmentSummary {
        private Long id;
        private String title;
        private String moduleCode;
        private String moduleName;
        private String dueDateTime;
        private String type;
        private Double weight;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskSummary {
        private Long id;
        private String title;
        private String moduleCode;
        private String dueDateTime;
        private Double estimatedHours;
        private Double remainingHours;
        private String priority;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilitySummary {
        private String dayOfWeek;
        private double availableHours;
    }
}
