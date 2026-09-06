export type SemesterStatus = 'PLANNED' | 'ACTIVE' | 'COMPLETED';

export interface Semester {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  status: SemesterStatus;
  moduleCount : number;
  totalCredits: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface SemesterRequest {
  name: string;
  startDate: string;
  endDate: string;
  status?: SemesterStatus;
}

export interface Module {
  id: number;
  semesterId?: number;
  semesterName?: string;
  code: string;
  name: string;
  credits: number;
  description?: string;
  assessmentCount?: number;
}

export interface ModuleRequest {
  code: string;
  name: string;
  credits: number;
  description?: string;
}

export type AssessmentType = 'ASSIGNMENT' | 'EXAM' | 'QUIZ' | 'PROJECT';
export type PriorityLevel = 'HIGH' | 'MEDIUM' | 'LOW';
export type AssessmentStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface Assessment {
  id: number;
  moduleId: number;
  moduleName?: string;
  title: string;
  type: AssessmentType;
  description?: string;
  dueDateTime: string;
  weight: number;
  priority: PriorityLevel;
  status: AssessmentStatus;
  daysUntilDeadline?: number;
}

export interface AssessmentRequest {
  title: string;
  type: AssessmentType;
  description?: string;
  dueDateTime: string;
  weight: number;
  priority?: PriorityLevel;
  status?: AssessmentStatus;
}

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface Task {
  id: number;
  moduleId?: number;
  moduleName?: string;
  assessmentId?: number;
  assessmentTitle?: string;
  title: string;
  description?: string;
  estimatedHours: number;
  remainingHours: number;
  priority: PriorityLevel;
  status: TaskStatus;
  dueDateTime?: string;
  completedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TaskRequest {
  title: string;
  description?: string;
  moduleId?: number;
  assessmentId?: number;
  estimatedHours?: number;
  priority?: PriorityLevel;
  status?: TaskStatus;
  dueDateTime?: string;
}

export interface TaskProgressUpdate {
  workedHours: number;
}

export type ItemStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'POSTPONED';

export interface StudyPlanItem {
  id: number;
  date: string;
  startTime: string;
  endTime: string;
  moduleId: number;
  moduleCode: string;
  moduleName: string;
  assessmentId?: number;
  assessmentTitle?: string;
  taskId?: number;
  taskTitle?: string;
  plannedHours: number;
  actualHours?: number;
  status: ItemStatus;
  priorityScore?: number;
}

export interface StudyPlan {
  id: number;
  type: 'WEEKLY' | 'MONTHLY';
  startDate: string;
  endDate: string;
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  totalAvailableHours: number;
  totalPlannedHours: number;
  items: StudyPlanItem[];
}

export interface WeeklyPlanRequest {
  startDate: string;
  availability: Record<string, number>;
}
