package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentStatus;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.entity.Task.TaskStatus;
import com.academicplanner.planning.dto.ActivityDto;
import com.academicplanner.planning.dto.PlanningRequestDto;
import com.academicplanner.planning.mapper.PlanningRequestMapper;
import com.academicplanner.planning.mapper.PriorityMapper;
import com.academicplanner.planning.mapper.WorkloadEstimator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningRequestMapperTest {

    private PlanningRequestMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        WorkloadEstimator workloadEstimator = new WorkloadEstimator();
        PriorityMapper priorityMapper = new PriorityMapper();
        mapper = new PlanningRequestMapper(workloadEstimator, priorityMapper);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void testAssessmentWithWeightPropagated() throws Exception {
        Module module = Module.builder()
                .code("CS201")
                .name("Data Structures")
                .credits(3)
                .build();

        Assessment examWithWeight = Assessment.builder()
                .id(101L)
                .title("DSA Mid Exam")
                .type(AssessmentType.EXAM)
                .module(module)
                .dueDateTime(LocalDateTime.of(2026, 9, 20, 23, 59))
                .status(AssessmentStatus.PENDING)
                .weight(40.0)
                .build();

        Assessment quizWithoutWeight = Assessment.builder()
                .id(102L)
                .title("DSA Quiz 1")
                .type(AssessmentType.QUIZ)
                .module(module)
                .dueDateTime(LocalDateTime.of(2026, 9, 21, 23, 59))
                .status(AssessmentStatus.PENDING)
                .weight(null)
                .build();

        Task task = Task.builder()
                .id(201L)
                .title("Exercise Sheet 1")
                .priority(TaskPriority.MEDIUM)
                .module(module)
                .dueDateTime(LocalDateTime.of(2026, 9, 22, 23, 59))
                .status(TaskStatus.TODO)
                .estimatedHours(3.0)
                .build();

        PlanningRequestDto request = mapper.toRequest(
                LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 26),
                List.of(examWithWeight, quizWithoutWeight),
                List.of(task),
                Collections.emptyMap()
        );

        List<ActivityDto> activities = request.activities();
        assertThat(activities).hasSize(3);

        ActivityDto examDto = activities.get(0);
        assertThat(examDto.id()).isEqualTo("A-101");
        assertThat(examDto.title()).isEqualTo("DSA Mid Exam");
        assertThat(examDto.activityType()).isEqualTo("EXAM");
        assertThat(examDto.remainingHours()).isEqualTo(8.0);
        assertThat(examDto.priority()).isEqualTo(5);
        assertThat(examDto.weight()).isEqualTo(40.0);

        ActivityDto quizDto = activities.get(1);
        assertThat(quizDto.id()).isEqualTo("A-102");
        assertThat(quizDto.weight()).isNull();

        ActivityDto taskDto = activities.get(2);
        assertThat(taskDto.id()).isEqualTo("T-201");
        assertThat(taskDto.weight()).isNull();

        // Verify JSON serialization format
        String json = objectMapper.writeValueAsString(request);
        assertThat(json).contains("\"weight\":40.0");
        assertThat(json).contains("\"weight\":null");
    }
}
