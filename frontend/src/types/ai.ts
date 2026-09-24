export type AiIntent =
  | "GENERAL_PLAN_QUESTION"
  | "GET_TODAY_PLAN"
  | "GET_WEEK_PLAN"
  | "GET_UPCOMING_ASSESSMENTS"
  | "GET_TASKS"
  | "GET_AVAILABLE_TIME"
  | "CREATE_DAILY_PLAN"
  | "MODIFY_PLAN"
  | "REPLAN"
  | "MARK_ACTIVITY_COMPLETED"
  | "UNKNOWN";

export type AiAction =
  | "NONE"
  | "CREATE_DAILY_PLAN"
  | "INCREASE_ACTIVITY_TIME"
  | "DECREASE_ACTIVITY_TIME"
  | "MOVE_ACTIVITY"
  | "REPLAN"
  | "MARK_COMPLETED"
  | "UPDATE_AVAILABILITY";

export interface AiChatRequest {
  message: string;
}

export interface PlanModificationProposal {
  proposalId?: string;
  type?: string;
  status?: "FEASIBLE" | "PARTIALLY_FEASIBLE" | "INFEASIBLE" | "REQUIRES_CONFIRMATION";
  requestedMinutes?: number;
  allocatedMinutes?: number;
  reason?: string;
  affectedActivities?: string[];
}

export interface AiChatResponse {
  message: string;
  intent: AiIntent;
  actionRequired: boolean;
  action?: PlanModificationProposal | null;
  planPreview?: any;
}

export interface AiActionRequest {
  proposalId: string;
  confirmed: boolean;
}

export interface AiActionResponse {
  success: boolean;
  message: string;
  result?: any;
}

export interface ChatMessage {
  id: string;
  sender: "user" | "assistant";
  text: string;
  intent?: AiIntent;
  actionRequired?: boolean;
  action?: PlanModificationProposal | null;
  planPreview?: any;
  timestamp: string;
}
