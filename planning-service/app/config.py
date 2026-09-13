"""
Central configuration for the planning service.

Every "magic number" used by the domain model or the CP-SAT model is defined
here, not inline in solver code. This satisfies the requirement that
objective coefficients and model assumptions be configurable, documented,
and easy to tune/replace later (e.g. with a learned productivity model).
"""
from dataclasses import dataclass, field


@dataclass(frozen=True)
class TimeGranularity:
    """CP-SAT reasons about discrete time slots, not continuous minutes."""

    # Minutes per solver time-slot. 15 minutes is a practical first choice:
    # fine enough for realistic sessions, coarse enough to keep the model small.
    slot_minutes: int = 15

    def minutes_to_slots(self, minutes: int) -> int:
        return minutes // self.slot_minutes

    def slots_to_minutes(self, slots: int) -> int:
        return slots * self.slot_minutes


@dataclass(frozen=True)
class WorkloadModel:
    """
    Converts abstract "workload units" into schedulable minutes.

    This is intentionally a simple, configurable linear model for Phase 1:
        minutes_needed = work_units / units_per_hour * 60

    It is NOT a hard mathematical truth — it is a placeholder assumption
    that can later be replaced with a per-activity or per-student learned
    productivity factor (see README "Future Improvements").
    """

    # Default productivity: how many workload units a student completes per
    # hour of focused study, if the activity does not specify its own value.
    default_units_per_hour: float = 2.0

    # Named workload sizes a caller may use instead of a raw numeric value.
    small_units: float = 1.0
    medium_units: float = 2.0
    large_units: float = 4.0

    def units_to_minutes(self, work_units: float, units_per_hour: float | None = None) -> int:
        rate = units_per_hour if units_per_hour and units_per_hour > 0 else self.default_units_per_hour
        hours = work_units / rate
        return round(hours * 60)


@dataclass(frozen=True)
class SessionPreferences:
    """Soft preferences about how sessions should look."""

    # Preferred discrete session lengths, in minutes. The solver is rewarded
    # (softly) for producing sessions close to one of these lengths.
    preferred_lengths_minutes: tuple[int, ...] = (30, 60, 90, 120)

    # Minimum viable session length. Sessions shorter than this are allowed
    # only if nothing else fits (kept short via the objective, not banned
    # outright — Phase 1 keeps this as a soft floor, not a hard constraint).
    min_session_minutes: int = 15

    # Maximum single session length, to avoid unrealistic multi-hour blocks.
    max_session_minutes: int = 150


@dataclass(frozen=True)
class ObjectiveWeights:
    """
    All coefficients used in the CP-SAT objective function.

    Documented here instead of buried as magic numbers inside objective.py.
    Positive weights reward; the solver maximizes total weighted objective.
    Tune these to change the schedule's character without touching model code.
    """

    # 1. Reward for each minute of HIGH-importance / high-priority work scheduled.
    high_priority_minute_reward: int = 5

    # 2. Reward for each minute of any remaining work scheduled (completion).
    completed_work_minute_reward: int = 2

    # 3. Penalty per minute of declared availability left unused.
    #    (Phase 1 keeps this small relative to (1)/(2) so the solver doesn't
    #    force-fill low-value work just to avoid idle time.)
    unused_availability_penalty: int = 1

    # 4. Penalty for fragmentation: charged once per session that is shorter
    #    than SessionPreferences.min_session_minutes-equivalent "ideal" block.
    #    Approximated in Phase 1 as a flat per-session penalty, discounted
    #    for sessions that match a preferred length.
    fragmentation_penalty_per_session: int = 3
    preferred_length_bonus_per_session: int = 2


@dataclass(frozen=True)
class ImportanceWeights:
    """
    Numeric weight per activity 'importance' level, used as a multiplier in
    the priority score. Separate from ObjectiveWeights so importance tuning
    doesn't require touching objective math.
    """

    weights: dict = field(default_factory=lambda: {
        "HIGH": 3,
        "MEDIUM": 2,
        "LOW": 1,
    })

    def value_for(self, importance: str | None) -> int:
        if not importance:
            return self.weights["MEDIUM"]
        return self.weights.get(importance.upper(), self.weights["MEDIUM"])


@dataclass(frozen=True)
class SolverSettings:
    max_time_in_seconds: float = 10.0
    num_search_workers: int = 8


@dataclass(frozen=True)
class PlanningConfig:
    time: TimeGranularity = field(default_factory=TimeGranularity)
    workload: WorkloadModel = field(default_factory=WorkloadModel)
    session_prefs: SessionPreferences = field(default_factory=SessionPreferences)
    objective: ObjectiveWeights = field(default_factory=ObjectiveWeights)
    importance: ImportanceWeights = field(default_factory=ImportanceWeights)
    solver: SolverSettings = field(default_factory=SolverSettings)


DEFAULT_CONFIG = PlanningConfig()
