package com.academicplanner.controller;

import com.academicplanner.dto.task.TaskProgressUpdate;
import com.academicplanner.dto.task.TaskRequest;
import com.academicplanner.dto.task.TaskResponse;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.UserRepository;
import com.academicplanner.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllForUser(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(taskService.getAllForUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getById(@PathVariable Long id, Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(taskService.getById(id, user));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request,
                                                Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody TaskRequest request,
                                                Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(taskService.update(id, request, user));
    }

    @PatchMapping("/{id}/progress")
    public ResponseEntity<TaskResponse> updateProgress(@PathVariable Long id,
                                                        @Valid @RequestBody TaskProgressUpdate update,
                                                        Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(taskService.updateProgress(id, update, user));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<TaskResponse> toggleComplete(@PathVariable Long id,
                                                        Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(taskService.toggleComplete(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = getCurrentUser(authentication);
        taskService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
