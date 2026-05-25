import {
  ClockCounterClockwise,
  GridFour,
  Heart,
  ListBullets,
  PlayCircle,
  SquaresFour,
  WarningCircle,
  type Icon,
} from '@phosphor-icons/react';
import { useLayoutMode } from '../theme/LayoutModeProvider';

type HallSwitchIconName = 'all' | 'favorites' | 'running' | 'failed' | 'recent' | 'card' | 'table';

type Props = {
  name: HallSwitchIconName;
  tone: string;
};

const icons = {
  all: SquaresFour,
  favorites: Heart,
  running: PlayCircle,
  failed: WarningCircle,
  recent: ClockCounterClockwise,
  card: GridFour,
  table: ListBullets,
} satisfies Record<HallSwitchIconName, Icon>;

export default function HallSwitchIcon({ name, tone }: Props) {
  const { menuIconStyle } = useLayoutMode();
  const Icon = icons[name];
  return (
    <span className={`hall-switch-icon hall-switch-icon--${tone} hall-switch-icon--${menuIconStyle}`} aria-hidden="true">
      <Icon weight={menuIconStyle} />
    </span>
  );
}
