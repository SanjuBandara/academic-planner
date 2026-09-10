package com.academicplanner.planning.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of everything the planning pipeline needs to function.
 * Created once per planning request and passed into each sub-component.
 *
 * <p>Using an explicit context object keeps sub-component signatures simple
 * and makes the planning pipeline easy to test by substituting a fixed
 * {@code now} timestamp.
 */
public record PlanningContext(
        /** All schedulable candidates (assessments + standalone tasks), mutable list. */
        List<PlanningCandidate> candidates,

        /** Daily capacity map: LocalDate → available hours. */
        Map<LocalDate, Double> dailyHours,

        /**
         * Wall-clock reference time used for urgency/deadline calculations.
         * Injected as a parameter so unit tests can fix it to a known instant.
         */
        LocalDateTime now
) {}
