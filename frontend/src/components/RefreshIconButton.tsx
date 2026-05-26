import { ArrowsClockwise } from '@phosphor-icons/react';
import { Button } from 'antd';
import type { ButtonProps } from 'antd';

type Props = Omit<ButtonProps, 'children' | 'icon'> & {
  label?: string;
};

export default function RefreshIconButton({ label = '刷新', ...props }: Props) {
  return (
    <Button
      aria-label={label}
      title={label}
      icon={<ArrowsClockwise size={16} weight="bold" />}
      {...props}
    />
  );
}
