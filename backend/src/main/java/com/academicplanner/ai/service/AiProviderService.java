package com.academicplanner.ai.service;

import com.academicplanner.ai.config.AiConfig;
import com.academicplanner.ai.model.AiContext;
import com.academicplanner.ai.model.AiIntent;
import com.academicplanner.ai.provider.AiProvider;
import com.academicplanner.ai.provider.AiProvider.AiProviderResponse;
import com.academicplanner.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Service that manages AI providers and coordinates generating responses.
 * Houses the centralized AI System Prompt and the grounded deterministic fallback engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final AiConfig aiConfig;
    private final List<AiProvider> providers;
    private final AiToolService aiToolService;

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
        return generateResponse(userMessage, context, null);
    }

    /**
     * Generates a response for the given user message and context.
     * Tries the configured LLM provider first; falls back to the grounded deterministic engine.
     */
    public AiProviderResponse generateResponse(String userMessage, AiContext context, User user) {
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
                AiProviderResponse providerResp = activeProvider.generateResponse(CENTRAL_SYSTEM_PROMPT, userMessage, context);
                if (providerResp.getIntent() == AiIntent.MODIFY_PLAN || providerResp.isActionRequired()) {
                    return enrichPlanModificationWithTool(providerResp, userMessage, context, user);
                }
                return providerResp;
            } catch (Exception e) {
                log.warn("[AiProviderService] Primary AI provider failed: {}. Falling back to deterministic planner assistant.", e.getMessage());
            }
        }

        // Fallback: Smart deterministic assistant grounded directly in the student's real PostgreSQL data
        return generateGroundedDeterministicResponse(userMessage, context, user);
    }

    /**
     * Responds accurately to student inquiries when no remote LLM API key is configured
     * or during provider outages, guaranteeing 100% truthful data retrieval without hallucination.
     */
    private AiProviderResponse generateGroundedDeterministicResponse(String message, AiContext context, User user) {
        String lower = message.toLowerCase(Locale.ROOT).trim();

        // ── Phase 4: Skip Today ──────────────────────────────────────────────
        if ((lower.contains("sick") || lower.contains("rest day") || lower.contains("can't study")
                || lower.contains("cannot study") || lower.contains("take a break today")
                || lower.contains("skip today") || lower.contains("skip all today")
                || lower.contains("emergency")) && lower.contains("today")) {
            return handleSkipToday(user);
        }

        // ── Phase 4: Burnout / Progress Analysis ─────────────────────────────
        if (lower.contains("burnout") || lower.contains("how am i doing")
                || lower.contains("my progress") || lower.contains("consistency")
                || lower.contains("how consistent") || lower.contains("completion rate")
                || lower.contains("am i on track") || lower.contains("pattern")) {
            return handleBurnoutCheck(user);
        }

        // ── Phase 4: Study Breakdown / Exam Strategy ─────────────────────────
        if ((lower.contains("how should i study") || lower.contains("study strategy")
                || lower.contains("break down") || lower.contains("breakdown")
                || lower.contains("study plan for") || lower.contains("prepare for"))
                && (lower.contains("exam") || lower.contains("assessment") || lower.contains("quiz")
                || lower.contains("assignment") || lower.contains("test"))) {
            return handleStudyBreakdown(lower, context);
        }

        // ── Phase 5/6: Plan Modification Requests ────────────────────────────
        if (isPlanModificationRequest(lower)) {
            return handlePlanModification(message, lower, context, user);
        }

        // ── Phase 4: Quick-Add Task ─────────────────────────────────────────
        if (lower.startsWith("add ") || lower.startsWith("create ") || lower.startsWith("new task")
                || lower.contains("add a task") || lower.contains("create a task")
                || lower.contains("remind me to") || lower.contains("add assignment")) {
            return handleQuickAddTask(message, user);
        }

        // ── Phase 2: Mark Activity Completed ────────────────────────────────
        if (lower.contains("done") || lower.contains("finished") || lower.contains("completed")
                || lower.contains("mark") || lower.contains("i did") || lower.contains("i've done")) {
            return handleMarkCompleted(message, lower, context, user);
        }

        // ── Phase 8: Adaptive Replanning — Missed Sessions ─────────────────
        if (lower.contains("missed") || lower.contains("couldn't study") || lower.contains("could not study")
                || lower.contains("didn't study") || lower.contains("unable to study")) {
            return handleAdaptiveMissedSession(message, lower, context, user);
        }

        // ── Phase 8: Adaptive Replanning — Availability Constraints ─────────
        if ((lower.contains("only have") || lower.contains("only got")) && (lower.contains("hour") || lower.contains("hr") || lower.contains("tonight") || lower.contains("today"))) {
            return handleAdaptiveAvailabilityLimit(lower, user);
        }

        if ((lower.contains("cannot study after") || lower.contains("can't study after") || lower.contains("no study after") || lower.contains("past 8") || lower.contains("after 8"))
                && (lower.contains("pm") || lower.contains(":00") || lower.contains("8") || lower.contains("9") || lower.contains("10"))) {
            return handleAdaptiveCutoffTime(lower, user);
        }

        // ── Phase 9: Planning Intelligence — Explanations & Insights ────────
        if (lower.contains("why") && (lower.contains("more time") || lower.contains("more hours") || lower.contains("so much time")
                || lower.contains("so many hours") || lower.contains("given more") || lower.contains("allocated"))) {
            return handleExplainAllocation(message, lower, context);
        }

        if ((lower.contains("which day") || lower.contains("what day") || lower.contains("busiest day") || lower.contains("heaviest day"))
                && (lower.contains("study time") || lower.contains("most") || lower.contains("hours") || lower.contains("workload") || lower.contains("plan"))) {
            return handleHeaviestStudyDays(context);
        }

        if (lower.contains("am i behind") || lower.contains("behind on my") || lower.contains("falling behind") || lower.contains("am i on track")) {
            return handleBehindScheduleAnalysis(context);
        }

        // ── Replan Summary / General Replanning Guidance ────────────────────
        if (lower.contains("replan") || lower.contains("reschedule") || lower.contains("regenerate plan")
                || lower.contains("update my plan") || lower.contains("adjust my schedule")
                || lower.contains("behind schedule")) {
            return handleReplanSummary(context);
        }

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
                        ? s.getStartTime() + " – " + s.getEndTime()
                        : String.format("%.1f hrs", s.getPlannedHours());
                String statusEmoji = "COMPLETED".equals(s.getStatus()) ? "✅" :
                        "SKIPPED".equals(s.getStatus()) ? "⏭️" : "📖";
                sb.append(String.format("• %s **%s** (%s): %s [%s] — %s\n",
                        statusEmoji,
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
            long completedCount = weekSessions.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Your active weekly plan includes **%d scheduled session(s)** totaling **%.1f planned hours** (%d completed so far).\n\n",
                    weekSessions.size(), totalHours, completedCount));

            int count = 0;
            for (AiContext.SessionSummary s : weekSessions) {
                if (count++ < 7) {
                    String statusEmoji = "COMPLETED".equals(s.getStatus()) ? "✅" :
                            "SKIPPED".equals(s.getStatus()) ? "⏭️" : "📖";
                    sb.append(String.format("• %s **%s** on %s (%s to %s) — %.1f hrs (%s)\n",
                            statusEmoji,
                            s.getActivityLabel() != null ? s.getActivityLabel() : "Study Session",
                            s.getDate(),
                            s.getStartTime() != null ? s.getStartTime() : "–",
                            s.getEndTime() != null ? s.getEndTime() : "–",
                            s.getPlannedHours(),
                            s.getModuleCode() != null ? s.getModuleCode() : "General"));
                }
            }
            if (weekSessions.size() > 7) {
                sb.append(String.format("\n*...and %d more session(s) scheduled for later this week.*", weekSessions.size() - 7));
            }

            return AiProviderResponse.builder()
                    .message(sb.toString().trim())
                    .intent(AiIntent.GET_WEEK_PLAN)
                    .actionRequired(false)
                    .build();
        }

        // 2.5 Workload Prioritisation Advice
        if (lower.contains("focus") || lower.contains("prioritize") || lower.contains("prioritise") || lower.contains("important")) {
            List<AiContext.AssessmentSummary> assessments = context.getUpcomingAssessments();
            List<AiContext.TaskSummary> tasks = context.getTasks();

            if ((assessments == null || assessments.isEmpty()) && (tasks == null || tasks.isEmpty())) {
                return AiProviderResponse.builder()
                        .message("You have no upcoming assessments or high-priority tasks right now. I recommend balancing your study time evenly across your active modules.")
                        .intent(AiIntent.GENERAL_PLAN_QUESTION)
                        .actionRequired(false)
                        .build();
            }

            StringBuilder sb = new StringBuilder("Based on your upcoming deadlines and task weights, here is what you should focus on most:\n\n");
            
            if (assessments != null && !assessments.isEmpty()) {
                AiContext.AssessmentSummary urgentAss = assessments.get(0);
                sb.append(String.format("🚨 **Top Priority Assessment:** **%s** (%s)\n", urgentAss.getTitle(), urgentAss.getModuleCode() != null ? urgentAss.getModuleCode() : "General"));
                sb.append(String.format("   • Due: %s\n", urgentAss.getDueDateTime() != null ? urgentAss.getDueDateTime().replace("T", " ") : "soon"));
                if (urgentAss.getWeight() != null && urgentAss.getWeight() > 0) {
                    sb.append(String.format("   • Weight: %.0f%% of your grade\n", urgentAss.getWeight()));
                }
                sb.append("   *Recommendation: Ensure your daily plan allocates the largest block of study time to this module.* \n\n");
            }

            if (tasks != null) {
                List<AiContext.TaskSummary> highPriorityTasks = tasks.stream()
                        .filter(t -> "HIGH".equals(t.getPriority()) && !"COMPLETED".equals(t.getStatus()))
                        .toList();
                
                if (!highPriorityTasks.isEmpty()) {
                    sb.append("🔴 **High Priority Tasks:**\n");
                    for (AiContext.TaskSummary t : highPriorityTasks) {
                        sb.append(String.format("   • **%s** (%s) - %.1f hrs remaining\n", 
                                t.getTitle(), t.getModuleCode() != null ? t.getModuleCode() : "General", t.getRemainingHours() != null ? t.getRemainingHours() : 0.0));
                    }
                }
            }

            return AiProviderResponse.builder()
                    .message(sb.toString().trim())
                    .intent(AiIntent.GENERAL_PLAN_QUESTION)
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
            sb.append(String.format("You have **%d upcoming assessment(s)**:\n\n", assessments.size()));
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

        // 4. Specific module query
        if (lower.contains("hours") || lower.contains("module") || lower.contains("how much time")) {
            // Try to extract a module code from the message
            String targetCode = extractModuleHint(lower, context);

            List<AiContext.SessionSummary> matching = (context.getWeekPlan() != null)
                    ? context.getWeekPlan().stream()
                    .filter(s -> (targetCode == null) ||
                            (s.getModuleCode() != null && s.getModuleCode().toUpperCase().contains(targetCode)) ||
                            (s.getModuleName() != null && s.getModuleName().toUpperCase().contains(targetCode)) ||
                            (s.getActivityLabel() != null && s.getActivityLabel().toUpperCase().contains(targetCode)))
                    .toList()
                    : List.of();

            double moduleHours = matching.stream().mapToDouble(AiContext.SessionSummary::getPlannedHours).sum();

            if (targetCode != null && !matching.isEmpty()) {
                return AiProviderResponse.builder()
                        .message(String.format("You have **%.1f hours** allocated for **%s** across **%d session(s)** in your active weekly plan.",
                                moduleHours, targetCode, matching.size()))
                        .intent(AiIntent.GENERAL_PLAN_QUESTION)
                        .actionRequired(false)
                        .build();
            } else if (!matching.isEmpty()) {
                return AiProviderResponse.builder()
                        .message(String.format("Your weekly plan has **%.1f hours** across **%d total session(s)**.", moduleHours, matching.size()))
                        .intent(AiIntent.GENERAL_PLAN_QUESTION)
                        .actionRequired(false)
                        .build();
            }
        }

        // 5. Remaining study time / availability
        if (lower.contains("remaining") || lower.contains("available time") || lower.contains("free time") || lower.contains("how much")) {
            double totalAvail = (context.getAvailability() != null)
                    ? context.getAvailability().stream().mapToDouble(AiContext.AvailabilitySummary::getAvailableHours).sum()
                    : 0.0;
            double totalPlanned = (context.getWeekPlan() != null)
                    ? context.getWeekPlan().stream()
                    .filter(s -> !"COMPLETED".equals(s.getStatus()) && !"SKIPPED".equals(s.getStatus()))
                    .mapToDouble(AiContext.SessionSummary::getPlannedHours).sum()
                    : 0.0;
            double remaining = Math.max(0.0, totalAvail - totalPlanned);

            return AiProviderResponse.builder()
                    .message(String.format("Your weekly declared availability is **%.1f hours**. You currently have **%.1f hours** of uncompleted sessions planned, leaving **%.1f hours** of remaining unscheduled study time this week.",
                            totalAvail, totalPlanned, remaining))
                    .intent(AiIntent.GET_AVAILABLE_TIME)
                    .actionRequired(false)
                    .build();
        }

        // 6. Tasks
        if (lower.contains("task") || lower.contains("todo") || lower.contains("to-do") || lower.contains("pending")) {
            List<AiContext.TaskSummary> tasks = context.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                return AiProviderResponse.builder()
                        .message("You have no pending tasks on your Task Board. Great work staying on top of everything!")
                        .intent(AiIntent.GET_TASKS)
                        .actionRequired(false)
                        .build();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("You have **%d pending task(s)**:\n\n", tasks.size()));
            for (AiContext.TaskSummary t : tasks) {
                String priorityEmoji = "HIGH".equals(t.getPriority()) ? "🔴" :
                        "MEDIUM".equals(t.getPriority()) ? "🟡" : "🟢";
                sb.append(String.format("• %s **%s** (%s) — Priority: %s | Remaining: %.1f hrs | Status: %s\n",
                        priorityEmoji,
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

        // 7. Help / capabilities
        if (lower.contains("help") || lower.contains("what can you") || lower.contains("capabilities")) {
            return AiProviderResponse.builder()
                    .message("I'm your **AI Study Planning Assistant**! Here's what I can help you with:\n\n"
                            + "📅 **Schedule Queries**\n"
                            + "• \"What do I have to study today?\"\n"
                            + "• \"Show me my full week plan\"\n\n"
                            + "🎯 **Assessments & Tasks**\n"
                            + "• \"What are my upcoming assessments?\"\n"
                            + "• \"Show my pending tasks\"\n\n"
                            + "⏱️ **Time & Availability**\n"
                            + "• \"How much study time do I have remaining this week?\"\n"
                            + "• \"How many hours of [Module] do I have?\"\n\n"
                            + "✅ **Mark Sessions Done**\n"
                            + "• \"I finished my DSA session\"\n"
                            + "• \"Mark Statistics as done\"\n\n"
                            + "🔄 **Replanning Guidance**\n"
                            + "• \"I'm behind schedule, what should I do?\"\n"
                            + "• \"I missed my session today\"")
                    .intent(AiIntent.GENERAL_PLAN_QUESTION)
                    .actionRequired(false)
                    .build();
        }

        // Default general response
        return AiProviderResponse.builder()
                .message("Hello! I am your **AI Planning Assistant**. You can ask me about your schedule for today or this week, upcoming assessments, remaining study hours, pending tasks, or even mark sessions as done.\n\n"
                        + "Try:\n"
                        + "• \"What do I have to study today?\"\n"
                        + "• \"What are my upcoming assessments?\"\n"
                        + "• \"I finished my DSA session\"\n"
                        + "• \"How much study time do I have remaining?\"\n"
                        + "• \"I'm behind schedule — what should I do?\"\n\n"
                        + "Type **help** for a full list of my capabilities.")
                .intent(AiIntent.GENERAL_PLAN_QUESTION)
                .actionRequired(false)
                .build();
    }

    // ── Phase 2 Handlers ────────────────────────────────────────────────────

    /**
     * Handles the MARK_ACTIVITY_COMPLETED intent.
     * Attempts to fuzzy-match today's sessions against words in the user's message.
     */
    private AiProviderResponse handleMarkCompleted(String originalMessage, String lower, AiContext context, User user) {
        // Try to extract a meaningful fragment from the message
        String fragment = extractMarkFragment(lower, context);

        if (fragment == null || fragment.isBlank()) {
            // List today's pending sessions and ask for clarification
            List<AiContext.SessionSummary> pending = (context.getTodayPlan() != null)
                    ? context.getTodayPlan().stream()
                    .filter(s -> !"COMPLETED".equals(s.getStatus()) && !"SKIPPED".equals(s.getStatus()))
                    .toList()
                    : List.of();

            if (pending.isEmpty()) {
                return AiProviderResponse.builder()
                        .message("You have no remaining sessions to mark as done today. All sessions are either completed or skipped. 🎉")
                        .intent(AiIntent.MARK_ACTIVITY_COMPLETED)
                        .actionRequired(false)
                        .build();
            }

            StringBuilder sb = new StringBuilder("Which session would you like to mark as done? Here are your remaining sessions today:\n\n");
            for (AiContext.SessionSummary s : pending) {
                sb.append(String.format("• **%s** (%s)\n",
                        s.getActivityLabel() != null ? s.getActivityLabel() : "Study Session",
                        s.getModuleCode() != null ? s.getModuleCode() : "General"));
            }
            sb.append("\nJust say something like: *\"I finished [session name]\"*");

            return AiProviderResponse.builder()
                    .message(sb.toString())
                    .intent(AiIntent.MARK_ACTIVITY_COMPLETED)
                    .actionRequired(false)
                    .build();
        }

        // Attempt to mark the matched session
        String markedTitle = aiToolService.markByTitleFragment(fragment, user);
        if (markedTitle != null) {
            // Check if there are remaining sessions to encourage progress
            List<AiContext.SessionSummary> remaining = (context.getTodayPlan() != null)
                    ? context.getTodayPlan().stream()
                    .filter(s -> !"COMPLETED".equals(s.getStatus()) && !"SKIPPED".equals(s.getStatus()))
                    .toList()
                    : List.of();
            long remainingCount = remaining.size();

            String encouragement = remainingCount == 0
                    ? "\n\n🎉 **You've completed all your sessions for today! Excellent work!**"
                    : String.format("\n\n📖 You still have **%d session(s)** left today. Keep going!", remainingCount);

            return AiProviderResponse.builder()
                    .message(String.format("✅ Great work! I've marked **\"%s\"** as completed.%s", markedTitle, encouragement))
                    .intent(AiIntent.MARK_ACTIVITY_COMPLETED)
                    .actionRequired(false)
                    .build();
        } else {
            return AiProviderResponse.builder()
                    .message(String.format("I couldn't find a session matching *\"%s\"* in today's schedule. Please check the session name or use the dashboard to mark it directly.", fragment))
                    .intent(AiIntent.MARK_ACTIVITY_COMPLETED)
                    .actionRequired(false)
                    .build();
        }
    }

    /**
     * Handles replan guidance based on the student's current plan status.
     * Provides actionable advice based on real data without invoking the OR-Tools engine directly.
     */
    private AiProviderResponse handleReplanSummary(AiContext context) {
        List<AiContext.SessionSummary> weekPlan = context.getWeekPlan();

        if (weekPlan == null || weekPlan.isEmpty()) {
            return AiProviderResponse.builder()
                    .message("You don't have an active weekly plan. Head to the **Adaptive Study Planner** tab to generate one — it will use your availability, assessments, and tasks to create an optimised schedule.")
                    .intent(AiIntent.REPLAN)
                    .actionRequired(false)
                    .build();
        }

        long skipped = weekPlan.stream().filter(s -> "SKIPPED".equals(s.getStatus())).count();
        long pending = weekPlan.stream().filter(s -> "PLANNED".equals(s.getStatus())).count();
        long completed = weekPlan.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        double pendingHours = weekPlan.stream()
                .filter(s -> "PLANNED".equals(s.getStatus()))
                .mapToDouble(AiContext.SessionSummary::getPlannedHours).sum();

        // Upcoming deadlines from assessments
        List<AiContext.AssessmentSummary> urgentAssessments = (context.getUpcomingAssessments() != null)
                ? context.getUpcomingAssessments().stream().limit(3).toList()
                : List.of();

        StringBuilder sb = new StringBuilder("📊 **Current Plan Status:**\n\n");
        sb.append(String.format("• ✅ Completed sessions: **%d**\n", completed));
        sb.append(String.format("• 📖 Pending sessions: **%d** (%.1f hrs total)\n", pending, pendingHours));
        if (skipped > 0) {
            sb.append(String.format("• ⏭️ Skipped sessions: **%d**\n", skipped));
        }

        if (!urgentAssessments.isEmpty()) {
            sb.append("\n🎯 **Upcoming Deadlines to Keep in Mind:**\n");
            for (AiContext.AssessmentSummary a : urgentAssessments) {
                sb.append(String.format("• **%s** (%s) due %s\n",
                        a.getTitle(),
                        a.getModuleCode() != null ? a.getModuleCode() : "N/A",
                        a.getDueDateTime() != null ? a.getDueDateTime().replace("T", " ") : "soon"));
            }
        }

        sb.append("\n💡 **Recommendation:**\n");
        if (skipped > 0 || pending > 4) {
            sb.append("You have sessions that need attention. To generate a fresh optimised schedule, go to the **Adaptive Study Planner** tab and click **Generate New Plan**. ");
            sb.append("The OR-Tools engine will redistribute your remaining workload across your available time, prioritising urgent assessments.");
        } else {
            sb.append("Your plan looks manageable. Continue working through your remaining sessions. ");
            sb.append("If your schedule has changed significantly, use the **Adaptive Study Planner** to regenerate an updated plan.");
        }

        return AiProviderResponse.builder()
                .message(sb.toString().trim())
                .intent(AiIntent.REPLAN)
                .actionRequired(false)
                .build();
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    /**
     * Attempts to extract a module code or title hint from the user's message.
     */
    private String extractModuleHint(String lower, AiContext context) {
        if (context.getWeekPlan() == null) return null;
        for (AiContext.SessionSummary s : context.getWeekPlan()) {
            if (s.getModuleCode() != null && lower.contains(s.getModuleCode().toLowerCase())) {
                return s.getModuleCode().toUpperCase();
            }
            if (s.getModuleName() != null && lower.contains(s.getModuleName().toLowerCase())) {
                return s.getModuleName().toUpperCase();
            }
        }
        return null;
    }

    /**
     * Extracts a title/module fragment to use for fuzzy-matching in mark-completed intent.
     */
    private String extractMarkFragment(String lower, AiContext context) {
        // Try to match against known module codes in today's plan first
        if (context.getTodayPlan() != null) {
            for (AiContext.SessionSummary s : context.getTodayPlan()) {
                if (s.getModuleCode() != null && lower.contains(s.getModuleCode().toLowerCase())) {
                    return s.getModuleCode();
                }
                if (s.getActivityLabel() != null && lower.contains(s.getActivityLabel().toLowerCase())) {
                    return s.getActivityLabel();
                }
            }
        }
        // Strip common command words to get the subject
        String cleaned = lower
                .replace("i've done", "").replace("i've finished", "").replace("i finished", "")
                .replace("i did", "").replace("mark", "").replace("done", "").replace("finished", "")
                .replace("completed", "").replace("complete", "").replace("as done", "")
                .replace("the", "").replace("my", "").replace("session", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    // ── Phase 4 Handler Methods ───────────────────────────────────────────────

    private AiProviderResponse handleSkipToday(User user) {
        int skipped = aiToolService.skipAllTodaySessions(user);
        String msg = skipped == 0
            ? "✅ You have no pending sessions left for today — nothing to skip!"
            : String.format(
                "⏭️ Done! I've marked **%d session(s)** as skipped for today. Rest up! 🛌\n\n" +
                "💡 *Tip:* Visit the **Adaptive Planner** to regenerate your schedule around these missed sessions.",
                skipped);
        return AiProviderResponse.builder()
                .message(msg).intent(AiIntent.SKIP_TODAY).actionRequired(false).build();
    }

    private AiProviderResponse handleBurnoutCheck(User user) {
        com.academicplanner.ai.tool.BurnoutDetectorTool.BurnoutReport report = aiToolService.analyseBurnout(user);
        String msg = "## 📊 Your Study Progress Report (Last 14 Days)\n\n"
                + report.getSummary() + "\n\n---\n## 💡 Recommendation\n\n"
                + report.getRecommendation();
        return AiProviderResponse.builder()
                .message(msg).intent(AiIntent.BURNOUT_CHECK).actionRequired(false).build();
    }

    private AiProviderResponse handleStudyBreakdown(String lower, AiContext context) {
        java.util.List<AiContext.AssessmentSummary> assessments = context.getUpcomingAssessments();
        AiContext.AssessmentSummary target = null;
        if (assessments != null) {
            for (AiContext.AssessmentSummary a : assessments) {
                if ((a.getTitle() != null && lower.contains(a.getTitle().toLowerCase()))
                        || (a.getModuleCode() != null && lower.contains(a.getModuleCode().toLowerCase()))
                        || (a.getModuleName() != null && lower.contains(a.getModuleName().toLowerCase()))) {
                    target = a; break;
                }
            }
            if (target == null && !assessments.isEmpty()) target = assessments.get(0);
        }
        if (target == null) {
            return AiProviderResponse.builder()
                    .message("I couldn't find an upcoming assessment. Please add your assessments in the **Assessments** section first.")
                    .intent(AiIntent.STUDY_BREAKDOWN).actionRequired(false).build();
        }
        String due = target.getDueDateTime() != null ? target.getDueDateTime().replace("T", " at ") : "soon";
        String weight = target.getWeight() != null && target.getWeight() > 0
                ? String.format(" (%.0f%% of your grade)", target.getWeight()) : "";
        String msg = String.format(
            "## 📚 Study Breakdown: **%s**\n**Module:** %s | **Due:** %s%s\n\n" +
            "• **Day 1-2 — Review & Understand** 📖\n  Revisit lecture notes. Summarise each topic in your own words.\n\n" +
            "• **Day 3-4 — Active Practice** ✏️\n  Attempt past papers or practice questions. Focus on weak areas.\n\n" +
            "• **Day 5 — Gap Analysis** 🔍\n  Review answers. Re-read specific topics where you struggled.\n\n" +
            "• **Day 6 — Consolidation** 🧠\n  Create a one-page summary or mind map. Teach the concepts aloud.\n\n" +
            "• **Day 7 — Light Review & Rest** 😴\n  Light review only. Get a full night of sleep.\n\n" +
            "💡 *Add these as tasks in the **Task Board** so the Adaptive Planner can schedule them automatically.*",
            target.getTitle(), target.getModuleCode() != null ? target.getModuleCode() : "General", due, weight);
        return AiProviderResponse.builder()
                .message(msg).intent(AiIntent.STUDY_BREAKDOWN).actionRequired(false).build();
    }

    private AiProviderResponse handleQuickAddTask(String originalMessage, User user) {
        com.academicplanner.dto.task.TaskResponse created = aiToolService.quickAddTask(originalMessage, user);
        if (created == null) {
            return AiProviderResponse.builder()
                    .message("⚠️ I couldn't parse that task. Try:\n\n" +
                             "• *\"Add a DSA assignment due next Friday, 3 hours\"*\n" +
                             "• *\"Create a task for CS101 essay due tomorrow, 2 hours, high priority\"*")
                    .intent(AiIntent.QUICK_ADD_TASK).actionRequired(false).build();
        }
        String due = created.dueDateTime() != null
                ? " — Due: **" + created.dueDateTime().toString().replace("T", " ").substring(0, 16) + "**" : "";
        String mod = created.moduleName() != null ? " (" + created.moduleCode() + ")" : "";
        String hrs = created.estimatedHours() != null
                ? String.format(", **%.1f hrs** estimated", created.estimatedHours()) : "";
        String msg = String.format(
            "✅ Task created!\n\n📋 **%s**%s%s%s\nPriority: **%s**\n\n" +
            "View it in the **Task Board**. It will be included in your next plan generation! 🚀",
            created.title(), mod, due, hrs,
            created.priority() != null ? created.priority().name() : "MEDIUM");
        return AiProviderResponse.builder()
                .message(msg).intent(AiIntent.QUICK_ADD_TASK).actionRequired(false).build();
    }

    // ── Phase 5 & 6 Helpers ──────────────────────────────────────────────────

    private boolean isPlanModificationRequest(String lower) {
        if (lower.contains("add a task") || lower.contains("create a task") || lower.contains("add assignment") || lower.contains("new task")) {
            return false;
        }
        boolean hasActionWord = lower.contains("add") || lower.contains("increase") || lower.contains("reduce")
                || lower.contains("decrease") || lower.contains("more time") || lower.contains("less time")
                || lower.contains("give") || lower.contains("extend") || lower.contains("cut")
                || lower.contains("enough time") || lower.contains("another hour");
        boolean hasTimeWord = lower.contains("hour") || lower.contains("hr") || lower.contains("minute")
                || lower.contains("min") || lower.contains("time") || lower.contains("session");
        return hasActionWord && hasTimeWord;
    }

    private AiProviderResponse handlePlanModification(String originalMessage, String lower, AiContext context, User user) {
        if (user == null) {
            return AiProviderResponse.builder()
                    .message("User context is required to modify study plans.")
                    .intent(AiIntent.MODIFY_PLAN)
                    .actionRequired(false)
                    .build();
        }

        com.academicplanner.ai.model.AiAction action =
                (lower.contains("reduce") || lower.contains("decrease") || lower.contains("less") || lower.contains("cut"))
                        ? com.academicplanner.ai.model.AiAction.DECREASE_ACTIVITY_TIME
                        : com.academicplanner.ai.model.AiAction.INCREASE_ACTIVITY_TIME;

        int minutes = parseMinutesFromMessage(lower);
        String targetActivity = extractTargetActivityOrModule(lower, context);

        com.academicplanner.ai.dto.PlanModificationRequest req = com.academicplanner.ai.dto.PlanModificationRequest.builder()
                .action(action)
                .moduleCode(targetActivity)
                .activityName(targetActivity)
                .additionalMinutes(minutes)
                .build();

        com.academicplanner.ai.dto.PlanModificationResult result = aiToolService.requestPlanModification(req, user);

        boolean hasProposal = result.getProposalId() != null;
        String formattedMessage;
        if ("FEASIBLE".equals(result.getStatus())) {
            formattedMessage = String.format("⚡ **Feasible Plan Change Proposed**\n\n%s\n\nClick **Apply Change** below to update your timetable.",
                    result.getReason());
        } else if ("REQUIRES_CONFIRMATION".equals(result.getStatus())) {
            formattedMessage = String.format("⚡ **Adjustment Proposal**\n\n%s\n\nClick **Apply Change** below to confirm these schedule adjustments.",
                    result.getReason());
        } else if ("PARTIALLY_FEASIBLE".equals(result.getStatus())) {
            formattedMessage = String.format("⚡ **Partial Allocation Proposed**\n\n%s\n\nClick **Apply Change** to apply this partial time increase.",
                    result.getReason());
        } else {
            formattedMessage = String.format("⚠️ **Unable to Modify Schedule**\n\n%s", result.getReason());
        }

        return AiProviderResponse.builder()
                .message(formattedMessage)
                .intent(AiIntent.MODIFY_PLAN)
                .actionRequired(hasProposal)
                .action(hasProposal ? result : null)
                .planPreview(result.getAffectedActivities())
                .build();
    }

    private int parseMinutesFromMessage(String lower) {
        java.util.regex.Matcher hourMatcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?|h)\\b").matcher(lower);
        if (hourMatcher.find()) {
            double hrs = Double.parseDouble(hourMatcher.group(1));
            return (int) Math.round(hrs * 60);
        }
        java.util.regex.Matcher minMatcher = java.util.regex.Pattern.compile("(\\d+)\\s*(?:minutes?|mins?|m)\\b").matcher(lower);
        if (minMatcher.find()) {
            return Integer.parseInt(minMatcher.group(1));
        }
        if (lower.contains("another hour") || lower.contains("an hour") || lower.contains("one hour")) {
            return 60;
        }
        if (lower.contains("two hours") || lower.contains("2 hours")) {
            return 120;
        }
        if (lower.contains("three hours") || lower.contains("3 hours")) {
            return 180;
        }
        if (lower.contains("half an hour") || lower.contains("30 min")) {
            return 30;
        }
        return 60;
    }

    private String extractTargetActivityOrModule(String lower, AiContext context) {
        if (context.getTodayPlan() != null) {
            for (AiContext.SessionSummary s : context.getTodayPlan()) {
                if (s.getModuleCode() != null && lower.contains(s.getModuleCode().toLowerCase())) {
                    return s.getModuleCode();
                }
                if (s.getModuleName() != null && lower.contains(s.getModuleName().toLowerCase())) {
                    return s.getModuleName();
                }
                if (s.getActivityLabel() != null && lower.contains(s.getActivityLabel().toLowerCase())) {
                    return s.getActivityLabel();
                }
            }
        }
        if (context.getWeekPlan() != null) {
            for (AiContext.SessionSummary s : context.getWeekPlan()) {
                if (s.getModuleCode() != null && lower.contains(s.getModuleCode().toLowerCase())) {
                    return s.getModuleCode();
                }
                if (s.getModuleName() != null && lower.contains(s.getModuleName().toLowerCase())) {
                    return s.getModuleName();
                }
                if (s.getActivityLabel() != null && lower.contains(s.getActivityLabel().toLowerCase())) {
                    return s.getActivityLabel();
                }
            }
        }
        if (context.getUpcomingAssessments() != null) {
            for (AiContext.AssessmentSummary a : context.getUpcomingAssessments()) {
                if (a.getModuleCode() != null && lower.contains(a.getModuleCode().toLowerCase())) {
                    return a.getModuleCode();
                }
                if (a.getTitle() != null && lower.contains(a.getTitle().toLowerCase())) {
                    return a.getTitle();
                }
            }
        }
        java.util.regex.Matcher codeMatcher = java.util.regex.Pattern.compile("\\b([a-zA-Z]{2,4}\\d{3,4})\\b").matcher(lower);
        if (codeMatcher.find()) {
            return codeMatcher.group(1).toUpperCase();
        }

        // ── Phase 10: Multi-Turn Conversation Memory ────────────────────────
        // If no explicit module code was in the current message, infer from previous turns
        if (context.getRecentChatMessages() != null && !context.getRecentChatMessages().isEmpty()) {
            for (com.academicplanner.entity.AiChatHistory past : context.getRecentChatMessages()) {
                String pastMsg = past.getMessage();
                if (pastMsg == null) continue;
                String pastLower = pastMsg.toLowerCase();

                if (context.getTodayPlan() != null) {
                    for (AiContext.SessionSummary s : context.getTodayPlan()) {
                        if (s.getModuleCode() != null && pastLower.contains(s.getModuleCode().toLowerCase())) {
                            return s.getModuleCode();
                        }
                    }
                }
                if (context.getWeekPlan() != null) {
                    for (AiContext.SessionSummary s : context.getWeekPlan()) {
                        if (s.getModuleCode() != null && pastLower.contains(s.getModuleCode().toLowerCase())) {
                            return s.getModuleCode();
                        }
                    }
                }
                java.util.regex.Matcher pm = java.util.regex.Pattern.compile("\\b([a-zA-Z]{2,4}\\d{3,4})\\b").matcher(pastMsg);
                if (pm.find()) {
                    return pm.group(1).toUpperCase();
                }
            }
        }

        return null;
    }

    private AiProviderResponse enrichPlanModificationWithTool(AiProviderResponse providerResp, String userMessage, AiContext context, User user) {
        if (user == null) {
            return providerResp;
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        AiProviderResponse toolResp = handlePlanModification(userMessage, lower, context, user);
        if (toolResp.isActionRequired() || toolResp.getAction() != null) {
            return toolResp;
        }
        return toolResp;
    }

    // ── Phase 8 Helpers ──────────────────────────────────────────────────────

    private AiProviderResponse handleAdaptiveMissedSession(String originalMessage, String lower, AiContext context, User user) {
        if (user == null) {
            return AiProviderResponse.builder()
                    .message("User context is required to adaptively reschedule sessions.")
                    .intent(AiIntent.REPLAN)
                    .actionRequired(false)
                    .build();
        }

        String target = extractTargetActivityOrModule(lower, context);
        if (target == null && context.getTodayPlan() != null && !context.getTodayPlan().isEmpty()) {
            target = context.getTodayPlan().stream()
                    .filter(s -> !"COMPLETED".equals(s.getStatus()) && !"SKIPPED".equals(s.getStatus()))
                    .findFirst()
                    .map(AiContext.SessionSummary::getActivityLabel)
                    .orElse(context.getTodayPlan().get(0).getActivityLabel());
        }

        if (target == null) {
            return AiProviderResponse.builder()
                    .message("Which session did you miss? (e.g. *\"I missed my DSA session today\"*)")
                    .intent(AiIntent.REPLAN)
                    .actionRequired(false)
                    .build();
        }

        com.academicplanner.ai.dto.PlanModificationResult result = aiToolService.createMissedSessionProposal(target, user);
        return AiProviderResponse.builder()
                .message("🔄 **Adaptive Replan Proposal**\n\n" + result.getReason())
                .intent(AiIntent.REPLAN)
                .actionRequired(result.getProposalId() != null)
                .action(result.getProposalId() != null ? result : null)
                .planPreview(result.getAffectedActivities())
                .build();
    }

    private AiProviderResponse handleAdaptiveAvailabilityLimit(String lower, User user) {
        if (user == null) {
            return AiProviderResponse.builder()
                    .message("User context is required to update availability.")
                    .intent(AiIntent.REPLAN)
                    .actionRequired(false)
                    .build();
        }

        double hours = 2.0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?|h)").matcher(lower);
        if (m.find()) {
            hours = Double.parseDouble(m.group(1));
        } else if (lower.contains("one hour") || lower.contains("1 hour")) {
            hours = 1.0;
        } else if (lower.contains("three hours") || lower.contains("3 hours")) {
            hours = 3.0;
        }

        com.academicplanner.ai.dto.PlanModificationResult result = aiToolService.createAvailabilityChangeProposal(hours, user);
        return AiProviderResponse.builder()
                .message("⚡ **Availability Limit Adjustment**\n\n" + result.getReason())
                .intent(AiIntent.REPLAN)
                .actionRequired(result.getProposalId() != null)
                .action(result.getProposalId() != null ? result : null)
                .planPreview(result.getAffectedActivities())
                .build();
    }

    private AiProviderResponse handleAdaptiveCutoffTime(String lower, User user) {
        if (user == null) {
            return AiProviderResponse.builder()
                    .message("User context is required to update cutoff time.")
                    .intent(AiIntent.REPLAN)
                    .actionRequired(false)
                    .build();
        }

        java.time.LocalTime cutoff = java.time.LocalTime.of(20, 0); // default 8 PM
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matcher(lower);
        while (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3);
            if ("pm".equalsIgnoreCase(ampm) && hour < 12) hour += 12;
            if (hour >= 1 && hour <= 24) {
                cutoff = java.time.LocalTime.of(hour % 24, min);
                break;
            }
        }

        com.academicplanner.ai.dto.PlanModificationResult result = aiToolService.createTimeCutoffProposal(cutoff, user);
        return AiProviderResponse.builder()
                .message("🌙 **Evening Study Cutoff**\n\n" + result.getReason())
                .intent(AiIntent.REPLAN)
                .actionRequired(result.getProposalId() != null)
                .action(result.getProposalId() != null ? result : null)
                .planPreview(result.getAffectedActivities())
                .build();
    }

    // ── Phase 9 Helpers: Planning Intelligence & Explanations ─────────────────

    private AiProviderResponse handleExplainAllocation(String originalMessage, String lower, AiContext context) {
        String target = extractTargetActivityOrModule(lower, context);
        if (target == null && context.getWeekPlan() != null && !context.getWeekPlan().isEmpty()) {
            target = context.getWeekPlan().get(0).getModuleCode();
        }

        if (target == null) {
            return AiProviderResponse.builder()
                    .message("Which module or subject would you like me to explain the schedule allocation for?")
                    .intent(AiIntent.GENERAL_PLAN_QUESTION)
                    .actionRequired(false)
                    .build();
        }

        String finalTarget = target;
        List<AiContext.AssessmentSummary> relatedAssessments = (context.getUpcomingAssessments() != null)
                ? context.getUpcomingAssessments().stream()
                .filter(a -> (a.getModuleCode() != null && a.getModuleCode().equalsIgnoreCase(finalTarget))
                        || (a.getTitle() != null && a.getTitle().toLowerCase().contains(finalTarget.toLowerCase())))
                .toList()
                : List.of();

        List<AiContext.TaskSummary> relatedTasks = (context.getTasks() != null)
                ? context.getTasks().stream()
                .filter(t -> (t.getModuleCode() != null && t.getModuleCode().equalsIgnoreCase(finalTarget))
                        || (t.getTitle() != null && t.getTitle().toLowerCase().contains(finalTarget.toLowerCase())))
                .toList()
                : List.of();

        double plannedHours = (context.getWeekPlan() != null)
                ? context.getWeekPlan().stream()
                .filter(s -> (s.getModuleCode() != null && s.getModuleCode().equalsIgnoreCase(finalTarget))
                        || (s.getActivityLabel() != null && s.getActivityLabel().toLowerCase().contains(finalTarget.toLowerCase())))
                .mapToDouble(AiContext.SessionSummary::getPlannedHours).sum()
                : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 🧠 Why the Planner Allocated **%.1f Hours** to **%s**\n\n", plannedHours, target));
        sb.append("The Python OR-Tools CP-SAT engine prioritised this subject based on strict optimization criteria:\n\n");

        if (!relatedAssessments.isEmpty()) {
            AiContext.AssessmentSummary ass = relatedAssessments.get(0);
            sb.append(String.format("1. **High Assessment Weight & Deadline Pressure** 🎯\n" +
                            "   • Assessment: **%s**\n" +
                            "   • Due: **%s**\n" +
                            "   • Grade Weight: **%.0f%%**\n" +
                            "   *The solver penalizes overdue risk exponentially as deadlines draw closer.*\n\n",
                    ass.getTitle(), ass.getDueDateTime() != null ? ass.getDueDateTime().replace("T", " ") : "Soon", ass.getWeight() != null ? ass.getWeight() : 0.0));
        }

        if (!relatedTasks.isEmpty()) {
            double taskHours = relatedTasks.stream().mapToDouble(t -> t.getRemainingHours() != null ? t.getRemainingHours() : 0.0).sum();
            sb.append(String.format("2. **Pending Task Workload** 📋\n" +
                    "   • You have **%d active task(s)** requiring approximately **%.1f hours** of remaining effort.\n\n",
                    relatedTasks.size(), taskHours));
        }

        sb.append("3. **Paced Study Buffer** ⏱️\n" +
                "   • Rather than cramming near the deadline, the CP-SAT engine spaces study sessions evenly across your declared available days.\n\n" +
                "💡 *Need adjustments? You can say: \"Add 1 hour to " + target + "\" or \"Reduce " + target + " by 30 minutes\".*");

        return AiProviderResponse.builder()
                .message(sb.toString().trim())
                .intent(AiIntent.GENERAL_PLAN_QUESTION)
                .actionRequired(false)
                .build();
    }

    private AiProviderResponse handleHeaviestStudyDays(AiContext context) {
        if (context.getWeekPlan() == null || context.getWeekPlan().isEmpty()) {
            return AiProviderResponse.builder()
                    .message("You do not have an active weekly study plan right now. Generate one in the **Adaptive Study Planner** to see your day-by-day study distribution.")
                    .intent(AiIntent.GENERAL_PLAN_QUESTION)
                    .actionRequired(false)
                    .build();
        }

        java.util.Map<String, Double> dayHours = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> daySessions = new java.util.LinkedHashMap<>();

        for (AiContext.SessionSummary s : context.getWeekPlan()) {
            String date = s.getDate();
            dayHours.put(date, dayHours.getOrDefault(date, 0.0) + s.getPlannedHours());
            daySessions.put(date, daySessions.getOrDefault(date, 0) + 1);
        }

        List<java.util.Map.Entry<String, Double>> sorted = dayHours.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .toList();

        StringBuilder sb = new StringBuilder("### 📅 Weekly Study Workload Breakdown\n\n");
        sb.append("Here is your study time distributed by day (ranked by heaviest workload):\n\n");

        for (int i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            String badge = (i == 0) ? " 🏋️ *(Heaviest)*" : (i == sorted.size() - 1) ? " 🍃 *(Lightest)*" : "";
            sb.append(String.format("• **%s**: **%.1f hrs** (%d session(s))%s\n",
                    entry.getKey(), entry.getValue(), daySessions.getOrDefault(entry.getKey(), 1), badge));
        }

        return AiProviderResponse.builder()
                .message(sb.toString().trim())
                .intent(AiIntent.GENERAL_PLAN_QUESTION)
                .actionRequired(false)
                .build();
    }

    private AiProviderResponse handleBehindScheduleAnalysis(AiContext context) {
        List<AiContext.SessionSummary> weekPlan = context.getWeekPlan();
        if (weekPlan == null || weekPlan.isEmpty()) {
            return AiProviderResponse.builder()
                    .message("You don't have an active study plan for this week. Head to the **Adaptive Study Planner** to create one!")
                    .intent(AiIntent.GENERAL_PLAN_QUESTION)
                    .actionRequired(false)
                    .build();
        }

        long completed = weekPlan.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long skipped = weekPlan.stream().filter(s -> "SKIPPED".equals(s.getStatus())).count();
        long pending = weekPlan.stream().filter(s -> "PLANNED".equals(s.getStatus())).count();
        double completedHours = weekPlan.stream().filter(s -> "COMPLETED".equals(s.getStatus())).mapToDouble(AiContext.SessionSummary::getPlannedHours).sum();
        double pendingHours = weekPlan.stream().filter(s -> "PLANNED".equals(s.getStatus())).mapToDouble(AiContext.SessionSummary::getPlannedHours).sum();

        StringBuilder sb = new StringBuilder();
        if (skipped > 0) {
            sb.append(String.format("⚠️ **You are slightly behind schedule.**\n\n" +
                    "• **Skipped Sessions:** %d\n" +
                    "• **Completed:** %d (%.1f hrs)\n" +
                    "• **Remaining:** %d (%.1f hrs)\n\n" +
                    "💡 *Recommendation:* You can say *\"I missed my session\"* to have the planner reschedule it, or click **Generate New Plan** in the planner tab to redistribute your remaining workload.",
                    skipped, completed, completedHours, pending, pendingHours));
        } else {
            sb.append(String.format("🎉 **You are right on track!**\n\n" +
                    "• **Completed:** %d session(s) (%.1f hrs)\n" +
                    "• **Remaining:** %d session(s) (%.1f hrs)\n" +
                    "• **Skipped:** 0 sessions\n\n" +
                    "Keep up the great consistency! You're making excellent progress through your academic goals.",
                    completed, completedHours, pending, pendingHours));
        }

        return AiProviderResponse.builder()
                .message(sb.toString())
                .intent(AiIntent.GENERAL_PLAN_QUESTION)
                .actionRequired(false)
                .build();
    }
}

