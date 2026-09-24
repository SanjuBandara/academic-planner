package com.academicplanner.ai.tool;

import com.academicplanner.dto.assessment.AssessmentResponse;
import com.academicplanner.entity.User;
import com.academicplanner.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Controlled tool for retrieving assessment details for the authenticated student.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentTool {

    private final AssessmentService assessmentService;

    public List<AssessmentResponse> getUpcomingAssessments(User user) {
        log.info("[AssessmentTool] Retrieving upcoming assessments for user: {}", user.getId());
        return assessmentService.getUpcoming(user, 30);
    }

    public AssessmentResponse getAssessmentDetails(Long assessmentId, User user) {
        log.info("[AssessmentTool] Retrieving assessment details for assessmentId: {}, user: {}", assessmentId, user.getId());
        return assessmentService.getById(assessmentId, user);
    }
}
