from __future__ import annotations

from datetime import date

from app.models.request import PlanningRequest
from app.models.response import SolverStatus
from app.solver.planner import generate_plan


def _activity(id_, remaining_hours, deadline=None, priority=3, credits=None, activity_type="TASK"):
    a = {
        "id": id_,
        "title": id_,
        "activityType": activity_type,
        "remainingHours": remaining_hours,
        "priority": priority,
    }
    if deadline:
        a["deadline"] = deadline
    if credits is not None:
        a["credits"] = credits
    return a


def _request(start_date, end_date, availability, activities):
    return PlanningRequest.model_validate({
        "planningPeriod": {"startDate": start_date, "endDate": end_date},
        "availability": availability,
        "activities": activities,
    })


def test_enough_availability_schedules_all_required_work():
    # Available = 20h, Required = 10h
    availability = [{"date": "2026-09-14", "startTime": "06:00", "endTime": "20:00"}]  # 14h
    availability.append({"date": "2026-09-15", "startTime": "06:00", "endTime": "12:00"})  # +6h = 20h
    activities = [_activity("A-1", remaining_hours=10)]
    req = _request("2026-09-14", "2026-09-15", availability, activities)

    resp = generate_plan(req)

    assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
    assert resp.statistics.unfinished_required_minutes == 0
    assert resp.statistics.completed_required_minutes == 10 * 60


def test_insufficient_availability_schedules_partial_work_no_invalid_sessions():
    # Available = 10h, Required = 20h
    availability = [{"date": "2026-09-14", "startTime": "06:00", "endTime": "16:00"}]  # 10h
    activities = [_activity("A-1", remaining_hours=20)]
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
    assert resp.statistics.completed_required_minutes == pytest_approx_minutes(10 * 60)
    assert resp.statistics.unfinished_required_minutes > 0
    assert len(resp.warnings) >= 1
    # every session must have a positive duration and lie within the day
    for s in resp.sessions:
        assert s.duration_minutes > 0


def pytest_approx_minutes(expected, tolerance=15):
    class _Approx:
        def __eq__(self, other):
            return abs(other - expected) <= tolerance
    return _Approx()


def test_no_session_occurs_in_unavailable_gap():
    # 08:00-12:00 and 18:00-20:00 available; nothing between 12:00 and 18:00.
    availability = [
        {"date": "2026-09-14", "startTime": "08:00", "endTime": "12:00"},
        {"date": "2026-09-14", "startTime": "18:00", "endTime": "20:00"},
    ]
    activities = [_activity("A-1", remaining_hours=6)]
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    import datetime as dt
    gap_start = dt.time(12, 0)
    gap_end = dt.time(18, 0)
    for s in resp.sessions:
        assert not (s.start_time < gap_end and s.end_time > gap_start), (
            f"session {s.start_time}-{s.end_time} intrudes on the unavailable gap"
        )


def test_no_two_sessions_overlap_under_contention():
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "10:00"}]
    activities = [
        _activity("A-1", remaining_hours=2),
        _activity("A-2", remaining_hours=2),
        _activity("A-3", remaining_hours=2),
    ]
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    intervals = [(s.date, s.start_time, s.end_time) for s in resp.sessions]
    for i in range(len(intervals)):
        for j in range(i + 1, len(intervals)):
            d1, s1, e1 = intervals[i]
            d2, s2, e2 = intervals[j]
            if d1 == d2:
                assert e1 <= s2 or e2 <= s1, "sessions overlap"
