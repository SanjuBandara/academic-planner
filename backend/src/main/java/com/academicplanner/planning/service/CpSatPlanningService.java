package com.academicplanner.planning.service;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.client.PythonPlanningClient;
import com.academicplanner.planning.dto.PlanningRequestDto;
import com.academicplanner.planning.dto.PlanningResponseDto;
import com.academicplanner.planning.mapper.PlanningRequestMapper;
import com.academicplanner.planning.mapper.PlanningResponseMapper;
import com.academicplanner.planning.model.DailyAvailability;
import com.academicplanner.planning.result.CpSatPlanningResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * New planning entry point that delegates the actual scheduling decision to
 * the Python CP-SAT service, replacing the
 * {@code PriorityCalculator -> TimeAllocator -> FeasibilityAnalyzer -> SessionScheduler}
 * proportional-allocation pipeline.
 *
 * <p>
 * <b>Phase 2 change:</b> reads the Python service's new
 * {@code completedRequiredMinutes}/{@code unfinishedRequiredMinutes}
 * statistics and surfaces them on {@link CpSatPlanningResult}.
 *
 * <pre>
 *   CpSatPlanningService
 *        ↓
 *   PlanningRequestMapper   → PlanningRequestDto
 *        ↓
 *   PythonPlanningClient    → HTTP → Python CP-SAT service
 *        ↓
 *   PlanningResponseMapper  → List&lt;StudyPlanItem&gt;
 * </pre>
 */
@Slf4j
@Service
public class CpSatPlanningService {

    private final PlanningRequestMapper requestMapper;
    private final PythonPlanningClient pythonPlanningClient;
    private final PlanningResponseMapper responseMapper;

    public CpSatPlanningService(PlanningRequestMapper requestMapper,
            PythonPlanningClient pythonPlanningClient,
            PlanningResponseMapper responseMapper) {
        this.requestMapper = requestMapper;
        this.pythonPlanningClient = pythonPlanningClient;
        this.responseMapper = responseMapper;
    }

    public CpSatPlanningResult generatePlan(StudyPlan studyPlan,
            LocalDate startDate,
            LocalDate endDate,
            List<Assessment> assessments,
            List<Task> tasks,
            Map<LocalDate, DailyAvailability> dailyAvailability) {

        PlanningRequestDto request = requestMapper.toRequest(startDate, endDate, assessments, tasks, dailyAvailability);

        if (request.availability().isEmpty()) {
            log.warn("[CpSatPlanningService] No time-slot availability found for user's plan {} — skipping solver call",
                    studyPlan.getId());
            return new CpSatPlanningResult(List.of(), 0.0, 0.0, 0.0, 0.0, 0.0, "FEASIBLE",
                    List.of("No time-slot availability declared — provide start/end times per day, not just total hours, so the solver can place sessions."));
        }

        PlanningResponseDto response = pythonPlanningClient.generatePlan(request);

        if (!response.isUsable()) {
            log.warn("[CpSatPlanningService] Solver returned status {} for plan {}", response.status(),
                    studyPlan.getId());
            return new CpSatPlanningResult(List.of(), 0.0,
                    response.statistics().availableMinutes() / 60.0, 0.0, 0.0, 0.0,
                    response.status(), response.warnings());
        }

        Map<Long, Assessment> assessmentsById = assessments.stream()
                .collect(Collectors.toMap(Assessment::getId, Function.identity()));
        Map<Long, Task> tasksById = tasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        List<StudyPlanItem> items = responseMapper.toStudyPlanItems(studyPlan, response, assessmentsById, tasksById);

        double plannedHours = response.statistics().plannedMinutes() / 60.0;
        double availableHours = response.statistics().availableMinutes() / 60.0;
        double unallocatedHours = response.statistics().unallocatedMinutes() / 60.0;
        double completedRequiredHours = response.statistics().completedRequiredMinutes() / 60.0;
        double unfinishedRequiredHours = response.statistics().unfinishedRequiredMinutes() / 60.0;

        if (response.warnings() != null && !response.warnings().isEmpty()) {
            log.warn("[CpSatPlanningService] Planning warnings for plan {}: {}", studyPlan.getId(),
                    response.warnings());
        }

        return new CpSatPlanningResult(items, plannedHours, availableHours, unallocatedHours,
                completedRequiredHours, unfinishedRequiredHours, response.status(), response.warnings());
    }
}
