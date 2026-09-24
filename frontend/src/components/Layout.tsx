import { ReactNode, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useCurrentUser, useLogout } from "../hooks/useAuth";
import { AiAssistant } from "./ai/AiAssistant";

interface LayoutProps {
  children: ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  const [isAiAssistantOpen, setIsAiAssistantOpen] = useState(false);
  
  const { data: user } = useCurrentUser();
  const logoutMutation = useLogout();
  const location = useLocation();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await logoutMutation.mutateAsync();
      navigate("/login");
    } catch (err) {
      console.error("Logout failed:", err);
    }
  };

  const navItems = [
    { label: "Dashboard", path: "/dashboard", icon: "📊" },
    { label: "Semesters & Modules", path: "/semesters", icon: "📚" },
    { label: "Assessments", path: "/assessments", icon: "🎯" },
    { label: "Task Board", path: "/tasks", icon: "✅" },
    { label: "Adaptive Study Planner", path: "/planner", icon: "⚡" },
  ];

  return (
    <div className="min-h-screen bg-paper flex flex-col text-ink">
      {/* Top Navbar */}
      <header className="bg-ink text-white border-b border-hairline sticky top-0 z-50 shadow-md">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <span className="text-2xl">🎓</span>
            <Link to="/dashboard" className="text-xl font-bold font-serif tracking-wide text-gold">
              Academic Planner
            </Link>
          </div>

          <div className="flex items-center space-x-4">
            {user?.email && (
              <span className="text-sm font-medium text-slate-300 hidden md:inline">
                👤 {user.email}
              </span>
            )}
            <button
              onClick={handleLogout}
              className="px-3 py-1.5 text-xs font-semibold rounded bg-red-600/80 hover:bg-red-600 text-white transition duration-150"
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      {/* Navigation Sub-Bar */}
      <nav className="bg-ink-light border-b border-hairline/20 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex overflow-x-auto space-x-1 py-2 scrollbar-none">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center space-x-2 px-4 py-2 text-sm font-medium rounded-md whitespace-nowrap transition duration-150 ${
                  isActive
                    ? "bg-gold text-ink font-semibold shadow"
                    : "text-slate-200 hover:bg-ink/50 hover:text-white"
                }`}
              >
                <span>{item.icon}</span>
                <span>{item.label}</span>
              </Link>
            );
          })}
        </div>
      </nav>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>

      {/* Footer */}
      <footer className="border-t border-hairline py-4 bg-white/50 text-center text-xs text-slate-500">
        Academic Workload & Adaptive Study Planning System • Java 21 & React
      </footer>

      {/* Floating Action Button for AI Assistant */}
      <button
        onClick={() => setIsAiAssistantOpen(true)}
        className="fixed bottom-6 right-6 w-14 h-14 bg-ink hover:bg-ink-light text-gold rounded-full shadow-2xl flex items-center justify-center text-2xl transition-transform hover:scale-105 z-40"
        title="Open AI Assistant"
      >
        ✨
      </button>

      {/* AI Assistant Right Sidebar Overlay */}
      {isAiAssistantOpen && (
        <div className="fixed inset-0 z-50 flex justify-end">
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-ink/30 backdrop-blur-sm transition-opacity"
            onClick={() => setIsAiAssistantOpen(false)}
          ></div>
          
          {/* Sidebar Drawer */}
          <div className="relative w-full max-w-sm md:max-w-md h-full bg-white shadow-2xl flex flex-col animate-slide-in-right">
            {/* Close Button Header */}
            <div className="flex items-center justify-between p-3 border-b border-hairline bg-slate-50">
              <span className="font-serif font-bold text-ink">AI Assistant</span>
              <button
                onClick={() => setIsAiAssistantOpen(false)}
                className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-slate-200 text-slate-500 transition"
                title="Close AI Assistant"
              >
                ✖
              </button>
            </div>
            
            {/* AI Assistant Component container */}
            <div className="flex-1 overflow-hidden [&>div]:h-full [&>div]:border-0 [&>div]:rounded-none">
              <AiAssistant /> 
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
