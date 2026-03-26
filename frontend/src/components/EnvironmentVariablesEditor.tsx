import { Button, Checkbox, Input, Space } from 'antd';

/**
 * 将后端可能返回的对象结构统一转成数组，方便在表单里逐行编辑。
 */
function normalizeItems(value) {
  if (!value) {
    return [];
  }
  if (Array.isArray(value)) {
    return value;
  }
  return Object.entries(value).map(([key, itemValue]) => ({
    key,
    value: itemValue,
    prependPath: false,
  }));
}

/**
 * 运行环境附加变量编辑器。
 * 主要用于维护 PATH 补充项、镜像源和构建工具额外变量。
 */
export default function EnvironmentVariablesEditor({ value = [], onChange }) {
  const items = normalizeItems(value);

  /**
   * 对外只抛出完整列表，保持父组件状态更新逻辑简单。
   */
  const updateItems = (nextItems) => {
    onChange?.(nextItems);
  };

  /** 追加一条新的环境变量记录。 */
  const addItem = () => {
    updateItems([
      ...items,
      { key: '', value: '', prependPath: false },
    ]);
  };

  /** 删除指定索引的环境变量记录。 */
  const removeItem = (index) => {
    updateItems(items.filter((_, itemIndex) => itemIndex !== index));
  };

  /** 按补丁方式更新单条环境变量，避免在组件内部写重复赋值逻辑。 */
  const updateItem = (index, patch) => {
    updateItems(items.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)));
  };

  return (
    <div className="variable-editor space-y-3">
      {items.length === 0 ? <div className="variable-empty">当前没有附加环境变量。</div> : null}
      {items.map((item, index) => (
        <div key={`${index}-${item.key}`} className="variable-row">
          <div className="grid gap-3 md:grid-cols-[1.2fr_1.6fr_auto]">
            <Input
              placeholder="变量名，例如 NPM_REGISTRY"
              value={item.key}
              onChange={(event) => updateItem(index, { key: event.target.value })}
            />
            <Input
              placeholder="变量值，例如 https://registry.npmmirror.com"
              value={item.value}
              onChange={(event) => updateItem(index, { value: event.target.value })}
            />
            <Button danger onClick={() => removeItem(index)}>删除</Button>
          </div>
          <div className="mt-3">
            <Checkbox
              checked={Boolean(item.prependPath)}
              onChange={(event) => updateItem(index, { prependPath: event.target.checked })}
            >
              追加到 PATH 前面
            </Checkbox>
          </div>
        </div>
      ))}
      <Space>
        <Button onClick={addItem}>新增环境变量</Button>
      </Space>
    </div>
  );
}
