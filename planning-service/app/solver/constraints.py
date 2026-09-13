"""
Hard constraints (Section 6 of the spec) for the Phase 1 model.

Every function here takes the AssignmentVariables built in variables.py and
adds constraints to the underlying cp_model.CpModel in place. The solver is
never allowed to violate any of these.

Constraints NOT needing explicit code because they are structural:
    - Availability constraint: x[a, s] only exists for slots inside a
      declared availability window (see build_slots / build_assignment_variables).
    - Deadline constraint: x[a, s] is never created for slots after the
      activity's deadline (see build_assignment_variables).
    - Completed activity constraint: no variables are created for activities
      with remaining_work_units <= 0.
    - Valid session / plan-period constraint: every slot is, by
      construction, a fixed-size block strictly inside the requested
      planning period with positive duration.
"""
from __future__ import annotations

from ortools.sat.python import cp_model

from app.config import PlanningConfig
from app.domain.activity import Activity
from app.solver.variables import AssignmentVariables


def add_no_overlap_constraint(assignment: AssignmentVariables) -> None:
    """No two activities may occupy the same slot at the same time."""
    model = assignment.model
    for slot_index, activity_ids in assignment.slot_activities.items():
        vars_in_slot = [assignment.x[(aid, slot_index)] for aid in activity_ids]
        if vars_in_slot:
            model.Add(sum(vars_in_slot) <= 1)


def add_remaining_workload_constraint(
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> None:
    """
    An activity may never be assigned more scheduled time than its
    remaining workload converts to. This is what makes the planner schedule
    REMAINING work only, never the task "from zero" (Section 3).
    """
    model = assignment.model
    slot_minutes = config.time.slot_minutes

    for activity in activities:
        slot_ids = assignment.activity_slots.get(activity.id, [])
        if not slot_ids:
            continue

        needed_minutes = config.workload.units_to_minutes(
            activity.remaining_work_units,
            activity.productivity_units_per_hour,
        )
        max_slots = needed_minutes // slot_minutes
        # Round up by one slot if there's a meaningful remainder, so a small
        # amount of remaining work still gets at least one slot of capacity.
        if needed_minutes % slot_minutes >= slot_minutes / 2:
            max_slots += 1

        vars_for_activity = [assignment.x[(activity.id, s)] for s in slot_ids]
        model.Add(sum(vars_for_activity) <= max_slots)


def add_study_capacity_constraint(assignment: AssignmentVariables) -> None:
    """
    Total scheduled time cannot exceed total available study time.
    This falls out of the no-overlap constraint plus the fact that x[a,s]
    only exists inside availability windows, but we add it explicitly as a
    global sum for clarity/explainability, per the spec's guidance to
    favor explicit, readable constraints in Phase 1.
    """
    model = assignment.model
    all_vars = list(assignment.x.values())
    total_slots = len(assignment.slots)
    if all_vars:
        model.Add(sum(all_vars) <= total_slots)


def apply_all_hard_constraints(
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> None:
    add_no_overlap_constraint(assignment)
    add_remaining_workload_constraint(assignment, activities, config)
    add_study_capacity_constraint(assignment)
