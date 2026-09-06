import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchCurrentUser, loginUser, logoutUser, registerUser } from "../api/authApi";
import type { LoginPayload, RegisterPayload } from "../types/auth";

export const CURRENT_USER_QUERY_KEY = ["auth", "currentUser"] as const;

/**
 * Central authentication hook. Session state is derived entirely from the
 * backend (GET /api/auth/me) — there is no locally persisted token. On
 * app startup this query runs once and tells the router whether to show
 * the app or redirect to /login.
 */
export function useCurrentUser() {
  return useQuery({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: fetchCurrentUser,
    retry: false,
    // A 401 is an expected "not logged in" state, not a transient error.
    staleTime: 5 * 60 * 1000,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: LoginPayload) => loginUser(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY });
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: (payload: RegisterPayload) => registerUser(payload),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => logoutUser(),
    onSuccess: () => {
      queryClient.setQueryData(CURRENT_USER_QUERY_KEY, {
        authenticated: false,
        id: null,
        email: null,
      });
      queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY });
    },
  });
}
