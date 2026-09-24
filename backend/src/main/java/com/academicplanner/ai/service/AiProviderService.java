package com.academicplanner.ai.service;

import com.academicplanner.ai.config.AiConfig;
import com.academicplanner.ai.model.AiAction;
import com.academicplanner.ai.model.AiContext;
import com.academicplanner.ai.model.AiIntent;
import com.academicplanner.ai.provider.AiProvider;
import com.academicplanner.ai.provider.AiProvider.AiProviderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Service that manages AI providers and coordinates generating responses.
 * Houses the centralized AI System Prompt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final AiConfig aiConfig;
    private final List<AiProvider> providers;

    public static final String CENTRAL_SYSTEM_PROMPT = """
            You are the AI Planning Assistant for an Academic Planner.

            Your responsibilities are:
            1. Understand student questions accurately and empathetically.
            2. Explain the student's existing study plan clearly based strictly on real data.
            3. Help create daily study plans without inventing non-existent slots.
            4. Interpret requested schedule changes into structured actions.
            5. Use available planning tools when necessary.
            6. Never invent schedules or sessions that are not present in the student context.
            7. Never claim that a schedule change was completed unless the backend confirms it.
            8. Never directly modify the database.
            9. The planning engine is authoritative for schedule generation.
            10. Respect the student's existing constraints and availability.
            11. When a requested change is infeasible, explain the conflict clearly.
            12. For significant plan changes, provide a preview and require confirmation.
            13. Only access and discuss data belonging to the authenticated student.
            14. Do not expose internal implementation details or secrets unnecessarily.
            """;

    public AiProviderResponse generateResponse(String userMessage, AiContext context) {
        if (!aiConfig.isEnabled()) {
            log.info("[AiProviderService] AI features are currently disabled via configuration.");
            return AiProviderResponse.builder()
                    .message("The AI assistant is currently disabled. Your existing planner is fully functional.")
                    .intent(AiIntent.UNKNOWN)
                    .actionRequired(false)
                    .build();
        }

        // Find configured provider
        AiProvider activeProvider = providers.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(aiConfig.getProvider()))
                .findFirst()
                .orElse(null);

        if (activeProvider != null && activeProvider.isAvailable()) {
            try {
                return activeProvider.generateResponse(CENTRAL_SYSTEM_PROMPT, userMessage, context);
            } catch (Exception e) {
                log.warn("[AiProviderService] Primary AI provider failed: {}. Falling back to deterministic planner assistant.", e.getMessage());
            }
        }

        // Fallback: Smart deterministic assistant grounded directly in the student's real PostgreSQL data
        return generateGroundedDeterministicResponse(userMessage, context);
    }

    /**
     * Responds accurately to student inquiries when no remote LLM API key is configured
     * or during provider outages, guaranteeing 100% truthful data retrieval without hallucination.
     */
    private AiProviderResponse generateGroundedDeterministicResponse(String message, AiContext context) {
        String lower = message.toLowerCase(Locale.ROOT).trim();

        // 1. Today's plan
        if (lower.contains("today") || lower.contains("today's plan") || lower.contains("what do i have today")) {
            List<AiContext.SessionSummary> todaySessions = context.getTodayPlan();
            if (todaySessions == null || todaySessions.isEmpty()) {
                return AiProviderResponse.builder()
                        .message("You have no study sessions scheduled for today (" + context.getCurrentDate() + "). You're free to take a break or generate a new study plan in the planner!")
                        .intent(AiIntent.GET_TODAY_PLAN)
                        .actionRequired(false)
                        .build();
            }

            double totalHours = todaySessions.stream().mapToDouble(AiContext.SessionSummary::getPlannedHours).sum();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Today (%s), you have %d study session(s) totaling %.1f hours:\n\n",
                    context.getCurrentDate(), todaySessions.size(), totalHours));

            for (AiContext.SessionSummary s : todaySessions) {
                String timeRange = (s.getStartTime() != null && s.getEndTime() != null)
                        ? s.getStartTime() + " - " + s.getEndTime()
                        : String.format("%.1f hrs", s.getPlannedHours());
                sb.append(String.format("• **%s** (%s): %s [%s] — Status: %s\n",
                        s.getActivityLabel() != null ? s.getActivityLabel() : "Session",
                        s.getModuleCode() != null ? s.getModuleCode() : "General",
                        timeRange,
                        s.getActivityType() != null ? s.getActivityType() : "STUDY",
                        s.getStatus()));
            }

            return AiProviderResponse.builder()
                    .message(sb.toString().trim())
                    .intent(AiIntent.GET_TODAY_PLAN)
                    .actionRequired(false)
                    .build();
        }

        // 2. Weekly plan
        if (lower.contains("week") || lower.contains("weekly") || lower.contains("full plan")) {
            List<AiContext.SessionSummary> weekSessions = context.getWeekPlan();
            if (weekSessions == null || weekSessions.isEmpty()) {
                return AiProviderResponse.builder()
                        .message("You do not have an active weekly study plan right now. You can create one from the Adaptive Study Planner tab.")
                        .intent(AiIntent.GET_WEEK_PLAN)
                        .actionRequired(false)
                        .build();
            }

            double totalHours = weekSessions.stream().mapToDouble(AiContext.SessionSummary::getPlannedHours).sum();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Your active weekly plan includes %d scheduled session(s) totaling %.1f planned hours across the week.\n\n",
                    weekSessions.size(), totalHours));

            // Group by date or display upcoming 5
            int count = 0;
            for (AiContext.SessionSummary s : weekSessions) {
                if (count++ < 6) {
                    sb.append(String.format("• **%s** on %s (%s to %s) — %.1f hrs (%s)\n",
                            s.getActivityLabel() != null ? s.getActivityLabel() : "Study Session",
                            s.getDate(),
                            s.getStartTime() != null ? s.getStartTime() : "",
                            s.getEndTime() != null ? s.getEndTime() : "",
                            s.getPlannedHours(),
                            s.getModuleCode() != null ? s.getModuleCode() : "General"));
                }
            }
            if (weekSessions.size() > 6) {
                sb.append(String.format("\n*...and %d more session(s) scheduled for later this week.*", weekSessions.size() - 6));
            }

            return AiProviderResponse.builder()
                    .message(sb.toString().trim())
                    .intent(AiIntent.GET_WEEK_PLAN)
                    .actionRequired(false)
                    .build();
        }

        // 3. Upcoming assessments / most urgent
        if (lower.contains("assessment") || lower.contains("quiz") || lower.contains("exam") || lower.contains("urgent")) {
            List<AiContext.AssessmentSummary> assessments = context.getUpcomingAssessments();
            if (assessments == null || assessments.isEmpty()) {
                return AiProviderResponse.builder()
                        .message("You have no upcoming assessments recorded in the next 30 days. Great job staying ahead!")
                        .intent(AiIntent.GET_UPCOMING_ASSESSMENTS)
                        .actionRequired(false)
                        .build();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("You have %d upcoming assessment(s):\n\n", assessments.size()));
            for (AiContext.AssessmentSummary a : assessments) {
                sb.append(String.format("• **%s** (%s) — Due: %s | Weight: %.0f%% | Status: %s\n",
                        a.getTitle(),
                        a.getModuleCode() != null ? a.getModuleCode() : "N/A",
                        a.getDueDateTime() != null ? a.getDueDateTime().replace("T", " ") : "No date",
                        a.getWeight() != null ? a.getWeight() : 0.0,
                        a.getStatus()));
            }

            AiContext.AssessmentSummary mostUrgent = assessments.get(0);
            sb.append(String.format("\n🎯 **Most Urgent:** %s (%s), due on %s.",
                    mostUrgent.getTitle(),
                    mostUrgent.getModuleCode(),
                    mostUrgent.getDueDateTime() != null ? mostUrgent.getDueDateTime().replace("T", " ") : "soon"));

            return AiProviderResponse.builder()
                    .message(sb.toString().trim())
                    .intent(AiIntent.GET_UPCOMING_ASSESSMENTS)
                    .actionRequired(false)
                    .build();
        }

        // 4. Specific module query (e.g. "DSA" or "IN2011")
        if (lower.contains("hours") || lower.contains("dsa") || lower.contains("in2011") || lower.contains("statistics")) {
            String targetCode = null;
            if (lower.contains("dsa")) targetCode = "DSA";
            else if (lower.contains("in2011")) targetCode = "IN2011";
            else if (lower.contains("statistics")) targetCode = "STAT";

            final String codeFilter = targetCode;
            List<AiContext.SessionSummary> matching = (context.getWeekPlan() != null)
                    ? context.getWeekPlan().stream()
                    .filter(s -> (codeFilter == null) ||
                            (s.getModuleCode() != null && s.getModuleCode().toUpperCase().contains(codeFilter)) ||
                            (s.getActivityLabel() != null && s.getActivityLabel().toUpperCase().contains(codeFilter)))
                    .toList()
                    : List.of();

            double moduleHours = matching.stream().mapToDouble(AiContext.SessionSummary::getPlannedHours).sum();

            if (codeFilter != null) {
                return AiProviderResponse.builder()
                        .message(String.format("You have %.1f hours allocated for %s across %d session(s) in your active weekly plan.",
                                moduleHours, codeFilter, matching.size()))
                        .intent(AiIntent.GENERAL_PLAN_QUESTION)
                        .actionRequired(false)
                        .build();
            }
        }

        // 5. Remaining study time / availability
        if (lower.contains("remaining") || lower.contains("available time") || lower.contains("free time")) {
            double totalAvail = (context.getAvailability() != null)
                    ? context.getAvailability().stream().mapToDouble(AiContext.AvailabilitySummary::getAvailableHours).sum()
                    : 0.0;
            double totalPlanned = (context.getWeekPlan() != null)
                    ? context.getWeekPlan().stream().mapToDouble(AiContext.SessionSummary::getPlannedHours).sum()
                    : 0.0;
            double remaining = Math.max(0.0, totalAvail - totalPlanned);

            return AiProviderResponse.builder()
                    .message(String.format("Your weekly declared availability is %.1f hours. You currently have %.1f hours planned, leaving **%.1f hours** of remaining unscheduled study time this week.",
                            totalAvail, totalPlanned, remaining))
                    .intent(AiIntent.GET_AVAILABLE_TIME)
                    .actionRequired(false)
                    .build();
        }

        // 6. Tasks
        if (lower.contains("task") || lower.contains("todo") || lower.contains("to-do")) {
            List<AiContext.TaskSummary> tasks = context.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                return AiProviderResponse.builder()
                        .message("You have no pending tasks on your Task Board.")
                        .intent(AiIntent.GET_TASKS)
                        .actionRequired(false)
                        .build();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("You have %d pending task(s):\n\n", tasks.size()));
            for (AiContext.TaskSummary t : tasks) {
                sb.append(String.format("• **%s** (%s) — Priority: %s | Remaining: %.1f hrs | Status: %s\n",
                        t.getTitle(),
                        t.getModuleCode() != null ? t.getModuleCode() : "General",
                        t.getPriority(),
                        t.getRemainingHours() != null ? t.getRemainingHours() : 0.0,
                        t.getStatus()));
            }

            return AiProviderResponse.builder()
                    .message(sb.toString().trim())
                    .intent(AiIntent.GET_TASKS)
                    .actionRequired(false)
                    .build();
        }

        // Default general response
        return AiProviderResponse.builder()
                .message("Hello! I am your AI Planning Assistant. You can ask me about your schedule for today or this week, upcoming assessments, remaining study hours, or tasks. For example:\n\n"
                        + "• \"What do I have to study today?\"\n"
                        + "• \"What are my upcoming assessments?\"\n"
                        + "• \"How many hours of DSA do I have this week?\"\n"
                        + "• \"How much study time do I have remaining this week?\"")
                .intent(AiIntent.GENERAL_PLAN_QUESTION)
                .actionRequired(false)
                .build();
    }
}
