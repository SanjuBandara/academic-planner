"""
Central configuration for the Python planning service.

Phase 2 change from Phase 1: the objective is now an explicit lexicographic
hierarchy (spec Section 10) rather than a flat weighted sum. It's
implemented as a *big-M weighted sum* — a standard, CP-SAT-friendly way to
approximate strict lexicographic ordering: each tier's weight is large
enough that no amount of improvement in a lower tier can outweigh even one
unit of improvement in a higher tier.

    Tier 1 (hard constraints)     -> enforced structurally, not scored
    Tier 2 (completed work)       -> weight 10^9
    Tier 3 (deadline protection)  -> weight 10^6
    Tier 4 (priority)             -> weight 10^4
    Tier 5 (credits)              -> weight 10^2
    Tier 6 (contiguity)           -> weight 10
    Tier 7 (context switching)    -> weight 1

These are documented, named constants — not inline magic numbers — and are
safe to retune as long as the ordering above is preserved.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class TimeGranularity:
    """CP-SAT reasons about discrete time slots, not continuous minutes."""

    slot_minutes: int = 15  # spec Section 4: 15-minute scheduling units


@dataclass(frozen=True)
class SessionPreferences:
    """Soft preferences about how sessions should look (spec Section 9, Objective 5)."""

    preferred_lengths_minutes: tuple[int, ...] = (30, 45, 60, 90, 120)
    min_session_minutes: int = 15
    max_session_minutes: int = 150


@dataclass(frozen=True)
class LexicographicWeights:
    """
    One big-M multiplier per objective tier (spec Section 10). Strictly
    decreasing by several orders of magnitude so higher tiers always
    dominate. All objective code reads from here — no other file defines
    a tier weight.
    """

    completed_work: int = 1_000_000_000     # Objective 1 (spec Section 9)
    deadline_protection: int = 1_000_000    # Objective 2
    priority: int = 10_000                  # Objective 3
    credits: int = 100                      # Objective 4
    contiguity: int = 10                    # Objective 5
    context_switching: int = 1              # Objective 6


@dataclass(frozen=True)
class DeadlineUrgencyScale:
    """
    Discrete urgency score (0-5) used ONLY inside the objective function
    (spec Section 9, Objective 2 explicitly forbids reusing the old Java
    DeadlineUrgencyCalculator; this is a fresh, small, documented model
    built directly into the Python objective).
    """

    thresholds: tuple[tuple[float, int], ...] = (
        (0, 5),        # overdue or due within the next instant
        (24, 4),       # due within 1 day
        (72, 3),       # due within 3 days
        (168, 2),      # due within 1 week
        (336, 1),      # due within 2 weeks
    )
    default_score: int = 0  # no deadline, or further away than all thresholds

    def score_for(self, hours_until_deadline: float | None) -> int:
        if hours_until_deadline is None:
            return self.default_score
        for upper_bound, score in self.thresholds:
            if hours_until_deadline <= upper_bound:
                return score
        return self.default_score


@dataclass(frozen=True)
class SolverSettings:
    max_time_in_seconds: float = 10.0
    num_search_workers: int = 8
    random_seed: int = 42  # deterministic solver behavior (spec Section 19)


@dataclass(frozen=True)
class PlanningConfig:
    time: TimeGranularity = field(default_factory=TimeGranularity)
    session_prefs: SessionPreferences = field(default_factory=SessionPreferences)
    weights: LexicographicWeights = field(default_factory=LexicographicWeights)
    urgency: DeadlineUrgencyScale = field(default_factory=DeadlineUrgencyScale)
    solver: SolverSettings = field(default_factory=SolverSettings)


DEFAULT_CONFIG = PlanningConfig()
