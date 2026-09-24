import React from "react";
import { PlanModificationProposal } from "../../types/ai";

interface PlanChangeConfirmationProps {
  proposal: PlanModificationProposal;
  onConfirm: (proposalId: string) => void;
  onCancel: (proposalId: string) => void;
  isProcessing?: boolean;
}

export const PlanChangeConfirmation: React.FC<PlanChangeConfirmationProps> = ({
  proposal,
  onConfirm,
  onCancel,
  isProcessing = false,
}) => {
  if (!proposal || !proposal.proposalId) {
    return null;
  }

  const isFeasible = proposal.status === "FEASIBLE";
  const isPartially = proposal.status === "PARTIALLY_FEASIBLE";

  return (
    <div className="mt-3 p-4 rounded-xl border border-hairline bg-paper/90 shadow-sm space-y-3">
      <div className="flex items-center justify-between">
        <span className="text-xs uppercase font-bold tracking-wider text-ink flex items-center gap-1.5">
          <span>⚡</span>
          <span>Proposed Schedule Change</span>
        </span>
        <span
          className={`px-2 py-0.5 text-[10px] font-semibold rounded-full uppercase ${
            isFeasible
              ? "bg-emerald-100 text-emerald-800"
              : isPartially
              ? "bg-amber-100 text-amber-800"
              : "bg-red-100 text-red-800"
          }`}
        >
          {proposal.status || "CONFIRMATION NEEDED"}
        </span>
      </div>

      {proposal.reason && (
        <p className="text-xs text-slate-600 leading-relaxed bg-white p-2.5 rounded-lg border border-hairline/60">
          {proposal.reason}
        </p>
      )}

      {proposal.allocatedMinutes !== undefined && (
        <div className="flex items-center gap-4 text-xs text-slate-500">
          <div>
            Requested:{" "}
            <span className="font-semibold text-ink">{proposal.requestedMinutes} min</span>
          </div>
          <div>
            Allocatable:{" "}
            <span className="font-semibold text-gold-dark">{proposal.allocatedMinutes} min</span>
          </div>
        </div>
      )}

      <div className="flex items-center gap-2 pt-1">
        <button
          type="button"
          onClick={() => onConfirm(proposal.proposalId!)}
          disabled={isProcessing}
          className="px-3.5 py-1.5 bg-gold hover:bg-gold-dark text-ink font-bold text-xs rounded-lg shadow-sm transition disabled:opacity-50 flex items-center gap-1"
        >
          <span>✓</span>
          <span>Apply Change</span>
        </button>
        <button
          type="button"
          onClick={() => onCancel(proposal.proposalId!)}
          disabled={isProcessing}
          className="px-3.5 py-1.5 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold text-xs rounded-lg transition disabled:opacity-50"
        >
          Cancel
        </button>
      </div>
    </div>
  );
};
