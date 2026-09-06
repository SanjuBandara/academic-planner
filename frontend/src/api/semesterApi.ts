import apiClient from "./client";
import { Semester, SemesterRequest } from "../types/academic";

export const semesterApi = {
  getAll: async (): Promise<Semester[]> => {
    const res = await apiClient.get<Semester[]>("/api/semesters");
    return res.data;
  },

  getById: async (id: number): Promise<Semester> => {
    const res = await apiClient.get<Semester>(`/api/semesters/${id}`);
    return res.data;
  },

  create: async (data: SemesterRequest): Promise<Semester> => {
    const res = await apiClient.post<Semester>("/api/semesters", data);
    return res.data;
  },

  update: async (id: number, data: SemesterRequest): Promise<Semester> => {
    const res = await apiClient.put<Semester>(`/api/semesters/${id}`, data);
    return res.data;
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/semesters/${id}`);
  },
};
