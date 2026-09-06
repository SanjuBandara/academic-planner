package com.academicplanner.service;

import com.academicplanner.dto.task.TaskProgressUpdate;
import com.academicplanner.dto.task.TaskRequest;
import com.academicplanner.dto.task.TaskResponse;
import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.entity.Task.TaskStatus;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.AssessmentRepository;
import com.academicplanner.repository.ModuleRepository;
import com.academicplanner.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ModuleRepository moduleRepository;
    private final AssessmentRepository assessmentRepository;

    @Transactional(readOnly = true)
    public List<TaskResponse> getAllForUser(User user) {
        return taskRepository.findAllByUserOrderByDueDateTimeAscCreatedAtDesc(user)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(Long id, User user) {
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse create(TaskRequest request, User user) {
        Module module = null;
        if (request.moduleId() != null) {
            module = moduleRepository.findByIdAndSemester_User_Id(request.moduleId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + request.moduleId()));
        }

        Assessment assessment = null;
        if (request.assessmentId() != null) {
            assessment = assessmentRepository.findByIdAndModule_Semester_User_Id(request.assessmentId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + request.assessmentId()));
        }

        Double hours = request.estimatedHours() != null ? request.estimatedHours() : 1.0;

        Task task = Task.builder()
                .user(user)
                .module(module)
                .assessment(assessment)
                .title(request.title())
                .description(request.description())
                .estimatedHours(hours)
                .remainingHours(hours)
                .priority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM)
                .status(request.status() != null ? request.status() : TaskStatus.TODO)
                .dueDateTime(request.dueDateTime())
                .build();

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request, User user) {
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        if (request.moduleId() != null) {
            Module module = moduleRepository.findByIdAndSemester_User_Id(request.moduleId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + request.moduleId()));
            task.setModule(module);
        } else {
            task.setModule(null);
        }

        if (request.assessmentId() != null) {
            Assessment assessment = assessmentRepository.findByIdAndModule_Semester_User_Id(request.assessmentId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + request.assessmentId()));
            task.setAssessment(assessment);
        } else {
            task.setAssessment(null);
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        if (request.estimatedHours() != null) {
            task.setEstimatedHours(request.estimatedHours());
            if (task.getRemainingHours() == null || task.getRemainingHours() > request.estimatedHours()) {
                task.setRemainingHours(request.estimatedHours());
            }
        }
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.status() != null) {
            task.setStatus(request.status());
            if (request.status() == TaskStatus.COMPLETED) {
                task.setRemainingHours(0.0);
                task.setCompletedAt(LocalDateTime.now());
            }
        }
        if (request.dueDateTime() != null) task.setDueDateTime(request.dueDateTime());

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateProgress(Long id, TaskProgressUpdate update, User user) {
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        double currentRemaining = task.getRemainingHours() != null ? task.getRemainingHours() : task.getEstimatedHours();
        double newRemaining = Math.max(0.0, currentRemaining - update.workedHours());
        task.setRemainingHours(newRemaining);

        if (newRemaining <= 0.0) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
        } else if (task.getStatus() == TaskStatus.TODO) {
            task.setStatus(TaskStatus.IN_PROGRESS);
        }

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse toggleComplete(Long id, User user) {
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        if (task.getStatus() == TaskStatus.COMPLETED) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setRemainingHours(task.getEstimatedHours() > 0 ? task.getEstimatedHours() : 1.0);
            task.setCompletedAt(null);
        } else {
            task.setStatus(TaskStatus.COMPLETED);
            task.setRemainingHours(0.0);
            task.setCompletedAt(LocalDateTime.now());
        }

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id, User user) {
        Task task = taskRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        taskRepository.delete(task);
    }
}
