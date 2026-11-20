import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Bell,
  Copy,
  Cpu,
  Power,
  Search,
  X,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { POWER_ACTIONS_META, TOOLS_META } from '../data/presets';
import { MainTab, PowerAction, ToolId } from '../types/ezd';
import { GlassButton, OutlineButton, StatusLabel } from './GlassUI';

export const GlobalSearchDialog: React.FC = () => {
  const {
    state,
    visuals,
    setShowSearchSheet,
    setActiveTab,
    openTool,
    setPendingAppPkg,
    buildSearchIndex,
  } = useEzd();

  const [query, setQuery] = useState('');

  const results = useMemo(() => {
    const toolEntries = TOOLS_META.map((t) => ({
      id: `tool_${t.id}`,
      title: t.title,
      subtitle: t.subtitle,
      category: 'Tools',
      targetTab: MainTab.TOOLS,
      targetTool: t.id as ToolId,
      packageName: undefined as string | undefined,
    }));
    const all = [...buildSearchIndex().slice(0, 3), ...toolEntries, ...buildSearchIndex().slice(3)];
    const q = query.trim().toLowerCase();
    if (!q) return all.slice(0, 14);
    return all.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        item.subtitle.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q)
    );
  }, [query, buildSearchIndex]);

  if (!state.showSearchSheet) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/70 backdrop-blur-sm p-4 pt-14"
      onClick={() => setShowSearchSheet(false)}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-lg rounded-[26px] border p-5 shadow-2xl"
        style={{
          backgroundColor: visuals.solidSurfaceElevated,
          borderColor: visuals.glassBorder,
        }}
      >
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Search className="w-4 h-4" style={{ color: visuals.accent }} />
            <h2 className="text-base font-bold" style={{ color: visuals.textPrimary }}>
              Search Everywhere
            </h2>
          </div>
          <button
            type="button"
            onClick={() => setShowSearchSheet(false)}
            aria-label="Close search"
            className="p-1.5 rounded-full cursor-pointer"
            style={{ color: visuals.textSecondary }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <input
          type="text"
          autoFocus
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search tools, settings, CPU/GPU, apps..."
          className="mt-3 w-full rounded-2xl border px-3.5 py-2.5 text-sm outline-none"
          style={{
            backgroundColor: visuals.solidSurfaceBase,
            borderColor: visuals.glassBorder,
            color: visuals.textPrimary,
          }}
        />

        <div className="mt-3 max-h-80 overflow-y-auto space-y-1.5 ezd-scroll-thin">
          {results.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                setShowSearchSheet(false);
                if (item.targetTool) {
                  setActiveTab(MainTab.TOOLS);
                  openTool(item.targetTool);
                } else {
                  openTool(null);
                  setActiveTab(item.targetTab);
                  if (item.packageName) setPendingAppPkg(item.packageName);
                }
              }}
              className="w-full flex items-center justify-between gap-3 rounded-2xl border px-3.5 py-2.5 text-left transition-colors hover:brightness-110 cursor-pointer"
              style={{
                backgroundColor: visuals.solidSurfaceBase,
                borderColor: visuals.glassBorder,
              }}
            >
              <div className="min-w-0">
                <div className="text-sm font-semibold truncate" style={{ color: visuals.textPrimary }}>
                  {item.title}
                </div>
                <div className="text-xs truncate" style={{ color: visuals.textSecondary }}>
                  {item.subtitle}
                </div>
              </div>
              <StatusLabel label={item.category} color={visuals.accent} />
            </button>
          ))}
          {results.length === 0 && (
            <p className="py-6 text-center text-xs" style={{ color: visuals.textSecondary }}>
              No matching tools, apps, or sections found.
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export const PowerControlSheet: React.FC = () => {
  const { state, visuals, setShowPowerSheet, performPowerAction } = useEzd();
  const [confirmAction, setConfirmAction] = useState<PowerAction | null>(null);

  if (!state.showPowerSheet) return null;

  const selectedMeta = POWER_ACTIONS_META.find((p) => p.action === confirmAction);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/70 backdrop-blur-sm p-3 sm:p-4"
      onClick={() => setShowPowerSheet(false)}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-[26px] border p-5 shadow-2xl"
        style={{
          backgroundColor: visuals.solidSurfaceElevated,
          borderColor: visuals.glassBorder,
        }}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Power className="w-4 h-4" style={{ color: visuals.errorColor }} />
            <h2 className="text-base font-bold" style={{ color: visuals.textPrimary }}>
              Power & Reboot Controls
            </h2>
          </div>
          <button
            type="button"
            onClick={() => setShowPowerSheet(false)}
            aria-label="Close power menu"
            className="p-1.5 rounded-full cursor-pointer"
            style={{ color: visuals.textSecondary }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <p className="mt-1 text-xs" style={{ color: visuals.textSecondary }}>
          Requires Root (uid=0) shell authority to dispatch power state commands.
        </p>

        {!selectedMeta ? (
          <div className="mt-4 grid grid-cols-1 gap-2">
            {POWER_ACTIONS_META.map((item) => {
              const disabled = item.action === PowerAction.EDL && !state.edlSupported;
              return (
                <button
                  key={item.action}
                  type="button"
                  disabled={disabled}
                  onClick={() => setConfirmAction(item.action)}
                  className="flex items-center justify-between rounded-2xl border px-4 py-3 text-left transition-colors hover:brightness-110 disabled:opacity-45 cursor-pointer"
                  style={{
                    backgroundColor: visuals.solidSurfaceBase,
                    borderColor: visuals.glassBorder,
                  }}
                >
                  <div>
                    <div className="text-sm font-semibold" style={{ color: visuals.textPrimary }}>
                      {item.title}
                    </div>
                    <div className="text-xs" style={{ color: visuals.textSecondary }}>
                      {item.description}
                    </div>
                  </div>
                  <StatusLabel
                    label={item.action === PowerAction.EDL ? 'QCOM' : 'ROOT'}
                    color={item.action === PowerAction.POWER_OFF ? visuals.errorColor : visuals.accent}
                  />
                </button>
              );
            })}
          </div>
        ) : (
          <div
            className="mt-4 rounded-2xl border p-4 space-y-3"
            style={{
              backgroundColor: visuals.solidSurfaceBase,
              borderColor: `${visuals.errorColor}55`,
            }}
          >
            <div className="flex items-center gap-2" style={{ color: visuals.errorColor }}>
              <AlertTriangle className="w-5 h-5" />
              <span className="font-bold text-sm">Confirm {selectedMeta.title}</span>
            </div>
            <p className="text-xs" style={{ color: visuals.textSecondary }}>
              {selectedMeta.destructiveNote}
            </p>
            <div className="flex gap-2 justify-end">
              <OutlineButton onClick={() => setConfirmAction(null)}>Cancel</OutlineButton>
              <GlassButton
                text={`Execute ${selectedMeta.title}`}
                onClick={() => {
                  const act = selectedMeta.action;
                  setConfirmAction(null);
                  performPowerAction(act);
                }}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export const NotificationsSheet: React.FC = () => {
  const { state, daemonStats, visuals, setShowNotificationsSheet } = useEzd();

  if (!state.showNotificationsSheet) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/70 backdrop-blur-sm p-3 sm:p-4"
      onClick={() => setShowNotificationsSheet(false)}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-[26px] border p-5 shadow-2xl"
        style={{
          backgroundColor: visuals.solidSurfaceElevated,
          borderColor: visuals.glassBorder,
        }}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bell className="w-4 h-4" style={{ color: visuals.accent }} />
            <h2 className="text-base font-bold" style={{ color: visuals.textPrimary }}>
              Activity & Notifications
            </h2>
          </div>
          <button
            type="button"
            onClick={() => setShowNotificationsSheet(false)}
            aria-label="Close notifications"
            className="p-1.5 rounded-full cursor-pointer"
            style={{ color: visuals.textSecondary }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div
          className="mt-3 rounded-2xl border p-3.5"
          style={{
            backgroundColor: visuals.solidSurfaceBase,
            borderColor: visuals.glassBorder,
          }}
        >
          <div className="text-[11px] font-bold uppercase tracking-wider" style={{ color: visuals.accent }}>
            Latest Status
          </div>
          <p className="mt-1 text-sm" style={{ color: visuals.textPrimary }}>
            {state.settings.notifications.lastNotificationText}
          </p>
        </div>

        <div className="mt-3">
          <div className="text-xs font-semibold mb-1.5" style={{ color: visuals.textSecondary }}>
            Daemon Event Stream ({daemonStats.commandsExecuted} commands)
          </div>
          <div
            className="max-h-56 overflow-y-auto rounded-2xl border p-3 font-mono text-[11px] space-y-1 ezd-scroll-thin"
            style={{
              backgroundColor: visuals.solidSurfaceBase,
              borderColor: visuals.glassBorder,
              color: visuals.textSecondary,
            }}
          >
            {daemonStats.logs.map((line, i) => (
              <div key={i} className="leading-relaxed">
                {line}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export const DeviceDetailsDialog: React.FC = () => {
  const { state, visuals, setShowDeviceDetails, buildDeviceReportText, showToast } = useEzd();

  if (!state.showDeviceDetails) return null;

  const report = buildDeviceReportText();

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(report);
      showToast('Device specifications copied');
    } catch {
      showToast('Clipboard unavailable');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
      onClick={() => setShowDeviceDetails(false)}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-lg rounded-[26px] border p-5 shadow-2xl"
        style={{
          backgroundColor: visuals.solidSurfaceElevated,
          borderColor: visuals.glassBorder,
        }}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Cpu className="w-4 h-4" style={{ color: visuals.accent }} />
            <h2 className="text-base font-bold" style={{ color: visuals.textPrimary }}>
              Device Specifications
            </h2>
          </div>
          <button
            type="button"
            onClick={() => setShowDeviceDetails(false)}
            aria-label="Close specifications"
            className="p-1.5 rounded-full cursor-pointer"
            style={{ color: visuals.textSecondary }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <pre
          className="mt-3 max-h-80 overflow-y-auto rounded-2xl border p-3.5 text-xs font-mono whitespace-pre-wrap ezd-scroll-thin"
          style={{
            backgroundColor: visuals.solidSurfaceBase,
            borderColor: visuals.glassBorder,
            color: visuals.textPrimary,
          }}
        >
          {report}
        </pre>

        <div className="mt-4 flex justify-end gap-2">
          <OutlineButton onClick={handleCopy}>
            <Copy className="w-3.5 h-3.5" />
            <span>Copy Specs</span>
          </OutlineButton>
          <GlassButton text="Done" onClick={() => setShowDeviceDetails(false)} />
        </div>
      </div>
    </div>
  );
};

export const GlobalToast: React.FC = () => {
  const { toastMessage, visuals } = useEzd();
  if (!toastMessage) return null;

  return (
    <div className="pointer-events-none fixed top-16 inset-x-0 z-50 flex justify-center px-4">
      <div
        className="rounded-full border px-4 py-2 text-xs font-semibold shadow-2xl"
        style={{
          backgroundColor: visuals.solidSurfaceElevated,
          borderColor: `${visuals.accent}66`,
          color: visuals.textPrimary,
        }}
      >
        {toastMessage}
      </div>
    </div>
  );
};
