/**
 * 纯 CSS 竖柱图（CR-023）。刻意不引入图表库（DD-11 / AC-305-4）：
 * 结构只有 Y 轴刻度 + 柱列 + X 轴标签，高度用百分比，颜色走主题变量。
 */
export interface BarDatum {
  /** 稳定 key（日期或名称） */
  key: string
  /** 横轴标签，如 11/11 */
  label: string
  value: number
}

interface Props {
  data: BarDatum[]
  /** 数值单位，用于柱体 title 与无障碍文案，如「次」「token」 */
  unit?: string
  emptyText?: string
  tone?: 'accent' | 'ok' | 'warn'
}

/** 取整到好看的坐标上限：最多 4 档，避免出现 37 / 27.75 / 18.5 这种刻度。 */
function niceMax(value: number): number {
  if (value <= 4) return 4
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)))
  for (const multiplier of [1, 2, 2.5, 5, 10]) {
    const step = multiplier * magnitude
    if (value / step <= 4) return Math.ceil(value / step) * step
  }
  return Math.ceil(value / (10 * magnitude)) * 10 * magnitude
}

/** 刻度文案：万 / k 缩写，避免 6 位数把 Y 轴撑宽。 */
function formatTick(value: number): string {
  if (value >= 10000) {
    const scaled = value / 10000
    return `${Number.isInteger(scaled) ? scaled : scaled.toFixed(1)}万`
  }
  if (value >= 1000) {
    const scaled = value / 1000
    return `${Number.isInteger(scaled) ? scaled : scaled.toFixed(1)}k`
  }
  return String(Math.round(value))
}

export default function BarChart({ data, unit = '', emptyText = '该时间范围内没有数据', tone = 'accent' }: Props) {
  if (data.length === 0) {
    return <p className="muted bar-chart-empty">{emptyText}</p>
  }

  const top = niceMax(Math.max(...data.map((item) => item.value)))
  const ticks = [top, top * 0.75, top * 0.5, top * 0.25, 0]
  /** 柱太密时按步长隔几个显示一个横轴标签，避免日期挤成一团。 */
  const stride = data.length > 14 ? Math.ceil(data.length / 10) : 1

  return (
    <div className="bar-chart">
      <div className="bar-chart-axis" aria-hidden="true">
        {ticks.map((tick) => <span key={tick}>{formatTick(tick)}</span>)}
      </div>
      <div className="bar-chart-area">
        <div className="bar-chart-grid" aria-hidden="true">
          {ticks.map((tick) => <span key={tick} />)}
        </div>
        <div className="bar-chart-bars">
          {data.map((item, index) => (
            <div
              key={item.key}
              className={`bar-chart-col tone-${tone}`}
              title={`${item.key}：${item.value}${unit}`}
            >
              <span className="bar-chart-bar" style={{ height: `${barHeight(item.value, top)}px` }} />
              <span className="bar-chart-x">
                {index % stride === 0 ? item.label : ''}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

/** 绘图区高度（px），必须与 global.css 里 `.bar-chart-bars { height }` 保持一致。 */
const PLOT_HEIGHT = 180

/** 用像素而非百分比：flex 列里百分比高度会被标签行挤掉，导致柱体基线错位。 */
function barHeight(value: number, top: number): number {
  if (value <= 0 || top <= 0) return 0
  const scaled = (value / top) * PLOT_HEIGHT
  // 极小的非零值（例如 1 次）至少留 2px，否则看起来像没有数据
  return Math.max(2, Math.round(scaled))
}
