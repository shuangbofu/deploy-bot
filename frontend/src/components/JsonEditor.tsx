import { useMemo, useRef } from 'react';

type EditorLanguage = 'json' | 'shell';

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function highlightJson(value: string) {
  return escapeHtml(value)
    .replace(/("(?:\\.|[^"\\])*")(\s*:)?/g, (_, stringToken: string, colon: string) => {
      if (colon) {
        return `<span class="code-token-string">${stringToken}</span>${colon}`;
      }
      return `<span class="code-token-string">${stringToken}</span>`;
    })
    .replace(/\b(true|false|null)\b/g, '<span class="code-token-keyword">$1</span>')
    .replace(/\b-?\d+(?:\.\d+)?\b/g, '<span class="code-token-number">$&</span>');
}

function highlightShell(value: string) {
  let html = escapeHtml(value);
  html = html.replace(/(#.*)$/gm, '<span class="code-token-comment">$1</span>');
  html = html.replace(/(\{\{[^}]+\}\})/g, '<span class="code-token-template">$1</span>');
  html = html.replace(/("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')/g, '<span class="code-token-string">$1</span>');
  html = html.replace(/(\$\{[^}]+\}|\$[A-Za-z_][A-Za-z0-9_]*)/g, '<span class="code-token-variable">$1</span>');
  html = html.replace(/\b(set|if|then|elif|else|fi|for|do|done|case|esac|while|function|export|local|echo|cd|mkdir|rm|cp|mv|rsync|git|npm|mvn|java|nohup)\b/g, '<span class="code-token-keyword">$1</span>');
  return html;
}

function highlightCode(value: string, language: EditorLanguage) {
  if (!value) {
    return '&nbsp;';
  }
  if (language === 'json') {
    return highlightJson(value);
  }
  return highlightShell(value);
}

type Props = {
  value: string;
  onChange: (value: string) => void;
  rows?: number;
  language?: EditorLanguage;
  placeholder?: string;
};

/**
 * 轻量级代码编辑器，提供基础语法高亮，避免模板脚本编辑时完全是纯文本。
 */
export default function JsonEditor({
  value,
  onChange,
  rows = 8,
  language = 'json',
  placeholder,
}: Props) {
  const highlighted = useMemo(() => highlightCode(value, language), [language, value]);
  const previewRef = useRef<HTMLPreElement | null>(null);
  const editorHeight = `calc(${rows} * 1.6em + 28px)`;

  return (
    <div className="code-editor" style={{ height: editorHeight }}>
      <pre
        aria-hidden="true"
        className="code-editor-preview"
        ref={previewRef}
        dangerouslySetInnerHTML={{ __html: highlighted }}
      />
      <textarea
        rows={rows}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onScroll={(event) => {
          if (previewRef.current) {
            previewRef.current.scrollTop = event.currentTarget.scrollTop;
            previewRef.current.scrollLeft = event.currentTarget.scrollLeft;
          }
        }}
        placeholder={placeholder || (language === 'shell' ? '请输入 Shell 脚本' : '请输入 JSON')}
        spellCheck={false}
        className="code-editor-input"
      />
    </div>
  );
}
