package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.planning.model.PlanningResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanningEngine")
class PlanningEngineTest {

    private PlanningEngine engine;

    @BeforeEach
    void setUp() {
        DeadlineUrgencyCalculator urgencyCalculator = new DeadlineUrgencyCalculator();
        PriorityCalculator priorityCalculator = new PriorityCalculator(urgencyCalculator);
        TimeAllocator timeAllocator = new TimeAllocator();
        FeasibilityAnalyzer feasibilityAnalyzer = new FeasibilityAnalyzer(urgencyCalculator);
        SessionScheduler sessionScheduler = new SessionScheduler();

        engine = new PlanningEngine(
                priorityCalculator,
                timeAllocator,
                feasibilityAnalyzer,
                sessionScheduler
        );
    }

    @Test
    @DisplayName("End-to-end planning with Quiz and High-Priority Task matches specification formula")
    void testEndToEndPlanningWithSpecExample() {
        StudyPlan plan = StudyPlan.builder().id(1L).build();
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 8, 0);

        LocalDate day1 = LocalDate.of(2026, 9, 10);
        LocalDate day2 = LocalDate.of(2026, 9, 11);

        Map<LocalDate, Double> daily = new LinkedHashMap<>();
        daily.put(day1, 4.5);
        daily.put(day2, 4.5); // Total 9.0 hours available

        Module dsaModule = Module.builder().id(1L).code("CS201").name("Data Structures").credits(4).build();

        // Assessment: DSA Quiz, due in 2 days (urgency bonus +2). Base QUIZ = 3. Weight = (3 + 2) * 4 = 20.0
        Assessment dsaQuiz = Assessment.builder()
                .id(10L)
                .module(dsaModule)
                .title("DSA Quiz")
                .type(AssessmentType.QUIZ)
                .dueDateTime(now.plusDays(2))
                .build();

        // Standalone Task: Complete DSA practice problems, Priority: HIGH (2.5). Weight = 2.5 * 4 = 10.0
        Task dsaTask = Task.builder()
                .id(20L)
                .module(dsaModule)
                .title("Complete DSA practice problems")
                .priority(TaskPriority.HIGH)
                .build();

        PlanningResult result = engine.generatePlan(
                plan,
                List.of(dsaQuiz),
                List.of(dsaTask),
                daily,
                now
        );

        assertThat(result.items()).isNotEmpty();
        assertThat(result.totalPlannedHours()).isEqualTo(9.0);

        // Quiz gets 20/30 of 9.0h = 6.0h
        List<StudyPlanItem> quizItems = result.items().stream()
                .filter(i -> i.getAssessment() != null && i.getAssessment().getId().equals(10L))
                .toList();
        double quizHours = quizItems.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        assertThat(quizHours).isEqualTo(6.0);
        assertThat(quizItems.get(0).getActivityType()).isEqualTo("ASSESSMENT_PREP");

        // Task gets 10/30 of 9.0h = 3.0h
        List<StudyPlanItem> taskItems = result.items().stream()
                .filter(i -> i.getTask() != null && i.getTask().getId().equals(20L))
                .toList();
        double taskHours = taskItems.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        assertThat(taskHours).isEqualTo(3.0);
        assertThat(taskItems.get(0).getActivityType()).isEqualTo("TASK");
        assertThat(taskItems.get(0).getActivityLabel()).isEqualTo("Complete DSA practice problems");
    }

    @Test
    @DisplayName("Empty inputs gracefully yield empty plan with zero planned hours")
    void testEmptyInputs() {
        StudyPlan plan = StudyPlan.builder().id(1L).build();
        Map<LocalDate, Double> daily = Map.of(LocalDate.now(), 4.0);

        PlanningResult result = engine.generatePlan(plan, List.of(), List.of(), daily);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalPlannedHours()).isEqualTo(0.0);
        assertThat(result.totalAvailableHours()).isEqualTo(4.0);
        assertThat(result.unallocatedHours()).isEqualTo(4.0);
    }
}
