"""
Request schema for POST /api/v1/plan.

Mirrors the JSON contract in the Phase 1 spec (Section 11), with validation.
"""
from __future__ import annotations

from datetime import date, datetime, time
from enum import Enum

from pydantic import BaseModel, Field, field_validator, model_validator


class ActivityTypeIn(str, Enum):
    EXAM = "EXAM"
    PROJECT = "PROJECT"
    ASSIGNMENT = "ASSIGNMENT"
    QUIZ = "QUIZ"
    REPORT = "REPORT"
    PRESENTATION = "PRESENTATION"
    SELF_STUDY = "SELF_STUDY"
    LECTURE = "LECTURE"
    ASSESSMENT = "ASSESSMENT"
    TASK = "TASK"
    OTHER = "OTHER"


class ImportanceIn(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


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
    type: ActivityTypeIn
    title: str
    module_id: int | None = Field(default=None, alias="moduleId")
    module_credits: int | None = Field(default=None, alias="moduleCredits")
    deadline: datetime | None = None
    remaining_work_units: float = Field(alias="remainingWorkUnits")
    importance: ImportanceIn = ImportanceIn.MEDIUM
    user_priority: ImportanceIn | None = Field(default=None, alias="userPriority")
    productivity_units_per_hour: float | None = Field(default=None, alias="productivityUnitsPerHour")

    model_config = {"populate_by_name": True}

    @field_validator("remaining_work_units")
    @classmethod
    def non_negative_remaining_work(cls, v: float) -> float:
        if v < 0:
            raise ValueError("remainingWorkUnits cannot be negative")
        return v


class PlanningRequest(BaseModel):
    planning_period: PlanningPeriodIn = Field(alias="planningPeriod")
    availability: list[AvailabilityWindowIn] = Field(default_factory=list)
    activities: list[ActivityIn] = Field(default_factory=list)

    model_config = {"populate_by_name": True}
