import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Form, Input, Space, Switch, Tag, Typography } from 'antd';

/**
 * 新增变量时的默认结构。
 */
const emptyVariable = {
  name: '',
  label: '',
  placeholder: '',
  required: false,
};

/**
 * 阶段文案映射，用于把 build / deploy / shared 渲染成中文。
 */
const phaseLabelMap = {
  build: '构建',
  deploy: '发布',
  shared: '共用',
};

/**
 * 模板变量编辑器。
 * 用于定义模板暴露给流水线和用户端的变量元信息。
 */
export default function TemplateVariablesEditor({ value, onChange, title, description, phase }) {
  const variables = value?.length ? value : [];

  /** 更新指定索引的变量定义。 */
  const updateVariable = (index, patch) => {
    const next = variables.map((item, currentIndex) => (currentIndex === index ? { ...item, ...patch } : item));
    onChange(next);
  };

  /** 追加一条新的模板变量。 */
  const addVariable = () => {
    onChange([...variables, { ...emptyVariable }]);
  };

  /** 删除指定索引的模板变量。 */
  const removeVariable = (index) => {
    onChange(variables.filter((_, currentIndex) => currentIndex !== index));
  };

  return (
    <div className="variable-editor">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          {title ? <Typography.Text strong>{title}</Typography.Text> : null}
          <Typography.Text className="block text-slate-500">
            {description || '配置模板对外暴露的变量，流水线和用户端将基于这些变量进行填写或覆盖。'}
          </Typography.Text>
        </div>
        <Button icon={<PlusOutlined />} onClick={addVariable}>
          添加变量
        </Button>
      </div>
      {variables.length === 0 ? <div className="variable-empty">当前没有暴露变量。</div> : null}
      <Space direction="vertical" className="w-full" size="middle">
        {variables.map((item, index) => (
          <div key={`${item.name}-${index}`} className="variable-row">
            <div className="variable-row-header">
              <Space>
                <Typography.Text strong>变量 {index + 1}</Typography.Text>
                <Tag>{phaseLabelMap[item.phase || phase || 'shared'] || '共用'}</Tag>
              </Space>
              <Button danger type="text" icon={<DeleteOutlined />} onClick={() => removeVariable(index)}>
                删除
              </Button>
            </div>
            <Form layout="vertical">
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <Form.Item label="变量名">
                  <Input
                    placeholder="如 targetDir"
                    value={item.name}
                    onChange={(event) => updateVariable(index, { name: event.target.value })}
                  />
                </Form.Item>
                <Form.Item label="显示名称">
                  <Input
                    placeholder="如 目标目录"
                    value={item.label}
                    onChange={(event) => updateVariable(index, { label: event.target.value })}
                  />
                </Form.Item>
              </div>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_auto] md:items-end">
                <Form.Item label="占位提示">
                  <Input
                    placeholder="如 /data/www/app"
                    value={item.placeholder}
                    onChange={(event) => updateVariable(index, { placeholder: event.target.value })}
                  />
                </Form.Item>
                <Form.Item label="必填" className="min-w-[120px]">
                  <Switch
                    checked={Boolean(item.required)}
                    checkedChildren="是"
                    unCheckedChildren="否"
                    onChange={(checked) => updateVariable(index, { required: checked })}
                  />
                </Form.Item>
              </div>
            </Form>
          </div>
        ))}
      </Space>
    </div>
  );
}
