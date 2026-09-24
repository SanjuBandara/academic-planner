import React from "react";
import { ChatMessage, PlanModificationProposal } from "../../types/ai";
import { PlanChangeConfirmation } from "./PlanChangeConfirmation";

interface AiMessageProps {
  message: ChatMessage;
  onConfirmProposal?: (proposalId: string) => void;
  onCancelProposal?: (proposalId: string) => void;
  isProcessingAction?: boolean;
}

export const AiMessage: React.FC<AiMessageProps> = ({
  message,
  onConfirmProposal,
  onCancelProposal,
  isProcessingAction,
}) => {
  const isUser = message.sender === "user";

  // Simple formatter for bullet points and bold markdown
  const renderFormattedText = (text: string) => {
    return text.split("\n").map((line, idx) => {
      // Bold replacement
      const parts = line.split(/(\*\*.*?\*\*)/g);
      const formattedLine = parts.map((part, pIdx) => {
        if (part.startsWith("**") && part.endsWith("**")) {
          return (
            <strong key={pIdx} className="font-semibold text-ink">
              {part.slice(2, -2)}
            </strong>
          );
        }
        return part;
      });

      return (
        <span key={idx} className="block min-h-[1.2rem]">
          {formattedLine}
        </span>
      );
    });
  };

  return (
    <div className={`flex flex-col ${isUser ? "items-end" : "items-start"} mb-4`}>
      <div className="flex items-center gap-1.5 mb-1 px-1 text-[11px] text-slate-400">
        <span>{isUser ? "You" : "AI Planner Assistant"}</span>
        <span>•</span>
        <span>{message.timestamp}</span>
        {message.intent && !isUser && (
          <span className="ml-1 px-1.5 py-0.2 bg-gold/15 text-gold-dark rounded text-[9px] font-mono font-medium">
            {message.intent}
          </span>
        )}
      </div>

      <div
        className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm ${
          isUser
            ? "bg-ink text-white rounded-br-none"
            : "bg-white text-slate-700 border border-hairline rounded-bl-none"
        }`}
      >
        <div className="space-y-1">{renderFormattedText(message.text)}</div>

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
