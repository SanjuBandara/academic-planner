"""Domain-level representation of a single scheduled study session."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, time


@dataclass(frozen=True)
class ScheduledSession:
    """
    One concrete block of time the solver assigned to an activity.
    This is what gets translated into a StudyPlanItem on the Spring Boot side.
    """

    activity_id: str
    date: date
    start_time: time
    end_time: time

    @property
    def duration_minutes(self) -> int:
        start_minutes = self.start_time.hour * 60 + self.start_time.minute
        end_minutes = self.end_time.hour * 60 + self.end_time.minute
        return end_minutes - start_minutes
