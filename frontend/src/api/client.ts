import axios from 'axios';
import { message } from 'antd';
import type { ApiResult } from './types';

/**
 * 统一的前端 API 客户端。
 * 开发环境走 Vite 代理，生产环境走同源 /api。
 */
const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
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
        message.error(errorMessage);
        return Promise.reject(new Error(errorMessage));
      }
      response.data = result.data;
    }
    return response;
  },
  (error) => {
    const errorMessage = error?.response?.data?.message || error?.message || '请求失败';
    message.error(errorMessage);
    return Promise.reject(error);
  },
);

export default client;
