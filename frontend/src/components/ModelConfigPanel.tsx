import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  addProviderModels, deleteProvider, deleteProviderModel, fetchRemoteModels,
  listProviders, listTemplates, saveProvider, setDefaultModel, testProvider, testProviderModel,
  updateProviderModel,
  type FetchModelsResult, type ModelInput, type ModelTemplate, type ModelTestResult,
  type ProviderItem, type ProviderList, type ProviderModelItem, type ProviderInput,
  type ProviderTestResult,
} from '../api/modelProvider'
import RowActionMenu from './RowActionMenu'

type Dialog =
  | { kind: 'provider'; provider?: ProviderItem }
  | { kind: 'model'; providerId: number; model?: ProviderModelItem }
  | { kind: 'fetch'; providerId: number }
  | null

const EMPTY_PROVIDER: ProviderInput = { name: '', baseUrl: '', apiKey: '' }
const EMPTY_MODEL: ModelInput = {
  modelName: '', visionSupported: false, streamSupported: true, extraOptions: '', isDebug: false,
}

/** CR-020：耗时展示——1s 以内用毫秒，超过用一位小数秒。 */
function formatLatency(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

/** CR-012：厂商一行一条 + 点开模型清单；新增 / 编辑全部由按钮触发弹窗，不占页面右侧。 */
export default function ModelConfigPanel() {
  const [data, setData] = useState<ProviderList>({ providers: [], defaultModelId: null })
  const [templates, setTemplates] = useState<ModelTemplate[]>([])
  const [expanded, setExpanded] = useState<number[]>([])
  const [dialog, setDialog] = useState<Dialog>(null)
  const [providerForm, setProviderForm] = useState<ProviderInput>(EMPTY_PROVIDER)
  const [template, setTemplate] = useState('custom')
  const [modelForm, setModelForm] = useState<ModelInput>(EMPTY_MODEL)
  const [fetchResult, setFetchResult] = useState<FetchModelsResult | null>(null)
  const [selectedModels, setSelectedModels] = useState<string[]>([])
  const [manualModel, setManualModel] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  /** CR-020：测试进行中的范围（厂商级 = 不带 modelId）。 */
  const [testing, setTesting] = useState<{ providerId: number; modelId?: number } | null>(null)
  /** CR-020：测试结果只存组件状态，刷新即清空——避免过期结论误导。 */
  const [modelResults, setModelResults] = useState<Record<number, ModelTestResult>>({})
  const [providerResults, setProviderResults] = useState<Record<number, ProviderTestResult>>({})

  const reload = useCallback(async () => {
    const [list, tpl] = await Promise.all([listProviders(), listTemplates()])
    setData(list)
    setTemplates(tpl)
  }, [])

  useEffect(() => { void reload().catch((e) => setError(String(e))) }, [reload])

  const run = (action: () => Promise<unknown>, message?: string) => void (async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await action()
      await reload()
      if (message) setNotice(message)
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  })()

  /** CR-020：单模型测试（后端发真实最小对话请求）。 */
  const testOneModel = (providerId: number, model: ProviderModelItem) => void (async () => {
    setTesting({ providerId, modelId: model.id })
    setError(null)
    setNotice(null)
    try {
      const result = await testProviderModel(providerId, model.id)
      setModelResults((previous) => ({ ...previous, [model.id]: result }))
    } catch (e) {
      setError(String(e))
    } finally {
      setTesting(null)
    }
  })()

  /** CR-020：厂商「全部测试」——覆盖该厂商模型列表下的全部模型。 */
  const testWholeProvider = (providerId: number) => void (async () => {
    setTesting({ providerId })
    setError(null)
    setNotice(null)
    try {
      const result = await testProvider(providerId)
      setProviderResults((previous) => ({ ...previous, [providerId]: result }))
      setModelResults((previous) => {
        const next = { ...previous }
        result.results.forEach((item) => { next[item.modelId] = item })
        return next
      })
    } catch (e) {
      setError(String(e))
    } finally {
      setTesting(null)
    }
  })()

  const closeDialog = () => {
    setDialog(null)
    setFetchResult(null)
    setSelectedModels([])
    setManualModel('')
    setError(null)
  }

  const openProviderDialog = (provider?: ProviderItem) => {
    setProviderForm(provider
      ? { name: provider.name, baseUrl: provider.baseUrl, apiKey: '' }
      : EMPTY_PROVIDER)
    setTemplate('custom')
    setDialog({ kind: 'provider', provider })
  }

  const openModelDialog = (providerId: number, model?: ProviderModelItem) => {
    setModelForm(model
      ? {
        modelName: model.modelName, visionSupported: model.visionSupported,
        streamSupported: model.streamSupported, extraOptions: model.extraOptions ?? '',
        isDebug: model.isDebug,
      }
      : EMPTY_MODEL)
    setDialog({ kind: 'model', providerId, model })
  }

  const openFetchDialog = (providerId: number) => {
    setFetchResult(null)
    setSelectedModels([])
    setManualModel('')
    setDialog({ kind: 'fetch', providerId })
    void (async () => {
      setBusy(true)
      try {
        setFetchResult(await fetchRemoteModels(providerId))
      } catch (e) {
        setFetchResult({ models: [], error: String(e) })
      } finally {
        setBusy(false)
      }
    })()
  }

  const submitDialog = (event: FormEvent) => {
    event.preventDefault()
    if (!dialog) return
    if (dialog.kind === 'provider') {
      run(() => saveProvider(providerForm, dialog.provider?.id),
        dialog.provider ? '厂商配置已更新' : '厂商配置已新增')
      closeDialog()
      return
    }
    if (dialog.kind === 'model') {
      const payload: ModelInput = {
        modelName: modelForm.modelName?.trim(),
        visionSupported: modelForm.visionSupported,
        streamSupported: modelForm.streamSupported,
        extraOptions: modelForm.extraOptions?.trim() || undefined,
        isDebug: !!modelForm.isDebug,
      }
      run(() => dialog.model
        ? updateProviderModel(dialog.providerId, dialog.model!.id, payload)
        : addProviderModels(dialog.providerId, payload),
      dialog.model ? '模型已更新' : '模型已添加')
      closeDialog()
      return
    }
    const names = [...selectedModels, ...(manualModel.trim() ? [manualModel.trim()] : [])]
    if (names.length === 0) {
      setError('请至少勾选一个模型，或在下方手动填写模型名')
      return
    }
    run(() => addProviderModels(dialog.providerId, { modelNames: names }),
      `已添加 ${names.length} 个模型`)
    closeDialog()
  }

  const toggleExpanded = (id: number) => {
    setExpanded((previous) => previous.includes(id)
      ? previous.filter((item) => item !== id) : [...previous, id])
  }

  return (
    <section className="card">
      <div className="panel-head">
        <div>
          <h2>模型配置</h2>
          <p className="muted">
            厂商只填名称 / Base URL / API Key；模型可从端点获取或手动添加。视觉等属性在模型上设置，
            对话页按「厂商 + 模型」为当前会话选择。「测试」会发一次极小的真实对话请求，
            「全部测试」覆盖该厂商下的全部模型；测试结果不落库、不计入用量。
          </p>
        </div>
        <button type="button" className="btn primary" onClick={() => openProviderDialog()}>＋ 新增配置</button>
      </div>

      {error && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="ok">{notice}</p>}
      {data.defaultModelId && <p className="muted">全局默认模型 id：{data.defaultModelId}（新会话自动继承）</p>}

      <ul className="provider-list">
        {data.providers.map((provider) => {
          const open = expanded.includes(provider.id)
          return (
            <li key={provider.id} className="provider-card">
              <div className="provider-head" onClick={() => toggleExpanded(provider.id)} role="button" tabIndex={0}
                onKeyDown={(e) => { if (e.key === 'Enter') toggleExpanded(provider.id) }}>
                <div className="provider-summary">
                  <strong>{provider.name} <span className="muted">· {provider.models.length} 个模型</span></strong>
                  <small>{provider.baseUrl}</small>
                  <small>Key：{provider.hasApiKey ? '已设置（不回显）' : '未设置'}</small>
                  {providerResults[provider.id] && (
                    <small className={providerResults[provider.id].failed === 0 ? 'conn-ok' : 'conn-fail'}>
                      连通性：{providerResults[provider.id].message}
                      {' · '}{formatLatency(providerResults[provider.id].elapsedMs)}
                    </small>
                  )}
                </div>
                <div className="row-actions" onClick={(e) => e.stopPropagation()}>
                  <button type="button" className="btn small" disabled={!!testing}
                    onClick={() => testWholeProvider(provider.id)}>
                    {testing?.providerId === provider.id && testing.modelId === undefined
                      ? '测试中…' : '全部测试'}
                  </button>
                  <button type="button" className="btn small" onClick={() => toggleExpanded(provider.id)}>
                    {open ? '收起' : '展开模型'}
                  </button>
                  <RowActionMenu
                    label={`厂商操作：${provider.name}`}
                    items={[
                      { key: 'edit', label: '编辑厂商', onSelect: () => openProviderDialog(provider) },
                      { key: 'delete', label: '删除厂商', danger: true,
                        onSelect: () => run(() => deleteProvider(provider.id), '厂商已删除') },
                    ]}
                  />
                </div>
              </div>

              {open && (
                <div className="provider-body">
                  {provider.models.length === 0 && <p className="muted">该厂商下还没有模型，先获取或手动添加。</p>}
                  <ul className="model-list">
                    {provider.models.map((model) => {
                      const result = modelResults[model.id]
                      const modelTesting = testing?.providerId === provider.id && testing.modelId === model.id
                      return (
                        <li key={model.id} className="model-row">
                          <div className="model-name">
                            <strong>{model.modelName}</strong>
                            {model.isDefault && <span className="tag">全局默认</span>}
                            <small>视觉：{model.visionSupported ? '支持' : '不支持'} · 流式：{model.streamSupported ? '支持' : '不支持'}{model.isDebug ? ' · 调试模型' : ''}</small>
                            {result && (
                              <small className={result.ok ? 'conn-ok' : 'conn-fail'}>
                                {result.ok
                                  ? `✅ 通过 · ${formatLatency(result.latencyMs)}`
                                  : `❌ ${result.message}`}
                              </small>
                            )}
                          </div>
                          <div className="row-actions">
                            <label className="inline-check">
                              <input type="checkbox" checked={model.visionSupported}
                                onChange={(e) => run(() => updateProviderModel(provider.id, model.id,
                                  { visionSupported: e.target.checked }), '视觉标记已更新')} />
                              图片
                            </label>
                            <button type="button" className="btn small" disabled={!!testing}
                              onClick={() => testOneModel(provider.id, model)}>
                              {modelTesting ? '测试中…' : '测试'}
                            </button>
                            {!model.isDefault && (
                              <button type="button" className="btn small"
                                onClick={() => run(() => setDefaultModel(provider.id, model.id), '已设为全局默认')}>
                                设为默认
                              </button>
                            )}
                            <RowActionMenu
                              label={`模型操作：${model.modelName}`}
                              items={[
                                { key: 'edit', label: '编辑模型', onSelect: () => openModelDialog(provider.id, model) },
                                { key: 'delete', label: '删除模型', danger: true,
                                  onSelect: () => run(() => deleteProviderModel(provider.id, model.id), '模型已删除') },
                              ]}
                            />
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                  <div className="row-actions">
                    <button type="button" className="btn small" onClick={() => openFetchDialog(provider.id)}>
                      从端点获取模型
                    </button>
                    <button type="button" className="btn small" onClick={() => openModelDialog(provider.id)}>
                      ＋ 手动添加模型
                    </button>
                  </div>
                </div>
              )}
            </li>
          )
        })}
        {data.providers.length === 0 && <li className="muted">暂无厂商配置，点右上角「新增配置」开始。</li>}
      </ul>

      {dialog && (
        <div className="modal-backdrop" onClick={closeDialog}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <form onSubmit={submitDialog}>
              <h3>{dialog.kind === 'provider'
                ? (dialog.provider ? '编辑厂商配置' : '新增厂商配置')
                : dialog.kind === 'model'
                  ? (dialog.model ? '编辑模型' : '手动添加模型')
                  : '从端点获取模型'}</h3>

              {dialog.kind === 'provider' && (
                <>
                  <label className="field">模板（只填地址，不内置 Key）
                    <select value={template} onChange={(e) => {
                      setTemplate(e.target.value)
                      const tpl = templates.find((t) => t.provider === e.target.value)
                      if (tpl?.baseUrl) {
                        setProviderForm((prev) => ({ ...prev, baseUrl: tpl.baseUrl }))
                      }
                    }}>
                      {templates.map((t) => <option key={t.provider} value={t.provider}>{t.label}</option>)}
                    </select>
                  </label>
                  <label className="field">名称
                    <input required value={providerForm.name}
                      onChange={(e) => setProviderForm({ ...providerForm, name: e.target.value })} />
                  </label>
                  <label className="field">Base URL
                    <input required placeholder="https://.../v1" value={providerForm.baseUrl}
                      onChange={(e) => setProviderForm({ ...providerForm, baseUrl: e.target.value })} />
                  </label>
                  <label className="field">API Key
                    <input type="password" autoComplete="off"
                      placeholder={dialog.provider ? '留空表示不修改' : '仅提交时传入，不回显'}
                      value={providerForm.apiKey}
                      onChange={(e) => setProviderForm({ ...providerForm, apiKey: e.target.value })} />
                  </label>
                </>
              )}

              {dialog.kind === 'model' && (
                <>
                  <label className="field">模型名
                    <input required value={modelForm.modelName ?? ''}
                      onChange={(e) => setModelForm({ ...modelForm, modelName: e.target.value })} />
                  </label>
                  <label className="inline-check"><input type="checkbox" checked={!!modelForm.visionSupported}
                    onChange={(e) => setModelForm({ ...modelForm, visionSupported: e.target.checked })} />
                    支持图片输入（图片题必需）</label>
                  <label className="inline-check"><input type="checkbox" checked={!!modelForm.streamSupported}
                    onChange={(e) => setModelForm({ ...modelForm, streamSupported: e.target.checked })} />
                    支持流式输出</label>
                  <label className="inline-check"><input type="checkbox" checked={!!modelForm.isDebug}
                    onChange={(e) => setModelForm({ ...modelForm, isDebug: e.target.checked })} />
                    调试模型（调用不计入用量看板）</label>
                  <label className="field">扩展参数 JSON（可选）
                    <textarea rows={3} value={modelForm.extraOptions ?? ''}
                      onChange={(e) => setModelForm({ ...modelForm, extraOptions: e.target.value })} />
                  </label>
                </>
              )}

              {dialog.kind === 'fetch' && (
                <>
                  {busy && <p className="muted">正在向端点请求模型列表…</p>}
                  {fetchResult?.error && <p className="error">{fetchResult.error}</p>}
                  {fetchResult && fetchResult.models.length > 0 && (
                    <div className="model-candidates">
                      {fetchResult.models.map((name) => (
                        <label key={name} className="inline-check">
                          <input type="checkbox" checked={selectedModels.includes(name)}
                            onChange={(e) => setSelectedModels((prev) => e.target.checked
                              ? [...prev, name] : prev.filter((item) => item !== name))} />
                          {name}
                        </label>
                      ))}
                    </div>
                  )}
                  {fetchResult && fetchResult.models.length === 0 && !fetchResult.error
                    && <p className="muted">端点没有返回可用模型，可在下方手动填写。</p>}
                  <label className="field">手动补一个模型名（可选）
                    <input value={manualModel} placeholder="例如 gpt-4o-mini"
                      onChange={(e) => setManualModel(e.target.value)} />
                  </label>
                  <p className="muted">Key 只在后端代理请求时使用，不会出现在页面或响应里。</p>
                </>
              )}

              <div className="modal-actions">
                <button type="submit" className="btn primary" disabled={busy}>
                  {dialog.kind === 'fetch' ? '添加所选模型' : '保存'}
                </button>
                <button type="button" className="btn" onClick={closeDialog}>取消</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  )
}
