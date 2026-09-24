import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import { semesterApi } from "../api/semesterApi";
import { assessmentApi } from "../api/assessmentApi";
import { taskApi } from "../api/taskApi";
import { studyPlanApi } from "../api/studyPlanApi";
import { aiAssistantApi } from "../api/aiAssistantApi";
import { ItemStatus } from "../types/academic";

export default function Dashboard() {

  const queryClient = useQueryClient();

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

  const { data: activePlan, isLoading: loadingActivePlan } = useQuery({
    queryKey: ["active-study-plan"],
    queryFn: studyPlanApi.getActivePlan,
    retry: false,
  });

  const { data: todaysSchedule, isLoading: loadingSchedule } = useQuery({
    queryKey: ["todays-schedule"],
    queryFn: studyPlanApi.getTodaysSchedule,
  });

  const { data: aiHintData } = useQuery({
    queryKey: ["ai-daily-hint"],
    queryFn: aiAssistantApi.getDailyHint,
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ItemStatus }) =>
      studyPlanApi.updateItemStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["todays-schedule"] });
      queryClient.invalidateQueries({ queryKey: ["active-study-plan"] });
    },
    onError: (err: any) => {
      alert("Failed to update status: " + (err.response?.data?.message || err.message || "Unknown error"));
    },
  });

  const pendingTasks = tasks.filter((t) => t.status !== "COMPLETED");
  const totalRemainingHours = pendingTasks.reduce((acc, t) => acc + (t.remainingHours || 0), 0);

  const sessions = todaysSchedule?.sessions || [];
  const completedCount = sessions.filter((s) => s.status === "COMPLETED").length;
  const totalSessions = sessions.length;
  const progressPercent = totalSessions > 0 ? Math.round((completedCount / totalSessions) * 100) : 0;

  const formattedTodayDate = todaysSchedule?.date
    ? new Date(todaysSchedule.date + "T00:00:00").toLocaleDateString("en-US", {
        weekday: "long",
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : new Date().toLocaleDateString("en-US", {
        weekday: "long",
        day: "numeric",
        month: "short",
        year: "numeric",
      });

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

        {/* AI Daily Hint Widget */}
        {aiHintData?.hint && (
          <div className="bg-gradient-to-r from-gold/10 via-white to-transparent border border-gold/30 p-4 rounded-xl flex items-center gap-3 shadow-sm">
            <span className="text-xl">✨</span>
            <p className="text-sm font-medium text-ink/80">{aiHintData.hint}</p>
          </div>
        )}

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
            <span className="text-[10px] text-slate-500 block">{totalRemainingHours} hrs remaining workload</span>
          </div>

          <div className="bg-white p-5 rounded-xl border border-hairline shadow-sm space-y-1">
            <span className="text-xs text-slate-500 font-semibold block">Today's Study Target</span>
            <span className="text-2xl font-bold font-serif text-gold-dark">
              {todaysSchedule ? `${todaysSchedule.totalPlannedHours} hrs` : "0 hrs"}
            </span>
            <span className="text-[10px] text-slate-500 block">
              {totalSessions} session{totalSessions === 1 ? "" : "s"} scheduled today
            </span>
          </div>
        </div>

        {/* Content Columns */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Today's Study Plan Section */}
          <div className="bg-white p-6 rounded-xl border border-hairline shadow-sm space-y-5 flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-start justify-between border-b border-hairline pb-3">
                <div>
                  <h2 className="text-lg font-serif font-bold text-ink flex items-center space-x-2">
                    <span>⏱️</span>
                    <span>Today's Study Plan</span>
                  </h2>
                  <p className="text-xs text-slate-500 mt-0.5">{formattedTodayDate}</p>
                </div>
                <Link to="/planner" className="text-xs text-gold-dark font-semibold hover:underline">
                  View Full Week →
                </Link>
              </div>

              {loadingSchedule || loadingActivePlan ? (
                <div className="text-center py-8 text-slate-400 text-sm animate-pulse">
                  Loading today's schedule...
                </div>
              ) : !activePlan ? (
                /* No active study plan exists */
                <div className="text-center py-8 px-4 rounded-xl bg-paper/60 border border-hairline space-y-3">
                  <span className="text-3xl block">📋</span>
                  <p className="font-semibold text-sm text-ink">No active study plan found</p>
                  <p className="text-xs text-slate-500 max-w-sm mx-auto">
                    Generate an optimized 7-day study plan starting today to see your daily schedule here.
                  </p>
                  <Link
                    to="/planner"
                    className="inline-flex items-center gap-1.5 px-4 py-2 bg-gold hover:bg-gold-dark text-ink font-bold text-xs rounded-lg shadow-sm transition"
                  >
                    <span>⚡</span>
                    <span>Generate First Study Plan</span>
                  </Link>
                </div>
              ) : totalSessions === 0 ? (
                /* Active plan exists, but nothing scheduled today */
                <div className="text-center py-8 px-4 rounded-xl bg-paper/60 border border-hairline space-y-3">
                  <span className="text-3xl block">☕</span>
                  <p className="font-semibold text-sm text-ink">No study sessions scheduled for today</p>
                  <p className="text-xs text-slate-500 max-w-sm mx-auto">
                    Take a break or generate an updated study plan in the Adaptive Planner.
                  </p>
                  <Link
                    to="/planner"
                    className="inline-flex items-center gap-1.5 px-4 py-2 bg-ink hover:bg-ink-light text-gold font-bold text-xs rounded-lg transition"
                  >
                    <span>📅</span>
                    <span>Open Planner</span>
                  </Link>
                </div>
              ) : (
                /* Today has scheduled sessions */
                <div className="space-y-4">
                  {/* Summary Bar & Progress */}
                  <div className="p-3.5 bg-paper rounded-xl border border-hairline space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-ink">
                        Progress: <b className="text-gold-dark">{completedCount} of {totalSessions} completed</b>
                      </span>
                      <span className="font-mono text-slate-500 font-bold">
                        {progressPercent}%
                      </span>
                    </div>
                    {/* Visual Progress Bar */}
                    <div className="w-full bg-slate-200 h-2 rounded-full overflow-hidden">
                      <div
                        className="bg-emerald-500 h-2 rounded-full transition-all duration-500"
                        style={{ width: `${progressPercent}%` }}
                      />
                    </div>
                    <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
                      <span>Total Time: <b>{todaysSchedule?.totalPlannedHours ?? 0} hrs</b> ({todaysSchedule?.totalPlannedMinutes ?? 0} mins)</span>
                      <span>{totalSessions - completedCount} session{totalSessions - completedCount === 1 ? "" : "s"} remaining</span>
                    </div>
                  </div>

                  {/* Sessions List */}
                  <div className="space-y-3">
                    {sessions.map((session) => {
                      const isCompleted = session.status === "COMPLETED";
                      const isSkipped = session.status === "SKIPPED";
                      const isAssessmentPrep =
                        session.activityType === "ASSESSMENT_PREP" ||
                        session.activityType?.includes("ASSESSMENT");

                      const formattedTime =
                        session.startTime && session.endTime
                          ? `${session.startTime.slice(0, 5)} - ${session.endTime.slice(0, 5)}`
                          : null;

                      return (
                        <div
                          key={session.id}
                          className={`p-3.5 rounded-xl border transition flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                            isCompleted
                              ? "bg-emerald-50/40 border-emerald-200"
                              : isSkipped
                              ? "bg-slate-100/60 border-slate-200 opacity-60"
                              : "bg-paper/70 border-hairline hover:border-slate-300"
                          }`}
                        >
                          <div className="space-y-1.5 flex-1 min-w-0">
                            {/* Tags / Badges row */}
                            <div className="flex flex-wrap items-center gap-1.5">
                              {session.moduleCode && (
                                <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 bg-ink text-gold rounded">
                                  {session.moduleCode}
                                </span>
                              )}
                              <span
                                className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                                  isAssessmentPrep
                                    ? "bg-purple-100 text-purple-800 border border-purple-200"
                                    : "bg-blue-100 text-blue-800 border border-blue-200"
                                }`}
                              >
                                {isAssessmentPrep ? "📑 Assessment Prep" : "✅ Task"}
                              </span>
                              {/* Status Badge */}
                              <span
                                className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                                  isCompleted
                                    ? "bg-emerald-100 text-emerald-800"
                                    : isSkipped
                                    ? "bg-slate-200 text-slate-600"
                                    : "bg-amber-100 text-amber-800"
                                }`}
                              >
                                {session.status}
                              </span>
                            </div>

                            {/* Title */}
                            <h4
                              className={`text-sm font-semibold truncate ${
                                isCompleted
                                  ? "line-through text-slate-500"
                                  : "text-ink"
                              }`}
                              title={session.title}
                            >
                              {session.title}
                            </h4>

                            {session.moduleName && (
                              <p className="text-xs text-slate-500 truncate">{session.moduleName}</p>
                            )}
                          </div>

                          {/* Time & Quick Actions */}
                          <div className="flex sm:flex-col items-center sm:items-end justify-between sm:justify-center gap-2 flex-shrink-0">
                            <div className="text-left sm:text-right">
                              {formattedTime ? (
                                <span className="font-mono text-xs font-bold text-ink px-2 py-0.5 rounded bg-amber-50 border border-amber-200 inline-block">
                                  {formattedTime}
                                </span>
                              ) : (
                                <span className="text-xs text-slate-600 font-semibold">
                                  Flexible Session
                                </span>
                              )}
                              <span className="text-[10px] text-slate-500 block">
                                {session.durationMinutes} mins ({Number(session.durationMinutes / 60).toFixed(1)}h)
                              </span>
                            </div>

                            {/* Action Buttons */}
                            <div className="flex items-center gap-1.5">
                              {session.status !== "COMPLETED" ? (
                                <button
                                  onClick={() =>
                                    statusMutation.mutate({ id: session.id, status: "COMPLETED" })
                                  }
                                  disabled={statusMutation.isPending}
                                  className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded-lg text-xs transition shadow-sm"
                                  title="Mark as completed"
                                >
                                  ✓ Done
                                </button>
                              ) : (
                                <button
                                  onClick={() =>
                                    statusMutation.mutate({ id: session.id, status: "PLANNED" })
                                  }
                                  disabled={statusMutation.isPending}
                                  className="px-2 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded-lg text-xs transition"
                                  title="Mark as planned"
                                >
                                  Undo
                                </button>
                              )}

                              {session.status !== "SKIPPED" && (
                                <button
                                  onClick={() =>
                                    statusMutation.mutate({ id: session.id, status: "SKIPPED" })
                                  }
                                  disabled={statusMutation.isPending}
                                  className="px-2 py-1 bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold rounded-lg text-xs transition"
                                  title="Skip this session"
                                >
                                  Skip
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
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
