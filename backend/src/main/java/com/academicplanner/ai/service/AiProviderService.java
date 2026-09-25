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
                return activeProvider.generateResponse(CENTRAL_SYSTEM_PROMPT, userMessage, context);
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

        // ── Phase 2: Replan / Reschedule ────────────────────────────────────
        if (lower.contains("replan") || lower.contains("reschedule") || lower.contains("regenerate plan")
                || lower.contains("update my plan") || lower.contains("adjust my schedule")
                || lower.contains("behind schedule") || lower.contains("missed")) {
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
}

