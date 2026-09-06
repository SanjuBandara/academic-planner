package com.academicplanner.controller;

import com.academicplanner.dto.module.ModuleRequest;
import com.academicplanner.dto.module.ModuleResponse;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.UserRepository;
import com.academicplanner.service.ModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @GetMapping("/api/semesters/{semesterId}/modules")
    public ResponseEntity<List<ModuleResponse>> getBySemester(
            @PathVariable Long semesterId,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.ok(
                moduleService.getAllBySemester(semesterId, user)
        );
    }

    @GetMapping("/api/modules")
    public ResponseEntity<List<ModuleResponse>> getAllForUser(
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.ok(
                moduleService.getAllForUser(user)
        );
    }

    @GetMapping("/api/modules/{id}")
    public ResponseEntity<ModuleResponse> getById(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.ok(
                moduleService.getById(id, user)
        );
    }

    @PostMapping("/api/semesters/{semesterId}/modules")
    public ResponseEntity<ModuleResponse> create(
            @PathVariable Long semesterId,
            @Valid @RequestBody ModuleRequest request,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(moduleService.create(semesterId, request, user));
    }

    @PutMapping("/api/modules/{id}")
    public ResponseEntity<ModuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ModuleRequest request,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return ResponseEntity.ok(
                moduleService.update(id, request, user)
        );
    }

    @DeleteMapping("/api/modules/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        moduleService.delete(id, user);

        return ResponseEntity.noContent().build();
    }
}