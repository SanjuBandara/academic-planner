from __future__ import annotations

from datetime import date, datetime

from ortools.sat.python import cp_model

from app.config import DEFAULT_CONFIG
from app.domain.activity import Activity
from app.domain.availability import AvailabilityWindow
from app.solver.constraints import (
    add_no_overlap_constraint,
    add_required_work_upper_bound,
    add_study_capacity_constraint,
)
from app.solver.variables import build_assignment_variables, build_slots


def _solve(model: cp_model.CpModel, assignment):
    solver = cp_model.CpSolver()
    # Maximize total assigned slots so tests exercise the constraints under
    # solver pressure to schedule as much as possible.
    if assignment.x:
        model.Maximize(sum(assignment.x.values()))
    status = solver.Solve(model)
    return solver, status


def test_no_overlap_constraint_prevents_double_booking():
    period_start = date(2026, 9, 14)
    availability = [AvailabilityWindow(date=period_start, start_time=datetime(2026, 9, 14, 8, 0).time(),
                                        end_time=datetime(2026, 9, 14, 9, 0).time())]
    activities = [
        Activity(id="A", title="A", activity_type="TASK", module_id=None, credits=None, deadline=None, remaining_hours=1),
        Activity(id="B", title="B", activity_type="TASK", module_id=None, credits=None, deadline=None, remaining_hours=1),
    ]
    model = cp_model.CpModel()
    slots = build_slots(availability, DEFAULT_CONFIG, period_start)
    assignment = build_assignment_variables(model, activities, slots, DEFAULT_CONFIG, period_start)

    add_no_overlap_constraint(assignment)
    solver, status = _solve(model, assignment)

    assert status in (cp_model.OPTIMAL, cp_model.FEASIBLE)
    for slot_index in assignment.slot_activities:
        occupied = sum(solver.Value(assignment.x[(aid, slot_index)]) for aid in assignment.slot_activities[slot_index])
        assert occupied <= 1


def test_required_work_upper_bound_caps_assigned_units():
    period_start = date(2026, 9, 14)
    availability = [AvailabilityWindow(date=period_start, start_time=datetime(2026, 9, 14, 8, 0).time(),
                                        end_time=datetime(2026, 9, 14, 20, 0).time())]  # 12h, plenty
    activities = [Activity(id="A", title="A", activity_type="TASK", module_id=None, credits=None,
                            deadline=None, remaining_hours=2)]  # 2h = 8 units
    model = cp_model.CpModel()
    slots = build_slots(availability, DEFAULT_CONFIG, period_start)
    assignment = build_assignment_variables(model, activities, slots, DEFAULT_CONFIG, period_start)

    add_required_work_upper_bound(assignment, activities, DEFAULT_CONFIG)
    solver, status = _solve(model, assignment)

    assert status in (cp_model.OPTIMAL, cp_model.FEASIBLE)
    assigned_units = sum(solver.Value(v) for (aid, _), v in assignment.x.items() if aid == "A")
    assert assigned_units <= 8


def test_study_capacity_constraint_never_exceeds_total_slots():
    period_start = date(2026, 9, 14)
    availability = [AvailabilityWindow(date=period_start, start_time=datetime(2026, 9, 14, 8, 0).time(),
                                        end_time=datetime(2026, 9, 14, 9, 0).time())]  # 1h = 4 slots
    activities = [Activity(id="A", title="A", activity_type="TASK", module_id=None, credits=None,
                            deadline=None, remaining_hours=10)]
    model = cp_model.CpModel()
    slots = build_slots(availability, DEFAULT_CONFIG, period_start)
    assignment = build_assignment_variables(model, activities, slots, DEFAULT_CONFIG, period_start)

    add_study_capacity_constraint(assignment)
    solver, status = _solve(model, assignment)

    assert status in (cp_model.OPTIMAL, cp_model.FEASIBLE)
    total_assigned = sum(solver.Value(v) for v in assignment.x.values())
    assert total_assigned <= len(slots)


def test_no_variables_created_for_completed_activity():
    period_start = date(2026, 9, 14)
    availability = [AvailabilityWindow(date=period_start, start_time=datetime(2026, 9, 14, 8, 0).time(),
                                        end_time=datetime(2026, 9, 14, 9, 0).time())]
    activities = [Activity(id="DONE", title="Done", activity_type="TASK", module_id=None, credits=None,
                            deadline=None, remaining_hours=0)]
    model = cp_model.CpModel()
    slots = build_slots(availability, DEFAULT_CONFIG, period_start)
    assignment = build_assignment_variables(model, activities, slots, DEFAULT_CONFIG, period_start)

    assert assignment.activity_slots["DONE"] == []
    assert not any(aid == "DONE" for (aid, _) in assignment.x)
