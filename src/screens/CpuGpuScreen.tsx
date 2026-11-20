import React, { useState } from 'react';
import { ChevronDown, ChevronUp, Cpu, RotateCcw } from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import {
  GlassCard,
  OutlineButton,
  SectionHeader,
  StatusLabel,
} from '../components/GlassUI';
import { PrivilegeMode } from '../types/ezd';

export const CpuGpuScreen: React.FC = () => {
  const {
    state,
    visuals,
    setCpuFrequencyRange,
    setCpuGovernor,
    resetCpuPolicy,
    toggleCpuCore,
    setGpuFrequency,
    setGpuGovernor,
    resetGpu,
  } = useEzd();

  const [gpuDiagnosticsOpen, setGpuDiagnosticsOpen] = useState(false);
  const canWriteRoot = state.effectivePrivilegeMode === PrivilegeMode.ROOT;
  const gpu = state.gpuState;

  return (
    <div className="space-y-4">
      {/* GPU Section */}
      <GlassCard>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2 flex-wrap">
            <SectionHeader title={`GPU · ${gpu.vendorHint}`} subtitle={gpu.reason} />
            <StatusLabel
              label={gpu.probeStatus}
              color={gpu.probeStatus === 'Applied' ? visuals.successColor : visuals.warningColor}
            />
          </div>
          <OutlineButton onClick={resetGpu} disabled={!canWriteRoot}>
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset Baseline</span>
          </OutlineButton>
        </div>

        <div className="grid grid-cols-3 gap-2.5 mb-4">
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Live Clock</div>
            <div className="text-base font-bold font-mono tabular-nums" style={{ color: visuals.textPrimary }}>
              {gpu.currentMhz ?? '--'} MHz
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>GPU Load</div>
            <div className="text-base font-bold font-mono tabular-nums" style={{ color: visuals.accent }}>
              {gpu.usagePercent ?? '--'}%
            </div>
          </div>
          <div className="rounded-2xl border p-3" style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}>
            <div className="text-[11px]" style={{ color: visuals.textSecondary }}>Governor</div>
            <div className="text-xs font-bold font-mono truncate mt-1" style={{ color: visuals.textPrimary }}>
              {gpu.currentGovernor}
            </div>
          </div>
        </div>

        <div className="space-y-3">
          <div>
            <label className="block text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
              GPU Governor
            </label>
            <select
              disabled={!canWriteRoot}
              value={gpu.currentGovernor}
              onChange={(e) => setGpuGovernor(e.target.value)}
              className="w-full rounded-xl border px-3 py-2 text-sm outline-none disabled:opacity-50"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
                color: visuals.textPrimary,
              }}
            >
              {gpu.availableGovernors.map((gov) => (
                <option key={gov} value={gov}>
                  {gov}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <div className="flex justify-between text-xs mb-1">
                <span style={{ color: visuals.textSecondary }}>Min Frequency</span>
                <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                  {gpu.minMhz ?? gpu.availableMhz[0]} MHz
                </span>
              </div>
              <input
                type="range"
                disabled={!canWriteRoot}
                min={0}
                max={Math.max(0, gpu.availableMhz.length - 1)}
                value={Math.max(0, gpu.availableMhz.indexOf(gpu.minMhz ?? gpu.availableMhz[0]))}
                onChange={(e) => {
                  const chosen = gpu.availableMhz[Number(e.target.value)];
                  if (chosen !== undefined) setGpuFrequency(chosen, null);
                }}
                className="ezd-slider"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1">
                <span style={{ color: visuals.textSecondary }}>Max Frequency Ceiling</span>
                <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                  {gpu.maxMhz ?? gpu.availableMhz[gpu.availableMhz.length - 1]} MHz
                </span>
              </div>
              <input
                type="range"
                disabled={!canWriteRoot}
                min={0}
                max={Math.max(0, gpu.availableMhz.length - 1)}
                value={Math.max(
                  0,
                  gpu.availableMhz.indexOf(gpu.maxMhz ?? gpu.availableMhz[gpu.availableMhz.length - 1])
                )}
                onChange={(e) => {
                  const chosen = gpu.availableMhz[Number(e.target.value)];
                  if (chosen !== undefined) setGpuFrequency(null, chosen);
                }}
                className="ezd-slider"
              />
            </div>
          </div>

          <button
            type="button"
            onClick={() => setGpuDiagnosticsOpen((v) => !v)}
            className="inline-flex items-center gap-1.5 text-xs font-semibold cursor-pointer"
            style={{ color: visuals.accent }}
          >
            <span>Devfreq / KGSL Probe Diagnostics</span>
            {gpuDiagnosticsOpen ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </button>

          {gpuDiagnosticsOpen && (
            <div
              className="rounded-2xl border p-3 text-[11px] font-mono space-y-1"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
                color: visuals.textSecondary,
              }}
            >
              <div>Writable nodes ({gpu.writableNodes.length}): {gpu.writableNodes.join(', ')}</div>
              <div>Paths tried: {gpu.pathsTried.join(', ')}</div>
            </div>
          )}
        </div>
      </GlassCard>

      {/* CPU Clusters Section */}
      <div className="space-y-3">
        {state.cpuPolicies.map((pol) => {
          const minIdx = Math.max(0, pol.availableFreqsKhz.indexOf(pol.minKhz ?? pol.availableFreqsKhz[0]));
          const maxIdx = Math.max(
            0,
            pol.availableFreqsKhz.indexOf(pol.maxKhz ?? pol.availableFreqsKhz[pol.availableFreqsKhz.length - 1])
          );
          return (
            <GlassCard key={pol.id}>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
                <div className="flex items-center gap-2">
                  <Cpu className="w-4 h-4" style={{ color: visuals.accent }} />
                  <div>
                    <div className="text-sm font-bold uppercase font-mono" style={{ color: visuals.textPrimary }}>
                      {pol.id} · CPUs {pol.cpus}
                    </div>
                    <div className="text-xs font-mono tabular-nums" style={{ color: visuals.textSecondary }}>
                      Current: {Math.round((pol.currentKhz ?? 0) / 1000)} MHz · Gov: {pol.currentGovernor}
                    </div>
                  </div>
                </div>
                <OutlineButton onClick={() => resetCpuPolicy(pol)} disabled={!canWriteRoot}>
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Reset</span>
                </OutlineButton>
              </div>

              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-semibold mb-1" style={{ color: visuals.textSecondary }}>
                    Cluster Governor
                  </label>
                  <select
                    disabled={!canWriteRoot || !pol.govWritable}
                    value={pol.currentGovernor}
                    onChange={(e) => setCpuGovernor(pol, e.target.value)}
                    className="w-full rounded-xl border px-3 py-2 text-sm outline-none disabled:opacity-50"
                    style={{
                      backgroundColor: visuals.solidSurfaceBase,
                      borderColor: visuals.glassBorder,
                      color: visuals.textPrimary,
                    }}
                  >
                    {pol.governors.map((g) => (
                      <option key={g} value={g}>
                        {g}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <div className="flex justify-between text-xs mb-1">
                      <span style={{ color: visuals.textSecondary }}>Min Frequency</span>
                      <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                        {Math.round((pol.minKhz ?? 0) / 1000)} MHz
                      </span>
                    </div>
                    <input
                      type="range"
                      disabled={!canWriteRoot || !pol.minWritable}
                      min={0}
                      max={Math.max(0, pol.availableFreqsKhz.length - 1)}
                      value={minIdx}
                      onChange={(e) => {
                        const khz = pol.availableFreqsKhz[Number(e.target.value)];
                        if (khz !== undefined) setCpuFrequencyRange(pol, khz, null);
                      }}
                      className="ezd-slider"
                    />
                  </div>

                  <div>
                    <div className="flex justify-between text-xs mb-1">
                      <span style={{ color: visuals.textSecondary }}>Max Frequency</span>
                      <span className="font-mono font-bold tabular-nums" style={{ color: visuals.textPrimary }}>
                        {Math.round((pol.maxKhz ?? 0) / 1000)} MHz
                      </span>
                    </div>
                    <input
                      type="range"
                      disabled={!canWriteRoot || !pol.maxWritable}
                      min={0}
                      max={Math.max(0, pol.availableFreqsKhz.length - 1)}
                      value={maxIdx}
                      onChange={(e) => {
                        const khz = pol.availableFreqsKhz[Number(e.target.value)];
                        if (khz !== undefined) setCpuFrequencyRange(pol, null, khz);
                      }}
                      className="ezd-slider"
                    />
                  </div>
                </div>
              </div>
            </GlassCard>
          );
        })}
      </div>

      {/* 8-Core Hotplug Grid */}
      <GlassCard>
        <SectionHeader
          title="CPU Core Hotplug"
          subtitle="Tap online toggle per core (CPU0 remains anchored online)"
        />
        <div className="mt-3 grid grid-cols-2 sm:grid-cols-4 gap-2.5">
          {state.cpuCores.map((core) => (
            <button
              key={core.index}
              type="button"
              disabled={!canWriteRoot || !core.hotplugSupported}
              onClick={() => toggleCpuCore(core, !core.online)}
              className="rounded-2xl border p-3 text-left transition-colors disabled:opacity-65 cursor-pointer disabled:cursor-not-allowed"
              style={{
                backgroundColor: core.online ? `${visuals.accent}18` : visuals.solidSurfaceBase,
                borderColor: core.online ? `${visuals.accent}66` : visuals.glassBorder,
              }}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-bold font-mono" style={{ color: visuals.textPrimary }}>
                  CPU{core.index}
                </span>
                <StatusLabel
                  label={core.online ? 'ON' : 'OFF'}
                  color={core.online ? visuals.successColor : visuals.textSecondary}
                />
              </div>
              <div className="mt-1.5 text-xs font-mono tabular-nums" style={{ color: visuals.textSecondary }}>
                {core.online ? `${Math.round((core.currentKhz ?? 0) / 1000)} MHz` : 'Offline'}
              </div>
            </button>
          ))}
        </div>
      </GlassCard>
    </div>
  );
};
