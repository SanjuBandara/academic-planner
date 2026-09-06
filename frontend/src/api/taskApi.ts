import apiClient from "./client";
import { Task, TaskProgressUpdate, TaskRequest } from "../types/academic";

export const taskApi = {
  getAll: async (): Promise<Task[]> => {
    const res = await apiClient.get<Task[]>("/api/tasks");
    return res.data;
  },

  getById: async (id: number): Promise<Task> => {
    const res = await apiClient.get<Task>(`/api/tasks/${id}`);
    return res.data;
  },

  create: async (data: TaskRequest): Promise<Task> => {
    const res = await apiClient.post<Task>("/api/tasks", data);
    return res.data;
  },

  update: async (id: number, data: TaskRequest): Promise<Task> => {
    const res = await apiClient.put<Task>(`/api/tasks/${id}`, data);
    return res.data;
  },

  updateProgress: async (id: number, update: TaskProgressUpdate): Promise<Task> => {
    const res = await apiClient.patch<Task>(`/api/tasks/${id}/progress`, update);
    return res.data;
  },

  toggleComplete: async (id: number): Promise<Task> => {
    const res = await apiClient.patch<Task>(`/api/tasks/${id}/toggle`);
    return res.data;
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/tasks/${id}`);
  },
};
