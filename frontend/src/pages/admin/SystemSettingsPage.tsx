import { useEffect, useState } from 'react';
import { Button, Card, Form, Input, Space, message } from 'antd';
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

  return (
    <>
      <PageHeaderBar
        title="系统设置"
        description="在这里配置平台级参数。工作空间已经下沉到主机管理，这里只保留平台通用的 Git 执行和系统 SSH 密钥对。"
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
          <Form.Item>
            <Space>
              <Button type="primary" onClick={() => generateKeyPair().catch(() => message.error('生成密钥对失败'))}>
                生成密钥对
              </Button>
              <Button
                disabled={!form.gitSshPublicKey}
                onClick={() => navigator.clipboard.writeText(form.gitSshPublicKey).then(() => message.success('公钥已复制')).catch(() => message.error('复制公钥失败'))}
              >
                复制公钥
              </Button>
            </Space>
          </Form.Item>
          <Form.Item label="系统公钥">
            <Input.TextArea
              rows={5}
              readOnly
              value={form.gitSshPublicKey}
              placeholder="点击“生成密钥对”后，公钥会显示在这里。把这段公钥添加到 Git 平台即可。"
            />
          </Form.Item>
          <Form.Item label="known_hosts">
            <Input.TextArea
              rows={5}
              value={form.gitSshKnownHosts}
              onChange={(event) => setForm({ ...form, gitSshKnownHosts: event.target.value })}
              placeholder="可选。填写后会启用主机指纹校验；不填则默认关闭 StrictHostKeyChecking。"
            />
          </Form.Item>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
            这是整个系统共用的一套 SSH 密钥对。项目选择“密钥对模式”后，会统一使用这套私钥拉代码；管理员只需要把上面的公钥添加到对应 Git 平台。
          </div>
        </Form>
      </Card>
      <Card className="app-card mt-4">
        <Form layout="vertical">
          <div className="mb-4 text-base font-semibold text-slate-800">主机 SSH 密钥对</div>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={() => generateHostKeyPair().catch(() => message.error('生成主机密钥对失败'))}>
                生成密钥对
              </Button>
              <Button
                disabled={!form.hostSshPublicKey}
                onClick={() => navigator.clipboard.writeText(form.hostSshPublicKey).then(() => message.success('公钥已复制')).catch(() => message.error('复制公钥失败'))}
              >
                复制公钥
              </Button>
            </Space>
          </Form.Item>
          <Form.Item label="主机公钥">
            <Input.TextArea
              rows={5}
              readOnly
              value={form.hostSshPublicKey}
              placeholder="把这段公钥添加到目标主机的 ~/.ssh/authorized_keys。"
            />
          </Form.Item>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
            这套密钥专门用于平台登录远程主机执行部署。请把上面的公钥添加到目标主机的 `authorized_keys`，不要和 Git 仓库的 SSH 密钥混用。
          </div>
        </Form>
      </Card>
    </>
  );
}
