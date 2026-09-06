package com.academicplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a university module (course) within a semester.
 * Module codes must be unique within the same semester.
 */
@Entity
@Table(name = "modules",
        uniqueConstraints = @UniqueConstraint(name = "uk_module_semester_code",
                columnNames = {"semester_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    /** E.g. "IT3040" */
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    /** E.g. "Software Engineering" */
    @Column(name = "name", nullable = false)
    private String name;

    /** Credit value — must be > 0. Drives baseline workload allocation. */
    @Column(name = "credits", nullable = false)
    private int credits;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Assessment> assessments = new ArrayList<>();

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
}
