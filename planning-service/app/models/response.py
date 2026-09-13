"""Response schema for POST /api/v1/plan — Phase 2 contract (spec Section 12)."""
from __future__ import annotations

from datetime import date, time
from enum import Enum

from pydantic import BaseModel, Field


class SolverStatus(str, Enum):
    OPTIMAL = "OPTIMAL"
    FEASIBLE = "FEASIBLE"
    INFEASIBLE = "INFEASIBLE"
    UNKNOWN = "UNKNOWN"


class SessionOut(BaseModel):
    activity_id: str = Field(alias="activityId")
    date: date
    start_time: time = Field(alias="startTime")
    end_time: time = Field(alias="endTime")
    duration_minutes: int = Field(alias="durationMinutes")

    model_config = {"populate_by_name": True}


class StatisticsOut(BaseModel):
    available_minutes: int = Field(alias="availableMinutes")
    planned_minutes: int = Field(alias="plannedMinutes")
    completed_required_minutes: int = Field(alias="completedRequiredMinutes")
    unfinished_required_minutes: int = Field(alias="unfinishedRequiredMinutes")
    unallocated_minutes: int = Field(alias="unallocatedMinutes")

    model_config = {"populate_by_name": True}


class PlanningResponse(BaseModel):
    status: SolverStatus
    sessions: list[SessionOut] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    statistics: StatisticsOut

    model_config = {"populate_by_name": True}
