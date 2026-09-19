"""
Request schema for POST /api/v1/plan — Phase 2 contract (spec Section 3).
"""
from __future__ import annotations

from datetime import date, datetime, time

from pydantic import BaseModel, Field, field_validator, model_validator


class PlanningPeriodIn(BaseModel):
    start_date: date = Field(alias="startDate")
    end_date: date = Field(alias="endDate")

    model_config = {"populate_by_name": True}

    @model_validator(mode="after")
    def check_order(self) -> "PlanningPeriodIn":
        if self.start_date > self.end_date:
            raise ValueError("planningPeriod.startDate must be on or before endDate")
        return self


class AvailabilityWindowIn(BaseModel):
    date: date
    start_time: time = Field(alias="startTime")
    end_time: time = Field(alias="endTime")

    model_config = {"populate_by_name": True}

    @model_validator(mode="after")
    def check_order(self) -> "AvailabilityWindowIn":
        if self.start_time >= self.end_time:
            raise ValueError(f"availability window on {self.date}: startTime must be before endTime")
        return self


class ActivityIn(BaseModel):
    id: str
    title: str
    activity_type: str = Field(alias="activityType")
    module_id: str | None = Field(default=None, alias="moduleId")
    credits: float | None = None
    deadline: datetime | None = None
    remaining_hours: float = Field(alias="remainingHours")
    priority: int = 3
    weight: float | None = None

    model_config = {"populate_by_name": True}

    @field_validator("remaining_hours")
    @classmethod
    def non_negative_remaining_hours(cls, v: float) -> float:
        if v < 0:
            raise ValueError("remainingHours cannot be negative")
        return v

    @field_validator("priority")
    @classmethod
    def priority_in_range(cls, v: int) -> int:
        if not (1 <= v <= 5):
            raise ValueError("priority must be between 1 and 5")
        return v

    @field_validator("weight")
    @classmethod
    def weight_in_range(cls, v: float | None) -> float | None:
        if v is not None and not 0 <= v <= 100:
            raise ValueError("weight must be between 0 and 100")
        return v


class PlanningRequest(BaseModel):
    planning_period: PlanningPeriodIn = Field(alias="planningPeriod")
    availability: list[AvailabilityWindowIn] = Field(default_factory=list)
    activities: list[ActivityIn] = Field(default_factory=list)

    model_config = {"populate_by_name": True}

    @model_validator(mode="after")
    def no_overlapping_windows(self) -> "PlanningRequest":
        """
        Spec Section 5: 'Validate that availability windows do not overlap.'
        Checked here across ALL windows sharing a date, not just within a
        single window (which is already enforced per-window above).
        """
        by_date: dict[date, list[AvailabilityWindowIn]] = {}
        for window in self.availability:
            by_date.setdefault(window.date, []).append(window)

        for day, windows in by_date.items():
            ordered = sorted(windows, key=lambda w: w.start_time)
            for prev, curr in zip(ordered, ordered[1:]):
                if curr.start_time < prev.end_time:
                    raise ValueError(
                        f"Overlapping availability windows on {day}: "
                        f"[{prev.start_time}-{prev.end_time}] and [{curr.start_time}-{curr.end_time}]"
                    )
        return self
