package com.academicplanner.repository;

import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlan.PlanStatus;
import com.academicplanner.entity.StudyPlan.PlanType;
import com.academicplanner.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    List<StudyPlan> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<StudyPlan> findByIdAndUser(Long id, User user);

    /** Find the currently active plan of a given type (at most one should exist). */
    Optional<StudyPlan> findByUserAndTypeAndStatus(User user, PlanType type, PlanStatus status);
}
