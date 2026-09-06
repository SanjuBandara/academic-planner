package com.academicplanner.controller;

import com.academicplanner.dto.semester.SemesterRequest;
import com.academicplanner.dto.semester.SemesterResponse;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.UserRepository;
import com.academicplanner.service.SemesterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/semesters")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @GetMapping
    public ResponseEntity<List<SemesterResponse>> getAllForUser(
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.ok(
                semesterService.getAllForUser(user)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SemesterResponse> getById(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.ok(
                semesterService.getById(id, user)
        );
    }

    @PostMapping
    public ResponseEntity<SemesterResponse> create(
            @Valid @RequestBody SemesterRequest request,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(semesterService.create(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SemesterResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SemesterRequest request,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.ok(
                semesterService.update(id, request, user)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        semesterService.delete(id, user);

        return ResponseEntity.noContent().build();
    }
}