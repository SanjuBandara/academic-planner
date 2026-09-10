import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import { semesterApi } from "../api/semesterApi";
import { assessmentApi } from "../api/assessmentApi";
import { taskApi } from "../api/taskApi";
import { studyPlanApi } from "../api/studyPlanApi";

export default function Dashboard() {
  const { data: semesters = [] } = useQuery({
    queryKey: ["semesters"],
    queryFn: semesterApi.getAll,
  });

  const activeSemester = semesters.find((s) => s.status === "ACTIVE") || semesters[0];

  const { data: upcomingAssessments = [] } = useQuery({
    queryKey: ["upcoming-assessments"],
    queryFn: () => assessmentApi.getUpcoming(14),
  });

  const { data: tasks = [] } = useQuery({
    queryKey: ["tasks"],
    queryFn: taskApi.getAll,
  });

  const { data: todaysItems = [] } = useQuery({
    queryKey: ["todays-study-items"],
    queryFn: studyPlanApi.getTodaysItems,
  });

  const pendingTasks = tasks.filter((t) => t.status !== "COMPLETED");
  const totalRemainingHours = pendingTasks.reduce((acc, t) => acc + (t.remainingHours || 0), 0);

  return (
    <Layout>
      <div className="space-y-8">
        {/* Welcome Header */}
        <div className="bg-gradient-to-r from-ink via-ink-light to-ink p-6 sm:p-8 rounded-2xl text-white shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          <div className="space-y-2">
            <span className="text-xs uppercase font-bold text-gold tracking-widest">
              Student Dashboard
            </span>
            <h1 className="text-3xl sm:text-4xl font-serif font-bold">
              Welcome Back to Academic Planner
            </h1>
            <p className="text-slate-300 text-sm max-w-xl">
              {activeSemester
                ? `Active Term: ${activeSemester.name} (${activeSemester.startDate} to ${activeSemester.endDate})`
                : "Get started by creating your semester and adding modules!"}
            </p>
          </div>
          <Link
            to="/planner"
            className="px-6 py-3 bg-gold hover:bg-gold-dark text-ink font-bold text-sm rounded-xl shadow-lg transition whitespace-nowrap"
          >
            ⚡ Open Adaptive Planner
          </Link>
        </div>

        {/* Top Metric Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white p-5 rounded-xl border border-hairline shadow-sm space-y-1">
            <span className="text-xs text-slate-500 font-semibold block">Active Semester</span>
            <span className="text-2xl font-bold font-serif text-ink">
              {activeSemester ? activeSemester.name : "None"}
            </span>
            <span className="text-[10px] text-gold-dark font-medium block">
              {semesters.length} Semesters Total
            </span>
          </div>

          <div className="bg-white p-5 rounded-xl border border-hairline shadow-sm space-y-1">
            <span className="text-xs text-slate-500 font-semibold block">Upcoming Assessments</span>
            <span className="text-2xl font-bold font-serif text-ink">
              {upcomingAssessments.length}
            </span>
            <span className="text-[10px] text-amber-600 font-medium block">Due in next 14 days</span>
          </div>

          <div className="bg-white p-5 rounded-xl border border-hairline shadow-sm space-y-1">
            <span className="text-xs text-slate-500 font-semibold block">Pending Tasks</span>
            <span className="text-2xl font-bold font-serif text-ink">{pendingTasks.length}</span>
            <span className="text-[10px] text-slate-500 block">Out of {tasks.length} total tasks</span>
          </div>

          <div className="bg-white p-5 rounded-xl border border-hairline shadow-sm space-y-1">
            <span className="text-xs text-slate-500 font-semibold block">Remaining Workload</span>
            <span className="text-2xl font-bold font-serif text-gold-dark">
              {totalRemainingHours} hrs
            </span>
            <span className="text-[10px] text-slate-500 block">Across active tasks</span>
          </div>
        </div>

        {/* Content Columns */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Today's Scheduled Sessions */}
          <div className="bg-white p-6 rounded-xl border border-hairline shadow-sm space-y-4">
            <div className="flex items-center justify-between border-b border-hairline pb-3">
              <h2 className="text-lg font-serif font-bold text-ink flex items-center space-x-2">
                <span>⏱️</span>
                <span>Today's Study Schedule</span>
              </h2>
              <Link to="/planner" className="text-xs text-gold-dark font-semibold hover:underline">
                View Full Week →
              </Link>
            </div>

            {todaysItems.length === 0 ? (
              <div className="text-center py-6 text-slate-500 text-sm">
                No study sessions scheduled for today. Generate or update your weekly plan in the Adaptive Planner.
              </div>
            ) : (
              <div className="space-y-3">
                {todaysItems.map((item) => (
                  <div
                    key={item.id}
                    className="p-3.5 rounded-lg border border-hairline bg-paper/60 flex items-center justify-between"
                  >
                    <div>
                      <div className="flex items-center space-x-2">
                        <span className="text-xs font-mono font-bold px-2 py-0.5 bg-ink text-gold rounded">
                          {item.moduleCode}
                        </span>
                        <span className="font-semibold text-sm text-ink">{item.moduleName}</span>
                      </div>
                      {item.assessmentTitle && (
                        <p className="text-xs text-slate-500 mt-1">Target: {item.assessmentTitle}</p>
                      )}
                    </div>
                    <div className="text-right">
                      <span className="text-xs font-mono font-bold text-slate-700 block">
                        {item.startTime && item.endTime
                          ? `${item.startTime.slice(0, 5)} - ${item.endTime.slice(0, 5)}`
                          : "Daily Session"}
                      </span>
                      <span className="text-[10px] text-slate-500 block">{item.plannedHours} hrs</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Urgent Upcoming Assessments */}
          <div className="bg-white p-6 rounded-xl border border-hairline shadow-sm space-y-4">
            <div className="flex items-center justify-between border-b border-hairline pb-3">
              <h2 className="text-lg font-serif font-bold text-ink flex items-center space-x-2">
                <span>🎯</span>
                <span>Upcoming Assessments</span>
              </h2>
              <Link to="/assessments" className="text-xs text-gold-dark font-semibold hover:underline">
                All Assessments →
              </Link>
            </div>

            {upcomingAssessments.length === 0 ? (
              <div className="text-center py-6 text-slate-500 text-sm">
                No upcoming assessments due in the next 14 days.
              </div>
            ) : (
              <div className="space-y-3">
                {upcomingAssessments.slice(0, 4).map((a) => (
                  <div
                    key={a.id}
                    className="p-3.5 rounded-lg border border-hairline bg-paper/60 flex items-center justify-between"
                  >
                    <div>
                      <div className="flex items-center space-x-2">
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-ink/10 text-ink uppercase">
                          {a.type}
                        </span>
                        <h4 className="font-bold text-sm text-ink">{a.title}</h4>
                      </div>
                      <p className="text-xs text-slate-500 mt-1">
                        Module: <b>{a.moduleName}</b> • Weight: <b>{a.weight}%</b>
                      </p>
                    </div>
                    <div className="text-right">
                      <span className="text-xs font-semibold text-red-600 block">
                        Due {new Date(a.dueDateTime).toLocaleDateString()}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}
