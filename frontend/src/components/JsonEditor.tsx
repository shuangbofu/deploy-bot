import { Input } from 'antd';

/**
 * 简单 JSON 文本编辑器。
 * 目前仍保留给少量结构化文本场景使用。
 */
export default function JsonEditor({ value, onChange, rows = 8 }) {
  return (
    <Input.TextArea
      rows={rows}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder="请输入 JSON"
      spellCheck={false}
    />
  );
}
