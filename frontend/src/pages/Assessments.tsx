import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import { semesterApi } from "../api/semesterApi";
import { moduleApi } from "../api/moduleApi";
import { assessmentApi } from "../api/assessmentApi";
import { Assessment, AssessmentRequest, AssessmentType, AssessmentStatus } from "../types/academic";

/* ─── helpers ─────────────────────────────────────────────── */
const getDefaultDueDateTime = () => {
  const d = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T23:59`;
};

const defaultForm = (): AssessmentRequest => ({
  title: "",
  type: "ASSIGNMENT",
  description: "",
  dueDateTime: getDefaultDueDateTime(),
  weight: 20,
  status: "PENDING",
});

const TYPE_META: Record<AssessmentType, { label: string; icon: string; bg: string; accent: string; basePriority: number }> = {
  EXAM: { label: "Exam", icon: "📑", bg: "bg-purple-50 border-purple-200", accent: "bg-purple-100 text-purple-800 border-purple-200", basePriority: 5 },
  PROJECT: { label: "Project", icon: "🚀", bg: "bg-emerald-50 border-emerald-200", accent: "bg-emerald-100 text-emerald-800 border-emerald-200", basePriority: 4 },
  ASSIGNMENT: { label: "Assignment", icon: "📝", bg: "bg-amber-50 border-amber-200", accent: "bg-amber-100 text-amber-800 border-amber-200", basePriority: 3 },
  QUIZ: { label: "Quiz", icon: "💡", bg: "bg-blue-50 border-blue-200", accent: "bg-blue-100 text-blue-800 border-blue-200", basePriority: 3 },
  PRESENTATION: { label: "Presentation", icon: "🎤", bg: "bg-teal-50 border-teal-200", accent: "bg-teal-100 text-teal-800 border-teal-200", basePriority: 3 },
  REPORT: { label: "Report", icon: "📊", bg: "bg-indigo-50 border-indigo-200", accent: "bg-indigo-100 text-indigo-800 border-indigo-200", basePriority: 3 },
  OTHER: { label: "Other", icon: "📌", bg: "bg-slate-50 border-slate-200", accent: "bg-slate-100 text-slate-800 border-slate-200", basePriority: 2 },
};

const STATUS_META: Record<AssessmentStatus, { label: string; cls: string }> = {
  PENDING: { label: "Pending", cls: "bg-slate-100 text-slate-600" },
  IN_PROGRESS: { label: "In Progress", cls: "bg-blue-100 text-blue-700" },
  COMPLETED: { label: "Completed", cls: "bg-emerald-100 text-emerald-700" },
  CANCELLED: { label: "Cancelled", cls: "bg-red-100 text-red-600" },
};

/* ─── component ───────────────────────────────────────────── */
export default function Assessments() {
  const queryClient = useQueryClient();

  // UI state
  const [selectedModuleId, setSelectedModuleId] = useState<number | null>(null);
  const [notification, setNotification] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingAssessment, setEditingAssessment] = useState<Assessment | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [addAnother, setAddAnother] = useState(false);
  const [form, setForm] = useState<AssessmentRequest>(defaultForm());

  /* ── data fetching ── */
  const { data: semesters = [], isLoading: loadingSemesters } = useQuery({
    queryKey: ["semesters"],
    queryFn: semesterApi.getAll,
  });

  const activeSemester = useMemo(
    () =>
      semesters.find((s) => s.status === "ACTIVE") ||
      semesters.find((s) => s.status === "PLANNED") ||
      semesters[0] ||
      null,
    [semesters]
  );

  const { data: activeModules = [], isLoading: loadingModules } = useQuery({
    queryKey: ["modules", activeSemester?.id],
    queryFn: () =>
      activeSemester ? moduleApi.getBySemester(activeSemester.id) : Promise.resolve([]),
    enabled: !!activeSemester?.id,
  });

  const currentModuleId = useMemo(() => {
    if (selectedModuleId && activeModules.some((m) => m.id === selectedModuleId))
      return selectedModuleId;
    return activeModules[0]?.id ?? null;
  }, [selectedModuleId, activeModules]);

  const currentModule = useMemo(
    () => activeModules.find((m) => m.id === currentModuleId) ?? null,
    [activeModules, currentModuleId]
  );

  const { data: assessments = [], isLoading: loadingAssessments } = useQuery({
    queryKey: ["assessments", currentModuleId],
    queryFn: () =>
      currentModuleId ? assessmentApi.getByModule(currentModuleId) : Promise.resolve([]),
    enabled: !!currentModuleId,
  });

  /* ── weight sum ── */
  const totalWeight = useMemo(
    () => assessments.reduce((acc, a) => acc + (a.weight ?? 0), 0),
    [assessments]
  );

  /* ── error helper ── */
  const extractErrorMessage = (err: any): string => {
    if (err?.response?.data?.message) {
      if (err.response.data.fieldErrors) {
        const details = Object.values(err.response.data.fieldErrors).join(", ");
        return `${err.response.data.message}: ${details}`;
      }
      return err.response.data.message;
    }
    if (err?.response?.data?.error) return err.response.data.error;
    if (err?.message) return err.message;
    return "An unexpected error occurred. Please try again.";
  };

  /* ── mutations ── */
  const createMutation = useMutation({
    mutationFn: (data: AssessmentRequest) => assessmentApi.create(currentModuleId!, data),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["assessments", currentModuleId] });
      queryClient.invalidateQueries({ queryKey: ["modules", activeSemester?.id] });
      setErrorMessage(null);
      showBanner(`✅ "${created.title}" added successfully!`);
      if (addAnother) {
        setForm(defaultForm());
      } else {
        setShowModal(false);
        setForm(defaultForm());
      }
    },
    onError: (err: any) => setErrorMessage(extractErrorMessage(err)),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: AssessmentRequest }) =>
      assessmentApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["assessments", currentModuleId] });
      setShowModal(false);
      setEditingAssessment(null);
      setErrorMessage(null);
      showBanner("✅ Assessment updated successfully.");
    },
    onError: (err: any) => setErrorMessage(extractErrorMessage(err)),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => assessmentApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["assessments", currentModuleId] });
      queryClient.invalidateQueries({ queryKey: ["modules", activeSemester?.id] });
      showBanner("🗑️ Assessment deleted.");
    },
    onError: (err: any) => alert("Failed to delete: " + extractErrorMessage(err)),
  });

  /* ── helpers ── */
  const showBanner = (msg: string) => {
    setNotification(msg);
    setTimeout(() => setNotification(null), 4000);
  };

  const handleOpenCreate = () => {
    if (!currentModuleId) return alert("Please select a module first.");
    setEditingAssessment(null);
    setErrorMessage(null);
    setForm(defaultForm());
    setShowModal(true);
  };

  const handleOpenEdit = (item: Assessment) => {
    setEditingAssessment(item);
    setErrorMessage(null);
    let due = item.dueDateTime ?? "";
    if (due.length > 16) due = due.slice(0, 16);
    setForm({
      title: item.title,
      type: item.type,
      description: item.description ?? "",
      dueDateTime: due || getDefaultDueDateTime(),
      weight: item.weight ?? 0,
      status: item.status ?? "PENDING",
    });
    setShowModal(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    if (!currentModuleId) return setErrorMessage("Please select a module.");
    if (editingAssessment) {
      updateMutation.mutate({ id: editingAssessment.id, data: form });
    } else {
      createMutation.mutate(form);
    }
  };

  const fmtDue = (iso: string) => {
    const d = new Date(iso);
    return {
      date: d.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" }),
      time: d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" }),
    };
  };

  const isBusy = createMutation.isPending || updateMutation.isPending;

  /* ═══════════════════════════════════════════════════════════
     RENDER
  ═══════════════════════════════════════════════════════════ */
  return (
    <Layout>
      <div className="space-y-6">

        {/* ── Banner notification ── */}
        {notification && (
          <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-xl flex items-center justify-between text-sm shadow-sm">
            <span>{notification}</span>
            <button
              onClick={() => setNotification(null)}
              className="text-emerald-600 hover:text-emerald-900 font-bold ml-4"
            >
              ✕
            </button>
          </div>
        )}

        {/* ── Page header ── */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-hairline pb-5">
          <div>
            <h1 className="text-3xl font-serif font-bold text-ink">Assessments</h1>
            <p className="text-slate-500 text-sm mt-1">
              Track exams, quizzes, assignments, and projects. Planning priorities are automatically computed.
            </p>
          </div>

          {currentModuleId && (
            <button
              id="add-assessment-btn"
              onClick={handleOpenCreate}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-ink hover:bg-ink-light text-white font-semibold rounded-xl shadow-md transition-all text-sm"
            >
              <span className="text-base">➕</span>
              Add Assessment
            </button>
          )}
        </div>

        {/* ── Module selector panel ── */}
        {loadingSemesters || loadingModules ? (
          <div className="bg-white p-6 rounded-2xl border border-hairline text-slate-400 animate-pulse text-sm">
            Loading active semester modules…
          </div>
        ) : !activeSemester ? (
          <div className="bg-white p-10 rounded-2xl border border-hairline text-center space-y-3 shadow-sm">
            <div className="text-5xl">📚</div>
            <h3 className="text-lg font-bold text-ink">No Active Semester Found</h3>
            <p className="text-slate-500 text-sm max-w-sm mx-auto">
              Create a semester and set its status to ACTIVE to manage assessments.
            </p>
            <Link
              to="/semesters"
              className="inline-block mt-2 px-5 py-2 bg-gold hover:bg-gold-dark text-ink font-semibold rounded-xl shadow text-sm transition"
            >
              Go to Semesters
            </Link>
          </div>
        ) : activeModules.length === 0 ? (
          <div className="bg-white p-10 rounded-2xl border border-hairline text-center space-y-3 shadow-sm">
            <div className="text-5xl">📖</div>
            <h3 className="text-lg font-bold text-ink">No Modules in {activeSemester.name}</h3>
            <p className="text-slate-500 text-sm max-w-sm mx-auto">
              Add modules to your active semester first.
            </p>
            <Link
              to="/semesters"
              className="inline-block mt-2 px-5 py-2 bg-gold hover:bg-gold-dark text-ink font-semibold rounded-xl shadow text-sm transition"
            >
              ➕ Add Modules
            </Link>
          </div>
        ) : (
          /* ── Selector card ── */
          <div className="bg-white rounded-2xl border border-hairline shadow-sm p-5 space-y-4">
            <div className="flex flex-col md:flex-row md:items-end gap-4">
              <div className="flex items-center gap-2 shrink-0">
                <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-200">
                  {activeSemester.status === "ACTIVE" ? "🟢 Active" : activeSemester.status}
                </span>
                <span className="text-sm font-bold text-ink">{activeSemester.name}</span>
              </div>

              <div className="flex-1 md:max-w-sm">
                <label
                  htmlFor="moduleSelect"
                  className="block text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1"
                >
                  Select Module
                </label>
                <select
                  id="moduleSelect"
                  value={currentModuleId ?? ""}
                  onChange={(e) => setSelectedModuleId(Number(e.target.value))}
                  className="w-full px-3.5 py-2.5 bg-paper border border-hairline rounded-xl text-sm font-semibold text-ink shadow-xs focus:ring-2 focus:ring-gold focus:border-gold outline-none cursor-pointer transition"
                >
                  {activeModules.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.code} — {m.name} ({m.credits} cr)
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Selected module summary bar */}
            {currentModule && (
              <div className="bg-slate-50 border border-hairline rounded-xl p-4 flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="font-mono font-bold text-xs px-2.5 py-1 bg-ink text-gold rounded-lg">
                    {currentModule.code}
                  </span>
                  <div>
                    <p className="font-bold text-ink text-sm leading-tight">{currentModule.name}</p>
                    <p className="text-slate-500 text-xs">{currentModule.credits} Credits</p>
                  </div>
                </div>

                <div className="flex items-center gap-6 text-xs">
                  <div className="text-right">
                    <span className="text-slate-400 block text-[10px] uppercase tracking-wide">Assessments</span>
                    <span className="font-bold text-ink text-sm">{assessments.length}</span>
                  </div>
                  <div className="text-right">
                    <span className="text-slate-400 block text-[10px] uppercase tracking-wide">Grade Weight</span>
                    <span className={`font-bold text-sm ${
                      totalWeight === 100 ? "text-emerald-700"
                      : totalWeight > 100 ? "text-rose-600"
                      : "text-amber-700"
                    }`}>
                      {totalWeight}% / 100%
                    </span>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ── Assessments list ── */}
        {currentModule && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold font-serif text-ink">
                {currentModule.code} — Assessments
              </h2>
              <span className="text-xs text-slate-500">
                {assessments.length} {assessments.length === 1 ? "item" : "items"}
              </span>
            </div>

            {loadingAssessments ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="bg-white rounded-2xl border border-hairline p-5 animate-pulse h-40" />
                ))}
              </div>
            ) : assessments.length === 0 ? (
              <div className="bg-white rounded-2xl border border-dashed border-hairline p-12 text-center space-y-3">
                <div className="text-4xl">📝</div>
                <h4 className="font-bold text-ink">No assessments for {currentModule.code} yet</h4>
                <p className="text-slate-500 text-sm max-w-sm mx-auto">
                  Add exams, quizzes, assignments, and projects to track deadlines and grade weights.
                </p>
                <button
                  onClick={handleOpenCreate}
                  className="mt-2 px-5 py-2.5 bg-gold hover:bg-gold-dark text-ink font-semibold rounded-xl shadow text-sm transition"
                >
                  ➕ Add First Assessment
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {assessments.map((a) => {
                  const typeMeta = TYPE_META[a.type] ?? TYPE_META.ASSIGNMENT;
                  const statMeta = STATUS_META[a.status] ?? STATUS_META.PENDING;
                  const due = a.dueDateTime ? fmtDue(a.dueDateTime) : null;
                  const days = a.daysUntilDeadline;
                  const isPast = a.dueDateTime ? new Date(a.dueDateTime).getTime() < Date.now() : false;
                  const overdue = isPast || (days !== undefined && days !== null && days < 0);
                  const soon = !overdue && days !== undefined && days !== null && days >= 0 && days <= 3;
                  const basePri = a.calculatedBasePriority ?? typeMeta.basePriority;

                  return (
                    <div
                      key={a.id}
                      className={`bg-white rounded-2xl border shadow-sm hover:shadow-md transition-all flex flex-col justify-between p-5 space-y-4 ${typeMeta.bg}`}
                    >
                      {/* Top row: type badge + system base priority */}
                      <div className="flex items-start justify-between gap-2">
                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold border ${typeMeta.accent}`}>
                          <span>{typeMeta.icon}</span>
                          {typeMeta.label}
                        </span>
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-medium rounded-full bg-slate-100 text-slate-700 border border-slate-200" title="Base priority determined by assessment type (urgency bonus added during planning)">
                          ⚡ System Priority: <strong className="text-ink">{basePri}</strong>
                        </span>
                      </div>

                      {/* Title + description */}
                      <div>
                        <h3 className="font-bold text-ink text-base leading-snug">{a.title}</h3>
                        {a.description ? (
                          <p className="text-xs text-slate-500 mt-1.5 line-clamp-2 leading-relaxed">{a.description}</p>
                        ) : (
                          <p className="text-xs text-slate-400 italic mt-1.5">No description</p>
                        )}
                      </div>

                      {/* Stats grid */}
                      <div className="grid grid-cols-2 gap-2 bg-white/70 rounded-xl border border-white/80 p-3 text-xs">
                        <div>
                          <span className="text-slate-400 block text-[10px] uppercase tracking-wide">Grade Weight</span>
                          <span className="font-bold text-ink text-sm">{a.weight ?? 0}%</span>
                        </div>
                        <div>
                          <span className="text-slate-400 block text-[10px] uppercase tracking-wide">Due Date & Time</span>
                          {due ? (
                            <span className="font-semibold text-ink leading-tight block">
                              📅 {due.date}
                              <span className="text-slate-500 ml-1">⏰ {due.time}</span>
                            </span>
                          ) : (
                            <span className="text-slate-400 italic">No date set</span>
                          )}
                        </div>
                      </div>

                      {/* Deadline alert */}
                      {(overdue || soon) && (
                        <div className={`text-xs font-semibold px-3 py-1.5 rounded-lg ${
                          overdue ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-800"
                        }`}>
                          {overdue
                            ? days !== undefined && days !== null && days < 0
                              ? `⚠️ Overdue by ${Math.abs(days)} day${Math.abs(days) !== 1 ? "s" : ""}`
                              : "⚠️ Overdue (deadline passed)"
                            : days === 0
                            ? "🔥 Due today!"
                            : days === 1
                            ? "⏳ Due tomorrow!"
                            : `⏳ Due in ${days} days`}
                        </div>
                      )}

                      {/* Footer: status + actions */}
                      <div className="flex items-center justify-between pt-2 border-t border-black/5 text-xs">
                        <span className={`px-2.5 py-0.5 rounded-lg text-[11px] font-bold ${statMeta.cls}`}>
                          {statMeta.label}
                        </span>
                        <div className="flex items-center gap-3">
                          <button
                            onClick={() => handleOpenEdit(a)}
                            className="text-slate-600 hover:text-ink font-semibold transition"
                          >
                            ✏️ Edit
                          </button>
                          <button
                            onClick={() => {
                              if (confirm(`Delete "${a.title}"?`)) deleteMutation.mutate(a.id);
                            }}
                            className="text-red-400 hover:text-red-700 font-semibold transition"
                          >
                            🗑️ Delete
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* ══════════════════════════════════════════════════════
            ADD / EDIT MODAL
        ══════════════════════════════════════════════════════ */}
        {showModal && (
          <div className="fixed inset-0 bg-ink/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl w-full max-w-lg shadow-2xl border border-hairline overflow-y-auto max-h-[90vh]">

              {/* Modal header */}
              <div className="flex items-center justify-between px-6 pt-6 pb-4 border-b border-hairline sticky top-0 bg-white z-10 rounded-t-2xl">
                <div>
                  <h3 className="text-xl font-serif font-bold text-ink">
                    {editingAssessment ? "Edit Assessment" : "New Assessment"}
                  </h3>
                  {currentModule && (
                    <p className="text-xs text-slate-500 mt-0.5">
                      <span className="font-semibold text-ink">{currentModule.code}</span> — {currentModule.name}
                    </p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="w-8 h-8 flex items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-ink transition text-lg"
                >
                  ✕
                </button>
              </div>

              {/* Error banner */}
              {errorMessage && (
                <div className="mx-6 mt-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-xl text-xs">
                  ⚠️ {errorMessage}
                </div>
              )}

              <form onSubmit={handleSubmit} className="px-6 pb-6 pt-4 space-y-5">

                {/* Title */}
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1.5">
                    Assessment Title <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="assessment-title"
                    type="text"
                    required
                    placeholder="e.g. Midterm Examination, Final Project Report…"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-gold focus:border-gold outline-none transition"
                  />
                </div>

                {/* Type – icon button grid */}
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-2">
                    Type <span className="text-red-500">*</span>
                  </label>
                  <div className="grid grid-cols-4 gap-2">
                    {(Object.keys(TYPE_META) as AssessmentType[]).map((t) => {
                      const m = TYPE_META[t];
                      const active = form.type === t;
                      return (
                        <button
                          key={t}
                          type="button"
                          onClick={() => setForm({ ...form, type: t })}
                          className={`flex flex-col items-center justify-center gap-1.5 p-3 rounded-xl border-2 text-xs font-bold transition-all ${
                            active
                              ? "border-ink bg-ink text-white shadow-md scale-105"
                              : "border-slate-200 bg-slate-50 text-slate-600 hover:border-slate-400 hover:bg-slate-100"
                          }`}
                        >
                          <span className="text-2xl">{m.icon}</span>
                          <span>{m.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* System Priority Notice */}
                <div className="bg-amber-50/70 border border-amber-200/80 rounded-xl p-3 text-xs text-amber-900 flex items-start gap-2.5">
                  <span className="text-base leading-none mt-0.5">⚡</span>
                  <div>
                    <span className="font-bold block mb-0.5">System-Generated Priority ({TYPE_META[form.type]?.basePriority ?? 3})</span>
                    <span className="text-slate-600">
                      Planning priority is calculated automatically: base priority from assessment type + deadline urgency bonus (+1 to +4) multiplied by module credits.
                    </span>
                  </div>
                </div>

                {/* Weight + Status */}
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1.5">
                      Grade Weight (%) <span className="text-red-500">*</span>
                    </label>
                    <input
                      id="assessment-weight"
                      type="number"
                      min="0"
                      max="100"
                      step="0.5"
                      required
                      placeholder="25"
                      value={form.weight}
                      onChange={(e) => setForm({ ...form, weight: parseFloat(e.target.value) || 0 })}
                      className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-gold outline-none transition"
                    />
                    <span className="text-[10px] text-slate-400 mt-0.5 block">
                      Module total: {totalWeight}%
                      {!editingAssessment && form.weight > 0
                        ? ` → ${(totalWeight + form.weight).toFixed(1)}%`
                        : ""}
                    </span>
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1.5">Status</label>
                    <select
                      value={form.status}
                      onChange={(e) => setForm({ ...form, status: e.target.value as AssessmentStatus })}
                      className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-gold outline-none bg-white transition"
                    >
                      <option value="PENDING">Pending</option>
                      <option value="IN_PROGRESS">In Progress</option>
                      <option value="COMPLETED">Completed</option>
                      <option value="CANCELLED">Cancelled</option>
                    </select>
                  </div>
                </div>

                {/* Due Date & Time */}
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1.5">
                    Due Date & Time <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="assessment-due"
                    type="datetime-local"
                    required
                    value={form.dueDateTime}
                    onChange={(e) => setForm({ ...form, dueDateTime: e.target.value })}
                    className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-gold outline-none bg-white transition"
                  />
                  <span className="text-[10px] text-slate-400 mt-0.5 block">
                    Submission deadline or exam sitting time
                  </span>
                </div>

                {/* Description */}
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1.5">
                    Description / Notes{" "}
                    <span className="text-slate-400 font-normal">(optional)</span>
                  </label>
                  <textarea
                    rows={3}
                    placeholder="Chapters covered, submission format, exam scope…"
                    value={form.description ?? ""}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-gold outline-none resize-none transition"
                  />
                </div>

                {/* Add another checkbox */}
                {!editingAssessment && (
                  <label className="flex items-center gap-2 cursor-pointer text-xs text-slate-600 font-medium">
                    <input
                      type="checkbox"
                      id="addAnotherAssessment"
                      checked={addAnother}
                      onChange={(e) => setAddAnother(e.target.checked)}
                      className="rounded text-gold focus:ring-gold"
                    />
                    Keep this form open after saving (add another)
                  </label>
                )}

                {/* Actions */}
                <div className="flex justify-end gap-3 pt-2 border-t border-hairline">
                  <button
                    type="button"
                    onClick={() => setShowModal(false)}
                    className="px-4 py-2 text-sm font-semibold text-slate-600 hover:text-ink rounded-lg transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isBusy}
                    className="px-6 py-2.5 bg-gold hover:bg-gold-dark text-ink font-bold text-sm rounded-xl shadow transition disabled:opacity-50 flex items-center gap-2"
                  >
                    {isBusy && <span className="animate-spin text-base">⏳</span>}
                    {isBusy
                      ? editingAssessment ? "Updating…" : "Saving…"
                      : editingAssessment
                      ? "Update Assessment"
                      : "Save Assessment"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

      </div>
    </Layout>
  );
}
