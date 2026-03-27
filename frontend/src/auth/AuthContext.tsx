import { createContext, useContext, useEffect, useState } from 'react';
import { message } from 'antd';
import { authApi } from '../api/auth';
import type { LoginPayload, UserSummary } from '../api/types';
import { authStorage } from './authStorage';

type AuthContextValue = {
  user?: UserSummary;
  loading: boolean;
  isAdmin: boolean;
  login: (payload: LoginPayload) => Promise<UserSummary>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserSummary>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = authStorage.getToken();
    if (!token) {
      setLoading(false);
      return;
    }
    authApi.me()
      .then((currentUser) => setUser(currentUser))
      .catch(() => {
        authStorage.clearToken();
        setUser(undefined);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = async (payload: LoginPayload) => {
    const response = await authApi.login(payload);
    authStorage.setToken(response.token);
    setUser(response.user);
    message.success('登录成功');
    return response.user;
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore
    }
    authStorage.clearToken();
    setUser(undefined);
  };

  return (
    <AuthContext.Provider value={{ user, loading, isAdmin: user?.role === 'ADMIN', login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}
