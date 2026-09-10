package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningEngineTest {

    private PlanningEngine engine;

    @BeforeEach
    void setUp() {
        WorkloadEstimator workloadEstimator = new WorkloadEstimator();
        DeadlineAnalyzer deadlineAnalyzer = new DeadlineAnalyzer();
        PriorityCalculator priorityCalculator = new PriorityCalculator(deadlineAnalyzer);
        FeasibilityAnalyzer feasibilityAnalyzer = new FeasibilityAnalyzer();
        WorkloadAllocator workloadAllocator = new WorkloadAllocator();
        SessionScheduler sessionScheduler = new SessionScheduler();

        engine = new PlanningEngine(
                workloadEstimator,
                deadlineAnalyzer,
                priorityCalculator,
                feasibilityAnalyzer,
                workloadAllocator,
                sessionScheduler
        );
    }

    @Test
    @DisplayName("Specification Example (Section 25): Database Exam, Software Assignment, Math Quiz")
    void testSpecExample() {
        StudyPlan plan = StudyPlan.builder().id(1L).build();

        // Start Monday 2026-09-07
        LocalDate mon = LocalDate.of(2026, 9, 7);
        LocalDate tue = LocalDate.of(2026, 9, 8);
        LocalDate wed = LocalDate.of(2026, 9, 9);
        LocalDate thu = LocalDate.of(2026, 9, 10);
        LocalDate fri = LocalDate.of(2026, 9, 11);

        Map<LocalDate, Double> daily = new LinkedHashMap<>();
        daily.put(mon, 4.0);
        daily.put(tue, 4.0);
        daily.put(wed, 3.0);
        daily.put(thu, 4.0);
        daily.put(fri, 3.0);

        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 8, 0);

        Module modCS = Module.builder().id(1L).code("CS").credits(4).build();
        Module modSE = Module.builder().id(2L).code("SE").credits(3).build();
        Module modMA = Module.builder().id(3L).code("MA").credits(2).build();

        // Database Exam: EXAM, 4 cr, 8h, Wed 17:00
        Assessment dbExam = Assessment.builder()
                .id(101L).title("Database Exam")
                .module(modCS).type(AssessmentType.EXAM)
                .estimatedHours(8.0)
                .dueDateTime(LocalDateTime.of(2026, 9, 9, 17, 0))
                .build();

        // Software Assignment: ASSIGNMENT, 3 cr, 5h, Fri 17:00
        Assessment swAssign = Assessment.builder()
                .id(102L).title("Software Assignment")
                .module(modSE).type(AssessmentType.ASSIGNMENT)
                .estimatedHours(5.0)
                .dueDateTime(LocalDateTime.of(2026, 9, 11, 17, 0))
                .build();

        // Math Quiz: QUIZ, 2 cr, 2h, Thu 17:00
        Assessment mathQuiz = Assessment.builder()
                .id(103L).title("Math Quiz")
                .module(modMA).type(AssessmentType.QUIZ)
                .estimatedHours(2.0)
                .dueDateTime(LocalDateTime.of(2026, 9, 10, 17, 0))
                .build();

        PlanningResult result = engine.generatePlan(
                plan,
                List.of(dbExam, swAssign, mathQuiz),
                List.of(),
                daily,
                now
        );

        // Verify all 3 are fully planned since total need is 8 + 5 + 2 = 15h, and total availability is 18h
        assertThat(result.items()).isNotEmpty();
        assertThat(result.totalPlannedHours()).isEqualTo(15.0);
        assertThat(result.unallocatedHours()).isEqualTo(3.0);

        // Database Exam items must strictly be scheduled on or before Wednesday
        List<StudyPlanItem> dbItems = result.items().stream()
                .filter(i -> i.getAssessment() != null && i.getAssessment().getId().equals(101L))
                .toList();
        double dbHours = dbItems.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        assertThat(dbHours).isEqualTo(8.0);
        for (StudyPlanItem item : dbItems) {
            assertThat(item.getDate()).isBeforeOrEqualTo(wed);
        }

        // Math Quiz items must be scheduled on or before Thursday
        List<StudyPlanItem> mathItems = result.items().stream()
                .filter(i -> i.getAssessment() != null && i.getAssessment().getId().equals(103L))
                .toList();
        double mathHours = mathItems.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        assertThat(mathHours).isEqualTo(2.0);
        for (StudyPlanItem item : mathItems) {
            assertThat(item.getDate()).isBeforeOrEqualTo(thu);
        }

        // Software Assignment items must be scheduled on or before Friday
        List<StudyPlanItem> swItems = result.items().stream()
                .filter(i -> i.getAssessment() != null && i.getAssessment().getId().equals(102L))
                .toList();
        double swHours = swItems.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        assertThat(swHours).isEqualTo(5.0);
        for (StudyPlanItem item : swItems) {
            assertThat(item.getDate()).isBeforeOrEqualTo(fri);
        }
    }

    @Test
    @DisplayName("Replanning: updating task remaining workload updates future generated plan")
    void testReplanningWithUpdatedWorkload() {
        StudyPlan plan = StudyPlan.builder().id(1L).build();
        LocalDate mon = LocalDate.of(2026, 9, 7);
        LocalDate tue = LocalDate.of(2026, 9, 8);
        Map<LocalDate, Double> daily = Map.of(mon, 4.0, tue, 4.0);

        Assessment a = Assessment.builder()
                .id(1L).title("Exam")
                .type(AssessmentType.EXAM)
                .dueDateTime(LocalDateTime.of(2026, 9, 8, 17, 0))
                .build();

        // Run 1: Task has 6 remaining hours
        Task tRun1 = Task.builder().id(10L).assessment(a).title("Task 1").remainingHours(6.0).build();
        PlanningResult result1 = engine.generatePlan(plan, List.of(a), List.of(tRun1), daily, LocalDateTime.of(2026, 9, 7, 8, 0));
        assertThat(result1.totalPlannedHours()).isEqualTo(6.0);

        // Run 2: Student completed 4 hours, now only 2 remaining hours remain
        Task tRun2 = Task.builder().id(10L).assessment(a).title("Task 1").remainingHours(2.0).build();
        PlanningResult result2 = engine.generatePlan(plan, List.of(a), List.of(tRun2), daily, LocalDateTime.of(2026, 9, 7, 8, 0));
        assertThat(result2.totalPlannedHours()).isEqualTo(2.0);
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
