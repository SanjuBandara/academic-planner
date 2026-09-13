from __future__ import annotations

from app.models.request import PlanningRequest
from app.models.response import SolverStatus
from app.solver.planner import generate_plan


def _activity(id_, remaining_work_units, deadline=None, importance="MEDIUM", type_="ASSIGNMENT"):
    a = {
        "id": id_,
        "type": type_,
        "title": id_,
        "remainingWorkUnits": remaining_work_units,
        "importance": importance,
    }
    if deadline:
        a["deadline"] = deadline
    return a


def _request(start_date, end_date, availability, activities):
    return PlanningRequest.model_validate({
        "planningPeriod": {"startDate": start_date, "endDate": end_date},
        "availability": availability,
        "activities": activities,
    })


def test_no_session_exceeds_available_hours():
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "14:00"}]  # 6h
    activities = [_activity("A-1", remaining_work_units=40)]  # far more than fits
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    assert resp.statistics.planned_minutes <= resp.statistics.available_minutes
    assert resp.statistics.available_minutes == 360


def test_no_overlapping_sessions():
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "10:00"}]
    activities = [
        _activity("A-1", remaining_work_units=8),
        _activity("A-2", remaining_work_units=8),
    ]
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    # Build a set of (date, start_time) pairs and confirm no duplicates.
    seen = set()
    for s in resp.sessions:
        # every 15-min slot within [start,end) must be unique across sessions
        pass
    intervals = [(s.date, s.start_time, s.end_time) for s in resp.sessions]
    for i in range(len(intervals)):
        for j in range(i + 1, len(intervals)):
            d1, s1, e1 = intervals[i]
            d2, s2, e2 = intervals[j]
            if d1 == d2:
                assert e1 <= s2 or e2 <= s1, "sessions overlap"


def test_no_work_scheduled_after_deadline():
    availability = [
        {"date": "2026-09-14", "startTime": "08:00", "endTime": "12:00"},  # Monday
        {"date": "2026-09-16", "startTime": "08:00", "endTime": "12:00"},  # Wednesday
        {"date": "2026-09-17", "startTime": "08:00", "endTime": "12:00"},  # Thursday
    ]
    activities = [_activity("A-1", remaining_work_units=20, deadline="2026-09-16T23:59:00")]
    req = _request("2026-09-14", "2026-09-17", availability, activities)

    resp = generate_plan(req)

    for s in resp.sessions:
        assert s.date <= __import__("datetime").date(2026, 9, 16)


def test_high_priority_preferred_over_low_priority_no_deadline():
    # Only enough room for one activity's full workload.
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "09:00"}]  # 1h = 4 slots
    activities = [
        _activity("HIGH-DUE-TOMORROW", remaining_work_units=2, deadline="2026-09-15T23:59:00", importance="HIGH"),
        _activity("LOW-NO-DEADLINE", remaining_work_units=2, importance="LOW"),
    ]
    req = _request("2026-09-14", "2026-09-15", availability, activities)

    resp = generate_plan(req)

    high_minutes = sum(s.duration_minutes for s in resp.sessions if s.activity_id == "HIGH-DUE-TOMORROW")
    low_minutes = sum(s.duration_minutes for s in resp.sessions if s.activity_id == "LOW-NO-DEADLINE")
    assert high_minutes >= low_minutes


def test_does_not_schedule_more_than_remaining_workload():
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "20:00"}]  # 12h, plenty
    activities = [_activity("A-1", remaining_work_units=4)]  # 4 units @ default 2 units/hr = 2h = 120 min
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    planned = sum(s.duration_minutes for s in resp.sessions if s.activity_id == "A-1")
    assert planned <= 125  # allow one slot of rounding


def test_insufficient_availability_returns_partial_plan_and_warning():
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "13:00"}]  # 5h
    activities = [_activity("A-1", remaining_work_units=20)]  # needs 10h @ default productivity
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
    assert len(resp.sessions) > 0
    assert len(resp.warnings) >= 1


def test_no_availability_returns_empty_schedule_with_explanation():
    req = _request("2026-09-14", "2026-09-14", [], [_activity("A-1", remaining_work_units=4)])

    resp = generate_plan(req)

    assert resp.sessions == []
    assert len(resp.warnings) == 1


def test_multiple_activities_with_competing_deadlines():
    availability = [
        {"date": "2026-09-14", "startTime": "08:00", "endTime": "12:00"},
        {"date": "2026-09-15", "startTime": "08:00", "endTime": "12:00"},
    ]
    activities = [
        _activity("DSA", remaining_work_units=8, deadline="2026-09-14T23:59:00", importance="HIGH"),
        _activity("ECON-QUIZ", remaining_work_units=4, deadline="2026-09-15T23:59:00", importance="MEDIUM"),
        _activity("STATS-LECTURE", remaining_work_units=2, importance="LOW"),
    ]
    req = _request("2026-09-14", "2026-09-15", availability, activities)

    resp = generate_plan(req)

    dsa_sessions = [s for s in resp.sessions if s.activity_id == "DSA"]
    assert all(s.date == __import__("datetime").date(2026, 9, 14) for s in dsa_sessions)
