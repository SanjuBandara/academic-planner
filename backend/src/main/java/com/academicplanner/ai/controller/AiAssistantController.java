package com.academicplanner.ai.controller;

import com.academicplanner.ai.dto.AiActionRequest;
import com.academicplanner.ai.dto.AiActionResponse;
import com.academicplanner.ai.dto.AiChatRequest;
import com.academicplanner.ai.dto.AiChatResponse;
import com.academicplanner.ai.service.AiAssistantService;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AI Planning Assistant interactions.
 * All requests are bound strictly to the authenticated student.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final UserRepository userRepository;

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("Authenticated user context is missing");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }

    /**
     * Natural-language chat endpoint for the AI Assistant.
     */
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request,
                                               Authentication authentication) {
        User user = getCurrentUser(authentication);
        log.info("[AiAssistantController] Chat request received from student: {}", user.getEmail());
        AiChatResponse response = aiAssistantService.processChat(request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Confirmation endpoint for applying or cancelling AI-proposed plan modifications.
     */
    @PostMapping("/action/confirm")
    public ResponseEntity<AiActionResponse> confirmAction(@Valid @RequestBody AiActionRequest request,
                                                          Authentication authentication) {
        User user = getCurrentUser(authentication);
        log.info("[AiAssistantController] Action confirm received from student {}: proposalId={}, confirmed={}",
                user.getEmail(), request.getProposalId(), request.isConfirmed());
        AiActionResponse response = aiAssistantService.confirmAction(request, user);
        return ResponseEntity.ok(response);
    }
}
