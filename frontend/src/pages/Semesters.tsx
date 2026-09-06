import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Layout from "../components/Layout";
import { semesterApi } from "../api/semesterApi";
import { moduleApi } from "../api/moduleApi";
import { Semester, SemesterRequest, Module, ModuleRequest, SemesterStatus } from "../types/academic";

export default function Semesters() {
  const queryClient = useQueryClient();
  const [selectedSemesterId, setSelectedSemesterId] = useState<number | null>(null);
  const [notification, setNotification] = useState<string | null>(null);

  // Semester Modal / Form state
  const [showSemesterModal, setShowSemesterModal] = useState(false);
  const [editingSemester, setEditingSemester] = useState<Semester | null>(null);
  const [semesterError, setSemesterError] = useState<string | null>(null);
  const [semForm, setSemForm] = useState<SemesterRequest>({
    name: "",
    startDate: new Date().toISOString().split("T")[0],
    endDate: new Date(Date.now() + 120 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
    status: "ACTIVE" as SemesterStatus,
  });

  // Module Modal / Form state
  const [showModuleModal, setShowModuleModal] = useState(false);
  const [editingModule, setEditingModule] = useState<Module | null>(null);
  const [moduleError, setModuleError] = useState<string | null>(null);
  const [addAnother, setAddAnother] = useState(false);
  const [modForm, setModForm] = useState<ModuleRequest>({
    code: "",
    name: "",
    credits: 3,
    description: "",
  });

  // Queries
  const { data: semesters = [], isLoading: loadingSemesters } = useQuery({
    queryKey: ["semesters"],
    queryFn: semesterApi.getAll,
  });

  const activeSemester =
    semesters.find((s) => s.id === selectedSemesterId) ||
    semesters.find((s) => s.status === "ACTIVE") ||
    semesters[0] ||
    null;

  const activeSemesterId = activeSemester?.id || null;

  const { data: modules = [], isLoading: loadingModules } = useQuery({
    queryKey: ["modules", activeSemesterId],
    queryFn: () => (activeSemesterId ? moduleApi.getBySemester(activeSemesterId) : Promise.resolve([])),
    enabled: !!activeSemesterId,
  });

  // Helper to extract error messages from Axios / server response
  const getErrorMessage = (err: any): string => {
    if (err?.response?.data?.message) {
      if (err.response.data.fieldErrors) {
        const fieldDetails = Object.values(err.response.data.fieldErrors).join(", ");
        return `${err.response.data.message}: ${fieldDetails}`;
      }
      return err.response.data.message;
    }
    if (err?.response?.data?.error) return err.response.data.error;
    if (err?.message) return err.message;
    return "An unexpected error occurred. Please try again.";
  };

  // Semester Mutations
  const createSemesterMutation = useMutation({
    mutationFn: (data: SemesterRequest) => semesterApi.create(data),
    onSuccess: (newSemester) => {
      queryClient.invalidateQueries({ queryKey: ["semesters"] });
      setShowSemesterModal(false);
      setSelectedSemesterId(newSemester.id);
      setSemesterError(null);
      setSemForm({
        name: "",
        startDate: new Date().toISOString().split("T")[0],
        endDate: new Date(Date.now() + 120 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
        status: "ACTIVE" as SemesterStatus,
      });
      // Automatically prompt to add modules for the newly created semester
      setModForm({ code: "", name: "", credits: 3, description: "" });
      setEditingModule(null);
      setModuleError(null);
      setShowModuleModal(true);
      setNotification(`🎉 Semester "${newSemester.name}" created! Add your courses/modules below.`);
    },
    onError: (err: any) => {
      setSemesterError(getErrorMessage(err));
    },
  });

  const updateSemesterMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: SemesterRequest }) =>
      semesterApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["semesters"] });
      setShowSemesterModal(false);
      setEditingSemester(null);
      setSemesterError(null);
    },
    onError: (err: any) => {
      setSemesterError(getErrorMessage(err));
    },
  });

  const deleteSemesterMutation = useMutation({
    mutationFn: (id: number) => semesterApi.delete(id),
    onSuccess: (_, deletedId) => {
      queryClient.invalidateQueries({ queryKey: ["semesters"] });
      if (selectedSemesterId === deletedId) {
        setSelectedSemesterId(null);
      }
      setNotification("Semester deleted successfully.");
    },
    onError: (err: any) => {
      alert("Failed to delete semester: " + getErrorMessage(err));
    },
  });

  // Module Mutations
  const createModuleMutation = useMutation({
    mutationFn: (data: ModuleRequest) => moduleApi.create(activeSemesterId!, data),
    onSuccess: (newModule) => {
      queryClient.invalidateQueries({ queryKey: ["modules", activeSemesterId] });
      queryClient.invalidateQueries({ queryKey: ["semesters"] });
      setModuleError(null);
      setNotification(`Module "${newModule.code} - ${newModule.name}" added successfully!`);

      if (addAnother) {
        setModForm({ code: "", name: "", credits: 3, description: "" });
      } else {
        setShowModuleModal(false);
        setModForm({ code: "", name: "", credits: 3, description: "" });
      }
    },
    onError: (err: any) => {
      setModuleError(getErrorMessage(err));
    },
  });

  const updateModuleMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: ModuleRequest }) =>
      moduleApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["modules", activeSemesterId] });
      queryClient.invalidateQueries({ queryKey: ["semesters"] });
      setShowModuleModal(false);
      setEditingModule(null);
      setModuleError(null);
      setNotification("Module updated successfully.");
    },
    onError: (err: any) => {
      setModuleError(getErrorMessage(err));
    },
  });

  const deleteModuleMutation = useMutation({
    mutationFn: (id: number) => moduleApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["modules", activeSemesterId] });
      queryClient.invalidateQueries({ queryKey: ["semesters"] });
      setNotification("Module removed successfully.");
    },
    onError: (err: any) => {
      alert("Failed to delete module: " + getErrorMessage(err));
    },
  });

  // Modal handlers
  const openCreateSemester = () => {
    setEditingSemester(null);
    setSemForm({
      name: "",
      startDate: new Date().toISOString().split("T")[0],
      endDate: new Date(Date.now() + 120 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
      status: "ACTIVE",
    });
    setSemesterError(null);
    setShowSemesterModal(true);
  };

  const openEditSemester = (sem: Semester) => {
    setEditingSemester(sem);
    setSemForm({
      name: sem.name,
      startDate: sem.startDate,
      endDate: sem.endDate,
      status: sem.status,
    });
    setSemesterError(null);
    setShowSemesterModal(true);
  };

  const openCreateModule = () => {
    if (!activeSemesterId) {
      alert("Please create or select a semester first.");
      return;
    }
    setEditingModule(null);
    setModForm({ code: "", name: "", credits: 3, description: "" });
    setModuleError(null);
    setShowModuleModal(true);
  };

  const openEditModule = (mod: Module) => {
    setEditingModule(mod);
    setModForm({
      code: mod.code,
      name: mod.name,
      credits: mod.credits,
      description: mod.description || "",
    });
    setModuleError(null);
    setShowModuleModal(true);
  };

  const handleSemesterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSemesterError(null);
    if (editingSemester) {
      updateSemesterMutation.mutate({ id: editingSemester.id, data: semForm });
    } else {
      createSemesterMutation.mutate(semForm);
    }
  };

  const handleModuleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setModuleError(null);
    if (!activeSemesterId) {
      setModuleError("No semester is currently selected.");
      return;
    }
    if (editingModule) {
      updateModuleMutation.mutate({ id: editingModule.id, data: modForm });
    } else {
      createModuleMutation.mutate(modForm);
    }
  };

  const totalCredits = modules.reduce((acc, m) => acc + m.credits, 0);

  return (
    <Layout>
      <div className="space-y-8">
        {/* Banner Notification */}
        {notification && (
          <div className="bg-gold/15 border border-gold/40 text-ink px-4 py-3 rounded-lg flex items-center justify-between text-sm shadow-sm animate-fade-in">
            <div className="flex items-center space-x-2">
              <span className="text-lg">✨</span>
              <span>{notification}</span>
            </div>
            <button
              onClick={() => setNotification(null)}
              className="text-slate-500 hover:text-ink font-bold ml-4"
            >
              ✕
            </button>
          </div>
        )}

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-hairline pb-4">
          <div>
            <h1 className="text-3xl font-serif font-bold text-ink">Semesters & Modules</h1>
            <p className="text-slate-600 text-sm mt-1">
              Organize your academic semesters, enroll modules, and allocate credit workload.
            </p>
          </div>
          <button
            onClick={openCreateSemester}
            className="px-4 py-2.5 bg-ink hover:bg-ink-light text-white font-medium rounded-lg shadow-md transition flex items-center justify-center space-x-2 cursor-pointer"
          >
            <span>➕</span>
            <span>Add New Semester</span>
          </button>
        </div>

        {/* Semesters List / Selector */}
        {loadingSemesters ? (
          <div className="text-slate-500 animate-pulse p-4">Loading semesters...</div>
        ) : semesters.length === 0 ? (
          <div className="bg-white p-10 rounded-xl border border-hairline text-center shadow-sm space-y-4">
            <div className="text-4xl">📚</div>
            <h3 className="text-lg font-bold text-ink">No semesters added yet</h3>
            <p className="text-slate-600 text-sm max-w-md mx-auto">
              Create your semester term to begin adding modules, assessments, and generating your study plans.
            </p>
            <button
              onClick={openCreateSemester}
              className="px-5 py-2.5 bg-gold hover:bg-gold-dark text-ink font-semibold rounded-lg shadow transition"
            >
              ➕ Create First Semester
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-500">
                Your Semesters ({semesters.length})
              </h2>
              <span className="text-xs text-slate-500">Click a semester to view its modules</span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {semesters.map((sem) => {
                const isSelected = sem.id === activeSemesterId;
                return (
                  <div
                    key={sem.id}
                    onClick={() => setSelectedSemesterId(sem.id)}
                    className={`cursor-pointer p-5 rounded-xl border transition-all duration-200 relative group ${
                      isSelected
                        ? "border-gold bg-gold/10 shadow-md ring-2 ring-gold/40"
                        : "border-hairline bg-white hover:border-slate-300 hover:shadow-sm"
                    }`}
                  >
                    <div className="flex items-start justify-between">
                      <div>
                        <h3 className="font-bold text-ink text-lg">{sem.name}</h3>
                        <div className="text-xs text-slate-500 mt-1 flex items-center space-x-1">
                          <span>📅</span>
                          <span>{sem.startDate} → {sem.endDate}</span>
                        </div>
                      </div>
                      <span
                        className={`px-2.5 py-0.5 text-xs font-semibold rounded-full ${
                          sem.status === "ACTIVE"
                            ? "bg-emerald-100 text-emerald-800"
                            : sem.status === "COMPLETED"
                            ? "bg-blue-100 text-blue-800"
                            : "bg-slate-100 text-slate-700"
                        }`}
                      >
                        {sem.status}
                      </span>
                    </div>

                    <div className="mt-4 pt-3 border-t border-hairline/60 flex items-center justify-between text-xs text-slate-600">
                      <div>
                        <span className="font-semibold text-ink">{sem.moduleCount || 0}</span> modules •{" "}
                        <span className="font-semibold text-ink">{sem.totalCredits || 0}</span> credits
                      </div>
                      <div className="space-x-2 opacity-80 group-hover:opacity-100">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            openEditSemester(sem);
                          }}
                          className="text-slate-600 hover:text-ink font-medium"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            if (confirm(`Delete semester "${sem.name}" and all its modules?`)) {
                              deleteSemesterMutation.mutate(sem.id);
                            }
                          }}
                          className="text-red-500 hover:text-red-700 font-medium"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Modules Section for Selected Semester */}
        {activeSemester && (
          <div className="bg-white p-6 rounded-xl border border-hairline shadow-sm space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-hairline pb-4">
              <div>
                <div className="flex items-center space-x-2">
                  <h2 className="text-2xl font-bold font-serif text-ink">
                    Modules in {activeSemester.name}
                  </h2>
                  <span
                    className={`px-2.5 py-0.5 text-xs font-semibold rounded-full ${
                      activeSemester.status === "ACTIVE"
                        ? "bg-emerald-100 text-emerald-800"
                        : "bg-slate-100 text-slate-700"
                    }`}
                  >
                    {activeSemester.status}
                  </span>
                </div>
                <div className="flex items-center space-x-3 mt-1.5 text-xs text-slate-600">
                  <span className="px-2 py-0.5 bg-gold/20 font-semibold text-ink rounded">
                    Total: {modules.length} Modules ({totalCredits} Credits)
                  </span>
                  <span>Term: {activeSemester.startDate} to {activeSemester.endDate}</span>
                </div>
              </div>
              <button
                onClick={openCreateModule}
                className="px-4 py-2 bg-gold hover:bg-gold-dark text-ink font-semibold rounded-lg shadow text-sm transition flex items-center justify-center space-x-1.5 cursor-pointer"
              >
                <span>➕</span>
                <span>Add Module</span>
              </button>
            </div>

            {loadingModules ? (
              <div className="text-slate-500 animate-pulse p-4">Loading modules...</div>
            ) : modules.length === 0 ? (
              <div className="text-center py-12 px-4 border border-dashed border-hairline rounded-xl space-y-3 bg-paper/30">
                <div className="text-3xl">📖</div>
                <h4 className="font-bold text-ink">No modules added to this semester yet</h4>
                <p className="text-slate-600 text-sm max-w-sm mx-auto">
                  Add modules with their course codes, names, and credit counts to start tracking assignments and planning your studies.
                </p>
                <button
                  onClick={openCreateModule}
                  className="mt-2 px-4 py-2 bg-gold hover:bg-gold-dark text-ink font-semibold rounded-md shadow text-sm transition"
                >
                  ➕ Add Module Now
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {modules.map((mod) => (
                  <div
                    key={mod.id}
                    className="p-5 rounded-xl border border-hairline bg-paper/40 hover:bg-white hover:border-gold/60 transition-all shadow-sm flex flex-col justify-between"
                  >
                    <div>
                      <div className="flex items-center justify-between mb-2">
                        <span className="font-mono text-xs font-bold px-2.5 py-1 bg-ink text-gold rounded shadow-xs">
                          {mod.code}
                        </span>
                        <span className="text-xs font-semibold px-2 py-0.5 bg-slate-100 text-slate-700 rounded-full">
                          {mod.credits} {mod.credits === 1 ? "Credit" : "Credits"}
                        </span>
                      </div>
                      <h3 className="font-bold text-ink text-base mt-2">{mod.name}</h3>
                      {mod.description ? (
                        <p className="text-xs text-slate-600 mt-2 line-clamp-2 leading-relaxed">
                          {mod.description}
                        </p>
                      ) : (
                        <p className="text-xs text-slate-400 italic mt-2">No description provided</p>
                      )}
                    </div>
                    <div className="mt-4 pt-3 border-t border-hairline flex items-center justify-between">
                      <span className="text-xs text-slate-500">
                        {mod.assessmentCount || 0} assessments
                      </span>
                      <div className="space-x-3 text-xs">
                        <button
                          onClick={() => openEditModule(mod)}
                          className="text-slate-700 hover:text-ink font-medium"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => {
                            if (confirm(`Are you sure you want to delete module "${mod.code} - ${mod.name}"?`)) {
                              deleteModuleMutation.mutate(mod.id);
                            }
                          }}
                          className="text-red-500 hover:text-red-700 font-medium"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Semester Modal */}
        {showSemesterModal && (
          <div className="fixed inset-0 bg-ink/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-hairline space-y-4 animate-scale-up">
              <div className="flex items-center justify-between border-b border-hairline pb-3">
                <h3 className="text-lg font-serif font-bold text-ink">
                  {editingSemester ? "Edit Semester" : "Add New Semester"}
                </h3>
                <button
                  type="button"
                  onClick={() => setShowSemesterModal(false)}
                  className="text-slate-400 hover:text-ink text-sm font-bold"
                >
                  ✕
                </button>
              </div>

              {semesterError && (
                <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-xs">
                  ⚠️ {semesterError}
                </div>
              )}

              <form onSubmit={handleSemesterSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Semester Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Fall 2026 / Year 3 Semester 1"
                    value={semForm.name}
                    onChange={(e) => setSemForm({ ...semForm, name: e.target.value })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-gold focus:border-gold outline-none"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Start Date <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="date"
                      required
                      value={semForm.startDate}
                      onChange={(e) => setSemForm({ ...semForm, startDate: e.target.value })}
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-gold outline-none"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      End Date <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="date"
                      required
                      value={semForm.endDate}
                      onChange={(e) => setSemForm({ ...semForm, endDate: e.target.value })}
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-gold outline-none"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Status
                  </label>
                  <select
                    value={semForm.status}
                    onChange={(e) => setSemForm({ ...semForm, status: e.target.value as SemesterStatus })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-gold outline-none bg-white"
                  >
                    <option value="ACTIVE">ACTIVE (Current)</option>
                    <option value="PLANNED">PLANNED (Upcoming)</option>
                    <option value="COMPLETED">COMPLETED (Past)</option>
                  </select>
                </div>

                <div className="flex justify-end space-x-3 pt-4 border-t border-hairline">
                  <button
                    type="button"
                    onClick={() => setShowSemesterModal(false)}
                    className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-ink"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={createSemesterMutation.isPending || updateSemesterMutation.isPending}
                    className="px-5 py-2.5 bg-gold hover:bg-gold-dark text-ink font-semibold text-xs rounded-lg shadow transition disabled:opacity-50 flex items-center space-x-1.5"
                  >
                    {(createSemesterMutation.isPending || updateSemesterMutation.isPending) && (
                      <span className="animate-spin">⏳</span>
                    )}
                    <span>
                      {createSemesterMutation.isPending
                        ? "Creating..."
                        : updateSemesterMutation.isPending
                        ? "Saving..."
                        : editingSemester
                        ? "Update Semester"
                        : "Create Semester & Add Modules"}
                    </span>
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Module Modal */}
        {showModuleModal && (
          <div className="fixed inset-0 bg-ink/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-hairline space-y-4 animate-scale-up">
              <div className="flex items-center justify-between border-b border-hairline pb-3">
                <div>
                  <h3 className="text-lg font-serif font-bold text-ink">
                    {editingModule ? "Edit Module" : "Add Module to Semester"}
                  </h3>
                  {activeSemester && (
                    <p className="text-xs text-slate-500">Semester: {activeSemester.name}</p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setShowModuleModal(false)}
                  className="text-slate-400 hover:text-ink text-sm font-bold"
                >
                  ✕
                </button>
              </div>

              {moduleError && (
                <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-xs">
                  ⚠️ {moduleError}
                </div>
              )}

              <form onSubmit={handleModuleSubmit} className="space-y-4">
                <div className="grid grid-cols-3 gap-3">
                  <div className="col-span-1">
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Code <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. CS201"
                      value={modForm.code}
                      onChange={(e) => setModForm({ ...modForm, code: e.target.value.toUpperCase() })}
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm uppercase font-mono focus:ring-2 focus:ring-gold outline-none"
                    />
                  </div>
                  <div className="col-span-2">
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Module Name <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. Data Structures"
                      value={modForm.name}
                      onChange={(e) => setModForm({ ...modForm, name: e.target.value })}
                      className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-gold outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Credits <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="30"
                    required
                    value={modForm.credits}
                    onChange={(e) => setModForm({ ...modForm, credits: parseInt(e.target.value) || 1 })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-gold outline-none"
                  />
                  <p className="text-[11px] text-slate-500 mt-1">
                    Credits define the baseline study workload for this subject.
                  </p>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Description / Syllabus Notes (Optional)
                  </label>
                  <textarea
                    rows={3}
                    placeholder="Key topics, lecture schedule, or focus areas..."
                    value={modForm.description || ""}
                    onChange={(e) => setModForm({ ...modForm, description: e.target.value })}
                    className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-gold outline-none resize-none"
                  />
                </div>

                {!editingModule && (
                  <div className="flex items-center space-x-2 pt-1">
                    <input
                      type="checkbox"
                      id="addAnother"
                      checked={addAnother}
                      onChange={(e) => setAddAnother(e.target.checked)}
                      className="rounded text-gold focus:ring-gold"
                    />
                    <label htmlFor="addAnother" className="text-xs text-slate-600 font-medium cursor-pointer">
                      Keep modal open to add another module after saving
                    </label>
                  </div>
                )}

                <div className="flex justify-end space-x-3 pt-4 border-t border-hairline">
                  <button
                    type="button"
                    onClick={() => setShowModuleModal(false)}
                    className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-ink"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={createModuleMutation.isPending || updateModuleMutation.isPending}
                    className="px-5 py-2.5 bg-gold hover:bg-gold-dark text-ink font-semibold text-xs rounded-lg shadow transition disabled:opacity-50 flex items-center space-x-1.5"
                  >
                    {(createModuleMutation.isPending || updateModuleMutation.isPending) && (
                      <span className="animate-spin">⏳</span>
                    )}
                    <span>
                      {createModuleMutation.isPending
                        ? "Saving..."
                        : updateModuleMutation.isPending
                        ? "Updating..."
                        : editingModule
                        ? "Update Module"
                        : "Save Module"}
                    </span>
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

