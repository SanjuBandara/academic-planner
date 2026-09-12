import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Layout from "../components/Layout";
import { taskApi } from "../api/taskApi";
import { moduleApi } from "../api/moduleApi";
import { TaskRequest, PriorityLevel } from "../types/academic";

const PRIORITY_OPTIONS: { level: PriorityLevel; label: string; value: number; desc: string; badgeCls: string; activeCls: string }[] = [
  { level: "HIGH", label: "High", value: 2.5, desc: "Highest planning weight (2.5x credits)", badgeCls: "bg-rose-100 text-rose-700 border-rose-200", activeCls: "bg-rose-600 text-white border-rose-600" },
  { level: "MEDIUM", label: "Medium", value: 2.0, desc: "Standard planning weight (2.0x credits)", badgeCls: "bg-amber-100 text-amber-800 border-amber-200", activeCls: "bg-amber-500 text-white border-amber-500" },
  { level: "LOW", label: "Low", value: 1.0, desc: "Relaxed planning weight (1.0x credits)", badgeCls: "bg-slate-100 text-slate-700 border-slate-200", activeCls: "bg-slate-600 text-white border-slate-600" },
];

export default function Tasks() {
  const queryClient = useQueryClient();
  const [filterStatus, setFilterStatus] = useState<string>("ALL");

  const [showModal, setShowModal] = useState(false);
  const [workModalTask, setWorkModalTask] = useState<number | null>(null);
  const [workedHoursInput, setWorkedHoursInput] = useState<number>(1);

  const [form, setForm] = useState<TaskRequest>({
    title: "",
    description: "",
    moduleId: undefined,
    estimatedHours: 2.0,
    priority: "MEDIUM",
    dueDateTime: undefined,
    status: "TODO",
  });

  const { data: tasks = [], isLoading } = useQuery({
    queryKey: ["tasks"],
    queryFn: taskApi.getAll,
  });

  const { data: modules = [] } = useQuery({
    queryKey: ["all-modules"],
    queryFn: moduleApi.getAll,
  });

  const selectedModule = useMemo(
    () => modules.find((m) => m.id === form.moduleId) || null,
    [modules, form.moduleId]
  );

  const createMutation = useMutation({
    mutationFn: (payload: TaskRequest) => taskApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      setShowModal(false);
      setForm({
        title: "",
        description: "",
        moduleId: undefined,
        estimatedHours: 2.0,
        priority: "MEDIUM",
        dueDateTime: undefined,
        status: "TODO",
      });
    },
  });

  const toggleMutation = useMutation({
    mutationFn: taskApi.toggleComplete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });

  const progressMutation = useMutation({
    mutationFn: ({ id, hours }: { id: number; hours: number }) =>
      taskApi.updateProgress(id, { workedHours: hours }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      setWorkModalTask(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => taskApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
    onError: (err: any) => {
      alert("Failed to delete task: " + (err.response?.data?.message || err.message || "Unknown error"));
    },
  });

  const filteredTasks = tasks.filter((t) => {
    if (filterStatus === "ALL") return true;
    return t.status === filterStatus;
  });

  return (
    <Layout>
      <div className="space-y-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-hairline pb-4">
          <div>
            <h1 className="text-3xl font-serif font-bold text-ink">Academic Task Board</h1>
            <p className="text-slate-600 text-sm mt-1">
              Organize your academic tasks. Choose your priority (High, Medium, Low) to steer time allocation in your study plans.
            </p>
          </div>
          <button
            onClick={() => setShowModal(true)}
            className="px-4 py-2 bg-ink hover:bg-ink-light text-white font-medium rounded-xl shadow transition flex items-center justify-center space-x-2 text-sm"
          >
            <span>➕</span>
            <span>Create New Task</span>
          </button>
        </div>

        {/* Filter Bar */}
        <div className="flex items-center space-x-2 border-b border-hairline pb-4">
          {["ALL", "TODO", "IN_PROGRESS", "COMPLETED"].map((st) => (
            <button
              key={st}
              onClick={() => setFilterStatus(st)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                filterStatus === st
                  ? "bg-gold text-ink font-bold shadow-sm"
                  : "bg-white border border-hairline text-slate-600 hover:bg-slate-50"
              }`}
            >
              {st === "ALL" ? "All Tasks" : st.replace("_", " ")}
            </button>
          ))}
        </div>

        {/* Task Cards Grid */}
        {isLoading ? (
          <div className="text-slate-500 animate-pulse">Loading tasks...</div>
        ) : filteredTasks.length === 0 ? (
          <div className="bg-white p-8 rounded-xl border border-hairline text-center text-slate-500">
            No tasks found. Click "Create New Task" to add your first study task!
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredTasks.map((t) => {
              const priMeta = PRIORITY_OPTIONS.find((p) => p.level === t.priority) || PRIORITY_OPTIONS[1];

              return (
                <div
                  key={t.id}
                  className={`p-5 rounded-2xl border transition shadow-sm flex flex-col justify-between ${
                    t.status === "COMPLETED"
                      ? "bg-slate-50 border-slate-200 opacity-80"
                      : "bg-white border-hairline hover:border-gold"
                  }`}
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                      <span
                        className={`text-xs font-bold px-2.5 py-0.5 rounded-full border ${priMeta.badgeCls}`}
                        title={`Weight multiplier: ${priMeta.value}x credits`}
                      >
                        {priMeta.label} Priority ({priMeta.value}x)
                      </span>

                      {t.moduleName && (
                        <span className="text-[10px] font-mono font-semibold px-2 py-0.5 bg-ink text-gold rounded-lg">
                          {t.moduleCode || t.moduleName}
                        </span>
                      )}
                    </div>

                    <h3
                      className={`font-semibold text-ink text-base ${
                        t.status === "COMPLETED" ? "line-through text-slate-400" : ""
                      }`}
                    >
                      {t.title}
                    </h3>

                    {t.description && (
                      <p className="text-xs text-slate-500 line-clamp-2">{t.description}</p>
                    )}

                    {t.dueDateTime && (
                      <p className="text-[11px] text-slate-500 flex items-center gap-1">
                        <span>⏰</span>
                        <span>Due: {new Date(t.dueDateTime).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })}</span>
                      </p>
                    )}
                  </div>

                  <div className="mt-4 pt-3 border-t border-hairline/60 space-y-3">
                    <div className="flex items-center justify-between text-xs text-slate-600">
                      <span>
                        Workload: <b>{t.remainingHours}h</b> remaining {t.estimatedHours ? `/ ${t.estimatedHours}h` : ""}
                      </span>
                      <button
                        onClick={() => {
                          setWorkModalTask(t.id);
                          setWorkedHoursInput(1);
                        }}
                        disabled={t.status === "COMPLETED"}
                        className="text-xs text-gold-dark hover:underline font-semibold disabled:opacity-40"
                      >
                        ⏱ Log Work
                      </button>
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <button
                        onClick={() => toggleMutation.mutate(t.id)}
                        className={`px-3 py-1 text-xs font-semibold rounded-lg transition ${
                          t.status === "COMPLETED"
                            ? "bg-slate-200 text-slate-700 hover:bg-slate-300"
                            : "bg-emerald-600 text-white hover:bg-emerald-700 shadow-sm"
                        }`}
                      >
                        {t.status === "COMPLETED" ? "Mark Pending" : "✓ Complete"}
                      </button>
                      <button
                        onClick={() => {
                          if (window.confirm(`Are you sure you want to delete "${t.title}"?`)) {
                            deleteMutation.mutate(t.id);
                          }
                        }}
                        disabled={deleteMutation.isPending}
                        className="text-xs text-red-500 hover:text-red-800 disabled:opacity-50 transition"
                      >
                        {deleteMutation.isPending ? "Deleting..." : "Delete"}
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Modal Create */}
        {showModal && (
          <div className="fixed inset-0 bg-ink/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-hairline space-y-4">
              <div className="flex items-center justify-between border-b border-hairline pb-3">
                <h3 className="text-xl font-serif font-bold text-ink">
                  Create Study Task
                </h3>
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="w-8 h-8 flex items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-ink transition text-lg"
                >
                  ✕
                </button>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  createMutation.mutate(form);
                }}
                className="space-y-4"
              >
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Task Title *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Read Chapter 4 / Implement Binary Search"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm outline-none focus:border-gold"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Description (Optional)
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Notes, topics covered, or specific exercises..."
                    value={form.description || ""}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm outline-none focus:border-gold resize-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Module (Optional)
                  </label>
                  <select
                    value={form.moduleId || ""}
                    onChange={(e) => {
                      const modId = e.target.value ? parseInt(e.target.value) : undefined;
                      setForm({ ...form, moduleId: modId });
                    }}
                    className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm outline-none bg-white"
                  >
                    <option value="">-- Standalone Task (No Module) --</option>
                    {modules.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.code} - {m.name} ({m.credits} credits)
                      </option>
                    ))}
                  </select>
                </div>

                {/* Priority Selection Buttons (User Selected) */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                    Priority * <span className="font-normal text-slate-500">(influences planned study hours)</span>
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    {PRIORITY_OPTIONS.map((opt) => {
                      const isSelected = form.priority === opt.level;
                      return (
                        <button
                          key={opt.level}
                          type="button"
                          onClick={() => setForm({ ...form, priority: opt.level })}
                          className={`py-2 px-3 rounded-xl border-2 text-xs font-bold transition text-center flex flex-col items-center justify-center gap-0.5 ${
                            isSelected
                              ? opt.activeCls + " shadow-sm scale-105"
                              : "border-slate-200 bg-slate-50 text-slate-700 hover:border-slate-300"
                          }`}
                        >
                          <span>{opt.label}</span>
                          <span className={`text-[10px] font-normal ${isSelected ? "text-white/80" : "text-slate-500"}`}>
                            {opt.value}x Weight
                          </span>
                        </button>
                      );
                    })}
                  </div>
                  <p className="text-[11px] text-slate-500 mt-1">
                    Planning Weight: <b>{PRIORITY_OPTIONS.find(p => p.level === form.priority)?.value} × {selectedModule ? selectedModule.credits : 1} credits = {((PRIORITY_OPTIONS.find(p => p.level === form.priority)?.value || 2) * (selectedModule ? selectedModule.credits : 1)).toFixed(1)}</b>
                  </p>
                </div>

                {/* Estimated Work Hours */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Estimated Hours Cap (Optional)
                  </label>
                  <input
                    type="number"
                    step="0.5"
                    min="0.5"
                    placeholder="2.0"
                    value={form.estimatedHours ?? ""}
                    onChange={(e) => setForm({ ...form, estimatedHours: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm outline-none bg-white"
                  />
                  <span className="text-[10px] text-slate-400 mt-0.5 block">
                    Acts as an upper cap so planning won't assign more hours than needed.
                  </span>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Due Date & Time (Optional)
                  </label>
                  <input
                    type="datetime-local"
                    value={form.dueDateTime || ""}
                    onChange={(e) => setForm({ ...form, dueDateTime: e.target.value || undefined })}
                    className="w-full px-3.5 py-2 border border-slate-300 rounded-xl text-sm outline-none bg-white"
                  />
                </div>

                <div className="flex justify-end space-x-3 pt-4 border-t border-hairline">
                  <button
                    type="button"
                    onClick={() => setShowModal(false)}
                    className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-ink rounded-lg"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={createMutation.isPending}
                    className="px-5 py-2 bg-gold hover:bg-gold-dark text-ink font-bold text-xs rounded-xl shadow transition disabled:opacity-50"
                  >
                    {createMutation.isPending ? "Saving..." : "Save Task"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Modal Log Progress */}
        {workModalTask && (
          <div className="fixed inset-0 bg-ink/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-xs w-full p-6 shadow-xl border border-hairline space-y-4 text-center">
              <h3 className="text-base font-bold text-ink">Log Work Session</h3>
              <p className="text-xs text-slate-600">
                Enter hours spent working on this task to update remaining workload.
              </p>
              <input
                type="number"
                step="0.25"
                min="0.25"
                value={workedHoursInput}
                onChange={(e) => setWorkedHoursInput(parseFloat(e.target.value) || 0.25)}
                className="w-full text-center px-3 py-2 border border-hairline rounded-xl text-lg font-bold text-ink outline-none"
              />
              <div className="flex justify-center space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setWorkModalTask(null)}
                  className="px-3 py-1.5 text-xs text-slate-600 hover:text-ink rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => progressMutation.mutate({ id: workModalTask, hours: workedHoursInput })}
                  className="px-4 py-1.5 bg-gold hover:bg-gold-dark text-ink font-semibold text-xs rounded-xl shadow"
                >
                  Deduct Hours
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
}
