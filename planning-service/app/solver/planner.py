"""
Orchestrates a single planning run (spec Section 15):

    PlanningRequest -> Activity/AvailabilityWindow (domain)
        -> CP-SAT variables, hard constraints, objective
        -> solve
        -> ExtractedSolution (via solution.py)
        -> PlanningResponse
"""
from __future__ import annotations

from datetime import datetime as _dt, time as _time

from ortools.sat.python import cp_model

from app.config import PlanningConfig, DEFAULT_CONFIG
from app.domain.activity import Activity
from app.domain.availability import AvailabilityWindow
from app.models.request import PlanningRequest
from app.models.response import PlanningResponse, SessionOut, SolverStatus, StatisticsOut
from app.solver.constraints import apply_all_hard_constraints
from app.solver.objective import apply_objective
from app.solver.solution import extract_solution
from app.solver.variables import AssignmentVariables, build_assignment_variables, build_slots

_STATUS_MAP = {
    cp_model.OPTIMAL: SolverStatus.OPTIMAL,
    cp_model.FEASIBLE: SolverStatus.FEASIBLE,
    cp_model.INFEASIBLE: SolverStatus.INFEASIBLE,
    cp_model.MODEL_INVALID: SolverStatus.UNKNOWN,
    cp_model.UNKNOWN: SolverStatus.UNKNOWN,
}


def _to_domain_activities(request: PlanningRequest) -> list[Activity]:
    return [
        Activity(
            id=a.id,
            title=a.title,
            activity_type=a.activity_type,
            module_id=a.module_id,
            credits=a.credits,
            deadline=a.deadline,
            remaining_hours=a.remaining_hours,
            priority=a.priority,
            weight=a.weight,
        )
        for a in request.activities
    ]


def _to_domain_availability(request: PlanningRequest) -> list[AvailabilityWindow]:
    """
    Converts the raw availability windows in the request to domain objects,
    applying two filters:

    1.  Period filter: only windows whose date falls inside [startDate, endDate]
        are kept.
    2.  Current-time trimming: if ``currentDateTime`` is provided, windows on
        the current day that have already (partly or fully) elapsed are trimmed
        or dropped so the solver never schedules study into the past.

        Rules (where ``current_date`` = currentDateTime.date() and
               ``current_time`` = currentDateTime.time()):
        - window.date < current_date           -> drop (whole day in the past)
        - window.date == current_date:
            - window.end_time   <= current_time -> drop (window fully elapsed)
            - window.start_time <  current_time -> trim start to current_time
            - otherwise                         -> keep unchanged
        - window.date > current_date           -> keep unchanged
    """
    period_start = request.planning_period.start_date
    period_end = request.planning_period.end_date

    current_date = None
    current_time = None
    if request.current_date_time is not None:
        current_date = request.current_date_time.date()
        current_time = request.current_date_time.time().replace(second=0, microsecond=0)

    windows: list[AvailabilityWindow] = []
    for w in request.availability:
        if not (period_start <= w.date <= period_end):
            continue

        start_time = w.start_time
        end_time = w.end_time

        if current_date is not None:
            if w.date < current_date:
                # Entire day is in the past — skip
                continue
            elif w.date == current_date:
                if end_time <= current_time:
                    # Window completely elapsed — skip
                    continue
                elif start_time < current_time:
                    # Window partially elapsed — trim start to now
                    start_time = current_time

        # Defensive: skip zero-length windows produced by trimming
        if start_time >= end_time:
            continue

        windows.append(AvailabilityWindow(date=w.date, start_time=start_time, end_time=end_time))

    return windows


def generate_plan(request: PlanningRequest, config: PlanningConfig = DEFAULT_CONFIG) -> PlanningResponse:
    activities = _to_domain_activities(request)
    availability = _to_domain_availability(request)
    period_start = request.planning_period.start_date

    slots = build_slots(availability, config, period_start)
    available_minutes = len(slots) * config.time.slot_minutes

    if not slots:
        return PlanningResponse(
            status=SolverStatus.FEASIBLE,
            sessions=[],
            warnings=["No availability declared for the planning period — empty schedule returned."],
            statistics=StatisticsOut(
                availableMinutes=0, plannedMinutes=0,
                completedRequiredMinutes=0, unfinishedRequiredMinutes=0, unallocatedMinutes=0,
            ),
        )

    schedulable_activities = [a for a in activities if a.remaining_hours > 0]
    if not schedulable_activities:
        return PlanningResponse(
            status=SolverStatus.FEASIBLE,
            sessions=[],
            warnings=["No activities with remaining work were provided."],
            statistics=StatisticsOut(
                availableMinutes=available_minutes, plannedMinutes=0,
                completedRequiredMinutes=0, unfinishedRequiredMinutes=0,
                unallocatedMinutes=available_minutes,
            ),
        )

    model = cp_model.CpModel()
    assignment: AssignmentVariables = build_assignment_variables(
        model, schedulable_activities, slots, config, period_start
    )

    if not assignment.x:
        return PlanningResponse(
            status=SolverStatus.INFEASIBLE,
            sessions=[],
            warnings=["No activity could be matched to any available slot before its deadline."],
            statistics=StatisticsOut(
                availableMinutes=available_minutes, plannedMinutes=0,
                completedRequiredMinutes=0, unfinishedRequiredMinutes=0,
                unallocatedMinutes=available_minutes,
            ),
        )

    apply_all_hard_constraints(assignment, schedulable_activities, config)
    apply_objective(model, assignment, schedulable_activities, config, period_start)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = config.solver.max_time_in_seconds
    solver.parameters.num_search_workers = config.solver.num_search_workers
    solver.parameters.random_seed = config.solver.random_seed

    cp_status = solver.Solve(model)
    status = _STATUS_MAP.get(cp_status, SolverStatus.UNKNOWN)

    if status not in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE):
        return PlanningResponse(
            status=status,
            sessions=[],
            warnings=["Solver could not find a feasible schedule with the given constraints."],
            statistics=StatisticsOut(
                availableMinutes=available_minutes, plannedMinutes=0,
                completedRequiredMinutes=0, unfinishedRequiredMinutes=0,
                unallocatedMinutes=available_minutes,
            ),
        )

    solution = extract_solution(solver, assignment, schedulable_activities, config)

    planned_minutes_total = sum(s.duration_minutes for s in solution.sessions)
    unallocated_minutes = max(0, available_minutes - planned_minutes_total)

    warnings: list[str] = []
    for activity in schedulable_activities:
        shortfall_hours = solution.unfinished_by_activity.get(activity.id)
        if shortfall_hours:
            warnings.append(
                f"\"{activity.title}\" could not be fully scheduled within the planning period "
                f"({shortfall_hours:.1f}h of remaining work left unassigned) — insufficient availability "
                f"and/or deadline before it could be completed."
            )

    return PlanningResponse(
        status=status,
        sessions=[
            SessionOut(
                activityId=s.activity_id,
                date=s.date,
                startTime=s.start_time,
                endTime=s.end_time,
                durationMinutes=s.duration_minutes,
            )
            for s in solution.sessions
        ],
        warnings=warnings,
        statistics=StatisticsOut(
            availableMinutes=available_minutes,
            plannedMinutes=planned_minutes_total,
            completedRequiredMinutes=solution.completed_required_minutes,
            unfinishedRequiredMinutes=solution.unfinished_required_minutes,
            unallocatedMinutes=unallocated_minutes,
        ),
    )
