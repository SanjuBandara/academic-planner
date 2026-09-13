from fastapi import FastAPI

from app.api.planning import router as planning_router

app = FastAPI(
    title="Academic Planner — Planning Service",
    description="Phase 1: CP-SAT based study-schedule optimizer.",
    version="0.1.0",
)

app.include_router(planning_router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
