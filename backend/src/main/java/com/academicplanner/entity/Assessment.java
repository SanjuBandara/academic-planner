package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents an academic assessment (assignment, exam, quiz, etc.) for a module.
 * The planning engine uses dueDateTime, estimatedHours, priority, and status
 * to calculate urgency scores when generating study plans.
 */
@Entity
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AssessmentType type;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** The exact deadline for submission / sitting the assessment. */
    @Column(name = "due_date_time")
    private LocalDateTime dueDateTime;

    /**
     * Total estimated work hours required.
     * The planning engine uses task.remainingHours (not this field directly)
     * when tasks exist, but falls back to this for untracked assessments.
     */
    @Column(name = "estimated_hours")
    private Double estimatedHours;

    /** Percentage weight of this assessment in the overall module grade (0–100). */
    @Column(name = "weight")
    private Double weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private AssessmentPriority priority = AssessmentPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AssessmentStatus status = AssessmentStatus.PENDING;

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

    public enum AssessmentType {
        ASSIGNMENT, EXAM, QUIZ, PRESENTATION, PROJECT, REPORT, OTHER
    }

    public enum AssessmentStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public enum AssessmentPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
