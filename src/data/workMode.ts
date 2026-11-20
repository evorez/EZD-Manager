import {
  PrivilegeMode,
  RootStatus,
  ShizukuStatus,
  ToolId,
  WorkMode,
} from '../types/ezd';

export interface ToolAuthorityBadge {
  label: string;
  colorHex: string;
  isGranted: boolean;
  isStrictlyLocked: boolean;
}

export function resolveEffectiveMode(
  preferredMode: WorkMode,
  rootStatus: RootStatus,
  shizukuStatus: ShizukuStatus
): PrivilegeMode {
  const rootGranted = rootStatus === RootStatus.GRANTED;
  const shizukuGranted = shizukuStatus === ShizukuStatus.GRANTED;
  switch (preferredMode) {
    case WorkMode.AUTO:
      if (rootGranted) return PrivilegeMode.ROOT;
      if (shizukuGranted) return PrivilegeMode.SHIZUKU;
      return PrivilegeMode.BASIC;
    case WorkMode.ROOT:
      return rootGranted ? PrivilegeMode.ROOT : PrivilegeMode.BASIC;
    case WorkMode.SHIZUKU:
      return shizukuGranted ? PrivilegeMode.SHIZUKU : PrivilegeMode.BASIC;
    case WorkMode.BASIC:
      return PrivilegeMode.BASIC;
  }
}

export function getRequiredAuthority(tool: ToolId): PrivilegeMode {
  switch (tool) {
    case ToolId.CPU_GPU_CONTROL:
    case ToolId.ZRAM_VM:
    case ToolId.KERNEL_TWEAKS:
    case ToolId.TOUCH:
    case ToolId.FAST_CHARGE:
    case ToolId.DEVICE_SPOOF:
    case ToolId.MODULES:
    case ToolId.BUILD_PROP:
      return PrivilegeMode.ROOT;
    case ToolId.DISPLAY:
    case ToolId.NETWORK:
    case ToolId.BATTERY_SAVING:
    case ToolId.APP_BACKUP:
    case ToolId.ROOT_TERMINAL:
      return PrivilegeMode.SHIZUKU;
    case ToolId.AUDIO_ENHANCER:
    case ToolId.PROFILES:
    case ToolId.BOOT_GUARD:
    case ToolId.EZD_BACKUP:
      return PrivilegeMode.BASIC;
  }
}

export function isStrictlyRootLocked(tool: ToolId): boolean {
  return (
    tool === ToolId.MODULES ||
    tool === ToolId.BUILD_PROP ||
    tool === ToolId.DEVICE_SPOOF
  );
}

export function getToolBadge(tool: ToolId, currentMode: PrivilegeMode): ToolAuthorityBadge {
  const required = getRequiredAuthority(tool);
  if (required === PrivilegeMode.ROOT && currentMode === PrivilegeMode.ROOT) {
    return { label: 'ROOT', colorHex: '#22C55E', isGranted: true, isStrictlyLocked: false };
  }
  if (required === PrivilegeMode.ROOT && currentMode !== PrivilegeMode.ROOT) {
    const locked = isStrictlyRootLocked(tool);
    return {
      label: locked ? 'LOCKED (ROOT)' : 'TELEMETRY ONLY',
      colorHex: locked ? '#EF4444' : '#F59E0B',
      isGranted: false,
      isStrictlyLocked: locked,
    };
  }
  if (
    required === PrivilegeMode.SHIZUKU &&
    (currentMode === PrivilegeMode.ROOT || currentMode === PrivilegeMode.SHIZUKU)
  ) {
    return {
      label: currentMode === PrivilegeMode.ROOT ? 'ROOT' : 'SHIZUKU',
      colorHex: '#3B82F6',
      isGranted: true,
      isStrictlyLocked: false,
    };
  }
  if (required === PrivilegeMode.SHIZUKU && currentMode === PrivilegeMode.BASIC) {
    return { label: 'SHIZUKU REQ', colorHex: '#F59E0B', isGranted: false, isStrictlyLocked: false };
  }
  return { label: 'SAFE', colorHex: '#10B981', isGranted: true, isStrictlyLocked: false };
}

export function getAuthorityNotice(tool: ToolId, currentMode: PrivilegeMode): string | null {
  if (currentMode === PrivilegeMode.ROOT) return null;
  const required = getRequiredAuthority(tool);
  if (isStrictlyRootLocked(tool)) {
    return 'Requires Root (su) authority. This tool modifies protected system partitions or Magisk/KernelSU modules.';
  }
  if (required === PrivilegeMode.ROOT && currentMode === PrivilegeMode.SHIZUKU) {
    return 'Operating with Shizuku: Hardware parameters can be monitored, but kernel sysfs writes require Root (su).';
  }
  if (required === PrivilegeMode.ROOT && currentMode === PrivilegeMode.BASIC) {
    return 'Operating in Basic mode: Read-only telemetry enabled. Grant Root to tune clocks and governors.';
  }
  if (required === PrivilegeMode.SHIZUKU && currentMode === PrivilegeMode.BASIC) {
    return 'Grant Shizuku permission or Root to execute elevated system commands for this feature.';
  }
  return null;
}

export const PRIVILEGE_MODE_LABEL: Record<PrivilegeMode, string> = {
  [PrivilegeMode.ROOT]: 'Root',
  [PrivilegeMode.SHIZUKU]: 'Shizuku',
  [PrivilegeMode.BASIC]: 'Monitor',
};

export const WORK_MODE_META: Record<WorkMode, { label: string; subtitle: string }> = {
  [WorkMode.AUTO]: { label: 'Auto', subtitle: 'Prefer Root -> fallback to Shizuku -> Basic' },
  [WorkMode.ROOT]: { label: 'Root (su)', subtitle: 'Full kernel sysfs, CPU/GPU, ZRAM, Resetprop & Modules' },
  [WorkMode.SHIZUKU]: { label: 'Shizuku', subtitle: 'Elevated ADB shell (Display, Apps, DNS, Battery, Anim)' },
  [WorkMode.BASIC]: { label: 'Basic', subtitle: 'Safe read-only telemetry & non-root battery monitor' },
};

export const SHIZUKU_STATUS_LABEL: Record<ShizukuStatus, string> = {
  [ShizukuStatus.NOT_INSTALLED]: 'Not Installed',
  [ShizukuStatus.NOT_RUNNING]: 'Service Stopped',
  [ShizukuStatus.PERMISSION_REQUIRED]: 'Permission Needed',
  [ShizukuStatus.GRANTED]: 'Granted',
};
