import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.join(__dirname, 'data');
fs.mkdirSync(DATA_DIR, { recursive: true });

const DB_PATH = process.env.DB_PATH || path.join(DATA_DIR, 'omnidrive.sqlite');
const catalogPath = path.resolve(__dirname, '..', 'shared', 'catalog.json');
const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf-8'));

const db = new DatabaseSync(DB_PATH);

db.exec(`
  PRAGMA journal_mode = WAL;
  CREATE TABLE IF NOT EXISTS sound_stats (
    sound_id TEXT PRIMARY KEY,
    vehicle_id TEXT NOT NULL,
    play_count INTEGER NOT NULL DEFAULT 0,
    last_played_at TEXT
  );
  CREATE TABLE IF NOT EXISTS favorites (
    vehicle_id TEXT PRIMARY KEY,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
  CREATE TABLE IF NOT EXISTS presets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    sound_ids_json TEXT NOT NULL,
    is_default INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
  CREATE TABLE IF NOT EXISTS play_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sound_id TEXT NOT NULL,
    vehicle_id TEXT NOT NULL,
    played_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
`);

// Seed initial sound_stats & default presets
const upsertStat = db.prepare(`
  INSERT INTO sound_stats (sound_id, vehicle_id, play_count)
  VALUES (?, ?, 0)
  ON CONFLICT(sound_id) DO NOTHING
`);
for (const v of catalog.vehicles) {
  for (const s of v.sounds) {
    upsertStat.run(s.id, v.id);
  }
}

const upsertPreset = db.prepare(`
  INSERT INTO presets (id, name, description, sound_ids_json, is_default)
  VALUES (?, ?, ?, ?, 1)
  ON CONFLICT(id) DO NOTHING
`);
for (const p of catalog.defaultPresets) {
  upsertPreset.run(p.id, p.name, p.description, JSON.stringify(p.soundIds));
}

export function getCatalogWithState() {
  const statsRows = db.prepare('SELECT sound_id, play_count, last_played_at FROM sound_stats').all();
  const statMap = new Map(statsRows.map((r) => [r.sound_id, r]));

  const favRows = db.prepare('SELECT vehicle_id FROM favorites').all();
  const favSet = new Set(favRows.map((r) => r.vehicle_id));

  const vehicles = catalog.vehicles.map((v) => ({
    ...v,
    isFavorite: favSet.has(v.id),
    totalPlays: v.sounds.reduce((acc, s) => acc + (statMap.get(s.id)?.play_count || 0), 0),
    sounds: v.sounds.map((s) => ({
      ...s,
      playCount: statMap.get(s.id)?.play_count || 0
    }))
  }));

  return {
    app: catalog.app,
    categories: catalog.categories,
    vehicles,
    favorites: Array.from(favSet),
    presets: listPresets()
  };
}

export function recordPlay(soundId) {
  const vehicle = catalog.vehicles.find((v) => v.sounds.some((s) => s.id === soundId));
  if (!vehicle) return null;

  db.prepare(`
    UPDATE sound_stats
    SET play_count = play_count + 1, last_played_at = datetime('now')
    WHERE sound_id = ?
  `).run(soundId);

  db.prepare(`
    INSERT INTO play_logs (sound_id, vehicle_id) VALUES (?, ?)
  `).run(soundId, vehicle.id);

  const row = db.prepare('SELECT sound_id, play_count FROM sound_stats WHERE sound_id = ?').get(soundId);
  return { soundId, vehicleId: vehicle.id, playCount: row?.play_count || 1 };
}

export function toggleFavorite(vehicleId) {
  const exists = catalog.vehicles.some((v) => v.id === vehicleId);
  if (!exists) return null;

  const row = db.prepare('SELECT vehicle_id FROM favorites WHERE vehicle_id = ?').get(vehicleId);
  if (row) {
    db.prepare('DELETE FROM favorites WHERE vehicle_id = ?').run(vehicleId);
    return { vehicleId, isFavorite: false };
  } else {
    db.prepare('INSERT INTO favorites (vehicle_id) VALUES (?)').run(vehicleId);
    return { vehicleId, isFavorite: true };
  }
}

export function listPresets() {
  return db
    .prepare('SELECT id, name, description, sound_ids_json, is_default, created_at FROM presets ORDER BY is_default DESC, created_at DESC')
    .all()
    .map((r) => ({
      id: r.id,
      name: r.name,
      description: r.description,
      soundIds: JSON.parse(r.sound_ids_json),
      isDefault: Boolean(r.is_default),
      createdAt: r.created_at
    }));
}

export function createPreset({ name, description, soundIds }) {
  const id = `preset-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
  db.prepare(`
    INSERT INTO presets (id, name, description, sound_ids_json, is_default)
    VALUES (?, ?, ?, ?, 0)
  `).run(id, name.trim(), (description || '').trim(), JSON.stringify(soundIds));
  return listPresets();
}

export function deletePreset(id) {
  const info = db.prepare('DELETE FROM presets WHERE id = ? AND is_default = 0').run(id);
  return info.changes > 0;
}

export function getStats() {
  const totalRow = db.prepare('SELECT COALESCE(SUM(play_count), 0) AS totalPlays FROM sound_stats').get();
  const favRow = db.prepare('SELECT COUNT(*) AS favCount FROM favorites').get();
  const topSounds = db
    .prepare('SELECT sound_id, vehicle_id, play_count FROM sound_stats ORDER BY play_count DESC LIMIT 8')
    .all();

  return {
    totalPlays: totalRow?.totalPlays || 0,
    totalVehicles: catalog.vehicles.length,
    totalSounds: catalog.vehicles.reduce((a, v) => a + v.sounds.length, 0),
    favoriteCount: favRow?.favCount || 0,
    topSounds
  };
}
