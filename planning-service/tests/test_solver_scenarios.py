from __future__ import annotations

import datetime as dt

from app.models.request import PlanningRequest
from app.models.response import SolverStatus
from app.solver.planner import generate_plan


def _activity(id_, remaining_hours, deadline=None, priority=3, credits=None, activity_type="TASK", module_id=None):
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
    if module_id is not None:
        a["moduleId"] = module_id
    return a


def _request(start_date, end_date, availability, activities):
    return PlanningRequest.model_validate({
        "planningPeriod": {"startDate": start_date, "endDate": end_date},
        "availability": availability,
        "activities": activities,
    })


def _minutes_for(resp, activity_id):
    return sum(s.duration_minutes for s in resp.sessions if s.activity_id == activity_id)


# ---- Test 3: earlier deadline wins under limited availability ----------

def test_earlier_deadline_wins_under_scarce_availability():
    # Only 1h of capacity total, both activities need more than that.
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "09:00"}]
    activities = [
        _activity("DSA", remaining_hours=6, deadline="2026-09-16T23:59:00"),
        _activity("ECON", remaining_hours=4, deadline="2026-09-20T23:59:00"),
    ]
    req = _request("2026-09-14", "2026-09-20", availability, activities)

    resp = generate_plan(req)

    assert _minutes_for(resp, "DSA") >= _minutes_for(resp, "ECON")


# ---- Test 6: multiple competing deadlines -------------------------------

def test_multiple_deadlines_protect_earliest_first():
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "09:30"}]  # 1.5h only
    activities = [
        _activity("DSA", remaining_hours=6, deadline="2026-09-16T23:59:00"),
        _activity("ECON", remaining_hours=6, deadline="2026-09-17T23:59:00"),
        _activity("STATS", remaining_hours=6, deadline="2026-09-20T23:59:00"),
    ]
    req = _request("2026-09-14", "2026-09-20", availability, activities)

    resp = generate_plan(req)

    dsa = _minutes_for(resp, "DSA")
    econ = _minutes_for(resp, "ECON")
    stats = _minutes_for(resp, "STATS")
    assert dsa >= econ >= stats


# ---- Test 7: credits influence importance, not extra hours --------------

def test_credits_influence_objective_but_never_exceed_required_work():
    availability = [{"date": "2026-09-14", "startTime": "08:00", "endTime": "10:00"}]  # 2h total
    activities = [
        _activity("HIGH-CREDIT", remaining_hours=1, credits=6.0),
        _activity("LOW-CREDIT", remaining_hours=1, credits=1.0),
    ]
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    # Neither activity should ever get more than its own required hours,
    # regardless of credits.
    assert _minutes_for(resp, "HIGH-CREDIT") <= 65  # ~1h + rounding tolerance
    assert _minutes_for(resp, "LOW-CREDIT") <= 65
    # With equal priority/deadline and enough combined room for both,
    # the higher-credit activity should be filled first/fully.
    assert _minutes_for(resp, "HIGH-CREDIT") >= _minutes_for(resp, "LOW-CREDIT")


# ---- Test 8: irregular daily availability --------------------------------

def test_irregular_daily_availability_is_respected_exactly():
    availability = [
        {"date": "2026-09-14", "startTime": "08:00", "endTime": "12:00"},
        {"date": "2026-09-14", "startTime": "18:00", "endTime": "20:00"},
        {"date": "2026-09-15", "startTime": "09:00", "endTime": "11:00"},
        {"date": "2026-09-16", "startTime": "19:00", "endTime": "22:00"},
    ]
    activities = [_activity("A-1", remaining_hours=20)]  # more than fits, forces full usage
    req = _request("2026-09-14", "2026-09-16", availability, activities)

    resp = generate_plan(req)

    allowed_windows = {
        dt.date(2026, 9, 14): [(dt.time(8, 0), dt.time(12, 0)), (dt.time(18, 0), dt.time(20, 0))],
        dt.date(2026, 9, 15): [(dt.time(9, 0), dt.time(11, 0))],
        dt.date(2026, 9, 16): [(dt.time(19, 0), dt.time(22, 0))],
    }
    for s in resp.sessions:
        windows = allowed_windows[s.date]
        assert any(w_start <= s.start_time and s.end_time <= w_end for w_start, w_end in windows), (
            f"session {s.date} {s.start_time}-{s.end_time} falls outside declared availability"
        )


# ---- Test 9: activity larger than a single window gets split ------------

def test_activity_larger_than_one_window_is_split_across_windows():
    availability = [
        {"date": "2026-09-14", "startTime": "08:00", "endTime": "10:00"},
        {"date": "2026-09-14", "startTime": "18:00", "endTime": "20:00"},
    ]
    activities = [_activity("A-1", remaining_hours=4)]  # needs both 2h windows
    req = _request("2026-09-14", "2026-09-14", availability, activities)

    resp = generate_plan(req)

    assert len(resp.sessions) == 2
    assert resp.statistics.unfinished_required_minutes == 0
    morning = [s for s in resp.sessions if s.start_time < dt.time(12, 0)]
    evening = [s for s in resp.sessions if s.start_time >= dt.time(12, 0)]
    assert len(morning) == 1 and len(evening) == 1


# ---- Test 10: full academic scenario -------------------------------------

def test_real_academic_scenario():
    availability = [
        {"date": "2026-09-14", "startTime": "08:00", "endTime": "12:00"},  # Monday
        {"date": "2026-09-14", "startTime": "18:00", "endTime": "20:00"},
        {"date": "2026-09-15", "startTime": "09:00", "endTime": "12:00"},  # Tuesday
        {"date": "2026-09-15", "startTime": "18:00", "endTime": "20:00"},
        {"date": "2026-09-16", "startTime": "08:00", "endTime": "11:00"},  # Wednesday
    ]
    activities = [
        _activity("DSA-ASSIGNMENT", remaining_hours=6, deadline="2026-09-16T23:59:00",
                   credits=3.0, priority=5, module_id="DSA", activity_type="ASSESSMENT_PREP"),
        _activity("ECON-QUIZ", remaining_hours=2, deadline="2026-09-17T23:59:00",
                   credits=2.5, priority=4, module_id="ECON", activity_type="ASSESSMENT_PREP"),
        _activity("ECON-SELF-STUDY", remaining_hours=4,
                   credits=2.5, priority=2, module_id="ECON", activity_type="SELF_STUDY"),
        _activity("DSA-SELF-STUDY", remaining_hours=3,
                   credits=3.0, priority=2, module_id="DSA", activity_type="SELF_STUDY"),
        _activity("STATS-LECTURE", remaining_hours=3,
                   credits=2.5, priority=3, module_id="STATS", activity_type="LECTURE"),
    ]
    req = _request("2026-09-14", "2026-09-16", availability, activities)

    resp = generate_plan(req)

    assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)

    # No overlaps
    intervals = [(s.date, s.start_time, s.end_time) for s in resp.sessions]
    for i in range(len(intervals)):
        for j in range(i + 1, len(intervals)):
            d1, s1, e1 = intervals[i]
            d2, s2, e2 = intervals[j]
            if d1 == d2:
                assert e1 <= s2 or e2 <= s1

    # No work past DSA's deadline (Sept 16 23:59, so all its sessions must be <= Sept 16)
    dsa_sessions = [s for s in resp.sessions if s.activity_id == "DSA-ASSIGNMENT"]
    assert all(s.date <= dt.date(2026, 9, 16) for s in dsa_sessions)

    # No activity scheduled beyond its own required work
    for activity_id, hours in [("DSA-ASSIGNMENT", 6), ("ECON-QUIZ", 2), ("ECON-SELF-STUDY", 4),
                               ("DSA-SELF-STUDY", 3), ("STATS-LECTURE", 3)]:
        assert _minutes_for(resp, activity_id) <= hours * 60 + 15  # rounding tolerance

    # Total planned time is internally consistent
    assert resp.statistics.planned_minutes == sum(s.duration_minutes for s in resp.sessions)
