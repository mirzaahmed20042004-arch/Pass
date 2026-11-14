import React, { useState } from 'react';
import { CheckCircle2, Download, Server, Smartphone, X } from 'lucide-react';
import { getApiBase, isNativeApp, setApiBase } from '../api/client';

export function DownloadModal({ open, onClose, apkInfo, backendConnected, onRefreshCatalog }) {
  const [serverUrl, setServerUrl] = useState(() => getApiBase());
  const [savedMsg, setSavedMsg] = useState('');

  if (!open) return null;

  const downloadHref = apkInfo?.availableLocally
    ? apkInfo.localDownloadPath
    : apkInfo?.githubReleaseUrl || '/download/OmniDrive-Vehicle-Sounds.apk';

  function handleSaveServer(e) {
    e.preventDefault();
    setApiBase(serverUrl);
    setSavedMsg('Saved! Syncing...');
    onRefreshCatalog?.();
    setTimeout(() => setSavedMsg(''), 2000);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
      <div className="w-full sm:max-w-md rounded-t-3xl sm:rounded-2xl bg-cockpit-900 border border-white/15 p-5 space-y-4">
        <div className="flex items-center justify-between border-b border-white/10 pb-3">
          <div className="flex items-center gap-2">
            <Smartphone className="w-5 h-5 text-emerald-400" />
            <h2 className="text-lg font-bold text-white">Android APK &amp; Backend Sync</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* APK Download Section */}
        <div className="rounded-xl bg-gradient-to-br from-emerald-500/15 to-cockpit-950 border border-emerald-500/30 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm font-bold text-white">OmniDrive-Vehicle-Sounds.apk</div>
              <div className="text-xs text-slate-300 mt-0.5">
                v{apkInfo?.version || '1.0.0'} • {apkInfo?.packageId || 'com.omnidrive.vehiclesounds'}
              </div>
            </div>
            {apkInfo?.sizeMB && (
              <span className="text-xs font-mono px-2 py-1 rounded-lg bg-emerald-500/20 text-emerald-300">
                {apkInfo.sizeMB} MB
              </span>
            )}
          </div>

          <a
            href={downloadHref}
            download="OmniDrive-Vehicle-Sounds.apk"
            className="flex items-center justify-center gap-2 w-full rounded-xl bg-emerald-500 hover:bg-emerald-400 text-cockpit-950 font-extrabold text-sm py-3 shadow-lg shadow-emerald-950/50 active:scale-95 transition-all"
          >
            <Download className="w-4 h-4" />
            Download Android APK Directly
          </a>

          {apkInfo?.githubReleaseUrl && (
            <a
              href={apkInfo.githubReleaseUrl}
              target="_blank"
              rel="noreferrer"
              className="block text-center text-xs text-emerald-300 hover:underline"
            >
              Mirror: Direct GitHub Release APK Link
            </a>
          )}

          {apkInfo?.sha256 && (
            <div className="text-[10px] font-mono text-slate-400 break-all bg-black/30 rounded-lg p-2">
              SHA-256: {apkInfo.sha256}
            </div>
          )}
        </div>

        {/* Full-Stack Backend Connection */}
        <form onSubmit={handleSaveServer} className="rounded-xl bg-cockpit-800/80 border border-white/10 p-3.5 space-y-2.5">
          <div className="flex items-center justify-between text-xs">
            <span className="flex items-center gap-1.5 font-semibold text-slate-200">
              <Server className="w-4 h-4 text-cyan-400" />
              Full-Stack Backend Sync
            </span>
            <span
              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${
                backendConnected
                  ? 'bg-emerald-500/20 text-emerald-300'
                  : 'bg-amber-500/20 text-amber-300'
              }`}
            >
              <CheckCircle2 className="w-3 h-3" />
              {backendConnected ? 'SQLite Connected' : isNativeApp() ? 'Offline APK Mode' : 'Local Cache'}
            </span>
          </div>

          <input
            type="url"
            value={serverUrl}
            onChange={(e) => setServerUrl(e.target.value)}
            placeholder="Optional API server URL (e.g. https://your-server.app)"
            className="w-full rounded-lg bg-cockpit-950 border border-white/15 px-3 py-2 text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-cyan-400"
          />

          <div className="flex items-center justify-between gap-2">
            <span className="text-[11px] text-slate-400">
              {savedMsg || 'All 30 sounds work 100% offline inside the APK.'}
            </span>
            <button
              type="submit"
              className="px-3 py-1.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-cockpit-950 font-bold text-xs shrink-0"
            >
              Sync
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
