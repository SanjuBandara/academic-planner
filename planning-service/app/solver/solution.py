"""
Solution extraction (spec Section 13):

    CP-SAT variables -> selected time units -> group consecutive units ->
    study sessions

Kept separate from planner.py so the "turn a solved model into
sessions/statistics" concern doesn't blend with the "build and solve the
model" concern.
"""
from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from app.config import PlanningConfig
from app.domain.activity import Activity
from app.domain.session import ScheduledSession
from app.solver.variables import AssignmentVariables, Slot


def _merge_slots_into_sessions(activity_id: str, assigned_slots: list[Slot]) -> list[ScheduledSession]:
    """
    Groups consecutive assigned slots for one activity into sessions.
    Only splits when there's a real scheduling boundary: availability ends,
    or the next assigned slot doesn't immediately follow the previous one
    in wall-clock time (spec Section 13).
    """
    sessions: list[ScheduledSession] = []
    if not assigned_slots:
        return sessions

    ordered = sorted(assigned_slots, key=lambda s: s.index)
    block_start = ordered[0]
    block_end = ordered[0]

    def flush():
        sessions.append(ScheduledSession(
            activity_id=activity_id,
            date=block_start.date,
            start_time=block_start.start_time,
            end_time=block_end.end_time,
        ))

    for slot in ordered[1:]:
        contiguous = slot.date == block_end.date and slot.start_time == block_end.end_time
        if contiguous:
            block_end = slot
        else:
            flush()
            block_start = slot
            block_end = slot

    flush()
    return sessions


@dataclass
class ExtractedSolution:
    sessions: list[ScheduledSession]
    completed_required_minutes: int
    unfinished_required_minutes: int
    unfinished_by_activity: dict[str, float]  # activity_id -> unfinished hours


def extract_solution(
    solver: cp_model.CpSolver,
    assignment: AssignmentVariables,
    activities: list[Activity],
    config: PlanningConfig,
) -> ExtractedSolution:
    slots_by_index = {s.index: s for s in assignment.slots}
    slot_minutes = config.time.slot_minutes

    all_sessions: list[ScheduledSession] = []
    completed_minutes = 0
    unfinished_minutes = 0
    unfinished_by_activity: dict[str, float] = {}

    for activity in activities:
        assigned_slots = [
            slots_by_index[s_idx]
            for s_idx in assignment.activity_slots.get(activity.id, [])
            if solver.Value(assignment.x[(activity.id, s_idx)]) == 1
        ]
        all_sessions.extend(_merge_slots_into_sessions(activity.id, assigned_slots))

        required_units = activity.required_units(slot_minutes)
        planned_units = len(assigned_slots)
        completed_minutes += planned_units * slot_minutes

        if planned_units < required_units:
            shortfall_minutes = (required_units - planned_units) * slot_minutes
            unfinished_minutes += shortfall_minutes
            unfinished_by_activity[activity.id] = shortfall_minutes / 60.0

    all_sessions.sort(key=lambda s: (s.date, s.start_time))

    return ExtractedSolution(
        sessions=all_sessions,
        completed_required_minutes=completed_minutes,
        unfinished_required_minutes=unfinished_minutes,
        unfinished_by_activity=unfinished_by_activity,
    )
