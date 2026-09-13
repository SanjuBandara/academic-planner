"""
Orchestrates a single planning run:

    PlanningRequest (API model)
        -> Activity / AvailabilityWindow (domain model)
        -> CP-SAT variables, hard constraints, objective
        -> solve
        -> ScheduledSession list (domain model)
        -> PlanningResponse (API model)

Kept deliberately thin: all real logic lives in domain/*, solver/variables.py,
solver/constraints.py, and solver/objective.py.
"""
from __future__ import annotations

from datetime import date, timedelta

from ortools.sat.python import cp_model

from app.config import PlanningConfig, DEFAULT_CONFIG
from app.domain.activity import Activity, ActivityType, Importance
from app.domain.availability import AvailabilityWindow
from app.domain.session import ScheduledSession
from app.models.request import PlanningRequest
from app.models.response import PlanningResponse, SessionOut, SolverStatus, StatisticsOut
from app.solver.constraints import apply_all_hard_constraints
from app.solver.objective import apply_objective
from app.solver.variables import AssignmentVariables, Slot, build_assignment_variables, build_slots

_STATUS_MAP = {
    cp_model.OPTIMAL: SolverStatus.OPTIMAL,
    cp_model.FEASIBLE: SolverStatus.FEASIBLE,
    cp_model.INFEASIBLE: SolverStatus.INFEASIBLE,
    cp_model.MODEL_INVALID: SolverStatus.UNKNOWN,
    cp_model.UNKNOWN: SolverStatus.UNKNOWN,
}


def _to_domain_activities(request: PlanningRequest) -> list[Activity]:
    activities = []
    for a in request.activities:
        activities.append(Activity(
            id=a.id,
            title=a.title,
            type=ActivityType(a.type.value),
            module_id=a.module_id,
            module_credits=a.module_credits,
            deadline=a.deadline,
            remaining_work_units=a.remaining_work_units,
            importance=Importance(a.importance.value),
            user_priority=Importance(a.user_priority.value) if a.user_priority else None,
            productivity_units_per_hour=a.productivity_units_per_hour,
        ))
    return activities


def _to_domain_availability(request: PlanningRequest) -> list[AvailabilityWindow]:
    windows = []
    for w in request.availability:
        # Only keep windows inside the declared planning period (plan-period constraint).
        if request.planning_period.start_date <= w.date <= request.planning_period.end_date:
            windows.append(AvailabilityWindow(date=w.date, start_time=w.start_time, end_time=w.end_time))
    return windows


def _merge_slots_into_sessions(
    activity_id: str,
    assigned_slots: list[Slot],
    config: PlanningConfig,
) -> list[ScheduledSession]:
    """Merges contiguous assigned slots for one activity into sessions."""
    sessions: list[ScheduledSession] = []
    if not assigned_slots:
        return sessions

    ordered = sorted(assigned_slots, key=lambda s: s.index)
    block_start = ordered[0]
    block_end = ordered[0]

    def flush():
        sessions.append(ScheduledSession(
            activity_id=activity_id,
            date=block_start.date,
            start_time=block_start.start_time,
            end_time=block_end.end_time,
        ))

    for slot in ordered[1:]:
        contiguous = (
            slot.date == block_end.date
            and slot.start_time == block_end.end_time
        )
        if contiguous:
            block_end = slot
        else:
            flush()
            block_start = slot
            block_end = slot

    flush()
    return sessions


def generate_plan(request: PlanningRequest, config: PlanningConfig = DEFAULT_CONFIG) -> PlanningResponse:
    activities = _to_domain_activities(request)
    availability = _to_domain_availability(request)

    slots = build_slots(availability, config)
    available_minutes = len(slots) * config.time.slot_minutes

    if not slots:
        return PlanningResponse(
            status=SolverStatus.FEASIBLE,
            sessions=[],
            warnings=["No availability declared for the planning period — empty schedule returned."],
            statistics=StatisticsOut(availableMinutes=0, plannedMinutes=0, unallocatedMinutes=0),
        )

    schedulable_activities = [a for a in activities if a.remaining_work_units > 0]
    if not schedulable_activities:
        return PlanningResponse(
            status=SolverStatus.FEASIBLE,
            sessions=[],
            warnings=["No activities with remaining work were provided."],
            statistics=StatisticsOut(
                availableMinutes=available_minutes, plannedMinutes=0, unallocatedMinutes=available_minutes
            ),
        )

    model = cp_model.CpModel()
    assignment: AssignmentVariables = build_assignment_variables(model, schedulable_activities, slots)

    if not assignment.x:
        return PlanningResponse(
            status=SolverStatus.INFEASIBLE,
            sessions=[],
            warnings=["No activity could be matched to any available slot before its deadline."],
            statistics=StatisticsOut(
                availableMinutes=available_minutes, plannedMinutes=0, unallocatedMinutes=available_minutes
            ),
        )

    apply_all_hard_constraints(assignment, schedulable_activities, config)
    apply_objective(model, assignment, schedulable_activities, config)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = config.solver.max_time_in_seconds
    solver.parameters.num_search_workers = config.solver.num_search_workers

    cp_status = solver.Solve(model)
    status = _STATUS_MAP.get(cp_status, SolverStatus.UNKNOWN)

    if status not in (SolverStatus.OPTIMAL, SolverStatus.FEASIBLE):
        return PlanningResponse(
            status=status,
            sessions=[],
            warnings=["Solver could not find a feasible schedule with the given constraints."],
            statistics=StatisticsOut(
                availableMinutes=available_minutes, plannedMinutes=0, unallocatedMinutes=available_minutes
            ),
        )

    # Extract assigned slots per activity, then merge into contiguous sessions.
    slots_by_index = {s.index: s for s in slots}
    sessions: list[ScheduledSession] = []
    remaining_by_activity: dict[str, float] = {}

    for activity in schedulable_activities:
        assigned_slots = [
            slots_by_index[s_idx]
            for s_idx in assignment.activity_slots.get(activity.id, [])
            if solver.Value(assignment.x[(activity.id, s_idx)]) == 1
        ]
        sessions.extend(_merge_slots_into_sessions(activity.id, assigned_slots, config))

        needed_minutes = config.workload.units_to_minutes(
            activity.remaining_work_units, activity.productivity_units_per_hour
        )
        planned_minutes = len(assigned_slots) * config.time.slot_minutes
        if planned_minutes < needed_minutes:
            remaining_by_activity[activity.id] = (needed_minutes - planned_minutes) / 60.0

    sessions.sort(key=lambda s: (s.date, s.start_time))
    planned_minutes_total = sum(s.duration_minutes for s in sessions)
    unallocated_minutes = max(0, available_minutes - planned_minutes_total)

    warnings: list[str] = []
    for activity in schedulable_activities:
        short_by_hours = remaining_by_activity.get(activity.id)
        if short_by_hours:
            warnings.append(
                f"\"{activity.title}\" could not be fully scheduled within the planning period "
                f"({short_by_hours:.1f}h of remaining work left unassigned) — insufficient availability "
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
            for s in sessions
        ],
        warnings=warnings,
        statistics=StatisticsOut(
            availableMinutes=available_minutes,
            plannedMinutes=planned_minutes_total,
            unallocatedMinutes=unallocated_minutes,
        ),
    )
