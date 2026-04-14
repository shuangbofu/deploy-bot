export function resolvePortalSwitchPath(pathname: string, target: 'admin' | 'user') {
  const normalized = pathname.replace(/\/+$/, '') || '/';

  if (target === 'admin') {
    if (normalized === '/user/dashboard') {
      return '/admin/dashboard';
    }
    if (normalized === '/user/pipelines' || /^\/user\/pipelines\/\d+\/history$/.test(normalized)) {
      return '/admin/pipelines';
    }
    if (normalized === '/user/deployments' || /^\/user\/deployments\/\d+$/.test(normalized)) {
      return '/admin/deployments';
    }
    return '/admin/pipelines';
  }

  if (normalized === '/admin/dashboard') {
    return '/user/dashboard';
  }
  if (normalized === '/admin/pipelines') {
    return '/user/pipelines';
  }
  if (normalized === '/admin/deployments' || /^\/admin\/deployments\/\d+$/.test(normalized)) {
    return '/user/deployments';
  }
  return '/user/pipelines';
}
