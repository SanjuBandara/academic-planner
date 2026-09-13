"""
Domain-level representation of something the solver can schedule.

This is intentionally NOT the same shape as the Spring Boot JPA entities
(Assessment, Task). It is the "planning model" layer described in the
Phase 1 spec: what the mathematical solver needs, not what the student's
domain data looks like verbatim.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum


class ActivityType(str, Enum):
    EXAM = "EXAM"
    PROJECT = "PROJECT"
    ASSIGNMENT = "ASSIGNMENT"
    QUIZ = "QUIZ"
    REPORT = "REPORT"
    PRESENTATION = "PRESENTATION"
    SELF_STUDY = "SELF_STUDY"
    LECTURE = "LECTURE"
    ASSESSMENT = "ASSESSMENT"  # generic fallback when a finer type isn't given
    TASK = "TASK"
    OTHER = "OTHER"


class Importance(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


@dataclass(frozen=True)
class Activity:
    """
    A single schedulable activity for one planning run.

    remaining_work_units is the ONLY workload figure the solver plans
    against — completed work is never rescheduled (Section 3 of the spec).
    """

    id: str
    title: str
    type: ActivityType
    module_id: int | None
    module_credits: int | None
    deadline: datetime | None
    remaining_work_units: float
    importance: Importance = Importance.MEDIUM
    user_priority: Importance | None = None  # explicit student override, if any
    productivity_units_per_hour: float | None = None  # activity-specific override

    def __post_init__(self):
        if self.remaining_work_units < 0:
            raise ValueError(f"Activity {self.id}: remaining_work_units cannot be negative")

    @property
    def effective_importance(self) -> Importance:
        """User-set priority overrides the activity-type importance when present."""
        return self.user_priority or self.importance
