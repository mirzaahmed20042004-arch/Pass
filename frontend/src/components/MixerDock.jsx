import React, { useEffect, useState } from 'react';
import { BookmarkPlus, Layers, Radio, Square, Volume2, X } from 'lucide-react';
import { soundEngine } from '../audio/SoundEngine';

export function MixerDock({
  activeList,
  soloMode,
  onToggleSolo,
  onStopOne,
  onStopAll,
  onChangeVolume,
  onOpenSavePreset
}) {
  const [bars, setBars] = useState(() => new Array(14).fill(0));
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    if (activeList.length === 0) {
      setBars(new Array(14).fill(0));
      setExpanded(false);
      return;
    }
    let rafId;
    const tick = () => {
      setBars(soundEngine.getSpectrumBars(14));
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [activeList.length]);

  if (activeList.length === 0) {
    return (
      <div className="fixed bottom-0 inset-x-0 z-30 border-t border-white/10 bg-cockpit-950/90 backdrop-blur-lg px-4 py-2.5">
        <div className="max-w-6xl mx-auto flex items-center justify-between gap-3 text-xs text-slate-400">
          <div className="flex items-center gap-2">
            <Radio className="w-4 h-4 text-amber-400" />
            <span>Tap any vehicle button to start gapless looping playback.</span>
          </div>
          <button
            type="button"
            onClick={() => onToggleSolo(!soloMode)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-[11px] font-semibold transition-colors ${
              soloMode
                ? 'border-cyan-500/40 bg-cyan-500/15 text-cyan-300'
                : 'border-amber-500/40 bg-amber-500/15 text-amber-300'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            {soloMode ? 'Solo Mode (1 sound)' : 'Multi-Mix Mode'}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed bottom-0 inset-x-0 z-40 border-t border-amber-500/30 bg-cockpit-950/95 backdrop-blur-xl shadow-2xl">
      <div className="max-w-6xl mx-auto px-3 sm:px-5 py-2.5">
        {/* Expanded Per-Channel Mixer */}
        {expanded && (
          <div className="mb-3 pb-3 border-b border-white/10 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 max-h-56 overflow-y-auto">
            {activeList.map((item) => (
              <div
                key={item.id}
                className="flex items-center gap-2.5 rounded-xl bg-cockpit-800/90 border border-white/10 px-3 py-2"
              >
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-bold text-white truncate">
                    {item.meta.vehicleName} — {item.meta.shortLabel}
                  </div>
                  <div className="flex items-center gap-2 mt-1">
                    <Volume2 className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.05"
                      value={item.volume}
                      onChange={(e) => onChangeVolume(item.id, parseFloat(e.target.value))}
                      className="w-full accent-amber-400 h-1.5 cursor-pointer"
                    />
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => onStopOne(item.id)}
                  className="p-1.5 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-300 hover:text-rose-300 transition-colors"
                  aria-label={`Stop ${item.meta.shortLabel}`}
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* Compact Control Bar */}
        <div className="flex flex-wrap items-center justify-between gap-2.5">
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            className="flex items-center gap-3 text-left min-w-0 flex-1"
          >
            {/* Live Spectrum */}
            <div className="flex items-end gap-0.5 h-6 px-2 py-1 rounded-lg bg-cockpit-800 border border-white/10 shrink-0">
              {bars.map((val, i) => (
                <span
                  key={i}
                  className="w-1 rounded-full bg-gradient-to-t from-amber-500 to-cyan-400 transition-all duration-75"
                  style={{ height: `${Math.max(18, Math.round(val * 100))}%` }}
                />
              ))}
            </div>
            <div className="min-w-0">
              <div className="text-xs sm:text-sm font-bold text-white truncate">
                {activeList.length} {activeList.length === 1 ? 'Sound' : 'Sounds'} Looping
                <span className="text-amber-400 ml-1.5 text-[11px] font-normal">
                  ({expanded ? 'Hide Mixer' : 'Adjust Mix'})
                </span>
              </div>
              <div className="text-[11px] text-slate-400 truncate">
                {activeList.map((a) => a.meta.shortLabel).join(' + ')}
              </div>
            </div>
          </button>

          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={() => onToggleSolo(!soloMode)}
              className={`hidden sm:inline-flex items-center gap-1.5 px-2.5 py-2 rounded-xl border text-xs font-semibold ${
                soloMode
                  ? 'border-cyan-500/40 bg-cyan-500/15 text-cyan-300'
                  : 'border-amber-500/40 bg-amber-500/15 text-amber-300'
              }`}
            >
              <Layers className="w-3.5 h-3.5" />
              {soloMode ? 'Solo' : 'Mix'}
            </button>

            <button
              type="button"
              onClick={onOpenSavePreset}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-cockpit-800 hover:bg-cockpit-700 border border-white/15 text-xs font-semibold text-slate-200"
            >
              <BookmarkPlus className="w-3.5 h-3.5 text-amber-400" />
              <span className="hidden sm:inline">Save Mix</span>
            </button>

            <button
              type="button"
              onClick={onStopAll}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-extrabold shadow-lg shadow-rose-950/50 active:scale-95 transition-all"
            >
              <Square className="w-3.5 h-3.5 fill-current" />
              Stop All
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
