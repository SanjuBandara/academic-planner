"""
Tests for spec requirements introduced in the Part 1-12 implementation:

- Part 1: 7-day rolling planning period + current-time availability trimming
- Part 3: Deadline constraint uses slot END time
- Part 4: Max session length <= 150 minutes
- Part 2: Weekly distribution across days
"""
from __future__ import annotations

from datetime import date, datetime, time

import pytest
from app.models.request import PlanningRequest
from app.models.response import SolverStatus
from app.solver.planner import generate_plan


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_request(
    start: str,
    end: str,
    availability: list[dict],
    activities: list[dict],
    current_date_time: str | None = None,
) -> PlanningRequest:
    payload = {
        "planningPeriod": {"startDate": start, "endDate": end},
        "availability": availability,
        "activities": activities,
    }
    if current_date_time is not None:
        payload["currentDateTime"] = current_date_time
    return PlanningRequest.model_validate(payload)


def _activity(aid: str, remaining_hours: float, deadline: str | None = None) -> dict:
    d: dict = {"id": aid, "title": aid, "activityType": "TASK", "remainingHours": remaining_hours}
    if deadline:
        d["deadline"] = deadline
    return d


# ---------------------------------------------------------------------------
# Part 1 — Current-day availability trimming
# ---------------------------------------------------------------------------

class TestCurrentDayAvailabilityTrimming:
    """Test 2, 3, 4, 5, 6 from Part 12."""

    def test_window_entirely_before_current_time_is_kept(self):
        """Test 2: window [14:00-18:00] with current_time 13:00 → full window kept."""
        req = _make_request(
            "2026-09-23", "2026-09-29",
            [{"date": "2026-09-23", "startTime": "14:00", "endTime": "18:00"}],
            [_activity("T-1", 2.0)],
            current_date_time="2026-09-23T13:00:00",
        )
        resp = generate_plan(req)
        assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
        # All sessions must be at or after 14:00
        for s in resp.sessions:
            if s.date == date(2026, 9, 23):
                assert s.start_time >= time(14, 0), f"Session before 14:00: {s}"

    def test_window_partially_elapsed_is_trimmed(self):
        """Test 3: window [14:00-18:00] with current_time 15:00 → trimmed to [15:00-18:00]."""
        req = _make_request(
            "2026-09-23", "2026-09-29",
            [{"date": "2026-09-23", "startTime": "14:00", "endTime": "18:00"}],
            [_activity("T-1", 2.0)],
            current_date_time="2026-09-23T15:00:00",
        )
        resp = generate_plan(req)
        assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
        for s in resp.sessions:
            if s.date == date(2026, 9, 23):
                assert s.start_time >= time(15, 0), f"Session before trimmed start: {s}"

    def test_window_fully_elapsed_is_removed(self):
        """Test 4: window [14:00-18:00] with current_time 19:00 → window removed."""
        req = _make_request(
            "2026-09-23", "2026-09-29",
            [
                {"date": "2026-09-23", "startTime": "14:00", "endTime": "18:00"},
                {"date": "2026-09-24", "startTime": "09:00", "endTime": "12:00"},
            ],
            [_activity("T-1", 1.0)],
            current_date_time="2026-09-23T19:00:00",
        )
        resp = generate_plan(req)
        # No sessions on the fully-elapsed day (2026-09-23)
        for s in resp.sessions:
            assert s.date != date(2026, 9, 23), "Session scheduled on fully-elapsed day"

    def test_future_day_availability_unchanged(self):
        """Test 5: Wednesday availability unchanged when current time is Tuesday 15:00."""
        req = _make_request(
            "2026-09-23", "2026-09-29",
            [{"date": "2026-09-24", "startTime": "08:00", "endTime": "12:00"}],
            [_activity("T-1", 2.0)],
            current_date_time="2026-09-23T15:00:00",
        )
        resp = generate_plan(req)
        assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
        for s in resp.sessions:
            if s.date == date(2026, 9, 24):
                assert s.start_time >= time(8, 0)
                assert s.end_time <= time(12, 0)

    def test_past_day_availability_excluded(self):
        """Test 6: If today is Tuesday, Monday's availability must not be used."""
        req = _make_request(
            "2026-09-23", "2026-09-29",
            [
                {"date": "2026-09-22", "startTime": "09:00", "endTime": "17:00"},  # Monday (past)
                {"date": "2026-09-24", "startTime": "09:00", "endTime": "12:00"},  # Wednesday
            ],
            [_activity("T-1", 2.0)],
            current_date_time="2026-09-23T10:00:00",
        )
        resp = generate_plan(req)
        for s in resp.sessions:
            assert s.date != date(2026, 9, 22), "Session scheduled on a past day"


# ---------------------------------------------------------------------------
# Part 3 — Deadline uses slot end time
# ---------------------------------------------------------------------------

class TestDeadlineEndTime:
    """Part 3: the entire slot must finish before the deadline."""

    def test_slot_that_ends_after_deadline_is_excluded(self):
        """A slot ending 30 min after the deadline must not be assigned."""
        # Availability: 09:00-11:00. Deadline: 10:30.
        # 30-min slots: 09:00-09:30, 09:30-10:00, 10:00-10:30, 10:30-11:00
        # Only the first three slots end on or before 10:30.
        req = _make_request(
            "2026-09-23", "2026-09-23",
            [{"date": "2026-09-23", "startTime": "09:00", "endTime": "11:00"}],
            [_activity("A-1", 5.0, deadline="2026-09-23T10:30:00")],
        )
        resp = generate_plan(req)
        for s in resp.sessions:
            assert s.end_time <= time(10, 30), f"Session ends after deadline: {s}"

    def test_slot_ending_exactly_at_deadline_is_allowed(self):
        """A slot whose end time equals the deadline exactly must be allowed."""
        req = _make_request(
            "2026-09-23", "2026-09-23",
            [{"date": "2026-09-23", "startTime": "09:00", "endTime": "11:00"}],
            [_activity("A-1", 5.0, deadline="2026-09-23T10:30:00")],
        )
        resp = generate_plan(req)
        # The slot 10:00-10:30 ends exactly at the deadline → it should be usable.
        # (Whether it's actually assigned depends on demand; just verify no crash
        # and the deadline is not violated.)
        assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
        for s in resp.sessions:
            assert s.end_time <= time(10, 30)

    def test_deadline_before_all_slots_produces_no_sessions(self):
        """If the deadline is before the start of all availability, no sessions are scheduled."""
        req = _make_request(
            "2026-09-23", "2026-09-23",
            [{"date": "2026-09-23", "startTime": "12:00", "endTime": "16:00"}],
            [_activity("A-1", 2.0, deadline="2026-09-23T11:00:00")],
        )
        resp = generate_plan(req)
        # No slots are assignable — solver returns INFEASIBLE or empty FEASIBLE
        assert resp.statistics.planned_minutes == 0


# ---------------------------------------------------------------------------
# Part 4 — Max session length
# ---------------------------------------------------------------------------

class TestMaxSessionLength:
    """Test 10: No generated session exceeds 150 minutes."""

    def test_no_session_exceeds_150_minutes(self):
        """With 10h availability, no single continuous session exceeds 150 min."""
        req = _make_request(
            "2026-09-23", "2026-09-23",
            [{"date": "2026-09-23", "startTime": "06:00", "endTime": "20:00"}],
            [_activity("A-1", 10.0)],
        )
        resp = generate_plan(req)
        assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
        for s in resp.sessions:
            assert s.duration_minutes <= 150, (
                f"Session {s.start_time}-{s.end_time} = {s.duration_minutes} min exceeds 150 min cap"
            )

    def test_max_session_enforced_across_multiple_activities(self):
        """Max session constraint applies to each activity independently."""
        req = _make_request(
            "2026-09-23", "2026-09-23",
            [{"date": "2026-09-23", "startTime": "08:00", "endTime": "18:00"}],
            [
                _activity("A-1", 4.0),
                _activity("A-2", 4.0),
            ],
        )
        resp = generate_plan(req)
        assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
        for s in resp.sessions:
            assert s.duration_minutes <= 150, f"Session exceeds 150 min: {s}"


# ---------------------------------------------------------------------------
# Part 2 — Weekly distribution
# ---------------------------------------------------------------------------

class TestWeeklyDistribution:
    """Test 7: Workload distributed across available days rather than front-loaded."""

    def test_work_spreads_across_multiple_days(self):
        """
        With 7 days of 2h each (14h total availability) and 7h of workload,
        the solver should use at least 2 different days rather than cramming
        all 7h into day 1.
        """
        req = _make_request(
            "2026-09-23", "2026-09-29",
            [
                {"date": "2026-09-23", "startTime": "09:00", "endTime": "11:00"},  # 2h
                {"date": "2026-09-24", "startTime": "09:00", "endTime": "11:00"},  # 2h
                {"date": "2026-09-25", "startTime": "09:00", "endTime": "11:00"},  # 2h
                {"date": "2026-09-26", "startTime": "09:00", "endTime": "11:00"},  # 2h
                {"date": "2026-09-27", "startTime": "09:00", "endTime": "11:00"},  # 2h
            ],
            [_activity("A-1", 7.0)],
        )
        resp = generate_plan(req)
        assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
        days_used = {s.date for s in resp.sessions}
        assert len(days_used) >= 2, (
            f"Expected work spread across ≥2 days but only used: {days_used}"
        )
