import { unwrap } from './client'

export interface DayCount {
  date: string
  count: number
}

export interface SubjectCount {
  subject: string
  count: number
}

export interface LearningStats {
  sessions: number
  questions: number
  answers: number
  failedAnswers: number
  promptTokens: number
  completionTokens: number
  avgDurationMs: number
  currentSubject: string
  byDay: DayCount[]
  bySubject: SubjectCount[]
}

export async function getLearningStats(): Promise<LearningStats> {
  return unwrap<LearningStats>(await fetch('/api/stats/learning'))
}
