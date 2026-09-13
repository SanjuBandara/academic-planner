from __future__ import annotations

from datetime import date, datetime, time

from app.solver.time_units import (
    absolute_slot_to_datetime,
    datetime_to_absolute_slot,
    hours_to_units,
    slot_to_time_of_day,
    time_of_day_to_slot,
    units_to_minutes,
)


def test_time_of_day_to_slot_matches_spec_example():
    # spec Section 4: 08:00 -> slot 32 with 15-minute units
    assert time_of_day_to_slot(time(8, 0), slot_minutes=15) == 32
    assert time_of_day_to_slot(time(8, 15), slot_minutes=15) == 33
    assert time_of_day_to_slot(time(0, 0), slot_minutes=15) == 0


def test_slot_to_time_of_day_round_trips():
    for minute in (0, 15, 30, 45):
        t = time(9, minute)
        slot = time_of_day_to_slot(t, slot_minutes=15)
        assert slot_to_time_of_day(slot, slot_minutes=15) == t


def test_absolute_slot_round_trip_across_days():
    period_start = date(2026, 9, 14)
    dt = datetime(2026, 9, 16, 18, 30)
    slot = datetime_to_absolute_slot(dt, period_start, slot_minutes=15)
    back = absolute_slot_to_datetime(slot, period_start, slot_minutes=15)
    assert back == dt


def test_absolute_slot_increases_with_later_days():
    period_start = date(2026, 9, 14)
    day1 = datetime_to_absolute_slot(datetime(2026, 9, 14, 8, 0), period_start, 15)
    day2 = datetime_to_absolute_slot(datetime(2026, 9, 15, 8, 0), period_start, 15)
    assert day2 > day1


def test_hours_to_units_matches_spec_example():
    # spec Section 4: 60 minutes = 4 units, 90 minutes = 6 units, 120 = 8
    assert hours_to_units(1.0, slot_minutes=15) == 4
    assert hours_to_units(1.5, slot_minutes=15) == 6
    assert hours_to_units(2.0, slot_minutes=15) == 8


def test_hours_to_units_rounds_fractional_remainders():
    assert hours_to_units(1.9, slot_minutes=15) == round(1.9 * 4)


def test_units_to_minutes():
    assert units_to_minutes(4, slot_minutes=15) == 60
    assert units_to_minutes(1, slot_minutes=15) == 15
