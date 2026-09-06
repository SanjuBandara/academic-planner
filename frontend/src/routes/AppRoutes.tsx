import { Navigate, Route, Routes } from "react-router-dom";
import Login from "../pages/auth/Login";
import Register from "../pages/auth/Register";
import Dashboard from "../pages/Dashboard";
import Semesters from "../pages/Semesters";
import Assessments from "../pages/Assessments";
import Tasks from "../pages/Tasks";
import Planner from "../pages/Planner";
import ProtectedRoute from "./ProtectedRoute";
import GuestRoute from "./GuestRoute";

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />

      <Route
        path="/login"
        element={
          <GuestRoute>
            <Login />
          </GuestRoute>
        }
      />
      <Route
        path="/register"
        element={
          <GuestRoute>
            <Register />
          </GuestRoute>
        }
      />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/semesters"
        element={
          <ProtectedRoute>
            <Semesters />
          </ProtectedRoute>
        }
      />

      <Route
        path="/assessments"
        element={
          <ProtectedRoute>
            <Assessments />
          </ProtectedRoute>
        }
      />

      <Route
        path="/tasks"
        element={
          <ProtectedRoute>
            <Tasks />
          </ProtectedRoute>
        }
      />

      <Route
        path="/planner"
        element={
          <ProtectedRoute>
            <Planner />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
