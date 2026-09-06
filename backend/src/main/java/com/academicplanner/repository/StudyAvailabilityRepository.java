package com.academicplanner.repository;

import com.academicplanner.entity.StudyAvailability;
import com.academicplanner.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface StudyAvailabilityRepository extends JpaRepository<StudyAvailability, Long> {

    List<StudyAvailability> findAllByUser(User user);

    Optional<StudyAvailability> findByUserAndDayOfWeek(User user, DayOfWeek dayOfWeek);

    void deleteAllByUser(User user);
}
