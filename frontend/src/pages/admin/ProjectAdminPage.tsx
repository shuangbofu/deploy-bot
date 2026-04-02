import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Radio, Select, Space, Table, message } from 'antd';
import { projectsApi } from '../../api/projects';
import type { ProjectPayload, ProjectSummary } from '../../api/types';
import BooleanBadge from '../../components/BooleanBadge';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';

const emptyProject: ProjectPayload = {
  name: '',
  description: '',
  gitUrl: '',
  gitAuthType: 'NONE',
  gitUsername: '',
  gitPassword: '',
};

export default function ProjectAdminPage() {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [testingConnection, setTestingConnection] = useState(false);
  const [form, setForm] = useState<ProjectPayload>(emptyProject);
  const [editingId, setEditingId] = useState<number>();
  const [modalOpen, setModalOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [gitAuthTypeFilter, setGitAuthTypeFilter] = useState<string>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const loadProjects = async () => {
    setLoading(true);
    try {
      setProjects(await projectsApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProjects().catch(() => message.error('加载项目失败'));
  }, []);

  const openCreate = () => {
    setEditingId(undefined);
    setForm(emptyProject);
    setModalOpen(true);
  };

  const openEdit = (record: ProjectSummary) => {
    setEditingId(record.id);
    setForm({
      name: record.name || '',
      description: record.description || '',
      gitUrl: record.gitUrl || '',
      gitAuthType: record.gitAuthType || 'NONE',
      gitUsername: record.gitUsername || '',
      gitPassword: record.gitPassword || '',
    });
    setModalOpen(true);
  };

  const isHttpGitUrl = (gitUrl: string) => gitUrl.startsWith('http://') || gitUrl.startsWith('https://');
  const isSshGitUrl = (gitUrl: string) => gitUrl.startsWith('git@') || gitUrl.startsWith('ssh://');

  const convertHttpToSshUrl = (gitUrl: string) => {
    try {
      if (!isHttpGitUrl(gitUrl)) {
        return gitUrl;
      }
      const parsed = new URL(gitUrl);
      const path = parsed.pathname.replace(/^\/+/, '');
      if (!path) {
        return gitUrl;
      }
      return `git@${parsed.host}:${path}`;
    } catch {
      return gitUrl;
    }
  };

  const updateGitAuthType = (nextType: string) => {
    let nextGitUrl = form.gitUrl;

    if (nextType === 'SSH' && isHttpGitUrl(form.gitUrl)) {
      const converted = convertHttpToSshUrl(form.gitUrl);
      if (converted !== form.gitUrl) {
        nextGitUrl = converted;
        message.info('已自动把仓库地址切换为 SSH 形式，请确认地址是否正确。');
      }
    }

    setForm({ ...form, gitAuthType: nextType, gitUrl: nextGitUrl });
  };

  const saveProject = async () => {
    if (form.gitAuthType === 'BASIC' && !isHttpGitUrl(form.gitUrl)) {
      message.error('账号密码模式只能保存 HTTP/HTTPS 仓库地址。');
      return;
    }
    if (form.gitAuthType === 'SSH' && !isSshGitUrl(form.gitUrl)) {
      message.error('密钥对模式只能保存 SSH 仓库地址，请使用 git@... 或 ssh://... 地址。');
      return;
    }

    if (editingId) {
      await projectsApi.update(editingId, form);
    } else {
      await projectsApi.create(form);
    }
    setForm(emptyProject);
    setEditingId(undefined);
    setModalOpen(false);
    await loadProjects();
    message.success(editingId ? '项目已更新' : '项目已创建');
  };

  const testConnectionWithPayload = async (payload: ProjectPayload) => {
    if (!payload.gitUrl.trim()) {
      message.error('请先填写 Git 地址。');
      return;
    }

    if (payload.gitAuthType === 'BASIC' && !isHttpGitUrl(payload.gitUrl)) {
      message.error('账号密码模式只能测试 HTTP/HTTPS 仓库地址。');
      return;
    }
    if (payload.gitAuthType === 'SSH' && !isSshGitUrl(payload.gitUrl)) {
      message.error('密钥对模式只能测试 SSH 仓库地址。');
      return;
    }

    setTestingConnection(true);
    try {
      const result = await projectsApi.testConnection(payload);
      Modal.success({
        title: '仓库连通性测试成功',
        width: 720,
        content: (
          <div className="space-y-3">
            <div>认证方式：{result.gitAuthType}</div>
            <div>仓库地址：{result.gitUrl}</div>
            <div>{result.message}</div>
            {result.output ? (
              <pre className="max-h-72 overflow-auto rounded-xl bg-slate-950 p-4 text-xs leading-6 text-slate-100">
                {result.output}
              </pre>
            ) : null}
          </div>
        ),
      });
    } finally {
      setTestingConnection(false);
    }
  };

  const testConnection = async () => {
    await testConnectionWithPayload(form);
  };

  const removeProject = async (id: number) => {
    await projectsApi.remove(id);
    await loadProjects();
    message.success('项目已删除');
  };

  const filteredProjects = useMemo(() => projects.filter((item) => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [item.name, item.description, item.gitUrl]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
      if (!matched) {
        return false;
      }
    }
    if (gitAuthTypeFilter && item.gitAuthType !== gitAuthTypeFilter) {
      return false;
    }
    return true;
  }), [projects, keyword, gitAuthTypeFilter]);

  return (
    <>
      <PageHeaderBar
        title="项目管理"
        description="管理项目名称、仓库地址和 Git 认证配置。"
        extra={(
          <Space>
            <Button onClick={() => loadProjects().catch(() => message.error('加载项目失败'))}>刷新</Button>
            <Button type="primary" onClick={openCreate}>新建项目</Button>
          </Space>
        )}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-3">
          <Input
            value={keyword}
            placeholder="搜索项目名称 / 描述 / Git 地址"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={gitAuthTypeFilter}
            placeholder="筛选 Git 认证方式"
            options={[
              { label: '不认证', value: 'NONE' },
              { label: '账号密码', value: 'BASIC' },
              { label: '密钥对模式', value: 'SSH' },
            ]}
            onChange={(value) => {
              setGitAuthTypeFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <div className="flex items-center">
            <Button
              onClick={() => {
                setKeyword('');
                setGitAuthTypeFilter(undefined);
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
          scroll={{ x: 820 }}
          dataSource={filteredProjects}
          locale={{ emptyText: <EmptyPane description="还没有项目，点击右上角先创建一个真实项目。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredProjects.length,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              { title: '名称', dataIndex: 'name' },
              { title: '描述', dataIndex: 'description' },
              { title: 'Git 地址', dataIndex: 'gitUrl' },
              {
                title: 'Git 认证',
                render: (_, record) => {
                  if (record.gitAuthType === 'BASIC') {
                    return <BooleanBadge value trueLabel="账号密码" falseLabel="" />;
                  }
                  if (record.gitAuthType === 'SSH') {
                    return <BooleanBadge value trueLabel="密钥对模式" falseLabel="" />;
                  }
                  return <BooleanBadge value={false} trueLabel="" falseLabel="不认证" />;
                },
              },
              {
                title: '操作',
                width: 280,
                render: (_, record) => (
                  <Space>
                    <Button
                      size="small"
                      loading={testingConnection}
                      onClick={() => testConnectionWithPayload({
                        name: record.name || '',
                        description: record.description || '',
                        gitUrl: record.gitUrl || '',
                        gitAuthType: record.gitAuthType || 'NONE',
                        gitUsername: '',
                        gitPassword: '',
                      }).catch(() => undefined)}
                    >
                      测试连通性
                    </Button>
                    <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                    <Popconfirm
                      title="确认删除这个项目吗？"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => removeProject(record.id).catch(() => message.error('删除项目失败'))}
                    >
                      <Button size="small" danger>删除</Button>
                    </Popconfirm>
                  </Space>
                ),
              },
          ]}
        />
      </Card>
      </div>
      <Modal
        title={editingId ? '编辑项目' : '新建项目'}
        open={modalOpen}
        okText="保存"
        cancelText="取消"
        footer={[
          <Button key="test" loading={testingConnection} onClick={() => testConnection().catch(() => undefined)}>
            测试连通性
          </Button>,
          <Button key="cancel" onClick={() => setModalOpen(false)}>
            取消
          </Button>,
          <Button key="save" type="primary" onClick={() => saveProject().catch(() => message.error(editingId ? '更新项目失败' : '创建项目失败'))}>
            保存
          </Button>,
        ]}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form layout="vertical">
          <Form.Item label="项目名称">
            <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea rows={4} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </Form.Item>
          <Form.Item label="Git 地址">
            <Input value={form.gitUrl} onChange={(event) => setForm({ ...form, gitUrl: event.target.value })} />
          </Form.Item>
          <Form.Item label="Git 认证方式">
            <Radio.Group
              value={form.gitAuthType}
              onChange={(event) => updateGitAuthType(event.target.value)}
              optionType="button"
              buttonStyle="solid"
            >
              <Radio.Button value="NONE">不认证</Radio.Button>
              <Radio.Button value="BASIC">账号密码</Radio.Button>
              <Radio.Button value="SSH">密钥对模式</Radio.Button>
            </Radio.Group>
          </Form.Item>
          {form.gitAuthType === 'BASIC' ? (
            <>
              <Form.Item label="Git 用户名">
                <Input value={form.gitUsername} onChange={(event) => setForm({ ...form, gitUsername: event.target.value })} placeholder="适用于 HTTP/HTTPS 仓库" />
              </Form.Item>
              <Form.Item label="Git 密码">
                <Input.Password value={form.gitPassword} onChange={(event) => setForm({ ...form, gitPassword: event.target.value })} placeholder="请配合 HTTP/HTTPS 仓库地址使用" />
              </Form.Item>
            </>
          ) : null}
          {form.gitAuthType === 'SSH' ? (
            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
              当前项目将使用系统设置里统一生成的 SSH 密钥对访问 Git 仓库。
              请先到“系统设置”中生成密钥对，并把系统公钥配置到 Git 平台。
              同时请确认这里填写的是 SSH 仓库地址，而不是 HTTP 地址。
            </div>
          ) : null}
        </Form>
      </Modal>
    </>
  );
}
