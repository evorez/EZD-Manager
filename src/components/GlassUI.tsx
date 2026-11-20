import React from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { BackgroundTexturePreset } from '../types/ezd';

export const EzdAmbientBackground: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { visuals } = useEzd();

  const textureSvgUrl = React.useMemo(() => {
    if (visuals.texturePreset === BackgroundTexturePreset.PLAIN || visuals.isLiteMode) return null;
    if (visuals.texturePreset === BackgroundTexturePreset.PAPER) {
      return `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='0.24'/%3E%3C/svg%3E")`;
    }
    if (visuals.texturePreset === BackgroundTexturePreset.MOON) {
      return `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='180' height='180'%3E%3Ccircle cx='35' cy='42' r='18' fill='%23888888' opacity='0.14'/%3E%3Ccircle cx='120' cy='115' r='26' fill='%23888888' opacity='0.10'/%3E%3Ccircle cx='145' cy='38' r='10' fill='%23888888' opacity='0.16'/%3E%3C/svg%3E")`;
    }
    return `repeating-linear-gradient(90deg, rgba(160,175,195,0.08) 0px, rgba(160,175,195,0.08) 1px, transparent 1px, transparent 4px)`;
  }, [visuals.texturePreset, visuals.isLiteMode]);

  return (
    <div
      className="relative min-h-screen w-full transition-colors duration-200"
      style={{
        backgroundColor: visuals.bgBase,
        color: visuals.textPrimary,
        backgroundImage: visuals.gradientsAllowed
          ? `radial-gradient(ellipse 85% 45% at 50% 0%, ${visuals.bgTopGlow}, transparent 70%), radial-gradient(ellipse 80% 40% at 50% 100%, ${visuals.bgBottomGlow}, transparent 75%)`
          : undefined,
      }}
    >
      {textureSvgUrl && (
        <div
          aria-hidden="true"
          className="pointer-events-none fixed inset-0 z-0"
          style={{
            backgroundImage: textureSvgUrl,
            opacity: visuals.textureOpacity,
          }}
        />
      )}
      <div className="relative z-10 min-h-screen flex flex-col">{children}</div>
    </div>
  );
};

export const GlassCard: React.FC<{
  children: React.ReactNode;
  className?: string;
  highlightBorder?: boolean;
  onClick?: () => void;
}> = ({ children, className = '', highlightBorder = false, onClick }) => {
  const { visuals } = useEzd();

  const style: React.CSSProperties = {
    backgroundColor: visuals.isLiteMode
      ? visuals.solidSurfaceBase
      : visuals.blurAllowed
      ? visuals.glassSurface
      : visuals.solidSurfaceBase,
    borderColor: highlightBorder ? `${visuals.accent}66` : visuals.glassBorder,
    backdropFilter: visuals.blurAllowed ? `blur(${Math.round(visuals.blurIntensity * 16)}px) saturate(140%)` : undefined,
    WebkitBackdropFilter: visuals.blurAllowed ? `blur(${Math.round(visuals.blurIntensity * 16)}px) saturate(140%)` : undefined,
  };

  const Tag = onClick ? 'button' : 'div';

  return (
    <Tag
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      style={style}
      className={`w-full text-left rounded-[24px] border p-4 sm:p-5 transition-colors duration-150 ${
        onClick ? 'cursor-pointer hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2' : ''
      } ${className}`}
    >
      {children}
    </Tag>
  );
};

export const GlassButton: React.FC<{
  text: string;
  icon?: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  accent?: boolean;
  className?: string;
}> = ({ text, icon, onClick, disabled = false, accent = true, className = '' }) => {
  const { visuals } = useEzd();
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      style={{
        backgroundColor: accent ? visuals.accent : visuals.solidSurfaceElevated,
        color: accent ? visuals.accentOnColor : visuals.textPrimary,
        borderColor: accent ? visuals.accent : visuals.glassBorder,
      }}
      className={`inline-flex items-center justify-center gap-2 rounded-full border px-4 py-2.5 min-h-[42px] text-sm font-semibold whitespace-nowrap shrink-0 transition-transform duration-150 active:scale-[0.98] disabled:opacity-45 disabled:cursor-not-allowed cursor-pointer ${className}`}
    >
      {icon}
      <span>{text}</span>
    </button>
  );
};

export const OutlineButton: React.FC<{
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}> = ({ children, onClick, disabled = false, className = '' }) => {
  const { visuals } = useEzd();
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      style={{
        backgroundColor: visuals.solidSurfaceElevated,
        color: visuals.textPrimary,
        borderColor: visuals.glassBorder,
      }}
      className={`inline-flex items-center justify-center gap-1.5 rounded-full border px-4 py-2 min-h-[40px] text-xs font-semibold whitespace-nowrap shrink-0 transition-colors hover:brightness-110 disabled:opacity-45 disabled:cursor-not-allowed cursor-pointer ${className}`}
    >
      {children}
    </button>
  );
};

export const StatusLabel: React.FC<{
  label: string;
  color: string;
  className?: string;
}> = ({ label, color, className = '' }) => (
  <span
    style={{
      color,
      backgroundColor: `${color}1A`,
      borderColor: `${color}45`,
    }}
    className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-semibold font-mono whitespace-nowrap shrink-0 ${className}`}
  >
    {label}
  </span>
);

export const SectionHeader: React.FC<{
  title: string;
  subtitle?: string;
}> = ({ title, subtitle }) => {
  const { visuals } = useEzd();
  return (
    <div className="space-y-0.5">
      <h2 className="text-xs font-bold tracking-wider uppercase" style={{ color: visuals.accent }}>
        {title}
      </h2>
      {subtitle && (
        <p className="text-xs" style={{ color: visuals.textSecondary }}>
          {subtitle}
        </p>
      )}
    </div>
  );
};

export const ToggleSwitch: React.FC<{
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  ariaLabel?: string;
}> = ({ checked, onChange, disabled = false, ariaLabel }) => {
  const { visuals } = useEzd();
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={ariaLabel}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      style={{
        backgroundColor: checked ? visuals.accent : visuals.solidSurfaceElevated,
        borderColor: checked ? visuals.accent : visuals.glassBorder,
      }}
      className="relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors duration-150 disabled:opacity-45 cursor-pointer disabled:cursor-not-allowed"
    >
      <span
        className={`inline-block h-5 w-5 rounded-full bg-white shadow transition-transform duration-150 ${
          checked ? 'translate-x-6' : 'translate-x-1'
        }`}
      />
    </button>
  );
};

export const CollapsibleSection: React.FC<{
  title: string;
  subtitle?: string;
  expanded: boolean;
  onToggle: () => void;
  trailingBadge?: string;
  children: React.ReactNode;
}> = ({ title, subtitle, expanded, onToggle, trailingBadge, children }) => {
  const { visuals } = useEzd();
  return (
    <div>
      <button
        type="button"
        onClick={onToggle}
        className="w-full flex items-center justify-between gap-3 text-left cursor-pointer"
      >
        <div className="min-w-0 flex-1">
          <div className="text-base font-semibold" style={{ color: visuals.textPrimary }}>
            {title}
          </div>
          {subtitle && (
            <div className="text-xs truncate" style={{ color: visuals.textSecondary }}>
              {subtitle}
            </div>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {trailingBadge && <StatusLabel label={trailingBadge} color={visuals.accent} />}
          {expanded ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
        </div>
      </button>
      {expanded && <div className="mt-3 pt-3 border-t" style={{ borderColor: visuals.glassBorder }}>{children}</div>}
    </div>
  );
};

export const AppIconBadge: React.FC<{
  label: string;
  colorHex?: string;
  size?: number;
}> = ({ label, colorHex = '#3B82F6', size = 40 }) => {
  const initials = label
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? '')
    .join('');

  return (
    <div
      style={{
        width: size,
        height: size,
        backgroundColor: `${colorHex}26`,
        borderColor: `${colorHex}66`,
        color: colorHex,
      }}
      className="rounded-xl border flex items-center justify-center font-bold text-xs shrink-0 select-none"
    >
      {initials || 'AP'}
    </div>
  );
};
