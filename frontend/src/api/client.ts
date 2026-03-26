import axios from 'axios';

/**
 * 统一的前端 API 客户端。
 * 开发环境走 Vite 代理，生产环境走同源 /api。
 */
const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

export default client;
