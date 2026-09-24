import React, { useState, useRef, useEffect } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { aiAssistantApi } from "../../api/aiAssistantApi";
import { ChatMessage } from "../../types/ai";
import { AiMessage } from "./AiMessage";
import { AiInput } from "./AiInput";

export const AiAssistant: React.FC = () => {
  const queryClient = useQueryClient();
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "initial-welcome",
      sender: "assistant",
      text: "Hello! I am your AI Planning Assistant. Ask me anything about your current study plan, today's schedule, upcoming assessments, or workload. What would you like help with?",
      timestamp: "Just now",
      intent: "GENERAL_PLAN_QUESTION",
    },
  ]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Chat mutation
  const chatMutation = useMutation({
    mutationFn: (userText: string) => aiAssistantApi.sendMessage(userText),
    onSuccess: (data) => {
      const assistantMsg: ChatMessage = {
        id: "ai-" + Date.now(),
        sender: "assistant",
        text: data.message,
        intent: data.intent,
        actionRequired: data.actionRequired,
        action: data.action,
        planPreview: data.planPreview,
        timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
      };
      setMessages((prev) => [...prev, assistantMsg]);
    },
    onError: (err: any) => {
      const errorMsg: ChatMessage = {
        id: "err-" + Date.now(),
        sender: "assistant",
        text:
          err.response?.data?.message ||
          "The AI assistant is temporarily unavailable. Your existing planner is still available.",
        timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
      };
      setMessages((prev) => [...prev, errorMsg]);
    },
  });

  // Action confirmation mutation
  const confirmActionMutation = useMutation({
    mutationFn: ({ proposalId, confirmed }: { proposalId: string; confirmed: boolean }) =>
      aiAssistantApi.confirmAction(proposalId, confirmed),
    onSuccess: (data) => {
      const sysMsg: ChatMessage = {
        id: "sys-" + Date.now(),
        sender: "assistant",
        text: data.message,
        timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
      };
      setMessages((prev) => [...prev, sysMsg]);
      // Invalidate relevant queries so dashboard updates in real-time!
      queryClient.invalidateQueries({ queryKey: ["todays-schedule"] });
      queryClient.invalidateQueries({ queryKey: ["active-study-plan"] });
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
    onError: (err: any) => {
      alert("Failed to process proposal: " + (err.response?.data?.message || err.message));
    },
  });

  const handleSendMessage = (text: string) => {
    const userMsg: ChatMessage = {
      id: "usr-" + Date.now(),
      sender: "user",
      text,
      timestamp: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
    };
    setMessages((prev) => [...prev, userMsg]);
    chatMutation.mutate(text);
  };

  const handleConfirmProposal = (proposalId: string) => {
    confirmActionMutation.mutate({ proposalId, confirmed: true });
  };

  const handleCancelProposal = (proposalId: string) => {
    confirmActionMutation.mutate({ proposalId, confirmed: false });
  };

  const handleClearHistory = () => {
    setMessages([
      {
        id: "initial-welcome-" + Date.now(),
        sender: "assistant",
        text: "Chat cleared. How can I help you with your academic schedule today?",
        timestamp: "Just now",
        intent: "GENERAL_PLAN_QUESTION",
      },
    ]);
  };

  return (
    <div className="bg-white rounded-2xl border border-hairline shadow-sm overflow-hidden flex flex-col h-[520px]">
      {/* Header */}
      <div className="p-4 border-b border-hairline bg-gradient-to-r from-ink/5 via-paper to-ink/5 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-ink text-gold flex items-center justify-center font-bold shadow-sm text-base">
            🤖
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-serif font-bold text-sm text-ink">AI Study Assistant</h3>
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-medium bg-emerald-100 text-emerald-800">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                Active
              </span>
            </div>
            <p className="text-[11px] text-slate-500">
              Natural-language schedule intelligence & plan guidance
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={handleClearHistory}
          className="text-xs text-slate-400 hover:text-slate-600 transition px-2 py-1 rounded hover:bg-slate-100"
          title="Clear chat history"
        >
          Reset
        </button>
      </div>

      {/* Message List */}
      <div className="flex-1 overflow-y-auto p-4 space-y-2 bg-slate-50/50">
        {messages.map((msg) => (
          <AiMessage
            key={msg.id}
            message={msg}
            onConfirmProposal={handleConfirmProposal}
            onCancelProposal={handleCancelProposal}
            isProcessingAction={confirmActionMutation.isPending}
          />
        ))}

        {chatMutation.isPending && (
          <div className="flex items-center gap-2 text-xs text-slate-400 py-2 px-1">
            <span className="w-3.5 h-3.5 border-2 border-gold-dark border-t-transparent rounded-full animate-spin" />
            <span>AI Assistant is analyzing your academic plan...</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input Box */}
      <div className="p-3.5 border-t border-hairline bg-white">
        <AiInput onSendMessage={handleSendMessage} isLoading={chatMutation.isPending} />
      </div>
    </div>
  );
};
