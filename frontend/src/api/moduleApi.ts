import apiClient from "./client";
import { Module, ModuleRequest } from "../types/academic";

export const moduleApi = {
  getBySemester: async (semesterId: number): Promise<Module[]> => {
    const res = await apiClient.get<Module[]>(`/api/semesters/${semesterId}/modules`);
    return res.data;
  },

  getAll: async (): Promise<Module[]> => {
    const res = await apiClient.get<Module[]>("/api/modules");
    return res.data;
  },

  getById: async (id: number): Promise<Module> => {
    const res = await apiClient.get<Module>(`/api/modules/${id}`);
    return res.data;
  },

  create: async (semesterId: number, data: ModuleRequest): Promise<Module> => {
    const res = await apiClient.post<Module>(`/api/semesters/${semesterId}/modules`, data);
    return res.data;
  },

  update: async (id: number, data: ModuleRequest): Promise<Module> => {
    const res = await apiClient.put<Module>(`/api/modules/${id}`, data);
    return res.data;
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/modules/${id}`);
  },
};
