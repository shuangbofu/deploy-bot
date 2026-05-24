/**
 * 读取后端统一异常消息。
 */
export function getRequestErrorMessage(error: any, fallback = '请求失败') {
  return error?.apiResult?.subMessage
    || error?.apiResult?.message
    || error?.response?.data?.subMessage
    || error?.response?.data?.message
    || error?.message
    || fallback;
}

/**
 * 远程 SSH/命令执行类错误通常来自后台资源探测或服务轮询。
 * 这类错误更适合显示在页面对应资源上，避免轮询时反复弹全局提示。
 */
export function isRemoteExecutionError(errorOrMessage: unknown) {
  const message = typeof errorOrMessage === 'string'
    ? errorOrMessage
    : getRequestErrorMessage(errorOrMessage, '');
  return /远程执行失败|远程命令执行超时|命令执行超时|连接超时|连接失败|Connection timed out|Connection refused|timed out/i.test(message);
}
