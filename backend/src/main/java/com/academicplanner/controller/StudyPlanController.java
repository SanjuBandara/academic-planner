package com.academicplanner.controller;

import com.academicplanner.dto.studyplan.*;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.UserRepository;
import com.academicplanner.service.StudyPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/study-plans")
@RequiredArgsConstructor
public class StudyPlanController {

    private final StudyPlanService studyPlanService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @PostMapping("/generate")
    public ResponseEntity<StudyPlanResponse> generateWeeklyPlan(@Valid @RequestBody WeeklyPlanRequest request,
                                                                 Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(studyPlanService.generateWeeklyPlan(request, user));
    }

    @GetMapping("/active")
    public ResponseEntity<StudyPlanResponse> getActivePlan(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(studyPlanService.getActivePlan(user));
    }

    @GetMapping("/today")
    public ResponseEntity<TodaysScheduleResponse> getTodaysSchedule(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(studyPlanService.getTodaysSchedule(user));
    }

    @PatchMapping("/items/{itemId}/status")
    public ResponseEntity<StudyPlanItemResponse> updateItemStatus(@PathVariable Long itemId,
                                                                   @RequestParam ItemStatus status,
                                                                   Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(studyPlanService.updateItemStatus(itemId, status, user));
    }
}
