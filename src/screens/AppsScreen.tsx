import React, { useEffect, useMemo, useState } from 'react';
import {
  Check,
  Search,
  Shield,
  SlidersHorizontal,
  Snowflake,
  Trash2,
  X,
  ZapOff,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { DnsProviderLogo } from '../components/CustomVisuals';
import {
  AppIconBadge,
  GlassButton,
  GlassCard,
  OutlineButton,
  SectionHeader,
  StatusLabel,
  ToggleSwitch,
} from '../components/GlassUI';
import { BUILT_IN_PROFILES, DNS_PRESETS } from '../data/presets';
import {
  AppCategoryFilter,
  AppEntry,
  AppSortOption,
  PerAppConfig,
  PerAppDnsMode,
} from '../types/ezd';

export const AppsScreen: React.FC = () => {
  const {
    state,
    visuals,
    pendingAppPkg,
    setPendingAppPkg,
    setIncludeSystemApps,
    toggleUsageAccess,
    savePerAppConfig,
    syncPerAppDnsVpn,
    clearAppCache,
    forceStopApp,
    toggleFreezeApp,
    uninstallApp,
    allSpoofPresets,
  } = useEzd();

  const [searchQuery, setSearchQuery] = useState('');
  const [category, setCategory] = useState<AppCategoryFilter>(AppCategoryFilter.ALL);
  const [sortOption, setSortOption] = useState<AppSortOption>(AppSortOption.CONFIGURED);
  const [selectedApp, setSelectedApp] = useState<AppEntry | null>(null);

  useEffect(() => {
    if (!pendingAppPkg) return;
    const match = state.installedApps.find((a) => a.packageName === pendingAppPkg);
    if (match) setSelectedApp(match);
    setPendingAppPkg(null);
  }, [pendingAppPkg, state.installedApps, setPendingAppPkg]);

  const filteredApps = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const list = state.installedApps.filter((app) => {
      if (!state.includeSystemApps && app.isSystemApp) return false;
      if (q && !app.label.toLowerCase().includes(q) && !app.packageName.toLowerCase().includes(q)) {
        return false;
      }
      const cfg = state.perAppConfigs[app.packageName];
      if (category === AppCategoryFilter.CONFIGURED) return Boolean(cfg?.enabled);
      if (category === AppCategoryFilter.WHITELISTED) {
        return Boolean(cfg?.whitelisted || state.batteryWhitelist.includes(app.packageName));
      }
      return true;
    });

    return [...list].sort((a, b) => {
      if (sortOption === AppSortOption.CONFIGURED) {
        const aCfg = state.perAppConfigs[a.packageName]?.enabled ? 1 : 0;
        const bCfg = state.perAppConfigs[b.packageName]?.enabled ? 1 : 0;
        if (aCfg !== bCfg) return bCfg - aCfg;
      }
      if (sortOption === AppSortOption.PACKAGE) {
        return a.packageName.localeCompare(b.packageName);
      }
      return a.label.localeCompare(b.label);
    });
  }, [
    state.installedApps,
    state.includeSystemApps,
    state.perAppConfigs,
    state.batteryWhitelist,
    searchQuery,
    category,
    sortOption,
  ]);

  const allProfiles = [...BUILT_IN_PROFILES, ...state.customProfiles];
  const spoofs = allSpoofPresets();

  return (
    <div className="space-y-4">
      {/* Header Engine & VPN Card */}
      <GlassCard>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <SectionHeader
              title="Per-App Engine & Local DNS"
              subtitle="Automatic foreground profile, Hz, spoof & per-app DNS routing"
            />
            <div className="mt-2 flex items-center gap-2 flex-wrap">
              <StatusLabel
                label={state.usageAccessGranted ? 'UsageStats Active' : 'UsageStats Off'}
                color={state.usageAccessGranted ? visuals.successColor : visuals.warningColor}
              />
              <StatusLabel
                label={state.vpnActive ? 'DNS Tunnel Active' : 'DNS Tunnel Standby'}
                color={state.vpnActive ? visuals.accent : visuals.textSecondary}
              />
            </div>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <OutlineButton onClick={toggleUsageAccess}>
              <Shield className="w-3.5 h-3.5" />
              <span>{state.usageAccessGranted ? 'Usage Access On' : 'Grant Usage Access'}</span>
            </OutlineButton>
            <GlassButton text="Sync DNS VPN" onClick={syncPerAppDnsVpn} />
          </div>
        </div>
      </GlassCard>

      {/* Search, Filter & Sort Controls */}
      <GlassCard>
        <div className="space-y-3">
          <div className="relative">
            <Search
              className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"
              style={{ color: visuals.textSecondary }}
            />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search app name or package..."
              className="w-full rounded-2xl border pl-10 pr-4 py-2.5 text-sm outline-none"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
                color: visuals.textPrimary,
              }}
            />
          </div>

          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 flex-wrap">
              {(
                [
                  { id: AppCategoryFilter.ALL, label: 'All' },
                  { id: AppCategoryFilter.CONFIGURED, label: 'Configured' },
                  { id: AppCategoryFilter.WHITELISTED, label: 'Whitelisted' },
                ] as const
              ).map((c) => {
                const active = category === c.id;
                return (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => setCategory(c.id)}
                    className="rounded-xl border px-3 py-1.5 text-xs font-semibold cursor-pointer"
                    style={{
                      backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                      borderColor: active ? visuals.accent : visuals.glassBorder,
                      color: active ? visuals.accentOnColor : visuals.textPrimary,
                    }}
                  >
                    {c.label}
                  </button>
                );
              })}
            </div>

            <div className="flex items-center gap-3">
              <select
                aria-label="Sort apps"
                value={sortOption}
                onChange={(e) => setSortOption(e.target.value as AppSortOption)}
                className="rounded-xl border px-2.5 py-1.5 text-xs font-semibold outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceBase,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              >
                <option value={AppSortOption.CONFIGURED}>Configured First</option>
                <option value={AppSortOption.NAME}>Name (A–Z)</option>
                <option value={AppSortOption.PACKAGE}>Package Name</option>
              </select>

              <label className="inline-flex items-center gap-2 text-xs font-semibold cursor-pointer">
                <span style={{ color: visuals.textSecondary }}>System</span>
                <ToggleSwitch
                  checked={state.includeSystemApps}
                  onChange={setIncludeSystemApps}
                  ariaLabel="Include System Apps"
                />
              </label>
            </div>
          </div>
        </div>
      </GlassCard>

      {/* Apps List */}
      <div className="space-y-2.5">
        {filteredApps.map((app) => {
          const cfg = state.perAppConfigs[app.packageName];
          const isWhitelisted = Boolean(cfg?.whitelisted || state.batteryWhitelist.includes(app.packageName));
          return (
            <GlassCard key={app.packageName} onClick={() => setSelectedApp(app)}>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <AppIconBadge label={app.label} colorHex={app.colorHex} size={44} />
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-bold truncate" style={{ color: visuals.textPrimary }}>
                        {app.label}
                      </span>
                      {app.isDisabled && <StatusLabel label="FROZEN" color={visuals.errorColor} />}
                      {app.isSystemApp && <StatusLabel label="SYSTEM" color={visuals.textSecondary} />}
                    </div>
                    <div className="text-xs font-mono truncate" style={{ color: visuals.textSecondary }}>
                      {app.packageName} · v{app.versionName}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0 flex-wrap justify-end">
                  {cfg?.enabled && (
                    <StatusLabel label={`${cfg.profileId.toUpperCase()}${cfg.refreshRateHz ? ` · ${cfg.refreshRateHz}Hz` : ''}`} color={visuals.accent} />
                  )}
                  {isWhitelisted && <StatusLabel label="WHITELIST" color={visuals.successColor} />}
                  <SlidersHorizontal className="w-4 h-4 ml-1" style={{ color: visuals.textSecondary }} />
                </div>
              </div>
            </GlassCard>
          );
        })}

        {filteredApps.length === 0 && (
          <GlassCard>
            <p className="py-6 text-center text-sm" style={{ color: visuals.textSecondary }}>
              No applications match the active search or category filter.
            </p>
          </GlassCard>
        )}
      </div>

      {/* Per-App Detail Sheet Modal */}
      {selectedApp && (
        <PerAppDetailModal
          app={selectedApp}
          initialConfig={
            state.perAppConfigs[selectedApp.packageName] ?? {
              packageName: selectedApp.packageName,
              enabled: false,
              profileId: 'balanced',
              gpuProfile: '',
              refreshRateHz: null,
              spoofPresetId: null,
              dnsMode: PerAppDnsMode.SYSTEM,
              customDnsServer: '1.1.1.1',
              batteryRestricted: false,
              whitelisted: state.batteryWhitelist.includes(selectedApp.packageName),
              touchBoost: false,
              disableHwOverlays: false,
            }
          }
          profiles={allProfiles.map((p) => ({ id: p.id, name: p.name }))}
          spoofs={spoofs.map((s) => ({ id: s.id, name: s.name }))}
          onClose={() => setSelectedApp(null)}
          onSave={(cfg) => {
            savePerAppConfig(cfg);
            setSelectedApp(null);
          }}
          onClearCache={() => clearAppCache(selectedApp.packageName)}
          onForceStop={() => forceStopApp(selectedApp.packageName)}
          onToggleFreeze={() => toggleFreezeApp(selectedApp)}
          onUninstall={() => {
            uninstallApp(selectedApp);
            setSelectedApp(null);
          }}
        />
      )}
    </div>
  );
};

const PerAppDetailModal: React.FC<{
  app: AppEntry;
  initialConfig: PerAppConfig;
  profiles: { id: string; name: string }[];
  spoofs: { id: string; name: string }[];
  onClose: () => void;
  onSave: (cfg: PerAppConfig) => void;
  onClearCache: () => void;
  onForceStop: () => void;
  onToggleFreeze: () => void;
  onUninstall: () => void;
}> = ({
  app,
  initialConfig,
  profiles,
  spoofs,
  onClose,
  onSave,
  onClearCache,
  onForceStop,
  onToggleFreeze,
  onUninstall,
}) => {
  const { visuals } = useEzd();
  const [cfg, setCfg] = useState<PerAppConfig>(initialConfig);
  const [confirmUninstall, setConfirmUninstall] = useState(false);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/70 backdrop-blur-sm p-2 sm:p-4"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-lg max-h-[88vh] overflow-y-auto rounded-[28px] border p-5 shadow-2xl space-y-4 ezd-scroll-thin"
        style={{ backgroundColor: visuals.solidSurfaceElevated, borderColor: visuals.glassBorder }}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <AppIconBadge label={app.label} colorHex={app.colorHex} size={46} />
            <div className="min-w-0">
              <h3 className="text-base font-bold truncate" style={{ color: visuals.textPrimary }}>
                {app.label}
              </h3>
              <div className="text-xs font-mono truncate" style={{ color: visuals.textSecondary }}>
                {app.packageName}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close app detail"
            className="p-1.5 rounded-full cursor-pointer"
            style={{ color: visuals.textSecondary }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* App Management Row */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <OutlineButton onClick={onClearCache} className="justify-center">
            <Trash2 className="w-3.5 h-3.5" />
            <span>Clear Cache</span>
          </OutlineButton>
          <OutlineButton onClick={onForceStop} className="justify-center">
            <ZapOff className="w-3.5 h-3.5" />
            <span>Force Stop</span>
          </OutlineButton>
          <OutlineButton onClick={onToggleFreeze} className="justify-center">
            <Snowflake className="w-3.5 h-3.5" />
            <span>{app.isDisabled ? 'Unfreeze' : 'Freeze'}</span>
          </OutlineButton>
          <OutlineButton
            onClick={() => setConfirmUninstall(true)}
            disabled={app.isSystemApp}
            className="justify-center"
          >
            <span style={{ color: visuals.errorColor }}>Uninstall</span>
          </OutlineButton>
        </div>

        {confirmUninstall && (
          <div
            className="rounded-2xl border p-3 flex items-center justify-between gap-2"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: `${visuals.errorColor}55` }}
          >
            <span className="text-xs font-semibold" style={{ color: visuals.errorColor }}>
              Uninstall {app.label}?
            </span>
            <div className="flex gap-2">
              <OutlineButton onClick={() => setConfirmUninstall(false)}>No</OutlineButton>
              <GlassButton text="Uninstall" onClick={onUninstall} />
            </div>
          </div>
        )}

        {/* Auto Tuning Master Switch */}
        <div
          className="rounded-2xl border p-3.5 space-y-3"
          style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
        >
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
                Enable Auto Foreground Tuning
              </div>
              <div className="text-xs" style={{ color: visuals.textSecondary }}>
                Automatically applies profile, Hz, touch & spoof when launched.
              </div>
            </div>
            <ToggleSwitch
              checked={cfg.enabled}
              onChange={(enabled) => setCfg({ ...cfg, enabled })}
              ariaLabel="Enable auto tuning"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t" style={{ borderColor: visuals.glassBorder }}>
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: visuals.textSecondary }}>
                Target Profile
              </label>
              <select
                value={cfg.profileId}
                onChange={(e) => setCfg({ ...cfg, profileId: e.target.value })}
                className="w-full rounded-xl border px-3 py-2 text-xs font-semibold outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceElevated,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              >
                {profiles.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: visuals.textSecondary }}>
                Target Refresh Rate
              </label>
              <select
                value={cfg.refreshRateHz ?? ''}
                onChange={(e) =>
                  setCfg({ ...cfg, refreshRateHz: e.target.value ? Number(e.target.value) : null })
                }
                className="w-full rounded-xl border px-3 py-2 text-xs font-semibold outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceElevated,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              >
                <option value="">System Default</option>
                <option value="60">60 Hz</option>
                <option value="90">90 Hz</option>
                <option value="120">120 Hz</option>
                <option value="144">144 Hz</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: visuals.textSecondary }}>
                Device Spoof Preset
              </label>
              <select
                value={cfg.spoofPresetId ?? ''}
                onChange={(e) => setCfg({ ...cfg, spoofPresetId: e.target.value || null })}
                className="w-full rounded-xl border px-3 py-2 text-xs font-semibold outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceElevated,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              >
                <option value="">No Spoof Override</option>
                {spoofs.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: visuals.textSecondary }}>
                GPU Mode Override
              </label>
              <select
                value={cfg.gpuProfile}
                onChange={(e) => setCfg({ ...cfg, gpuProfile: e.target.value })}
                className="w-full rounded-xl border px-3 py-2 text-xs font-semibold outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceElevated,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              >
                <option value="">Follow Profile</option>
                <option value="powersave">Powersave</option>
                <option value="msm-adreno-tz">Adreno TZ (Balanced)</option>
                <option value="performance">Performance Lock</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 pt-2">
            <label className="flex items-center justify-between gap-2 text-xs">
              <span style={{ color: visuals.textPrimary }}>Touch Boost</span>
              <ToggleSwitch
                checked={cfg.touchBoost}
                onChange={(touchBoost) => setCfg({ ...cfg, touchBoost })}
              />
            </label>
            <label className="flex items-center justify-between gap-2 text-xs">
              <span style={{ color: visuals.textPrimary }}>No HW Overlays</span>
              <ToggleSwitch
                checked={cfg.disableHwOverlays}
                onChange={(disableHwOverlays) => setCfg({ ...cfg, disableHwOverlays })}
              />
            </label>
            <label className="flex items-center justify-between gap-2 text-xs">
              <span style={{ color: visuals.textPrimary }}>Whitelist Battery</span>
              <ToggleSwitch
                checked={cfg.whitelisted}
                onChange={(whitelisted) =>
                  setCfg({ ...cfg, whitelisted, batteryRestricted: whitelisted ? false : cfg.batteryRestricted })
                }
              />
            </label>
            <label className="flex items-center justify-between gap-2 text-xs">
              <span style={{ color: visuals.textPrimary }}>Restrict Bg</span>
              <ToggleSwitch
                checked={cfg.batteryRestricted}
                onChange={(batteryRestricted) =>
                  setCfg({ ...cfg, batteryRestricted, whitelisted: batteryRestricted ? false : cfg.whitelisted })
                }
              />
            </label>
          </div>
        </div>

        {/* Per-App DNS Routing */}
        <div
          className="rounded-2xl border p-3.5 space-y-3"
          style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
        >
          <div className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
            Per-App DNS Routing
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
            {Object.values(PerAppDnsMode).map((m) => {
              const selected = cfg.dnsMode === m;
              return (
                <button
                  key={m}
                  type="button"
                  onClick={() => setCfg({ ...cfg, dnsMode: m })}
                  className="rounded-xl border px-2 py-1.5 text-[11px] font-bold cursor-pointer"
                  style={{
                    backgroundColor: selected ? visuals.accent : visuals.solidSurfaceElevated,
                    borderColor: selected ? visuals.accent : visuals.glassBorder,
                    color: selected ? visuals.accentOnColor : visuals.textPrimary,
                  }}
                >
                  {m.replace('_', ' ')}
                </button>
              );
            })}
          </div>

          {cfg.dnsMode !== PerAppDnsMode.SYSTEM && (
            <>
              <div className="grid grid-cols-2 gap-2">
                {DNS_PRESETS.filter((d) => d.id !== 'off').map((preset) => {
                  const targetVal =
                    cfg.dnsMode === PerAppDnsMode.DOH
                      ? preset.dohUrl
                      : cfg.dnsMode === PerAppDnsMode.DOT
                      ? preset.hostname
                      : preset.primaryIp;
                  const active = cfg.customDnsServer === targetVal;
                  return (
                    <button
                      key={preset.id}
                      type="button"
                      onClick={() => setCfg({ ...cfg, customDnsServer: targetVal })}
                      className="flex items-center gap-2 rounded-xl border p-2 text-left cursor-pointer"
                      style={{
                        backgroundColor: active ? `${visuals.accent}1D` : visuals.solidSurfaceElevated,
                        borderColor: active ? visuals.accent : visuals.glassBorder,
                      }}
                    >
                      <DnsProviderLogo providerId={preset.id} size={26} />
                      <div className="min-w-0">
                        <div className="text-xs font-bold truncate" style={{ color: visuals.textPrimary }}>
                          {preset.name}
                        </div>
                        <div className="text-[10px] font-mono truncate" style={{ color: visuals.textSecondary }}>
                          {preset.primaryIp}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
              <input
                type="text"
                value={cfg.customDnsServer}
                onChange={(e) => setCfg({ ...cfg, customDnsServer: e.target.value })}
                placeholder="Custom DNS IP / DoT Hostname / DoH URL"
                className="w-full rounded-xl border px-3 py-2 text-xs font-mono outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceElevated,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              />
            </>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <OutlineButton onClick={onClose}>Cancel</OutlineButton>
          <GlassButton text="Save App Tuning" icon={<Check className="w-4 h-4" />} onClick={() => onSave(cfg)} />
        </div>
      </div>
    </div>
  );
};
