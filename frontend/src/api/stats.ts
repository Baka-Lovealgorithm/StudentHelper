import { unwrap } from './client'

export interface DayStat {
  date: string
  questions: number
  answers: number
  failed: number
}

export interface SubjectCount {
  subject: string
  count: number
}

/** CR-023：不再含 token 与耗时（只归用量看板）；days 为 0 表示全部。 */
export interface LearningStats {
  days: number
  sessions: number
  questions: number
  answers: number
  failedAnswers: number
  currentSubject: string
  byDay: DayStat[]
  bySubject: SubjectCount[]
}

export async function getLearningStats(days = 7): Promise<LearningStats> {
  return unwrap<LearningStats>(await fetch(`/api/stats/learning?days=${days}`))
}
