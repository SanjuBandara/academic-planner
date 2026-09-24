import React from "react";
import { ChatMessage, PlanModificationProposal } from "../../types/ai";
import { PlanChangeConfirmation } from "./PlanChangeConfirmation";

interface AiMessageProps {
  message: ChatMessage;
  onConfirmProposal?: (proposalId: string) => void;
  onCancelProposal?: (proposalId: string) => void;
  isProcessingAction?: boolean;
}

const INTENT_COLORS: Record<string, string> = {
  GET_TODAY_PLAN: "bg-blue-100 text-blue-700",
  GET_WEEK_PLAN: "bg-indigo-100 text-indigo-700",
  GET_UPCOMING_ASSESSMENTS: "bg-red-100 text-red-700",
  GET_TASKS: "bg-amber-100 text-amber-700",
  GET_AVAILABLE_TIME: "bg-teal-100 text-teal-700",
  MODIFY_PLAN: "bg-orange-100 text-orange-700",
  MARK_ACTIVITY_COMPLETED: "bg-emerald-100 text-emerald-700",
  REPLAN: "bg-purple-100 text-purple-700",
  CREATE_DAILY_PLAN: "bg-cyan-100 text-cyan-700",
  GENERAL_PLAN_QUESTION: "bg-slate-100 text-slate-600",
  // Phase 4
  QUICK_ADD_TASK: "bg-green-100 text-green-700",
  SKIP_TODAY: "bg-slate-200 text-slate-600",
  BURNOUT_CHECK: "bg-rose-100 text-rose-700",
  STUDY_BREAKDOWN: "bg-violet-100 text-violet-700",
};

/**
 * Renders a single AI or user message with full rich markdown formatting.
 * Supports: **bold**, *italic*, bullet lists (•, -), headers (##), and emoji.
 */
const renderMarkdown = (text: string): React.ReactNode => {
  const lines = text.split("\n");
  const nodes: React.ReactNode[] = [];
  let bulletBuffer: string[] = [];

  const flushBullets = (key: string) => {
    if (bulletBuffer.length > 0) {
      nodes.push(
        <ul key={`ul-${key}`} className="space-y-1 my-1.5 pl-1">
          {bulletBuffer.map((b, i) => (
            <li key={i} className="flex items-start gap-1.5 text-sm">
              <span className="mt-0.5 shrink-0 text-gold-dark">•</span>
              <span>{renderInline(b)}</span>
            </li>
          ))}
        </ul>
      );
      bulletBuffer = [];
    }
  };

  lines.forEach((line, idx) => {
    const trimmed = line.trim();

    // Blank line
    if (!trimmed) {
      flushBullets(String(idx));
      nodes.push(<div key={`br-${idx}`} className="h-1.5" />);
      return;
    }

    // ## Heading
    if (trimmed.startsWith("## ")) {
      flushBullets(String(idx));
      nodes.push(
        <p key={idx} className="font-bold text-ink text-sm mt-2 mb-0.5">
          {renderInline(trimmed.slice(3))}
        </p>
      );
      return;
    }

    // Bullet: lines starting with •, -, or *
    if (/^[•\-\*]\s/.test(trimmed)) {
      bulletBuffer.push(trimmed.replace(/^[•\-\*]\s/, ""));
      return;
    }

    // Regular paragraph
    flushBullets(String(idx));
    nodes.push(
      <p key={idx} className="text-sm leading-relaxed">
        {renderInline(trimmed)}
      </p>
    );
  });

  // Flush any trailing bullets
  flushBullets("end");
  return <>{nodes}</>;
};

/** Handles inline **bold**, *italic*, and plain text within a line */
const renderInline = (text: string): React.ReactNode => {
  const parts = text.split(/(\*\*.*?\*\*|\*.*?\*)/g);
  return parts.map((part, i) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={i} className="font-semibold text-ink">{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith("*") && part.endsWith("*")) {
      return <em key={i} className="italic text-slate-600">{part.slice(1, -1)}</em>;
    }
    return part;
  });
};

export const AiMessage: React.FC<AiMessageProps> = ({
  message,
  onConfirmProposal,
  onCancelProposal,
  isProcessingAction,
}) => {
  const isUser = message.sender === "user";
  const intentColorClass = message.intent ? (INTENT_COLORS[message.intent] || "bg-slate-100 text-slate-600") : "";

  return (
    <div className={`flex flex-col ${isUser ? "items-end" : "items-start"} mb-3`}>
      {/* Meta row: sender label, timestamp, intent badge */}
      <div className="flex items-center gap-1.5 mb-1 px-1 text-[11px] text-slate-400">
        <span className="font-medium">{isUser ? "You" : "AI Planner"}</span>
        <span>•</span>
        <span>{message.timestamp}</span>
        {message.intent && !isUser && (
          <span className={`ml-1 px-1.5 py-0.5 rounded text-[9px] font-mono font-semibold uppercase ${intentColorClass}`}>
            {message.intent.replace(/_/g, " ")}
          </span>
        )}
      </div>

      {/* Bubble */}
      <div
        className={`max-w-[88%] rounded-2xl px-4 py-3 shadow-sm ${
          isUser
            ? "bg-ink text-white rounded-br-none"
            : "bg-white text-slate-700 border border-hairline rounded-bl-none"
        }`}
      >
        {isUser ? (
          <p className="text-sm leading-relaxed">{message.text}</p>
        ) : (
          <div className="space-y-0.5">{renderMarkdown(message.text)}</div>
        )}

        {message.actionRequired && message.action && onConfirmProposal && onCancelProposal && (
          <PlanChangeConfirmation
            proposal={message.action as PlanModificationProposal}
            onConfirm={onConfirmProposal}
            onCancel={onCancelProposal}
            isProcessing={isProcessingAction}
          />
        )}
      </div>
    </div>
  );
};


