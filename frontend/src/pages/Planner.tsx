import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Layout from "../components/Layout";
import { studyPlanApi } from "../api/studyPlanApi";
import { ItemStatus, TimeSlot, WeeklyPlanRequest } from "../types/academic";

interface DayAvailabilityState {
  hours: number;
  timeSlots: TimeSlot[];
}

export default function Planner() {
  const queryClient = useQueryClient();

  const daysOfWeek = [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY",
  ];

  const [startDate, setStartDate] = useState<string>(
    new Date().toISOString().split("T")[0]
  );

  const [activeTab, setActiveTab] = useState<"TABLE" | "CARDS">("TABLE");

  // Daily availability state: hours (required) + optional timeSlots
  const [dailyState, setDailyState] = useState<Record<string, DayAvailabilityState>>({
    MONDAY: { hours: 6, timeSlots: [] },
    TUESDAY: { hours: 4, timeSlots: [] },
    WEDNESDAY: { hours: 4, timeSlots: [] },
    THURSDAY: { hours: 4, timeSlots: [] },
    FRIDAY: { hours: 4, timeSlots: [] },
    SATURDAY: { hours: 6, timeSlots: [] },
    SUNDAY: { hours: 6, timeSlots: [] },
  });

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
    onError: (err: any) => {
      alert("Failed to generate plan: " + (err.response?.data?.message || err.message || "Unknown error"));
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ItemStatus }) =>
      studyPlanApi.updateItemStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["active-study-plan"] });
    },
    onError: (err: any) => {
      alert("Failed to update item: " + (err.response?.data?.message || err.message || "Unknown error"));
    },
  });

  // Calculate total slot hours for a given day
  const calculateSlotHours = (slots: TimeSlot[]): number => {
    return slots.reduce((sum, slot) => {
      if (!slot.startTime || !slot.endTime) return sum;
      const [startH, startM] = slot.startTime.split(":").map(Number);
      const [endH, endM] = slot.endTime.split(":").map(Number);
      const startMinutes = startH * 60 + startM;
      const endMinutes = endH * 60 + endM;
      if (endMinutes <= startMinutes) return sum;
      return sum + (endMinutes - startMinutes) / 60;
    }, 0);
  };

  const handleHourChange = (day: string, val: number) => {
    setDailyState((prev) => ({
      ...prev,
      [day]: {
        ...prev[day],
        hours: Math.max(0, val),
      },
    }));
  };

  const handleAddTimeSlot = (day: string) => {
    setDailyState((prev) => {
      const currentSlots = prev[day]?.timeSlots || [];
      // Default initial slot suggestion
      const nextStart = currentSlots.length === 0 ? "09:00" : "14:00";
      const nextEnd = currentSlots.length === 0 ? "12:00" : "16:00";
      return {
        ...prev,
        [day]: {
          ...prev[day],
          timeSlots: [...currentSlots, { startTime: nextStart, endTime: nextEnd }],
        },
      };
    });
  };

  const handleSlotChange = (day: string, index: number, field: "startTime" | "endTime", value: string) => {
    setDailyState((prev) => {
      const slots = [...(prev[day]?.timeSlots || [])];
      slots[index] = { ...slots[index], [field]: value };
      return {
        ...prev,
        [day]: {
          ...prev[day],
          timeSlots: slots,
        },
      };
    });
  };

  const handleRemoveSlot = (day: string, index: number) => {
    setDailyState((prev) => {
      const slots = (prev[day]?.timeSlots || []).filter((_, i) => i !== index);
      return {
        ...prev,
        [day]: {
          ...prev[day],
          timeSlots: slots,
        },
      };
    });
  };

  const handleGenerate = () => {
    // Build payload with dailyAvailability
    const dailyAvailability: Record<string, { availableHours: number; timeSlots: TimeSlot[] }> = {};
    const simpleAvailability: Record<string, number> = {};

    daysOfWeek.forEach((day) => {
      const state = dailyState[day] || { hours: 4, timeSlots: [] };
      simpleAvailability[day] = state.hours;
      dailyAvailability[day] = {
        availableHours: state.hours,
        timeSlots: state.timeSlots.filter(
          (s) => s.startTime && s.endTime && s.startTime < s.endTime
        ),
      };
    });

    generateMutation.mutate({
      startDate,
      availability: simpleAvailability,
      dailyAvailability,
    });
  };

  const totalDeclaredWeeklyHours = useMemo(() => {
    return daysOfWeek.reduce((sum, d) => sum + (dailyState[d]?.hours || 0), 0);
  }, [dailyState]);

  const anySlotsConfigured = useMemo(() => {
    return daysOfWeek.some((d) => (dailyState[d]?.timeSlots?.length || 0) > 0);
  }, [dailyState]);

  return (
    <Layout>
      <div className="space-y-8 pb-12">
        <div className="border-b border-hairline pb-4 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-3xl font-serif font-bold text-ink">Adaptive Study Planner</h1>
            <p className="text-slate-600 text-sm mt-1">
              Generate an intelligent weekly study schedule based on your required daily available hours and optional exact time slots.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold px-3 py-1.5 rounded-md bg-paper border border-hairline text-ink">
              Weekly Capacity: <b>{totalDeclaredWeeklyHours}h</b>
            </span>
          </div>
        </div>

        {/* Plan Generation Control Panel */}
        <div className="bg-white p-6 rounded-2xl border border-hairline shadow-sm space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-hairline pb-4">
            <div>
              <h2 className="text-lg font-serif font-bold text-ink flex items-center space-x-2">
                <span>⚙️</span>
                <span>Configure Weekly Availability</span>
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Set required daily hours. Optionally add time slots if you want exact session windows.
              </p>
            </div>
            <div className="flex items-center space-x-2">
              <label className="text-xs font-semibold text-slate-700 whitespace-nowrap">Plan Start Date:</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="px-3 py-1.5 border border-hairline rounded-lg text-xs outline-none bg-paper font-mono focus:border-gold"
              />
            </div>
          </div>

          {/* Daily Availability Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-7 gap-4">
            {daysOfWeek.map((day) => {
              const state = dailyState[day] || { hours: 4, timeSlots: [] };
              const slotHours = calculateSlotHours(state.timeSlots);
              const hasSlots = state.timeSlots.length > 0;

              return (
                <div
                  key={day}
                  className={`p-4 rounded-xl border transition flex flex-col justify-between space-y-3 ${
                    hasSlots
                      ? "bg-amber-50/40 border-gold/50 shadow-sm"
                      : "bg-paper/50 border-hairline hover:border-slate-300"
                  }`}
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-mono font-bold text-ink uppercase tracking-wider">
                        {day.slice(0, 3)}
                      </span>
                      {hasSlots && (
                        <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-gold text-ink">
                          {state.timeSlots.length} slot{state.timeSlots.length > 1 ? "s" : ""}
                        </span>
                      )}
                    </div>

                    {/* Required Daily Hours */}
                    <div>
                      <label className="text-[10px] font-semibold text-slate-500 block uppercase">
                        Available Hours <span className="text-red-500">*</span>
                      </label>
                      <div className="flex items-center space-x-1 mt-1">
                        <input
                          type="number"
                          min="0"
                          max="24"
                          step="0.5"
                          value={state.hours}
                          onChange={(e) => handleHourChange(day, parseFloat(e.target.value) || 0)}
                          className="w-full text-center font-bold text-ink text-base bg-white border border-hairline rounded-lg py-1.5 outline-none focus:border-gold"
                        />
                        <span className="text-xs font-semibold text-slate-500">h</span>
                      </div>
                    </div>

                    {/* Optional Time Slots Section */}
                    {hasSlots && (
                      <div className="pt-2 border-t border-hairline/60 space-y-2">
                        <div className="flex items-center justify-between text-[11px]">
                          <span className="font-semibold text-slate-700">Time Slots:</span>
                          <span className="text-[10px] font-mono text-slate-500">{slotHours}h total</span>
                        </div>

                        {state.timeSlots.map((slot, idx) => (
                          <div key={idx} className="flex items-center gap-1 bg-white p-1.5 rounded border border-hairline text-xs">
                            <input
                              type="time"
                              value={slot.startTime}
                              onChange={(e) => handleSlotChange(day, idx, "startTime", e.target.value)}
                              className="w-16 text-[11px] font-mono p-0.5 border border-hairline rounded text-center outline-none"
                            />
                            <span className="text-slate-400">→</span>
                            <input
                              type="time"
                              value={slot.endTime}
                              onChange={(e) => handleSlotChange(day, idx, "endTime", e.target.value)}
                              className="w-16 text-[11px] font-mono p-0.5 border border-hairline rounded text-center outline-none"
                            />
                            <button
                              type="button"
                              onClick={() => handleRemoveSlot(day, idx)}
                              className="text-red-500 hover:text-red-700 p-0.5 text-xs ml-auto"
                              title="Remove slot"
                            >
                              ✕
                            </button>
                          </div>
                        ))}

                        {/* Informative message if slot hours differ from declared hours */}
                        {slotHours !== state.hours && (
                          <p className="text-[9px] text-amber-800 leading-tight">
                            {slotHours < state.hours
                              ? `ℹ Declared ${state.hours}h, but slots provide ${slotHours}h windows.`
                              : `ℹ Slots total ${slotHours}h. Planner caps at declared ${state.hours}h.`}
                          </p>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Add Slot Button */}
                  <div className="pt-2 border-t border-hairline/40">
                    <button
                      type="button"
                      onClick={() => handleAddTimeSlot(day)}
                      className="w-full py-1 text-[11px] font-semibold text-gold-dark hover:text-ink hover:bg-gold/20 rounded transition flex items-center justify-center gap-1"
                    >
                      <span>+</span>
                      <span>{hasSlots ? "Add Slot" : "Add Time Slot"}</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 pt-4 border-t border-hairline">
            <div className="text-xs text-slate-500">
              {anySlotsConfigured ? (
                <span className="inline-flex items-center gap-1.5 text-emerald-700 font-medium">
                  <span>✓</span> Time slots configured for specific days. Sessions will be placed inside those windows.
                </span>
              ) : (
                <span className="inline-flex items-center gap-1.5 text-slate-600">
                  <span>ℹ</span> Planning with daily hours only. The table's <b>Time</b> column will remain blank without invented times.
                </span>
              )}
            </div>

            <button
              onClick={handleGenerate}
              disabled={generateMutation.isPending}
              className="w-full sm:w-auto px-8 py-3 bg-gold hover:bg-gold-dark text-ink font-bold text-sm rounded-xl shadow-md transition disabled:opacity-50 flex items-center justify-center space-x-2"
            >
              <span>⚡</span>
              <span>{generateMutation.isPending ? "Generating Plan..." : "Generate Adaptive Study Schedule"}</span>
            </button>
          </div>
        </div>

        {/* Active Schedule Output */}
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-xl font-serif font-bold text-ink flex items-center space-x-2">
                <span>📅</span>
                <span>Your Generated Weekly Schedule</span>
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Full deterministic schedule prioritized by credit weight, assessment urgency, and task workload.
              </p>
            </div>

            {activePlan && activePlan.items && activePlan.items.length > 0 && (
              <div className="flex items-center space-x-2 bg-paper p-1 rounded-lg border border-hairline">
                <button
                  onClick={() => setActiveTab("TABLE")}
                  className={`px-3 py-1 rounded text-xs font-semibold transition ${
                    activeTab === "TABLE"
                      ? "bg-ink text-gold shadow-sm font-bold"
                      : "text-slate-600 hover:text-ink"
                  }`}
                >
                  📋 Standard Table View
                </button>
                <button
                  onClick={() => setActiveTab("CARDS")}
                  className={`px-3 py-1 rounded text-xs font-semibold transition ${
                    activeTab === "CARDS"
                      ? "bg-ink text-gold shadow-sm font-bold"
                      : "text-slate-600 hover:text-ink"
                  }`}
                >
                  🗂️ Card Grid View
                </button>
              </div>
            )}
          </div>

          {loadingPlan ? (
            <div className="text-slate-500 animate-pulse py-8 text-center bg-white rounded-xl border border-hairline">
              Loading active study plan...
            </div>
          ) : !activePlan || !activePlan.items || activePlan.items.length === 0 ? (
            <div className="bg-white p-12 rounded-2xl border border-hairline text-center space-y-3 shadow-sm">
              <span className="text-4xl block">📆</span>
              <h3 className="text-lg font-serif font-bold text-ink">No active study schedule generated yet</h3>
              <p className="text-sm text-slate-500 max-w-md mx-auto">
                Configure your daily available hours above and click <b>Generate Adaptive Study Schedule</b> to create your optimized plan.
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              {/* Plan Period Summary Banner */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs bg-ink text-white p-4 rounded-xl shadow-md">
                <div className="flex items-center space-x-4">
                  <span>
                    Plan Period: <b>{activePlan.startDate}</b> to <b>{activePlan.endDate}</b>
                  </span>
                  <span className="text-slate-400">|</span>
                  <span>
                    Total Planned Study Time: <b className="text-gold">{activePlan.totalPlannedHours} hrs</b>
                  </span>
                </div>
                <div className="flex items-center space-x-2">
                  <span className="px-2 py-0.5 rounded bg-gold/20 text-gold font-mono font-bold text-[10px]">
                    STATUS: {activePlan.status}
                  </span>
                </div>
              </div>

              {/* TABLE VIEW: Section 19 Required Table Structure */}
              {activeTab === "TABLE" ? (
                <div className="bg-white rounded-2xl border border-hairline shadow-sm overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                      <thead>
                        <tr className="bg-paper border-b border-hairline text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                          <th className="py-3 px-4">Day</th>
                          <th className="py-3 px-4">Time</th>
                          <th className="py-3 px-4 text-right">Duration</th>
                          <th className="py-3 px-4">Study Activity</th>
                          <th className="py-3 px-4">Task</th>
                          <th className="py-3 px-4">Assessment</th>
                          <th className="py-3 px-4">Module</th>
                          <th className="py-3 px-4 text-center">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-hairline text-xs">
                        {activePlan.items.map((item) => {
                          const dateObj = new Date(item.date + "T00:00:00");
                          const dayName = dateObj.toLocaleDateString("en-US", { weekday: "long" });
                          const isCompleted = item.status === "COMPLETED";
                          const isSkipped = item.status === "SKIPPED";

                          // Formatted scheduled time or blank if not provided
                          const formattedTime =
                            item.startTime && item.endTime
                              ? `${item.startTime.slice(0, 5)} - ${item.endTime.slice(0, 5)}`
                              : "";

                          // Study Activity representation
                          const studyActivity = item.taskTitle
                            ? `Task: ${item.taskTitle}`
                            : item.assessmentTitle
                            ? `Prep: ${item.assessmentTitle}`
                            : `${item.moduleName || item.moduleCode} Study Session`;

                          return (
                            <tr
                              key={item.id}
                              className={`hover:bg-slate-50/70 transition ${
                                isCompleted
                                  ? "bg-green-50/30 text-slate-400"
                                  : isSkipped
                                  ? "bg-slate-100/50 text-slate-400"
                                  : ""
                              }`}
                            >
                              {/* Day Column */}
                              <td className="py-3.5 px-4 whitespace-nowrap">
                                <span className="font-bold text-ink block">{dayName}</span>
                                <span className="text-[10px] text-slate-400 font-mono">{item.date}</span>
                              </td>

                              {/* Time Column (blank if no exact time slots were provided) */}
                              <td className="py-3.5 px-4 whitespace-nowrap font-mono text-ink">
                                {formattedTime ? (
                                  <span className="px-2 py-0.5 rounded bg-amber-50 text-ink border border-amber-200 text-xs font-semibold">
                                    {formattedTime}
                                  </span>
                                ) : (
                                  <span className="text-slate-300 text-xs select-none"></span>
                                )}
                              </td>

                              {/* Duration Column */}
                              <td className="py-3.5 px-4 whitespace-nowrap text-right font-bold text-ink">
                                {item.plannedHours}h
                              </td>

                              {/* Study Activity Column */}
                              <td className="py-3.5 px-4 max-w-xs font-medium text-ink">
                                <span className={isCompleted ? "line-through text-slate-400" : ""}>
                                  {studyActivity}
                                </span>
                              </td>

                              {/* Task Column */}
                              <td className="py-3.5 px-4 text-slate-600 max-w-xs">
                                {item.taskTitle ? (
                                  <span className="font-medium text-ink">{item.taskTitle}</span>
                                ) : (
                                  <span className="text-slate-300">—</span>
                                )}
                              </td>

                              {/* Assessment Column */}
                              <td className="py-3.5 px-4 text-slate-600 max-w-xs">
                                {item.assessmentTitle ? (
                                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-gold/15 text-gold-dark text-[11px] font-semibold border border-gold/30">
                                    <span>📑</span>
                                    <span>{item.assessmentTitle}</span>
                                  </span>
                                ) : (
                                  <span className="text-slate-300">—</span>
                                )}
                              </td>

                              {/* Module Column */}
                              <td className="py-3.5 px-4 whitespace-nowrap">
                                <div className="flex items-center space-x-1.5">
                                  <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 bg-ink text-gold rounded">
                                    {item.moduleCode}
                                  </span>
                                  <span className="text-xs text-slate-700 font-medium truncate max-w-[140px]" title={item.moduleName}>
                                    {item.moduleName}
                                  </span>
                                </div>
                              </td>

                              {/* Actions Column */}
                              <td className="py-3.5 px-4 whitespace-nowrap text-center">
                                <div className="inline-flex items-center gap-1">
                                  {item.status !== "COMPLETED" ? (
                                    <button
                                      onClick={() => statusMutation.mutate({ id: item.id, status: "COMPLETED" })}
                                      disabled={statusMutation.isPending}
                                      className="px-2.5 py-1 bg-green-600 hover:bg-green-700 text-white font-semibold rounded text-[11px] transition shadow-sm"
                                    >
                                      ✓ Done
                                    </button>
                                  ) : (
                                    <button
                                      onClick={() => statusMutation.mutate({ id: item.id, status: "PLANNED" })}
                                      disabled={statusMutation.isPending}
                                      className="px-2 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded text-[10px] transition"
                                    >
                                      Undo
                                    </button>
                                  )}
                                  {item.status !== "SKIPPED" && (
                                    <button
                                      onClick={() => statusMutation.mutate({ id: item.id, status: "SKIPPED" })}
                                      disabled={statusMutation.isPending}
                                      className="px-2 py-1 bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold rounded text-[10px] transition"
                                    >
                                      Skip
                                    </button>
                                  )}
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                /* CARDS VIEW */
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {activePlan.items.map((item) => {
                    const dateObj = new Date(item.date + "T00:00:00");
                    const dayName = dateObj.toLocaleDateString("en-US", { weekday: "long" });

                    return (
                      <div
                        key={item.id}
                        className={`p-5 rounded-2xl border transition shadow-sm flex flex-col justify-between space-y-4 ${
                          item.status === "COMPLETED"
                            ? "bg-green-50/40 border-green-200 opacity-85"
                            : item.status === "SKIPPED"
                            ? "bg-slate-50 border-slate-200 opacity-70"
                            : "bg-white border-hairline hover:border-gold"
                        }`}
                      >
                        <div className="space-y-3">
                          <div className="flex items-center justify-between text-xs border-b border-hairline pb-2.5">
                            <div>
                              <span className="font-bold text-ink text-sm block">{dayName}</span>
                              <span className="text-[10px] text-slate-400 font-mono">{item.date}</span>
                            </div>

                            {item.startTime && item.endTime ? (
                              <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-amber-50 text-ink border border-amber-200">
                                ⏱ {item.startTime.slice(0, 5)} - {item.endTime.slice(0, 5)}
                              </span>
                            ) : (
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-paper text-slate-600 border border-hairline">
                                Daily Session
                              </span>
                            )}
                          </div>

                          <div>
                            <div className="flex items-center space-x-2">
                              <span className="text-[10px] font-mono font-bold px-2 py-0.5 bg-ink text-gold rounded">
                                {item.moduleCode}
                              </span>
                              <h4 className="font-bold text-ink text-sm truncate">{item.moduleName}</h4>
                            </div>

                            {item.taskTitle && (
                              <p className="text-xs text-slate-600 mt-2">
                                <span className="font-semibold text-ink">Task:</span> {item.taskTitle}
                              </p>
                            )}

                            {item.assessmentTitle && (
                              <div className="mt-2 inline-flex items-center gap-1 px-2 py-0.5 rounded bg-gold/15 text-gold-dark text-[11px] font-semibold border border-gold/30">
                                <span>📑</span>
                                <span>Target: {item.assessmentTitle}</span>
                              </div>
                            )}
                          </div>
                        </div>

                        <div className="flex items-center justify-between pt-3 text-xs border-t border-hairline">
                          <span className="text-slate-600 font-medium">
                            Duration: <b className="text-ink">{item.plannedHours}h</b>
                          </span>
                          <div className="flex items-center space-x-1.5">
                            {item.status !== "COMPLETED" ? (
                              <button
                                onClick={() => statusMutation.mutate({ id: item.id, status: "COMPLETED" })}
                                className="px-2.5 py-1 bg-green-600 hover:bg-green-700 text-white font-semibold rounded text-xs transition shadow-sm"
                              >
                                ✓ Done
                              </button>
                            ) : (
                              <button
                                onClick={() => statusMutation.mutate({ id: item.id, status: "PLANNED" })}
                                className="px-2 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded text-xs transition"
                              >
                                Undo
                              </button>
                            )}
                            {item.status !== "SKIPPED" && (
                              <button
                                onClick={() => statusMutation.mutate({ id: item.id, status: "SKIPPED" })}
                                className="px-2 py-1 bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold rounded text-xs transition"
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
              )}
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}
