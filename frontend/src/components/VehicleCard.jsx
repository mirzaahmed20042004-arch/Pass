import React from 'react';
import { Heart, Megaphone, Play, Repeat, Siren, Square, Wind, Zap } from 'lucide-react';
import { VehicleSvg } from './VehicleSvg';

function RoleIcon({ role, isActive }) {
  if (isActive) {
    return <Repeat className="w-4 h-4 shrink-0 animate-spin" style={{ animationDuration: '2.8s' }} />;
  }
  switch (role) {
    case 'horn':
      return <Megaphone className="w-4 h-4 shrink-0" />;
    case 'siren':
      return <Siren className="w-4 h-4 shrink-0" />;
    case 'turbo':
      return <Zap className="w-4 h-4 shrink-0" />;
    case 'flying':
      return <Wind className="w-4 h-4 shrink-0" />;
    default:
      return <Play className="w-4 h-4 shrink-0 fill-current" />;
  }
}

export function VehicleCard({ vehicle, activeIds, onToggleSound, onToggleFavorite }) {
  const anyActive = vehicle.sounds.some((s) => activeIds.has(s.id));

  return (
    <article
      className={`relative flex flex-col justify-between rounded-2xl border p-4 transition-all duration-200 ${
        anyActive
          ? 'bg-cockpit-800/95 border-white/25 shadow-lg'
          : 'bg-cockpit-900/90 border-white/10 hover:border-white/20'
      }`}
      style={
        anyActive
          ? {
              boxShadow: `0 0 28px -8px ${vehicle.accent}88`,
              borderColor: `${vehicle.accent}99`
            }
          : undefined
      }
    >
      {/* Top row: Badge + Favorite */}
      <div className="flex items-center justify-between gap-2 mb-2.5">
        <span
          className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border"
          style={{
            color: vehicle.accent,
            borderColor: `${vehicle.accent}44`,
            backgroundColor: `${vehicle.accent}18`
          }}
        >
          {vehicle.badge}
        </span>

        <div className="flex items-center gap-2">
          {vehicle.totalPlays > 0 && (
            <span className="text-[11px] font-mono text-slate-400">
              {vehicle.totalPlays.toLocaleString()} loops
            </span>
          )}
          <button
            type="button"
            aria-label={vehicle.isFavorite ? 'Remove favorite' : 'Add favorite'}
            onClick={() => onToggleFavorite(vehicle.id)}
            className={`p-1.5 rounded-lg transition-colors ${
              vehicle.isFavorite
                ? 'text-rose-400 bg-rose-500/15'
                : 'text-slate-500 hover:text-slate-200 hover:bg-white/5'
            }`}
          >
            <Heart className={`w-4 h-4 ${vehicle.isFavorite ? 'fill-current' : ''}`} />
          </button>
        </div>
      </div>

      {/* Vehicle Art & Title */}
      <div className="flex items-center gap-3.5 mb-3">
        <div
          className="shrink-0 w-24 h-16 rounded-xl bg-cockpit-950/90 border border-white/10 flex items-center justify-center p-1.5"
          style={anyActive ? { borderColor: `${vehicle.accent}66` } : undefined}
        >
          <VehicleSvg type={vehicle.svgType} accent={vehicle.accent} active={anyActive} />
        </div>
        <div className="min-w-0">
          <h3 className="text-base font-bold text-white leading-tight truncate">{vehicle.name}</h3>
          <p className="text-xs text-slate-400 mt-0.5 line-clamp-2">{vehicle.subtitle}</p>
          <p className="text-[10px] font-mono text-slate-500 mt-1 truncate">{vehicle.specs}</p>
        </div>
      </div>

      {/* Tap-to-Loop Buttons */}
      <div className={`grid gap-2 ${vehicle.sounds.length > 1 ? 'grid-cols-2' : 'grid-cols-1'}`}>
        {vehicle.sounds.map((sound) => {
          const isActive = activeIds.has(sound.id);
          return (
            <button
              key={sound.id}
              type="button"
              onClick={() => onToggleSound(vehicle, sound)}
              className={`relative flex items-center justify-between gap-2 rounded-xl px-3 py-2.5 text-left transition-all active:scale-95 border ${
                isActive
                  ? 'text-cockpit-950 font-extrabold shadow-md'
                  : 'bg-cockpit-950/80 hover:bg-cockpit-700/60 text-slate-100 border-white/10 font-semibold'
              }`}
              style={
                isActive
                  ? {
                      backgroundColor: vehicle.accent,
                      borderColor: '#ffffff88'
                    }
                  : undefined
              }
            >
              <div className="flex items-center gap-2 min-w-0">
                <RoleIcon role={sound.role} isActive={isActive} />
                <div className="min-w-0">
                  <div className="text-xs sm:text-[13px] leading-tight truncate">
                    {sound.shortLabel}
                  </div>
                  <div
                    className={`text-[10px] leading-tight ${
                      isActive ? 'text-cockpit-950/80 font-bold' : 'text-slate-400'
                    }`}
                  >
                    {isActive ? 'LOOPING • TAP STOP' : 'TAP TO LOOP'}
                  </div>
                </div>
              </div>

              {isActive ? (
                <div className="flex items-end gap-0.5 h-3.5 shrink-0">
                  <span className="w-1 h-full bg-cockpit-950 rounded-full origin-bottom eq-bar-1" />
                  <span className="w-1 h-full bg-cockpit-950 rounded-full origin-bottom eq-bar-2" />
                  <span className="w-1 h-full bg-cockpit-950 rounded-full origin-bottom eq-bar-3" />
                </div>
              ) : (
                <Square className="w-3 h-3 opacity-0" />
              )}
            </button>
          );
        })}
      </div>
    </article>
  );
}
