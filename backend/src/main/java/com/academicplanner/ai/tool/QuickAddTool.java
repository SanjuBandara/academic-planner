package com.academicplanner.ai.tool;

import com.academicplanner.dto.task.TaskRequest;
import com.academicplanner.dto.task.TaskResponse;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.User;
import com.academicplanner.repository.ModuleRepository;
import com.academicplanner.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 4 tool: Parses a natural language task creation request and saves a
 * new Task directly to the database, respecting user ownership.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuickAddTool {

    private final TaskService taskService;
    private final ModuleRepository moduleRepository;

    /**
     * Parses a natural-language message like:
     *   "Add a DSA assignment due next Friday worth 4 hours"
     *   "Create a task for CS101 essay due tomorrow, 2 hours"
     *
     * @return the created TaskResponse, or null if parsing fails
     */
    public TaskResponse quickAddTask(String message, User user) {
        log.info("[QuickAddTool] Parsing quick-add request: '{}' for user: {}", message, user.getId());

        String lower = message.toLowerCase();

        // --- Extract title ---
        String title = extractTitle(message, lower);
        if (title == null || title.isBlank()) {
            log.warn("[QuickAddTool] Could not extract task title from: '{}'", message);
            return null;
        }

        // --- Extract estimated hours ---
        Double hours = extractHours(lower);

        // --- Extract due date ---
        LocalDateTime dueDateTime = extractDueDate(lower);

        // --- Find matching module by code/name mention ---
        Long moduleId = findModuleId(lower, user);

        // --- Determine priority from keywords ---
        Task.TaskPriority priority = extractPriority(lower);

        TaskRequest request = new TaskRequest(
                title,
                "Created by AI Assistant from: \"" + message + "\"",
                moduleId,
                hours != null ? hours : 2.0,
                priority,
                Task.TaskStatus.TODO,
                dueDateTime
        );

        try {
            TaskResponse created = taskService.create(request, user);
            log.info("[QuickAddTool] Successfully created task '{}' (id={}) for user {}", created.title(), created.id(), user.getId());
            return created;
        } catch (Exception e) {
            log.error("[QuickAddTool] Failed to create task: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractTitle(String message, String lower) {
        // Remove common lead phrases
        String cleaned = message
                .replaceAll("(?i)^(add|create|add a|create a|new task[:]?|task[:]?|remind me to)\\s+", "")
                .replaceAll("(?i)\\s+(due|by|deadline|for next|next|this|worth|estimated|hours?|hr).*", "")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private Double extractHours(String lower) {
        // Matches "4 hours", "2.5 hrs", "3h"
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?|h\\b)");
        Matcher m = p.matcher(lower);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private LocalDateTime extractDueDate(String lower) {
        LocalDateTime now = LocalDateTime.now();
        if (lower.contains("tomorrow")) return now.plusDays(1).withHour(23).withMinute(59);
        if (lower.contains("next monday"))    return nextWeekday(now, 1);
        if (lower.contains("next tuesday"))   return nextWeekday(now, 2);
        if (lower.contains("next wednesday")) return nextWeekday(now, 3);
        if (lower.contains("next thursday"))  return nextWeekday(now, 4);
        if (lower.contains("next friday"))    return nextWeekday(now, 5);
        if (lower.contains("next saturday"))  return nextWeekday(now, 6);
        if (lower.contains("next sunday"))    return nextWeekday(now, 7);
        if (lower.contains("this week") || lower.contains("end of week")) return now.plusDays(5).withHour(23).withMinute(59);
        if (lower.contains("next week"))   return now.plusDays(7).withHour(23).withMinute(59);
        if (lower.contains("in 3 days") || lower.contains("3 days")) return now.plusDays(3).withHour(23).withMinute(59);
        if (lower.contains("in 2 days") || lower.contains("2 days")) return now.plusDays(2).withHour(23).withMinute(59);
        return null; // no due date extracted
    }

    private LocalDateTime nextWeekday(LocalDateTime from, int targetDayOfWeek) {
        int today = from.getDayOfWeek().getValue(); // 1=MON … 7=SUN
        int daysUntil = (targetDayOfWeek - today + 7) % 7;
        if (daysUntil == 0) daysUntil = 7;
        return from.plusDays(daysUntil).withHour(23).withMinute(59);
    }

    private Long findModuleId(String lower, User user) {
        List<Module> modules = moduleRepository.findAllByUserId(user.getId());
        for (Module m : modules) {
            if (m.getCode() != null && lower.contains(m.getCode().toLowerCase())) return m.getId();
            if (m.getName() != null && lower.contains(m.getName().toLowerCase())) return m.getId();
        }
        return null;
    }

    private Task.TaskPriority extractPriority(String lower) {
        if (lower.contains("urgent") || lower.contains("high priority") || lower.contains("critical") || lower.contains("important")) {
            return Task.TaskPriority.HIGH;
        }
        if (lower.contains("low priority") || lower.contains("not urgent") || lower.contains("whenever")) {
            return Task.TaskPriority.LOW;
        }
        return Task.TaskPriority.MEDIUM;
    }
}
