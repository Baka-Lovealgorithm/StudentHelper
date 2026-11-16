import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/**
 * D-12：视图级错误边界。任何面板在渲染期抛异常时，React 会卸载整棵树导致整页白屏；
 * 这里把它拦在内容区内，给出可读提示与重试，侧边栏与其它页面不受影响。
 * 只兜渲染期异常，接口返回的 `E*` 业务错误仍走各面板自己的 error 横幅。
 */
export default class ErrorBoundary extends Component<Props, State> {

  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary] 视图渲染失败：', error, info.componentStack)
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children
    return (
      <section className="card">
        <h2>该视图暂时无法显示</h2>
        <p className="muted">渲染这个页面时出错了，其它功能不受影响。可以先重试，或切换到别的功能页。</p>
        <p className="error">{error.message || '未知渲染错误'}</p>
        <button type="button" className="btn" onClick={() => this.setState({ error: null })}>重试</button>
      </section>
    )
  }
}
