package com.academicplanner.planning.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the user's declared daily availability:
 * 1. availableHours (required primary declared capacity)
 * 2. timeSlots (optional list of exact time windows)
 */
public record DailyAvailability(
        LocalDate date,
        double availableHours,
        List<TimeSlot> timeSlots
) {
    public DailyAvailability {
        if (timeSlots == null) {
            timeSlots = Collections.emptyList();
        }
    }

    public static DailyAvailability ofHoursOnly(LocalDate date, double hours) {
        return new DailyAvailability(date, hours, Collections.emptyList());
    }

    public boolean hasTimeSlots() {
        return timeSlots != null && !timeSlots.isEmpty();
    }

    public double totalSlotHours() {
        if (!hasTimeSlots()) return 0.0;
        return timeSlots.stream().mapToDouble(TimeSlot::durationHours).sum();
    }

    /**
     * Schedulable capacity for this day.
     * When time slots are provided, capacity is capped by availableHours and totalSlotHours.
     */
    public double effectiveSchedulableHours() {
        if (hasTimeSlots()) {
            return Math.min(availableHours, totalSlotHours());
        }
        return availableHours;
    }
}
