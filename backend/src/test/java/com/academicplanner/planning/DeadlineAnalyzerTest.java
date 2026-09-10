package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import com.academicplanner.planning.model.PlanningContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class DeadlineAnalyzerTest {

    private DeadlineAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new DeadlineAnalyzer();
    }

    @Test
    @DisplayName("Effective deadline chooses earlier of task or assessment deadline")
    void testEffectiveDeadlineSelection() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 15, 12, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 18, 17, 0);

        Assessment aWithEarlier = Assessment.builder().dueDateTime(t1).build();
        Task tWithLater = Task.builder().dueDateTime(t2).build();
        assertThat(DeadlineAnalyzer.effectiveDeadline(aWithEarlier, tWithLater)).isEqualTo(t1);

        Assessment aWithLater = Assessment.builder().dueDateTime(t2).build();
        Task tWithEarlier = Task.builder().dueDateTime(t1).build();
        assertThat(DeadlineAnalyzer.effectiveDeadline(aWithLater, tWithEarlier)).isEqualTo(t1);

        // One null, one present
        assertThat(DeadlineAnalyzer.effectiveDeadline(aWithEarlier, null)).isEqualTo(t1);
        assertThat(DeadlineAnalyzer.effectiveDeadline(null, tWithEarlier)).isEqualTo(t1);

        // Both null
        assertThat(DeadlineAnalyzer.effectiveDeadline(null, null)).isNull();
    }

    @Test
    @DisplayName("Same-day deadline with exact time boundary correctly caps usable hours")
    void testSameDayDeadlineAvailableHours() {
        // Now is Friday 08:00. Deadline is Friday 12:00.
        // Sessions start at 09:00 -> between 09:00 and 12:00 is 3.0 hours.
        // Friday capacity declared is 6.0 hours. Usable before deadline should be 3.0h.
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 8, 0);
        LocalDate friday = now.toLocalDate();
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 11, 12, 0);

        Map<LocalDate, Double> dailyHours = new LinkedHashMap<>();
        dailyHours.put(friday, 6.0);

        double available = analyzer.computeAvailableBeforeDeadline(deadline, dailyHours, now);
        assertThat(available).isCloseTo(3.0, offset(0.001));
    }

    @Test
    @DisplayName("Deadline at or before 09:00 on same day yields 0 usable hours")
    void testDeadlineBeforeStartYieldsZero() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 8, 0);
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 11, 9, 0);

        Map<LocalDate, Double> dailyHours = Map.of(now.toLocalDate(), 5.0);
        double available = analyzer.computeAvailableBeforeDeadline(deadline, dailyHours, now);
        assertThat(available).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Multi-day capacity sums preceding days and caps deadline day")
    void testMultiDayCapacityBeforeDeadline() {
        // Monday: 4h, Tuesday: 4h, Wednesday: 6h.
        // Deadline is Wednesday 14:00. From 09:00 to 14:00 is 5h.
        // Total available = 4 (Mon) + 4 (Tue) + 5 (Wed) = 13h.
        LocalDate mon = LocalDate.of(2026, 9, 7);
        LocalDate tue = LocalDate.of(2026, 9, 8);
        LocalDate wed = LocalDate.of(2026, 9, 9);
        LocalDate thu = LocalDate.of(2026, 9, 10);

        Map<LocalDate, Double> dailyHours = new LinkedHashMap<>();
        dailyHours.put(mon, 4.0);
        dailyHours.put(tue, 4.0);
        dailyHours.put(wed, 6.0);
        dailyHours.put(thu, 4.0);

        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 8, 0);
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 9, 14, 0);

        double available = analyzer.computeAvailableBeforeDeadline(deadline, dailyHours, now);
        assertThat(available).isCloseTo(13.0, offset(0.001));
    }

    @Test
    @DisplayName("No deadline treats all days in schedule as available")
    void testNoDeadlineTreatsAllAvailable() {
        LocalDate d1 = LocalDate.of(2026, 9, 7);
        LocalDate d2 = LocalDate.of(2026, 9, 8);
        Map<LocalDate, Double> dailyHours = Map.of(d1, 3.0, d2, 4.0);

        double available = analyzer.computeAvailableBeforeDeadline(null, dailyHours, LocalDateTime.of(2026, 9, 7, 8, 0));
        assertThat(available).isEqualTo(7.0);
    }

    @Test
    @DisplayName("analyzeAll populates availableHoursBeforeDeadline on candidates")
    void testAnalyzeAll() {
        LocalDate mon = LocalDate.of(2026, 9, 7);
        LocalDate tue = LocalDate.of(2026, 9, 8);
        Map<LocalDate, Double> dailyHours = Map.of(mon, 4.0, tue, 4.0);

        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 8, 0);
        PlanningCandidate c1 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("T1").build(),
                LocalDateTime.of(2026, 9, 7, 12, 0)); // 3h available on Mon
        PlanningCandidate c2 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(2L).title("T2").build(),
                null); // 8h available (all)

        List<PlanningCandidate> list = List.of(c1, c2);
        PlanningContext ctx = new PlanningContext(list, dailyHours, now);

        analyzer.analyzeAll(list, ctx);

        assertThat(c1.getAvailableHoursBeforeDeadline()).isCloseTo(3.0, offset(0.001));
        assertThat(c2.getAvailableHoursBeforeDeadline()).isCloseTo(8.0, offset(0.001));
    }
}
