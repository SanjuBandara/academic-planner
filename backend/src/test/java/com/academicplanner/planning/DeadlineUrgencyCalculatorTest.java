package com.academicplanner.planning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeadlineUrgencyCalculator")
class DeadlineUrgencyCalculatorTest {

    private DeadlineUrgencyCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DeadlineUrgencyCalculator();
    }

    @Test
    @DisplayName("Returns correct bonus based on days remaining")
    void urgencyBonusFromDays() {
        assertThat(calculator.urgencyBonusFromDays(5)).isEqualTo(0);
        assertThat(calculator.urgencyBonusFromDays(4)).isEqualTo(0);
        assertThat(calculator.urgencyBonusFromDays(3)).isEqualTo(1);
        assertThat(calculator.urgencyBonusFromDays(2)).isEqualTo(2);
        assertThat(calculator.urgencyBonusFromDays(1)).isEqualTo(3);
        assertThat(calculator.urgencyBonusFromDays(0)).isEqualTo(4);
        assertThat(calculator.urgencyBonusFromDays(-2)).isEqualTo(4); // overdue
    }

    @Test
    @DisplayName("Returns 0 for null deadline")
    void nullDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 10, 0);
        assertThat(calculator.urgencyBonus(null, now)).isEqualTo(0);
    }

    @Test
    @DisplayName("Returns 4 for overdue deadline")
    void overdueDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 10, 0);
        LocalDateTime past = now.minusHours(2);
        assertThat(calculator.urgencyBonus(past, now)).isEqualTo(4);
    }

    @Test
    @DisplayName("Calculates available hours before deadline")
    void availableHoursBeforeDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 8, 0);
        LocalDate day1 = LocalDate.of(2026, 9, 10);
        LocalDate day2 = LocalDate.of(2026, 9, 11);
        LocalDate day3 = LocalDate.of(2026, 9, 12);

        Map<LocalDate, Double> dailyHours = new LinkedHashMap<>();
        dailyHours.put(day1, 4.0);
        dailyHours.put(day2, 3.0);
        dailyHours.put(day3, 5.0);

        // Deadline on day2
        LocalDateTime deadlineDay2 = LocalDateTime.of(2026, 9, 11, 23, 59);
        double available = calculator.availableHoursBeforeDeadline(deadlineDay2, dailyHours, now);
        assertThat(available).isEqualTo(7.0); // 4 + 3

        // Null deadline gives sum of all days
        double allAvailable = calculator.availableHoursBeforeDeadline(null, dailyHours, now);
        assertThat(allAvailable).isEqualTo(12.0); // 4 + 3 + 5
    }
}
