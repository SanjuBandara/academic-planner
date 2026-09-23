"""
CP-SAT variable layer (spec Section 7).

Uses the discrete-slot assignment formulation: one Boolean variable
x[activity_id, slot_index] per (activity, slot) pair where the slot lies
inside a declared availability window AND on/before the activity's
deadline. x[a, s] == 1 means "activity a is being studied during slot s".

All date/time <-> slot arithmetic goes through app.solver.time_units —
nothing here computes minutes-per-slot itself (spec Section 4).
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta

from ortools.sat.python import cp_model

from app.config import PlanningConfig
from app.domain.activity import Activity
from app.domain.availability import AvailabilityWindow
from app.solver.time_units import datetime_to_absolute_slot, slot_to_time_of_day


@dataclass(frozen=True)
class Slot:
    index: int          # absolute slot index (see time_units.datetime_to_absolute_slot)
    date: date
    start_time: object   # datetime.time
    end_time: object      # datetime.time


def build_slots(availability: list[AvailabilityWindow], config: PlanningConfig, period_start: date) -> list[Slot]:
    """Expands every availability window into fixed-size Slot objects, in absolute-slot order."""
    slot_minutes = config.time.slot_minutes
    slots: list[Slot] = []

    ordered = sorted(availability, key=lambda w: (w.date, w.start_time))

    for window in ordered:
        window_start_dt = datetime.combine(window.date, window.start_time)
        window_end_dt = datetime.combine(window.date, window.end_time)
        cursor = window_start_dt

        while cursor + _delta(slot_minutes) <= window_end_dt:
            abs_index = datetime_to_absolute_slot(cursor, period_start, slot_minutes)
            slot_end = cursor + _delta(slot_minutes)
            slots.append(Slot(
                index=abs_index,
                date=cursor.date(),
                start_time=cursor.time(),
                end_time=slot_end.time(),
            ))
            cursor = slot_end

    return slots


def _delta(minutes: int):
    from datetime import timedelta
    return timedelta(minutes=minutes)


@dataclass
class AssignmentVariables:
    model: cp_model.CpModel
    slots: list[Slot]
    x: dict[tuple[str, int], cp_model.IntVar]
    activity_slots: dict[str, list[int]]
    slot_activities: dict[int, list[str]]


def build_assignment_variables(
    model: cp_model.CpModel,
    activities: list[Activity],
    slots: list[Slot],
    config: PlanningConfig,
    period_start: date,
) -> AssignmentVariables:
    x: dict[tuple[str, int], cp_model.IntVar] = {}
    activity_slots: dict[str, list[int]] = {a.id: [] for a in activities}
    slot_activities: dict[int, list[str]] = {s.index: [] for s in slots}
    slot_minutes = config.time.slot_minutes

    for activity in activities:
        if activity.remaining_hours <= 0:
            # Completed-activity constraint (Section 8): no variables at all.
            continue

        for slot in slots:
            # Deadline constraint (Part 3 fix): the ENTIRE slot must finish
            # on or before the deadline.  We compare the slot's end datetime
            # to the deadline datetime so that a session can never extend
            # past the deadline boundary.
            if activity.deadline is not None:
                slot_end_dt = datetime.combine(slot.date, slot.end_time)
                if slot_end_dt > activity.deadline:
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
