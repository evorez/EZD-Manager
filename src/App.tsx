import React from 'react';
import { EzdProvider, useEzd } from './context/EzdContext';
import { BrandLaunchSplash, DeveloperEggDialog } from './components/CustomVisuals';
import { FloatingPillNavBar } from './components/FloatingPillNavBar';
import { EzdAmbientBackground } from './components/GlassUI';
import {
  DeviceDetailsDialog,
  GlobalSearchDialog,
  GlobalToast,
  NotificationsSheet,
  PowerControlSheet,
} from './components/GlobalModals';
import { ToolDetailContainer } from './components/ToolDetailContainer';
import { TopBar } from './components/TopBar';
import { AppsScreen } from './screens/AppsScreen';
import { HomeScreen } from './screens/HomeScreen';
import { OnboardingScreen } from './screens/OnboardingScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { ToolsScreen } from './screens/ToolsScreen';
import { MainTab } from './types/ezd';

const EzdShell: React.FC = () => {
  const {
    state,
    activeTab,
    splashDone,
    setSplashDone,
    dismissDeveloperEgg,
  } = useEzd();

  const onboardingDone = state.settings.onboardingCompleted;

  return (
    <EzdAmbientBackground>
      {!splashDone && <BrandLaunchSplash onFinish={() => setSplashDone(true)} />}

      {!onboardingDone ? (
        <OnboardingScreen />
      ) : (
        <>
          <TopBar />
          <main className="mx-auto w-full max-w-5xl flex-1 px-4 pt-4 pb-32">
            {state.activeTool ? (
              <ToolDetailContainer />
            ) : activeTab === MainTab.HOME ? (
              <HomeScreen />
            ) : activeTab === MainTab.TOOLS ? (
              <ToolsScreen />
            ) : activeTab === MainTab.APPS ? (
              <AppsScreen />
            ) : (
              <SettingsScreen />
            )}
          </main>
          <FloatingPillNavBar />
        </>
      )}

      <GlobalSearchDialog />
      <PowerControlSheet />
      <NotificationsSheet />
      <DeviceDetailsDialog />
      {state.showDeveloperEggDialog && <DeveloperEggDialog onClose={dismissDeveloperEgg} />}
      <GlobalToast />
    </EzdAmbientBackground>
  );
};

export const App: React.FC = () => (
  <EzdProvider>
    <EzdShell />
  </EzdProvider>
);

export default App;
