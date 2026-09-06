import apiClient from "./client";
import { ItemStatus, StudyPlan, StudyPlanItem, WeeklyPlanRequest } from "../types/academic";

export const studyPlanApi = {
  generateWeeklyPlan: async (request: WeeklyPlanRequest): Promise<StudyPlan> => {
    const res = await apiClient.post<StudyPlan>("/api/study-plans/generate", request);
    return res.data;
  },

  getActivePlan: async (): Promise<StudyPlan> => {
    const res = await apiClient.get<StudyPlan>("/api/study-plans/active");
    return res.data;
  },

  getTodaysItems: async (): Promise<StudyPlanItem[]> => {
    const res = await apiClient.get<StudyPlanItem[]>("/api/study-plans/today");
    return res.data;
  },

  updateItemStatus: async (itemId: number, status: ItemStatus): Promise<StudyPlanItem> => {
    const res = await apiClient.patch<StudyPlanItem>(`/api/study-plans/items/${itemId}/status?status=${status}`);
    return res.data;
  },
};
