package com.academicplanner.repository;

import com.academicplanner.entity.Task;
import com.academicplanner.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByUserOrderByDueDateTimeAscCreatedAtDesc(User user);

    Optional<Task> findByIdAndUser(Long id, User user);

    List<Task> findAllByUserAndModule_Id(User user, Long moduleId);

    List<Task> findAllByUserAndAssessment_Id(User user, Long assessmentId);

    /** Tasks with remaining work that are not completed or cancelled — for planning. */
    @Query("""
            SELECT t FROM Task t
            WHERE t.user.id = :userId
              AND t.status NOT IN ('COMPLETED', 'CANCELLED')
              AND (t.remainingHours IS NULL OR t.remainingHours > 0)
            ORDER BY t.dueDateTime ASC NULLS LAST
            """)
    List<Task> findActivePlanningTasks(@Param("userId") Long userId);
}
