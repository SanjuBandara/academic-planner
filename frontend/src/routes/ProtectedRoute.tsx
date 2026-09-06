import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useCurrentUser } from "../hooks/useAuth";

/**
 * Frontend route guard. This is a UX convenience only — it prevents
 * flicker/unnecessary navigation for unauthenticated users. It is NOT a
 * security boundary; the backend (Spring Security session check on every
 * protected endpoint) is the actual enforcement point.
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
    const { data, isLoading, isError } = useCurrentUser();

    if (isLoading) {
        return (
            <div className="flex min-h-screen items-center justify-center text-sm text-gray-500">
                Loading...
            </div>
        );
    }

    if (isError || !data?.authenticated) {
        return <Navigate to="/login" replace />;
    }

    return <>{children}</>;
}
