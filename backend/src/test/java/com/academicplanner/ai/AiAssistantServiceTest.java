package com.academicplanner.ai;

import com.academicplanner.ai.dto.AiChatRequest;
import com.academicplanner.ai.dto.AiChatResponse;
import com.academicplanner.ai.model.AiContext;
import com.academicplanner.ai.model.AiIntent;
import com.academicplanner.ai.service.AiAssistantService;
import com.academicplanner.ai.service.AiContextService;
import com.academicplanner.ai.service.AiProviderService;
import com.academicplanner.ai.service.AiToolService;
import com.academicplanner.entity.User;
import com.academicplanner.repository.AiChatHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private AiContextService aiContextService;

    @Mock
    private AiProviderService aiProviderService;

    @Mock
    private AiToolService aiToolService;

    @Mock
    private AiChatHistoryRepository aiChatHistoryRepository;

    @InjectMocks
    private AiAssistantService aiAssistantService;

    private User testUser;
    private AiContext mockContext;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("student@university.edu")
                .passwordHash("secret")
                .build();

        mockContext = AiContext.builder()
                .currentDate(LocalDate.now())
                .currentTime(LocalTime.of(10, 0))
                .studentEmail(testUser.getEmail())
                .todayPlan(List.of(
                        AiContext.SessionSummary.builder()
                                .id(101L)
                                .activityLabel("DSA Assignment Preparation")
                                .moduleCode("CS201")
                                .startTime("14:00")
                                .endTime("16:00")
                                .plannedHours(2.0)
                                .status("PLANNED")
                                .build()
                ))
                .upcomingAssessments(List.of(
                        AiContext.AssessmentSummary.builder()
                                .id(201L)
                                .title("IN2011 Midterm Exam")
                                .moduleCode("IN2011")
                                .dueDateTime(LocalDate.now().plusDays(3) + "T10:00:00")
                                .weight(25.0)
                                .status("PENDING")
                                .build()
                ))
                .build();
    }

    @Test
    void testWhatDoIHaveToday_returnsTodayPlan() {
        when(aiContextService.buildContext(testUser)).thenReturn(mockContext);
        when(aiProviderService.generateResponse(eq("What do I have today?"), any(AiContext.class), eq(testUser)))
                .thenReturn(com.academicplanner.ai.provider.AiProvider.AiProviderResponse.builder()
                        .message("Today you have 1 session: DSA Assignment Preparation from 14:00 to 16:00.")
                        .intent(AiIntent.GET_TODAY_PLAN)
                        .actionRequired(false)
                        .build());

        AiChatRequest request = new AiChatRequest("What do I have today?");
        AiChatResponse response = aiAssistantService.processChat(request, testUser);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("DSA Assignment Preparation");
        assertThat(response.getIntent()).isEqualTo(AiIntent.GET_TODAY_PLAN);
        assertThat(response.isActionRequired()).isFalse();

        verify(aiContextService).buildContext(testUser);
        verify(aiProviderService).generateResponse("What do I have today?", mockContext, testUser);
    }

    @Test
    void testUpcomingAssessments_usesAssessmentContext() {
        when(aiContextService.buildContext(testUser)).thenReturn(mockContext);
        when(aiProviderService.generateResponse(eq("What are my upcoming assessments?"), any(AiContext.class), eq(testUser)))
                .thenReturn(com.academicplanner.ai.provider.AiProvider.AiProviderResponse.builder()
                        .message("You have 1 upcoming assessment: IN2011 Midterm Exam due in 3 days.")
                        .intent(AiIntent.GET_UPCOMING_ASSESSMENTS)
                        .actionRequired(false)
                        .build());

        AiChatRequest request = new AiChatRequest("What are my upcoming assessments?");
        AiChatResponse response = aiAssistantService.processChat(request, testUser);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("IN2011 Midterm Exam");
        assertThat(response.getIntent()).isEqualTo(AiIntent.GET_UPCOMING_ASSESSMENTS);
    }
}
