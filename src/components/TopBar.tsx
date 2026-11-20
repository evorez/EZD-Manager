import React from 'react';
import { Bell, Power, Search, Settings } from 'lucide-react';
import { useEzd } from '../context/EzdContext';
import { MainTab } from '../types/ezd';

const TAB_TITLES: Record<MainTab, string> = {
  [MainTab.HOME]: 'EZD Manager',
  [MainTab.TOOLS]: 'Tools & Engine',
  [MainTab.APPS]: 'Per-App Tuning',
  [MainTab.SETTINGS]: 'Settings',
};

export const TopBar: React.FC = () => {
  const {
    state,
    visuals,
    activeTab,
    setActiveTab,
    openTool,
    setShowSearchSheet,
    setShowNotificationsSheet,
    setShowPowerSheet,
    recordBrandLogoTap,
  } = useEzd();

  const unread = state.settings.notifications.unreadCount;

  return (
    <header
      className="sticky top-0 z-30 border-b transition-colors"
      style={{
        backgroundColor: visuals.blurAllowed ? visuals.pillBackground : visuals.solidSurfaceBase,
        borderColor: visuals.glassBorder,
        backdropFilter: visuals.blurAllowed ? `blur(${Math.round(visuals.blurIntensity * 18)}px)` : undefined,
        WebkitBackdropFilter: visuals.blurAllowed ? `blur(${Math.round(visuals.blurIntensity * 18)}px)` : undefined,
      }}
    >
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={recordBrandLogoTap}
            title="EZD Manager (Tap 7x for Reporting & Requesting)"
            className="shrink-0 rounded-full focus-visible:outline-2 cursor-pointer transition-transform active:scale-95"
          >
            <img
              src="/assets/evorez_logo_circular.png"
              alt="Evorez Logo"
              className="w-9 h-9 rounded-full object-cover border"
              style={{ borderColor: visuals.glassBorder }}
            />
          </button>
          <div className="min-w-0">
            <h1 className="text-base sm:text-lg font-bold tracking-tight truncate" style={{ color: visuals.textPrimary }}>
              {TAB_TITLES[activeTab]}
            </h1>
            <div className="flex items-center gap-2 text-[11px] font-mono truncate" style={{ color: visuals.textSecondary }}>
              <span>{state.effectivePrivilegeMode}</span>
              <span>·</span>
              <span className="truncate">{state.overview.model}</span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            onClick={() => setShowSearchSheet(true)}
            aria-label="Search Everywhere"
            title="Search Everywhere"
            className="p-2 rounded-full border transition-colors hover:brightness-110 cursor-pointer"
            style={{
              backgroundColor: visuals.solidSurfaceElevated,
              borderColor: visuals.glassBorder,
              color: visuals.textPrimary,
            }}
          >
            <Search className="w-4 h-4" />
          </button>

          <button
            type="button"
            onClick={() => setShowNotificationsSheet(true)}
            aria-label="Notifications"
            title="Activity & Notifications"
            className="relative p-2 rounded-full border transition-colors hover:brightness-110 cursor-pointer"
            style={{
              backgroundColor: visuals.solidSurfaceElevated,
              borderColor: visuals.glassBorder,
              color: visuals.textPrimary,
            }}
          >
            <Bell className="w-4 h-4" />
            {unread > 0 && (
              <span
                className="absolute -top-1 -right-1 min-w-[17px] h-[17px] px-1 rounded-full text-[10px] font-bold flex items-center justify-center"
                style={{ backgroundColor: visuals.accent, color: visuals.accentOnColor }}
              >
                {unread}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setShowPowerSheet(true)}
            aria-label="Power & Reboot"
            title="Power & Reboot"
            className="p-2 rounded-full border transition-colors hover:brightness-110 cursor-pointer"
            style={{
              backgroundColor: visuals.solidSurfaceElevated,
              borderColor: visuals.glassBorder,
              color: visuals.errorColor,
            }}
          >
            <Power className="w-4 h-4" />
          </button>

          {activeTab !== MainTab.SETTINGS && (
            <button
              type="button"
              onClick={() => {
                openTool(null);
                setActiveTab(MainTab.SETTINGS);
              }}
              aria-label="Open Settings"
              title="Settings"
              className="hidden sm:inline-flex p-2 rounded-full border transition-colors hover:brightness-110 cursor-pointer"
              style={{
                backgroundColor: visuals.solidSurfaceElevated,
                borderColor: visuals.glassBorder,
                color: visuals.textPrimary,
              }}
            >
              <Settings className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
