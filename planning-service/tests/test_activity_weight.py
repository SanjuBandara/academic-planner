from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.models.request import ActivityIn, PlanningRequest
from app.models.response import SolverStatus
from app.solver.planner import generate_plan, _to_domain_activities


def test_activity_in_with_weight():
    act = ActivityIn.model_validate({
        "id": "A-101",
        "title": "DSA Mid Exam",
        "activityType": "EXAM",
        "moduleId": "CS201",
        "credits": 3.0,
        "deadline": "2026-09-20T23:59:00",
        "remainingHours": 8.0,
        "priority": 5,
        "weight": 40.0,
    })
    assert act.weight == 40.0


def test_activity_in_without_weight_or_none():
    act1 = ActivityIn.model_validate({
        "id": "A-102",
        "title": "Quiz",
        "activityType": "QUIZ",
        "remainingHours": 2.0,
    })
    assert act1.weight is None

    act2 = ActivityIn.model_validate({
        "id": "T-201",
        "title": "Lab prep",
        "activityType": "TASK",
        "remainingHours": 2.0,
        "weight": None,
    })
    assert act2.weight is None


def test_activity_in_weight_range_validation():
    # Valid bounds
    ActivityIn.model_validate({
        "id": "A-1",
        "title": "Min weight",
        "activityType": "EXAM",
        "remainingHours": 1.0,
        "weight": 0.0,
    })
    ActivityIn.model_validate({
        "id": "A-2",
        "title": "Max weight",
        "activityType": "EXAM",
        "remainingHours": 1.0,
        "weight": 100.0,
    })

    # Below 0
    with pytest.raises(ValidationError):
        ActivityIn.model_validate({
            "id": "A-bad-1",
            "title": "Negative weight",
            "activityType": "EXAM",
            "remainingHours": 1.0,
            "weight": -0.1,
        })

    # Above 100
    with pytest.raises(ValidationError):
        ActivityIn.model_validate({
            "id": "A-bad-2",
            "title": "Over 100 weight",
            "activityType": "EXAM",
            "remainingHours": 1.0,
            "weight": 100.1,
        })


def test_weight_propagates_to_domain_and_plan():
    req = PlanningRequest.model_validate({
        "planningPeriod": {"startDate": "2026-09-20", "endDate": "2026-09-21"},
        "availability": [
            {"date": "2026-09-20", "startTime": "09:00", "endTime": "17:00"},
            {"date": "2026-09-21", "startTime": "09:00", "endTime": "17:00"},
        ],
        "activities": [
            {
                "id": "A-1",
                "title": "DSA Mid Exam",
                "activityType": "EXAM",
                "moduleId": "CS201",
                "credits": 3.0,
                "deadline": "2026-09-20T23:59:00",
                "remainingHours": 4.0,
                "priority": 5,
                "weight": 40.0,
            },
            {
                "id": "T-1",
                "title": "Exercise Sheet",
                "activityType": "TASK",
                "moduleId": "CS201",
                "credits": 3.0,
                "deadline": "2026-09-21T23:59:00",
                "remainingHours": 2.0,
                "priority": 3,
                "weight": None,
            },
        ],
    })

    domain_acts = _to_domain_activities(req)
    assert len(domain_acts) == 2
    assert domain_acts[0].weight == 40.0
    assert domain_acts[1].weight is None

    resp = generate_plan(req)
    assert resp.status in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE)
    assert resp.statistics.completed_required_minutes == 6 * 60
