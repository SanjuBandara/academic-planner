"""
Hard constraints (spec Section 8) — the solver may never violate any of these.

Structural (no code needed, enforced by variables.py):
    A. Availability   — x[a,s] only exists for slots inside declared windows
    D. Deadline        — x[a,s] never created for slots after the deadline
    (completed)         — no variables created for remaining_hours <= 0
    F. Session validity/plan-period — every slot is a fixed-size block
                          strictly inside the requested planning period

Explicit constraints below:
    B. No overlapping study
    C. Required work upper bound
    E. Planning-period / study-capacity (global sanity check)
"""
from __future__ import annotations

from ortools.sat.python import cp_model

from app.config import PlanningConfig
from app.domain.activity import Activity
from app.solver.variables import AssignmentVariables


def add_no_overlap_constraint(assignment: AssignmentVariables) -> None:
    """Constraint B: at most one activity per time slot."""
    model = assignment.model
    for slot_index, activity_ids in assignment.slot_activities.items():
        vars_in_slot = [assignment.x[(aid, slot_index)] for aid in activity_ids]
        if vars_in_slot:
            model.Add(sum(vars_in_slot) <= 1)


def add_required_work_upper_bound(
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> None:
    """
    Constraint C: an activity may never be assigned more scheduled units
    than `remainingHours` converts to. This also implements the "schedule
    REMAINING work, not from zero" requirement (spec Section 6).
    """
    model = assignment.model

    for activity in activities:
        slot_ids = assignment.activity_slots.get(activity.id, [])
        if not slot_ids:
            continue

        required_units = activity.required_units(config.time.slot_minutes)
        vars_for_activity = [assignment.x[(activity.id, s)] for s in slot_ids]
        model.Add(sum(vars_for_activity) <= required_units)


def add_study_capacity_constraint(assignment: AssignmentVariables) -> None:
    """
    Constraint E (global sanity check): total scheduled units can never
    exceed the total number of available units. Already implied by
    no-overlap + availability-only variables, but kept explicit for
    readability/explainability per the spec's Phase 1 guidance (still
    honored in Phase 2).
    """
    model = assignment.model
    all_vars = list(assignment.x.values())
    total_slots = len(assignment.slots)
    if all_vars:
        model.Add(sum(all_vars) <= total_slots)


def add_max_session_length_constraint(
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> None:
    """
    Part 4: Hard constraint — no activity may have more than
    ``max_session_slots`` consecutive assigned slots on the same day,
    preventing continuous study sessions longer than
    ``config.session_prefs.max_session_minutes``.

    Implementation: sliding window of size (max_slots + 1) across each
    activity's slots on each day. If all max_slots+1 slots in a window
    were assigned the sum would be max_slots+1, which exceeds the cap.
    We forbid that: sum(window) <= max_slots.
    """
    model = assignment.model
    slot_minutes = config.time.slot_minutes
    max_slots = config.session_prefs.max_session_minutes // slot_minutes
    if max_slots <= 0:
        return

    slots_by_index = {s.index: s for s in assignment.slots}
    ordered_slots = sorted(assignment.slots, key=lambda s: s.index)

    for activity in activities:
        slot_ids = assignment.activity_slots.get(activity.id, [])
        if not slot_ids:
            continue

        slot_id_set = set(slot_ids)
        # Build a per-day ordered list of the activity's available slots
        from collections import defaultdict
        by_day: dict = defaultdict(list)
        for slot in ordered_slots:
            if slot.index in slot_id_set:
                by_day[slot.date].append(slot)

        for day_slots in by_day.values():
            window_size = max_slots + 1
            if len(day_slots) < window_size:
                continue
            for i in range(len(day_slots) - max_slots):
                window = day_slots[i: i + window_size]
                # Only apply the constraint if all slots in the window are
                # actually consecutive (no gap in time).
                all_consecutive = all(
                    window[j].end_time == window[j + 1].start_time
                    for j in range(len(window) - 1)
                )
                if not all_consecutive:
                    continue
                vars_in_window = [assignment.x[(activity.id, s.index)] for s in window]
                model.Add(sum(vars_in_window) <= max_slots)


def apply_all_hard_constraints(
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> None:
    add_no_overlap_constraint(assignment)
    add_required_work_upper_bound(assignment, activities, config)
    add_study_capacity_constraint(assignment)
    add_max_session_length_constraint(assignment, activities, config)
