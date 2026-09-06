import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useCurrentUser } from "../hooks/useAuth";

/**
 * Redirects already-authenticated users away from guest-only pages
 * (login/register) to the dashboard.
 */
export default function GuestRoute({ children }: { children: ReactNode }) {
    const { data, isLoading } = useCurrentUser();

    if (isLoading) {
        return (
            <div className="flex min-h-screen items-center justify-center text-sm text-gray-500">
                Loading...
            </div>
        );
    }

    if (data?.authenticated) {
        return <Navigate to="/dashboard" replace />;
    }

    return <>{children}</>;
}
