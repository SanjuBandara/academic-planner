package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents an academic assessment (assignment, exam, quiz, etc.) for a module.
 *
 * <p>Assessment priority is NOT user-selectable. It is calculated automatically
 * by the planning engine using:
 * <pre>
 *   basePriority (by type) + deadlineUrgencyBonus
 * </pre>
 * The {@link #weight} field is the grade percentage (0–100), not the planning weight.
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
     * Grade weight of this assessment in the overall module grade (0–100).
     * This is for grade tracking purposes only, NOT the planning weight.
     * Planning weight is derived from type + module credits + deadline urgency.
     */
    @Column(name = "weight")
    private Double weight;

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
        ASSIGNMENT, EXAM, QUIZ, PROJECT, PRESENTATION, REPORT, OTHER
    }

    public enum AssessmentStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
