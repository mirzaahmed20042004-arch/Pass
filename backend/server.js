import express from 'express';
import cors from 'cors';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import {
  getCatalogWithState,
  recordPlay,
  toggleFavorite,
  listPresets,
  createPreset,
  deletePreset,
  getStats
} from './db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, '..');
const FRONTEND_DIST = path.join(ROOT_DIR, 'frontend', 'dist');
const AUDIO_DIR = path.join(ROOT_DIR, 'frontend', 'public', 'audio');
const DOWNLOADS_DIR = path.join(ROOT_DIR, 'downloads');
const APK_FILENAME = 'OmniDrive-Vehicle-Sounds.apk';
const APK_PATH = path.join(DOWNLOADS_DIR, APK_FILENAME);

const GITHUB_RELEASE_APK_URL =
  process.env.GITHUB_RELEASE_APK_URL ||
  'https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.0.0-omnidrive/OmniDrive-Vehicle-Sounds.apk';

const app = express();
app.use(cors({ origin: true, credentials: true }));
app.use(express.json());

// Health endpoint
app.get('/api/health', (_req, res) => {
  res.json({
    ok: true,
    service: 'omnidrive-backend',
    timestamp: new Date().toISOString(),
    stats: getStats()
  });
});

// Full catalog + user state
app.get('/api/catalog', (req, res) => {
  const data = getCatalogWithState();
  const category = (req.query.category || '').toString().trim().toLowerCase();
  const q = (req.query.q || '').toString().trim().toLowerCase();

  let vehicles = data.vehicles;
  if (category && category !== 'all') {
    if (category === 'favorites') {
      vehicles = vehicles.filter((v) => v.isFavorite);
    } else {
      vehicles = vehicles.filter((v) => v.category === category);
    }
  }
  if (q) {
    vehicles = vehicles.filter(
      (v) =>
        v.name.toLowerCase().includes(q) ||
        v.subtitle.toLowerCase().includes(q) ||
        v.specs.toLowerCase().includes(q) ||
        v.category.toLowerCase().includes(q) ||
        v.sounds.some((s) => s.label.toLowerCase().includes(q) || s.shortLabel.toLowerCase().includes(q))
    );
  }

  res.json({
    ...data,
    vehicles
  });
});

// Record play telemetry
app.post('/api/sounds/:soundId/play', (req, res) => {
  const result = recordPlay(req.params.soundId);
  if (!result) {
    return res.status(404).json({ error: 'Sound not found' });
  }
  res.json(result);
});

// Toggle favorite
app.post('/api/favorites/:vehicleId/toggle', (req, res) => {
  const result = toggleFavorite(req.params.vehicleId);
  if (!result) {
    return res.status(404).json({ error: 'Vehicle not found' });
  }
  res.json(result);
});

// Mix presets
app.get('/api/presets', (_req, res) => {
  res.json({ presets: listPresets() });
});

app.post('/api/presets', (req, res) => {
  const { name, description, soundIds } = req.body || {};
  if (!name || typeof name !== 'string' || !name.trim()) {
    return res.status(400).json({ error: 'Preset name is required' });
  }
  if (!Array.isArray(soundIds) || soundIds.length === 0) {
    return res.status(400).json({ error: 'Select at least one active sound to save a preset' });
  }
  const presets = createPreset({ name, description, soundIds });
  res.status(201).json({ presets });
});

app.delete('/api/presets/:id', (req, res) => {
  const ok = deletePreset(req.params.id);
  if (!ok) {
    return res.status(404).json({ error: 'Custom preset not found or is built-in' });
  }
  res.json({ ok: true, presets: listPresets() });
});

// Global stats
app.get('/api/stats', (_req, res) => {
  res.json(getStats());
});

// APK metadata & direct download
app.get('/api/apk-info', (_req, res) => {
  const exists = fs.existsSync(APK_PATH);
  let sizeBytes = 0;
  let sha256 = null;
  if (exists) {
    const stat = fs.statSync(APK_PATH);
    sizeBytes = stat.size;
    sha256 = crypto.createHash('sha256').update(fs.readFileSync(APK_PATH)).digest('hex');
  }
  res.json({
    availableLocally: exists,
    filename: APK_FILENAME,
    packageId: 'com.omnidrive.vehiclesounds',
    version: '1.0.0',
    sizeBytes,
    sizeMB: exists ? Number((sizeBytes / (1024 * 1024)).toFixed(2)) : null,
    sha256,
    localDownloadPath: `/download/${APK_FILENAME}`,
    githubReleaseUrl: GITHUB_RELEASE_APK_URL
  });
});

app.get(`/download/${APK_FILENAME}`, (_req, res) => {
  if (fs.existsSync(APK_PATH)) {
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', `attachment; filename="${APK_FILENAME}"`);
    return res.sendFile(APK_PATH);
  }
  return res.redirect(302, GITHUB_RELEASE_APK_URL);
});

// Static audio assets (dev & prod)
if (fs.existsSync(AUDIO_DIR)) {
  app.use('/audio', express.static(AUDIO_DIR, { maxAge: '7d' }));
}

// Serve built frontend in production / preview
if (fs.existsSync(FRONTEND_DIST)) {
  app.use(express.static(FRONTEND_DIST));
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api/') || req.path.startsWith('/download/')) {
      return next();
    }
    res.sendFile(path.join(FRONTEND_DIST, 'index.html'));
  });
}

const PORT = Number(process.env.PORT || 3001);
app.listen(PORT, '0.0.0.0', () => {
  console.log(`OmniDrive full-stack server listening on http://0.0.0.0:${PORT}`);
});
