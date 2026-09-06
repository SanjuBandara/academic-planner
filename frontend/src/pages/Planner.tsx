import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Layout from "../components/Layout";
import { studyPlanApi } from "../api/studyPlanApi";
import { ItemStatus, WeeklyPlanRequest } from "../types/academic";

export default function Planner() {
  const queryClient = useQueryClient();

  const [availability, setAvailability] = useState<Record<string, number>>({
    MONDAY: 4,
    TUESDAY: 4,
    WEDNESDAY: 4,
    THURSDAY: 4,
    FRIDAY: 4,
    SATURDAY: 6,
    SUNDAY: 6,
  });

  const [startDate, setStartDate] = useState<string>(
    new Date().toISOString().split("T")[0]
  );

  const { data: activePlan, isLoading: loadingPlan } = useQuery({
    queryKey: ["active-study-plan"],
    queryFn: studyPlanApi.getActivePlan,
    retry: false,
  });

  const generateMutation = useMutation({
    mutationFn: (req: WeeklyPlanRequest) => studyPlanApi.generateWeeklyPlan(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["active-study-plan"] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ItemStatus }) =>
      studyPlanApi.updateItemStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["active-study-plan"] });
    },
  });

  const handleGenerate = () => {
    generateMutation.mutate({ startDate, availability });
  };

  const daysOfWeek = [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY",
  ];

  return (
    <Layout>
      <div className="space-y-8">
        <div className="border-b border-hairline pb-4">
          <h1 className="text-3xl font-serif font-bold text-ink">Adaptive Study Planner</h1>
          <p className="text-slate-600 text-sm mt-1">
            Generate an automated time-blocked weekly study schedule prioritized by credit weight, assessment urgency, and task workload.
          </p>
        </div>

        {/* Plan Generation Control Panel */}
        <div className="bg-white p-6 rounded-xl border border-hairline shadow-sm space-y-6">
          <h2 className="text-lg font-serif font-bold text-ink flex items-center space-x-2">
            <span>⚙️</span>
            <span>Configure Weekly Availability (Hours / Day)</span>
          </h2>

          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3">
            {daysOfWeek.map((day) => (
              <div key={day} className="bg-paper p-3 rounded-lg border border-hairline text-center space-y-1">
                <span className="text-[10px] font-bold text-slate-500 block uppercase">
                  {day.slice(0, 3)}
                </span>
                <input
                  type="number"
                  min="0"
                  max="14"
                  step="0.5"
                  value={availability[day] ?? 4}
                  onChange={(e) =>
                    setAvailability({
                      ...availability,
                      [day]: parseFloat(e.target.value) || 0,
                    })
                  }
                  className="w-full text-center font-bold text-ink border border-hairline rounded py-1 text-sm outline-none bg-white"
                />
                <span className="text-[10px] text-slate-400 block">hrs</span>
              </div>
            ))}
          </div>

          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 pt-4 border-t border-hairline">
            <div className="flex items-center space-x-2">
              <label className="text-xs font-semibold text-slate-700">Start Date:</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="px-3 py-1.5 border border-hairline rounded text-xs outline-none bg-white font-mono"
              />
            </div>
            <button
              onClick={handleGenerate}
              disabled={generateMutation.isPending}
              className="w-full sm:w-auto px-6 py-2.5 bg-gold hover:bg-gold-dark text-ink font-bold text-sm rounded-lg shadow-md transition disabled:opacity-50"
            >
              {generateMutation.isPending ? "Generating Plan..." : "⚡ Generate Adaptive Study Schedule"}
            </button>
          </div>
        </div>

        {/* Active Schedule Output */}
        <div className="space-y-4">
          <h2 className="text-xl font-serif font-bold text-ink flex items-center space-x-2">
            <span>📅</span>
            <span>Your Generated Weekly Schedule</span>
          </h2>

          {loadingPlan ? (
            <div className="text-slate-500 animate-pulse">Loading active plan...</div>
          ) : !activePlan || !activePlan.items || activePlan.items.length === 0 ? (
            <div className="bg-white p-8 rounded-xl border border-hairline text-center text-slate-500">
              No active study schedule generated yet. Configure your daily availability above and click "Generate Adaptive Study Schedule".
            </div>
          ) : (
            <div className="space-y-6">
              <div className="flex items-center justify-between text-xs bg-ink text-white p-4 rounded-xl shadow">
                <span>
                  Plan Period: <b>{activePlan.startDate}</b> to <b>{activePlan.endDate}</b>
                </span>
                <span>
                  Total Planned Study Time: <b>{activePlan.totalPlannedHours} hrs</b>
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {activePlan.items.map((item) => (
                  <div
                    key={item.id}
                    className={`p-4 rounded-xl border transition shadow-sm space-y-3 ${
                      item.status === "COMPLETED"
                        ? "bg-green-50/50 border-green-200"
                        : item.status === "SKIPPED"
                        ? "bg-red-50/50 border-red-200"
                        : "bg-white border-hairline hover:border-gold"
                    }`}
                  >
                    <div className="flex items-center justify-between text-xs border-b border-hairline pb-2">
                      <span className="font-bold text-slate-700">📅 {item.date}</span>
                      <span className="font-mono bg-paper px-2 py-0.5 rounded text-ink border border-hairline">
                        {item.startTime.slice(0, 5)} - {item.endTime.slice(0, 5)}
                      </span>
                    </div>

                    <div>
                      <span className="text-[10px] font-mono font-bold px-2 py-0.5 bg-ink text-gold rounded">
                        {item.moduleCode}
                      </span>
                      <h4 className="font-bold text-ink text-sm mt-1">{item.moduleName}</h4>
                      {item.assessmentTitle && (
                        <p className="text-xs text-gold-dark font-medium mt-0.5">
                          Target: {item.assessmentTitle}
                        </p>
                      )}
                    </div>

                    <div className="flex items-center justify-between pt-2 text-xs border-t border-hairline">
                      <span className="text-slate-500">Duration: <b>{item.plannedHours}h</b></span>
                      <div className="flex items-center space-x-1">
                        {item.status !== "COMPLETED" && (
                          <button
                            onClick={() =>
                              statusMutation.mutate({ id: item.id, status: "COMPLETED" })
                            }
                            className="px-2 py-1 bg-green-600 hover:bg-green-700 text-white font-semibold rounded text-[10px]"
                          >
                            ✓ Done
                          </button>
                        )}
                        {item.status !== "SKIPPED" && (
                          <button
                            onClick={() =>
                              statusMutation.mutate({ id: item.id, status: "SKIPPED" })
                            }
                            className="px-2 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded text-[10px]"
                          >
                            Skip
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}
