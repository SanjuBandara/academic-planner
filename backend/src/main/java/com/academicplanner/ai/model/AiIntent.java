package com.academicplanner.ai.model;

/**
 * Supported intents for the AI Planning Assistant.
 * Strictly controlled so the LLM cannot invent arbitrary intent strings.
 */
public enum AiIntent {
    GENERAL_PLAN_QUESTION,
    GET_TODAY_PLAN,
    GET_WEEK_PLAN,
    GET_UPCOMING_ASSESSMENTS,
    GET_TASKS,
    GET_AVAILABLE_TIME,
    CREATE_DAILY_PLAN,
    MODIFY_PLAN,
    REPLAN,
    MARK_ACTIVITY_COMPLETED,
    UNKNOWN
}
