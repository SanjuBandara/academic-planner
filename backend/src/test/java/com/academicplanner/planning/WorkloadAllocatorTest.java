package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import com.academicplanner.planning.model.PlanningCandidate.FeasibilityStatus;
import com.academicplanner.planning.model.PriorityFactors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class WorkloadAllocatorTest {

    private WorkloadAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new WorkloadAllocator();
    }

    @Test
    @DisplayName("Never allocates more than remaining work")
    void testNeverAllocatesMoreThanRemaining() {
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("Short Task").build(), null);
        c.setRemainingWorkHours(2.0);
        c.setAvailableHoursBeforeDeadline(20.0);
        c.setPriorityFactors(new PriorityFactors(1.0, 1.0, 1.0, 1.0, 1.0));

        Map<LocalDate, Double> daily = Map.of(
                LocalDate.of(2026, 9, 7), 5.0,
                LocalDate.of(2026, 9, 8), 5.0
        );

        allocator.allocate(List.of(c), daily);

        assertThat(c.getAllocatedHours()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Leaves unused time unallocated when workload < available")
    void testLeavesUnusedTimeUnallocated() {
        PlanningCandidate c1 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("T1").build(), null);
        c1.setRemainingWorkHours(3.0);
        c1.setAvailableHoursBeforeDeadline(20.0);
        c1.setPriorityFactors(new PriorityFactors(1.0, 1.0, 1.0, 1.0, 1.0));

        Map<LocalDate, Double> daily = Map.of(
                LocalDate.of(2026, 9, 7), 10.0
        );

        allocator.allocate(List.of(c1), daily);

        assertThat(c1.getAllocatedHours()).isEqualTo(3.0);
        // Total available is 10, total allocated is 3 -> 7 hours unallocated
    }

    @Test
    @DisplayName("Protects capacity for earlier deadline before later deadline")
    void testProtectsEarlierDeadline() {
        LocalDate mon = LocalDate.of(2026, 9, 7);
        LocalDate tue = LocalDate.of(2026, 9, 8);
        LocalDate wed = LocalDate.of(2026, 9, 9);

        // Day 1 (Mon): 3h, Day 2 (Tue): 3h, Day 3 (Wed): 3h. Total = 9h.
        Map<LocalDate, Double> daily = new LinkedHashMap<>();
        daily.put(mon, 3.0);
        daily.put(tue, 3.0);
        daily.put(wed, 3.0);

        // Item A due Tuesday 17:00 (available: Mon 3h + Tue 3h = 6h). Needs 5h.
        LocalDateTime dlA = LocalDateTime.of(2026, 9, 8, 17, 0);
        PlanningCandidate candA = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("Due Tuesday").build(), dlA);
        candA.setRemainingWorkHours(5.0);
        candA.setAvailableHoursBeforeDeadline(6.0);
        candA.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
        candA.setPriorityFactors(new PriorityFactors(2.5, 0.5, 6.0, 0.5, 7.5));

        // Item B due Wednesday 17:00 (available: 9h). Needs 6h.
        LocalDateTime dlB = LocalDateTime.of(2026, 9, 9, 17, 0);
        PlanningCandidate candB = PlanningCandidate.forStandaloneTask(
                Task.builder().id(2L).title("Due Wednesday").build(), dlB);
        candB.setRemainingWorkHours(6.0);
        candB.setAvailableHoursBeforeDeadline(9.0);
        candB.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
        candB.setPriorityFactors(new PriorityFactors(5.0, 0.5, 4.0, 0.6, 6.0));

        allocator.allocate(List.of(candA, candB), daily);

        // candA has the earlier deadline -> its 5 hours are protected!
        assertThat(candA.getAllocatedHours()).isEqualTo(5.0);
        // candB gets remaining capacity (9 - 5 = 4 hours)
        assertThat(candB.getAllocatedHours()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Never allocates more than available capacity before deadline for constrained candidates")
    void testNeverExceedsDeadlineCapacity() {
        LocalDate fri = LocalDate.of(2026, 9, 11);
        LocalDate sat = LocalDate.of(2026, 9, 12);

        // Friday 6h, Saturday 6h
        Map<LocalDate, Double> daily = new LinkedHashMap<>();
        daily.put(fri, 6.0);
        daily.put(sat, 6.0);

        // Deadline Friday 12:00 -> only 3h available before deadline on Friday!
        LocalDateTime dl = LocalDateTime.of(2026, 9, 11, 12, 0);
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("Friday Noon Exam").build(), dl);
        c.setRemainingWorkHours(8.0);
        c.setAvailableHoursBeforeDeadline(3.0);
        c.setFeasibilityStatus(FeasibilityStatus.IMPOSSIBLE_WITH_CURRENT_AVAILABILITY);
        c.setPriorityFactors(new PriorityFactors(5.0, 1.0, 6.0, 1.0, 30.0));

        allocator.allocate(List.of(c), daily);

        // Cannot exceed 3.0h before its deadline!
        assertThat(c.getAllocatedHours()).isLessThanOrEqualTo(3.0);
    }
}
