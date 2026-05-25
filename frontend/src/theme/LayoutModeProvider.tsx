import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';

type LayoutMode = 'top' | 'side';
export type MenuIconStyle = 'duotone' | 'fill';

type LayoutModeContextValue = {
  mode: LayoutMode;
  menuIconStyle: MenuIconStyle;
  isSideMode: boolean;
  sideCollapsed: boolean;
  setMode: (mode: LayoutMode) => void;
  setMenuIconStyle: (style: MenuIconStyle) => void;
  setSideCollapsed: (collapsed: boolean) => void;
  toggleMode: () => void;
  toggleSideCollapsed: () => void;
};

const LAYOUT_MODE_STORAGE_KEY = 'deploy-bot:layout-mode';
const SIDE_COLLAPSED_STORAGE_KEY = 'deploy-bot:side-collapsed';
const MENU_ICON_STYLE_STORAGE_KEY = 'deploy-bot:menu-icon-style';

const LayoutModeContext = createContext<LayoutModeContextValue | null>(null);

function readInitialMode(): LayoutMode {
  if (typeof window === 'undefined') {
    return 'side';
  }
  const stored = window.localStorage.getItem(LAYOUT_MODE_STORAGE_KEY);
  return stored === 'top' || stored === 'side' ? stored : 'side';
}

function readInitialSideCollapsed() {
  if (typeof window === 'undefined') {
    return true;
  }
  const stored = window.localStorage.getItem(SIDE_COLLAPSED_STORAGE_KEY);
  return stored === null ? true : stored === 'true';
}

function readInitialMenuIconStyle(): MenuIconStyle {
  if (typeof window === 'undefined') {
    return 'fill';
  }
  const stored = window.localStorage.getItem(MENU_ICON_STYLE_STORAGE_KEY);
  return stored === 'fill' || stored === 'duotone' ? stored : 'fill';
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
  const [menuIconStyle, setMenuIconStyleState] = useState<MenuIconStyle>(() => readInitialMenuIconStyle());
  const [sideCollapsed, setSideCollapsedState] = useState(() => readInitialSideCollapsed());
  const isSideMode = mode === 'side';

  useEffect(() => {
    window.localStorage.setItem(LAYOUT_MODE_STORAGE_KEY, mode);
    window.localStorage.setItem(SIDE_COLLAPSED_STORAGE_KEY, String(sideCollapsed));
    window.localStorage.setItem(MENU_ICON_STYLE_STORAGE_KEY, menuIconStyle);
  }, [menuIconStyle, mode, sideCollapsed]);

  const value = useMemo<LayoutModeContextValue>(() => ({
    mode,
    menuIconStyle,
    isSideMode,
    sideCollapsed,
    setMode: setModeState,
    setMenuIconStyle: setMenuIconStyleState,
    setSideCollapsed: setSideCollapsedState,
    toggleMode: () => setModeState((current) => (current === 'side' ? 'top' : 'side')),
    toggleSideCollapsed: () => setSideCollapsedState((current) => !current),
  }), [isSideMode, menuIconStyle, mode, sideCollapsed]);

  return <LayoutModeContext.Provider value={value}>{children}</LayoutModeContext.Provider>;
}

export function useLayoutMode() {
  const context = useContext(LayoutModeContext);
  if (!context) {
    throw new Error('useLayoutMode must be used inside LayoutModeProvider');
  }
  return context;
}
