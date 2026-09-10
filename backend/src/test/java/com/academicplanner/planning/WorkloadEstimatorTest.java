package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskStatus;
import com.academicplanner.planning.model.PlanningCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class WorkloadEstimatorTest {

    private WorkloadEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new WorkloadEstimator();
    }

    @Test
    @DisplayName("Assessment with linked tasks sums active tasks remaining hours")
    void testAssessmentDerivesFromLinkedTasks() {
        Assessment a = Assessment.builder().id(100L).title("Exam 1").estimatedHours(20.0).build();

        Task t1 = Task.builder().id(1L).assessment(a).title("T1").remainingHours(3.0).status(TaskStatus.TODO).build();
        Task t2 = Task.builder().id(2L).assessment(a).title("T2").remainingHours(4.0).status(TaskStatus.IN_PROGRESS).build();
        Task t3 = Task.builder().id(3L).assessment(a).title("T3").remainingHours(5.0).status(TaskStatus.COMPLETED).build(); // completed -> 0

        PlanningCandidate c = PlanningCandidate.forAssessment(a, null);
        List<PlanningCandidate> candidates = List.of(c);
        List<Task> allTasks = List.of(t1, t2, t3);

        estimator.estimate(candidates, allTasks);

        // 3.0 + 4.0 + 0.0 = 7.0 (not the assessment's 20.0, and completed task ignored)
        assertThat(c.getRemainingWorkHours()).isCloseTo(7.0, offset(0.001));
    }

    @Test
    @DisplayName("Assessment with no linked tasks falls back to assessment estimatedHours")
    void testAssessmentFallbackToEstimatedHours() {
        Assessment a = Assessment.builder().id(200L).title("Exam 2").estimatedHours(8.5).build();
        PlanningCandidate c = PlanningCandidate.forAssessment(a, null);

        estimator.estimate(List.of(c), List.of());

        assertThat(c.getRemainingWorkHours()).isCloseTo(8.5, offset(0.001));
    }

    @Test
    @DisplayName("Standalone task uses remainingHours or falls back to estimatedHours")
    void testStandaloneTaskWorkload() {
        Task tWithRemaining = Task.builder().id(1L).title("Task A").remainingHours(2.5).estimatedHours(5.0).build();
        Task tWithEstimatedOnly = Task.builder().id(2L).title("Task B").remainingHours(null).estimatedHours(4.0).build();

        PlanningCandidate c1 = PlanningCandidate.forStandaloneTask(tWithRemaining, null);
        PlanningCandidate c2 = PlanningCandidate.forStandaloneTask(tWithEstimatedOnly, null);

        estimator.estimate(List.of(c1, c2), List.of(tWithRemaining, tWithEstimatedOnly));

        assertThat(c1.getRemainingWorkHours()).isCloseTo(2.5, offset(0.001));
        assertThat(c2.getRemainingWorkHours()).isCloseTo(4.0, offset(0.001));
    }

    @Test
    @DisplayName("Explicit remainingHours of 0.0 does not fall back to estimatedHours")
    void testExplicitZeroDoesNotFallback() {
        Task t = Task.builder().id(3L).title("Task C").remainingHours(0.0).estimatedHours(10.0).status(TaskStatus.TODO).build();

        assertThat(estimator.resolveTaskRemaining(t)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Completed or cancelled tasks have 0 remaining hours")
    void testCompletedOrCancelledTasksZeroRemaining() {
        Task completed = Task.builder().id(4L).title("Done").status(TaskStatus.COMPLETED).remainingHours(10.0).build();
        Task cancelled = Task.builder().id(5L).title("Dropped").status(TaskStatus.CANCELLED).remainingHours(8.0).build();

        assertThat(estimator.resolveTaskRemaining(completed)).isEqualTo(0.0);
        assertThat(estimator.resolveTaskRemaining(cancelled)).isEqualTo(0.0);
    }
}
