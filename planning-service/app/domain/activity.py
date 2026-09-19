"""
Domain-level representation of something the solver can schedule.

Phase 2 field set matches the spec's Section 3/6 contract exactly:
id, title, activityType, moduleId, credits, deadline, remainingHours,
priority. This is deliberately NOT the same shape as Java's Assessment/Task
entities (spec Section 20) — Spring Boot's Phase 3 mapper is responsible
for that translation.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from app.solver.time_units import hours_to_units


@dataclass(frozen=True)
class Activity:
    id: str
    title: str
    activity_type: str
    module_id: str | None
    credits: float | None
    deadline: datetime | None
    remaining_hours: float
    priority: int = 3  # student/system-set priority, e.g. 1 (low) .. 5 (high)
    weight: float | None = None

    def __post_init__(self):
        if self.remaining_hours < 0:
            raise ValueError(f"Activity {self.id}: remainingHours cannot be negative")

    def required_units(self, slot_minutes: int) -> int:
        """
        Whole time units needed to complete all remaining work, per spec
        Section 7: `requiredUnits = remainingHours * (60 / slotMinutes)`.
        No productivity/conversion factor — remainingHours is taken at
        face value, since the spec's Phase 2 model does not introduce one.
        """
        return hours_to_units(self.remaining_hours, slot_minutes)
