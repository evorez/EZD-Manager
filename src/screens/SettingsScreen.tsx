import React, { useState } from 'react';
import {
  Bell,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Copy,
  Download,
  ExternalLink,
  HardDrive,
  Moon,
  RefreshCw,
  Shield,
  Sparkles,
  Sun,
  Terminal,
  X,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import {
  CollapsibleSection,
  GlassButton,
  GlassCard,
  OutlineButton,
  SectionHeader,
  StatusLabel,
  ToggleSwitch,
} from '../components/GlassUI';
import { TOOLS_META } from '../data/presets';
import { WORK_MODE_META } from '../data/workMode';
import {
  ACCENT_PRESETS_META,
  FONT_STYLES_META,
  TEXTURE_PRESETS_META,
  THEME_STYLES_META,
} from '../theme/themes';
import {
  MainTab,
  RootStatus,
  ShizukuStatus,
  ThemeCategory,
  ThemeMode,
  ToolId,
  WorkMode,
} from '../types/ezd';

export const SettingsScreen: React.FC = () => {
  const {
    state,
    daemonStats,
    visuals,
    setActiveTab,
    openTool,
    recordBrandLogoTap,
    setThemeMode,
    setThemeStyle,
    setAccentPreset,
    setBlurIntensity,
    setReduceAnimations,
    setFloatingPillOpaque,
    setBackgroundTexture,
    setBackgroundTextureOpacity,
    setAppFontStyle,
    movePinnedPillTool,
    unpinToolFromPill,
    setPrivilegeSimulation,
    exportAndCopyDiagnostics,
    setApplyOnBoot,
    captureBootMemorySnapshot,
    applyBootMemoryNow,
    clearBootSafeMode,
    setNotificationPrefs,
    toggleDaemonService,
    checkForGitHubUpdates,
    builtInChangelog,
  } = useEzd();

  const [appearanceOpen, setAppearanceOpen] = useState(true);
  const [permissionsOpen, setPermissionsOpen] = useState(false);
  const [daemonLogsOpen, setDaemonLogsOpen] = useState(false);
  const [changelogOpen, setChangelogOpen] = useState(false);

  const toggleQuickDarkLight = () => {
    setThemeMode(visuals.isDark ? ThemeMode.LIGHT : ThemeMode.DARK);
  };

  const permList = [
    { label: 'Root (su) Authority', granted: state.rootStatus === RootStatus.GRANTED },
    { label: 'Shizuku ADB Binder', granted: state.shizukuStatus === ShizukuStatus.GRANTED },
    { label: 'UsageStats Foreground Monitor', granted: state.usageAccessGranted },
    { label: 'WRITE_SETTINGS & Notifications', granted: state.writeSettingsGranted && state.notificationsGranted },
  ];
  const grantedCount = permList.filter((p) => p.granted).length;

  return (
    <div className="space-y-4">
      {/* Settings Header & Quick Dark/Light Switch */}
      <GlassCard>
        <div className="flex items-center justify-between gap-3">
          <SectionHeader title="EZD Control Settings" subtitle="Themes, privilege engine, daemon & diagnostics" />
          <OutlineButton onClick={toggleQuickDarkLight}>
            {visuals.isDark ? <Sun className="w-3.5 h-3.5" /> : <Moon className="w-3.5 h-3.5" />}
            <span>{visuals.isDark ? 'Light Mode' : 'Dark Mode'}</span>
          </OutlineButton>
        </div>
      </GlassCard>

      {/* Evorez Author Profile Card (7-tap Easter Egg) */}
      <GlassCard>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <button
              type="button"
              onClick={recordBrandLogoTap}
              title="Tap 7 times to open Reporting & Requesting"
              className="rounded-full shrink-0 cursor-pointer transition-transform active:scale-95"
            >
              <img
                src="/assets/evorez_avatar.png"
                alt="Evorez"
                className="w-14 h-14 rounded-full object-cover border-2"
                style={{ borderColor: visuals.accent }}
              />
            </button>
            <div>
              <div className="text-base font-bold" style={{ color: visuals.textPrimary }}>
                𝙴𝚟𝚘𝚛𝚎𝚣🪽
              </div>
              <div className="text-xs font-mono" style={{ color: visuals.accent }}>
                Evolve & Rise · Creator & Maintainer
              </div>
              <div className="text-[11px] mt-0.5" style={{ color: visuals.textSecondary }}>
                Tap avatar 7x for bug reports & feature requests
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <a
              href="https://github.com/evorez/EZD-Manager"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 rounded-full border px-3.5 py-2 text-xs font-semibold hover:brightness-110"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
                color: visuals.textPrimary,
              }}
            >
              <span>GitHub</span>
              <ExternalLink className="w-3 h-3" />
            </a>
            <a
              href="https://t.me/Evorez"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 rounded-full border px-3.5 py-2 text-xs font-semibold hover:brightness-110"
              style={{
                backgroundColor: visuals.accent,
                borderColor: visuals.accent,
                color: visuals.accentOnColor,
              }}
            >
              <span>Telegram</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>
      </GlassCard>

      {/* Collapsible Appearance, Themes, Textures & Fonts */}
      <GlassCard>
        <CollapsibleSection
          title="Appearance, Themes, Textures & Fonts"
          subtitle={`14 Themes · Active: ${
            THEME_STYLES_META.find((t) => t.style === state.settings.themeStyle)?.displayName ?? state.settings.themeStyle
          }`}
          expanded={appearanceOpen}
          onToggle={() => setAppearanceOpen((v) => !v)}
          trailingBadge={state.settings.themeMode}
        >
          <div className="space-y-4">
            {/* Theme Mode (Dark / Light / System / Lite) */}
            <div>
              <div className="text-xs font-bold mb-2" style={{ color: visuals.textSecondary }}>
                Theme Engine Mode
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {Object.values(ThemeMode).map((mode) => {
                  const active = state.settings.themeMode === mode;
                  return (
                    <button
                      key={mode}
                      type="button"
                      onClick={() => setThemeMode(mode)}
                      className="rounded-xl border px-3 py-2 text-xs font-bold cursor-pointer"
                      style={{
                        backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                        borderColor: active ? visuals.accent : visuals.glassBorder,
                        color: active ? visuals.accentOnColor : visuals.textPrimary,
                      }}
                    >
                      {mode === ThemeMode.LITE ? 'LITE (NO BLUR)' : mode}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 14 Theme Styles grouped by White / Black / Special */}
            {[ThemeCategory.WHITE, ThemeCategory.BLACK, ThemeCategory.SPECIAL].map((cat) => (
              <div key={cat} className="space-y-2">
                <div className="text-[11px] font-bold uppercase tracking-wider" style={{ color: visuals.accent }}>
                  {cat} Series
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {THEME_STYLES_META.filter((m) => m.category === cat).map((meta) => {
                    const selected = state.settings.themeStyle === meta.style;
                    return (
                      <button
                        key={meta.style}
                        type="button"
                        onClick={() => setThemeStyle(meta.style)}
                        className="rounded-2xl border p-3 text-left transition-colors cursor-pointer"
                        style={{
                          backgroundColor: selected ? `${visuals.accent}1D` : visuals.solidSurfaceBase,
                          borderColor: selected ? visuals.accent : visuals.glassBorder,
                        }}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                            {meta.displayName}
                          </span>
                          {selected && <StatusLabel label="SELECTED" color={visuals.accent} />}
                        </div>
                        <div className="text-[11px] mt-0.5" style={{ color: visuals.textSecondary }}>
                          {meta.subtitle}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}

            {/* Accent Color Swatches */}
            <div>
              <div className="text-xs font-bold mb-2" style={{ color: visuals.textSecondary }}>
                Accent Color Override
              </div>
              <div className="flex flex-wrap gap-2">
                {ACCENT_PRESETS_META.map((acc) => {
                  const selected = state.settings.accentPreset === acc.preset;
                  return (
                    <button
                      key={acc.preset}
                      type="button"
                      onClick={() => setAccentPreset(acc.preset)}
                      className="inline-flex items-center gap-2 rounded-xl border px-3 py-1.5 text-xs font-semibold cursor-pointer"
                      style={{
                        backgroundColor: selected ? `${visuals.accent}22` : visuals.solidSurfaceBase,
                        borderColor: selected ? visuals.accent : visuals.glassBorder,
                        color: visuals.textPrimary,
                      }}
                    >
                      <span
                        className="w-3.5 h-3.5 rounded-full border"
                        style={{
                          backgroundColor: acc.hex ?? visuals.accent,
                          borderColor: visuals.glassBorder,
                        }}
                      />
                      <span>{acc.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Background Texture & Opacity */}
            <div className="space-y-2.5">
              <div className="text-xs font-bold" style={{ color: visuals.textSecondary }}>
                Background Ambient Texture
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {TEXTURE_PRESETS_META.map((tex) => {
                  const active = state.settings.backgroundTexture === tex.preset;
                  return (
                    <button
                      key={tex.preset}
                      type="button"
                      onClick={() => setBackgroundTexture(tex.preset)}
                      className="rounded-xl border p-2.5 text-left cursor-pointer"
                      style={{
                        backgroundColor: active ? `${visuals.accent}1D` : visuals.solidSurfaceBase,
                        borderColor: active ? visuals.accent : visuals.glassBorder,
                      }}
                    >
                      <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                        {tex.label}
                      </div>
                      <div className="text-[10px]" style={{ color: visuals.textSecondary }}>
                        {tex.subtitle}
                      </div>
                    </button>
                  );
                })}
              </div>

              <div>
                <div className="flex justify-between text-xs mb-1">
                  <span style={{ color: visuals.textSecondary }}>Texture Opacity</span>
                  <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                    {Math.round(state.settings.backgroundTextureOpacity * 100)}%
                  </span>
                </div>
                <input
                  type="range"
                  min={0.05}
                  max={1}
                  step={0.05}
                  value={state.settings.backgroundTextureOpacity}
                  onChange={(e) => setBackgroundTextureOpacity(Number(e.target.value))}
                  className="ezd-slider"
                />
              </div>
            </div>

            {/* Typography Font Selector */}
            <div>
              <div className="text-xs font-bold mb-2" style={{ color: visuals.textSecondary }}>
                Typography Style
              </div>
              <div className="grid grid-cols-3 gap-2">
                {FONT_STYLES_META.map((f) => {
                  const active = state.settings.appFontStyle === f.style;
                  return (
                    <button
                      key={f.style}
                      type="button"
                      onClick={() => setAppFontStyle(f.style)}
                      className="rounded-xl border px-3 py-2 text-xs font-semibold cursor-pointer"
                      style={{
                        backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                        borderColor: active ? visuals.accent : visuals.glassBorder,
                        color: active ? visuals.accentOnColor : visuals.textPrimary,
                      }}
                    >
                      {f.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Blur Intensity, Reduce Motion & Floating Pill Opaque */}
            <div className="space-y-3 pt-2 border-t" style={{ borderColor: visuals.glassBorder }}>
              <div>
                <div className="flex justify-between text-xs mb-1">
                  <span style={{ color: visuals.textSecondary }}>Frosted Blur Intensity</span>
                  <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                    {Math.round(state.settings.blurIntensity * 100)}%
                  </span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.05}
                  value={state.settings.blurIntensity}
                  onChange={(e) => setBlurIntensity(Number(e.target.value))}
                  className="ezd-slider"
                />
              </div>

              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                    Reduce Animations
                  </div>
                  <div className="text-[11px]" style={{ color: visuals.textSecondary }}>
                    Snap transitions & static audio visualizer frames.
                  </div>
                </div>
                <ToggleSwitch checked={state.settings.reduceAnimations} onChange={setReduceAnimations} />
              </div>

              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                    Opaque Floating Pill Navigation
                  </div>
                  <div className="text-[11px]" style={{ color: visuals.textSecondary }}>
                    Use solid surface fill instead of translucent backdrop blur.
                  </div>
                </div>
                <ToggleSwitch checked={state.settings.floatingPillOpaque} onChange={setFloatingPillOpaque} />
              </div>
            </div>

            {/* Pinned Pill Shortcuts */}
            <div className="pt-2 border-t space-y-2" style={{ borderColor: visuals.glassBorder }}>
              <div className="text-xs font-bold" style={{ color: visuals.textSecondary }}>
                Pinned Floating Pill Shortcuts ({state.settings.pinnedPillTools.length}/6)
              </div>
              {state.settings.pinnedPillTools.length === 0 ? (
                <p className="text-xs" style={{ color: visuals.textSecondary }}>
                  No tools pinned yet. Drag a tool onto the bottom pill or click Pin in the Tools tab.
                </p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {state.settings.pinnedPillTools.map((tid, idx) => {
                    const meta = TOOLS_META.find((t) => t.id === tid);
                    return (
                      <div
                        key={tid}
                        className="inline-flex items-center gap-1.5 rounded-xl border px-2.5 py-1.5 text-xs"
                        style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
                      >
                        <span className="font-semibold" style={{ color: visuals.textPrimary }}>
                          {meta?.title ?? tid}
                        </span>
                        {idx > 0 && (
                          <button type="button" onClick={() => movePinnedPillTool(tid, -1)} className="cursor-pointer">
                            <ChevronLeft className="w-3.5 h-3.5" />
                          </button>
                        )}
                        {idx < state.settings.pinnedPillTools.length - 1 && (
                          <button type="button" onClick={() => movePinnedPillTool(tid, 1)} className="cursor-pointer">
                            <ChevronRight className="w-3.5 h-3.5" />
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => unpinToolFromPill(tid)}
                          className="cursor-pointer"
                          style={{ color: visuals.errorColor }}
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </CollapsibleSection>
      </GlassCard>

      {/* Interactive Privilege & WorkMode Selector */}
      <GlassCard>
        <div className="flex items-center gap-2 mb-2">
          <Shield className="w-4 h-4" style={{ color: visuals.accent }} />
          <SectionHeader
            title="Work Mode & Privilege Switcher"
            subtitle={`Effective Mode: ${state.effectivePrivilegeMode}`}
          />
        </div>
        <div className="mt-3 grid grid-cols-2 sm:grid-cols-4 gap-2">
          {Object.values(WorkMode).map((wm) => {
            const active = state.settings.workMode === wm;
            return (
              <button
                key={wm}
                type="button"
                onClick={() => setPrivilegeSimulation(state.rootStatus, state.shizukuStatus, wm)}
                className="rounded-xl border p-2.5 text-left cursor-pointer"
                style={{
                  backgroundColor: active ? `${visuals.accent}1D` : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                }}
              >
                <div className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                  {WORK_MODE_META[wm].label}
                </div>
                <div className="text-[10px] line-clamp-2 mt-0.5" style={{ color: visuals.textSecondary }}>
                  {WORK_MODE_META[wm].subtitle}
                </div>
              </button>
            );
          })}
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <OutlineButton
            onClick={() =>
              setPrivilegeSimulation(
                state.rootStatus === RootStatus.GRANTED ? RootStatus.DENIED : RootStatus.GRANTED,
                state.shizukuStatus
              )
            }
          >
            <span>Root: {state.rootStatus === RootStatus.GRANTED ? 'Granted' : 'Denied'}</span>
          </OutlineButton>
          <OutlineButton
            onClick={() =>
              setPrivilegeSimulation(
                state.rootStatus,
                state.shizukuStatus === ShizukuStatus.GRANTED
                  ? ShizukuStatus.NOT_RUNNING
                  : ShizukuStatus.GRANTED
              )
            }
          >
            <span>Shizuku: {state.shizukuStatus === ShizukuStatus.GRANTED ? 'Granted' : 'Stopped'}</span>
          </OutlineButton>
        </div>
      </GlassCard>

      {/* Boot Memory on Start */}
      <GlassCard>
        <div className="flex items-center justify-between gap-3">
          <div>
            <SectionHeader title="Boot Memory on Start" subtitle={state.settings.bootMemory.summary} />
          </div>
          <ToggleSwitch checked={state.settings.bootMemory.applyOnBoot} onChange={setApplyOnBoot} />
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <OutlineButton onClick={captureBootMemorySnapshot}>
            <HardDrive className="w-3.5 h-3.5" />
            <span>Save Snapshot</span>
          </OutlineButton>
          <OutlineButton onClick={applyBootMemoryNow}>
            <Sparkles className="w-3.5 h-3.5" />
            <span>Apply Now</span>
          </OutlineButton>
          {state.bootGuardState.safeModeActive && (
            <OutlineButton onClick={clearBootSafeMode}>Clear Safe Mode</OutlineButton>
          )}
        </div>
      </GlassCard>

      {/* Notifications & Root Background Daemon */}
      <GlassCard>
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <Bell className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="Notifications & Background Daemon" />
          </div>

          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold" style={{ color: visuals.textPrimary }}>
              Root Background Daemon ({daemonStats.serviceRunning ? 'Running' : 'Paused'})
            </span>
            <ToggleSwitch checked={state.settings.daemonEnabled} onChange={toggleDaemonService} />
          </div>

          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold" style={{ color: visuals.textPrimary }}>
              Daemon & Per-App Alerts
            </span>
            <ToggleSwitch
              checked={state.settings.notifications.daemonAlerts}
              onChange={(daemonAlerts) =>
                setNotificationPrefs({ ...state.settings.notifications, daemonAlerts })
              }
            />
          </div>

          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold" style={{ color: visuals.textPrimary }}>
              Battery Thermal Alerts
            </span>
            <ToggleSwitch
              checked={state.settings.notifications.batteryAlerts}
              onChange={(batteryAlerts) =>
                setNotificationPrefs({ ...state.settings.notifications, batteryAlerts })
              }
            />
          </div>

          <div className="flex flex-wrap gap-2 pt-1">
            <OutlineButton onClick={() => setDaemonLogsOpen((v) => !v)}>
              <span>Daemon Logs ({daemonStats.logs.length})</span>
              {daemonLogsOpen ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
            </OutlineButton>
            <OutlineButton
              onClick={() => {
                setActiveTab(MainTab.TOOLS);
                openTool(ToolId.ROOT_TERMINAL);
              }}
            >
              <Terminal className="w-3.5 h-3.5" />
              <span>Open Root Terminal</span>
            </OutlineButton>
          </div>

          {daemonLogsOpen && (
            <div
              className="rounded-2xl border p-3 font-mono text-[11px] max-h-40 overflow-y-auto space-y-1 ezd-scroll-thin"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
                color: visuals.textSecondary,
              }}
            >
              {daemonStats.logs.map((line, i) => (
                <div key={i}>{line}</div>
              ))}
            </div>
          )}
        </div>
      </GlassCard>

      {/* Permissions Status (Collapsible 4/4) */}
      <GlassCard>
        <CollapsibleSection
          title="Permissions Status"
          subtitle="Runtime authority & Android system hooks"
          expanded={permissionsOpen}
          onToggle={() => setPermissionsOpen((v) => !v)}
          trailingBadge={`${grantedCount}/4`}
        >
          <div className="space-y-2">
            {permList.map((perm) => (
              <div
                key={perm.label}
                className="flex items-center justify-between rounded-xl border px-3 py-2 text-xs"
                style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
              >
                <span style={{ color: visuals.textPrimary }}>{perm.label}</span>
                <span className="inline-flex items-center gap-1 font-mono font-bold" style={{ color: perm.granted ? visuals.successColor : visuals.warningColor }}>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  {perm.granted ? 'READY' : 'PENDING'}
                </span>
              </div>
            ))}
          </div>
        </CollapsibleSection>
      </GlassCard>

      {/* Hardware Diagnostics & OTA Check */}
      <GlassCard>
        <div className="space-y-3">
          <SectionHeader
            title="Diagnostics & GitHub OTA Releases"
            subtitle={`Current Version: ${state.releaseInfo.currentVersion} · evorez/EZD-Manager`}
          />

          <div className="flex flex-wrap gap-2">
            <OutlineButton onClick={exportAndCopyDiagnostics}>
              <Copy className="w-3.5 h-3.5" />
              <span>Copy & Export Diagnostics</span>
            </OutlineButton>
            <GlassButton
              text={state.checkingUpdate ? 'Checking...' : 'Check for Updates'}
              icon={<RefreshCw className={`w-3.5 h-3.5 ${state.checkingUpdate ? 'animate-spin' : ''}`} />}
              onClick={checkForGitHubUpdates}
              disabled={state.checkingUpdate}
            />
            <OutlineButton onClick={() => setChangelogOpen((v) => !v)}>
              <span>Release Changelog</span>
            </OutlineButton>
          </div>

          {state.diagnosticsExportPath && (
            <div className="text-xs font-mono" style={{ color: visuals.successColor }}>
              Exported: {state.diagnosticsExportPath}
            </div>
          )}

          {state.releaseInfo.statusMessage && (
            <div
              className="rounded-2xl border p-3 text-xs space-y-2"
              style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
            >
              <div className="font-semibold" style={{ color: visuals.textPrimary }}>
                {state.releaseInfo.statusMessage}
              </div>
              {state.releaseInfo.updateAvailable && state.releaseInfo.downloadUrl && (
                <a
                  href={state.releaseInfo.downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 font-semibold"
                  style={{ color: visuals.accent }}
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Open Release {state.releaseInfo.latestTag}</span>
                </a>
              )}
            </div>
          )}

          {changelogOpen && (
            <div className="space-y-2.5 pt-2">
              {builtInChangelog().map((rel) => (
                <div
                  key={rel.version}
                  className="rounded-2xl border p-3.5 space-y-1.5"
                  style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
                >
                  <div className="text-xs font-bold" style={{ color: visuals.accent }}>
                    {rel.version}
                  </div>
                  <ul className="list-disc pl-4 space-y-1 text-xs" style={{ color: visuals.textSecondary }}>
                    {rel.notes.map((n, i) => (
                      <li key={i}>{n}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      </GlassCard>
    </div>
  );
};
