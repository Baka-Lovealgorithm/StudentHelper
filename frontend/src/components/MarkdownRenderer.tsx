import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'
import { prepareMarkdown } from '../utils/mathText'
import { rehypeKatexSizingCompat } from '../utils/katexClassCompat'

/**
 * Markdown / LaTeX 渲染器。整组依赖（react-markdown / remark-math / rehype-katex / katex）
 * 只在本组件被首次渲染时加载，避免首屏 bundle 被 KaTeX 撑大（TCR-010）。
 * skipHtml：模型返回的任何标签只作为纯文本，不会被注入 DOM，代码永不被执行（AC-107-3）。
 * prepareMarkdown：统一公式定界符，并把 KaTeX 无法正确绘制的纯中文文本盒降级为普通文本。
 * remark-gfm（CR-029）：启用 GFM 管道表格 / 删除线 / 任务列表；表格按「极简仅横线」样式渲染，
 * 单元格内的 $...$ 仍交给 remark-math + rehype-katex，skipHtml 与 lazy chunk 口径不变。
 * CR-047：图改由后端渲染成 PNG（`diagram_render` 工具），答案里只有 `![](/api/images/…)`，
 * 本组件按普通 Markdown 图片渲染，不再加载 mermaid。
 * CR-055 / D-19：rehypeKatex 输出的是 katex 0.16 的旧类名 `sizing`，这里在 hast 层
 * 升级为 0.19 CSS 认识的 `katex-sizing`，修好行内分式（分母带指数时）文字重合。
 * CR-062：传入 `onZoomImage` 时图片包成可点击按钮，点击交给调用方打开页内放大层
 * （与题面图一致）；未传时仍输出裸 `<img>`，其它使用方不受影响。
 */
export default function MarkdownRenderer({
  text, onZoomImage,
}: { text: string; onZoomImage?: (url: string) => void }) {
  return (
    <div className="markdown-body">
      <ReactMarkdown remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex, rehypeKatexSizingCompat]} skipHtml
        components={onZoomImage ? {
          img: ({ src, alt, title }) => (
            <button
              type="button"
              className="markdown-image"
              aria-label={alt ? `放大查看图片：${alt}` : '放大查看图片'}
              onClick={() => { if (typeof src === 'string') onZoomImage(src) }}
            >
              <img src={typeof src === 'string' ? src : undefined} alt={alt ?? ''}
                title={title} loading="lazy" />
            </button>
          ),
        } : undefined}>
        {prepareMarkdown(text)}
      </ReactMarkdown>
    </div>
  )
}
