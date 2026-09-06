import apiClient from "./client";
import { Assessment, AssessmentRequest } from "../types/academic";

export const assessmentApi = {
  getByModule: async (moduleId: number): Promise<Assessment[]> => {
    const res = await apiClient.get<Assessment[]>(`/api/modules/${moduleId}/assessments`);
    return res.data;
  },

  getUpcoming: async (days = 14): Promise<Assessment[]> => {
    const res = await apiClient.get<Assessment[]>(`/api/assessments/upcoming?days=${days}`);
    return res.data;
  },

  getById: async (id: number): Promise<Assessment> => {
    const res = await apiClient.get<Assessment>(`/api/assessments/${id}`);
    return res.data;
  },

  create: async (moduleId: number, data: AssessmentRequest): Promise<Assessment> => {
    const res = await apiClient.post<Assessment>(`/api/modules/${moduleId}/assessments`, data);
    return res.data;
  },

  update: async (id: number, data: AssessmentRequest): Promise<Assessment> => {
    const res = await apiClient.put<Assessment>(`/api/assessments/${id}`, data);
    return res.data;
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/assessments/${id}`);
  },
};
