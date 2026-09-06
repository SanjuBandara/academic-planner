import axios from "axios";

/**
 * Central Axios instance.
 *
 * - withCredentials: true  -> ensures the JSESSIONID session cookie (and the
 *   XSRF-TOKEN cookie) are sent on every request. There is NO token stored
 *   in localStorage/sessionStorage/React state — the backend session cookie
 *   is the sole source of truth for authentication.
 *
 * - xsrfCookieName / xsrfHeaderName -> Axios automatically reads the
 *   "XSRF-TOKEN" cookie set by Spring Security's CookieCsrfTokenRepository
 *   and echoes it back as the "X-XSRF-TOKEN" header on state-changing
 *   requests (POST/PUT/PATCH/DELETE). This satisfies the backend's CSRF
 *   double-submit-cookie requirement without any manual wiring per request.
 */
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  headers: {
    "Content-Type": "application/json",
  },
});

export default apiClient;
