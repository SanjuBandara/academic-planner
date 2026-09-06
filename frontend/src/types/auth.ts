export interface RegisterPayload {
  email: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface AuthResponse {
  id: number;
  email: string;
  authenticated: boolean;
}

export interface CurrentUserResponse {
  authenticated: boolean;
  id: number | null;
  email: string | null;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  message: string;
  errors?: Record<string, string>;
}
