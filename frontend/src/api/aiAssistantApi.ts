import apiClient from "./client";
import { AiActionRequest, AiActionResponse, AiChatRequest, AiChatResponse } from "../types/ai";

export const aiAssistantApi = {
  sendMessage: async (message: string): Promise<AiChatResponse> => {
    const payload: AiChatRequest = { message };
    const res = await apiClient.post<AiChatResponse>("/api/ai/assistant/chat", payload);
    return res.data;
  },

  confirmAction: async (proposalId: string, confirmed: boolean): Promise<AiActionResponse> => {
    const payload: AiActionRequest = { proposalId, confirmed };
    const res = await apiClient.post<AiActionResponse>("/api/ai/assistant/action/confirm", payload);
    return res.data;
  },

  getChatHistory: async (): Promise<{ messages: any[] }> => {
    const res = await apiClient.get<{ messages: any[] }>("/api/ai/assistant/history");
    return res.data;
  },

  getDailyHint: async (): Promise<{ hint: string }> => {
    const res = await apiClient.get<{ hint: string }>("/api/ai/assistant/daily-hint");
    return res.data;
  },
};
