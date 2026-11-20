import React from 'react';
import { ArrowLeft } from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { GlassCard, StatusLabel } from './GlassUI';
import { TOOLS_META } from '../data/presets';
import { getAuthorityNotice, getToolBadge } from '../data/workMode';
import { CpuGpuScreen } from '../screens/CpuGpuScreen';
import {
  AudioEnhancerPanel,
  BatterySavingPanel,
  DisplayPanel,
  FastChargePanel,
  NetworkPanel,
  TouchPanel,
} from '../screens/ToolDetailsA';
import {
  AppBackupPanel,
  DeviceSpoofPanel,
  EzdBackupPanel,
  ProfilesPanel,
  RootTerminalPanel,
} from '../screens/ToolDetailsB';
import {
  BootGuardPanel,
  BuildPropEditorPanel,
  KernelTweaksPanel,
  RootModulesPanel,
  ZramVmPanel,
} from '../screens/ToolDetailsC';
import { ToolId } from '../types/ezd';

export const ToolDetailContainer: React.FC = () => {
  const { state, visuals, openTool } = useEzd();
  const toolId = state.activeTool;
  if (!toolId) return null;

  const meta = TOOLS_META.find((t) => t.id === toolId);
  const badge = getToolBadge(toolId, state.effectivePrivilegeMode);
  const notice = getAuthorityNotice(toolId, state.effectivePrivilegeMode);

  const renderPanel = () => {
    switch (toolId) {
      case ToolId.CPU_GPU_CONTROL:
        return <CpuGpuScreen />;
      case ToolId.ZRAM_VM:
        return <ZramVmPanel />;
      case ToolId.KERNEL_TWEAKS:
        return <KernelTweaksPanel />;
      case ToolId.DISPLAY:
        return <DisplayPanel />;
      case ToolId.TOUCH:
        return <TouchPanel />;
      case ToolId.NETWORK:
        return <NetworkPanel />;
      case ToolId.AUDIO_ENHANCER:
        return <AudioEnhancerPanel />;
      case ToolId.BATTERY_SAVING:
        return <BatterySavingPanel />;
      case ToolId.FAST_CHARGE:
        return <FastChargePanel />;
      case ToolId.DEVICE_SPOOF:
        return <DeviceSpoofPanel />;
      case ToolId.PROFILES:
        return <ProfilesPanel />;
      case ToolId.MODULES:
        return <RootModulesPanel />;
      case ToolId.BUILD_PROP:
        return <BuildPropEditorPanel />;
      case ToolId.BOOT_GUARD:
        return <BootGuardPanel />;
      case ToolId.EZD_BACKUP:
        return <EzdBackupPanel />;
      case ToolId.APP_BACKUP:
        return <AppBackupPanel />;
      case ToolId.ROOT_TERMINAL:
        return <RootTerminalPanel />;
    }
  };

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <button
              type="button"
              onClick={() => openTool(null)}
              aria-label="Back to Tools"
              className="p-2 rounded-full border shrink-0 cursor-pointer hover:brightness-110"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
                color: visuals.textPrimary,
              }}
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <div className="min-w-0">
              <h2 className="text-base font-bold truncate" style={{ color: visuals.textPrimary }}>
                {meta?.title ?? toolId}
              </h2>
              <p className="text-xs truncate" style={{ color: visuals.textSecondary }}>
                {meta?.subtitle}
              </p>
            </div>
          </div>
          <StatusLabel label={badge.label} color={badge.colorHex} />
        </div>

        {(notice || state.statusBanner) && (
          <div
            className="mt-3 rounded-xl border px-3 py-2 text-xs"
            style={{
              backgroundColor: visuals.solidSurfaceBase,
              borderColor: `${visuals.accent}44`,
              color: visuals.textPrimary,
            }}
          >
            {state.statusBanner || notice}
          </div>
        )}
      </GlassCard>

      {renderPanel()}
    </div>
  );
};
