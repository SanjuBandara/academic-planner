"""
CP-SAT variable layer.

Phase 1 uses a discrete-slot assignment formulation rather than one interval
variable per entity, per the spec's guidance to reason carefully about time
representation:

    - The planning period + availability windows are discretized into
      fixed-size time slots (app.config.TimeGranularity, default 15 min).
    - One Boolean variable x[activity_id, slot_index] is created for every
      (activity, slot) pair where the slot lies inside a declared
      availability window AND on/before the activity's deadline.
    - x[a, s] == 1 means "activity a is being studied during slot s".

This keeps the model explainable: no-overlap, capacity, and deadline
constraints all reduce to simple linear sums over these booleans. Sessions
(contiguous blocks) are reconstructed from the solution afterwards.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta

from ortools.sat.python import cp_model

from app.config import PlanningConfig
from app.domain.activity import Activity
from app.domain.availability import AvailabilityWindow


@dataclass(frozen=True)
class Slot:
    index: int
    date: date
    start_time: time
    end_time: time

    def start_datetime(self) -> datetime:
        return datetime.combine(self.date, self.start_time)


def build_slots(availability: list[AvailabilityWindow], config: PlanningConfig) -> list[Slot]:
    """Expands every availability window into fixed-size Slot objects, in order."""
    slot_minutes = config.time.slot_minutes
    slots: list[Slot] = []
    index = 0

    # Sort windows chronologically so slot indices increase with time —
    # this makes deadline comparisons a simple index comparison later.
    ordered = sorted(availability, key=lambda w: (w.date, w.start_time))

    for window in ordered:
        cursor = datetime.combine(window.date, window.start_time)
        end = datetime.combine(window.date, window.end_time)
        while cursor + timedelta(minutes=slot_minutes) <= end:
            slot_end = cursor + timedelta(minutes=slot_minutes)
            slots.append(Slot(
                index=index,
                date=window.date,
                start_time=cursor.time(),
                end_time=slot_end.time(),
            ))
            index += 1
            cursor = slot_end

    return slots


@dataclass
class AssignmentVariables:
    model: cp_model.CpModel
    slots: list[Slot]
    # (activity_id, slot_index) -> BoolVar, only present when the slot is a
    # legal candidate for that activity (inside availability, before deadline).
    x: dict[tuple[str, int], cp_model.IntVar]
    # activity_id -> list of slot indices legal for that activity
    activity_slots: dict[str, list[int]]
    # slot_index -> list of activity_ids that could occupy it
    slot_activities: dict[int, list[str]]


def build_assignment_variables(
    model: cp_model.CpModel,
    activities: list[Activity],
    slots: list[Slot],
) -> AssignmentVariables:
    x: dict[tuple[str, int], cp_model.IntVar] = {}
    activity_slots: dict[str, list[int]] = {a.id: [] for a in activities}
    slot_activities: dict[int, list[str]] = {s.index: [] for s in slots}

    for activity in activities:
        if activity.remaining_work_units <= 0:
            # Completed-activity constraint (Section 6): no variables at all
            # are created for activities with no remaining work, so the
            # solver structurally cannot schedule them.
            continue

        deadline_dt = activity.deadline

        for slot in slots:
            if deadline_dt is not None and slot.start_datetime() > deadline_dt:
                # Deadline constraint (Section 6): slot starts after the
                # deadline -> not a legal candidate for this activity.
                continue

            var = model.NewBoolVar(f"x_{activity.id}_{slot.index}")
            x[(activity.id, slot.index)] = var
            activity_slots[activity.id].append(slot.index)
            slot_activities[slot.index].append(activity.id)

    return AssignmentVariables(
        model=model,
        slots=slots,
        x=x,
        activity_slots=activity_slots,
        slot_activities=slot_activities,
    )
