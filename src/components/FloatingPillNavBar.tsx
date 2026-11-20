import React, { useState } from 'react';
import {
  AppWindow,
  ChevronLeft,
  ChevronRight,
  Home,
  Settings,
  Wrench,
  X,
} from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { TOOLS_META } from '../data/presets';
import { MainTab, ToolId } from '../types/ezd';

const NAV_ITEMS: { tab: MainTab; label: string; icon: React.FC<{ className?: string }> }[] = [
  { tab: MainTab.HOME, label: 'Home', icon: Home },
  { tab: MainTab.TOOLS, label: 'Tools', icon: Wrench },
  { tab: MainTab.APPS, label: 'Apps', icon: AppWindow },
  { tab: MainTab.SETTINGS, label: 'Settings', icon: Settings },
];

export const FloatingPillNavBar: React.FC = () => {
  const {
    state,
    visuals,
    activeTab,
    setActiveTab,
    openTool,
    pinToolToPill,
    unpinToolFromPill,
    movePinnedPillTool,
  } = useEzd();

  const [dragOver, setDragOver] = useState(false);
  const pinned = state.settings.pinnedPillTools;

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    const toolId = e.dataTransfer.getData('text/ezd-tool-id') as ToolId;
    if (toolId && Object.values(ToolId).includes(toolId)) {
      pinToolToPill(toolId);
    }
  };

  const pillSurfaceStyle: React.CSSProperties = {
    backgroundColor: state.settings.floatingPillOpaque || visuals.isLiteMode
      ? visuals.solidSurfaceElevated
      : visuals.pillBackground,
    borderColor: dragOver ? visuals.accent : visuals.glassBorder,
    backdropFilter:
      !state.settings.floatingPillOpaque && visuals.blurAllowed
        ? `blur(${Math.round(visuals.blurIntensity * 22)}px) saturate(165%)`
        : undefined,
    WebkitBackdropFilter:
      !state.settings.floatingPillOpaque && visuals.blurAllowed
        ? `blur(${Math.round(visuals.blurIntensity * 22)}px) saturate(165%)`
        : undefined,
  };

  return (
    <div
      className="pointer-events-none fixed inset-x-0 bottom-0 z-30 flex flex-col items-center px-3 pb-4 pt-6"
      style={{
        background: `linear-gradient(to top, ${visuals.bgBase}E6 25%, transparent 100%)`,
      }}
    >
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className="pointer-events-auto w-full max-w-md flex flex-col items-center gap-2"
      >
        {pinned.length > 0 && (
          <div
            style={pillSurfaceStyle}
            className="w-full rounded-2xl border px-2.5 py-1.5 shadow-xl flex items-center gap-1.5 overflow-x-auto ezd-scroll-thin"
          >
            {pinned.map((toolId, idx) => {
              const meta = TOOLS_META.find((t) => t.id === toolId);
              const isActive = state.activeTool === toolId;
              return (
                <div
                  key={toolId}
                  className="inline-flex items-center rounded-xl border px-2 py-1 gap-1 shrink-0"
                  style={{
                    backgroundColor: isActive ? `${visuals.accent}24` : visuals.solidSurfaceBase,
                    borderColor: isActive ? visuals.accent : visuals.glassBorder,
                  }}
                >
                  <button
                    type="button"
                    onClick={() => {
                      setActiveTab(MainTab.TOOLS);
                      openTool(toolId);
                    }}
                    className="text-[11px] font-semibold cursor-pointer whitespace-nowrap"
                    style={{ color: isActive ? visuals.accent : visuals.textPrimary }}
                  >
                    {meta?.title ?? toolId}
                  </button>
                  {idx > 0 && (
                    <button
                      type="button"
                      onClick={() => movePinnedPillTool(toolId, -1)}
                      aria-label="Move shortcut left"
                      className="p-0.5 opacity-70 hover:opacity-100 cursor-pointer"
                      style={{ color: visuals.textSecondary }}
                    >
                      <ChevronLeft className="w-3 h-3" />
                    </button>
                  )}
                  {idx < pinned.length - 1 && (
                    <button
                      type="button"
                      onClick={() => movePinnedPillTool(toolId, 1)}
                      aria-label="Move shortcut right"
                      className="p-0.5 opacity-70 hover:opacity-100 cursor-pointer"
                      style={{ color: visuals.textSecondary }}
                    >
                      <ChevronRight className="w-3 h-3" />
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => unpinToolFromPill(toolId)}
                    aria-label="Unpin shortcut"
                    className="p-0.5 opacity-70 hover:opacity-100 cursor-pointer"
                    style={{ color: visuals.textSecondary }}
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>
              );
            })}
          </div>
        )}

        <nav
          aria-label="Primary Navigation"
          style={pillSurfaceStyle}
          className="w-full rounded-full border p-1.5 shadow-2xl grid grid-cols-4 gap-1 transition-all"
        >
          {NAV_ITEMS.map(({ tab, label, icon: Icon }) => {
            const selected = activeTab === tab && !state.activeTool;
            return (
              <button
                key={tab}
                type="button"
                onClick={() => {
                  openTool(null);
                  setActiveTab(tab);
                }}
                style={{
                  backgroundColor: selected ? visuals.accent : 'transparent',
                  color: selected ? visuals.accentOnColor : visuals.textSecondary,
                }}
                className="flex flex-col sm:flex-row items-center justify-center gap-1 sm:gap-1.5 rounded-full py-2 px-2 text-[11px] sm:text-xs font-semibold transition-colors cursor-pointer"
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span>{label}</span>
              </button>
            );
          })}
        </nav>
      </div>
    </div>
  );
};
