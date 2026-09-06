package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a generated study plan (weekly or monthly) for a student.
 * Only one ACTIVE plan of each type should exist at a time (enforced in the service).
 *
 * <p>Plan generation is transactional — either all items are created or none are.
 */
@Entity
@Table(name = "study_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private PlanType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private PlanStatus status = PlanStatus.ACTIVE;

    /** Total hours the student declared as available for this plan period. */
    @Column(name = "total_available_hours")
    private Double totalAvailableHours;

    /** Total hours actually allocated across all plan items. */
    @Column(name = "total_planned_hours")
    private Double totalPlannedHours;

    @OneToMany(mappedBy = "studyPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudyPlanItem> items = new ArrayList<>();

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

    public enum PlanType {
        WEEKLY, MONTHLY
    }

    public enum PlanStatus {
        ACTIVE, COMPLETED, CANCELLED
    }
}
