import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';

type AppThemeMode = 'light' | 'dark';

type AppThemeContextValue = {
  mode: AppThemeMode;
  isDark: boolean;
  followSystem: boolean;
  autoDarkAtNight: boolean;
  setMode: (mode: AppThemeMode) => void;
  setFollowSystem: (followSystem: boolean) => void;
  setAutoDarkAtNight: (autoDarkAtNight: boolean) => void;
  toggleMode: () => void;
};

const THEME_STORAGE_KEY = 'deploy-bot:theme-mode';
const THEME_FOLLOW_SYSTEM_STORAGE_KEY = 'deploy-bot:theme-follow-system';
const THEME_AUTO_NIGHT_STORAGE_KEY = 'deploy-bot:theme-auto-night';

const AppThemeContext = createContext<AppThemeContextValue | null>(null);

function readInitialTheme(): AppThemeMode {
  if (typeof window === 'undefined') {
    return 'light';
  }
  const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function readBooleanStorage(key: string, defaultValue: boolean) {
  if (typeof window === 'undefined') {
    return defaultValue;
  }
  const stored = window.localStorage.getItem(key);
  if (stored === 'true') {
    return true;
  }
  if (stored === 'false') {
    return false;
  }
  return defaultValue;
}

function isNightTime(date = new Date()) {
  const hour = date.getHours();
  return hour >= 18 || hour < 6;
}

type Props = {
  children: ReactNode;
};

/**
 * 应用主题入口。
 * 这里同时驱动 Ant Design 主题算法和 Tailwind 的 dark class，页面只需要使用 useAppTheme。
 */
export function AppThemeProvider({ children }: Props) {
  const [mode, setModeState] = useState<AppThemeMode>(() => readInitialTheme());
  const [followSystem, setFollowSystemState] = useState(() => readBooleanStorage(THEME_FOLLOW_SYSTEM_STORAGE_KEY, true));
  const [autoDarkAtNight, setAutoDarkAtNightState] = useState(() => readBooleanStorage(THEME_AUTO_NIGHT_STORAGE_KEY, true));
  const [systemDark, setSystemDark] = useState(() => (
    typeof window !== 'undefined' ? Boolean(window.matchMedia?.('(prefers-color-scheme: dark)').matches) : false
  ));
  const [nightTime, setNightTime] = useState(() => isNightTime());
  const isDark = autoDarkAtNight && nightTime ? true : (followSystem ? systemDark : mode === 'dark');

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark);
    document.documentElement.dataset.theme = isDark ? 'dark' : 'light';
    window.localStorage.setItem(THEME_STORAGE_KEY, mode);
    window.localStorage.setItem(THEME_FOLLOW_SYSTEM_STORAGE_KEY, String(followSystem));
    window.localStorage.setItem(THEME_AUTO_NIGHT_STORAGE_KEY, String(autoDarkAtNight));
  }, [autoDarkAtNight, followSystem, isDark, mode]);

  useEffect(() => {
    const matcher = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (!matcher) {
      return undefined;
    }
    const updateSystemTheme = () => setSystemDark(matcher.matches);
    updateSystemTheme();
    matcher.addEventListener('change', updateSystemTheme);
    return () => matcher.removeEventListener('change', updateSystemTheme);
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setNightTime(isNightTime()), 60 * 1000);
    return () => window.clearInterval(timer);
  }, []);

  const value = useMemo<AppThemeContextValue>(() => ({
    mode,
    isDark,
    followSystem,
    autoDarkAtNight,
    setMode: (nextMode) => {
      setModeState(nextMode);
      setFollowSystemState(false);
    },
    setFollowSystem: setFollowSystemState,
    setAutoDarkAtNight: setAutoDarkAtNightState,
    toggleMode: () => setModeState((current) => (current === 'dark' ? 'light' : 'dark')),
  }), [autoDarkAtNight, followSystem, isDark, mode]);

  return (
    <AppThemeContext.Provider value={value}>
      <ConfigProvider
        locale={zhCN}
        theme={{
          algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
          token: {
            colorPrimary: isDark ? '#2563eb' : '#102542',
            colorInfo: isDark ? '#2563eb' : '#102542',
            colorSuccess: isDark ? '#22c55e' : '#166534',
            colorWarning: isDark ? '#d6a85f' : '#b88c4a',
            colorBgBase: isDark ? '#08111f' : '#ffffff',
            colorTextBase: isDark ? '#d8e3f0' : '#173055',
            borderRadius: 14,
          },
          components: {
            Card: {
              colorBgContainer: isDark ? '#111c2f' : '#ffffff',
            },
            Layout: {
              bodyBg: 'transparent',
              headerBg: 'transparent',
            },
            Menu: {
              darkItemBg: 'transparent',
              darkSubMenuItemBg: 'transparent',
            },
          },
        }}
      >
        {children}
      </ConfigProvider>
    </AppThemeContext.Provider>
  );
}

export function useAppTheme() {
  const context = useContext(AppThemeContext);
  if (!context) {
    throw new Error('useAppTheme must be used inside AppThemeProvider');
  }
  return context;
}
