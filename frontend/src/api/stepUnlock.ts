import { unwrap } from './client'

export interface UnlockState {
  messageId: number
  unlockedStep: number
}

export async function unlockStep(messageId: number, step: number): Promise<UnlockState> {
  return unwrap<UnlockState>(await fetch('/api/step-unlock', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messageId, step }),
  }))
}

export async function getUnlockState(messageId: number): Promise<UnlockState> {
  return unwrap<UnlockState>(await fetch(`/api/step-unlock?messageId=${messageId}`))
}
