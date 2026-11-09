import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'
import { prepareMarkdown } from '../utils/mathText'

/**
 * Markdown / LaTeX 渲染器。整组依赖（react-markdown / remark-math / rehype-katex / katex）
 * 只在本组件被首次渲染时加载，避免首屏 bundle 被 KaTeX 撑大（TCR-010）。
 * skipHtml：模型返回的任何标签只作为纯文本，不会被注入 DOM，代码永不被执行（AC-107-3）。
 * prepareMarkdown：统一公式定界符，并把 KaTeX 无法正确绘制的纯中文文本盒降级为普通文本。
 */
export default function MarkdownRenderer({ text }: { text: string }) {
  return (
    <div className="markdown-body">
      <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]} skipHtml>
        {prepareMarkdown(text)}
      </ReactMarkdown>
    </div>
  )
}
