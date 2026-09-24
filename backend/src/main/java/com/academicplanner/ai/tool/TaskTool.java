package com.academicplanner.ai.tool;

import com.academicplanner.dto.task.TaskResponse;
import com.academicplanner.entity.User;
import com.academicplanner.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Controlled tool for retrieving task details for the authenticated student.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTool {

    private final TaskService taskService;

    public List<TaskResponse> getTasks(User user) {
        log.info("[TaskTool] Retrieving tasks for user: {}", user.getId());
        return taskService.getAllForUser(user);
    }

    public TaskResponse getTaskDetails(Long taskId, User user) {
        log.info("[TaskTool] Retrieving task details for taskId: {}, user: {}", taskId, user.getId());
        return taskService.getById(taskId, user);
    }
}
