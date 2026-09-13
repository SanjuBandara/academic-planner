"""
Objective function (Section 8, scoped down to Section 13's Phase 1 subset):

    1. maximize high-priority work
    2. maximize total completed workload
    3. minimize unused available time
    4. minimize fragmentation

Kept as small, separately-computed terms summed at the end so more terms
(module balance, preferred study periods, etc.) can be added in later
phases without touching this structure. All coefficients come from
app.config.ObjectiveWeights / ImportanceWeights — nothing here is a bare
magic number.
"""
from __future__ import annotations

from ortools.sat.python import cp_model

from app.config import PlanningConfig
from app.domain.activity import Activity
from app.solver.variables import AssignmentVariables, Slot


def _are_adjacent(prev: Slot, curr: Slot) -> bool:
    """True if `curr` starts exactly when `prev` ends (no gap, same day)."""
    return prev.date == curr.date and prev.end_time == curr.start_time


def build_objective_terms(
    model: cp_model.CpModel,
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> list[cp_model.LinearExpr]:
    terms: list[cp_model.LinearExpr] = []
    slot_minutes = config.time.slot_minutes
    obj = config.objective

    activities_by_id = {a.id: a for a in activities}
    slots_by_index = {s.index: s for s in assignment.slots}

    # --- Terms 1 & 2: completed work + high-priority work -----------------
    # Every scheduled slot earns a base "work completed" reward, plus an
    # importance-scaled bonus so higher-priority activities are preferred
    # when the solver must choose between competing uses of limited time.
    max_importance_weight = max(config.importance.weights.values())

    for (activity_id, slot_index), var in assignment.x.items():
        activity = activities_by_id[activity_id]
        importance_weight = config.importance.value_for(activity.effective_importance.value)

        base_reward = obj.completed_work_minute_reward * slot_minutes
        priority_reward = (
            obj.high_priority_minute_reward
            * slot_minutes
            * importance_weight
            // max_importance_weight
        )
        terms.append(var * (base_reward + priority_reward))

    # --- Term 3: minimize unused available time ----------------------------
    # For every slot that at least one activity could occupy, "used" equals
    # the sum of its candidate assignment vars (which the no-overlap
    # constraint already keeps at 0 or 1). Rewarding `used` directly is
    # mathematically the same as penalizing (1 - used) up to a constant, so
    # we add a positive reward for used slots rather than a separate
    # penalty term — simpler and avoids introducing extra variables.
    for slot_index, activity_ids in assignment.slot_activities.items():
        candidate_vars = [assignment.x[(aid, slot_index)] for aid in activity_ids]
        if not candidate_vars:
            continue
        terms.append(sum(candidate_vars) * obj.unused_availability_penalty * slot_minutes)

    # --- Term 4: minimize fragmentation ------------------------------------
    # A "start" happens whenever an activity begins occupying a slot that
    # does not immediately continue its own previous slot. Fewer starts =
    # longer, less fragmented sessions. We only lower-bound `start`
    # (start >= x[s] - x[s-1]); because it's purely penalized, the solver
    # will never set it higher than the true minimum.
    ordered_slots = sorted(assignment.slots, key=lambda s: s.index)
    for activity in activities:
        slot_ids = assignment.activity_slots.get(activity.id, [])
        if not slot_ids:
            continue
        slot_id_set = set(slot_ids)
        prev_slot: Slot | None = None
        for slot in ordered_slots:
            if slot.index not in slot_id_set:
                prev_slot = None
                continue
            curr_var = assignment.x[(activity.id, slot.index)]
            start_var = model.NewBoolVar(f"start_{activity.id}_{slot.index}")
            if prev_slot is not None and _are_adjacent(prev_slot, slot):
                prev_var = assignment.x[(activity.id, prev_slot.index)]
                model.Add(start_var >= curr_var - prev_var)
            else:
                model.Add(start_var >= curr_var)
            terms.append(start_var * (-1 * obj.fragmentation_penalty_per_session))
            prev_slot = slot

    return terms


def apply_objective(
    model: cp_model.CpModel,
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> None:
    terms = build_objective_terms(model, assignment, activities, config)
    if terms:
        model.Maximize(sum(terms))
