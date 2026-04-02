import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, message } from 'antd';
import { usersApi } from '../../api/users';
import type { UserPayload, UserSummary } from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';

const emptyUser: UserPayload = {
  username: '',
  displayName: '',
  password: '',
  role: 'USER',
  enabled: true,
};

/**
 * 用户管理页。
 * 管理员可以在这里维护登录账号、角色和启用状态。
 */
export default function UserAdminPage() {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number>();
  const [form, setForm] = useState<UserPayload>(emptyUser);
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState<'ADMIN' | 'USER'>();
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>(undefined);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const loadUsers = async () => {
    setLoading(true);
    try {
      setUsers(await usersApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers().catch(() => message.error('加载用户失败'));
  }, []);

  const openCreate = () => {
    setEditingId(undefined);
    setForm(emptyUser);
    setModalOpen(true);
  };

  const openEdit = (record: UserSummary) => {
    setEditingId(record.id);
    setForm({
      username: record.username,
      displayName: record.displayName,
      password: '',
      role: record.role,
      enabled: record.enabled,
    });
    setModalOpen(true);
  };

  const saveUser = async () => {
    if (editingId) {
      await usersApi.update(editingId, form);
    } else {
      await usersApi.create(form);
    }
    setModalOpen(false);
    setEditingId(undefined);
    setForm(emptyUser);
    await loadUsers();
    message.success(editingId ? '用户已更新' : '用户已创建');
  };

  const removeUser = async (id: number) => {
    await usersApi.remove(id);
    await loadUsers();
    message.success('用户已删除');
  };

  const filteredUsers = useMemo(() => users.filter((item) => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [item.username, item.displayName]
        .some((value) => String(value || '').toLowerCase().includes(normalizedKeyword));
      if (!matched) {
        return false;
      }
    }
    if (roleFilter && item.role !== roleFilter) {
      return false;
    }
    if (enabledFilter !== undefined && item.enabled !== enabledFilter) {
      return false;
    }
    return true;
  }), [users, keyword, roleFilter, enabledFilter]);

  return (
    <>
      <PageHeaderBar
        title="用户管理"
        description="管理登录账号、显示名称、角色和启用状态。"
        extra={(
          <Space>
            <Button onClick={() => loadUsers().catch(() => message.error('加载用户失败'))}>刷新</Button>
            <Button type="primary" onClick={openCreate}>新建用户</Button>
          </Space>
        )}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-4">
          <Input
            value={keyword}
            placeholder="搜索用户名 / 显示名称"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={roleFilter}
            placeholder="筛选角色"
            options={[
              { label: '管理员', value: 'ADMIN' },
              { label: '普通用户', value: 'USER' },
            ]}
            onChange={(value) => {
              setRoleFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={enabledFilter}
            placeholder="筛选状态"
            options={[
              { label: '启用', value: true },
              { label: '停用', value: false },
            ]}
            onChange={(value) => {
              setEnabledFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <div className="flex items-center">
            <Button
              onClick={() => {
                setKeyword('');
                setRoleFilter(undefined);
                setEnabledFilter(undefined);
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            >
              重置条件
            </Button>
          </div>
        </div>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={filteredUsers}
          locale={{ emptyText: <EmptyPane description="还没有额外用户，先创建一个账号。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredUsers.length,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
            { title: '用户名', dataIndex: 'username' },
            { title: '显示名称', dataIndex: 'displayName' },
            { title: '角色', render: (_, record) => (record.role === 'ADMIN' ? '管理员' : '普通用户') },
            { title: '状态', render: (_, record) => (record.enabled ? '启用' : '停用') },
            {
              title: '操作',
              render: (_, record) => (
                <Space>
                  <Button type="link" onClick={() => openEdit(record)}>编辑</Button>
                  <Popconfirm title="确认删除这个用户吗？" onConfirm={() => removeUser(record.id)}>
                    <Button type="link" danger>删除</Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Card>
      </div>

      <Modal
        open={modalOpen}
        title={editingId ? '编辑用户' : '新建用户'}
        onCancel={() => setModalOpen(false)}
        onOk={() => saveUser().catch(() => undefined)}
        destroyOnClose
      >
        <Form layout="vertical">
          <Form.Item label="用户名" required>
            <Input value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} />
          </Form.Item>
          <Form.Item label="显示名称" required>
            <Input value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} />
          </Form.Item>
          <Form.Item label={editingId ? '重置密码' : '密码'} required={!editingId}>
            <Input.Password
              placeholder={editingId ? '留空表示不修改密码' : '请输入登录密码'}
              value={form.password}
              onChange={(event) => setForm({ ...form, password: event.target.value })}
            />
          </Form.Item>
          <Form.Item label="角色" required>
            <Select
              value={form.role}
              options={[
                { label: '管理员', value: 'ADMIN' },
                { label: '普通用户', value: 'USER' },
              ]}
              onChange={(value) => setForm({ ...form, role: value })}
            />
          </Form.Item>
          <Form.Item label="启用状态">
            <Switch checked={form.enabled} onChange={(checked) => setForm({ ...form, enabled: checked })} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
