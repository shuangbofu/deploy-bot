import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';

type LayoutMode = 'top' | 'side';

type LayoutModeContextValue = {
  mode: LayoutMode;
  isSideMode: boolean;
  sideCollapsed: boolean;
  setMode: (mode: LayoutMode) => void;
  setSideCollapsed: (collapsed: boolean) => void;
  toggleMode: () => void;
  toggleSideCollapsed: () => void;
};

const LAYOUT_MODE_STORAGE_KEY = 'deploy-bot:layout-mode';
const SIDE_COLLAPSED_STORAGE_KEY = 'deploy-bot:side-collapsed';

const LayoutModeContext = createContext<LayoutModeContextValue | null>(null);

function readInitialMode(): LayoutMode {
  if (typeof window === 'undefined') {
    return 'top';
  }
  const stored = window.localStorage.getItem(LAYOUT_MODE_STORAGE_KEY);
  return stored === 'side' ? 'side' : 'top';
}

function readInitialSideCollapsed() {
  if (typeof window === 'undefined') {
    return false;
  }
  return window.localStorage.getItem(SIDE_COLLAPSED_STORAGE_KEY) === 'true';
}

type Props = {
  children: ReactNode;
};

/**
 * 布局模式入口。
 * 管理端和用户端共用同一份状态，支持在顶部导航和左侧菜单之间切换。
 */
export function LayoutModeProvider({ children }: Props) {
  const [mode, setModeState] = useState<LayoutMode>(() => readInitialMode());
  const [sideCollapsed, setSideCollapsedState] = useState(() => readInitialSideCollapsed());
  const isSideMode = mode === 'side';

  useEffect(() => {
    window.localStorage.setItem(LAYOUT_MODE_STORAGE_KEY, mode);
    window.localStorage.setItem(SIDE_COLLAPSED_STORAGE_KEY, String(sideCollapsed));
  }, [mode, sideCollapsed]);

  const value = useMemo<LayoutModeContextValue>(() => ({
    mode,
    isSideMode,
    sideCollapsed,
    setMode: setModeState,
    setSideCollapsed: setSideCollapsedState,
    toggleMode: () => setModeState((current) => (current === 'side' ? 'top' : 'side')),
    toggleSideCollapsed: () => setSideCollapsedState((current) => !current),
  }), [isSideMode, mode, sideCollapsed]);

  return <LayoutModeContext.Provider value={value}>{children}</LayoutModeContext.Provider>;
}

export function useLayoutMode() {
  const context = useContext(LayoutModeContext);
  if (!context) {
    throw new Error('useLayoutMode must be used inside LayoutModeProvider');
  }
  return context;
}
