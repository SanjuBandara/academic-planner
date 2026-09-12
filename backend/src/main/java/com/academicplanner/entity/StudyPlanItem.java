package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A single scheduled study/work session within a {@link StudyPlan}.
 *
 * <p>Links to a module and optionally to an assessment and/or task.
 * The {@code activityLabel} provides a human-readable name shown in the plan,
 * e.g. "DSA Quiz Preparation" or "Complete DSA Practice Problems".
 *
 * <p>Historical (COMPLETED/SKIPPED) items must never be modified by the
 * replanning algorithm — only PLANNED future items can be updated.
 */
@Entity
@Table(name = "study_plan_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_plan_id", nullable = false)
    private StudyPlan studyPlan;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private Module module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    /**
     * Human-readable activity name displayed in the academic plan.
     * Examples:
     * <ul>
     *   <li>"DSA Quiz Preparation" (for assessment-based item)</li>
     *   <li>"Complete DSA Practice Problems" (for task-based item)</li>
     * </ul>
     */
    @Column(name = "activity_label")
    private String activityLabel;

    /**
     * Type of the planning item: ASSESSMENT_PREP or TASK.
     * Used by the frontend to display the correct badge/icon.
     */
    @Column(name = "activity_type", length = 30)
    private String activityType;

    @Column(name = "planned_hours", nullable = false)
    private double plannedHours;

    @Column(name = "actual_hours")
    @Builder.Default
    private double actualHours = 0.0;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private ItemStatus status = ItemStatus.PLANNED;

    /**
     * Planning weight score stored for display/sorting.
     * Higher = more important (more time was allocated).
     */
    @Column(name = "priority_score")
    private Double priorityScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ItemStatus {
        PLANNED, IN_PROGRESS, COMPLETED, SKIPPED
    }
}
