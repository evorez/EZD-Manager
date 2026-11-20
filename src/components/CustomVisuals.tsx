import React, { useEffect, useRef } from 'react';
import { ExternalLink, MessageCircle, X } from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { AudioVisualStyle } from '../types/ezd';

export const DnsProviderLogo: React.FC<{
  providerId: string;
  size?: number;
}> = ({ providerId, size = 28 }) => {
  switch (providerId) {
    case 'cloudflare':
      return (
        <svg width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true">
          <rect width="36" height="36" rx="10" fill="#F38020" fillOpacity="0.18" />
          <path
            d="M24.2 22.8H11.5C9.6 22.8 8 21.3 8 19.4C8 17.6 9.3 16.2 11 16C11.6 13.4 13.9 11.5 16.7 11.5C19.6 11.5 22 13.6 22.4 16.4C22.8 16.2 23.3 16.1 23.8 16.1C25.8 16.1 27.5 17.7 27.5 19.6C27.5 21.4 26 22.8 24.2 22.8Z"
            fill="#F6821F"
          />
          <path d="M25.6 18.4L28.5 18.2M26.2 20.5H28.8" stroke="#FBBF24" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      );
    case 'google':
      return (
        <svg width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true">
          <rect width="36" height="36" rx="10" fill="#4285F4" fillOpacity="0.16" />
          <path
            d="M26.2 18.2C26.2 17.6 26.1 17 26 16.5H18.2V19.7H22.7C22.5 20.8 21.9 21.7 21 22.3V24.4H23.7C25.3 22.9 26.2 20.8 26.2 18.2Z"
            fill="#4285F4"
          />
          <path
            d="M18.2 26.4C20.4 26.4 22.3 25.7 23.7 24.4L21 22.3C20.2 22.8 19.3 23.1 18.2 23.1C16 23.1 14.2 21.7 13.5 19.7H10.7V21.9C12.1 24.7 15 26.4 18.2 26.4Z"
            fill="#34A853"
          />
          <path
            d="M13.5 19.7C13.3 19.2 13.2 18.6 13.2 18C13.2 17.4 13.3 16.8 13.5 16.3V14.1H10.7C10.1 15.3 9.8 16.6 9.8 18C9.8 19.4 10.1 20.7 10.7 21.9L13.5 19.7Z"
            fill="#FBBC05"
          />
          <path
            d="M18.2 12.9C19.4 12.9 20.5 13.3 21.4 14.1L23.8 11.7C22.3 10.3 20.4 9.6 18.2 9.6C15 9.6 12.1 11.3 10.7 14.1L13.5 16.3C14.2 14.3 16 12.9 18.2 12.9Z"
            fill="#EA4335"
          />
        </svg>
      );
    case 'adguard':
      return (
        <svg width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true">
          <rect width="36" height="36" rx="10" fill="#68BC71" fillOpacity="0.18" />
          <path
            d="M18 8.5L26 11.6V17.6C26 22.4 22.5 26.1 18 27.6C13.5 26.1 10 22.4 10 17.6V11.6L18 8.5Z"
            fill="#68BC71"
          />
          <path
            d="M14.5 17.9L17 20.4L22 15.2"
            stroke="#06210C"
            strokeWidth="2.1"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      );
    case 'quad9':
      return (
        <svg width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true">
          <rect width="36" height="36" rx="10" fill="#3B82F6" fillOpacity="0.18" />
          <circle cx="17" cy="17" r="6.5" stroke="#60A5FA" strokeWidth="2.4" />
          <path d="M21.5 21.5L26 26" stroke="#60A5FA" strokeWidth="2.4" strokeLinecap="round" />
          <text x="14.5" y="20" fill="#93C5FD" fontSize="8" fontWeight="700" fontFamily="monospace">
            9
          </text>
        </svg>
      );
    default:
      return (
        <svg width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true">
          <rect width="36" height="36" rx="10" fill="#94A3B8" fillOpacity="0.16" />
          <circle cx="18" cy="18" r="7" stroke="#94A3B8" strokeWidth="1.8" />
          <ellipse cx="18" cy="18" rx="3.2" ry="7" stroke="#94A3B8" strokeWidth="1.4" />
          <path d="M11.3 18H24.7" stroke="#94A3B8" strokeWidth="1.4" />
        </svg>
      );
  }
};

export const AudioEnhancerVisualizer: React.FC<{
  style: AudioVisualStyle;
  bandLevels: number[];
  bassStrength: number;
  loudnessGainMb: number;
  bassEnabled: boolean;
  eqEnabled: boolean;
  loudnessEnabled: boolean;
}> = ({
  style,
  bandLevels,
  bassStrength,
  loudnessGainMb,
  bassEnabled,
  eqEnabled,
  loudnessEnabled,
}) => {
  const { visuals } = useEzd();
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let rafId = 0;
    let t = 0;

    const drawFrame = () => {
      const w = canvas.width;
      const h = canvas.height;
      ctx.clearRect(0, 0, w, h);

      const bassBoost = bassEnabled ? bassStrength / 1000 : 0.12;
      const gainFactor = loudnessEnabled ? 1 + loudnessGainMb / 2500 : 1;
      const activeFactor = bassEnabled || eqEnabled || loudnessEnabled ? 1 : 0.25;

      const avgBand =
        bandLevels.length > 0
          ? bandLevels.reduce((acc, v) => acc + (v + 1500) / 3000, 0) / bandLevels.length
          : 0.5;

      if (style === AudioVisualStyle.BASS_PULSE) {
        const cx = w / 2;
        const cy = h / 2;
        const pulse = Math.sin(t * 2.8) * 0.5 + 0.5;
        const baseR = Math.min(w, h) * 0.16 * (0.85 + bassBoost * 0.45 * pulse * activeFactor);

        for (let rIdx = 3; rIdx >= 0; rIdx--) {
          const radius = baseR + rIdx * 14 + pulse * 6 * activeFactor;
          ctx.beginPath();
          ctx.arc(cx, cy, radius, 0, Math.PI * 2);
          ctx.strokeStyle = visuals.accent;
          ctx.globalAlpha = Math.max(0.12, (0.65 - rIdx * 0.14) * activeFactor);
          ctx.lineWidth = rIdx === 0 ? 3 : 1.5;
          ctx.stroke();
        }
        ctx.globalAlpha = 0.22 * activeFactor;
        ctx.fillStyle = visuals.accent;
        ctx.beginPath();
        ctx.arc(cx, cy, baseR * 0.9, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha = 1;
      } else if (style === AudioVisualStyle.PRISM_BARS) {
        const barCount = 24;
        const gap = 4;
        const barW = (w - gap * (barCount + 1)) / barCount;
        for (let i = 0; i < barCount; i++) {
          const bandIdx = Math.min(bandLevels.length - 1, Math.floor((i / barCount) * bandLevels.length));
          const bandNorm = eqEnabled ? (bandLevels[bandIdx] + 1500) / 3000 : 0.45;
          const wave = Math.sin(t * 3.1 + i * 0.55) * 0.22 + 0.78;
          const normH = Math.min(0.92, Math.max(0.1, bandNorm * wave * gainFactor * activeFactor));
          const barH = h * normH;
          const x = gap + i * (barW + gap);
          const y = h - barH - 6;

          ctx.fillStyle = i % 3 === 0 ? visuals.successColor : visuals.accent;
          ctx.globalAlpha = 0.78;
          ctx.beginPath();
          ctx.roundRect(x, y, barW, barH, 4);
          ctx.fill();
        }
        ctx.globalAlpha = 1;
      } else if (style === AudioVisualStyle.SILK_WAVE) {
        for (let layer = 0; layer < 3; layer++) {
          ctx.beginPath();
          const amp = (14 + bassBoost * 24 + layer * 4) * activeFactor * avgBand;
          const freq = 0.018 + layer * 0.006;
          for (let x = 0; x <= w; x += 4) {
            const y =
              h / 2 +
              Math.sin(x * freq + t * (2.2 + layer * 0.4) + layer) * amp * Math.sin((x / w) * Math.PI);
            if (x === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
          }
          ctx.strokeStyle = layer === 1 ? visuals.successColor : visuals.accent;
          ctx.globalAlpha = 0.85 - layer * 0.22;
          ctx.lineWidth = 2.4 - layer * 0.5;
          ctx.stroke();
        }
        ctx.globalAlpha = 1;
      } else {
        // CYBER_MATRIX
        const cols = 18;
        const rows = 7;
        const cellW = w / cols;
        const cellH = h / rows;
        for (let c = 0; c < cols; c++) {
          const bandIdx = Math.min(bandLevels.length - 1, Math.floor((c / cols) * bandLevels.length));
          const level = eqEnabled ? (bandLevels[bandIdx] + 1500) / 3000 : 0.5;
          const wave = Math.sin(t * 3.4 + c * 0.7) * 0.25 + 0.75;
          const litRows = Math.max(1, Math.round(rows * Math.min(1, level * wave * activeFactor)));
          for (let r = 0; r < rows; r++) {
            const fromBottom = rows - 1 - r;
            const lit = fromBottom < litRows;
            ctx.fillStyle = fromBottom > rows - 3 ? visuals.warningColor : visuals.accent;
            ctx.globalAlpha = lit ? 0.85 : 0.1;
            ctx.fillRect(c * cellW + 2, r * cellH + 2, cellW - 4, cellH - 4);
          }
        }
        ctx.globalAlpha = 1;
      }

      if (!visuals.reduceMotion && !visuals.isLiteMode) {
        t += 0.035;
        rafId = requestAnimationFrame(drawFrame);
      }
    };

    drawFrame();
    return () => {
      if (rafId) cancelAnimationFrame(rafId);
    };
  }, [
    style,
    bandLevels,
    bassStrength,
    loudnessGainMb,
    bassEnabled,
    eqEnabled,
    loudnessEnabled,
    visuals.accent,
    visuals.successColor,
    visuals.warningColor,
    visuals.reduceMotion,
    visuals.isLiteMode,
  ]);

  return (
    <div
      className="relative w-full overflow-hidden rounded-2xl border p-2"
      style={{
        backgroundColor: visuals.solidSurfaceBase,
        borderColor: visuals.glassBorder,
      }}
    >
      <canvas
        ref={canvasRef}
        width={520}
        height={132}
        className="w-full h-[116px] block rounded-xl"
      />
      <div className="mt-1.5 flex items-center justify-between px-1 text-[11px] font-mono" style={{ color: visuals.textSecondary }}>
        <span>DSP ENGINE: {bassEnabled || eqEnabled || loudnessEnabled ? 'ACTIVE' : 'STANDBY'}</span>
        <span className="tabular-nums">
          BASS {bassEnabled ? `${Math.round(bassStrength / 10)}%` : 'OFF'} · GAIN{' '}
          {loudnessEnabled ? `+${(loudnessGainMb / 100).toFixed(1)}dB` : '0.0dB'}
        </span>
      </div>
    </div>
  );
};

export const BrandLaunchSplash: React.FC<{ onFinish: () => void }> = ({ onFinish }) => {
  const { visuals } = useEzd();

  useEffect(() => {
    const delay = visuals.reduceMotion ? 320 : 1050;
    const timer = setTimeout(onFinish, delay);
    return () => clearTimeout(timer);
  }, [onFinish, visuals.reduceMotion]);

  return (
    <div
      onClick={onFinish}
      className="fixed inset-0 z-50 flex flex-col items-center justify-center cursor-pointer transition-opacity duration-300"
      style={{ backgroundColor: visuals.bgBase }}
    >
      <div className="relative flex items-center justify-center w-32 h-32">
        <div
          className={`absolute inset-0 rounded-full border-2 border-dashed ${
            visuals.reduceMotion ? '' : 'ezd-slow-spin'
          }`}
          style={{ borderColor: `${visuals.accent}66` }}
        />
        <img
          src="/assets/evorez_logo_circular.png"
          alt="EZD Manager"
          className="w-24 h-24 rounded-full object-cover shadow-2xl border"
          style={{ borderColor: visuals.glassBorder }}
        />
      </div>
      <div className="mt-5 text-center space-y-1">
        <h1 className="text-xl font-bold tracking-tight" style={{ color: visuals.textPrimary }}>
          EZD Manager
        </h1>
        <p className="text-xs font-mono tracking-wider uppercase" style={{ color: visuals.accent }}>
          Evorez Kernel & System Engine
        </p>
      </div>
    </div>
  );
};

export const DeveloperEggDialog: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const { visuals } = useEzd();

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-sm rounded-[28px] border p-6 text-center shadow-2xl"
        style={{
          backgroundColor: visuals.solidSurfaceElevated,
          borderColor: visuals.glassBorder,
        }}
      >
        <div className="flex justify-end">
          <button
            type="button"
            onClick={onClose}
            aria-label="Close developer dialog"
            className="p-1.5 rounded-full hover:brightness-125 cursor-pointer"
            style={{ color: visuals.textSecondary }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="relative mx-auto w-32 h-32 flex items-center justify-center">
          {/* Scalloped 12-point rotating cookie seal */}
          <svg
            viewBox="0 0 120 120"
            className={`absolute inset-0 w-full h-full ${visuals.reduceMotion ? '' : 'ezd-slow-spin'}`}
            aria-hidden="true"
          >
            {Array.from({ length: 12 }).map((_, i) => {
              const angle = (i * 30 * Math.PI) / 180;
              const cx = 60 + Math.cos(angle) * 44;
              const cy = 60 + Math.sin(angle) * 44;
              return <circle key={i} cx={cx} cy={cy} r="13" fill={visuals.accent} fillOpacity="0.22" />;
            })}
            <circle cx="60" cy="60" r="44" fill={visuals.accent} fillOpacity="0.18" stroke={visuals.accent} strokeWidth="1.5" />
          </svg>
          <img
            src="/assets/evorez_avatar.png"
            alt="Evorez"
            className="relative z-10 w-20 h-20 rounded-full object-cover border-2 shadow-lg"
            style={{ borderColor: visuals.accent }}
          />
        </div>

        <h3 className="mt-4 text-lg font-bold" style={{ color: visuals.textPrimary }}>
          Reporting & Requesting
        </h3>
        <p className="mt-1 text-xs font-mono" style={{ color: visuals.accent }}>
          𝙴𝚟𝚘𝚛𝚎𝚣🪽 · Evolve & Rise
        </p>
        <p className="mt-3 text-sm" style={{ color: visuals.textSecondary }}>
          Report bugs OR Have any ideas? PM ME
        </p>

        <div className="mt-5 flex flex-col gap-2">
          <a
            href="https://t.me/Evorez"
            target="_blank"
            rel="noopener noreferrer"
            style={{ backgroundColor: visuals.accent, color: visuals.accentOnColor }}
            className="inline-flex items-center justify-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold transition-transform active:scale-95"
          >
            <MessageCircle className="w-4 h-4" />
            <span>Message @Evorez on Telegram</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full px-4 py-2 text-xs font-semibold border cursor-pointer"
            style={{
              backgroundColor: visuals.solidSurfaceBase,
              borderColor: visuals.glassBorder,
              color: visuals.textPrimary,
            }}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
