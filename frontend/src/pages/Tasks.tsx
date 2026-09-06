import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Layout from "../components/Layout";
import { taskApi } from "../api/taskApi";
import { moduleApi } from "../api/moduleApi";
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
    estimatedHours: 2,
    priority: "MEDIUM",
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

  const createMutation = useMutation({
    mutationFn: taskApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      setShowModal(false);
      setForm({
        title: "",
        description: "",
        moduleId: undefined,
        estimatedHours: 2,
        priority: "MEDIUM",
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
              Track daily tasks, remaining work hours, and study progress.
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
                  <div className="flex items-center justify-between">
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        t.priority === "HIGH"
                          ? "bg-red-100 text-red-700"
                          : t.priority === "MEDIUM"
                          ? "bg-amber-100 text-amber-700"
                          : "bg-slate-100 text-slate-700"
                      }`}
                    >
                      {t.priority}
                    </span>
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

                  {t.description && (
                    <p className="text-xs text-slate-500 line-clamp-2">{t.description}</p>
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
              <h3 className="text-lg font-serif font-bold text-ink border-b border-hairline pb-2">
                Create Task
              </h3>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  createMutation.mutate(form);
                }}
                className="space-y-4"
              >
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Task Title
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Read Chapter 4 / Implement Binary Search"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Module (Optional)
                  </label>
                  <select
                    value={form.moduleId || ""}
                    onChange={(e) =>
                      setForm({ ...form, moduleId: e.target.value ? parseInt(e.target.value) : undefined })
                    }
                    className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none bg-white"
                  >
                    <option value="">-- No Module --</option>
                    {modules.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.code} - {m.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Est. Hours
                    </label>
                    <input
                      type="number"
                      step="0.5"
                      min="0.5"
                      required
                      value={form.estimatedHours}
                      onChange={(e) => setForm({ ...form, estimatedHours: parseFloat(e.target.value) || 1 })}
                      className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Priority
                    </label>
                    <select
                      value={form.priority}
                      onChange={(e) => setForm({ ...form, priority: e.target.value as PriorityLevel })}
                      className="w-full px-3 py-2 border border-hairline rounded-md text-sm outline-none bg-white"
                    >
                      <option value="HIGH">High</option>
                      <option value="MEDIUM">Medium</option>
                      <option value="LOW">Low</option>
                    </select>
                  </div>
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
                    className="px-4 py-2 bg-gold hover:bg-gold-dark text-ink font-semibold text-xs rounded shadow"
                  >
                    Save Task
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
