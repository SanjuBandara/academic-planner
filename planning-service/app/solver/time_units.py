"""
Single source of truth for converting between wall-clock date/time and the
discrete 15-minute planning units CP-SAT reasons about (spec Section 4).

Nothing else in the solver should do its own minutes-per-slot arithmetic —
import from here instead, so the granularity can be changed in one place.
"""
from __future__ import annotations

from datetime import date, datetime, time, timedelta


def time_of_day_to_slot(t: time, slot_minutes: int) -> int:
    """
    Maps a time-of-day to its slot number within a single day.
    e.g. with 15-minute slots: 08:00 -> 32, 08:15 -> 33 (spec Section 4 example).
    """
    minutes_since_midnight = t.hour * 60 + t.minute
    return minutes_since_midnight // slot_minutes


def slot_to_time_of_day(slot: int, slot_minutes: int) -> time:
    total_minutes = slot * slot_minutes
    return time(hour=(total_minutes // 60) % 24, minute=total_minutes % 60)


def datetime_to_absolute_slot(dt: datetime, period_start: date, slot_minutes: int) -> int:
    """
    Maps a full date+time to a single integer slot index counted from
    midnight of `period_start`. Used for deadline comparisons and for
    ordering slots across multiple days on one consistent integer axis.
    """
    days_since_start = (dt.date() - period_start).days
    slot_in_day = time_of_day_to_slot(dt.time(), slot_minutes)
    slots_per_day = (24 * 60) // slot_minutes
    return days_since_start * slots_per_day + slot_in_day


def absolute_slot_to_datetime(slot: int, period_start: date, slot_minutes: int) -> datetime:
    slots_per_day = (24 * 60) // slot_minutes
    day_offset, slot_in_day = divmod(slot, slots_per_day)
    day = period_start + timedelta(days=day_offset)
    return datetime.combine(day, slot_to_time_of_day(slot_in_day, slot_minutes))


def hours_to_units(hours: float, slot_minutes: int) -> int:
    """
    Converts a quantity of hours directly into whole time units.
    Per Phase 2 spec Section 7: `requiredUnits = remainingHours * (60 / slotMinutes)`.
    Rounds to the nearest unit rather than truncating, so small remainders
    (e.g. 1.9h) still get their fair share of scheduling capacity.
    """
    units_per_hour = 60 / slot_minutes
    return round(hours * units_per_hour)


def units_to_minutes(units: int, slot_minutes: int) -> int:
    return units * slot_minutes
