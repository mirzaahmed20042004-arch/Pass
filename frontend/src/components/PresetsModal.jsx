import React, { useState } from 'react';
import { BookmarkPlus, Play, Sparkles, Trash2, X } from 'lucide-react';

export function PresetsModal({
  open,
  onClose,
  presets,
  activeList,
  onPlayPreset,
  onSavePreset,
  onDeletePreset
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);

  if (!open) return null;

  async function handleSave(e) {
    e.preventDefault();
    if (!name.trim() || activeList.length === 0) return;
    setSaving(true);
    try {
      await onSavePreset({
        name: name.trim(),
        description: description.trim() || activeList.map((a) => a.meta.shortLabel).join(' + '),
        soundIds: activeList.map((a) => a.id)
      });
      setName('');
      setDescription('');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
      <div className="w-full sm:max-w-lg rounded-t-3xl sm:rounded-2xl bg-cockpit-900 border border-white/15 p-5 max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between gap-2 pb-3 border-b border-white/10">
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-amber-400" />
            <h2 className="text-lg font-bold text-white">Sound Mix Presets</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="overflow-y-auto space-y-4 py-4 flex-1">
          {/* Save Current Active Mix */}
          <form onSubmit={handleSave} className="rounded-xl bg-cockpit-800/80 border border-white/10 p-3.5 space-y-2.5">
            <div className="flex items-center justify-between text-xs font-semibold text-amber-300">
              <span className="flex items-center gap-1.5">
                <BookmarkPlus className="w-4 h-4" /> Save Currently Looping Mix
              </span>
              <span className="text-slate-400">{activeList.length} active</span>
            </div>
            {activeList.length === 0 ? (
              <p className="text-xs text-slate-400">
                Start looping 1 or more vehicle sounds first, then save your custom scene here.
              </p>
            ) : (
              <>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Scene name (e.g. Highway Chase)"
                  className="w-full rounded-lg bg-cockpit-950 border border-white/15 px-3 py-2 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-amber-400"
                  required
                />
                <input
                  type="text"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional description..."
                  className="w-full rounded-lg bg-cockpit-950 border border-white/15 px-3 py-2 text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-amber-400"
                />
                <button
                  type="submit"
                  disabled={saving || !name.trim()}
                  className="w-full rounded-xl bg-amber-500 hover:bg-amber-400 disabled:opacity-50 text-cockpit-950 font-bold text-xs py-2.5 transition-colors"
                >
                  {saving ? 'Saving...' : 'Save Custom Preset'}
                </button>
              </>
            )}
          </form>

          {/* Existing Presets */}
          <div className="space-y-2">
            {presets.map((preset) => (
              <div
                key={preset.id}
                className="flex items-center justify-between gap-3 rounded-xl bg-cockpit-950/80 border border-white/10 p-3 hover:border-amber-500/40 transition-colors"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-sm text-white truncate">{preset.name}</span>
                    {preset.isDefault && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
                        BUILT-IN
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-400 mt-0.5 truncate">{preset.description}</p>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => {
                      onPlayPreset(preset);
                      onClose();
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-cockpit-950 font-bold text-xs active:scale-95"
                  >
                    <Play className="w-3.5 h-3.5 fill-current" />
                    Loop Mix
                  </button>
                  {!preset.isDefault && (
                    <button
                      type="button"
                      onClick={() => onDeletePreset(preset.id)}
                      className="p-2 rounded-xl text-slate-400 hover:text-rose-400 hover:bg-rose-500/15"
                      aria-label="Delete preset"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
