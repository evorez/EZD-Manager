import React, { useState } from 'react';
import {
  AlertTriangle,
  ExternalLink,
  FileCode2,
  Flame,
  Layers,
  Plus,
  Puzzle,
  RefreshCw,
  ShieldAlert,
  Trash2,
  X,
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
import { GpuRendererMode, PrivilegeMode, RootModuleInfo, ThermalProfileMode } from '../types/ezd';

export const ZramVmPanel: React.FC = () => {
  const { state, visuals, refreshZramVm, applyZramConfig, applyVmTweaks } = useEzd();
  const z = state.zramVmState;
  const isRoot = state.effectivePrivilegeMode === PrivilegeMode.ROOT;

  const [swappiness, setSwappiness] = useState(z.swappiness);
  const [vfsCache, setVfsCache] = useState(z.vfsCachePressure);
  const [dirtyRatio, setDirtyRatio] = useState(z.dirtyRatio);
  const [dirtyBg, setDirtyBg] = useState(z.dirtyBackgroundRatio);

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Layers className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="ZRAM Compressed Swap" subtitle={z.statusNote} />
          </div>
          <OutlineButton onClick={refreshZramVm}>
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </OutlineButton>
        </div>

        <div className="grid grid-cols-3 gap-2 mb-4">
          <div className="rounded-xl border p-2.5" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[10px]" style={{ color: visuals.textSecondary }}>Disk Size</div>
            <div className="text-sm font-bold font-mono tabular-nums">{z.diskSizeMb} MB</div>
          </div>
          <div className="rounded-xl border p-2.5" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[10px]" style={{ color: visuals.textSecondary }}>Compressed Used</div>
            <div className="text-sm font-bold font-mono tabular-nums">{z.usedMemMb} MB</div>
          </div>
          <div className="rounded-xl border p-2.5" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[10px]" style={{ color: visuals.textSecondary }}>Algorithm</div>
            <div className="text-sm font-bold font-mono uppercase">{z.currentAlgorithm}</div>
          </div>
        </div>

        <div className="space-y-3">
          <div>
            <div className="text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
              Swap Size Preset
            </div>
            <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5">
              {[0, 1024, 2048, 4096, 6144, 8192].map((mb) => {
                const active = z.diskSizeMb === mb;
                return (
                  <button
                    key={mb}
                    type="button"
                    disabled={!isRoot}
                    onClick={() => applyZramConfig(mb, z.currentAlgorithm)}
                    className="rounded-xl border py-2 text-xs font-bold font-mono cursor-pointer disabled:opacity-50"
                    style={{
                      backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                      borderColor: active ? visuals.accent : visuals.glassBorder,
                      color: active ? visuals.accentOnColor : visuals.textPrimary,
                    }}
                  >
                    {mb === 0 ? 'OFF' : `${mb / 1024}G`}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <div className="text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
              Compression Algorithm
            </div>
            <div className="grid grid-cols-4 gap-2">
              {z.availableAlgorithms.map((algo) => {
                const active = z.currentAlgorithm === algo;
                return (
                  <button
                    key={algo}
                    type="button"
                    disabled={!isRoot}
                    onClick={() => applyZramConfig(z.diskSizeMb, algo)}
                    className="rounded-xl border py-2 text-xs font-bold font-mono cursor-pointer disabled:opacity-50"
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
          </div>
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Linux Virtual Memory (/proc/sys/vm)" />
        <div className="mt-3 space-y-3">
          {[
            { label: 'vm.swappiness', val: swappiness, set: setSwappiness, min: 0, max: 160, step: 5 },
            { label: 'vm.vfs_cache_pressure', val: vfsCache, set: setVfsCache, min: 10, max: 200, step: 10 },
            { label: 'vm.dirty_ratio', val: dirtyRatio, set: setDirtyRatio, min: 5, max: 50, step: 5 },
            { label: 'vm.dirty_background_ratio', val: dirtyBg, set: setDirtyBg, min: 2, max: 30, step: 2 },
          ].map((item) => (
            <div key={item.label}>
              <div className="flex justify-between text-xs mb-1">
                <span className="font-mono" style={{ color: visuals.textSecondary }}>{item.label}</span>
                <span className="font-mono font-bold tabular-nums">{item.val}</span>
              </div>
              <input
                type="range"
                disabled={!isRoot}
                min={item.min}
                max={item.max}
                step={item.step}
                value={item.val}
                onChange={(e) => item.set(Number(e.target.value))}
                className="ezd-slider"
              />
            </div>
          ))}
          <div className="flex justify-end pt-1">
            <GlassButton
              text="Apply VM Parameters"
              disabled={!isRoot}
              onClick={() => applyVmTweaks(swappiness, vfsCache, dirtyRatio, dirtyBg)}
            />
          </div>
        </div>
      </GlassCard>
    </div>
  );
};

export const KernelTweaksPanel: React.FC = () => {
  const {
    state,
    visuals,
    refreshIoThermalUi,
    setIoScheduler,
    setIoReadAhead,
    applyThermalProfile,
    setSystemAnimationScale,
    setGpuRenderer,
  } = useEzd();

  const k = state.ioThermalUiState;
  const isRoot = state.effectivePrivilegeMode === PrivilegeMode.ROOT;

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Flame className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="I/O Block Schedulers & Read-Ahead" subtitle={k.ioStatusNote} />
          </div>
          <OutlineButton onClick={refreshIoThermalUi}>
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </OutlineButton>
        </div>

        <div className="space-y-3">
          {k.blockDevices.map((dev) => (
            <div
              key={dev.name}
              className="rounded-2xl border p-3.5 space-y-2.5"
              style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold font-mono">/dev/block/{dev.name}</span>
                <span className="text-[11px] font-mono" style={{ color: visuals.accent }}>
                  {dev.currentScheduler} · {dev.readAheadKb} KB
                </span>
              </div>

              <div className="flex flex-wrap gap-1.5">
                {dev.availableSchedulers.map((sched) => {
                  const active = dev.currentScheduler === sched;
                  return (
                    <button
                      key={sched}
                      type="button"
                      disabled={!isRoot}
                      onClick={() => setIoScheduler(dev, sched)}
                      className="rounded-xl border px-2.5 py-1 text-xs font-mono font-semibold cursor-pointer disabled:opacity-50"
                      style={{
                        backgroundColor: active ? visuals.accent : visuals.solidSurfaceElevated,
                        borderColor: active ? visuals.accent : visuals.glassBorder,
                        color: active ? visuals.accentOnColor : visuals.textPrimary,
                      }}
                    >
                      {sched}
                    </button>
                  );
                })}
              </div>

              <div className="flex flex-wrap gap-1.5">
                {[128, 256, 512, 1024, 2048].map((kb) => {
                  const active = dev.readAheadKb === kb;
                  return (
                    <button
                      key={kb}
                      type="button"
                      disabled={!isRoot}
                      onClick={() => setIoReadAhead(dev, kb)}
                      className="rounded-xl border px-2.5 py-1 text-[11px] font-mono cursor-pointer disabled:opacity-50"
                      style={{
                        backgroundColor: active ? `${visuals.accent}24` : visuals.solidSurfaceElevated,
                        borderColor: active ? visuals.accent : visuals.glassBorder,
                        color: visuals.textPrimary,
                      }}
                    >
                      {kb} KB
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Thermal Throttling Profile" subtitle={k.thermalBackendNote} />
        <div className="mt-3 grid grid-cols-2 sm:grid-cols-5 gap-2">
          {Object.values(ThermalProfileMode).map((mode) => {
            const active = k.thermalProfile === mode;
            return (
              <button
                key={mode}
                type="button"
                disabled={!isRoot}
                onClick={() => applyThermalProfile(mode)}
                className="rounded-xl border py-2 px-2 text-xs font-bold cursor-pointer disabled:opacity-50"
                style={{
                  backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                  borderColor: active ? visuals.accent : visuals.glassBorder,
                  color: active ? visuals.accentOnColor : visuals.textPrimary,
                }}
              >
                {mode.replace('_', ' ')}
              </button>
            );
          })}
        </div>
      </GlassCard>

      <GlassCard>
        <SectionHeader title="UI Animation Scale & HWUI Renderer" />
        <div className="mt-3 space-y-4">
          <div>
            <div className="text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
              Window / Transition / Animator Scale ({k.windowAnimScale}×)
            </div>
            <div className="grid grid-cols-5 gap-1.5">
              {[0, 0.25, 0.5, 0.75, 1].map((scale) => {
                const active = k.windowAnimScale === scale;
                return (
                  <button
                    key={scale}
                    type="button"
                    onClick={() => setSystemAnimationScale(scale)}
                    className="rounded-xl border py-2 text-xs font-mono font-bold cursor-pointer"
                    style={{
                      backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                      borderColor: active ? visuals.accent : visuals.glassBorder,
                      color: active ? visuals.accentOnColor : visuals.textPrimary,
                    }}
                  >
                    {scale}×
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <div className="text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
              HWUI GPU Rendering Backend
            </div>
            <div className="grid grid-cols-3 gap-2">
              {Object.values(GpuRendererMode).map((rend) => {
                const active = k.gpuRenderer === rend;
                return (
                  <button
                    key={rend}
                    type="button"
                    disabled={!isRoot}
                    onClick={() => setGpuRenderer(rend)}
                    className="rounded-xl border py-2 px-2 text-xs font-bold cursor-pointer disabled:opacity-50"
                    style={{
                      backgroundColor: active ? visuals.accent : visuals.solidSurfaceBase,
                      borderColor: active ? visuals.accent : visuals.glassBorder,
                      color: active ? visuals.accentOnColor : visuals.textPrimary,
                    }}
                  >
                    {rend.replace('_', ' ')}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </GlassCard>
    </div>
  );
};

export const RootModulesPanel: React.FC = () => {
  const { state, visuals, refreshRootModules, toggleRootModule, toggleRootModuleRemove } = useEzd();
  const [webUiMod, setWebUiMod] = useState<RootModuleInfo | null>(null);

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Puzzle className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="Magisk / KernelSU Modules" subtitle="/data/adb/modules" />
          </div>
          <OutlineButton onClick={refreshRootModules}>
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Scan</span>
          </OutlineButton>
        </div>

        <div className="space-y-2.5">
          {state.rootModules.map((mod) => (
            <div
              key={mod.id}
              className="rounded-2xl border p-3.5 space-y-2"
              style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
                      {mod.name}
                    </span>
                    <StatusLabel label={mod.version} color={visuals.accent} />
                    <StatusLabel label={mod.managerType} color={visuals.textSecondary} />
                    {mod.pendingRemove && <StatusLabel label="PENDING REMOVE" color={visuals.errorColor} />}
                  </div>
                  <div className="text-[11px]" style={{ color: visuals.textSecondary }}>
                    by {mod.author} · {mod.description}
                  </div>
                </div>

                <ToggleSwitch
                  checked={mod.enabled}
                  onChange={(en) => toggleRootModule(mod, en)}
                  ariaLabel={`Toggle ${mod.name}`}
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-1">
                {mod.hasWebUi && (
                  <OutlineButton onClick={() => setWebUiMod(mod)}>
                    <ExternalLink className="w-3.5 h-3.5" />
                    <span>Open WebUI</span>
                  </OutlineButton>
                )}
                <OutlineButton onClick={() => toggleRootModuleRemove(mod, !mod.pendingRemove)}>
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>{mod.pendingRemove ? 'Undo Remove' : 'Remove on Reboot'}</span>
                </OutlineButton>
              </div>
            </div>
          ))}
        </div>
      </GlassCard>

      {webUiMod && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4"
          onClick={() => setWebUiMod(null)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-lg rounded-[26px] border p-4 shadow-2xl space-y-3"
            style={{ backgroundColor: visuals.solidSurfaceElevated, borderColor: visuals.glassBorder }}
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">{webUiMod.name} WebUI</h3>
              <button type="button" onClick={() => setWebUiMod(null)} className="p-1 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>
            <iframe
              title={`${webUiMod.name} WebUI`}
              sandbox=""
              srcDoc={webUiMod.webUiHtml ?? '<p>Module WebUI</p>'}
              className="w-full h-64 rounded-2xl border bg-black"
              style={{ borderColor: visuals.glassBorder }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export const BuildPropEditorPanel: React.FC = () => {
  const { state, visuals, createBuildPropBackup, restoreBuildPropBackup, updateBuildProp } = useEzd();
  const [search, setSearch] = useState('');
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editingVal, setEditingVal] = useState('');
  const [newKey, setNewKey] = useState('');
  const [newVal, setNewVal] = useState('');

  const filtered = state.buildProps.filter(
    (p) =>
      !search.trim() ||
      p.key.toLowerCase().includes(search.toLowerCase()) ||
      p.value.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <FileCode2 className="w-4 h-4" style={{ color: visuals.accent }} />
            <SectionHeader title="Safe Build.prop Editor" subtitle="Auto-backups created before every write" />
          </div>
          <OutlineButton onClick={createBuildPropBackup}>Create Backup</OutlineButton>
        </div>

        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search property key or value..."
          className="w-full rounded-xl border px-3 py-2 text-xs font-mono outline-none mb-3"
          style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
        />

        <div className="space-y-2 max-h-72 overflow-y-auto ezd-scroll-thin">
          {filtered.map((entry) => (
            <div
              key={entry.key}
              className="rounded-xl border p-2.5 flex items-center justify-between gap-2"
              style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
            >
              <div className="min-w-0">
                <div className="text-xs font-mono font-bold truncate" style={{ color: visuals.accent }}>
                  {entry.key}
                </div>
                <div className="text-xs font-mono truncate" style={{ color: visuals.textPrimary }}>
                  {entry.value} <span className="text-[10px]" style={{ color: visuals.textSecondary }}>({entry.sourceFile})</span>
                </div>
              </div>
              <OutlineButton
                onClick={() => {
                  setEditingKey(entry.key);
                  setEditingVal(entry.value);
                }}
              >
                Edit
              </OutlineButton>
            </div>
          ))}
        </div>

        {editingKey && (
          <div
            className="mt-3 rounded-2xl border p-3 space-y-2"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.accent }}
          >
            <div className="text-xs font-mono font-bold" style={{ color: visuals.accent }}>
              Editing: {editingKey}
            </div>
            <input
              type="text"
              value={editingVal}
              onChange={(e) => setEditingVal(e.target.value)}
              className="w-full rounded-xl border px-3 py-2 text-xs font-mono outline-none"
              style={{ backgroundColor: visuals.solidSurfaceElevated, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
            />
            <div className="flex justify-end gap-2">
              <OutlineButton onClick={() => setEditingKey(null)}>Cancel</OutlineButton>
              <GlassButton
                text="Auto-Backup & Save"
                onClick={() => {
                  updateBuildProp(editingKey, editingVal);
                  setEditingKey(null);
                }}
              />
            </div>
          </div>
        )}
      </GlassCard>

      <GlassCard>
        <SectionHeader title="Add New Property" />
        <div className="mt-3 flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            placeholder="Property Key (e.g. persist.sys.scrollingcache)"
            value={newKey}
            onChange={(e) => setNewKey(e.target.value)}
            className="flex-1 rounded-xl border px-3 py-2 text-xs font-mono outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
          <input
            type="text"
            placeholder="Value"
            value={newVal}
            onChange={(e) => setNewVal(e.target.value)}
            className="w-full sm:w-36 rounded-xl border px-3 py-2 text-xs font-mono outline-none"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder, color: visuals.textPrimary }}
          />
          <GlassButton
            text="Add"
            icon={<Plus className="w-3.5 h-3.5" />}
            onClick={() => {
              if (!newKey.trim()) return;
              updateBuildProp(newKey.trim(), newVal.trim());
              setNewKey('');
              setNewVal('');
            }}
          />
        </div>
      </GlassCard>

      {state.buildPropBackups.length > 0 && (
        <GlassCard>
          <SectionHeader title={`Build.prop Snapshots (${state.buildPropBackups.length})`} />
          <div className="mt-3 space-y-2">
            {state.buildPropBackups.map((bk) => (
              <div
                key={bk.id}
                className="rounded-xl border p-2.5 flex items-center justify-between gap-2"
                style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
              >
                <div className="min-w-0">
                  <div className="text-xs font-mono font-bold truncate">{bk.fileName}</div>
                  <div className="text-[10px]" style={{ color: visuals.textSecondary }}>
                    {bk.entryCount} entries · {bk.createdAt}
                  </div>
                </div>
                <OutlineButton onClick={() => restoreBuildPropBackup(bk)}>Restore</OutlineButton>
              </div>
            ))}
          </div>
        </GlassCard>
      )}
    </div>
  );
};

export const BootGuardPanel: React.FC = () => {
  const {
    state,
    visuals,
    clearBootSafeMode,
    testSimulateBootLoopGuard,
    setApplyOnBoot,
    captureBootMemorySnapshot,
    applyBootMemoryNow,
  } = useEzd();

  const bg = state.bootGuardState;

  return (
    <div className="space-y-4">
      <GlassCard highlightBorder={bg.safeModeActive}>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <ShieldAlert
              className="w-5 h-5"
              style={{ color: bg.safeModeActive ? visuals.errorColor : visuals.successColor }}
            />
            <SectionHeader
              title="3-Boot Safe Mode Guard"
              subtitle={bg.safeModeActive ? 'SAFE MODE ENGAGED' : 'Boot sequence verified healthy'}
            />
          </div>
          <StatusLabel
            label={bg.safeModeActive ? 'SAFE MODE' : 'HEALTHY'}
            color={bg.safeModeActive ? visuals.errorColor : visuals.successColor}
          />
        </div>

        <p className="text-xs leading-relaxed" style={{ color: visuals.textSecondary }}>
          If the device fails to stabilize within 3 consecutive boots after applying startup kernel tweaks, EZD automatically pauses Boot Memory and flags Safe Mode.
        </p>

        {bg.lastReason && (
          <div
            className="mt-3 rounded-xl border p-3 flex items-start gap-2 text-xs"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: `${visuals.warningColor}55` }}
          >
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" style={{ color: visuals.warningColor }} />
            <span>{bg.lastReason}</span>
          </div>
        )}

        <div className="mt-4 flex flex-wrap gap-2">
          {bg.safeModeActive ? (
            <GlassButton text="Clear Safe Mode & Re-Arm" onClick={clearBootSafeMode} />
          ) : (
            <OutlineButton onClick={testSimulateBootLoopGuard}>
              Simulate 3-Fail Boot Guard
            </OutlineButton>
          )}
        </div>
      </GlassCard>

      <GlassCard>
        <div className="flex items-center justify-between gap-3 mb-3">
          <SectionHeader title="Boot Memory Snapshot" subtitle={state.settings.bootMemory.summary} />
          <ToggleSwitch checked={state.settings.bootMemory.applyOnBoot} onChange={setApplyOnBoot} />
        </div>
        <div className="flex flex-wrap gap-2">
          <GlassButton text="Capture Snapshot" onClick={captureBootMemorySnapshot} />
          <OutlineButton onClick={applyBootMemoryNow}>Apply Snapshot Now</OutlineButton>
        </div>
      </GlassCard>
    </div>
  );
};
