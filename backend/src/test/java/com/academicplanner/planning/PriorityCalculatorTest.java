package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PriorityCalculatorTest {

    private DeadlineAnalyzer deadlineAnalyzer;
    private PriorityCalculator priorityCalculator;

    @BeforeEach
    void setUp() {
        deadlineAnalyzer = new DeadlineAnalyzer();
        priorityCalculator = new PriorityCalculator(deadlineAnalyzer);
    }

    @Test
    @DisplayName("Academic type weights follow EXAM > PROJECT > ASSIGNMENT > QUIZ > OTHER")
    void testTypeWeightOrdering() {
        assertThat(priorityCalculator.typeWeight(AssessmentType.EXAM)).isEqualTo(5.0);
        assertThat(priorityCalculator.typeWeight(AssessmentType.PROJECT)).isEqualTo(3.0);
        assertThat(priorityCalculator.typeWeight(AssessmentType.ASSIGNMENT)).isEqualTo(2.5);
        assertThat(priorityCalculator.typeWeight(AssessmentType.REPORT)).isEqualTo(2.5);
        assertThat(priorityCalculator.typeWeight(AssessmentType.QUIZ)).isEqualTo(2.0);
        assertThat(priorityCalculator.typeWeight(AssessmentType.PRESENTATION)).isEqualTo(2.0);
        assertThat(priorityCalculator.typeWeight(AssessmentType.OTHER)).isEqualTo(1.0);
        assertThat(priorityCalculator.typeWeight(null)).isEqualTo(1.0);

        assertThat(priorityCalculator.typeWeight(AssessmentType.EXAM))
                .isGreaterThan(priorityCalculator.typeWeight(AssessmentType.PROJECT));
        assertThat(priorityCalculator.typeWeight(AssessmentType.PROJECT))
                .isGreaterThan(priorityCalculator.typeWeight(AssessmentType.ASSIGNMENT));
        assertThat(priorityCalculator.typeWeight(AssessmentType.ASSIGNMENT))
                .isGreaterThan(priorityCalculator.typeWeight(AssessmentType.QUIZ));
        assertThat(priorityCalculator.typeWeight(AssessmentType.QUIZ))
                .isGreaterThan(priorityCalculator.typeWeight(AssessmentType.OTHER));
    }

    @Test
    @DisplayName("Credit weight normalizes correctly across modules")
    void testCreditNormalization() {
        Module modA = Module.builder().id(1L).code("CS101").credits(3).build();
        Module modB = Module.builder().id(2L).code("CS102").credits(6).build();
        Module modC = Module.builder().id(3L).code("CS103").credits(3).build();

        Assessment a1 = Assessment.builder().id(1L).module(modA).type(AssessmentType.ASSIGNMENT).build();
        Assessment a2 = Assessment.builder().id(2L).module(modB).type(AssessmentType.ASSIGNMENT).build();
        Assessment a3 = Assessment.builder().id(3L).module(modC).type(AssessmentType.ASSIGNMENT).build();

        PlanningCandidate c1 = PlanningCandidate.forAssessment(a1, null);
        c1.setRemainingWorkHours(5.0);
        PlanningCandidate c2 = PlanningCandidate.forAssessment(a2, null);
        c2.setRemainingWorkHours(5.0);
        PlanningCandidate c3 = PlanningCandidate.forAssessment(a3, null);
        c3.setRemainingWorkHours(5.0);

        List<PlanningCandidate> candidates = List.of(c1, c2, c3);
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 9, 0);

        priorityCalculator.calculateAll(candidates, now);

        // Total credits = 3 + 6 + 3 = 12
        assertThat(c1.getPriorityFactors().creditWeight()).isCloseTo(3.0 / 12.0, offset(0.001));
        assertThat(c2.getPriorityFactors().creditWeight()).isCloseTo(6.0 / 12.0, offset(0.001));
        assertThat(c3.getPriorityFactors().creditWeight()).isCloseTo(3.0 / 12.0, offset(0.001));
    }

    @Test
    @DisplayName("Deadline urgency levels match standard thresholds")
    void testUrgencyLevels() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 9, 0);

        // > 30 days -> 1.0
        assertThat(deadlineAnalyzer.urgencyMultiplier(35)).isEqualTo(1.0);
        // 15 - 30 days -> 1.5
        assertThat(deadlineAnalyzer.urgencyMultiplier(20)).isEqualTo(1.5);
        // 7 - 14 days -> 2.5
        assertThat(deadlineAnalyzer.urgencyMultiplier(10)).isEqualTo(2.5);
        // 3 - 6 days -> 4.0
        assertThat(deadlineAnalyzer.urgencyMultiplier(4)).isEqualTo(4.0);
        // 0 - 2 days -> 6.0
        assertThat(deadlineAnalyzer.urgencyMultiplier(1)).isEqualTo(6.0);
        // Overdue (< 0 days) -> 7.0
        assertThat(deadlineAnalyzer.urgencyMultiplier(-1)).isEqualTo(7.0);

        // Candidate with null deadline -> 1.0
        PlanningCandidate noDeadline = PlanningCandidate.forStandaloneTask(
                Task.builder().id(10L).title("Study").build(), null);
        assertThat(deadlineAnalyzer.urgencyMultiplier(noDeadline, now)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Workload factor scales proportionally to max remaining work")
    void testWorkloadFactor() {
        Module mod = Module.builder().id(1L).credits(4).build();
        Assessment a1 = Assessment.builder().id(1L).module(mod).type(AssessmentType.EXAM).build();
        Assessment a2 = Assessment.builder().id(2L).module(mod).type(AssessmentType.EXAM).build();

        PlanningCandidate c1 = PlanningCandidate.forAssessment(a1, null);
        c1.setRemainingWorkHours(8.0);
        PlanningCandidate c2 = PlanningCandidate.forAssessment(a2, null);
        c2.setRemainingWorkHours(10.0);

        priorityCalculator.calculateAll(List.of(c1, c2), LocalDateTime.now());

        // Max remaining is 10.0
        assertThat(c1.getPriorityFactors().workloadFactor()).isCloseTo(0.8, offset(0.001));
        assertThat(c2.getPriorityFactors().workloadFactor()).isCloseTo(1.0, offset(0.001));
    }

    @Test
    @DisplayName("Candidate with zero remaining hours gets score zero")
    void testZeroWorkload() {
        Module mod = Module.builder().id(1L).credits(4).build();
        Assessment a = Assessment.builder().id(1L).module(mod).type(AssessmentType.EXAM).build();

        PlanningCandidate c = PlanningCandidate.forAssessment(a, null);
        c.setRemainingWorkHours(0.0);

        priorityCalculator.calculateAll(List.of(c), LocalDateTime.now());

        assertThat(c.getPriorityScore()).isEqualTo(0.0);
        assertThat(c.getPriorityFactors().workloadFactor()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Priority score is explainable product of the four factors")
    void testExplainablePriorityFormula() {
        Module mod = Module.builder().id(1L).credits(4).build();
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 15, 17, 0);
        Assessment a = Assessment.builder().id(1L).module(mod).type(AssessmentType.EXAM).build();

        PlanningCandidate c = PlanningCandidate.forAssessment(a, deadline);
        c.setRemainingWorkHours(6.0);

        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 9, 0); // 4 days remaining -> urgency 4.0

        priorityCalculator.calculateAll(List.of(c), now);

        var pf = c.getPriorityFactors();
        assertThat(pf.typeWeight()).isEqualTo(5.0);
        assertThat(pf.creditWeight()).isEqualTo(1.0); // only 1 module
        assertThat(pf.urgencyWeight()).isEqualTo(4.0);
        assertThat(pf.workloadFactor()).isEqualTo(1.0); // only 1 candidate

        double expected = 5.0 * 1.0 * 4.0 * 1.0; // 20.0
        assertThat(c.getPriorityScore()).isCloseTo(expected, offset(0.001));
        assertThat(pf.explain()).contains("typeWeight=5.00").contains("urgencyWeight=4.00");
    }
}
