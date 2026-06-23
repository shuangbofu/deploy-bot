import { ApiOutlined, GithubOutlined, LockOutlined, RobotOutlined, ThunderboltOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import deployBotLogo from '../assets/deploy-bot-logo.svg';

const GITHUB_REPOSITORY_URL = 'https://github.com/shuangbofu/deploy-bot';
const aiFeatureCards = [
  { icon: <RobotOutlined />, title: 'Agent 接入', description: 'Codex / Claude Code 可以读取 SKILL，并按平台约定接入项目。' },
  { icon: <ApiOutlined />, title: '免登调用', description: '不让 Agent 走账号密码登录，按权限范围控制读取、写入和部署能力。' },
  { icon: <ThunderboltOutlined />, title: '命令行自动化', description: '通过 deploy-bot CLI 创建项目、派生模板、流水线并触发预检查。' },
];

/**
 * 登录页。
 * 负责建立真实登录态，并根据角色跳转到对应工作台入口。
 */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);

  const resolveCallback = () => {
    const searchParams = new URLSearchParams(location.search);
    const callback = searchParams.get('callback');
    if (callback && callback.startsWith('/')) {
      return callback;
    }
    const stateRedirect = (location.state as { from?: string } | undefined)?.from;
    if (stateRedirect && stateRedirect.startsWith('/')) {
      return stateRedirect;
    }
    return undefined;
  };

  const handleSubmit = async (values: { username: string; password: string }) => {
    setSubmitting(true);
    try {
      const user = await login(values);
      const redirectTo = resolveCallback();
      if (redirectTo && redirectTo.startsWith(user.role === 'ADMIN' ? '/admin' : '/user')) {
        navigate(redirectTo, { replace: true });
        return;
      }
      navigate(user.role === 'ADMIN' ? '/admin/dashboard' : '/user/dashboard', { replace: true });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-app px-6 py-10">
      <div className="mx-auto grid min-h-[calc(100vh-5rem)] max-w-6xl items-center gap-6 lg:grid-cols-[1fr_460px]">
        <section className="login-ai-hero">
          <div className="login-ai-hero__eyebrow">AI Agent</div>
          <h1 className="login-ai-hero__title">让本地 Agent 也能按平台规则完成部署接入</h1>
          <p className="login-ai-hero__description">
            Deploy Bot 现在提供 API Token、仓库内 CLI 和 SKILL。AI 不需要拿账号密码登录，也不用猜接口，可以先识别项目，再生成可审查的模板、流水线和部署请求。
          </p>
          <div className="login-ai-feature-grid">
            {aiFeatureCards.map((item) => (
              <div key={item.title} className="login-ai-feature-card">
                <span className="login-ai-feature-card__icon">{item.icon}</span>
                <span className="login-ai-feature-card__body">
                  <span className="login-ai-feature-card__title">{item.title}</span>
                  <span className="login-ai-feature-card__description">{item.description}</span>
                </span>
              </div>
            ))}
          </div>
        </section>
        <Card className="app-card w-full max-w-[460px] !rounded-[28px] !border-white/10">
          <div className="mb-8 text-center">
            <img src={deployBotLogo} alt="Deploy Bot Logo" className="mx-auto mb-4 h-20 w-20" />
            <Typography.Text className="block text-[12px] uppercase tracking-[0.35em] !text-slate-400">
              Deploy Bot
            </Typography.Text>
            <Typography.Title level={2} className="!mb-2 !mt-3">
              登录
            </Typography.Title>
          </div>
          <Form layout="vertical" onFinish={handleSubmit} autoComplete="off">
            <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input size="large" prefix={<UserOutlined />} placeholder="请输入用户名" />
            </Form.Item>
            <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password size="large" prefix={<LockOutlined />} placeholder="请输入密码" />
            </Form.Item>
            <div className="flex items-center gap-3">
              <Button type="primary" htmlType="submit" size="large" loading={submitting} className="!flex-1">
                登录
              </Button>
              <a
                href={GITHUB_REPOSITORY_URL}
                target="_blank"
                rel="noreferrer"
                className="inline-flex !h-10 !w-10 shrink-0 items-center justify-center rounded-xl border border-slate-300 bg-white text-slate-700 transition-colors hover:border-slate-400 hover:text-slate-900"
              >
                <GithubOutlined className="text-lg" />
              </a>
            </div>
          </Form>
          <div className="mt-6 rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-500">
            默认管理员账号：<code>admin</code> / <code>Admin@123456</code>
          </div>
        </Card>
      </div>
    </div>
  );
}
