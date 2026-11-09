import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  activateModelConfig, deleteModelConfig, listModelConfigs, listTemplates, saveModelConfig,
  type ModelConfigInput, type ModelConfigItem, type ModelTemplate,
} from '../api/modelConfig'

const EMPTY: ModelConfigInput = {
  name: '', provider: 'custom', baseUrl: '', apiKey: '', modelName: '',
  visionSupported: false, streamSupported: true, extraOptions: '',
}

export default function ModelConfigPanel() {
  const [configs, setConfigs] = useState<ModelConfigItem[]>([])
  const [templates, setTemplates] = useState<ModelTemplate[]>([])
  const [form, setForm] = useState<ModelConfigInput>(EMPTY)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const reload = useCallback(async () => {
    try {
      const [list, tpl] = await Promise.all([listModelConfigs(), listTemplates()])
      setConfigs(list)
      setTemplates(tpl)
      setError(null)
    } catch (e) {
      setError(String(e))
    }
  }, [])

  useEffect(() => { void reload() }, [reload])

  const update = <K extends keyof ModelConfigInput>(key: K, value: ModelConfigInput[K]) => {
    setForm((previous) => ({ ...previous, [key]: value }))
  }

  const applyTemplate = (provider: string) => {
    const template = templates.find((t) => t.provider === provider)
    update('provider', provider)
    if (template) {
      update('baseUrl', template.baseUrl)
      update('visionSupported', template.visionSupported)
    }
  }

  const startEdit = (item: ModelConfigItem) => {
    setEditingId(item.id)
    setForm({
      name: item.name, provider: item.provider, baseUrl: item.baseUrl, apiKey: '',
      modelName: item.modelName, visionSupported: item.visionSupported,
      streamSupported: item.streamSupported, extraOptions: item.extraOptions ?? '',
    })
    setNotice('正在编辑；API Key 留空表示不修改。')
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    try {
      await saveModelConfig(form, editingId ?? undefined)
      setNotice(editingId ? '配置已更新' : '配置已新增')
      setEditingId(null)
      setForm(EMPTY)
      await reload()
    } catch (e) {
      setError(String(e))
    }
  }

  const act = async (fn: () => Promise<void>, message: string) => {
    try {
      await fn()
      setNotice(message)
      setError(null)
      await reload()
    } catch (e) {
      setError(String(e))
    }
  }

  return (
    <section className="card">
      <h2>模型配置</h2>
      <p className="muted">系统不内置任何厂商与密钥；请填写你自己的 OpenAI 兼容端点。图片题需要支持视觉的模型。</p>

      {error && <p className="error">操作失败：{error}</p>}
      {notice && <p className="ok">{notice}</p>}

      <div className="config-grid">
        <div>
          <h3>已有配置</h3>
          <ul className="config-list">
            {configs.map((item) => (
              <li key={item.id} className={item.isCurrent ? 'current' : ''}>
                <div>
                  <strong>{item.name}</strong>
                  <small>{item.provider} · {item.modelName}</small>
                  <small>{item.baseUrl || '（未填写地址）'}</small>
                  <small>
                    Key：{item.hasApiKey ? '已设置（不回显）' : '未设置'} ·
                    视觉：{item.visionSupported ? '支持' : '不支持'}
                    {item.isCurrent ? ' · 当前生效' : ''}
                  </small>
                </div>
                <div className="row-actions">
                  <button type="button" className="btn small" onClick={() => startEdit(item)}>编辑</button>
                  {!item.isCurrent && (
                    <button type="button" className="btn small" onClick={() => void act(() => activateModelConfig(item.id), '已设为当前')}>设为当前</button>
                  )}
                  <button type="button" className="btn small danger" onClick={() => void act(() => deleteModelConfig(item.id), '已删除')}>删除</button>
                </div>
              </li>
            ))}
          </ul>
          {configs.length === 0 && <p className="muted">暂无配置</p>}
        </div>

        <form onSubmit={(e) => void submit(e)} className="config-form">
          <h3>{editingId ? '编辑配置' : '新增配置'}</h3>
          <label>模板
            <select value={form.provider} onChange={(e) => applyTemplate(e.target.value)}>
              {templates.map((t) => <option key={t.provider} value={t.provider}>{t.label}</option>)}
            </select>
          </label>
          <label>显示名<input required value={form.name} onChange={(e) => update('name', e.target.value)} /></label>
          <label>Base URL<input required placeholder="https://.../v1" value={form.baseUrl} onChange={(e) => update('baseUrl', e.target.value)} /></label>
          <label>模型名<input required value={form.modelName} onChange={(e) => update('modelName', e.target.value)} /></label>
          <label>API Key<input type="password" autoComplete="off" placeholder={editingId ? '留空表示不修改' : '仅提交时传入'} value={form.apiKey} onChange={(e) => update('apiKey', e.target.value)} /></label>
          <label className="checkbox"><input type="checkbox" checked={form.visionSupported} onChange={(e) => update('visionSupported', e.target.checked)} />支持图片输入（必填声明）</label>
          <label className="checkbox"><input type="checkbox" checked={form.streamSupported} onChange={(e) => update('streamSupported', e.target.checked)} />支持流式输出</label>
          <label>扩展参数 JSON（可选）<textarea rows={2} value={form.extraOptions} onChange={(e) => update('extraOptions', e.target.value)} /></label>
          <div className="form-actions">
            <button type="submit" className="btn primary">{editingId ? '保存修改' : '新增配置'}</button>
            {editingId && <button type="button" className="btn" onClick={() => { setEditingId(null); setForm(EMPTY); setNotice(null) }}>取消编辑</button>}
          </div>
        </form>
      </div>
    </section>
  )
}
