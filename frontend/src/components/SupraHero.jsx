import React from 'react';
import { Flame, Repeat, Volume2, Zap } from 'lucide-react';
import { VehicleSvg } from './VehicleSvg';

function EqBars({ active }) {
  if (!active) return null;
  return (
    <span className="inline-flex items-end gap-0.5 h-4 ml-1.5">
      <span className="w-1 h-full bg-current rounded-full origin-bottom eq-bar-1" />
      <span className="w-1 h-full bg-current rounded-full origin-bottom eq-bar-2" />
      <span className="w-1 h-full bg-current rounded-full origin-bottom eq-bar-3" />
    </span>
  );
}

export function SupraHero({ supraVehicle, activeIds, onToggleSound }) {
  if (!supraVehicle) return null;
  const engineSound = supraVehicle.sounds[0];
  const flutterSound = supraVehicle.sounds[1];
  const engineActive = activeIds.has(engineSound.id);
  const flutterActive = activeIds.has(flutterSound.id);
  const anyActive = engineActive || flutterActive;

  return (
    <section className="relative overflow-hidden rounded-2xl border border-amber-500/30 bg-gradient-to-br from-amber-500/15 via-cockpit-800 to-cockpit-900 p-4 sm:p-5 shadow-xl shadow-amber-950/20">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <div className="shrink-0 rounded-xl bg-cockpit-950/80 border border-amber-500/30 p-2.5 flex items-center justify-center">
            <VehicleSvg type="supra" accent="#f59e0b" active={anyActive} />
          </div>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-400/30">
                <Flame className="w-3 h-3" /> Legend Spotlight
              </span>
              <span className="text-[11px] text-amber-200/70 font-mono">2JZ-GTE • 3.0L TWIN TURBO</span>
            </div>
            <h2 className="text-lg sm:text-xl font-extrabold text-white mt-1 tracking-tight">
              Toyota Supra MK4 — Real Boost &amp; Turbo Flutter
            </h2>
            <p className="text-xs sm:text-sm text-slate-300 mt-0.5">
              Tap either button to loop seamlessly — or tap both together for full 2JZ boost + Stututu blow-off!
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2.5 sm:min-w-[320px]">
          <button
            type="button"
            onClick={() => onToggleSound(supraVehicle, engineSound)}
            className={`group relative flex items-center justify-center gap-2 rounded-xl px-3.5 py-3 text-xs sm:text-sm font-bold transition-all active:scale-95 border ${
              engineActive
                ? 'bg-amber-500 text-cockpit-950 border-amber-300 shadow-lg shadow-amber-500/30'
                : 'bg-cockpit-950/80 text-amber-200 border-amber-500/40 hover:bg-amber-500/20'
            }`}
          >
            {engineActive ? <Repeat className="w-4 h-4 animate-spin" style={{ animationDuration: '3s' }} /> : <Volume2 className="w-4 h-4" />}
            <span>{engineActive ? 'Looping 2JZ' : 'Supra 2JZ Sound'}</span>
            <EqBars active={engineActive} />
          </button>

          <button
            type="button"
            onClick={() => onToggleSound(supraVehicle, flutterSound)}
            className={`group relative flex items-center justify-center gap-2 rounded-xl px-3.5 py-3 text-xs sm:text-sm font-bold transition-all active:scale-95 border ${
              flutterActive
                ? 'bg-orange-500 text-cockpit-950 border-orange-300 shadow-lg shadow-orange-500/30'
                : 'bg-cockpit-950/80 text-orange-200 border-orange-500/40 hover:bg-orange-500/20'
            }`}
          >
            <Zap className="w-4 h-4" />
            <span>{flutterActive ? 'Stututu ON' : 'Turbo Flutter'}</span>
            <EqBars active={flutterActive} />
          </button>
        </div>
      </div>
    </section>
  );
}
