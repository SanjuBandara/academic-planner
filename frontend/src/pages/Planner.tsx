import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Layout from "../components/Layout";
import { studyPlanApi } from "../api/studyPlanApi";
import { ItemStatus, StudyPlan, TimeSlot, WeeklyPlanRequest } from "../types/academic";

import { formatLocalDate, parseLocalDate, addDays } from "../utils/dateUtils";

interface DayAvailabilityState {
  hours: number;
  timeSlots: TimeSlot[];
}

export default function Planner() {
  const queryClient = useQueryClient();
  const [lastGeneratedPlan, setLastGeneratedPlan] = useState<StudyPlan | null>(null);

  const daysOfWeek = [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY",
  ];

  const [startDate, setStartDate] = useState<string>(formatLocalDate());

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

  const rollingDays = useMemo(() => {
    const base = startDate ? parseLocalDate(startDate) : new Date();
    const todayStr = formatLocalDate(new Date());
    const tmrStr = formatLocalDate(addDays(new Date(), 1));

    return Array.from({ length: 7 }, (_, i) => {
      const d = addDays(base, i);
      const dayName = d.toLocaleDateString("en-US", { weekday: "long" }).toUpperCase();
      const dateStr = formatLocalDate(d);
      const isToday = dateStr === todayStr;
      const isTomorrow = dateStr === tmrStr;
      return {
        dayIndex: i + 1,
        dayName,
        dateStr,
        formattedShort: d.toLocaleDateString("en-US", { weekday: "short", month: "numeric", day: "numeric" }),
        isToday,
        isTomorrow,
      };
    });
  }, [startDate]);

  const { data: activePlan, isLoading: loadingPlan } = useQuery({
    queryKey: ["active-study-plan"],
    queryFn: studyPlanApi.getActivePlan,
    retry: false,
  });

  const generateMutation = useMutation({
    mutationFn: (req: WeeklyPlanRequest) => studyPlanApi.generateWeeklyPlan(req),
    onSuccess: (data) => {
      setLastGeneratedPlan(data);
      queryClient.invalidateQueries({ queryKey: ["active-study-plan"] });
    },
    onError: (err: any) => {
      const msg = err.response?.data?.message || err.message || "Unknown error";
      alert("Failed to generate plan: " + msg);
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
      const nextStart = currentSlots.length === 0 ? "09:00" : "14:00";
      const nextEnd = currentSlots.length === 0 ? "12:00" : "16:00";
      const newSlots = [...currentSlots, { startTime: nextStart, endTime: nextEnd }];
      const newSlotHours = calculateSlotHours(newSlots);
      return {
        ...prev,
        [day]: {
          ...prev[day],
          timeSlots: newSlots,
          // Auto-update hours to match the new total slot duration
          hours: newSlotHours > 0 ? Math.round(newSlotHours * 10) / 10 : prev[day]?.hours ?? 4,
        },
      };
    });
  };

  const handleSlotChange = (day: string, index: number, field: "startTime" | "endTime", value: string) => {
    setDailyState((prev) => {
      const slots = [...(prev[day]?.timeSlots || [])];
      slots[index] = { ...slots[index], [field]: value };
      const newSlotHours = calculateSlotHours(slots);
      return {
        ...prev,
        [day]: {
          ...prev[day],
          timeSlots: slots,
          // Sync hours to total slot duration whenever a slot time is changed
          hours: newSlotHours > 0 ? Math.round(newSlotHours * 10) / 10 : prev[day]?.hours ?? 4,
        },
      };
    });
  };

  const handleRemoveSlot = (day: string, index: number) => {
    setDailyState((prev) => {
      const slots = (prev[day]?.timeSlots || []).filter((_, i) => i !== index);
      const newSlotHours = calculateSlotHours(slots);
      return {
        ...prev,
        [day]: {
          ...prev[day],
          timeSlots: slots,
          // When all slots removed, reset hours to 4; otherwise sync to remaining slot total
          hours: slots.length === 0 ? 4 : newSlotHours > 0 ? Math.round(newSlotHours * 10) / 10 : prev[day]?.hours ?? 4,
        },
      };
    });
  };

  const handleGenerate = () => {
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
              Generate an intelligent weekly study schedule based on your available study time. Proportional workload allocation driven by priority and credits.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold px-3 py-1.5 rounded-xl bg-paper border border-hairline text-ink">
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
                Set required daily study hours. Optionally add time slots if you want exact scheduling windows.
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
            {rollingDays.map(({ dayIndex, dayName, formattedShort, isToday, isTomorrow }) => {
              const state = dailyState[dayName] || { hours: 4, timeSlots: [] };
              const slotHours = calculateSlotHours(state.timeSlots);
              const hasSlots = state.timeSlots.length > 0;

              return (
                <div
                  key={dayName}
                  className={`p-4 rounded-xl border transition flex flex-col justify-between space-y-3 ${
                    isToday
                      ? "bg-amber-50/70 border-gold shadow-sm ring-1 ring-gold/40"
                      : hasSlots
                      ? "bg-amber-50/30 border-gold/40 shadow-sm"
                      : "bg-paper/50 border-hairline hover:border-slate-300"
                  }`}
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-mono font-bold text-ink uppercase tracking-wider">
                          {dayName.slice(0, 3)}
                        </span>
                        <span className="text-[10px] text-slate-500 font-mono">
                          {formattedShort}
                        </span>
                      </div>
                      {isToday ? (
                        <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-ink text-gold">
                          ⚡ TODAY
                        </span>
                      ) : isTomorrow ? (
                        <span className="text-[9px] font-semibold px-1.5 py-0.5 rounded bg-slate-200 text-slate-700">
                          Tomorrow
                        </span>
                      ) : (
                        <span className="text-[9px] font-mono text-slate-400">
                          Day {dayIndex}
                        </span>
                      )}
                    </div>

                    {isToday && (
                      <p className="text-[9px] text-amber-900 bg-amber-100/70 px-1.5 py-0.5 rounded leading-tight">
                        ⏱️ Starts from now (past hours skipped)
                      </p>
                    )}

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
                          onChange={(e) => handleHourChange(dayName, parseFloat(e.target.value) || 0)}
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
                              onChange={(e) => handleSlotChange(dayName, idx, "startTime", e.target.value)}
                              className="w-16 text-[11px] font-mono p-0.5 border border-hairline rounded text-center outline-none"
                            />
                            <span className="text-slate-400">→</span>
                            <input
                              type="time"
                              value={slot.endTime}
                              onChange={(e) => handleSlotChange(dayName, idx, "endTime", e.target.value)}
                              className="w-16 text-[11px] font-mono p-0.5 border border-hairline rounded text-center outline-none"
                            />
                            <button
                              type="button"
                              onClick={() => handleRemoveSlot(dayName, idx)}
                              className="text-red-500 hover:text-red-700 p-0.5 text-xs ml-auto"
                              title="Remove slot"
                            >
                              ✕
                            </button>
                          </div>
                        ))}

                        {Math.abs(slotHours - state.hours) > 0.05 && (
                          <p className="text-[9px] text-amber-800 leading-tight">
                            {slotHours < state.hours
                              ? `ℹ You set ${state.hours}h manually — slots cover ${slotHours}h. Planner will use slots only.`
                              : `ℹ Slots total ${slotHours}h. You set ${state.hours}h — planner will cap at ${state.hours}h.`}
                          </p>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Add Slot Button */}
                  <div className="pt-2 border-t border-hairline/40">
                    <button
                      type="button"
                      onClick={() => handleAddTimeSlot(dayName)}
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
                Full deterministic schedule prioritized by credit weight, assessment urgency, and task priority.
              </p>
            </div>

            {activePlan && activePlan.items && activePlan.items.length > 0 && (
              <div className="flex items-center space-x-2 bg-paper p-1 rounded-xl border border-hairline">
                <button
                  onClick={() => setActiveTab("TABLE")}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                    activeTab === "TABLE"
                      ? "bg-ink text-gold shadow-sm font-bold"
                      : "text-slate-600 hover:text-ink"
                  }`}
                >
                  📋 Standard Table View
                </button>
                <button
                  onClick={() => setActiveTab("CARDS")}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
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
              {/* Solver Status + Warnings Banner */}
              {(lastGeneratedPlan?.solverStatus || (lastGeneratedPlan?.warnings && lastGeneratedPlan.warnings.length > 0)) && (
                <div className="space-y-2">
                  {/* Solver Status pill */}
                  {lastGeneratedPlan?.solverStatus && (
                    <div className={`flex items-center gap-2 px-4 py-2.5 rounded-xl border text-xs font-semibold ${
                      lastGeneratedPlan.solverStatus === "OPTIMAL"
                        ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                        : lastGeneratedPlan.solverStatus === "FEASIBLE"
                        ? "bg-amber-50 border-amber-200 text-amber-800"
                        : lastGeneratedPlan.solverStatus === "INFEASIBLE"
                        ? "bg-red-50 border-red-200 text-red-800"
                        : "bg-slate-50 border-slate-200 text-slate-700"
                    }`}>
                      <span>
                        {lastGeneratedPlan.solverStatus === "OPTIMAL" && "✅"}
                        {lastGeneratedPlan.solverStatus === "FEASIBLE" && "⚠️"}
                        {lastGeneratedPlan.solverStatus === "INFEASIBLE" && "❌"}
                        {lastGeneratedPlan.solverStatus === "UNKNOWN" && "❓"}
                      </span>
                      <span>
                        CP-SAT Solver: <b>{lastGeneratedPlan.solverStatus}</b>
                        {lastGeneratedPlan.solverStatus === "OPTIMAL" && " — All activities fully scheduled within your availability windows."}
                        {lastGeneratedPlan.solverStatus === "FEASIBLE" && " — A schedule was found but may not be fully optimal. Some tasks might need attention."}
                        {lastGeneratedPlan.solverStatus === "INFEASIBLE" && " — Could not build a complete schedule with the given constraints. Check availability and task durations."}
                      </span>
                    </div>
                  )}

                  {/* Scheduling Warnings */}
                  {lastGeneratedPlan?.warnings && lastGeneratedPlan.warnings.length > 0 && (
                    <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 space-y-1">
                      <p className="text-xs font-bold text-amber-800 flex items-center gap-1.5">
                        <span>⚠️</span> Scheduling Notices ({lastGeneratedPlan.warnings.length})
                      </p>
                      <ul className="space-y-1 pl-4 list-disc">
                        {lastGeneratedPlan.warnings.map((w, i) => (
                          <li key={i} className="text-xs text-amber-900">{w}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

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
                  <span className="text-slate-400">|</span>
                  <span>
                    Available: <b>{activePlan.totalAvailableHours} hrs</b>
                  </span>
                </div>
                <div className="flex items-center space-x-2">
                  <span className="px-2.5 py-0.5 rounded-full bg-gold/20 text-gold font-mono font-bold text-[10px]">
                    STATUS: {activePlan.status}
                  </span>
                </div>
              </div>

              {/* TABLE VIEW */}
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
                          <th className="py-3 px-4">Type</th>
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

                          const isAssessmentPrep = item.activityType === "ASSESSMENT_PREP" || !!item.assessmentId;
                          const activityLabel = item.activityLabel || (item.taskTitle ? `Task: ${item.taskTitle}` : item.assessmentTitle ? `Prep: ${item.assessmentTitle}` : "Study Session");

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

                              {/* Time Column (blank if mode A) */}
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
                                <div className="space-y-0.5">
                                  <span className={`font-semibold text-sm ${isCompleted ? "line-through text-slate-400" : "text-ink"}`}>
                                    {activityLabel}
                                  </span>
                                  {item.taskTitle && item.activityLabel !== item.taskTitle && (
                                    <span className="block text-[11px] text-slate-500">
                                      Task: {item.taskTitle}
                                    </span>
                                  )}
                                  {item.assessmentTitle && (
                                    <span className="block text-[11px] text-amber-700">
                                      Assessment: {item.assessmentTitle}
                                    </span>
                                  )}
                                </div>
                              </td>

                              {/* Activity Type Badge */}
                              <td className="py-3.5 px-4 whitespace-nowrap">
                                {isAssessmentPrep ? (
                                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-purple-100 text-purple-800 text-[11px] font-bold border border-purple-200">
                                    <span>📑</span>
                                    <span>Assessment Prep</span>
                                  </span>
                                ) : (
                                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[11px] font-bold border border-emerald-200">
                                    <span>✅</span>
                                    <span>Task</span>
                                  </span>
                                )}
                              </td>

                              {/* Module Column */}
                              <td className="py-3.5 px-4 whitespace-nowrap">
                                {item.moduleCode ? (
                                  <div className="flex items-center space-x-1.5">
                                    <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 bg-ink text-gold rounded">
                                      {item.moduleCode}
                                    </span>
                                    <span className="text-xs text-slate-700 font-medium truncate max-w-[140px]" title={item.moduleName}>
                                      {item.moduleName}
                                    </span>
                                  </div>
                                ) : (
                                  <span className="text-slate-400 italic text-xs">General / Standalone</span>
                                )}
                              </td>

                              {/* Actions Column */}
                              <td className="py-3.5 px-4 whitespace-nowrap text-center">
                                <div className="inline-flex items-center gap-1">
                                  {item.status !== "COMPLETED" ? (
                                    <button
                                      onClick={() => statusMutation.mutate({ id: item.id, status: "COMPLETED" })}
                                      disabled={statusMutation.isPending}
                                      className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded-lg text-[11px] transition shadow-sm"
                                    >
                                      ✓ Done
                                    </button>
                                  ) : (
                                    <button
                                      onClick={() => statusMutation.mutate({ id: item.id, status: "PLANNED" })}
                                      disabled={statusMutation.isPending}
                                      className="px-2 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded-lg text-[10px] transition"
                                    >
                                      Undo
                                    </button>
                                  )}
                                  {item.status !== "SKIPPED" && (
                                    <button
                                      onClick={() => statusMutation.mutate({ id: item.id, status: "SKIPPED" })}
                                      disabled={statusMutation.isPending}
                                      className="px-2 py-1 bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold rounded-lg text-[10px] transition"
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
                    const isAssessmentPrep = item.activityType === "ASSESSMENT_PREP" || !!item.assessmentId;
                    const activityLabel = item.activityLabel || (item.taskTitle ? item.taskTitle : item.assessmentTitle ? `${item.assessmentTitle} Prep` : "Study Session");

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
                              <span className="font-mono text-xs font-bold px-2 py-0.5 rounded-lg bg-amber-50 text-ink border border-amber-200">
                                ⏱ {item.startTime.slice(0, 5)} - {item.endTime.slice(0, 5)}
                              </span>
                            ) : (
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded-lg bg-paper text-slate-600 border border-hairline">
                                Daily Session
                              </span>
                            )}
                          </div>

                          <div className="space-y-2">
                            <div className="flex items-center justify-between gap-2">
                              {isAssessmentPrep ? (
                                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-purple-100 text-purple-800 text-[11px] font-bold border border-purple-200">
                                  <span>📑</span>
                                  <span>Assessment Prep</span>
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[11px] font-bold border border-emerald-200">
                                  <span>✅</span>
                                  <span>Task</span>
                                </span>
                              )}

                              {item.moduleCode && (
                                <span className="text-[10px] font-mono font-bold px-2 py-0.5 bg-ink text-gold rounded-lg">
                                  {item.moduleCode}
                                </span>
                              )}
                            </div>

                            <h4 className="font-bold text-ink text-base leading-snug">{activityLabel}</h4>
                            {item.moduleName && (
                              <p className="text-xs text-slate-500">{item.moduleName}</p>
                            )}
                          </div>
                        </div>

                        <div className="flex items-center justify-between pt-3 text-xs border-t border-hairline">
                          <span className="text-slate-600 font-medium">
                            Duration: <b className="text-ink text-sm">{item.plannedHours}h</b>
                          </span>
                          <div className="flex items-center space-x-1.5">
                            {item.status !== "COMPLETED" ? (
                              <button
                                onClick={() => statusMutation.mutate({ id: item.id, status: "COMPLETED" })}
                                className="px-3 py-1 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded-lg text-xs transition shadow-sm"
                              >
                                ✓ Done
                              </button>
                            ) : (
                              <button
                                onClick={() => statusMutation.mutate({ id: item.id, status: "PLANNED" })}
                                className="px-2 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded-lg text-xs transition"
                              >
                                Undo
                              </button>
                            )}
                            {item.status !== "SKIPPED" && (
                              <button
                                onClick={() => statusMutation.mutate({ id: item.id, status: "SKIPPED" })}
                                className="px-2 py-1 bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold rounded-lg text-xs transition"
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
