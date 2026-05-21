import axios from 'axios';
import { message } from 'antd';
import type { ApiResult } from './types';
import { authStorage } from '../auth/authStorage';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const originalMessageError = message.error.bind(message);
const LOCAL_ERROR_SUPPRESS_WINDOW = 800;

let suppressNextLocalErrorUntil = 0;

/**
 * 统一的前端 API 客户端。
 * 开发环境走 Vite 代理，生产环境走同源 /api。
 */
const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

function markNextLocalErrorSuppressed() {
  suppressNextLocalErrorUntil = Date.now() + LOCAL_ERROR_SUPPRESS_WINDOW;
}

function shouldSuppressLocalError() {
  return Date.now() <= suppressNextLocalErrorUntil;
}

message.error = ((content: Parameters<typeof originalMessageError>[0], duration?: Parameters<typeof originalMessageError>[1], onClose?: Parameters<typeof originalMessageError>[2]) => {
  if (shouldSuppressLocalError()) {
    suppressNextLocalErrorUntil = 0;
    return (() => undefined) as ReturnType<typeof originalMessageError>;
  }
  return originalMessageError(content, duration as never, onClose as never);
}) as typeof message.error;

export function resolveBackendAssetUrl(url?: string | null) {
  if (!url) {
    return undefined;
  }
  if (/^https?:\/\//i.test(url) || url.startsWith('data:')) {
    return url;
  }
  const baseUrl = API_BASE_URL;
  if (/^https?:\/\//i.test(baseUrl)) {
    const apiOrigin = new URL(baseUrl).origin;
    return new URL(url, apiOrigin).toString();
  }
  return new URL(url, window.location.origin).toString();
}

function handleAuthExpired(subCode?: string | null) {
  if (subCode === 'AUTH-001' || subCode === 'AUTH-003') {
    authStorage.clearToken();
    const hashPath = window.location.hash.replace(/^#/, '') || '/';
    const callback = hashPath.startsWith('/') ? hashPath : `/${hashPath}`;
    const loginUrl = `/#/login?callback=${encodeURIComponent(callback)}`;
    if (!callback.startsWith('/login')) {
      window.location.replace(loginUrl);
    }
    return true;
  }
  return false;
}

client.interceptors.request.use((config) => {
  const token = authStorage.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResult<unknown> | unknown;
    if (
      payload
      && typeof payload === 'object'
      && 'success' in (payload as Record<string, unknown>)
      && 'message' in (payload as Record<string, unknown>)
    ) {
      const result = payload as ApiResult<unknown>;
      if (!result.success) {
        const errorMessage = result.subMessage || result.message || '请求失败';
        handleAuthExpired(result.subCode);
        markNextLocalErrorSuppressed();
        originalMessageError(errorMessage);
        return Promise.reject(Object.assign(new Error(errorMessage), { apiResult: result, requestErrorToastShown: true }));
      }
      response.data = result.data;
    }
    return response;
  },
  (error) => {
    handleAuthExpired(error?.response?.data?.subCode);
    const isTimeout = error?.code === 'ECONNABORTED' || String(error?.message || '').includes('timeout');
    const errorMessage = isTimeout
      ? '请求超时，请稍后重试'
      : (error?.response?.data?.message || error?.message || '请求失败');
    markNextLocalErrorSuppressed();
    originalMessageError(errorMessage);
    error.requestErrorToastShown = true;
    return Promise.reject(error);
  },
);

export default client;
