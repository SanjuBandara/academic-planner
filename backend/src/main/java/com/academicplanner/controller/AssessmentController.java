package com.academicplanner.controller;

import com.academicplanner.dto.assessment.AssessmentRequest;
import com.academicplanner.dto.assessment.AssessmentResponse;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.UserRepository;
import com.academicplanner.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @GetMapping("/api/modules/{moduleId}/assessments")
    public ResponseEntity<List<AssessmentResponse>> getByModule(@PathVariable Long moduleId,
                                                                 Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(assessmentService.getAllByModule(moduleId, user));
    }

    @GetMapping("/api/assessments/upcoming")
    public ResponseEntity<List<AssessmentResponse>> getUpcoming(@RequestParam(defaultValue = "14") int days,
                                                                 Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(assessmentService.getUpcoming(user, days));
    }

    @GetMapping("/api/assessments/{id}")
    public ResponseEntity<AssessmentResponse> getById(@PathVariable Long id, Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(assessmentService.getById(id, user));
    }

    @PostMapping("/api/modules/{moduleId}/assessments")
    public ResponseEntity<AssessmentResponse> create(@PathVariable Long moduleId,
                                                      @Valid @RequestBody AssessmentRequest request,
                                                      Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(assessmentService.create(moduleId, request, user));
    }

    @PutMapping("/api/assessments/{id}")
    public ResponseEntity<AssessmentResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody AssessmentRequest request,
                                                      Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(assessmentService.update(id, request, user));
    }

    @DeleteMapping("/api/assessments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = getCurrentUser(authentication);
        assessmentService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
