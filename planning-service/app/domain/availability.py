"""Domain-level representation of a declared available time window."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, time


@dataclass(frozen=True)
class AvailabilityWindow:
    """
    One contiguous block of time the student has declared as free to study,
    e.g. Monday 08:00-12:00. The solver treats these as hard scheduling
    resources: no session may exist outside any declared window.
    """

    date: date
    start_time: time
    end_time: time

    def __post_init__(self):
        if self.start_time >= self.end_time:
            raise ValueError(
                f"AvailabilityWindow on {self.date}: start_time must be before end_time"
            )

    def duration_minutes(self) -> int:
        start_minutes = self.start_time.hour * 60 + self.start_time.minute
        end_minutes = self.end_time.hour * 60 + self.end_time.minute
        return end_minutes - start_minutes
