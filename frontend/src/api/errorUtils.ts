import { AxiosError } from "axios";
import type { ApiErrorResponse } from "../types/auth";

/**
 * Extracts a user-friendly message from an error thrown by apiClient.
 * Backend errors follow the consistent ErrorResponse shape defined in
 * GlobalExceptionHandler: { timestamp, status, message, errors }.
 */
export function getApiErrorMessage(error: unknown, fallback = "Something went wrong. Please try again."): string {
    if (error instanceof AxiosError) {
        const data = error.response?.data as ApiErrorResponse | undefined;
        if (data?.errors && Object.keys(data.errors).length > 0) {
            return Object.values(data.errors)[0];
        }
        if (data?.message) {
            return data.message;
        }
    }
    return fallback;
}
