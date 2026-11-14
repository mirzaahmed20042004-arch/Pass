import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Bike,
  Car,
  Download,
  Heart,
  LayoutGrid,
  Plane,
  Search,
  Ship,
  Siren,
  Sparkles,
  Truck,
  Volume2,
  X
} from 'lucide-react';
import {
  fetchApkInfo,
  fetchCatalog,
  recordSoundPlay,
  removeMixPreset,
  saveMixPreset,
  toggleVehicleFavorite
} from './api/client';
import { soundEngine } from './audio/SoundEngine';
import { SupraHero } from './components/SupraHero';
import { VehicleCard } from './components/VehicleCard';
import { MixerDock } from './components/MixerDock';
import { PresetsModal } from './components/PresetsModal';
import { DownloadModal } from './components/DownloadModal';

const CATEGORY_ICONS = {
  grid: LayoutGrid,
  car: Car,
  bike: Bike,
  siren: Siren,
  truck: Truck,
  plane: Plane,
  ship: Ship
};

export default function App() {
  const [catalog, setCatalog] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [search, setSearch] = useState('');
  const [engineSnap, setEngineSnap] = useState(() => soundEngine.getSnapshot());
  const [presetsOpen, setPresetsOpen] = useState(false);
  const [downloadOpen, setDownloadOpen] = useState(false);
  const [apkInfo, setApkInfo] = useState(null);

  const loadData = useCallback(async () => {
    const [cat, apk] = await Promise.all([fetchCatalog(), fetchApkInfo()]);
    setCatalog(cat);
    setApkInfo(apk);
  }, []);

  useEffect(() => {
    loadData();
    const unsub = soundEngine.subscribe(setEngineSnap);
    return () => unsub();
  }, [loadData]);

  // Lazy preload high-priority audio buffers
  useEffect(() => {
    if (!catalog) return;
    const priority = [
      '/audio/supra_engine.mp3',
      '/audio/supra_turbo_flutter.mp3',
      '/audio/car_driving.mp3',
      '/audio/car_horn.mp3',
      '/audio/police_siren.mp3',
      '/audio/helicopter.mp3'
    ];
    const t = setTimeout(() => soundEngine.preload(priority), 600);
    return () => clearTimeout(t);
  }, [catalog]);

  const soundLookup = useMemo(() => {
    const map = new Map();
    if (!catalog) return map;
    for (const v of catalog.vehicles) {
      for (const s of v.sounds) {
        map.set(s.id, { ...s, vehicleId: v.id, vehicleName: v.name, accent: v.accent });
      }
    }
    return map;
  }, [catalog]);

  const handleToggleSound = useCallback(async (vehicle, sound) => {
    const meta = {
      ...sound,
      vehicleId: vehicle.id,
      vehicleName: vehicle.name,
      accent: vehicle.accent
    };
    const started = await soundEngine.toggle(meta);
    if (started) {
      recordSoundPlay(sound.id).then((res) => {
        if (!res) return;
        setCatalog((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            vehicles: prev.vehicles.map((v) =>
              v.id === vehicle.id
                ? {
                    ...v,
                    totalPlays: v.totalPlays + 1,
                    sounds: v.sounds.map((s) =>
                      s.id === sound.id ? { ...s, playCount: res.playCount } : s
                    )
                  }
                : v
            )
          };
        });
      });
    }
  }, []);

  const handleToggleFavorite = useCallback(async (vehicleId) => {
    const res = await toggleVehicleFavorite(vehicleId);
    if (!res) return;
    setCatalog((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        vehicles: prev.vehicles.map((v) =>
          v.id === vehicleId ? { ...v, isFavorite: res.isFavorite } : v
        )
      };
    });
  }, []);

  const handlePlayPreset = useCallback(
    async (preset) => {
      const list = preset.soundIds.map((id) => soundLookup.get(id)).filter(Boolean);
      if (list.length > 0) {
        await soundEngine.playPreset(list);
      }
    },
    [soundLookup]
  );

  const handleSavePreset = useCallback(async (payload) => {
    const res = await saveMixPreset(payload);
    if (res?.presets) {
      setCatalog((prev) => (prev ? { ...prev, presets: res.presets } : prev));
    }
  }, []);

  const handleDeletePreset = useCallback(async (id) => {
    const res = await removeMixPreset(id);
    if (res?.presets) {
      setCatalog((prev) => (prev ? { ...prev, presets: res.presets } : prev));
    }
  }, []);

  const filteredVehicles = useMemo(() => {
    if (!catalog) return [];
    const q = search.trim().toLowerCase();
    return catalog.vehicles.filter((v) => {
      if (selectedCategory === 'favorites' && !v.isFavorite) return false;
      if (selectedCategory !== 'all' && selectedCategory !== 'favorites' && v.category !== selectedCategory) {
        return false;
      }
      if (!q) return true;
      return (
        v.name.toLowerCase().includes(q) ||
        v.subtitle.toLowerCase().includes(q) ||
        v.specs.toLowerCase().includes(q) ||
        v.category.toLowerCase().includes(q) ||
        v.sounds.some(
          (s) => s.label.toLowerCase().includes(q) || s.shortLabel.toLowerCase().includes(q)
        )
      );
    });
  }, [catalog, selectedCategory, search]);

  const supraVehicle = useMemo(
    () => catalog?.vehicles.find((v) => v.id === 'supra'),
    [catalog]
  );

  const quickShortcuts = useMemo(() => {
    const ids = [
      'supra_engine',
      'supra_turbo_flutter',
      'car_driving',
      'car_horn',
      'bike_engine',
      'bike_horn',
      'police_siren',
      'ambulance_siren',
      'firebrigade_siren',
      'truck_airhorn',
      'bus_engine',
      'helicopter',
      'plane_jet',
      'supercar_v10'
    ];
    return ids.map((id) => soundLookup.get(id)).filter(Boolean);
  }, [soundLookup]);

  if (!catalog) {
    return (
      <div className="min-h-screen cockpit-bg flex items-center justify-center">
        <div className="text-center space-y-2">
          <div className="w-10 h-10 mx-auto rounded-full border-2 border-amber-400 border-t-transparent animate-spin" />
          <p className="text-sm text-slate-300 font-medium">Loading Vehicle Sound Engine...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen cockpit-bg pb-28">
      {/* Top Header */}
      <header className="sticky top-0 z-30 border-b border-white/10 bg-cockpit-950/90 backdrop-blur-xl">
        <div className="max-w-6xl mx-auto px-3 sm:px-5 py-3 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center shadow-lg shadow-amber-950/50 shrink-0">
                <Volume2 className="w-5 h-5 text-cockpit-950" />
              </div>
              <div className="min-w-0">
                <h1 className="text-base sm:text-lg font-extrabold text-white tracking-tight truncate">
                  OmniDrive <span className="text-amber-400">Sounds</span>
                </h1>
                <p className="text-[11px] text-slate-400 truncate">
                  20 Vehicles • 30 Real Tap-to-Loop Sounds
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <button
                type="button"
                onClick={() => setPresetsOpen(true)}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-cockpit-800 hover:bg-cockpit-700 border border-white/10 text-xs font-semibold text-slate-200 transition-colors"
              >
                <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                <span>Mixes</span>
              </button>

              <button
                type="button"
                onClick={() => setDownloadOpen(true)}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-cockpit-950 text-xs font-extrabold shadow-md shadow-emerald-950/40 active:scale-95 transition-all"
              >
                <Download className="w-3.5 h-3.5" />
                <span>APK</span>
              </button>
            </div>
          </div>

          {/* Search Input */}
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search Supra, Car Horn, Police Siren, Helicopter, Plane, Truck..."
              className="w-full rounded-xl bg-cockpit-900/90 border border-white/10 pl-10 pr-9 py-2.5 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-amber-400/70"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* Category Filter Pills */}
          <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar pb-0.5">
            {catalog.categories.map((cat) => {
              const Icon = CATEGORY_ICONS[cat.icon] || LayoutGrid;
              const active = selectedCategory === cat.id;
              return (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => setSelectedCategory(cat.id)}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap border transition-all ${
                    active
                      ? 'bg-amber-500 text-cockpit-950 border-amber-300 shadow-sm'
                      : 'bg-cockpit-900/80 text-slate-300 border-white/10 hover:border-white/25'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  {cat.label}
                </button>
              );
            })}

            <button
              type="button"
              onClick={() => setSelectedCategory('favorites')}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap border transition-all ${
                selectedCategory === 'favorites'
                  ? 'bg-rose-500 text-white border-rose-300 shadow-sm'
                  : 'bg-cockpit-900/80 text-slate-300 border-white/10 hover:border-white/25'
              }`}
            >
              <Heart className="w-3.5 h-3.5" />
              Favorites
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-6xl mx-auto px-3 sm:px-5 pt-4 space-y-5">
        {/* Instant Quick-Tap Loop Bar */}
        <section>
          <div className="flex items-center justify-between mb-2">
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
              Quick Tap-to-Loop Deck
            </span>
            <span className="text-[11px] text-slate-500">Tap to start • Tap again to stop</span>
          </div>
          <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
            {quickShortcuts.map((s) => {
              const isActive = engineSnap.activeIds.has(s.id);
              return (
                <button
                  key={s.id}
                  type="button"
                  onClick={() =>
                    handleToggleSound({ id: s.vehicleId, name: s.vehicleName, accent: s.accent }, s)
                  }
                  className={`shrink-0 px-3 py-2 rounded-xl text-xs font-bold border transition-all active:scale-95 ${
                    isActive
                      ? 'text-cockpit-950 shadow-md'
                      : 'bg-cockpit-900/90 text-slate-200 border-white/10 hover:border-white/25'
                  }`}
                  style={
                    isActive
                      ? { backgroundColor: s.accent, borderColor: '#ffffff80' }
                      : undefined
                  }
                >
                  {isActive ? `● ${s.shortLabel}` : s.shortLabel}
                </button>
              );
            })}
          </div>
        </section>

        {/* Featured Supra Spotlight */}
        {!search && (selectedCategory === 'all' || selectedCategory === 'cars') && (
          <SupraHero
            supraVehicle={supraVehicle}
            activeIds={engineSnap.activeIds}
            onToggleSound={handleToggleSound}
          />
        )}

        {/* Vehicle Grid */}
        {filteredVehicles.length === 0 ? (
          <div className="rounded-2xl border border-white/10 bg-cockpit-900/70 p-10 text-center space-y-2">
            <p className="text-base font-bold text-white">No matching vehicles found</p>
            <p className="text-xs text-slate-400">
              Try clearing your search or switching back to &quot;All Vehicles&quot;.
            </p>
            <button
              type="button"
              onClick={() => {
                setSearch('');
                setSelectedCategory('all');
              }}
              className="mt-2 px-4 py-2 rounded-xl bg-amber-500 text-cockpit-950 font-bold text-xs"
            >
              Show All Vehicles
            </button>
          </div>
        ) : (
          <section className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3.5">
            {filteredVehicles.map((vehicle) => (
              <VehicleCard
                key={vehicle.id}
                vehicle={vehicle}
                activeIds={engineSnap.activeIds}
                onToggleSound={handleToggleSound}
                onToggleFavorite={handleToggleFavorite}
              />
            ))}
          </section>
        )}
      </main>

      {/* Sticky Live Mixer Dock */}
      <MixerDock
        activeList={engineSnap.activeList}
        soloMode={engineSnap.soloMode}
        onToggleSolo={(val) => soundEngine.setSoloMode(val)}
        onStopOne={(id) => soundEngine.stop(id)}
        onStopAll={() => soundEngine.stopAll()}
        onChangeVolume={(id, vol) => soundEngine.setVolume(id, vol)}
        onOpenSavePreset={() => setPresetsOpen(true)}
      />

      {/* Modals */}
      <PresetsModal
        open={presetsOpen}
        onClose={() => setPresetsOpen(false)}
        presets={catalog.presets}
        activeList={engineSnap.activeList}
        onPlayPreset={handlePlayPreset}
        onSavePreset={handleSavePreset}
        onDeletePreset={handleDeletePreset}
      />

      <DownloadModal
        open={downloadOpen}
        onClose={() => setDownloadOpen(false)}
        apkInfo={apkInfo}
        backendConnected={catalog.backendConnected}
        onRefreshCatalog={loadData}
      />
    </div>
  );
}
