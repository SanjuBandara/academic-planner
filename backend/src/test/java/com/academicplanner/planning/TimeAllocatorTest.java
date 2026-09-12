package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimeAllocator")
class TimeAllocatorTest {

    private TimeAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new TimeAllocator();
    }

    @Test
    @DisplayName("Allocates hours proportionally to weight")
    void proportionalAllocation() {
        Assessment a1 = Assessment.builder().title("Exam Prep").build();
        Assessment a2 = Assessment.builder().title("Assignment").build();

        PlanningItem item1 = PlanningItem.forAssessment(a1);
        item1.setWeight(20.0); // 2/3 of weight

        PlanningItem item2 = PlanningItem.forAssessment(a2);
        item2.setWeight(10.0); // 1/3 of weight

        allocator.allocate(List.of(item1, item2), 9.0);

        assertThat(item1.getAllocatedHours()).isEqualTo(6.0);
        assertThat(item2.getAllocatedHours()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Respects task estimated hours cap and redistributes surplus")
    void respectsTaskCapAndRedistributes() {
        Task cappedTask = Task.builder()
                .title("Small Task")
                .estimatedHours(2.0)
                .build();

        Assessment uncapped = Assessment.builder()
                .title("Big Project")
                .build();

        PlanningItem taskItem = PlanningItem.forTask(cappedTask);
        taskItem.setWeight(20.0); // Equal weight

        PlanningItem projectItem = PlanningItem.forAssessment(uncapped);
        projectItem.setWeight(20.0); // Equal weight

        // Available: 10.0h.
        // Equal share without cap would be 5.0h each.
        // But taskItem is capped at 2.0h, so surplus 3.0h goes to projectItem (2.0h + 8.0h).
        allocator.allocate(List.of(taskItem, projectItem), 10.0);

        assertThat(taskItem.getAllocatedHours()).isEqualTo(2.0);
        assertThat(projectItem.getAllocatedHours()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("Handles zero available hours gracefully")
    void zeroAvailableHours() {
        PlanningItem item = PlanningItem.forAssessment(Assessment.builder().title("Quiz").build());
        item.setWeight(10.0);

        allocator.allocate(List.of(item), 0.0);

        assertThat(item.getAllocatedHours()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Total allocated never exceeds total available hours")
    void sumNeverExceedsTotalAvailable() {
        PlanningItem item1 = PlanningItem.forAssessment(Assessment.builder().title("Q1").build());
        item1.setWeight(7.0);

        PlanningItem item2 = PlanningItem.forAssessment(Assessment.builder().title("Q2").build());
        item2.setWeight(13.0);

        allocator.allocate(List.of(item1, item2), 7.3);

        double total = item1.getAllocatedHours() + item2.getAllocatedHours();
        assertThat(total).isLessThanOrEqualTo(7.3);
    }
}
