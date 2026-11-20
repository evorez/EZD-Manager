import React, { useState } from 'react';
import {
  Activity,
  AlertTriangle,
  BatteryCharging,
  Camera,
  ChevronDown,
  ChevronUp,
  Cpu,
  Edit3,
  Flame,
  Gauge,
  HardDrive,
  Layers,
  RefreshCw,
  ShieldCheck,
  Thermometer,
  Wifi,
  X,
  Zap,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import {
  GlassButton,
  GlassCard,
  OutlineButton,
  SectionHeader,
  StatusLabel,
  ToggleSwitch,
} from '../components/GlassUI';
import { BUILT_IN_PROFILES } from '../data/presets';
import { GameProfileId, MainTab, RootStatus, ToolId } from '../types/ezd';

export const HomeScreen: React.FC = () => {
  const {
    state,
    visuals,
    setActiveTab,
    openTool,
    setShowDeviceDetails,
    autoDetectRoot,
    refreshAllHardware,
    applyProfile,
    toggleBeastMode,
    setBeastThermalLimits,
    setBeastIgnoreThermalLimits,
    clearBootSafeMode,
    updateUserProfile,
  } = useEzd();

  const [editProfileOpen, setEditProfileOpen] = useState(false);
  const [draftName, setDraftName] = useState(state.settings.userName);
  const [draftTagline, setDraftTagline] = useState(state.settings.userTagline);
  const [draftPhoto, setDraftPhoto] = useState<string | null>(null);
  const [beastLimitsOpen, setBeastLimitsOpen] = useState(false);

  const isBeastActive = state.settings.activeProfileId === GameProfileId.BEAST;
  const ov = state.overview;
  const ramPct = Math.round((ov.ramUsedMb / Math.max(1, ov.ramTotalMb)) * 100);
  const allProfiles = [...BUILT_IN_PROFILES.filter((p) => p.id !== GameProfileId.BEAST), ...state.customProfiles];

  const handlePhotoPick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') setDraftPhoto(reader.result);
    };
    reader.readAsDataURL(f);
  };

  const quickShortcuts: { tool: ToolId; label: string; sub: string; icon: React.ReactNode }[] = [
    { tool: ToolId.ZRAM_VM, label: 'ZRAM & VM', sub: `${state.zramVmState.diskSizeMb} MB`, icon: <Layers className="w-4 h-4" /> },
    { tool: ToolId.KERNEL_TWEAKS, label: 'I/O & Thermal', sub: state.ioThermalUiState.thermalProfile, icon: <Flame className="w-4 h-4" /> },
    { tool: ToolId.BATTERY_SAVING, label: 'Battery Stats', sub: `${state.batteryMonitorStats.activeDrainPctPerHr}%/h`, icon: <BatteryCharging className="w-4 h-4" /> },
    { tool: ToolId.MODULES, label: 'Modules', sub: `${state.rootModules.filter((m) => m.enabled).length} active`, icon: <HardDrive className="w-4 h-4" /> },
    { tool: ToolId.NETWORK, label: 'Network', sub: state.networkState.tcpCongestion.toUpperCase(), icon: <Wifi className="w-4 h-4" /> },
    { tool: ToolId.APP_BACKUP, label: 'App Backup', sub: `${state.localAppBackups.length} saved`, icon: <Activity className="w-4 h-4" /> },
  ];

  return (
    <div className="space-y-4">
      {/* User Profile Greeting Card */}
      <GlassCard>
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3.5 min-w-0">
            <img
              src={state.settings.userPhotoDataUrl || '/assets/evorez_avatar.png'}
              alt={state.settings.userName}
              className="w-12 h-12 rounded-full object-cover border shrink-0"
              style={{ borderColor: visuals.accent }}
            />
            <div className="min-w-0">
              <div className="text-base font-bold truncate" style={{ color: visuals.textPrimary }}>
                {state.settings.userName}
              </div>
              <div className="text-xs truncate" style={{ color: visuals.textSecondary }}>
                {state.settings.userTagline} · Active Profile:{' '}
                <span className="font-semibold" style={{ color: visuals.accent }}>
                  {state.settings.activeProfileId.toUpperCase()}
                </span>
              </div>
            </div>
          </div>

          <OutlineButton
            onClick={() => {
              setDraftName(state.settings.userName);
              setDraftTagline(state.settings.userTagline);
              setDraftPhoto(null);
              setEditProfileOpen(true);
            }}
          >
            <Edit3 className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Edit</span>
          </OutlineButton>
        </div>
      </GlassCard>

      {/* Boot-Loop Safe Mode Warning Banner */}
      {state.bootGuardState.safeModeActive && (
        <GlassCard highlightBorder>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" style={{ color: visuals.warningColor }} />
              <div>
                <div className="text-sm font-bold" style={{ color: visuals.warningColor }}>
                  Safe Mode Active (Boot Guard)
                </div>
                <p className="text-xs mt-0.5" style={{ color: visuals.textSecondary }}>
                  {state.bootGuardState.lastReason || 'Boot Memory auto-apply paused after failed boot attempts.'}
                </p>
              </div>
            </div>
            <OutlineButton onClick={clearBootSafeMode}>Clear Safe Mode</OutlineButton>
          </div>
        </GlassCard>
      )}

      {/* Privilege Status Card */}
      <GlassCard>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div
              className="w-10 h-10 rounded-2xl border flex items-center justify-center shrink-0"
              style={{
                backgroundColor: `${visuals.accent}1A`,
                borderColor: `${visuals.accent}44`,
                color: visuals.accent,
              }}
            >
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
                  Authority: {state.effectivePrivilegeMode}
                </span>
                <StatusLabel
                  label={state.rootStatus === RootStatus.GRANTED ? state.rootManagerType : state.rootStatus}
                  color={state.rootStatus === RootStatus.GRANTED ? visuals.successColor : visuals.warningColor}
                />
                <StatusLabel
                  label={`SELinux: ${state.probeReport.selinuxMode}`}
                  color={visuals.accent}
                />
              </div>
              <p className="text-xs mt-0.5" style={{ color: visuals.textSecondary }}>
                Kernel {state.probeReport.kernelVersion} · {state.displayState.peakHardwareHz}Hz peak panel
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <OutlineButton onClick={() => autoDetectRoot(true)}>Verify Root</OutlineButton>
            <OutlineButton onClick={() => refreshAllHardware(true)} disabled={state.isRefreshingProbe}>
              <RefreshCw className={`w-3.5 h-3.5 ${state.isRefreshingProbe ? 'animate-spin' : ''}`} />
              <span>Probe</span>
            </OutlineButton>
          </div>
        </div>
      </GlassCard>

      {/* Device Overview Card */}
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <SectionHeader title="Device Overview" subtitle={`${ov.manufacturer} ${ov.model} (${ov.device}) · Android ${ov.androidVersion}`} />
          <OutlineButton onClick={() => setShowDeviceDetails(true)}>Specs</OutlineButton>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>CPU Load</div>
            <div className="mt-1 text-lg font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
              {ov.cpuUsagePercent ?? 0}%
            </div>
            <div className="text-[11px] font-mono" style={{ color: visuals.accent }}>
              {state.cpuCores.filter((c) => c.online).length}/{state.cpuCores.length} cores online
            </div>
          </div>

          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>GPU Clock</div>
            <div className="mt-1 text-lg font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
              {state.gpuState.currentMhz ?? 0} MHz
            </div>
            <div className="text-[11px] font-mono" style={{ color: visuals.accent }}>
              {state.gpuState.usagePercent ?? 0}% · {state.gpuState.vendorHint}
            </div>
          </div>

          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Memory (RAM)</div>
            <div className="mt-1 text-lg font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
              {ramPct}%
            </div>
            <div className="text-[11px] font-mono tabular-nums" style={{ color: visuals.textSecondary }}>
              {(ov.ramUsedMb / 1024).toFixed(1)} / {(ov.ramTotalMb / 1024).toFixed(0)} GB
            </div>
          </div>

          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Battery</div>
            <div className="mt-1 text-lg font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
              {ov.batteryPercent}%
            </div>
            <div className="text-[11px] font-mono tabular-nums" style={{ color: visuals.successColor }}>
              {state.thermalTelemetry.batteryTempC}°C · {state.displayState.currentRefreshHz}Hz
            </div>
          </div>
        </div>
      </GlassCard>

      {/* Mixed CPU & GPU Control Card */}
      <GlassCard>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
          <SectionHeader title="CPU & GPU Mixed Control" subtitle="Live cluster ceilings & instant profile switch" />
          <div className="flex items-center gap-2">
            <OutlineButton onClick={() => applyProfile(GameProfileId.BALANCED)}>Balanced</OutlineButton>
            <GlassButton text="Boost" icon={<Gauge className="w-3.5 h-3.5" />} onClick={() => applyProfile(GameProfileId.PERFORMANCE)} />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
          {state.cpuPolicies.map((pol) => (
            <div
              key={pol.id}
              className="rounded-2xl border p-3"
              style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold font-mono uppercase" style={{ color: visuals.accent }}>
                  {pol.id} (CPU {pol.cpus})
                </span>
                <span className="text-[11px] font-mono" style={{ color: visuals.textSecondary }}>
                  {pol.currentGovernor}
                </span>
              </div>
              <div className="mt-1.5 text-sm font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
                {Math.round((pol.currentKhz ?? 0) / 1000)} MHz
              </div>
              <div className="text-[11px] font-mono tabular-nums" style={{ color: visuals.textSecondary }}>
                Range: {Math.round((pol.minKhz ?? 0) / 1000)}–{Math.round((pol.maxKhz ?? 0) / 1000)} MHz
              </div>
            </div>
          ))}
        </div>

        <div className="mt-3 flex justify-end">
          <OutlineButton
            onClick={() => {
              setActiveTab(MainTab.TOOLS);
              openTool(ToolId.CPU_GPU_CONTROL);
            }}
          >
            <Cpu className="w-3.5 h-3.5" />
            <span>Open Full CPU & GPU Engine</span>
          </OutlineButton>
        </div>
      </GlassCard>

      {/* BEAST MODE Card */}
      <GlassCard highlightBorder={isBeastActive}>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <Zap className="w-5 h-5" style={{ color: isBeastActive ? visuals.errorColor : visuals.accent }} />
              <span className="text-base font-extrabold tracking-wide" style={{ color: visuals.textPrimary }}>
                𝙱𝙴𝙰𝚂𝚃 𝙼𝙾𝙳𝙴
              </span>
              <StatusLabel
                label={isBeastActive ? 'ARMED · 100% OPP' : 'STANDBY'}
                color={isBeastActive ? visuals.errorColor : visuals.textSecondary}
              />
            </div>
            <p className="text-xs" style={{ color: visuals.textSecondary }}>
              Locks 100% CPU & GPU clocks, forces 8 cores online, {state.displayState.peakHardwareHz}Hz peak refresh, Touch Boost & thermal headroom.
            </p>
          </div>

          <GlassButton
            text={isBeastActive ? 'DEACTIVATE' : 'ACTIVATE'}
            onClick={toggleBeastMode}
            accent={!isBeastActive}
            className="min-w-[140px]"
          />
        </div>

        {/* Live Thermal Watchdog Telemetry */}
        <div
          className="mt-3 rounded-2xl border p-3 flex flex-col gap-2.5"
          style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
        >
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <div className="flex items-center gap-3 text-xs font-mono tabular-nums">
              <span className="inline-flex items-center gap-1" style={{ color: visuals.textPrimary }}>
                <Thermometer className="w-3.5 h-3.5" style={{ color: visuals.warningColor }} />
                Bat: {state.thermalTelemetry.batteryTempC}°C (limit {state.settings.beastBatteryTempLimitC}°C)
              </span>
              <span style={{ color: visuals.textSecondary }}>|</span>
              <span style={{ color: visuals.textPrimary }}>
                SoC: {state.thermalTelemetry.socTempC ?? '--'}°C (limit {state.settings.beastSocTempLimitC}°C)
              </span>
            </div>

            <button
              type="button"
              onClick={() => setBeastLimitsOpen((v) => !v)}
              className="inline-flex items-center gap-1 text-xs font-semibold cursor-pointer"
              style={{ color: visuals.accent }}
            >
              <span>Thermal Watchdog</span>
              {beastLimitsOpen ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
            </button>
          </div>

          {beastLimitsOpen && (
            <div className="pt-2 border-t space-y-3" style={{ borderColor: visuals.glassBorder }}>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span style={{ color: visuals.textSecondary }}>Battery Step-Down Limit</span>
                    <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                      {state.settings.beastBatteryTempLimitC}°C
                    </span>
                  </div>
                  <input
                    type="range"
                    min={36}
                    max={50}
                    step={1}
                    value={state.settings.beastBatteryTempLimitC}
                    onChange={(e) => setBeastThermalLimits(Number(e.target.value), null)}
                    className="ezd-slider"
                  />
                </div>
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span style={{ color: visuals.textSecondary }}>SoC Step-Down Limit</span>
                    <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                      {state.settings.beastSocTempLimitC}°C
                    </span>
                  </div>
                  <input
                    type="range"
                    min={55}
                    max={95}
                    step={1}
                    value={state.settings.beastSocTempLimitC}
                    onChange={(e) => setBeastThermalLimits(null, Number(e.target.value))}
                    className="ezd-slider"
                  />
                </div>
              </div>

              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-xs font-semibold" style={{ color: visuals.textPrimary }}>
                    Ignore thermal limits
                  </div>
                  <div className="text-[11px]" style={{ color: visuals.textSecondary }}>
                    Disable automatic step-down to Balanced if temperature limits are reached.
                  </div>
                </div>
                <ToggleSwitch
                  checked={state.settings.beastIgnoreThermalLimits}
                  onChange={setBeastIgnoreThermalLimits}
                  ariaLabel="Ignore thermal limits"
                />
              </div>
            </div>
          )}
        </div>

        {/* Beast Execution Report */}
        {state.beastReport && isBeastActive && (
          <div className="mt-3 space-y-1.5">
            <div className="text-xs font-semibold" style={{ color: visuals.successColor }}>
              {state.beastReport.summary}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
              {state.beastReport.items.map((item, idx) => (
                <div
                  key={idx}
                  className="rounded-xl border px-3 py-2 flex items-center justify-between gap-2 text-xs"
                  style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
                >
                  <div className="min-w-0">
                    <div className="font-semibold truncate" style={{ color: visuals.textPrimary }}>
                      {item.category}: {item.label}
                    </div>
                    <div className="text-[11px] truncate" style={{ color: visuals.textSecondary }}>
                      {item.reason}
                    </div>
                  </div>
                  <StatusLabel label={item.status} color={visuals.successColor} />
                </div>
              ))}
            </div>
          </div>
        )}
      </GlassCard>

      {/* Performance Profiles Strip */}
      <GlassCard>
        <SectionHeader title="Performance Profiles" subtitle="Tap to apply system-wide CPU, GPU, refresh & touch targets" />
        <div className="mt-3 grid grid-cols-1 sm:grid-cols-3 gap-2.5">
          {allProfiles.map((prof) => {
            const active = state.settings.activeProfileId === prof.id;
            return (
              <button
                key={prof.id}
                type="button"
                onClick={() => applyProfile(prof)}
                className="rounded-2xl border p-3.5 text-left transition-colors cursor-pointer hover:brightness-110"
                style={{
                  backgroundColor: active ? `${visuals.accent}1F` : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                }}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-bold truncate" style={{ color: visuals.textPrimary }}>
                    {prof.name}
                  </span>
                  {active && <StatusLabel label="ACTIVE" color={visuals.accent} />}
                </div>
                <p className="mt-1 text-xs line-clamp-2" style={{ color: visuals.textSecondary }}>
                  {prof.description}
                </p>
              </button>
            );
          })}
        </div>
      </GlassCard>

      {/* Quick Actions Strip */}
      <GlassCard>
        <SectionHeader title="Quick Actions" subtitle="Jump straight into core tuning subsystems" />
        <div className="mt-3 grid grid-cols-2 sm:grid-cols-3 gap-2.5">
          {quickShortcuts.map((item) => (
            <button
              key={item.tool}
              type="button"
              onClick={() => {
                setActiveTab(MainTab.TOOLS);
                openTool(item.tool);
              }}
              className="rounded-2xl border p-3 text-left flex items-center gap-3 transition-colors hover:brightness-110 cursor-pointer"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
              }}
            >
              <div
                className="w-9 h-9 rounded-xl border flex items-center justify-center shrink-0"
                style={{
                  backgroundColor: `${visuals.accent}1A`,
                  borderColor: `${visuals.accent}40`,
                  color: visuals.accent,
                }}
              >
                {item.icon}
              </div>
              <div className="min-w-0">
                <div className="text-xs font-bold truncate" style={{ color: visuals.textPrimary }}>
                  {item.label}
                </div>
                <div className="text-[11px] font-mono truncate" style={{ color: visuals.textSecondary }}>
                  {item.sub}
                </div>
              </div>
            </button>
          ))}
        </div>
      </GlassCard>

      {/* Edit Profile Modal */}
      {editProfileOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          onClick={() => setEditProfileOpen(false)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-md rounded-[26px] border p-5 shadow-2xl space-y-4"
            style={{ backgroundColor: visuals.solidSurfaceElevated, borderColor: visuals.glassBorder }}
          >
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold" style={{ color: visuals.textPrimary }}>
                Edit Profile
              </h3>
              <button
                type="button"
                onClick={() => setEditProfileOpen(false)}
                className="p-1.5 rounded-full cursor-pointer"
                style={{ color: visuals.textSecondary }}
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex items-center gap-4">
              <label className="relative cursor-pointer shrink-0">
                <input type="file" accept="image/*" onChange={handlePhotoPick} className="hidden" />
                <img
                  src={draftPhoto || state.settings.userPhotoDataUrl || '/assets/evorez_avatar.png'}
                  alt="Avatar preview"
                  className="w-16 h-16 rounded-full object-cover border-2"
                  style={{ borderColor: visuals.accent }}
                />
                <span
                  className="absolute -bottom-1 -right-1 p-1 rounded-full"
                  style={{ backgroundColor: visuals.accent, color: visuals.accentOnColor }}
                >
                  <Camera className="w-3 h-3" />
                </span>
              </label>

              <div className="flex-1 space-y-2.5">
                <input
                  type="text"
                  value={draftName}
                  onChange={(e) => setDraftName(e.target.value)}
                  placeholder="Display Name"
                  className="w-full rounded-xl border px-3 py-2 text-sm outline-none"
                  style={{
                    backgroundColor: visuals.solidSurfaceBase,
                    borderColor: visuals.glassBorder,
                    color: visuals.textPrimary,
                  }}
                />
                <input
                  type="text"
                  value={draftTagline}
                  onChange={(e) => setDraftTagline(e.target.value)}
                  placeholder="Tagline"
                  className="w-full rounded-xl border px-3 py-2 text-sm outline-none"
                  style={{
                    backgroundColor: visuals.solidSurfaceBase,
                    borderColor: visuals.glassBorder,
                    color: visuals.textPrimary,
                  }}
                />
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <OutlineButton onClick={() => setEditProfileOpen(false)}>Cancel</OutlineButton>
              <GlassButton
                text="Save Profile"
                onClick={() => {
                  updateUserProfile(draftName, draftTagline, draftPhoto);
                  setEditProfileOpen(false);
                }}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
