import { useCallback, useEffect, useState } from 'react'
import { getProviderOptions, type ProviderOption } from '../api/modelProvider'

interface Props {
  sessionId: number | null
  /** 当前会话选中的模型；null 时显示全局默认。 */
  modelId: number | null
  onChanged: (modelId: number) => void
  disabled?: boolean
}

/** CR-012：对话页「厂商 / 模型」两级下拉，切换只影响当前会话。 */
export default function ModelSelector({ sessionId, modelId, onChanged, disabled }: Props) {
  const [options, setOptions] = useState<ProviderOption[]>([])
  const [defaultId, setDefaultId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const data = await getProviderOptions()
    setOptions(data.options)
    setDefaultId(data.defaultModelId)
  }, [])

  useEffect(() => { void reload().catch((e) => setError(String(e))) }, [reload])

  const effectiveId = modelId ?? defaultId
  const owner = options.find((option) => option.models.some((model) => model.id === effectiveId))
  const providerId = owner?.providerId ?? options[0]?.providerId ?? null
  const models = options.find((option) => option.providerId === providerId)?.models ?? []
  const unavailable = disabled || !sessionId || options.length === 0

  const changeProvider = (pid: number) => {
    const first = options.find((option) => option.providerId === pid)?.models[0]
    if (first && sessionId) onChanged(first.id)
  }

  return (
    <div className="model-selector">
      <span className="muted">模型</span>
      <select aria-label="模型厂商" value={providerId ?? ''} disabled={unavailable}
        onChange={(e) => changeProvider(Number(e.target.value))}>
        {options.map((option) => (
          <option key={option.providerId} value={option.providerId}>{option.providerName}</option>
        ))}
        {options.length === 0 && <option value="">未配置</option>}
      </select>
      <select aria-label="模型名字" value={effectiveId ?? ''} disabled={unavailable}
        onChange={(e) => { if (sessionId && e.target.value) onChanged(Number(e.target.value)) }}>
        {models.map((model) => (
          <option key={model.id} value={model.id}>
            {model.modelName}{model.id === defaultId ? '（默认）' : ''}
          </option>
        ))}
        {models.length === 0 && <option value="">未配置</option>}
      </select>
      {error && <span className="error" title={error}>选项加载失败</span>}
    </div>
  )
}
