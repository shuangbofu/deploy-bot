import { Form, Input, Typography } from 'antd';

/**
 * 流水线默认变量编辑器。
 * 变量按构建、发布、共用三个阶段分组展示，便于看清变量属于哪一段流程。
 */
export default function PipelineVariablesEditor({ variables, values, onChange }) {
  const variableList = variables?.length ? variables : [];
  const currentValues = values || {};

  const phaseGroups = [
    { key: 'build', title: '构建变量', description: '这些变量用于本机构建阶段。' },
    { key: 'deploy', title: '发布变量', description: '这些变量用于目标主机发布阶段。' },
    { key: 'shared', title: '共用变量', description: '这些变量会同时在构建和发布阶段使用。' },
  ].map((group) => ({
    ...group,
    items: variableList.filter((item) => (item.phase || 'shared') === group.key),
  })).filter((group) => group.items.length > 0);

  /** 更新某个变量的默认值。 */
  const updateValue = (name, value) => {
    onChange({
      ...currentValues,
      [name]: value,
    });
  };

  if (variableList.length === 0) {
    return <div className="variable-empty">当前模板没有暴露变量。</div>;
  }

  return (
    <div className="variable-editor">
      <Typography.Text className="mb-3 block text-slate-500">
        流水线为模板变量提供默认值，用户部署时可以直接复用。
      </Typography.Text>
      <div className="space-y-5">
        {phaseGroups.map((group) => (
          <div key={group.key}>
            <Typography.Text strong>{group.title}</Typography.Text>
            <Typography.Text className="mb-3 mt-1 block text-slate-500">{group.description}</Typography.Text>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {group.items.map((item) => (
                <Form.Item
                  key={item.name}
                  label={item.label || item.name}
                  required={Boolean(item.required)}
                >
                  <Input
                    placeholder={item.placeholder || `请输入 ${item.label || item.name}`}
                    value={currentValues[item.name] || ''}
                    onChange={(event) => updateValue(item.name, event.target.value)}
                  />
                </Form.Item>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
