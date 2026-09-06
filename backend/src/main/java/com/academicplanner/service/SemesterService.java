package com.academicplanner.service;

import com.academicplanner.dto.semester.SemesterRequest;
import com.academicplanner.dto.semester.SemesterResponse;
import com.academicplanner.entity.Semester;
import com.academicplanner.entity.User;
import com.academicplanner.exception.AccessDeniedException;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository semesterRepository;

    @Transactional(readOnly = true)
    public List<SemesterResponse> getAllForUser(User user) {
        return semesterRepository.findAllByUserOrderByStartDateDesc(user)
                .stream()
                .map(SemesterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SemesterResponse getById(Long id, User user) {
        Semester semester = semesterRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + id));
        return SemesterResponse.from(semester);
    }

    @Transactional
    public SemesterResponse create(SemesterRequest request, User user) {
        validateDates(request);
        Semester semester = Semester.builder()
                .user(user)
                .name(request.name())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(request.status() != null ? request.status() : Semester.SemesterStatus.PLANNED)
                .build();
        return SemesterResponse.from(semesterRepository.save(semester));
    }

    @Transactional
    public SemesterResponse update(Long id, SemesterRequest request, User user) {
        Semester semester = semesterRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + id));
        validateDates(request);
        semester.setName(request.name());
        semester.setStartDate(request.startDate());
        semester.setEndDate(request.endDate());
        if (request.status() != null) {
            semester.setStatus(request.status());
        }
        return SemesterResponse.from(semesterRepository.save(semester));
    }

    @Transactional
    public void delete(Long id, User user) {
        Semester semester = semesterRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + id));
        semesterRepository.delete(semester);
    }

    /** Returns the raw entity (for other services that need to cross-check ownership). */
    @Transactional(readOnly = true)
    public Semester getEntityById(Long id, User user) {
        return semesterRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found: " + id));
    }

    private void validateDates(SemesterRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date must be after start date.");
        }
    }
}
