import { useEffect, useRef } from 'react';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import 'monaco-editor/esm/vs/basic-languages/shell/shell.contribution';
import 'monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution';
import 'monaco-editor/esm/vs/basic-languages/xml/xml.contribution';

type EditorLanguage = 'shell' | 'yaml' | 'xml' | 'text';
type TemplateVariablePhase = 'build' | 'deploy' | 'shared';

type Props = {
  value: string;
  onChange: (value: string) => void;
  rows?: number;
  language?: EditorLanguage;
  placeholder?: string;
  readOnly?: boolean;
  templateVariablePhases?: Record<string, TemplateVariablePhase>;
};

const languageIdMap: Record<EditorLanguage, string> = {
  shell: 'shell',
  yaml: 'yaml',
  xml: 'xml',
  text: 'plaintext',
};

let monacoConfigured = false;

self.MonacoEnvironment = {
  getWorker() {
    return new EditorWorker();
  },
};

function configureMonaco() {
  if (monacoConfigured) {
    return;
  }
  monacoConfigured = true;
}

function resolvePlaceholder(language: EditorLanguage, placeholder?: string) {
  if (placeholder) {
    return placeholder;
  }
  if (language === 'shell') {
    return '请输入 Shell 脚本';
  }
  if (language === 'xml') {
    return '请输入 XML 配置';
  }
  if (language === 'yaml') {
    return '请输入 YAML 配置';
  }
  if (language === 'text') {
    return '请输入内容';
  }
  return '请输入代码';
}

function createTemplateVariableDecorations(
  model: monaco.editor.ITextModel,
  templateVariablePhases: Record<string, TemplateVariablePhase>,
) {
  const decorations: monaco.editor.IModelDeltaDecoration[] = [];
  const pattern = /\{\{\s*([A-Za-z0-9_]+)\s*}}/g;
  for (let lineNumber = 1; lineNumber <= model.getLineCount(); lineNumber += 1) {
    const lineContent = model.getLineContent(lineNumber);
    for (const match of lineContent.matchAll(pattern)) {
      const phase = templateVariablePhases[match[1]] || 'shared';
      decorations.push({
        range: new monaco.Range(
          lineNumber,
          (match.index || 0) + 1,
          lineNumber,
          (match.index || 0) + match[0].length + 1,
        ),
        options: { inlineClassName: `code-editor-template-variable code-editor-template-variable--${phase}` },
      });
    }
  }
  return decorations;
}

/**
 * 基于 Monaco 的通用代码编辑器。
 * <p>
 * 语言高亮使用 Monaco 自带 shell / yaml / json / xml 规则，模板变量 {@code {{xxx}}}
 * 额外通过 decoration 做醒目标记，避免为了模板变量重写整套语言语法。
 */
export default function CodeEditor({
  value,
  onChange,
  rows = 8,
  language = 'text',
  placeholder,
  readOnly = false,
  templateVariablePhases = {},
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const decorationsRef = useRef<monaco.editor.IEditorDecorationsCollection | null>(null);
  const onChangeRef = useRef(onChange);
  const templateVariablePhasesRef = useRef(templateVariablePhases);
  const valueRef = useRef(value);
  const editorHeight = rows * 21 + 24;

  const refreshTemplateVariables = (editor: monaco.editor.IStandaloneCodeEditor) => {
    const model = editor.getModel();
    if (!model || !decorationsRef.current) {
      return;
    }
    decorationsRef.current.set(createTemplateVariableDecorations(model, templateVariablePhasesRef.current));
  };

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    templateVariablePhasesRef.current = templateVariablePhases;
    if (editorRef.current) {
      refreshTemplateVariables(editorRef.current);
    }
  }, [templateVariablePhases]);

  useEffect(() => {
    configureMonaco();
    if (!containerRef.current || editorRef.current) {
      return;
    }

    const editor = monaco.editor.create(containerRef.current, {
      value,
      language: languageIdMap[language],
      theme: 'vs',
      readOnly,
      automaticLayout: true,
      minimap: { enabled: false },
      fontFamily: '"SFMono-Regular", "Consolas", "Liberation Mono", monospace',
      fontSize: 13,
      lineHeight: 21,
      lineNumbers: 'on',
      lineNumbersMinChars: 3,
      overviewRulerLanes: 0,
      padding: { top: 12, bottom: 12 },
      renderLineHighlight: 'line',
      scrollBeyondLastLine: false,
      scrollbar: { alwaysConsumeMouseWheel: false },
      tabSize: 2,
      wordWrap: 'on',
    });
    editorRef.current = editor;
    decorationsRef.current = editor.createDecorationsCollection();
    refreshTemplateVariables(editor);

    const subscription = editor.onDidChangeModelContent(() => {
      const nextValue = editor.getValue();
      valueRef.current = nextValue;
      refreshTemplateVariables(editor);
      onChangeRef.current(nextValue);
    });

    return () => {
      subscription.dispose();
      decorationsRef.current?.clear();
      editor.dispose();
      decorationsRef.current = null;
      editorRef.current = null;
    };
  }, []);

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor || value === valueRef.current) {
      return;
    }
    valueRef.current = value;
    editor.setValue(value);
    refreshTemplateVariables(editor);
  }, [value]);

  useEffect(() => {
    const model = editorRef.current?.getModel();
    if (model) {
      monaco.editor.setModelLanguage(model, languageIdMap[language]);
    }
  }, [language]);

  useEffect(() => {
    editorRef.current?.updateOptions({ readOnly });
  }, [readOnly]);

  return (
    <div className={`code-editor ${readOnly ? 'code-editor--readonly' : ''}`} style={{ height: editorHeight }}>
      {!value ? <div className="code-editor-placeholder">{resolvePlaceholder(language, placeholder)}</div> : null}
      <div ref={containerRef} className="code-editor-monaco" />
    </div>
  );
}
