package com.academicplanner.service;

import com.academicplanner.dto.assessment.AssessmentRequest;
import com.academicplanner.dto.assessment.AssessmentResponse;
import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.AssessmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final ModuleService moduleService;

    @Transactional(readOnly = true)
    public List<AssessmentResponse> getAllByModule(Long moduleId, User user) {
        // Ownership check via module lookup
        moduleService.getEntityById(moduleId, user);
        return assessmentRepository.findAllByModule_IdOrderByDueDateTimeAsc(moduleId)
                .stream().map(AssessmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getById(Long id, User user) {
        Assessment assessment = assessmentRepository.findByIdAndModule_Semester_User_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + id));
        return AssessmentResponse.from(assessment);
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> getUpcoming(User user, int days) {
        LocalDateTime cutoff = LocalDateTime.now().plusDays(days);
        return assessmentRepository.findUpcomingAssessments(user.getId(), cutoff)
                .stream().map(AssessmentResponse::from).toList();
    }

    @Transactional
    public AssessmentResponse create(Long moduleId, AssessmentRequest request, User user) {
        Module module = moduleService.getEntityById(moduleId, user);

        Assessment assessment = Assessment.builder()
                .module(module)
                .title(request.title())
                .type(request.type())
                .description(request.description())
                .dueDateTime(request.dueDateTime())
                .weight(request.weight())
                .status(request.status() != null ? request.status() : Assessment.AssessmentStatus.PENDING)
                .build();

        return AssessmentResponse.from(assessmentRepository.save(assessment));
    }

    @Transactional
    public AssessmentResponse update(Long id, AssessmentRequest request, User user) {
        Assessment assessment = assessmentRepository.findByIdAndModule_Semester_User_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + id));

        assessment.setTitle(request.title());
        assessment.setType(request.type());
        assessment.setDescription(request.description());
        assessment.setDueDateTime(request.dueDateTime());
        assessment.setWeight(request.weight());
        if (request.status() != null) assessment.setStatus(request.status());

        return AssessmentResponse.from(assessmentRepository.save(assessment));
    }

    @Transactional
    public void delete(Long id, User user) {
        Assessment assessment = assessmentRepository.findByIdAndModule_Semester_User_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + id));
        assessmentRepository.delete(assessment);
    }

    /** Raw entity for planning engine. */
    @Transactional(readOnly = true)
    public List<Assessment> getActivePlanningAssessments(User user) {
        return assessmentRepository.findActivePlanningAssessments(user.getId());
    }
}
