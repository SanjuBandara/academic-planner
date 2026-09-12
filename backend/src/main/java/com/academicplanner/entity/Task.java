package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a granular academic task belonging to a student.
 * Tasks can optionally be linked to a module.
 *
 * <p>Task priority is USER-SELECTED (High / Medium / Low).
 * The planning engine uses the numeric priority value × module credits
 * to determine the task's share of available study time.
 *
 * <p>Priority numeric values:
 * <pre>
 *   HIGH   = 2.5
 *   MEDIUM = 2.0
 *   LOW    = 1.0
 * </pre>
 *
 * <p>Planning weight formula:
 * <pre>
 *   Task Weight = priorityValue × moduleCredits
 * </pre>
 *
 * <p>{@code estimatedHours} acts as a cap — the planning engine will not allocate
 * more hours than this value to the task. Remaining hours are tracked via
 * {@code remainingHours}, which decreases as the student logs progress.
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

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Total estimated work hours. Acts as a planning cap — the engine will
     * not allocate more than this. Null means no cap (allocation determined
     * purely by weight).
     */
    @Column(name = "estimated_hours")
    private Double estimatedHours;

    /**
     * Remaining hours of work. Updated whenever the student records progress.
     * Must never go below 0. When null, falls back to estimatedHours.
     */
    @Column(name = "remaining_hours")
    private Double remainingHours;

    /**
     * User-selected priority. Drives planning weight calculation.
     * HIGH=2.5, MEDIUM=2.0, LOW=1.0
     */
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

    /** Returns the numeric priority value used by the planning engine. */
    public double priorityValue() {
        return switch (priority) {
            case HIGH   -> 2.5;
            case MEDIUM -> 2.0;
            case LOW    -> 1.0;
        };
    }

    public enum TaskStatus {
        TODO, IN_PROGRESS, COMPLETED, CANCELLED
    }

    /**
     * User-selectable priority levels.
     * CRITICAL removed — priorities are High / Medium / Low only.
     */
    public enum TaskPriority {
        HIGH, MEDIUM, LOW
    }
}
