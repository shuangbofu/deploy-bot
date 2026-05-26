import { formatDateTime, formatDateTimeWithoutYear } from '../utils/datetime';

type Props = {
  value?: string | null;
  className?: string;
};

export default function ShortDateTime({ value, className }: Props) {
  const full = formatDateTime(value);
  return (
    <span className={`text-xs ${className || ''}`.trim()} title={full === '-' ? undefined : full}>
      {formatDateTimeWithoutYear(value)}
    </span>
  );
}
