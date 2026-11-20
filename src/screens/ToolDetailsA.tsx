import React, { useState } from 'react';
import {
  BatteryCharging,
  Check,
  Monitor,
  MousePointerClick,
  Music2,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Wifi,
  Zap,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { AudioEnhancerVisualizer, DnsProviderLogo } from '../components/CustomVisuals';
import {
  AppIconBadge,
  GlassButton,
  GlassCard,
  OutlineButton,
  SectionHeader,
  StatusLabel,
  ToggleSwitch,
} from '../components/GlassUI';
import { AUDIO_PRESET_BANDS, DNS_PRESETS } from '../data/presets';
import { AudioVisualStyle, PrivilegeMode } from '../types/ezd';

export const DisplayPanel: React.FC = () => {
  const {
    state,
    visuals,
    setRefreshRate,
    resetRefreshRate,
    setBrightness,
    setHbm,
    setDisplaySaturation,
    setCabc,
  } = useEzd();

  const d = state.displayState;
  const canElevated =
    state.effectivePrivilegeMode === PrivilegeMode.ROOT ||
    state.effectivePrivilegeMode === PrivilegeMode.SHIZUKU;

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Monitor className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader
              title="Refresh Rate"
              subtitle={`Active: ${d.currentRefreshHz} Hz · Peak Panel: ${d.peakHardwareHz} Hz`}
            />
          </div>
          <OutlineButton onClick={resetRefreshRate} disabled={!canElevated}>
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset</span>
          </OutlineButton>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {d.supportedModes.map((mode) => {
            const active = d.currentRefreshHz === mode.refreshRateHz;
            return (
              <button
                key={mode.modeId}
                type="button"
                disabled={!canElevated}
                onClick={() => setRefreshRate(mode.refreshRateHz)}
                className="rounded-2xl border p-3 text-center transition-colors cursor-pointer disabled:opacity-50"
                style={{
                  backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                  color: active ? visuals.accentOnColor : visuals.textPrimary,
                }}
              >
                <div className="text-base font-bold font-mono tabular-nums">{mode.refreshRateHz} Hz</div>
                <div className="text-[10px] opacity-80">
                  {mode.width}×{mode.height}
                </div>
              </button>
            );
          })}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Brightness & Panel Controls" subtitle="Sysfs HBM, CABC & color saturation" />
        <div className="mt-3 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold" style={{ color: visuals.textPrimary }}>
              Adaptive Auto-Brightness
            </span>
            <ToggleSwitch
              checked={d.autoBrightness}
              onChange={(auto) => setBrightness(d.brightness, auto)}
            />
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <span style={{ color: visuals.textSecondary }}>Panel Brightness (0–255)</span>
              <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                {d.brightness} ({Math.round((d.brightness / 255) * 100)}%)
              </span>
            </div>
            <input
              type="range"
              min={5}
              max={255}
              value={d.brightness}
              onChange={(e) => setBrightness(Number(e.target.value), false)}
              className="ezd-slider"
            />
          </div>

          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                High Brightness Mode (HBM)
              </div>
              <div className="text-[11px] font-mono" style={{ color: visuals.textSecondary }}>
                {d.hbmPath ?? 'Unsupported'}
              </div>
            </div>
            <ToggleSwitch
              checked={d.hbmEnabled}
              disabled={!d.hbmSupported || state.effectivePrivilegeMode !== PrivilegeMode.ROOT}
              onChange={setHbm}
            />
          </div>

          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                CABC Power Dimming
              </div>
              <div className="text-[11px]" style={{ color: visuals.textSecondary }}>
                Content Adaptive Backlight Control ({d.cabcPath})
              </div>
            </div>
            <ToggleSwitch
              checked={d.cabcEnabled}
              disabled={!d.cabcSupported || state.effectivePrivilegeMode !== PrivilegeMode.ROOT}
              onChange={setCabc}
            />
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <span style={{ color: visuals.textSecondary }}>Display Color Saturation (SurfaceFlinger / KCAL)</span>
              <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                {d.saturationValue.toFixed(2)}×
              </span>
            </div>
            <input
              type="range"
              min={0.5}
              max={1.8}
              step={0.05}
              value={d.saturationValue}
              onChange={(e) => setDisplaySaturation(Number(e.target.value))}
              className="ezd-slider"
            />
          </div>
        </div>
      </GlassCard>
    </div>
  );
};

export const TouchPanel: React.FC = () => {
  const { state, visuals, setTouchControl, setDisableHwOverlays } = useEzd();
  const t = state.touchState;
  const isRoot = state.effectivePrivilegeMode === PrivilegeMode.ROOT;

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center gap-2 mb-3">
          <MousePointerClick className="w-4 h-4" style={{ color: visuals.accent }} />
          <SectionHeader title="Touchpanel & SurfaceFlinger" subtitle={t.statusNote} />
        </div>

        <div className="space-y-4">
          <div>
            <div className="text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
              Touch Report / Sampling Rate ({t.samplingValue} Hz)
            </div>
            <div className="grid grid-cols-3 gap-2">
              {['120', '240', '480'].map((rate) => {
                const active = t.samplingValue === rate;
                return (
                  <button
                    key={rate}
                    type="button"
                    disabled={!isRoot}
                    onClick={() =>
                      setTouchControl(t.samplingNodePath ?? '', rate, 'Touch Sampling Rate')
                    }
                    className="rounded-xl border py-2 text-xs font-bold font-mono cursor-pointer disabled:opacity-50"
                    style={{
                      backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                      borderColor: active ? visuals.accent : visuals.glassBorder,
                      color: active ? visuals.accentOnColor : visuals.textPrimary,
                    }}
                  >
                    {rate} Hz
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Touchpanel Gaming Mode
              </div>
              <div className="text-[11px] font-mono" style={{ color: visuals.textSecondary }}>
                {t.gamingModePath}
              </div>
            </div>
            <ToggleSwitch
              checked={t.gamingModeEnabled}
              disabled={!isRoot}
              onChange={(val) =>
                setTouchControl(t.gamingModePath ?? '', val ? '1' : '0', 'Touch Gaming Mode')
              }
            />
          </div>

          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Kernel Touch Boost
              </div>
              <div className="text-[11px] font-mono" style={{ color: visuals.textSecondary }}>
                {t.touchBoostPath}
              </div>
            </div>
            <ToggleSwitch
              checked={t.touchBoostEnabled}
              disabled={!isRoot}
              onChange={(val) =>
                setTouchControl(t.touchBoostPath ?? '', val ? '1' : '0', 'Kernel Touch Boost')
              }
            />
          </div>

          <div className="flex items-center justify-between gap-3 pt-2 border-t" style={{ borderColor: visuals.glassBorder }}>
            <div>
              <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Disable HW Overlays (Force GPU Composition)
              </div>
              <div className="text-[11px]" style={{ color: visuals.textSecondary }}>
                Routes all window layers through GLES/Vulkan GPU instead of Hardware Composer.
              </div>
            </div>
            <ToggleSwitch
              checked={t.hwOverlaysDisabled}
              disabled={state.effectivePrivilegeMode === PrivilegeMode.BASIC}
              onChange={setDisableHwOverlays}
            />
          </div>
        </div>
      </GlassCard>
    </div>
  );
};

export const NetworkPanel: React.FC = () => {
  const {
    state,
    visuals,
    resetNetworkSettings,
    applyTcpFastPreset,
    applyTcpRescuePreset,
    setTcpCongestion,
    applyDnsPreset,
  } = useEzd();

  const n = state.networkState;
  const isRoot = state.effectivePrivilegeMode === PrivilegeMode.ROOT;
  const canShell = state.effectivePrivilegeMode !== PrivilegeMode.BASIC;

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Wifi className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader
              title="TCP Stack & Congestion Control"
              subtitle={`${n.interfaceName} (${n.transportType}) · TFO=${n.tcpFastOpenValue}`}
            />
          </div>
          <OutlineButton onClick={resetNetworkSettings} disabled={!canShell}>
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset</span>
          </OutlineButton>
        </div>

        <div className="flex flex-wrap gap-2 mb-3">
          <GlassButton text="Apply TCP Fast (BBR + TFO)" onClick={applyTcpFastPreset} disabled={!isRoot} />
          <OutlineButton onClick={applyTcpRescuePreset} disabled={!isRoot}>
            TCP Rescue (Cubic)
          </OutlineButton>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {n.availableTcp.map((algo) => {
            const active = n.tcpCongestion === algo;
            return (
              <button
                key={algo}
                type="button"
                disabled={!isRoot}
                onClick={() => setTcpCongestion(algo)}
                className="rounded-xl border py-2 text-xs font-bold font-mono uppercase cursor-pointer disabled:opacity-50"
                style={{
                  backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                  color: active ? visuals.accentOnColor : visuals.textPrimary,
                }}
              >
                {algo}
              </button>
            );
          })}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader
          title="System Private DNS (DoT)"
          subtitle={`Mode: ${n.privateDnsMode} · ${n.privateDnsSpecifier || 'Automatic'}`}
        />
        <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-2.5">
          {DNS_PRESETS.map((preset) => {
            const active =
              (preset.id === 'off' && n.privateDnsMode === 'off') ||
              (preset.hostname && n.privateDnsSpecifier === preset.hostname);
            return (
              <button
                key={preset.id}
                type="button"
                disabled={!canShell}
                onClick={() => applyDnsPreset(preset)}
                className="flex items-center justify-between gap-3 rounded-2xl border p-3 text-left transition-colors cursor-pointer disabled:opacity-50"
                style={{
                  backgroundColor: active ? `${visuals.accent}1E` : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                }}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <DnsProviderLogo providerId={preset.id} size={32} />
                  <div className="min-w-0">
                    <div className="text-xs font-bold truncate" style={{ color: visuals.textPrimary }}>
                      {preset.name}
                    </div>
                    <div className="text-[11px] font-mono truncate" style={{ color: visuals.textSecondary }}>
                      {preset.hostname || 'Carrier / Router Auto'}
                    </div>
                  </div>
                </div>
                {active && <Check className="w-4 h-4 shrink-0" style={{ color: visuals.accent }} />}
              </button>
            );
          })}
        </div>
      </GlassCard>
    </div>
  );
};

export const AudioEnhancerPanel: React.FC = () => {
  const {
    state,
    visuals,
    setAudioVisualStyle,
    toggleBassEnhancer,
    setBassFrequencyFocus,
    toggleIncreaseLoudness,
    toggleEqualizer,
    selectAudioPreset,
    updateAudioManual,
  } = useEzd();

  const a = state.audioState;

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Music2 className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="Live DSP Spectrum Visualizer" subtitle={a.statusNote} />
          </div>
          <StatusLabel label={a.activePreset} color={visuals.accent} />
        </div>

        <AudioEnhancerVisualizer
          style={a.visualStyle}
          bandLevels={a.bandLevels}
          bassStrength={a.bassStrength}
          loudnessGainMb={a.loudnessGainMb}
          bassEnabled={a.bassEnabled}
          eqEnabled={a.equalizerEnabled}
          loudnessEnabled={a.increaseLoudnessEnabled}
        />

        <div className="mt-3 grid grid-cols-2 sm:grid-cols-4 gap-2">
          {Object.values(AudioVisualStyle).map((vs) => {
            const active = a.visualStyle === vs;
            return (
              <button
                key={vs}
                type="button"
                onClick={() => setAudioVisualStyle(vs)}
                className="rounded-xl border px-2.5 py-2 text-[11px] font-bold cursor-pointer"
                style={{
                  backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                  color: active ? visuals.accentOnColor : visuals.textPrimary,
                }}
              >
                {vs.replace('_', ' ')}
              </button>
            );
          })}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Bass Enhancer & Loudness Stage" />
        <div className="mt-3 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
              Independent Sub-Bass Enhancer
            </span>
            <ToggleSwitch checked={a.bassEnabled} onChange={toggleBassEnhancer} />
          </div>

          <div className="grid grid-cols-3 gap-2">
            {['Sub 40Hz', 'Punch 60Hz', 'Warm 90Hz'].map((focus) => {
              const active = a.bassFrequencyFocus === focus;
              return (
                <button
                  key={focus}
                  type="button"
                  onClick={() => setBassFrequencyFocus(focus)}
                  className="rounded-xl border py-2 text-xs font-semibold cursor-pointer"
                  style={{
                    backgroundColor: active ? `${visuals.accent}24` : visuals.solidSurfaceBase,
                    borderColor: active ? visuals.accent : visuals.glassBorder,
                    color: visuals.textPrimary,
                  }}
                >
                  {focus}
                </button>
              );
            })}
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <span style={{ color: visuals.textSecondary }}>Bass Strength</span>
              <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                {Math.round(a.bassStrength / 10)}%
              </span>
            </div>
            <input
              type="range"
              min={0}
              max={1000}
              step={25}
              value={a.bassStrength}
              onChange={(e) => updateAudioManual(a.bandLevels, Number(e.target.value), a.loudnessGainMb)}
              className="ezd-slider"
            />
          </div>

          <div className="pt-2 border-t space-y-3" style={{ borderColor: visuals.glassBorder }}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Increase Loudness (Pre-Amp Gain)
              </span>
              <ToggleSwitch checked={a.increaseLoudnessEnabled} onChange={toggleIncreaseLoudness} />
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1">
                <span style={{ color: visuals.textSecondary }}>Loudness Boost</span>
                <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                  +{(a.loudnessGainMb / 100).toFixed(1)} dB
                </span>
              </div>
              <input
                type="range"
                min={0}
                max={1200}
                step={50}
                value={a.loudnessGainMb}
                onChange={(e) => updateAudioManual(a.bandLevels, a.bassStrength, Number(e.target.value))}
                className="ezd-slider"
              />
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <SectionHeader title="5-Band Equalizer" />
          <ToggleSwitch checked={a.equalizerEnabled} onChange={toggleEqualizer} />
        </div>

        <div className="flex flex-wrap gap-2 mb-4">
          {Object.keys(AUDIO_PRESET_BANDS).map((presetName) => {
            const active = a.activePreset === presetName;
            return (
              <button
                key={presetName}
                type="button"
                onClick={() => selectAudioPreset(presetName)}
                className="rounded-xl border px-3 py-1.5 text-xs font-semibold cursor-pointer"
                style={{
                  backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                  color: active ? visuals.accentOnColor : visuals.textPrimary,
                }}
              >
                {presetName}
              </button>
            );
          })}
        </div>

        <div className="space-y-3">
          {a.bandsHz.map((hz, idx) => {
            const level = a.bandLevels[idx] ?? 0;
            return (
              <div key={hz}>
                <div className="flex justify-between text-xs mb-1">
                  <span className="font-mono" style={{ color: visuals.textSecondary }}>
                    {hz >= 1000 ? `${hz / 1000} kHz` : `${hz} Hz`}
                  </span>
                  <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                    {level >= 0 ? `+${(level / 100).toFixed(1)}` : (level / 100).toFixed(1)} dB
                  </span>
                </div>
                <input
                  type="range"
                  min={-1500}
                  max={1500}
                  step={50}
                  value={level}
                  onChange={(e) => {
                    const next = [...a.bandLevels];
                    next[idx] = Number(e.target.value);
                    updateAudioManual(next, a.bassStrength, a.loudnessGainMb);
                  }}
                  className="ezd-slider"
                />
              </div>
            );
          })}
        </div>
      </GlassCard>
    </div>
  );
};

export const BatterySavingPanel: React.FC = () => {
  const {
    state,
    visuals,
    resetBatteryMonitorStats,
    applyGmsBackgroundManagement,
    applyBackgroundAppLimit,
    runSafeMemoryTrim,
    toggleWhitelistApp,
    setAppBackgroundRestriction,
  } = useEzd();

  const [query, setQuery] = useState('');
  const bm = state.batteryMonitorStats;
  const bs = state.batterySavingState;

  const formatDuration = (ms: number) => {
    const mins = Math.floor(ms / 60000);
    const hrs = Math.floor(mins / 60);
    return `${hrs}h ${mins % 60}m`;
  };

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <BatteryCharging className="w-4 h-4" style={{ color: visuals.successColor }} />
            <SectionHeader title="Live Battery & Deep Sleep Telemetry" subtitle={bs.lastActionSummary} />
          </div>
          <OutlineButton onClick={resetBatteryMonitorStats}>
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset</span>
          </OutlineButton>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Active Drain</div>
            <div className="text-base font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
              {bm.activeDrainPctPerHr}% / hr
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Idle Drain</div>
            <div className="text-base font-bold font-mono tabular-nums" style={{ color: visuals.successColor }}>
              {bm.idleDrainPctPerHr}% / hr
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Screen On</div>
            <div className="text-base font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
              {formatDuration(bm.screenOnMs)}
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Deep Sleep</div>
            <div className="text-base font-bold font-mono tabular-nums" style={{ color: visuals.accent }}>
              {bm.deepSleepPercent}%
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="GMS & Background Optimization" />
        <div className="mt-3 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Non-Essential GMS Standby
              </div>
              <div className="text-[11px]" style={{ color: visuals.textSecondary }}>
                Reduces background Play Services wake locks without breaking push notifications.
              </div>
            </div>
            <ToggleSwitch checked={bs.gmsStandbyApplied} onChange={applyGmsBackgroundManagement} />
          </div>

          <div>
            <div className="text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
              Background Process Limit
            </div>
            <div className="grid grid-cols-3 gap-2">
              {['Stock', 'Balanced', 'Strict (4 procs)'].map((preset) => {
                const active = bs.backgroundLimitPreset === preset;
                return (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => applyBackgroundAppLimit(preset)}
                    className="rounded-xl border py-2 text-xs font-semibold cursor-pointer"
                    style={{
                      backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                      borderColor: active ? visuals.accent : visuals.glassBorder,
                      color: active ? visuals.accentOnColor : visuals.textPrimary,
                    }}
                  >
                    {preset}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex justify-end">
            <GlassButton text="Run Safe Memory Trim" onClick={runSafeMemoryTrim} />
          </div>
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader
          title="Whitelist & Background Restriction"
          subtitle={`${bs.whitelistCount} protected · ${bs.restrictedCount} restricted`}
        />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Filter installed apps..."
          className="mt-3 w-full rounded-xl border px-3 py-2 text-xs outline-none"
          style={{
            backgroundColor: visuals.solidSurfaceBase,
            borderColor: visuals.glassBorder,
            color: visuals.textPrimary,
          }}
        />
        <div className="mt-3 space-y-2 max-h-72 overflow-y-auto ezd-scroll-thin">
          {state.installedApps
            .filter(
              (a) =>
                !query.trim() ||
                a.label.toLowerCase().includes(query.toLowerCase()) ||
                a.packageName.toLowerCase().includes(query.toLowerCase())
            )
            .map((app) => {
              const wl = state.batteryWhitelist.includes(app.packageName);
              const restricted = Boolean(state.perAppConfigs[app.packageName]?.batteryRestricted);
              return (
                <div
                  key={app.packageName}
                  className="rounded-xl border px-3 py-2 flex items-center justify-between gap-2"
                  style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <AppIconBadge label={app.label} colorHex={app.colorHex} size={34} />
                    <div className="min-w-0">
                      <div className="text-xs font-bold truncate" style={{ color: visuals.textPrimary }}>
                        {app.label}
                      </div>
                      <div className="text-[10px] font-mono truncate" style={{ color: visuals.textSecondary }}>
                        {app.packageName}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => toggleWhitelistApp(app.packageName)}
                      className="rounded-lg border px-2 py-1 text-[11px] font-semibold cursor-pointer"
                      style={{
                        backgroundColor: wl ? visuals.successColor : visuals.solidSurfaceElevated,
                        borderColor: wl ? visuals.successColor : visuals.glassBorder,
                        color: wl ? '#052E16' : visuals.textSecondary,
                      }}
                    >
                      <ShieldCheck className="w-3 h-3 inline mr-1" />
                      {wl ? 'Protected' : 'Protect'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setAppBackgroundRestriction(app.packageName, !restricted)}
                      className="rounded-lg border px-2 py-1 text-[11px] font-semibold cursor-pointer"
                      style={{
                        backgroundColor: restricted ? visuals.warningColor : visuals.solidSurfaceElevated,
                        borderColor: restricted ? visuals.warningColor : visuals.glassBorder,
                        color: restricted ? '#1F1300' : visuals.textSecondary,
                      }}
                    >
                      {restricted ? 'Restricted' : 'Restrict'}
                    </button>
                  </div>
                </div>
              );
            })}
        </div>
      </GlassCard>
    </div>
  );
};

export const FastChargePanel: React.FC = () => {
  const {
    state,
    visuals,
    refreshFastCharge,
    applyFastChargeLimit,
    toggleForceFastCharge,
  } = useEzd();

  const fc = state.fastChargeState;
  const isRoot = state.effectivePrivilegeMode === PrivilegeMode.ROOT;

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Zap className="w-4 h-4" style={{ color: visuals.warningColor }} />
            <SectionHeader title="Fast Charge & Current Monitor" subtitle={fc.reason} />
          </div>
          <OutlineButton onClick={refreshFastCharge}>
            <RefreshCw className="w-3.5 h-3.5" />
            <span>{fc.isCharging ? 'Simulate Unplug' : 'Simulate Charger'}</span>
          </OutlineButton>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Status</div>
            <div className="text-sm font-bold mt-0.5" style={{ color: fc.isCharging ? visuals.successColor : visuals.textPrimary }}>
              {fc.chargeSource}
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Current</div>
            <div className="text-sm font-bold font-mono tabular-nums mt-0.5" style={{ color: visuals.textPrimary }}>
              {fc.currentMa} mA
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Voltage</div>
            <div className="text-sm font-bold font-mono tabular-nums mt-0.5" style={{ color: visuals.textPrimary }}>
              {(fc.voltageMv / 1000).toFixed(2)} V
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Power</div>
            <div className="text-sm font-bold font-mono tabular-nums mt-0.5" style={{ color: visuals.accent }}>
              {fc.powerWatts} W
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Charge Current Limit & USB Force Fast Charge" />
        <div className="mt-3 space-y-4">
          <div>
            <div className="flex justify-between text-xs mb-1">
              <span style={{ color: visuals.textSecondary }}>Max Input Current Ceiling</span>
              <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                {fc.activeLimitMa ?? 3300} mA
              </span>
            </div>
            <input
              type="range"
              disabled={!isRoot}
              min={1000}
              max={6000}
              step={250}
              value={fc.activeLimitMa ?? 3300}
              onChange={(e) => applyFastChargeLimit(Number(e.target.value))}
              className="ezd-slider"
            />
            <div className="mt-2 flex flex-wrap gap-2">
              {[1500, 2400, 3300, 4500, 6000].map((ma) => (
                <OutlineButton key={ma} disabled={!isRoot} onClick={() => applyFastChargeLimit(ma)}>
                  {ma} mA
                </OutlineButton>
              ))}
            </div>
          </div>

          <div className="flex items-center justify-between gap-3 pt-2 border-t" style={{ borderColor: visuals.glassBorder }}>
            <div>
              <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Force Fast Charge (USB 2.0 / PC Ports)
              </div>
              <div className="text-[11px] font-mono" style={{ color: visuals.textSecondary }}>
                {fc.forceFastChargePath}
              </div>
            </div>
            <ToggleSwitch
              checked={fc.forceFastChargeEnabled}
              disabled={!isRoot}
              onChange={toggleForceFastCharge}
            />
          </div>
        </div>
      </GlassCard>
    </div>
  );
};
