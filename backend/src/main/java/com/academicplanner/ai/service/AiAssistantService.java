package com.academicplanner.ai.service;

import com.academicplanner.ai.dto.AiActionRequest;
import com.academicplanner.ai.dto.AiActionResponse;
import com.academicplanner.ai.dto.AiChatRequest;
import com.academicplanner.ai.dto.AiChatResponse;
import com.academicplanner.ai.model.AiContext;
import com.academicplanner.ai.provider.AiProvider.AiProviderResponse;
import com.academicplanner.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /**
     * Processes a natural language question or command from the authenticated student.
     */
    public AiChatResponse processChat(AiChatRequest request, User user) {
        log.info("[AiAssistantService] Processing chat message for student {}: {}", user.getId(), request.getMessage());

        // Gather relevant data strictly for this authenticated user
        AiContext context = aiContextService.buildContext(user);

        // Generate response via AI provider (or grounded fallback)
        AiProviderResponse providerResponse = aiProviderService.generateResponse(request.getMessage(), context);

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
}
