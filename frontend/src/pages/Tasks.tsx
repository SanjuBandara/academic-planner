import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Layout from "../components/Layout";
import { taskApi } from "../api/taskApi";
import { moduleApi } from "../api/moduleApi";
import { assessmentApi } from "../api/assessmentApi";
import { TaskRequest, PriorityLevel } from "../types/academic";

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
    assessmentId: undefined,
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

  // Query assessments for the currently selected module in the modal
  const { data: moduleAssessments = [] } = useQuery({
    queryKey: ["module-assessments", form.moduleId],
    queryFn: () => (form.moduleId ? assessmentApi.getByModule(form.moduleId) : Promise.resolve([])),
    enabled: !!form.moduleId,
  });

  const selectedAssessment = useMemo(
    () => moduleAssessments.find((a) => a.id === form.assessmentId) || null,
    [moduleAssessments, form.assessmentId]
  );

  const selectedModule = useMemo(
    () => modules.find((m) => m.id === form.moduleId) || null,
    [modules, form.moduleId]
  );

  // Automatic calculation of workload hours and priority
  const autoPreview = useMemo(() => {
    // 1. Workload hours
    let hours = 2.0;
    if (selectedAssessment) {
      if (selectedAssessment.type === "EXAM" || selectedAssessment.type === "PROJECT") hours = 3.0;
      else if (selectedAssessment.type === "ASSIGNMENT") hours = 2.0;
      else if (selectedAssessment.type === "QUIZ") hours = 1.5;
    } else if (selectedModule && selectedModule.credits >= 4) {
      hours = 2.5;
    }

    // 2. Academic priority
    let priority: PriorityLevel = "MEDIUM";
    const effectiveDeadline = form.dueDateTime || selectedAssessment?.dueDateTime;

    if (effectiveDeadline) {
      const diffHours = (new Date(effectiveDeadline).getTime() - Date.now()) / (1000 * 60 * 60);
      if (diffHours <= 48) {
        priority = "CRITICAL";
      } else if (diffHours <= 24 * 6) {
        priority = "HIGH";
      } else if (diffHours <= 24 * 14) {
        priority = "MEDIUM";
      } else if (selectedAssessment?.type === "EXAM" || selectedAssessment?.type === "PROJECT") {
        priority = "MEDIUM";
      } else {
        priority = "LOW";
      }
    } else if (selectedAssessment) {
      if (selectedAssessment.type === "EXAM" || selectedAssessment.type === "PROJECT") priority = "HIGH";
      else if (selectedAssessment.type === "ASSIGNMENT" || selectedAssessment.type === "QUIZ") priority = "MEDIUM";
      else priority = "LOW";
    } else if (selectedModule && selectedModule.credits >= 4) {
      priority = "MEDIUM";
    }

    return { hours, priority };
  }, [selectedAssessment, selectedModule, form.dueDateTime]);

  const createMutation = useMutation({
    mutationFn: (payload: TaskRequest) => taskApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      setShowModal(false);
      setForm({
        title: "",
        description: "",
        moduleId: undefined,
        assessmentId: undefined,
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
    mutationFn: taskApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });

  const filteredTasks = tasks.filter((t) => {
    if (filterStatus === "ALL") return true;
    return t.status === filterStatus;
  });

  return (
    <Layout>
      <div className="space-y-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-hairline pb-4">
          <div>
            <h1 className="text-3xl font-serif font-bold text-ink">Academic Task Board</h1>
            <p className="text-slate-600 text-sm mt-1">
              Track daily tasks, remaining work hours, and study progress with automatic workload & priority assignment.
            </p>
          </div>
          <button
            onClick={() => setShowModal(true)}
            className="px-4 py-2 bg-ink hover:bg-ink-light text-white font-medium rounded-md shadow transition flex items-center justify-center space-x-2 text-sm"
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
              className={`px-3 py-1.5 rounded-md text-xs font-semibold transition ${
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
          <div className="bg-white p-8 rounded-lg border border-hairline text-center text-slate-500">
            No tasks found. Click "Create New Task" to add your first study task!
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredTasks.map((t) => (
              <div
                key={t.id}
                className={`p-5 rounded-xl border transition shadow-sm flex flex-col justify-between ${
                  t.status === "COMPLETED"
                    ? "bg-slate-50 border-slate-200 opacity-80"
                    : "bg-white border-hairline hover:border-gold"
                }`}
              >
                <div className="space-y-2">
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex items-center gap-1.5">
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                          t.priority === "CRITICAL"
                            ? "bg-red-200 text-red-800 border border-red-300 font-extrabold"
                            : t.priority === "HIGH"
                            ? "bg-red-100 text-red-700 font-bold"
                            : t.priority === "MEDIUM"
                            ? "bg-amber-100 text-amber-700"
                            : "bg-slate-100 text-slate-700"
                        }`}
                      >
                        {t.priority}
                      </span>
                      <span className="text-[9px] text-slate-400 font-mono">AUTO</span>
                    </div>

                    {t.moduleName && (
                      <span className="text-[10px] font-mono font-semibold px-2 py-0.5 bg-ink text-gold rounded">
                        {t.moduleName}
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

                  {t.assessmentTitle && (
                    <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-amber-50 text-amber-800 text-[11px] font-medium border border-amber-200">
                      <span>📑</span>
                      <span>{t.assessmentTitle}</span>
                    </div>
                  )}

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
                      Hours: <b>{t.remainingHours}h</b> left / {t.estimatedHours}h
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
                      className={`px-3 py-1 text-xs font-semibold rounded transition ${
                        t.status === "COMPLETED"
                          ? "bg-slate-200 text-slate-700 hover:bg-slate-300"
                          : "bg-green-600 text-white hover:bg-green-700 shadow-sm"
                      }`}
                    >
                      {t.status === "COMPLETED" ? "Mark Pending" : "✓ Complete"}
                    </button>
                    <button
                      onClick={() => deleteMutation.mutate(t.id)}
                      className="text-xs text-red-600 hover:text-red-800"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Modal Create */}
        {showModal && (
          <div className="fixed inset-0 bg-ink/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-xl border border-hairline space-y-4">
              <div className="flex items-center justify-between border-b border-hairline pb-2">
                <h3 className="text-lg font-serif font-bold text-ink">
                  Create Task
                </h3>
                <span className="text-[10px] font-mono font-bold px-2 py-0.5 bg-gold/20 text-ink rounded">
                  AUTO-ALLOCATED
                </span>
              </div>

              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  createMutation.mutate({
                    ...form,
                    estimatedHours: autoPreview.hours,
                    priority: autoPreview.priority,
                  });
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
                    className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none focus:border-gold"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Description (Optional)
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Notes, topics covered, or instructions..."
                    value={form.description || ""}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none focus:border-gold resize-none"
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
                      setForm({ ...form, moduleId: modId, assessmentId: undefined });
                    }}
                    className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none bg-white"
                  >
                    <option value="">-- No Module --</option>
                    {modules.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.code} - {m.name} ({m.credits} credits)
                      </option>
                    ))}
                  </select>
                </div>

                {form.moduleId && moduleAssessments.length > 0 && (
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Link to Assessment (Optional)
                    </label>
                    <select
                      value={form.assessmentId || ""}
                      onChange={(e) => {
                        const assId = e.target.value ? parseInt(e.target.value) : undefined;
                        const matched = moduleAssessments.find((a) => a.id === assId);
                        setForm({
                          ...form,
                          assessmentId: assId,
                          dueDateTime: form.dueDateTime || matched?.dueDateTime || undefined,
                        });
                      }}
                      className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none bg-white"
                    >
                      <option value="">-- None (General Module Task) --</option>
                      {moduleAssessments.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.title} ({a.type}) - Due {new Date(a.dueDateTime).toLocaleDateString()}
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Due Date & Time (Optional)
                  </label>
                  <input
                    type="datetime-local"
                    value={form.dueDateTime || ""}
                    onChange={(e) => setForm({ ...form, dueDateTime: e.target.value || undefined })}
                    className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none bg-white"
                  />
                </div>

                {/* Automatic Workload & Priority Indicator */}
                <div className="p-3 bg-paper rounded-lg border border-hairline space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-700 flex items-center gap-1.5">
                      <span>⚡</span> Auto-Calculated Planning Values:
                    </span>
                  </div>
                  <div className="flex items-center gap-4 text-xs">
                    <div>
                      <span className="text-slate-500">Estimated Workload: </span>
                      <b className="text-ink font-mono">{autoPreview.hours}h</b>
                    </div>
                    <div>
                      <span className="text-slate-500">Academic Priority: </span>
                      <span
                        className={`font-bold px-1.5 py-0.5 rounded text-[11px] ${
                          autoPreview.priority === "CRITICAL"
                            ? "bg-red-200 text-red-800"
                            : autoPreview.priority === "HIGH"
                            ? "bg-red-100 text-red-700"
                            : autoPreview.priority === "MEDIUM"
                            ? "bg-amber-100 text-amber-700"
                            : "bg-slate-100 text-slate-700"
                        }`}
                      >
                        {autoPreview.priority}
                      </span>
                    </div>
                  </div>
                  <p className="text-[11px] text-slate-500 leading-relaxed">
                    Hours and priority are computed automatically based on assessment type, module credits, and deadline urgency. You do not need to enter them manually.
                  </p>
                </div>

                <div className="flex justify-end space-x-3 pt-4 border-t border-hairline">
                  <button
                    type="button"
                    onClick={() => setShowModal(false)}
                    className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-ink"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={createMutation.isPending}
                    className="px-4 py-2 bg-gold hover:bg-gold-dark text-ink font-semibold text-xs rounded shadow transition disabled:opacity-50"
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
            <div className="bg-white rounded-xl max-w-xs w-full p-6 shadow-xl border border-hairline space-y-4 text-center">
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
                className="w-full text-center px-3 py-2 border border-hairline rounded-md text-lg font-bold text-ink outline-none"
              />
              <div className="flex justify-center space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setWorkModalTask(null)}
                  className="px-3 py-1.5 text-xs text-slate-600 hover:text-ink"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => progressMutation.mutate({ id: workModalTask, hours: workedHoursInput })}
                  className="px-4 py-1.5 bg-gold hover:bg-gold-dark text-ink font-semibold text-xs rounded shadow"
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
