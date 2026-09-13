# Academic Planner — Planning Engine Redesign, Phase 2

## Scope confirmation

Per the Phase 2 spec: **only** `planning-service/` was touched. Nothing in
the Spring Boot backend, the React frontend, or the Phase 1
`spring-client/` integration package was modified. `PlanningEngine`,
`TimeAllocator`, `PriorityCalculator`, `FeasibilityAnalyzer`,
`SessionScheduler` are untouched. No LLM/AI code was added.

## What changed from Phase 1, and why

Phase 1 used an abstract "work units + productivity factor" workload model
and HIGH/MEDIUM/LOW importance. The Phase 2 spec defines a more concrete,
different contract — `remainingHours` taken directly (no productivity
factor), numeric `priority` (1-5), `credits`, `activityType` as free text —
so the Python service's domain/models/solver layers were **updated in
place** to match, per the spec's own instruction to "adapt carefully
rather than blindly replace." `main.py`, `api/planning.py`,
`domain/availability.py`, and `domain/session.py` needed no changes at all.

**⚠️ This is a breaking contract change from Phase 1.** See "What Phase 3
will need to change" at the bottom — the Spring Boot `ActivityDto`/mappers
built in Phase 1 use the old field names and will need updating before
they're wired in. That wiring was never done (Phase 1 deliberately left
`CpSatPlanningService` unconnected), so nothing running is affected yet.

## Architecture (unchanged from Phase 1, still standalone)

```
Spring Boot  --(not yet connected)-->  Python Planning Service (FastAPI)
                                              |
                                        OR-Tools CP-SAT
```

## Time discretization (`solver/time_units.py`, new)

All date/time <-> slot arithmetic now lives in one module, per spec
Section 4. `time_of_day_to_slot`/`slot_to_time_of_day` implement the
spec's own example (08:00 -> slot 32 at 15-minute granularity).
`datetime_to_absolute_slot`/`absolute_slot_to_datetime` extend that to a
single integer axis across the whole planning period, used for deadline
comparisons and slot adjacency. `hours_to_units` implements
`requiredUnits = remainingHours × (60 / slotMinutes)` directly — no
per-activity productivity factor in Phase 2, matching the spec exactly.

## Workload model

`Activity.required_units(slot_minutes)` = `remainingHours` converted
straight to whole time units. Nothing invents study hours from task
estimates; `remainingHours` is taken at face value from the request.

## Availability model

Unchanged concept from Phase 1: `date + startTime + endTime` windows only.
**New in Phase 2:** `PlanningRequest` now validates that no two windows on
the same day overlap (spec Section 5), in addition to each window's own
start-before-end check.

## Hard constraints (`solver/constraints.py`)

| Spec constraint | Implementation |
|---|---|
| A. Availability | structural — variables only exist inside declared windows |
| B. No overlap | `add_no_overlap_constraint` |
| C. Required work upper bound | `add_required_work_upper_bound`, using `Activity.required_units` |
| D. Deadline | structural — variables never created for slots past the deadline |
| E. Planning period | structural — slots only built from windows inside the period |
| F. Session validity | structural — every slot is a fixed 15-min block by construction |
| (completed activity) | structural — no variables for `remainingHours <= 0` |

## Objective — lexicographic hierarchy (`solver/objective.py`)

Implemented as a **big-M weighted sum** (spec Section 10's fallback option,
explicitly documented and configurable per the spec's requirement):

| Tier | Weight | Spec objective |
|---|---|---|
| 2 | 10⁹ | Maximize completed required work |
| 3 | 10⁶ | Protect earlier deadlines (fresh urgency scale, NOT the old Java calculator) |
| 4 | 10⁴ | Respect activity priority |
| 5 | 10² | Consider credits (weighting only — never extra hours; capped by tier-2's hard constraint) |
| 6 | 10 | Prefer contiguous sessions (penalize session-start count) |
| 7 | 1 | Reduce context switching (reward same-activity continuation across adjacent slots) |

All weights live in `config.py::LexicographicWeights` — no magic numbers
inline. **Honest limitation, documented in code:** this is an
approximation of true lexicographic ordering, not a mathematical
guarantee at unbounded scale. For realistic weekly-planning problem sizes
it behaves as lexicographic in practice; true sequential multi-solve
lexicographic optimization is listed under Future Improvements.

Deadline urgency (tier 3) is a fresh 0-5 scale built directly into the
objective (`config.py::DeadlineUrgencyScale`), evaluated relative to the
planning period's start date — not a port of the old
`DeadlineUrgencyCalculator` Java class, per the spec's explicit
instruction not to reuse it.

## Session extraction (`solver/solution.py`, new)

Split out of `planner.py` per the required structure. Groups consecutive
assigned slots per activity into sessions, only splitting on a real
boundary (availability gap or non-contiguous time) — matches spec Section
13's example exactly. Also computes `completedRequiredMinutes` and
`unfinishedRequiredMinutes` per the Phase 2 response contract.

## Feasibility handling

Insufficient availability does **not** produce `INFEASIBLE` — the solver
schedules as much required work as it can (tier 2 of the objective) and
returns `FEASIBLE`/`OPTIMAL` with `unfinishedRequiredMinutes > 0` plus a
warning per under-scheduled activity. `INFEASIBLE`/`UNKNOWN` are reserved
for cases where the model itself has no valid assignment at all (verified
by `test_solver_basic.py`).

## Example request/response

See `docs/example_request.json` and `docs/example_response.json` — this
is the actual, verified output from running the live service (not a
hand-written illustration):

```
DSA Assignment (6h, due Sep 16) → split 08:00-12:00 + 18:00-20:00 on Sep 14
Economics Quiz (2h, due Sep 17) → 10:00-12:00 on Sep 15
completedRequiredMinutes: 480, unfinishedRequiredMinutes: 0
```

## Running the service and tests

```bash
cd planning-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000       # POST /api/v1/plan
pytest tests/ -v
```

**21 tests, all passing** — verified by actually running them, not just
written:

- `test_time_units.py` (7) — slot/time round-trips, spec's own 08:00→32 example, hours→units conversion
- `test_constraints.py` (4) — no-overlap, required-work cap, capacity, completed-activity exclusion, tested directly against the CP-SAT model
- `test_solver_basic.py` (4) — spec Tests 1, 2, 4, 5 (enough availability, insufficient availability + warning, exact time windows / no work in gaps, no overlap under contention)
- `test_solver_scenarios.py` (6) — spec Tests 3, 6, 7, 8, 9, 10 (earlier-deadline preference, multi-deadline ordering, credits-as-weighting-not-hours, irregular daily availability, splitting an activity across two windows, and the full 5-activity/3-module academic scenario)

## Known limitations

- Big-M weighted-sum objective, not strict sequential lexicographic
  optimization (see Objective section above).
- No preferred-session-length reward beyond fragmentation avoidance (spec
  Section 9 Objective 5 mentions target lengths like 30/45/60/90/120 min;
  Phase 2 only penalizes fragment *count*, doesn't reward hitting an exact
  target length — flagged as a Phase 3+ refinement, matching the spec's
  own "do not make this overly complicated in the first implementation").
- Context-switching tier (7) rewards immediate same-activity continuation;
  it does not distinguish "switch into idle time" from "switch to another
  activity" as two separate penalties — kept simple per spec Section 18's
  explicit instruction not to over-engineer Phase 2.

## What Phase 3 (Spring Boot integration) will need to change

Reporting this now, per the spec's instruction to stop and report before
touching Spring Boot files — **nothing below has been implemented yet**:

1. `ActivityDto.java` (from Phase 1) needs new fields: `activityType`
   (String, was `type`), `credits` (Double, was `moduleCredits` Integer),
   `remainingHours` (was `remainingWorkUnits`), `priority` (Integer 1-5,
   replaces the `importance`/`userPriority` HIGH/MEDIUM/LOW strings).
2. `StatisticsDto.java` needs `completedRequiredMinutes` and
   `unfinishedRequiredMinutes` fields to match the new response.
3. `ImportanceMapper.java` and `WorkloadEstimator.java` (Phase 1) will need
   rework: Phase 2 wants a numeric 1-5 priority, not HIGH/MEDIUM/LOW, and
   drops the "productivity units/hour" concept entirely — `remainingHours`
   passes straight through now.
4. `PlanningRequestMapper.java`/`PlanningResponseMapper.java` field
   mappings update accordingly; core structure (mapper pattern, HTTP
   client, orchestrating service) stays the same.
