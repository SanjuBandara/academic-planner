package com.academicplanner.ai.tool;

import com.academicplanner.entity.StudyAvailability;
import com.academicplanner.entity.User;
import com.academicplanner.repository.StudyAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Controlled tool for retrieving availability records for the authenticated student.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityTool {

    private final StudyAvailabilityRepository availabilityRepository;

    @Transactional(readOnly = true)
    public Optional<StudyAvailability> getTodayAvailability(User user) {
        log.info("[AvailabilityTool] Retrieving today's availability for user: {}", user.getId());
        return availabilityRepository.findByUserAndDayOfWeek(user, LocalDate.now().getDayOfWeek());
    }

    @Transactional(readOnly = true)
    public List<StudyAvailability> getWeeklyAvailability(User user) {
        log.info("[AvailabilityTool] Retrieving weekly availability for user: {}", user.getId());
        return availabilityRepository.findAllByUser(user);
    }
}
