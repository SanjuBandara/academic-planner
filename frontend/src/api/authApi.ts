import apiClient from "./client";
import type {
  AuthResponse,
  CurrentUserResponse,
  LoginPayload,
  RegisterPayload,
} from "../types/auth";

export async function registerUser(payload: RegisterPayload): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/api/auth/register", payload);
  return data;
}

export async function loginUser(payload: LoginPayload): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/api/auth/login", payload);
  return data;
}

export async function fetchCurrentUser(): Promise<CurrentUserResponse> {
  const { data } = await apiClient.get<CurrentUserResponse>("/api/auth/me");
  return data;
}

export async function logoutUser(): Promise<void> {
  await apiClient.post("/api/auth/logout");
}
