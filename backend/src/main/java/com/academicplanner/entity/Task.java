package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a granular academic task belonging to a student.
 * Tasks can optionally be linked to a module and/or an assessment.
 *
 * <p>The planning engine always works with {@code remainingHours} (not the
 * original estimate) so that partially-completed work reduces future allocation.
 * Remaining hours are floored at 0.0 — never negative.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning student — derived from Spring Security context, never from the request body. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Optional: task belongs to a specific module. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private Module module;

    /** Optional: task belongs to a specific assessment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    /**
     * Remaining hours of work. Updated whenever the student records progress.
     * Must never go below 0.
     */
    @Column(name = "remaining_hours")
    private Double remainingHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "due_date_time")
    private LocalDateTime dueDateTime;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        // Initialise remainingHours from estimatedHours if not explicitly set.
        if (this.remainingHours == null && this.estimatedHours != null) {
            this.remainingHours = this.estimatedHours;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum TaskStatus {
        TODO, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public enum TaskPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
