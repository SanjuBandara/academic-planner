package com.academicplanner.planning.mapper;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.dto.PlanningResponseDto;
import com.academicplanner.planning.dto.SessionDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts {@link SessionDto} entries from the Python planning service back
 * into {@link StudyPlanItem} rows the application already knows how to
 * persist and display.
 */
@Component
public class PlanningResponseMapper {

    /**
     * @param activityId "A-{id}" or "T-{id}" strings, as produced by
     *                   {@link PlanningRequestMapper}
     */
    public List<StudyPlanItem> toStudyPlanItems(StudyPlan studyPlan,
            PlanningResponseDto response,
            Map<Long, Assessment> assessmentsById,
            Map<Long, Task> tasksById) {
        return response.sessions().stream()
                .map(session -> toStudyPlanItem(studyPlan, session, assessmentsById, tasksById))
                .collect(Collectors.toList());
    }

    private StudyPlanItem toStudyPlanItem(StudyPlan studyPlan,
            SessionDto session,
            Map<Long, Assessment> assessmentsById,
            Map<Long, Task> tasksById) {
        String activityId = session.activityId();
        double plannedHours = session.durationMinutes() / 60.0;

        StudyPlanItem.StudyPlanItemBuilder builder = StudyPlanItem.builder()
                .studyPlan(studyPlan)
                .date(session.date())
                .startTime(session.startTime())
                .endTime(session.endTime())
                .plannedHours(plannedHours)
                .status(ItemStatus.PLANNED);

        if (activityId.startsWith("A-")) {
            Long id = Long.parseLong(activityId.substring(2));
            Assessment assessment = assessmentsById.get(id);
            builder.assessment(assessment);
            builder.activityType("ASSESSMENT_PREP");
            builder.activityLabel(assessment != null ? assessment.getTitle() + " Work" : "Assessment Work");
            if (assessment != null && assessment.getModule() != null) {
                builder.module(assessment.getModule());
            }
        } else if (activityId.startsWith("T-")) {
            Long id = Long.parseLong(activityId.substring(2));
            Task task = tasksById.get(id);
            builder.task(task);
            builder.activityType("TASK");
            builder.activityLabel(task != null ? task.getTitle() : "Study Task");
            if (task != null && task.getModule() != null) {
                builder.module(task.getModule());
            }
        }

        return builder.build();
    }
}
