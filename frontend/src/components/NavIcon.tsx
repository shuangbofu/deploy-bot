import {
  BellRinging,
  ChartPieSlice,
  ClockCounterClockwise,
  DesktopTower,
  FolderOpen,
  GearSix,
  HardDrives,
  Package,
  PuzzlePiece,
  RocketLaunch,
  UsersThree,
} from '@phosphor-icons/react';
import { useLayoutMode } from '../theme/LayoutModeProvider';

type NavIconName =
  | 'dashboard'
  | 'project'
  | 'host'
  | 'plugin'
  | 'pipeline'
  | 'deployment'
  | 'service'
  | 'notification'
  | 'user'
  | 'settings';

type Props = {
  name: NavIconName;
  tone?: string;
};

const icons = {
  dashboard: ChartPieSlice,
  project: FolderOpen,
  host: HardDrives,
  plugin: PuzzlePiece,
  pipeline: RocketLaunch,
  deployment: ClockCounterClockwise,
  service: DesktopTower,
  notification: BellRinging,
  user: UsersThree,
  settings: GearSix,
} satisfies Record<NavIconName, typeof ChartPieSlice>;

export default function NavIcon({ name, tone = 'slate' }: Props) {
  const { menuIconStyle } = useLayoutMode();
  const Icon = icons[name] || Package;
  return (
    <span className={`nav-icon nav-icon--${tone} nav-icon--${menuIconStyle}`} aria-hidden="true">
      <Icon weight={menuIconStyle} />
    </span>
  );
}
