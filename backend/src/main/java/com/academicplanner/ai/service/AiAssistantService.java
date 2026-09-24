package com.academicplanner.ai.service;

import com.academicplanner.ai.dto.AiActionRequest;
import com.academicplanner.ai.dto.AiActionResponse;
import com.academicplanner.ai.dto.AiChatRequest;
import com.academicplanner.ai.dto.AiChatResponse;
import com.academicplanner.ai.model.AiContext;
import com.academicplanner.ai.provider.AiProvider.AiProviderResponse;
import com.academicplanner.entity.AiChatHistory;
import com.academicplanner.entity.User;
import com.academicplanner.repository.AiChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Primary coordinator service for the AI Planning Assistant.
 * Orchestrates context assembly, provider invocation, and plan confirmation logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final AiContextService aiContextService;
    private final AiProviderService aiProviderService;
    private final AiToolService aiToolService;
    private final AiChatHistoryRepository aiChatHistoryRepository;

    /**
     * Processes a natural language question or command from the authenticated student.
     */
    @Transactional
    public AiChatResponse processChat(AiChatRequest request, User user) {
        log.info("[AiAssistantService] Processing chat message for student {}: {}", user.getId(), request.getMessage());

        // Save user message
        aiChatHistoryRepository.save(AiChatHistory.builder()
                .user(user)
                .role("user")
                .message(request.getMessage())
                .build());

        // Gather relevant data strictly for this authenticated user
        AiContext context = aiContextService.buildContext(user);

        // Generate response via AI provider (or grounded fallback), passing user for action intents
        AiProviderResponse providerResponse = aiProviderService.generateResponse(request.getMessage(), context, user);

        // Save assistant message
        aiChatHistoryRepository.save(AiChatHistory.builder()
                .user(user)
                .role("assistant")
                .message(providerResponse.getMessage())
                .intent(providerResponse.getIntent() != null ? providerResponse.getIntent().name() : null)
                .build());

        return AiChatResponse.builder()
                .message(providerResponse.getMessage())
                .intent(providerResponse.getIntent())
                .actionRequired(providerResponse.isActionRequired())
                .action(providerResponse.getAction())
                .planPreview(providerResponse.getPlanPreview())
                .build();
    }

    /**
     * Confirms or rejects a proposed plan modification.
     */
    public AiActionResponse confirmAction(AiActionRequest request, User user) {
        log.info("[AiAssistantService] Confirm action received: proposalId={}, confirmed={}, user={}",
                request.getProposalId(), request.isConfirmed(), user.getId());

        if (!request.isConfirmed()) {
            aiToolService.cancelProposal(request.getProposalId(), user);
            return AiActionResponse.builder()
                    .success(true)
                    .message("The proposed schedule modification was cancelled.")
                    .build();
        }

        boolean applied = aiToolService.applyProposal(request.getProposalId(), user);
        if (applied) {
            return AiActionResponse.builder()
                    .success(true)
                    .message("The proposed study plan changes have been successfully applied to your schedule.")
                    .build();
        } else {
            return AiActionResponse.builder()
                    .success(false)
                    .message("Failed to apply the proposed change. The proposal may have expired or is invalid.")
                    .build();
        }
    }

    /**
     * Retrieves the recent chat history for the authenticated user.
     */
    @Transactional(readOnly = true)
    public com.academicplanner.ai.dto.AiChatHistoryResponse getChatHistory(User user) {
        // Fetch last 50 messages, ordered desc, but we want them ascending for chat UI
        java.util.List<AiChatHistory> history = aiChatHistoryRepository
                .findRecentHistoryByUserId(user.getId(), org.springframework.data.domain.PageRequest.of(0, 50));
        
        // Reverse to chronological order
        java.util.List<com.academicplanner.ai.dto.AiChatHistoryResponse.ChatMessage> messages = new java.util.ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            AiChatHistory h = history.get(i);
            messages.add(com.academicplanner.ai.dto.AiChatHistoryResponse.ChatMessage.builder()
                    .id(h.getId())
                    .sender(h.getRole())
                    .text(h.getMessage())
                    .intent(h.getIntent())
                    .timestamp(h.getTimestamp())
                    .build());
        }

        return com.academicplanner.ai.dto.AiChatHistoryResponse.builder()
                .messages(messages)
                .build();
    }

    /**
     * Generates a dynamic daily hint based on the student's plan and upcoming deadlines.
     */
    @Transactional(readOnly = true)
    public String generateDailyHint(User user) {
        AiContext context = aiContextService.buildContext(user);
        
        java.util.List<AiContext.AssessmentSummary> assessments = context.getUpcomingAssessments();
        if (assessments != null && !assessments.isEmpty()) {
            AiContext.AssessmentSummary urgent = assessments.get(0);
            return String.format("💡 Don't forget! Your '%s' assessment is coming up on %s. Allocate time for it today.", 
                    urgent.getTitle(), urgent.getDueDateTime() != null ? urgent.getDueDateTime().split("T")[0] : "soon");
        }

        java.util.List<AiContext.SessionSummary> todayPlan = context.getTodayPlan();
        if (todayPlan != null && !todayPlan.isEmpty()) {
            long remaining = todayPlan.stream().filter(s -> !"COMPLETED".equals(s.getStatus()) && !"SKIPPED".equals(s.getStatus())).count();
            if (remaining > 0) {
                return String.format("💡 You have %d pending study session(s) today. Stay focused and knock them out!", remaining);
            } else {
                return "💡 Great job! You've finished all your planned sessions for today. Take a well-deserved break! 🎉";
            }
        }

        return "💡 Consistency is key! Even 30 minutes of review today can make a big difference.";
    }
}
