from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import ValidationError

from app.models.request import PlanningRequest
from app.models.response import PlanningResponse
from app.solver.planner import generate_plan

router = APIRouter(prefix="/api/v1", tags=["planning"])


@router.post("/plan", response_model=PlanningResponse, response_model_by_alias=True)
def plan(request: PlanningRequest) -> PlanningResponse:
    try:
        return generate_plan(request)
    except ValueError as exc:
        # Domain-level validation errors (e.g. malformed windows) -> 400.
        raise HTTPException(status_code=400, detail=str(exc)) from exc
