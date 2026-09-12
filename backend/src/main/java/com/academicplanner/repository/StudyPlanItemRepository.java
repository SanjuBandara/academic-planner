package com.academicplanner.repository;

import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StudyPlanItemRepository extends JpaRepository<StudyPlanItem, Long> {

    List<StudyPlanItem> findAllByStudyPlan_IdOrderByDateAscStartTimeAsc(Long studyPlanId);

    List<StudyPlanItem> findAllByStudyPlan_IdAndDateOrderByStartTimeAsc(Long studyPlanId, LocalDate date);

    Optional<StudyPlanItem> findByIdAndStudyPlan_User_Id(Long id, Long userId);

    /** Items for today for a specific user across all active plans. */
    @Query("""
            SELECT i FROM StudyPlanItem i
            WHERE i.studyPlan.user.id = :userId
              AND i.date = :date
              AND i.studyPlan.status = 'ACTIVE'
            ORDER BY i.startTime ASC
            """)
    List<StudyPlanItem> findTodaysItems(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** Future PLANNED items (after a given date) for replanning. */
    @Query("""
            SELECT i FROM StudyPlanItem i
            WHERE i.studyPlan.id = :planId
              AND i.date >= :fromDate
              AND i.status = 'PLANNED'
            ORDER BY i.date ASC, i.startTime ASC
            """)
    List<StudyPlanItem> findFuturePlannedItems(@Param("planId") Long planId,
                                               @Param("fromDate") LocalDate fromDate);

    boolean existsByIdAndStudyPlan_User_Id(Long id, Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE StudyPlanItem spi SET spi.task = null WHERE spi.task.id = :taskId")
    void nullifyTaskReference(@Param("taskId") Long taskId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE StudyPlanItem spi SET spi.assessment = null WHERE spi.assessment.id = :assessmentId")
    void nullifyAssessmentReference(@Param("assessmentId") Long assessmentId);
}
