package com.academicplanner.ai;

import com.academicplanner.ai.dto.PlanModificationRequest;
import com.academicplanner.ai.dto.PlanModificationResult;
import com.academicplanner.ai.model.AiAction;
import com.academicplanner.ai.tool.PlanningTool;
import com.academicplanner.entity.StudyAvailability;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.User;
import com.academicplanner.repository.StudyAvailabilityRepository;
import com.academicplanner.repository.StudyPlanItemRepository;
import com.academicplanner.repository.StudyPlanRepository;
import com.academicplanner.service.StudyPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningToolTest {

    @Mock
    private StudyPlanService studyPlanService;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private StudyPlanItemRepository studyPlanItemRepository;

    @Mock
    private StudyAvailabilityRepository availabilityRepository;

    @InjectMocks
    private PlanningTool planningTool;

    private User student1;
    private User student2;
    private StudyPlan activePlan;
    private StudyPlanItem plannedItem;
    private StudyPlanItem completedItem;

    @BeforeEach
    void setUp() {
        student1 = User.builder().id(1L).email("student1@uni.edu").build();
        student2 = User.builder().id(2L).email("student2@uni.edu").build();

        activePlan = StudyPlan.builder()
                .id(10L)
                .user(student1)
                .status(StudyPlan.PlanStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .totalAvailableHours(20.0)
                .totalPlannedHours(5.0)
                .build();

        plannedItem = StudyPlanItem.builder()
                .id(101L)
                .studyPlan(activePlan)
                .date(LocalDate.now())
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(15, 0))
                .activityLabel("IN2011 Preparation")
                .plannedHours(1.0)
                .status(StudyPlanItem.ItemStatus.PLANNED)
                .build();

        completedItem = StudyPlanItem.builder()
                .id(102L)
                .studyPlan(activePlan)
                .date(LocalDate.now())
                .activityLabel("Finished Quiz")
                .plannedHours(1.0)
                .status(StudyPlanItem.ItemStatus.COMPLETED)
                .build();
    }

    @Test
    void testRequestModification_enoughAvailableTime_returnsFeasible() {
        when(studyPlanItemRepository.findByIdAndStudyPlan_User_Id(101L, student1.getId()))
                .thenReturn(Optional.of(plannedItem));

        // 6 hours available today
        StudyAvailability availability = StudyAvailability.builder()
                .user(student1)
                .dayOfWeek(LocalDate.now().getDayOfWeek())
                .availableHours(6.0)
                .build();
        when(availabilityRepository.findByUserAndDayOfWeek(eq(student1), any()))
                .thenReturn(Optional.of(availability));

        // Currently only plannedItem (1 hr) scheduled today -> 5 hours remaining
        when(studyPlanItemRepository.findAllByStudyPlan_IdAndDateOrderByStartTimeAsc(activePlan.getId(), LocalDate.now()))
                .thenReturn(List.of(plannedItem));

        PlanModificationRequest request = PlanModificationRequest.builder()
                .action(AiAction.INCREASE_ACTIVITY_TIME)
                .activityId(101L)
                .additionalMinutes(120) // 2 hours
                .build();

        PlanModificationResult result = planningTool.requestPlanModification(request, student1);

        assertThat(result.getStatus()).isEqualTo("FEASIBLE");
        assertThat(result.getAllocatedMinutes()).isEqualTo(120);
        assertThat(result.getProposalId()).isNotNull();
    }

    @Test
    void testRequestModification_insufficientAvailableTime_returnsPartiallyFeasible() {
        when(studyPlanItemRepository.findByIdAndStudyPlan_User_Id(101L, student1.getId()))
                .thenReturn(Optional.of(plannedItem));

        // 2 hours total available today
        StudyAvailability availability = StudyAvailability.builder()
                .user(student1)
                .dayOfWeek(LocalDate.now().getDayOfWeek())
                .availableHours(2.0)
                .build();
        when(availabilityRepository.findByUserAndDayOfWeek(eq(student1), any()))
                .thenReturn(Optional.of(availability));

        // 1 hour currently scheduled -> only 1 hour (60 min) remaining
        when(studyPlanItemRepository.findAllByStudyPlan_IdAndDateOrderByStartTimeAsc(activePlan.getId(), LocalDate.now()))
                .thenReturn(List.of(plannedItem));

        PlanModificationRequest request = PlanModificationRequest.builder()
                .action(AiAction.INCREASE_ACTIVITY_TIME)
                .activityId(101L)
                .additionalMinutes(120) // requesting 120 mins
                .build();

        PlanModificationResult result = planningTool.requestPlanModification(request, student1);

        assertThat(result.getStatus()).isEqualTo("PARTIALLY_FEASIBLE");
        assertThat(result.getAllocatedMinutes()).isEqualTo(60);
    }

    @Test
    void testRequestModification_noAvailableTime_returnsInfeasible() {
        when(studyPlanItemRepository.findByIdAndStudyPlan_User_Id(101L, student1.getId()))
                .thenReturn(Optional.of(plannedItem));

        // 1 hour available today
        StudyAvailability availability = StudyAvailability.builder()
                .user(student1)
                .dayOfWeek(LocalDate.now().getDayOfWeek())
                .availableHours(1.0)
                .build();
        when(availabilityRepository.findByUserAndDayOfWeek(eq(student1), any()))
                .thenReturn(Optional.of(availability));

        // 1 hour already scheduled -> 0 min remaining
        when(studyPlanItemRepository.findAllByStudyPlan_IdAndDateOrderByStartTimeAsc(activePlan.getId(), LocalDate.now()))
                .thenReturn(List.of(plannedItem));

        PlanModificationRequest request = PlanModificationRequest.builder()
                .action(AiAction.INCREASE_ACTIVITY_TIME)
                .activityId(101L)
                .additionalMinutes(120)
                .build();

        PlanModificationResult result = planningTool.requestPlanModification(request, student1);

        assertThat(result.getStatus()).isEqualTo("INFEASIBLE");
        assertThat(result.getAllocatedMinutes()).isEqualTo(0);
        assertThat(result.getReason()).contains("No unused study time is available");
    }

    @Test
    void testRequestModification_completedActivity_returnsInfeasible() {
        when(studyPlanItemRepository.findByIdAndStudyPlan_User_Id(102L, student1.getId()))
                .thenReturn(Optional.of(completedItem));

        PlanModificationRequest request = PlanModificationRequest.builder()
                .action(AiAction.INCREASE_ACTIVITY_TIME)
                .activityId(102L)
                .additionalMinutes(60)
                .build();

        PlanModificationResult result = planningTool.requestPlanModification(request, student1);

        assertThat(result.getStatus()).isEqualTo("INFEASIBLE");
        assertThat(result.getReason()).contains("already completed");
    }

    @Test
    void testRequestModification_otherStudentActivity_returnsInfeasible() {
        // student2 tries to modify student1's item
        when(studyPlanItemRepository.findByIdAndStudyPlan_User_Id(101L, student2.getId()))
                .thenReturn(Optional.empty());

        PlanModificationRequest request = PlanModificationRequest.builder()
                .action(AiAction.INCREASE_ACTIVITY_TIME)
                .activityId(101L)
                .additionalMinutes(60)
                .build();

        PlanModificationResult result = planningTool.requestPlanModification(request, student2);

        assertThat(result.getStatus()).isEqualTo("INFEASIBLE");
        assertThat(result.getReason()).contains("not found in your active schedule");
    }
}
