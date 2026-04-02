import { useEffect, useState } from 'react';
import { Button, Card, Collapse, Form, Input, Modal, Popconfirm, Space, Typography, message } from 'antd';
import { systemSettingsApi } from '../../api/systemSettings';
import type { SystemSettingsPayload } from '../../api/types';
import PageHeaderBar from '../../components/PageHeaderBar';

const emptySettings: SystemSettingsPayload = {
  gitExecutable: 'git',
  gitSshPublicKey: '',
  gitSshKnownHosts: '',
  hostSshPublicKey: '',
};

export default function SystemSettingsPage() {
  const [form, setForm] = useState<SystemSettingsPayload>(emptySettings);
  const [saving, setSaving] = useState(false);
  const [previewTitle, setPreviewTitle] = useState('');
  const [previewContent, setPreviewContent] = useState('');
  const [previewOpen, setPreviewOpen] = useState(false);

  const hostInstallScript = form.hostSshPublicKey
    ? [
      '#!/usr/bin/env bash',
      'set -euo pipefail',
      'mkdir -p ~/.ssh',
      'chmod 700 ~/.ssh',
      `cat <<'__DEPLOYBOT_HOST_KEY__' >> ~/.ssh/authorized_keys`,
      form.hostSshPublicKey,
      '__DEPLOYBOT_HOST_KEY__',
      'chmod 600 ~/.ssh/authorized_keys',
    ].join('\n')
    : '';

  const copyText = async (text: string, successMessage: string) => {
    if (!text) {
      return;
    }
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.setAttribute('readonly', 'true');
        textarea.style.position = 'fixed';
        textarea.style.left = '-9999px';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      message.success(successMessage);
    } catch {
      message.error('复制失败，请点击“查看”后手动复制');
    }
  };

  const openPreview = (title: string, content: string) => {
    setPreviewTitle(title);
    setPreviewContent(content);
    setPreviewOpen(true);
  };

  const loadSettings = async () => {
    const response = await systemSettingsApi.get();
    setForm({
      gitExecutable: response.gitExecutable || 'git',
      gitSshPublicKey: response.gitSshPublicKey || '',
      gitSshKnownHosts: response.gitSshKnownHosts || '',
      hostSshPublicKey: response.hostSshPublicKey || '',
    });
  };

  useEffect(() => {
    loadSettings().catch(() => message.error('加载系统设置失败'));
  }, []);

  const saveSettings = async () => {
    setSaving(true);
    try {
      await systemSettingsApi.update(form);
      await loadSettings();
      message.success('系统设置已保存');
    } finally {
      setSaving(false);
    }
  };

  const generateKeyPair = async () => {
    await systemSettingsApi.generateGitKeyPair();
    await loadSettings();
    message.success('Git SSH 密钥对已生成');
  };

  const generateHostKeyPair = async () => {
    await systemSettingsApi.generateHostKeyPair();
    await loadSettings();
    message.success('主机 SSH 密钥对已生成');
  };

  const hasGitKeyPair = Boolean(form.gitSshPublicKey);
  const hasHostKeyPair = Boolean(form.hostSshPublicKey);

  return (
    <>
      <PageHeaderBar
        title="系统设置"
        description="配置平台级 Git 执行参数，以及 Git SSH 和主机 SSH 密钥。"
        extra={<Button type="primary" loading={saving} onClick={() => saveSettings().catch(() => message.error('保存系统设置失败'))}>保存设置</Button>}
      />
      <Card className="app-card">
        <Form layout="vertical">
          <div className="mb-4 text-base font-semibold text-slate-800">执行设置</div>
          <Form.Item label="Git 可执行文件">
            <Input
              value={form.gitExecutable}
              onChange={(event) => setForm({ ...form, gitExecutable: event.target.value })}
              placeholder="git"
            />
          </Form.Item>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
            项目自己的 Git 认证方式请到“项目管理”里配置；每台机器自己的工作空间请到“主机管理”里配置。这里的 `Git 可执行文件` 只控制平台执行 Git 命令时用哪个二进制。
          </div>
        </Form>
      </Card>
      <Card className="app-card mt-4">
        <Form layout="vertical">
          <div className="mb-4 text-base font-semibold text-slate-800">Git SSH 密钥对</div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="text-sm font-medium text-slate-800">系统公钥</div>
                <Typography.Paragraph className="!mb-0 !mt-1 max-w-[560px] font-mono !text-xs !text-slate-500" ellipsis={{ rows: 2 }}>
                  {form.gitSshPublicKey || '点击“生成密钥对”后，公钥会显示在这里。'}
                </Typography.Paragraph>
              </div>
              <Space wrap>
                {hasGitKeyPair ? (
                  <Popconfirm
                    title="确认重新生成 Git SSH 密钥对？"
                    description="重新生成后，系统当前使用的 Git SSH 公私钥会被替换。所有依赖这套 Git 密钥的仓库都需要同步更新平台公钥，否则后续拉代码会失败。"
                    okText="确认生成"
                    cancelText="取消"
                    onConfirm={() => generateKeyPair().catch(() => message.error('生成密钥对失败'))}
                  >
                    <Button type="primary">重新生成密钥对</Button>
                  </Popconfirm>
                ) : (
                  <Button type="primary" onClick={() => generateKeyPair().catch(() => message.error('生成密钥对失败'))}>
                    生成密钥对
                  </Button>
                )}
                <Button disabled={!form.gitSshPublicKey} onClick={() => copyText(form.gitSshPublicKey || '', '公钥已复制')}>
                  复制公钥
                </Button>
                <Button disabled={!form.gitSshPublicKey} onClick={() => openPreview('Git SSH 公钥', form.gitSshPublicKey || '')}>
                  查看
                </Button>
              </Space>
            </div>
          </div>
          <Collapse
            ghost
            className="mt-4"
            items={[
              {
                key: 'known-hosts',
                label: '高级：known_hosts / 主机指纹校验',
                children: (
                  <div className="space-y-3">
                    <div className="text-sm leading-7 text-slate-600">
                      `known_hosts` 用来校验 Git 服务器身份，避免连到被冒充的主机。不填时系统会关闭严格校验；只有你需要固定校验 Git 主机指纹时再展开填写。
                    </div>
                    <Input.TextArea
                      rows={5}
                      value={form.gitSshKnownHosts}
                      onChange={(event) => setForm({ ...form, gitSshKnownHosts: event.target.value })}
                      placeholder="可选。填写后会启用主机指纹校验；不填则默认关闭 StrictHostKeyChecking。"
                    />
                  </div>
                ),
              },
            ]}
          />
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
            这是整个系统共用的一套 SSH 密钥对。项目选择“密钥对模式”后，会统一使用这套私钥拉代码；管理员只需要把上面的公钥添加到对应 Git 平台。
          </div>
        </Form>
      </Card>
      <Card className="app-card mt-4">
        <Form layout="vertical">
          <div className="mb-4 text-base font-semibold text-slate-800">主机 SSH 密钥对</div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="text-sm font-medium text-slate-800">主机公钥</div>
                <Typography.Paragraph className="!mb-0 !mt-1 max-w-[560px] font-mono !text-xs !text-slate-500" ellipsis={{ rows: 2 }}>
                  {form.hostSshPublicKey || '点击“生成密钥对”后，公钥会显示在这里。'}
                </Typography.Paragraph>
              </div>
              <Space wrap>
                {hasHostKeyPair ? (
                  <Popconfirm
                    title="确认重新生成主机 SSH 密钥对？"
                    description="重新生成后，系统当前用于登录远程主机的 SSH 公私钥会被替换。所有已配置过这套主机公钥的服务器，都需要重新更新 authorized_keys，否则后续远程部署会失败。"
                    okText="确认生成"
                    cancelText="取消"
                    onConfirm={() => generateHostKeyPair().catch(() => message.error('生成主机密钥对失败'))}
                  >
                    <Button type="primary">重新生成密钥对</Button>
                  </Popconfirm>
                ) : (
                  <Button type="primary" onClick={() => generateHostKeyPair().catch(() => message.error('生成主机密钥对失败'))}>
                    生成密钥对
                  </Button>
                )}
                <Button disabled={!form.hostSshPublicKey} onClick={() => copyText(form.hostSshPublicKey || '', '公钥已复制')}>
                  复制公钥
                </Button>
                <Button disabled={!form.hostSshPublicKey} onClick={() => openPreview('主机 SSH 公钥', form.hostSshPublicKey || '')}>
                  查看
                </Button>
              </Space>
            </div>
          </div>
          <Collapse
            ghost
            className="mt-4"
            items={[
              {
                key: 'host-script',
                label: '快捷脚本：追加到 authorized_keys',
                children: (
                  <div className="space-y-3">
                    <div className="text-sm leading-7 text-slate-600">
                      登录到目标主机后，进入任意目录执行这段脚本即可。它会自动创建 `~/.ssh`，并把当前公钥追加到 `authorized_keys`。
                    </div>
                    <Space wrap>
                      <Button disabled={!hostInstallScript} onClick={() => openPreview('主机 SSH 快捷脚本', hostInstallScript)}>
                        查看脚本
                      </Button>
                      <Button disabled={!hostInstallScript} onClick={() => copyText(hostInstallScript, '快捷脚本已复制')}>
                        复制脚本
                      </Button>
                    </Space>
                  </div>
                ),
              },
            ]}
          />
          <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
            这套密钥专门用于平台登录远程主机执行部署。请把上面的公钥添加到目标主机的 `authorized_keys`，不要和 Git 仓库的 SSH 密钥混用。
          </div>
        </Form>
      </Card>
      <Modal
        title={previewTitle}
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={(
          <Space>
            <Button onClick={() => setPreviewOpen(false)}>关闭</Button>
            <Button type="primary" onClick={() => copyText(previewContent, '内容已复制')}>
              复制内容
            </Button>
          </Space>
        )}
      >
        <Input.TextArea readOnly rows={8} value={previewContent} className="font-mono" />
      </Modal>
    </>
  );
}
