package com.academicplanner.ai;

import com.academicplanner.ai.controller.AiAssistantController;
import com.academicplanner.ai.dto.AiChatRequest;
import com.academicplanner.ai.dto.AiChatResponse;
import com.academicplanner.ai.model.AiIntent;
import com.academicplanner.ai.service.AiAssistantService;
import com.academicplanner.entity.User;
import com.academicplanner.repository.UserRepository;
import com.academicplanner.security.AuthEntryPoint;
import com.academicplanner.security.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiAssistantController.class)
@AutoConfigureMockMvc
class AiAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiAssistantService aiAssistantService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private AuthEntryPoint authEntryPoint;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("student@uni.edu")
                .passwordHash("hash")
                .build();
    }

    @Test
    @WithMockUser(username = "student@uni.edu")
    void testChat_validMessage_returnsOk() throws Exception {
        when(userRepository.findByEmail("student@uni.edu")).thenReturn(Optional.of(testUser));
        when(aiAssistantService.processChat(any(AiChatRequest.class), any(User.class)))
                .thenReturn(AiChatResponse.builder()
                        .message("Today you have 2 sessions scheduled.")
                        .intent(AiIntent.GET_TODAY_PLAN)
                        .actionRequired(false)
                        .build());

        AiChatRequest request = new AiChatRequest("What do I have to study today?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Today you have 2 sessions scheduled."))
                .andExpect(jsonPath("$.intent").value("GET_TODAY_PLAN"))
                .andExpect(jsonPath("$.actionRequired").value(false));
    }

    @Test
    @WithMockUser(username = "student@uni.edu")
    void testChat_emptyMessage_returnsBadRequest() throws Exception {
        when(userRepository.findByEmail("student@uni.edu")).thenReturn(Optional.of(testUser));

        AiChatRequest request = new AiChatRequest("");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChat_unauthorized_returnsUnauthorized() throws Exception {
        AiChatRequest request = new AiChatRequest("What do I have to study today?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }


}
