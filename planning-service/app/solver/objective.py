"""
Objective function implementing the lexicographic hierarchy from spec
Section 10:

    1. (hard constraints — not scored here)
    2. Maximize completed required work
    3. Protect earlier deadlines
    4. Protect high-priority activities
    5. Consider module credits (weighting only — never extra hours)
    6. Prefer contiguous study sessions
    7. Reduce unnecessary context switching

Implementation note (documented per spec Section 10's requirement to
explain any weighted-sum choice): CP-SAT doesn't have a native
"lexicographic maximize" primitive without multiple sequential solves, so
this uses a big-M weighted sum — each tier's coefficient
(app.config.LexicographicWeights) is several orders of magnitude larger
than the one below it. For realistic planning-period sizes (a handful of
activities, at most a few hundred 15-minute slots) this behaves as
lexicographic in practice: no combination of lower-tier gains can outweigh
one unit of a higher tier. It is an approximation, not a mathematical
guarantee, at extreme scale — true sequential lexicographic optimization
(solve tier 2, pin its optimal value, optimize tier 3 subject to that,
etc.) is noted as a future improvement in the README.
"""
from __future__ import annotations

from ortools.sat.python import cp_model

from app.config import PlanningConfig
from app.domain.activity import Activity
from app.solver.variables import AssignmentVariables, Slot
from app.solver.time_units import datetime_to_absolute_slot

# Credits are often fractional (e.g. 2.5); CP-SAT objective coefficients
# must be integers, so credits are scaled up before being multiplied by
# the tier weight, then implicitly scaled back out by the tier ordering.
CREDITS_SCALE = 10


def _are_adjacent(prev: Slot, curr: Slot) -> bool:
    return prev.date == curr.date and prev.end_time == curr.start_time


def _hours_until_deadline(activity: Activity, reference_dt) -> float | None:
    if activity.deadline is None:
        return None
    delta = activity.deadline - reference_dt
    return delta.total_seconds() / 3600.0


def build_objective_terms(
    model: cp_model.CpModel,
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
    period_start,
) -> list[cp_model.LinearExpr]:
    terms: list[cp_model.LinearExpr] = []
    weights = config.weights
    activities_by_id = {a.id: a for a in activities}

    # Deadline urgency is evaluated once per activity, anchored at the start
    # of the planning period (treated as "now" for this planning run — see
    # module docstring / README for why no separate wall-clock time is used).
    from datetime import datetime as _dt
    reference_dt = _dt.combine(period_start, _dt.min.time())

    # ---- Tiers 2-5: per-slot rewards, scaled by each activity's fixed factors ----
    for (activity_id, slot_index), var in assignment.x.items():
        activity = activities_by_id[activity_id]

        completion_reward = weights.completed_work

        urgency_score = config.urgency.score_for(_hours_until_deadline(activity, reference_dt))
        deadline_reward = weights.deadline_protection * urgency_score

        priority_reward = weights.priority * activity.priority

        credits_value = round((activity.credits or 0.0) * CREDITS_SCALE)
        credits_reward = weights.credits * credits_value

        terms.append(var * (completion_reward + deadline_reward + priority_reward + credits_reward))

    # ---- Tier 6: contiguity — penalize the number of separate session starts ----
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
            terms.append(start_var * (-1 * weights.contiguity))
            prev_slot = slot

    # ---- Tier 7: context switching — reward immediate continuation of the
    # SAME activity across adjacent slots (fewer switches between different
    # activities back-to-back). Proper AND-linearization per activity/pair.
    slots_by_index = {s.index: s for s in assignment.slots}
    for i in range(len(ordered_slots) - 1):
        prev_slot = ordered_slots[i]
        curr_slot = ordered_slots[i + 1]
        if not _are_adjacent(prev_slot, curr_slot):
            continue

        prev_activities = assignment.slot_activities.get(prev_slot.index, [])
        curr_activities = assignment.slot_activities.get(curr_slot.index, [])
        shared_activities = set(prev_activities) & set(curr_activities)

        for activity_id in shared_activities:
            prev_var = assignment.x[(activity_id, prev_slot.index)]
            curr_var = assignment.x[(activity_id, curr_slot.index)]
            continue_var = model.NewBoolVar(f"continue_{activity_id}_{prev_slot.index}")
            model.Add(continue_var <= prev_var)
            model.Add(continue_var <= curr_var)
            model.Add(continue_var >= prev_var + curr_var - 1)
            terms.append(continue_var * weights.context_switching)

    # ---- Tier 6 (distribution): daily-spread incentive.
    # For each calendar day that has at least one slot assigned to any
    # activity, reward the solution with ``daily_spread`` points.
    # This softly encourages the solver to spread workload across the
    # available days rather than cramming everything into the first day(s).
    # No fake work is created — the reward only fires when a real activity
    # slot is scheduled on that day.
    from collections import defaultdict
    day_vars: dict = defaultdict(list)
    for (activity_id, slot_index), var in assignment.x.items():
        slot = next((s for s in assignment.slots if s.index == slot_index), None)
        if slot is not None:
            day_vars[slot.date].append(var)

    for day, day_slot_vars in day_vars.items():
        if not day_slot_vars:
            continue
        day_active = model.NewBoolVar(f"day_active_{day}")
        # day_active = 1 iff at least one slot on this day is assigned.
        # Linearization: day_active <= sum(vars), day_active * N >= sum(vars)
        model.Add(sum(day_slot_vars) >= day_active)
        model.Add(day_active * len(day_slot_vars) >= sum(day_slot_vars))
        terms.append(day_active * weights.daily_spread)

    return terms


def apply_objective(
    model: cp_model.CpModel,
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
    period_start,
) -> None:
    terms = build_objective_terms(model, assignment, activities, config, period_start)
    if terms:
        model.Maximize(sum(terms))
