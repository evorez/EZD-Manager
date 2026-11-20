import React, { useState } from 'react';
import {
  Archive,
  Check,
  Copy,
  Download,
  HardDrive,
  Plus,
  RotateCcw,
  Sliders,
  Smartphone,
  Terminal,
  Trash2,
  Upload,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import {
  AppIconBadge,
  GlassButton,
  GlassCard,
  OutlineButton,
  SectionHeader,
  StatusLabel,
  ToggleSwitch,
} from '../components/GlassUI';
import { BUILT_IN_PROFILES } from '../data/presets';
import { AppBackupOptions, ProfileConfig, SpoofPreset } from '../types/ezd';

export const DeviceSpoofPanel: React.FC = () => {
  const {
    state,
    visuals,
    allSpoofPresets,
    applySpoofPreset,
    resetSpoof,
    upsertCustomSpoofPreset,
    deleteCustomSpoofPreset,
  } = useEzd();

  const presets = allSpoofPresets();
  const s = state.spoofState;

  const [newName, setNewName] = useState('');
  const [newManufacturer, setNewManufacturer] = useState('');
  const [newBrand, setNewBrand] = useState('');
  const [newModel, setNewModel] = useState('');
  const [newDevice, setNewDevice] = useState('');

  const handleAdd = () => {
    if (!newName.trim() || !newModel.trim()) return;
    const preset: SpoofPreset = {
      id: `custom_${Date.now()}`,
      name: newName.trim(),
      manufacturer: newManufacturer.trim() || 'Xiaomi',
      brand: newBrand.trim() || 'Xiaomi',
      model: newModel.trim(),
      device: newDevice.trim() || 'custom',
      isCustom: true,
    };
    upsertCustomSpoofPreset(preset);
    setNewName('');
    setNewManufacturer('');
    setNewBrand('');
    setNewModel('');
    setNewDevice('');
  };

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Smartphone className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="Active Device Identity" subtitle={s.statusMessage} />
          </div>
          <OutlineButton onClick={resetSpoof}>
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Restore Original</span>
          </OutlineButton>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
          {[
            { label: 'Model', val: s.currentModel },
            { label: 'Brand', val: s.currentBrand },
            { label: 'Manufacturer', val: s.currentManufacturer },
            { label: 'Codename', val: s.currentDevice },
          ].map((f) => (
            <div key={f.label} className="rounded-xl border p-2.5" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
              <div className="text-[10px]" style={{ color: visuals.textSecondary }}>{f.label}</div>
              <div className="font-bold font-mono truncate mt-0.5" style={{ color: visuals.textPrimary }}>{f.val}</div>
            </div>
          ))}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Spoof Presets (resetprop)" subtitle="Apply flagship gaming profiles or create custom targets" />
        <div className="mt-3 space-y-2">
          {presets.map((p) => {
            const active = s.currentModel === p.model && s.currentDevice === p.device;
            return (
              <div
                key={p.id}
                className="rounded-2xl border p-3 flex items-center justify-between gap-2"
                style={{
                  backgroundColor: active ? `${visuals.accent}1A` : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                }}
              >
                <div className="min-w-0">
                  <div className="text-xs font-bold flex items-center gap-2" style={{ color: visuals.textPrimary }}>
                    <span>{p.name}</span>
                    {p.isCustom && <StatusLabel label="CUSTOM" color={visuals.accent} />}
                  </div>
                  <div className="text-[11px] font-mono truncate" style={{ color: visuals.textSecondary }}>
                    {p.manufacturer} / {p.model} ({p.device})
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  <GlassButton text={active ? 'Active' : 'Apply'} onClick={() => applySpoofPreset(p)} />
                  {p.isCustom && (
                    <OutlineButton onClick={() => deleteCustomSpoofPreset(p.id)}>
                      <Trash2 className="w-3.5 h-3.5" />
                    </OutlineButton>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Add Custom Spoof Preset" />
        <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-2.5">
          <input
            type="text"
            placeholder="Preset Name (e.g. RedMagic 10 Pro)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            className="rounded-xl border px-3 py-2 text-xs outline-none sm:col-span-2"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
          <input
            type="text"
            placeholder="Model (ro.product.model)"
            value={newModel}
            onChange={(e) => setNewModel(e.target.value)}
            className="rounded-xl border px-3 py-2 text-xs outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
          <input
            type="text"
            placeholder="Device (ro.product.device)"
            value={newDevice}
            onChange={(e) => setNewDevice(e.target.value)}
            className="rounded-xl border px-3 py-2 text-xs outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
          <input
            type="text"
            placeholder="Brand"
            value={newBrand}
            onChange={(e) => setNewBrand(e.target.value)}
            className="rounded-xl border px-3 py-2 text-xs outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
          <input
            type="text"
            placeholder="Manufacturer"
            value={newManufacturer}
            onChange={(e) => setNewManufacturer(e.target.value)}
            className="rounded-xl border px-3 py-2 text-xs outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
        </div>
        <div className="mt-3 flex justify-end">
          <GlassButton text="Save Preset" icon={<Plus className="w-3.5 h-3.5" />} onClick={handleAdd} />
        </div>
      </GlassCard>
    </div>
  );
};

export const ProfilesPanel: React.FC = () => {
  const {
    state,
    visuals,
    applyProfile,
    saveCustomProfile,
    deleteCustomProfile,
    resetCustomProfiles,
  } = useEzd();

  const [name, setName] = useState('');
  const [cpuScale, setCpuScale] = useState(80);
  const [gpuScale, setGpuScale] = useState(70);
  const [hz, setHz] = useState<number>(120);
  const [touchBoost, setTouchBoost] = useState(true);

  const allProfiles = [...BUILT_IN_PROFILES, ...state.customProfiles];

  const handleSaveCustom = () => {
    if (!name.trim()) return;
    const prof: ProfileConfig = {
      id: `custom_${Date.now()}`,
      name: name.trim(),
      description: `CPU ${cpuScale}% · GPU ${gpuScale}% · ${hz}Hz${touchBoost ? ' · Touch Boost' : ''}`,
      isBuiltIn: false,
      cpuGovernor: cpuScale >= 85 ? 'performance' : 'schedutil',
      cpuMaxScalePercent: cpuScale,
      cpuMinScalePercent: Math.max(10, Math.round(cpuScale * 0.4)),
      gpuGovernor: gpuScale >= 85 ? 'performance' : 'msm-adreno-tz',
      gpuMaxPercent: gpuScale,
      targetRefreshHz: hz,
      touchBoost,
    };
    saveCustomProfile(prof);
    setName('');
  };

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Sliders className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="System & Custom Profiles" subtitle={`Active: ${state.settings.activeProfileId}`} />
          </div>
          {state.customProfiles.length > 0 && (
            <OutlineButton onClick={resetCustomProfiles}>Reset Custom</OutlineButton>
          )}
        </div>

        <div className="space-y-2">
          {allProfiles.map((prof) => {
            const active = state.settings.activeProfileId === prof.id;
            return (
              <div
                key={prof.id}
                className="rounded-2xl border p-3.5 flex items-center justify-between gap-3"
                style={{
                  backgroundColor: active ? `${visuals.accent}1E` : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                }}
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
                      {prof.name}
                    </span>
                    {!prof.isBuiltIn && <StatusLabel label="CUSTOM" color={visuals.accent} />}
                    {active && <StatusLabel label="ACTIVE" color={visuals.successColor} />}
                  </div>
                  <p className="text-xs mt-0.5" style={{ color: visuals.textSecondary }}>
                    {prof.description}
                  </p>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  <GlassButton text={active ? 'Applied' : 'Apply'} onClick={() => applyProfile(prof)} />
                  {!prof.isBuiltIn && (
                    <OutlineButton onClick={() => deleteCustomProfile(prof.id)}>
                      <Trash2 className="w-3.5 h-3.5" />
                    </OutlineButton>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Create Custom Tuning Profile" />
        <div className="mt-3 space-y-3">
          <input
            type="text"
            placeholder="Profile Name (e.g. Esports 144Hz)"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-xl border px-3 py-2 text-xs outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
          <div>
            <div className="flex justify-between text-xs mb-1">
              <span style={{ color: visuals.textSecondary }}>CPU Max Scale</span>
              <span className="font-mono font-bold">{cpuScale}%</span>
            </div>
            <input type="range" min={20} max={100} step={5} value={cpuScale} onChange={(e) => setCpuScale(Number(e.target.value))} className="ezd-slider" />
          </div>
          <div>
            <div className="flex justify-between text-xs mb-1">
              <span style={{ color: visuals.textSecondary }}>GPU Max Scale</span>
              <span className="font-mono font-bold">{gpuScale}%</span>
            </div>
            <input type="range" min={10} max={100} step={5} value={gpuScale} onChange={(e) => setGpuScale(Number(e.target.value))} className="ezd-slider" />
          </div>
          <div className="flex items-center justify-between gap-3">
            <select
              value={hz}
              onChange={(e) => setHz(Number(e.target.value))}
              className="rounded-xl border px-3 py-2 text-xs font-semibold outline-none"
              style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
            >
              <option value={60}>60 Hz</option>
              <option value={90}>90 Hz</option>
              <option value={120}>120 Hz</option>
              <option value={144}>144 Hz</option>
            </select>

            <label className="flex items-center gap-2 text-xs font-semibold">
              <span style={{ color: visuals.textPrimary }}>Touch Boost</span>
              <ToggleSwitch checked={touchBoost} onChange={setTouchBoost} />
            </label>

            <GlassButton text="Create" icon={<Plus className="w-3.5 h-3.5" />} onClick={handleSaveCustom} />
          </div>
        </div>
      </GlassCard>
    </div>
  );
};

export const EzdBackupPanel: React.FC = () => {
  const {
    state,
    visuals,
    exportEncryptedBackup,
    inspectSavedEzdBackup,
    executeRestore,
    clearBackupDialogs,
  } = useEzd();

  const [includeNet, setIncludeNet] = useState(true);
  const [includeRoot, setIncludeRoot] = useState(true);
  const [passphrase, setPassphrase] = useState('');
  const [restoreEzd, setRestoreEzd] = useState(true);
  const [restoreNet, setRestoreNet] = useState(true);

  const handleFileImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        inspectSavedEzdBackup(reader.result, file.name, passphrase);
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center gap-2 mb-3">
          <Archive className="w-4 h-4" style={{ color: visuals.accent }} />
          <SectionHeader
            title="Encrypted EZD Config Backup"
            subtitle="AES-256-GCM envelope + SHA-256 integrity verification"
          />
        </div>

        <div className="space-y-3">
          <div className="flex items-center justify-between text-xs">
            <span style={{ color: visuals.textPrimary }}>Include Network DNS & TCP preferences</span>
            <ToggleSwitch checked={includeNet} onChange={setIncludeNet} />
          </div>
          <div className="flex items-center justify-between text-xs">
            <span style={{ color: visuals.textPrimary }}>Include Root Baselines & Profiles</span>
            <ToggleSwitch checked={includeRoot} onChange={setIncludeRoot} />
          </div>
          <input
            type="password"
            value={passphrase}
            onChange={(e) => setPassphrase(e.target.value)}
            placeholder="Optional encryption passphrase (leave blank for device key)"
            className="w-full rounded-xl border px-3 py-2 text-xs outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />

          <div className="flex flex-wrap gap-2">
            <GlassButton
              text="Create & Download Archive"
              icon={<Download className="w-3.5 h-3.5" />}
              onClick={() =>
                exportEncryptedBackup(
                  { includeNetworkConfig: includeNet, includeRootBaselines: includeRoot, passphrase },
                  true
                )
              }
            />
            <OutlineButton
              onClick={() =>
                exportEncryptedBackup(
                  { includeNetworkConfig: includeNet, includeRootBaselines: includeRoot, passphrase },
                  false
                )
              }
            >
              Save In-App Snapshot
            </OutlineButton>

            <label
              className="inline-flex items-center gap-1.5 rounded-full border px-4 py-2 text-xs font-semibold cursor-pointer"
              style={{
                backgroundColor: visuals.solidSurfaceElevated,
                borderColor: visuals.glassBorder,
                color: visuals.textPrimary,
              }}
            >
              <Upload className="w-3.5 h-3.5" />
              <span>Import .ezdcfg.json</span>
              <input type="file" accept=".json" onChange={handleFileImport} className="hidden" />
            </label>
          </div>
        </div>
      </GlassCard>

      {state.backupPreview && (
        <GlassCard highlightBorder>
          <SectionHeader title="Archive Preview & Restore" subtitle={state.backupPreview.filePath} />
          <div className="mt-2 flex flex-wrap gap-1.5">
            {state.backupPreview.categories.map((cat) => (
              <StatusLabel key={cat} label={cat} color={visuals.accent} />
            ))}
            <StatusLabel
              label={state.backupPreview.checksumValid ? 'SHA-256 VERIFIED' : 'INVALID CHECKSUM'}
              color={state.backupPreview.checksumValid ? visuals.successColor : visuals.errorColor}
            />
          </div>
          <div className="mt-3 space-y-2 text-xs">
            <label className="flex items-center justify-between">
              <span>Restore EZD Settings & Profiles</span>
              <ToggleSwitch checked={restoreEzd} onChange={setRestoreEzd} />
            </label>
            <label className="flex items-center justify-between">
              <span>Restore Network Preferences</span>
              <ToggleSwitch checked={restoreNet} onChange={setRestoreNet} />
            </label>
          </div>
          <div className="mt-3 flex justify-end gap-2">
            <OutlineButton onClick={clearBackupDialogs}>Dismiss</OutlineButton>
            <GlassButton
              text="Restore Selected"
              onClick={() => executeRestore(restoreEzd, restoreNet)}
            />
          </div>
        </GlassCard>
      )}

      {state.backupReport && (
        <GlassCard>
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold" style={{ color: state.backupReport.success ? visuals.successColor : visuals.errorColor }}>
              {state.backupReport.summary}
            </span>
            <OutlineButton onClick={clearBackupDialogs}>Clear</OutlineButton>
          </div>
          <div className="text-[11px] font-mono mt-1" style={{ color: visuals.textSecondary }}>
            {state.backupReport.filePath}
          </div>
        </GlassCard>
      )}

      {state.savedEzdBackups.length > 0 && (
        <GlassCard>
          <SectionHeader title="Saved Config Archives" />
          <div className="mt-3 space-y-2">
            {state.savedEzdBackups.map((bk) => (
              <div
                key={bk.id}
                className="rounded-xl border p-3 flex items-center justify-between gap-2"
                style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
              >
                <div className="min-w-0">
                  <div className="text-xs font-bold truncate" style={{ color: visuals.textPrimary }}>
                    {bk.fileName}
                  </div>
                  <div className="text-[10px] font-mono" style={{ color: visuals.textSecondary }}>
                    {bk.createdAt}
                  </div>
                </div>
                <OutlineButton onClick={() => inspectSavedEzdBackup(bk.payload, bk.fileName, passphrase)}>
                  Inspect / Restore
                </OutlineButton>
              </div>
            ))}
          </div>
        </GlassCard>
      )}
    </div>
  );
};

export const AppBackupPanel: React.FC = () => {
  const { state, visuals, runFullAppBackup, restoreLocalAppBackup, deleteLocalAppBackup } = useEzd();
  const [selected, setSelected] = useState<Set<string>>(() => new Set(['com.HoYoverse.GenshinImpact']));
  const [opts, setOpts] = useState<AppBackupOptions>({
    includeBaseApk: true,
    includeAppData: true,
    includeExternalData: true,
    includeObb: false,
    includeMedia: false,
  });

  const togglePkg = (pkg: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(pkg)) next.delete(pkg);
      else next.add(pkg);
      return next;
    });
  };

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center gap-2 mb-3">
          <HardDrive className="w-4 h-4" style={{ color: visuals.accent }} />
          <SectionHeader title="Granular App Backup & Restore" subtitle="Select components & packages to snapshot" />
        </div>

        <div className="flex flex-wrap gap-2 mb-3">
          {(
            [
              { key: 'includeBaseApk', label: 'Base APK' },
              { key: 'includeAppData', label: 'App Data' },
              { key: 'includeExternalData', label: 'Ext Data' },
              { key: 'includeObb', label: 'OBB' },
              { key: 'includeMedia', label: 'Media' },
            ] as const
          ).map((item) => {
            const on = opts[item.key];
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => setOpts({ ...opts, [item.key]: !on })}
                className="rounded-xl border px-3 py-1.5 text-xs font-semibold cursor-pointer"
                style={{
                  backgroundColor: on ? visuals.accent : visuals.solidSurfaceBase,
                  borderColor: on ? visuals.accent : visuals.glassBorder,
                  color: on ? visuals.accentOnColor : visuals.textPrimary,
                }}
              >
                {on && <Check className="w-3 h-3 inline mr-1" />}
                {item.label}
              </button>
            );
          })}
        </div>

        <div className="space-y-1.5 max-h-56 overflow-y-auto ezd-scroll-thin mb-3">
          {state.installedApps
            .filter((a) => !a.isSystemApp)
            .map((app) => {
              const checked = selected.has(app.packageName);
              return (
                <button
                  key={app.packageName}
                  type="button"
                  onClick={() => togglePkg(app.packageName)}
                  className="w-full rounded-xl border px-3 py-2 flex items-center justify-between gap-2 text-left cursor-pointer"
                  style={{
                    backgroundColor: checked ? `${visuals.accent}1A` : visuals.solidSurfaceBase,
                    borderColor: checked ? visuals.accent : visuals.glassBorder,
                  }}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <AppIconBadge label={app.label} colorHex={app.colorHex} size={32} />
                    <div className="min-w-0">
                      <div className="text-xs font-bold truncate" style={{ color: visuals.textPrimary }}>
                        {app.label}
                      </div>
                      <div className="text-[10px] font-mono truncate" style={{ color: visuals.textSecondary }}>
                        {app.packageName}
                      </div>
                    </div>
                  </div>
                  <StatusLabel label={checked ? 'SELECTED' : 'SKIP'} color={checked ? visuals.accent : visuals.textSecondary} />
                </button>
              );
            })}
        </div>

        <div className="flex justify-end">
          <GlassButton
            text={`Backup ${selected.size} App(s)`}
            disabled={selected.size === 0}
            onClick={() => runFullAppBackup(selected, opts)}
          />
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title={`Local App Snapshots (${state.localAppBackups.length})`} />
        <div className="mt-3 space-y-2">
          {state.localAppBackups.length === 0 ? (
            <p className="text-xs" style={{ color: visuals.textSecondary }}>
              No local app backups yet. Select apps above to create one.
            </p>
          ) : (
            state.localAppBackups.map((rec) => (
              <div
                key={rec.id}
                className="rounded-xl border p-3 flex items-center justify-between gap-2"
                style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
              >
                <div className="min-w-0">
                  <div className="text-xs font-bold truncate" style={{ color: visuals.textPrimary }}>
                    {rec.appLabel} · v{rec.versionName}
                  </div>
                  <div className="text-[11px] font-mono" style={{ color: visuals.textSecondary }}>
                    {rec.parts.join(' + ')} · {(rec.totalSizeBytes / 1_000_000).toFixed(1)} MB
                  </div>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <OutlineButton onClick={() => restoreLocalAppBackup(rec)}>Restore</OutlineButton>
                  <OutlineButton onClick={() => deleteLocalAppBackup(rec)}>
                    <Trash2 className="w-3.5 h-3.5" />
                  </OutlineButton>
                </div>
              </div>
            ))
          )}
        </div>
      </GlassCard>
    </div>
  );
};

export const RootTerminalPanel: React.FC = () => {
  const { state, visuals, runTerminalCommand, showToast } = useEzd();
  const [cmd, setCmd] = useState('');
  const [histCursor, setHistCursor] = useState<number | null>(null);

  const cmdHistory = state.terminalHistory.map((h) => h.command);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!cmd.trim()) return;
    runTerminalCommand(cmd);
    setCmd('');
    setHistCursor(null);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (cmdHistory.length === 0) return;
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      const nextIdx = histCursor === null ? cmdHistory.length - 1 : Math.max(0, histCursor - 1);
      setHistCursor(nextIdx);
      setCmd(cmdHistory[nextIdx]);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (histCursor === null) return;
      if (histCursor >= cmdHistory.length - 1) {
        setHistCursor(null);
        setCmd('');
      } else {
        const nextIdx = histCursor + 1;
        setHistCursor(nextIdx);
        setCmd(cmdHistory[nextIdx]);
      }
    }
  };

  const copyAllOutput = async () => {
    const text = state.terminalHistory
      .map((entry) => `$ ${entry.command}\n${entry.output}`)
      .join('\n\n');
    try {
      await navigator.clipboard.writeText(text || 'No terminal output');
      showToast('Terminal output copied');
    } catch {
      showToast('Clipboard unavailable');
    }
  };

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader
              title={`Interactive Shell (${state.effectivePrivilegeMode})`}
              subtitle="Supports id, uname -a, getprop, ls /sys/devices/system/cpu/cpufreq, free -m, clear"
            />
          </div>
          <div className="flex gap-1.5">
            <OutlineButton onClick={copyAllOutput}>
              <Copy className="w-3.5 h-3.5" />
              <span>Copy</span>
            </OutlineButton>
            <OutlineButton onClick={() => runTerminalCommand('clear')}>Clear</OutlineButton>
          </div>
        </div>

        {/* Quick Command Chips */}
        <div className="flex flex-wrap gap-1.5 mb-3">
          {['id', 'uname -a', 'getprop ro.product.model', 'free -m', 'ls /sys/devices/system/cpu/cpufreq', 'help'].map(
            (quick) => (
              <button
                key={quick}
                type="button"
                onClick={() => runTerminalCommand(quick)}
                className="rounded-xl border px-2.5 py-1 text-[11px] font-mono cursor-pointer hover:brightness-110"
                style={{
                  backgroundColor: visuals.solidSurfaceBase,
                  borderColor: visuals.glassBorder,
                  color: visuals.accent,
                }}
              >
                {quick}
              </button>
            )
          )}
        </div>

        {/* Terminal Output Window */}
        <div
          className="rounded-2xl border p-3.5 font-mono text-xs h-72 overflow-y-auto space-y-3 ezd-scroll-thin"
          style={{
            backgroundColor: '#05070B',
            borderColor: visuals.glassBorder,
            color: '#E2E8F0',
          }}
        >
          {state.terminalHistory.length === 0 && (
            <div className="text-slate-500">
              EZD Shell ready. Type a command below or tap a quick command chip above.
            </div>
          )}
          {state.terminalHistory.map((entry) => (
            <div key={entry.id} className="space-y-1">
              <div className="flex items-center justify-between text-emerald-400">
                <span>
                  {state.effectivePrivilegeMode === 'ROOT' ? '#' : '$'} {entry.command}
                </span>
                <span className="text-[10px] text-slate-500">exit {entry.exitCode}</span>
              </div>
              {entry.output && (
                <pre className="whitespace-pre-wrap text-slate-300 leading-relaxed">{entry.output}</pre>
              )}
            </div>
          ))}
        </div>

        <form onSubmit={handleSubmit} className="mt-3 flex gap-2">
          <input
            type="text"
            value={cmd}
            onChange={(e) => setCmd(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Enter shell command (Up/Down for history)..."
            className="flex-1 rounded-2xl border px-3.5 py-2.5 text-xs font-mono outline-none"
            style={{
              backgroundColor: visuals.solidSurfaceBase,
              borderColor: visuals.glassBorder,
              color: visuals.textPrimary,
            }}
          />
          <GlassButton text="Run" onClick={() => handleSubmit({ preventDefault: () => {} } as React.FormEvent)} />
        </form>
      </GlassCard>
    </div>
  );
};
