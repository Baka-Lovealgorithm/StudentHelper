import { useCallback, useEffect, useRef, useState } from 'react'
import { getProviderOptions, type ProviderOption } from '../api/modelProvider'

interface Props {
  sessionId: number | null
  /** 当前会话选中的模型；null 时显示全局默认。 */
  modelId: number | null
  onChanged: (modelId: number) => void
  disabled?: boolean
}

/**
 * CR-014：单一触发器的分组下拉——厂商名作分组标题、旗下模型完整平铺、当前项打勾，
 * 不提供搜索框；整组由 Composer 渲染在发送按钮左侧。接口与每会话作用域不变（CR-012）。
 */
export default function ModelSelector({ sessionId, modelId, onChanged, disabled }: Props) {
  const [options, setOptions] = useState<ProviderOption[]>([])
  const [defaultId, setDefaultId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  const reload = useCallback(async () => {
    const data = await getProviderOptions()
    setOptions(data.options)
    setDefaultId(data.defaultModelId)
  }, [])

  useEffect(() => { void reload().catch((e) => setError(String(e))) }, [reload])

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const effectiveId = modelId ?? defaultId
  const current = options.flatMap((option) => option.models).find((model) => model.id === effectiveId)
  const label = current?.modelName ?? (options.length === 0 ? '未配置' : '选择模型')
  const unavailable = disabled || !sessionId || options.length === 0

  return (
    <div className="model-selector" ref={rootRef}
      title="切换仅影响当前会话，新会话继承全局默认模型">
      <button type="button" className="model-trigger" aria-haspopup="listbox" aria-expanded={open}
        disabled={unavailable} onClick={() => setOpen((previous) => !previous)}>
        <span className="model-trigger-label">{label}</span>
        <span className="model-caret" aria-hidden="true">⌄</span>
      </button>
      {open && (
        <div className="model-menu" role="listbox" aria-label="选择模型">
          {options.map((group) => (
            <div className="model-group" key={group.providerId}>
              <div className="model-group-name">{group.providerName}</div>
              {group.models.map((model) => (
                <button type="button" role="option" aria-selected={model.id === effectiveId}
                  className={`model-option ${model.id === effectiveId ? 'selected' : ''}`}
                  key={model.id}
                  onClick={() => {
                    if (sessionId) onChanged(model.id)
                    setOpen(false)
                  }}>
                  <span>{model.modelName}{model.id === defaultId ? '（默认）' : ''}</span>
                  {model.id === effectiveId && <span className="model-check" aria-hidden="true">✓</span>}
                </button>
              ))}
            </div>
          ))}
          {options.length === 0 && <div className="model-empty">未配置模型</div>}
        </div>
      )}
      {error && <span className="error" title={error}>选项加载失败</span>}
    </div>
  )
}
