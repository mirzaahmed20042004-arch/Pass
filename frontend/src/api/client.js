import bundledCatalog from '../../../shared/catalog.json';

const LS_FAVS = 'omnidrive_favorites_v1';
const LS_PLAYS = 'omnidrive_plays_v1';
const LS_PRESETS = 'omnidrive_presets_v1';
const LS_API_BASE = 'omnidrive_api_base_v1';

export function isNativeApp() {
  return Boolean(window?.Capacitor?.isNativePlatform?.());
}

export function getApiBase() {
  const saved = localStorage.getItem(LS_API_BASE) || '';
  if (saved) return saved.replace(/\/+$/, '');
  return isNativeApp() ? '' : '';
}

export function setApiBase(url) {
  const clean = (url || '').trim().replace(/\/+$/, '');
  if (clean) {
    localStorage.setItem(LS_API_BASE, clean);
  } else {
    localStorage.removeItem(LS_API_BASE);
  }
}

function getLocalState() {
  const favs = new Set(JSON.parse(localStorage.getItem(LS_FAVS) || '[]'));
  const plays = JSON.parse(localStorage.getItem(LS_PLAYS) || '{}');
  const customPresets = JSON.parse(localStorage.getItem(LS_PRESETS) || '[]');
  return { favs, plays, customPresets };
}

function buildOfflineCatalog() {
  const { favs, plays, customPresets } = getLocalState();
  const vehicles = bundledCatalog.vehicles.map((v) => ({
    ...v,
    isFavorite: favs.has(v.id),
    totalPlays: v.sounds.reduce((sum, s) => sum + (plays[s.id] || 0), 0),
    sounds: v.sounds.map((s) => ({
      ...s,
      playCount: plays[s.id] || 0
    }))
  }));
  const presets = [
    ...bundledCatalog.defaultPresets.map((p) => ({ ...p, isDefault: true })),
    ...customPresets
  ];
  return {
    app: bundledCatalog.app,
    categories: bundledCatalog.categories,
    vehicles,
    favorites: Array.from(favs),
    presets,
    backendConnected: false
  };
}

async function apiFetch(path, options = {}) {
  const base = getApiBase();
  // In native APK without configured remote server, skip localhost network wait
  if (isNativeApp() && !base) {
    throw new Error('offline-native');
  }
  const res = await fetch(`${base}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  if (!res.ok) throw new Error(`API ${res.status}`);
  return res.json();
}

export async function fetchCatalog() {
  try {
    const data = await apiFetch('/api/catalog');
    return { ...data, backendConnected: true };
  } catch {
    return buildOfflineCatalog();
  }
}

export async function recordSoundPlay(soundId) {
  const { plays } = getLocalState();
  plays[soundId] = (plays[soundId] || 0) + 1;
  localStorage.setItem(LS_PLAYS, JSON.stringify(plays));

  try {
    return await apiFetch(`/api/sounds/${encodeURIComponent(soundId)}/play`, { method: 'POST' });
  } catch {
    return { soundId, playCount: plays[soundId] };
  }
}

export async function toggleVehicleFavorite(vehicleId) {
  const { favs } = getLocalState();
  if (favs.has(vehicleId)) favs.delete(vehicleId);
  else favs.add(vehicleId);
  localStorage.setItem(LS_FAVS, JSON.stringify(Array.from(favs)));

  try {
    return await apiFetch(`/api/favorites/${encodeURIComponent(vehicleId)}/toggle`, { method: 'POST' });
  } catch {
    return { vehicleId, isFavorite: favs.has(vehicleId) };
  }
}

export async function saveMixPreset({ name, description, soundIds }) {
  try {
    return await apiFetch('/api/presets', {
      method: 'POST',
      body: JSON.stringify({ name, description, soundIds })
    });
  } catch {
    const { customPresets } = getLocalState();
    const next = [
      {
        id: `preset-${Date.now().toString(36)}`,
        name,
        description: description || '',
        soundIds,
        isDefault: false
      },
      ...customPresets
    ];
    localStorage.setItem(LS_PRESETS, JSON.stringify(next));
    return {
      presets: [...bundledCatalog.defaultPresets.map((p) => ({ ...p, isDefault: true })), ...next]
    };
  }
}

export async function removeMixPreset(id) {
  try {
    return await apiFetch(`/api/presets/${encodeURIComponent(id)}`, { method: 'DELETE' });
  } catch {
    const { customPresets } = getLocalState();
    const next = customPresets.filter((p) => p.id !== id);
    localStorage.setItem(LS_PRESETS, JSON.stringify(next));
    return {
      ok: true,
      presets: [...bundledCatalog.defaultPresets.map((p) => ({ ...p, isDefault: true })), ...next]
    };
  }
}

export async function fetchApkInfo() {
  try {
    return await apiFetch('/api/apk-info');
  } catch {
    return {
      availableLocally: false,
      filename: 'OmniDrive-Vehicle-Sounds.apk',
      packageId: 'com.omnidrive.vehiclesounds',
      version: '1.0.0',
      localDownloadPath: '/download/OmniDrive-Vehicle-Sounds.apk',
      githubReleaseUrl:
        'https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.0.0-omnidrive/OmniDrive-Vehicle-Sounds.apk'
    };
  }
}
