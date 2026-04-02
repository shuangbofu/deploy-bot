import axios from 'axios';
import { message } from 'antd';
import type { ApiResult } from './types';
import { authStorage } from '../auth/authStorage';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

/**
 * 统一的前端 API 客户端。
 * 开发环境走 Vite 代理，生产环境走同源 /api。
 */
const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

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
    if (!window.location.hash.startsWith('#/login')) {
      window.location.hash = '#/login';
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
        message.error(errorMessage);
        return Promise.reject(Object.assign(new Error(errorMessage), { apiResult: result }));
      }
      response.data = result.data;
    }
    return response;
  },
  (error) => {
    handleAuthExpired(error?.response?.data?.subCode);
    const errorMessage = error?.response?.data?.message || error?.message || '请求失败';
    message.error(errorMessage);
    return Promise.reject(error);
  },
);

export default client;
