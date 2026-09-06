package com.academicplanner.repository;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findAllByModule_IdOrderByDueDateTimeAsc(Long moduleId);

    Optional<Assessment> findByIdAndModule_Semester_User_Id(Long id, Long userId);

    /** Fetch all non-completed, non-cancelled assessments for a user — used by the planning engine. */
    @Query("""
            SELECT a FROM Assessment a
            WHERE a.module.semester.user.id = :userId
              AND a.status NOT IN ('COMPLETED', 'CANCELLED')
            ORDER BY a.dueDateTime ASC NULLS LAST
            """)
    List<Assessment> findActivePlanningAssessments(@Param("userId") Long userId);

    /** Upcoming assessments within N days (for dashboard display). */
    @Query("""
            SELECT a FROM Assessment a
            WHERE a.module.semester.user.id = :userId
              AND a.status NOT IN ('COMPLETED', 'CANCELLED')
              AND a.dueDateTime IS NOT NULL
              AND a.dueDateTime <= :cutoff
            ORDER BY a.dueDateTime ASC
            """)
    List<Assessment> findUpcomingAssessments(@Param("userId") Long userId,
                                             @Param("cutoff") LocalDateTime cutoff);

    boolean existsByIdAndModule_Semester_User_Id(Long id, Long userId);
}
