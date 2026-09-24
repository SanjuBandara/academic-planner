package com.academicplanner.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatHistoryResponse {

    private List<ChatMessage> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private Long id;
        private String sender; // "user" or "assistant"
        private String text;
        private String intent;
        private LocalDateTime timestamp;
    }
}
