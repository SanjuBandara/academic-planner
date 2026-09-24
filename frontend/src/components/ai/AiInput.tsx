import React, { useState, KeyboardEvent } from "react";

interface AiInputProps {
  onSendMessage: (message: string) => void;
  isLoading: boolean;
}

const QUICK_PROMPTS = [
  "What do I have to study today?",
  "Show my upcoming assessments",
  "I finished my last session",
  "I'm behind schedule — what should I do?",
  "How much study time do I have remaining?",
  "Show my pending tasks",
];


export const AiInput: React.FC<AiInputProps> = ({ onSendMessage, isLoading }) => {
  const [input, setInput] = useState("");

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = input.trim();
    if (!trimmed || isLoading) return;
    onSendMessage(trimmed);
    setInput("");
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handlePromptClick = (prompt: string) => {
    if (isLoading) return;
    onSendMessage(prompt);
  };

  return (
    <div className="space-y-3">
      {/* Quick Prompts */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none text-xs">
        <span className="text-slate-400 font-medium text-[11px] whitespace-nowrap mr-1">
          💡 Try asking:
        </span>
        {QUICK_PROMPTS.map((prompt, i) => (
          <button
            key={i}
            type="button"
            onClick={() => handlePromptClick(prompt)}
            disabled={isLoading}
            className="px-2.5 py-1 bg-paper hover:bg-gold/10 hover:border-gold/40 border border-hairline rounded-full text-slate-600 hover:text-ink text-[11px] whitespace-nowrap transition disabled:opacity-50"
          >
            {prompt}
          </button>
        ))}
      </div>

      {/* Input Field */}
      <form onSubmit={handleSubmit} className="flex items-center gap-2">
        <div className="relative flex-1">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isLoading}
            placeholder="Ask about your study schedule, assessments, or plan..."
            className="w-full px-4 py-2.5 bg-paper/60 border border-hairline rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-gold/50 focus:border-gold text-ink placeholder:text-slate-400 disabled:opacity-60"
            maxLength={1000}
          />
        </div>
        <button
          type="submit"
          disabled={!input.trim() || isLoading}
          className="px-4 py-2.5 bg-gold hover:bg-gold-dark text-ink font-bold text-sm rounded-xl shadow-sm transition disabled:opacity-40 flex items-center justify-center min-w-[54px]"
        >
          {isLoading ? (
            <span className="w-4 h-4 border-2 border-ink border-t-transparent rounded-full animate-spin" />
          ) : (
            <span>➤</span>
          )}
        </button>
      </form>
    </div>
  );
};
