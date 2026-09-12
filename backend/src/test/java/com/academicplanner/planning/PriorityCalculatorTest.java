package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.planning.model.PlanningItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PriorityCalculator")
class PriorityCalculatorTest {

    private PriorityCalculator calculator;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        DeadlineUrgencyCalculator urgencyCalculator = new DeadlineUrgencyCalculator();
        calculator = new PriorityCalculator(urgencyCalculator);
        now = LocalDateTime.of(2026, 9, 10, 10, 0);
    }

    @Test
    @DisplayName("Assessment weight calculation matches spec: (basePriority + urgency) × credits")
    void assessmentWeightCalculation() {
        Module module = Module.builder().credits(4).name("Data Structures").build();

        // Quiz (base=3), due in 2 days (urgency=+2) → effective=5, credits=4 → weight = 20
        Assessment quiz = Assessment.builder()
                .module(module)
                .title("DSA Quiz")
                .type(AssessmentType.QUIZ)
                .dueDateTime(now.plusDays(2).plusHours(1))
                .build();

        PlanningItem item = PlanningItem.forAssessment(quiz);
        double weight = calculator.computeWeight(item, now);

        assertThat(weight).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Task weight calculation matches spec: userPriority × credits")
    void taskWeightCalculation() {
        Module module = Module.builder().credits(4).name("Data Structures").build();

        // High priority task with 4 credits → 2.5 × 4 = 10.0
        Task task = Task.builder()
                .module(module)
                .title("Complete DSA practice problems")
                .priority(TaskPriority.HIGH)
                .build();

        PlanningItem item = PlanningItem.forTask(task);
        double weight = calculator.computeWeight(item, now);

        assertThat(weight).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Task without module defaults credits to 1")
    void taskWithoutModuleDefaultsCreditsToOne() {
        Task task = Task.builder()
                .title("General reading")
                .priority(TaskPriority.MEDIUM) // 2.0
                .build();

        PlanningItem item = PlanningItem.forTask(task);
        double weight = calculator.computeWeight(item, now);

        assertThat(weight).isEqualTo(2.0);
    }

    @Test
    @DisplayName("calculateAll sets weights on all items in-place")
    void calculateAllMutatesItems() {
        Module module = Module.builder().credits(3).build();

        Assessment exam = Assessment.builder()
                .module(module)
                .type(AssessmentType.EXAM) // base 5
                .dueDateTime(now.plusDays(1)) // urgency +3
                .build(); // (5 + 3) * 3 = 24.0

        Task task = Task.builder()
                .module(module)
                .priority(TaskPriority.LOW) // 1.0 * 3 = 3.0
                .build();

        PlanningItem item1 = PlanningItem.forAssessment(exam);
        PlanningItem item2 = PlanningItem.forTask(task);

        calculator.calculateAll(List.of(item1, item2), now);

        assertThat(item1.getWeight()).isEqualTo(24.0);
        assertThat(item2.getWeight()).isEqualTo(3.0);
    }
}
