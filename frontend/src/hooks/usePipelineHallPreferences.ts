import { useEffect, useState } from 'react';

export type PipelineHallViewMode = 'card' | 'table';

export type PipelineHallFilterMode = 'all' | 'favorites' | 'running' | 'failed' | 'recent';

const VIEW_MODE_STORAGE_KEY = 'deploy-bot:user-pipelines-view';
const FILTER_MODE_STORAGE_KEY = 'deploy-bot:user-pipelines-filter-mode';
const AUTO_OPEN_DEPLOYMENT_DETAIL_STORAGE_KEY = 'deploy-bot:user-pipelines-auto-open-detail';
const PIN_ACTIVE_PIPELINES_STORAGE_KEY = 'deploy-bot:user-pipelines-pin-active';
const STOP_CONFIRMATION_STORAGE_KEY = 'deploy-bot:user-pipelines-stop-confirmation';
const SHOW_RUNNING_SERVICES_STORAGE_KEY = 'deploy-bot:user-pipelines-show-running-services';
const HIDE_STOPPED_SERVICES_STORAGE_KEY = 'deploy-bot:user-pipelines-hide-stopped-services';

const validFilterModes: PipelineHallFilterMode[] = ['all', 'favorites', 'running', 'failed', 'recent'];

function readString(key: string) {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.localStorage.getItem(key);
}

function writeString(key: string, value: string) {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(key, value);
  }
}

function readBoolean(key: string, fallback: boolean) {
  const stored = readString(key);
  return stored === null ? fallback : stored === 'true';
}

export function usePipelineHallPreferences() {
  const [viewMode, setViewMode] = useState<PipelineHallViewMode>(() => (
    readString(VIEW_MODE_STORAGE_KEY) === 'table' ? 'table' : 'card'
  ));
  const [filterMode, setFilterMode] = useState<PipelineHallFilterMode>(() => {
    const stored = readString(FILTER_MODE_STORAGE_KEY);
    return validFilterModes.includes((stored || '') as PipelineHallFilterMode) ? stored as PipelineHallFilterMode : 'all';
  });
  const [autoOpenDeploymentDetail, setAutoOpenDeploymentDetail] = useState(() => readBoolean(AUTO_OPEN_DEPLOYMENT_DETAIL_STORAGE_KEY, false));
  const [pinActivePipelines, setPinActivePipelines] = useState(() => readBoolean(PIN_ACTIVE_PIPELINES_STORAGE_KEY, false));
  const [stopConfirmationEnabled, setStopConfirmationEnabled] = useState(() => readBoolean(STOP_CONFIRMATION_STORAGE_KEY, true));
  const [showRunningServices, setShowRunningServices] = useState(() => readBoolean(SHOW_RUNNING_SERVICES_STORAGE_KEY, false));
  const [hideStoppedServices, setHideStoppedServices] = useState(() => readBoolean(HIDE_STOPPED_SERVICES_STORAGE_KEY, true));

  useEffect(() => writeString(VIEW_MODE_STORAGE_KEY, viewMode), [viewMode]);
  useEffect(() => writeString(FILTER_MODE_STORAGE_KEY, filterMode), [filterMode]);
  useEffect(() => writeString(AUTO_OPEN_DEPLOYMENT_DETAIL_STORAGE_KEY, String(autoOpenDeploymentDetail)), [autoOpenDeploymentDetail]);
  useEffect(() => writeString(PIN_ACTIVE_PIPELINES_STORAGE_KEY, String(pinActivePipelines)), [pinActivePipelines]);
  useEffect(() => writeString(STOP_CONFIRMATION_STORAGE_KEY, String(stopConfirmationEnabled)), [stopConfirmationEnabled]);
  useEffect(() => writeString(SHOW_RUNNING_SERVICES_STORAGE_KEY, String(showRunningServices)), [showRunningServices]);
  useEffect(() => writeString(HIDE_STOPPED_SERVICES_STORAGE_KEY, String(hideStoppedServices)), [hideStoppedServices]);

  return {
    viewMode,
    setViewMode,
    filterMode,
    setFilterMode,
    autoOpenDeploymentDetail,
    setAutoOpenDeploymentDetail,
    pinActivePipelines,
    setPinActivePipelines,
    stopConfirmationEnabled,
    setStopConfirmationEnabled,
    showRunningServices,
    setShowRunningServices,
    hideStoppedServices,
    setHideStoppedServices,
  };
}
