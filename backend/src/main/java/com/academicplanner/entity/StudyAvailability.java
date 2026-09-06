package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;

/**
 * Stores a student's weekly study availability for a single day-of-week.
 * The planning engine reads these values to determine how many hours are
 * available on each day when generating weekly or monthly plans.
 *
 * <p>One row per (user, dayOfWeek) — enforced by a unique constraint.
 */
@Entity
@Table(name = "study_availability",
        uniqueConstraints = @UniqueConstraint(name = "uk_availability_user_day",
                columnNames = {"user_id", "day_of_week"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    /** Number of hours the student is available to study on this day. */
    @Column(name = "available_hours", nullable = false)
    private double availableHours;
}
