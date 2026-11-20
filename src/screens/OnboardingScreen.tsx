import React, { useState } from 'react';
import { Camera, CheckCircle2, RefreshCw, Shield, Smartphone } from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { GlassButton, GlassCard, OutlineButton, StatusLabel } from '../components/GlassUI';
import { WORK_MODE_META } from '../data/workMode';
import { RootStatus, ShizukuStatus, WorkMode } from '../types/ezd';

export const OnboardingScreen: React.FC = () => {
  const {
    state,
    visuals,
    autoDetectRoot,
    requestShizukuPermission,
    refreshPrivilegeModes,
    completeOnboarding,
  } = useEzd();

  const [name, setName] = useState(state.settings.userName || 'Evorez User');
  const [tagline, setTagline] = useState(state.settings.userTagline || 'Device Tuner');
  const [photoDataUrl, setPhotoDataUrl] = useState(state.settings.userPhotoDataUrl || '');
  const [workMode, setWorkMode] = useState<WorkMode>(state.settings.workMode);

  const handlePhotoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') setPhotoDataUrl(reader.result);
    };
    reader.readAsDataURL(file);
  };

  const rootGranted = state.rootStatus === RootStatus.GRANTED;
  const shizukuGranted = state.shizukuStatus === ShizukuStatus.GRANTED;

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-8 space-y-5">
      <div className="text-center space-y-2">
        <img
          src="/assets/evorez_logo_circular.png"
          alt="EZD Manager"
          className="mx-auto w-16 h-16 rounded-full object-cover border shadow-xl"
          style={{ borderColor: visuals.glassBorder }}
        />
        <h1 className="text-2xl font-bold tracking-tight" style={{ color: visuals.textPrimary }}>
          Welcome to EZD Manager
        </h1>
        <p className="text-sm" style={{ color: visuals.textSecondary }}>
          Configure your profile and kernel privilege mode before entering the workstation.
        </p>
      </div>

      {/* User Profile Setup */}
      <GlassCard>
        <div className="flex flex-col sm:flex-row items-center gap-4">
          <label className="relative cursor-pointer group shrink-0">
            <input type="file" accept="image/*" onChange={handlePhotoChange} className="hidden" />
            <img
              src={photoDataUrl || '/assets/evorez_avatar.png'}
              alt="Profile Avatar"
              className="w-20 h-20 rounded-full object-cover border-2 transition-transform group-hover:scale-105"
              style={{ borderColor: visuals.accent }}
            />
            <span
              className="absolute bottom-0 right-0 p-1.5 rounded-full border shadow"
              style={{
                backgroundColor: visuals.accent,
                color: visuals.accentOnColor,
                borderColor: visuals.solidSurfaceBase,
              }}
            >
              <Camera className="w-3.5 h-3.5" />
            </span>
          </label>

          <div className="w-full space-y-3">
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: visuals.textSecondary }}>
                Display Name
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Evorez User"
                className="w-full rounded-xl border px-3 py-2 text-sm outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceBase,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: visuals.textSecondary }}>
                Subtitle / Tagline
              </label>
              <input
                type="text"
                value={tagline}
                onChange={(e) => setTagline(e.target.value)}
                placeholder="Device Tuner"
                className="w-full rounded-xl border px-3 py-2 text-sm outline-none"
                style={{
                  backgroundColor: visuals.solidSurfaceBase,
                  borderColor: visuals.glassBorder,
                  color: visuals.textPrimary,
                }}
              />
            </div>
          </div>
        </div>
      </GlassCard>

      {/* Root & Shizuku Detection */}
      <GlassCard>
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <Shield className="w-4 h-4" style={{ color: visuals.accent }} />
            <h2 className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
              Authority & Privilege Detection
            </h2>
          </div>
          <OutlineButton onClick={refreshPrivilegeModes}>
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </OutlineButton>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div
            className="rounded-2xl border p-3.5 flex flex-col justify-between gap-3"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Root (su · {state.rootManagerType})
              </span>
              <StatusLabel
                label={rootGranted ? 'GRANTED' : state.rootStatus}
                color={rootGranted ? visuals.successColor : visuals.warningColor}
              />
            </div>
            <OutlineButton onClick={() => autoDetectRoot(true)} className="w-full justify-center">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>{rootGranted ? 'Re-Verify Root (su)' : 'Request Root Access'}</span>
            </OutlineButton>
          </div>

          <div
            className="rounded-2xl border p-3.5 flex flex-col justify-between gap-3"
            style={{ backgroundColor: visuals.solidSurfaceBase, borderColor: visuals.glassBorder }}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-bold" style={{ color: visuals.textPrimary }}>
                Shizuku Binder
              </span>
              <StatusLabel
                label={shizukuGranted ? 'GRANTED' : state.shizukuStatus}
                color={shizukuGranted ? visuals.accent : visuals.warningColor}
              />
            </div>
            <OutlineButton onClick={requestShizukuPermission} className="w-full justify-center">
              <Smartphone className="w-3.5 h-3.5" />
              <span>{shizukuGranted ? 'Shizuku Authorized' : 'Request Shizuku'}</span>
            </OutlineButton>
          </div>
        </div>
      </GlassCard>

      {/* Preferred Work Mode */}
      <GlassCard>
        <h2 className="text-sm font-bold mb-2.5" style={{ color: visuals.textPrimary }}>
          Preferred Work Mode
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
          {Object.values(WorkMode).map((mode) => {
            const selected = workMode === mode;
            const meta = WORK_MODE_META[mode];
            return (
              <button
                key={mode}
                type="button"
                onClick={() => setWorkMode(mode)}
                className="rounded-2xl border p-3 text-left transition-colors cursor-pointer"
                style={{
                  backgroundColor: selected ? `${visuals.accent}1E` : visuals.solidSurfaceBase,
                  borderColor: selected ? visuals.accent : visuals.glassBorder,
                }}
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold" style={{ color: visuals.textPrimary }}>
                    {meta.label}
                  </span>
                  {selected && <StatusLabel label="ACTIVE" color={visuals.accent} />}
                </div>
                <p className="mt-1 text-xs" style={{ color: visuals.textSecondary }}>
                  {meta.subtitle}
                </p>
              </button>
            );
          })}
        </div>
      </GlassCard>

      <div className="flex flex-col sm:flex-row gap-3 pt-1">
        <GlassButton
          text="Enter EZD Manager"
          onClick={() => completeOnboarding(name, tagline, photoDataUrl, workMode)}
          className="flex-1 justify-center"
        />
        <OutlineButton
          onClick={() => completeOnboarding('Evorez User', 'Device Tuner', '', WorkMode.AUTO)}
          className="justify-center"
        >
          Skip & Use Default
        </OutlineButton>
      </div>
    </div>
  );
};
