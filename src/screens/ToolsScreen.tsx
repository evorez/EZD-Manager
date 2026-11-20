import React, { useState } from 'react';
import {
  Activity,
  Archive,
  BatteryCharging,
  Check,
  Cpu,
  FileCode2,
  Flame,
  Gauge,
  HardDrive,
  Layers,
  Lock,
  Monitor,
  MousePointerClick,
  Music2,
  Pin,
  Puzzle,
  ShieldAlert,
  Sliders,
  Smartphone,
  Terminal,
  Wifi,
  Zap,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import {
  CollapsibleSection,
  GlassButton,
  GlassCard,
  OutlineButton,
  SectionHeader,
  StatusLabel,
} from '../components/GlassUI';
import { BUILT_IN_PROFILES, TOOLS_META } from '../data/presets';
import { getToolBadge, PRIVILEGE_MODE_LABEL } from '../data/workMode';
import { PrivilegeMode, ToolId } from '../types/ezd';
import { CpuGpuScreen } from './CpuGpuScreen';

const TOOL_ICON_MAP: Record<ToolId, React.ReactNode> = {
  [ToolId.CPU_GPU_CONTROL]: <Cpu className="w-4 h-4" />,
  [ToolId.ZRAM_VM]: <Layers className="w-4 h-4" />,
  [ToolId.KERNEL_TWEAKS]: <Flame className="w-4 h-4" />,
  [ToolId.DISPLAY]: <Monitor className="w-4 h-4" />,
  [ToolId.TOUCH]: <MousePointerClick className="w-4 h-4" />,
  [ToolId.NETWORK]: <Wifi className="w-4 h-4" />,
  [ToolId.AUDIO_ENHANCER]: <Music2 className="w-4 h-4" />,
  [ToolId.BATTERY_SAVING]: <BatteryCharging className="w-4 h-4" />,
  [ToolId.FAST_CHARGE]: <Zap className="w-4 h-4" />,
  [ToolId.DEVICE_SPOOF]: <Smartphone className="w-4 h-4" />,
  [ToolId.PROFILES]: <Sliders className="w-4 h-4" />,
  [ToolId.MODULES]: <Puzzle className="w-4 h-4" />,
  [ToolId.BUILD_PROP]: <FileCode2 className="w-4 h-4" />,
  [ToolId.BOOT_GUARD]: <ShieldAlert className="w-4 h-4" />,
  [ToolId.EZD_BACKUP]: <Archive className="w-4 h-4" />,
  [ToolId.APP_BACKUP]: <HardDrive className="w-4 h-4" />,
  [ToolId.ROOT_TERMINAL]: <Terminal className="w-4 h-4" />,
};

export const ToolsScreen: React.FC = () => {
  const {
    state,
    visuals,
    openTool,
    applyProfile,
    pinToolToPill,
    unpinToolFromPill,
    autoDetectRoot,
  } = useEzd();

  const [cpuGpuExpanded, setCpuGpuExpanded] = useState(false);
  const [lockedToolDialog, setLockedToolDialog] = useState<ToolId | null>(null);

  const mode = state.effectivePrivilegeMode;
  const allProfiles = [...BUILT_IN_PROFILES, ...state.customProfiles];

  const handleOpenTool = (toolId: ToolId) => {
    const badge = getToolBadge(toolId, mode);
    if (badge.isStrictlyLocked) {
      setLockedToolDialog(toolId);
      return;
    }
    openTool(toolId);
  };

  return (
    <div className="space-y-4">
      {/* WorkMode Authority Banner */}
      <GlassCard>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <Activity className="w-4 h-4 shrink-0" style={{ color: visuals.accent }} />
            <div>
              <div className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
                Privilege Engine: {PRIVILEGE_MODE_LABEL[mode]} ({state.settings.workMode})
              </div>
              <div className="text-xs" style={{ color: visuals.textSecondary }}>
                {mode === PrivilegeMode.ROOT
                  ? 'Full sysfs, resetprop, modules & build.prop read/write active.'
                  : mode === PrivilegeMode.SHIZUKU
                  ? 'Rootless ADB shell active · Root-only system partition tools locked.'
                  : 'Read-only monitor active · Grant Root or Shizuku in Settings for full writes.'}
              </div>
            </div>
          </div>
          <StatusLabel
            label={mode}
            color={
              mode === PrivilegeMode.ROOT
                ? visuals.successColor
                : mode === PrivilegeMode.SHIZUKU
                ? visuals.accent
                : visuals.warningColor
            }
          />
        </div>
      </GlassCard>

      {/* Performance Profile Quick Switcher */}
      <GlassCard>
        <SectionHeader title="Quick Profile Switcher" subtitle="Apply profile targets across CPU, GPU, display & touch" />
        <div className="mt-3 flex gap-2 overflow-x-auto pb-1 ezd-scroll-thin">
          {allProfiles.map((prof) => {
            const active = state.settings.activeProfileId === prof.id;
            return (
              <button
                key={prof.id}
                type="button"
                onClick={() => applyProfile(prof)}
                className="inline-flex items-center gap-1.5 rounded-full border px-3.5 py-2 text-xs font-semibold whitespace-nowrap shrink-0 cursor-pointer transition-colors"
                style={{
                  backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                  color: active ? visuals.accentOnColor : visuals.textPrimary,
                }}
              >
                <Gauge className="w-3.5 h-3.5" />
                <span>{prof.name}</span>
                {active && <Check className="w-3.5 h-3.5" />}
              </button>
            );
          })}
        </div>
      </GlassCard>

      {/* Collapsible Unified CPU & GPU Tuning Engine */}
      <GlassCard>
        <CollapsibleSection
          title="CPU & GPU Tuning Engine"
          subtitle={`${state.cpuPolicies.length} CPU clusters (${state.overview.cpuUsagePercent ?? 0}%) · GPU ${state.gpuState.currentMhz ?? 0} MHz`}
          expanded={cpuGpuExpanded}
          onToggle={() => setCpuGpuExpanded((v) => !v)}
          trailingBadge={state.gpuState.vendorHint}
        >
          <CpuGpuScreen />
        </CollapsibleSection>
      </GlassCard>

      {/* Quick Jump Bar */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 ezd-scroll-thin">
        <span className="text-[11px] font-bold uppercase tracking-wider shrink-0" style={{ color: visuals.textSecondary }}>
          Jump:
        </span>
        {[ToolId.ZRAM_VM, ToolId.KERNEL_TWEAKS, ToolId.DISPLAY, ToolId.NETWORK, ToolId.AUDIO_ENHANCER, ToolId.ROOT_TERMINAL].map(
          (tid) => {
            const meta = TOOLS_META.find((t) => t.id === tid);
            if (!meta) return null;
            return (
              <button
                key={tid}
                type="button"
                onClick={() => handleOpenTool(tid)}
                className="rounded-full border px-3 py-1.5 text-xs font-semibold whitespace-nowrap shrink-0 cursor-pointer hover:brightness-110"
                style={{
                  backgroundColor: visuals.solidSurfaceElevated,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              >
                {meta.title}
              </button>
            );
          }
        )}
      </div>

      {/* 17-Tool Grid (Draggable onto Floating Pill Nav) */}
      <div className="space-y-2">
        <div className="flex items-center justify-between px-1">
          <SectionHeader title="Hardware & System Modules (17)" subtitle="Drag any card onto the bottom pill or tap Pin to add shortcuts" />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {TOOLS_META.map((tool) => {
            const badge = getToolBadge(tool.id, mode);
            const isPinned = state.settings.pinnedPillTools.includes(tool.id);

            return (
              <div
                key={tool.id}
                draggable
                onDragStart={(e) => {
                  e.dataTransfer.setData('text/ezd-tool-id', tool.id);
                  e.dataTransfer.effectAllowed = 'copy';
                }}
                style={{
                  backgroundColor: visuals.isLiteMode
                    ? visuals.solidSurfaceBase
                    : visuals.blurAllowed
                    ? visuals.glassSurface
                    : visuals.solidSurfaceBase,
                  borderColor: visuals.glassBorder,
                  backdropFilter: visuals.blurAllowed
                    ? `blur(${Math.round(visuals.blurIntensity * 14)}px)`
                    : undefined,
                }}
                className="rounded-[22px] border p-4 flex flex-col justify-between gap-3 transition-colors hover:brightness-105"
              >
                <div className="flex items-start justify-between gap-2">
                  <button
                    type="button"
                    onClick={() => handleOpenTool(tool.id)}
                    className="flex items-start gap-3 text-left min-w-0 flex-1 cursor-pointer"
                  >
                    <div
                      className="w-10 h-10 rounded-xl border flex items-center justify-center shrink-0"
                      style={{
                        backgroundColor: `${visuals.accent}1A`,
                        borderColor: `${visuals.accent}44`,
                        color: badge.isStrictlyLocked ? visuals.errorColor : visuals.accent,
                      }}
                    >
                      {badge.isStrictlyLocked ? <Lock className="w-4 h-4" /> : TOOL_ICON_MAP[tool.id]}
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-bold truncate" style={{ color: visuals.textPrimary }}>
                        {tool.title}
                      </div>
                      <p className="text-xs line-clamp-2 mt-0.5" style={{ color: visuals.textSecondary }}>
                        {tool.subtitle}
                      </p>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => (isPinned ? unpinToolFromPill(tool.id) : pinToolToPill(tool.id))}
                    title={isPinned ? 'Unpin from pill' : 'Pin to floating pill'}
                    className="p-1.5 rounded-full border shrink-0 cursor-pointer"
                    style={{
                      backgroundColor: isPinned ? visuals.accent : visuals.solidSurfaceBase,
                      borderColor: isPinned ? visuals.accent : visuals.glassBorder,
                      color: isPinned ? visuals.accentOnColor : visuals.textSecondary,
                    }}
                  >
                    <Pin className="w-3.5 h-3.5" />
                  </button>
                </div>

                <div className="flex items-center justify-between gap-2 pt-1">
                  <StatusLabel label={badge.label} color={badge.colorHex} />
                  <button
                    type="button"
                    onClick={() => handleOpenTool(tool.id)}
                    className="text-xs font-semibold cursor-pointer"
                    style={{ color: visuals.accent }}
                  >
                    {badge.isStrictlyLocked ? 'Locked' : 'Configure →'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Strictly Root-Locked Modal */}
      {lockedToolDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          onClick={() => setLockedToolDialog(null)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-sm rounded-[26px] border p-5 shadow-2xl space-y-3"
            style={{ backgroundColor: visuals.solidSurfaceElevated, borderColor: visuals.glassBorder }}
          >
            <div className="flex items-center gap-2" style={{ color: visuals.errorColor }}>
              <Lock className="w-5 h-5" />
              <h3 className="text-base font-bold">Root Authority Required</h3>
            </div>
            <p className="text-xs leading-relaxed" style={{ color: visuals.textSecondary }}>
              {TOOLS_META.find((t) => t.id === lockedToolDialog)?.title} modifies protected system properties or root modules and is locked while running in{' '}
              <strong style={{ color: visuals.textPrimary }}>{mode}</strong> mode.
            </p>
            <div className="flex justify-end gap-2 pt-1">
              <OutlineButton onClick={() => setLockedToolDialog(null)}>Close</OutlineButton>
              <GlassButton
                text="Verify Root (su)"
                onClick={() => {
                  autoDetectRoot(true);
                  setLockedToolDialog(null);
                }}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
