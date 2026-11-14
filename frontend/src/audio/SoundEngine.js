class SoundEngine {
  constructor() {
    this.ctx = null;
    this.masterGain = null;
    this.compressor = null;
    this.analyser = null;
    this.freqData = null;

    this.buffers = new Map();
    this.loading = new Map();
    this.active = new Map(); // soundId -> { meta, gainNode, source, htmlAudio, volume, startedAt }
    this.volumes = new Map(); // persisted per-sound volume 0..1
    this.soloMode = false;
    this.listeners = new Set();
  }

  subscribe(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  emit() {
    const snapshot = this.getSnapshot();
    for (const fn of this.listeners) {
      fn(snapshot);
    }
  }

  getSnapshot() {
    const activeList = [];
    for (const [id, entry] of this.active.entries()) {
      activeList.push({
        id,
        meta: entry.meta,
        volume: entry.volume,
        startedAt: entry.startedAt
      });
    }
    return {
      activeIds: new Set(this.active.keys()),
      activeList,
      soloMode: this.soloMode
    };
  }

  ensureContext() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return null;
      this.ctx = new AudioCtx();
      this.compressor = this.ctx.createDynamicsCompressor();
      this.compressor.threshold.value = -10;
      this.compressor.knee.value = 18;
      this.compressor.ratio.value = 4;
      this.compressor.attack.value = 0.004;
      this.compressor.release.value = 0.15;

      this.masterGain = this.ctx.createGain();
      this.masterGain.gain.value = 0.95;

      this.analyser = this.ctx.createAnalyser();
      this.analyser.fftSize = 64;
      this.analyser.smoothingTimeConstant = 0.72;
      this.freqData = new Uint8Array(this.analyser.frequencyBinCount);

      this.compressor.connect(this.masterGain);
      this.masterGain.connect(this.analyser);
      this.analyser.connect(this.ctx.destination);
    }
    if (this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
    return this.ctx;
  }

  async loadBuffer(url) {
    if (this.buffers.has(url)) return this.buffers.get(url);
    if (this.loading.has(url)) return this.loading.get(url);

    const ctx = this.ensureContext();
    if (!ctx) return null;

    const promise = fetch(url)
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.arrayBuffer();
      })
      .then((ab) => ctx.decodeAudioData(ab))
      .then((buf) => {
        this.buffers.set(url, buf);
        this.loading.delete(url);
        return buf;
      })
      .catch((err) => {
        this.loading.delete(url);
        throw err;
      });

    this.loading.set(url, promise);
    return promise;
  }

  preload(urls) {
    for (const url of urls) {
      if (!this.buffers.has(url) && !this.loading.has(url)) {
        this.loadBuffer(url).catch(() => {});
      }
    }
  }

  setSoloMode(enabled) {
    this.soloMode = Boolean(enabled);
    if (this.soloMode && this.active.size > 1) {
      const keys = Array.from(this.active.keys());
      const keep = keys[keys.length - 1];
      for (const k of keys) {
        if (k !== keep) this.stop(k, false);
      }
    }
    this.emit();
  }

  async toggle(soundMeta) {
    if (navigator.vibrate) {
      try {
        navigator.vibrate(15);
      } catch {}
    }

    if (this.active.has(soundMeta.id)) {
      this.stop(soundMeta.id);
      return false;
    }

    if (this.soloMode) {
      this.stopAll(false);
    }

    await this.start(soundMeta);
    return true;
  }

  async start(soundMeta) {
    if (this.active.has(soundMeta.id)) return;

    const vol = this.volumes.get(soundMeta.id) ?? 0.85;
    const ctx = this.ensureContext();

    // Reserve slot immediately so UI responds on tap without double-triggering
    const placeholder = {
      meta: soundMeta,
      volume: vol,
      startedAt: Date.now(),
      cancelled: false,
      source: null,
      gainNode: null,
      htmlAudio: null
    };
    this.active.set(soundMeta.id, placeholder);
    this.emit();

    try {
      if (ctx) {
        const buffer = await this.loadBuffer(soundMeta.file);
        if (placeholder.cancelled || !this.active.has(soundMeta.id)) return;

        const source = ctx.createBufferSource();
        source.buffer = buffer;
        source.loop = true;
        if (buffer.duration > 0.25) {
          source.loopStart = 0.04;
          source.loopEnd = buffer.duration - 0.04;
        }

        const gainNode = ctx.createGain();
        gainNode.gain.setValueAtTime(0.001, ctx.currentTime);
        gainNode.gain.exponentialRampToValueAtTime(Math.max(0.001, vol), ctx.currentTime + 0.045);

        source.connect(gainNode);
        gainNode.connect(this.compressor);
        source.start(0, buffer.duration > 0.25 ? 0.04 : 0);

        placeholder.source = source;
        placeholder.gainNode = gainNode;
        return;
      }
    } catch {
      // Fall back to HTML5 looping audio
    }

    if (placeholder.cancelled || !this.active.has(soundMeta.id)) return;
    try {
      const audio = new Audio(soundMeta.file);
      audio.loop = true;
      audio.volume = vol;
      await audio.play();
      placeholder.htmlAudio = audio;
    } catch {
      this.active.delete(soundMeta.id);
      this.emit();
    }
  }

  stop(soundId, notify = true) {
    const entry = this.active.get(soundId);
    if (!entry) return;
    entry.cancelled = true;
    this.active.delete(soundId);

    if (entry.gainNode && this.ctx) {
      try {
        const now = this.ctx.currentTime;
        entry.gainNode.gain.cancelScheduledValues(now);
        entry.gainNode.gain.setValueAtTime(Math.max(0.001, entry.gainNode.gain.value), now);
        entry.gainNode.gain.exponentialRampToValueAtTime(0.0001, now + 0.06);
        setTimeout(() => {
          try {
            entry.source?.stop();
            entry.source?.disconnect();
            entry.gainNode?.disconnect();
          } catch {}
        }, 70);
      } catch {
        try {
          entry.source?.stop();
        } catch {}
      }
    } else if (entry.source) {
      try {
        entry.source.stop();
      } catch {}
    }

    if (entry.htmlAudio) {
      try {
        entry.htmlAudio.pause();
        entry.htmlAudio.src = '';
      } catch {}
    }

    if (notify) this.emit();
  }

  stopAll(notify = true) {
    for (const id of Array.from(this.active.keys())) {
      this.stop(id, false);
    }
    if (notify) this.emit();
  }

  setVolume(soundId, volume) {
    const v = Math.max(0, Math.min(1, Number(volume)));
    this.volumes.set(soundId, v);
    const entry = this.active.get(soundId);
    if (entry) {
      entry.volume = v;
      if (entry.gainNode && this.ctx) {
        entry.gainNode.gain.setTargetAtTime(Math.max(0.0001, v), this.ctx.currentTime, 0.02);
      }
      if (entry.htmlAudio) {
        entry.htmlAudio.volume = v;
      }
      this.emit();
    }
  }

  async playPreset(soundMetaList) {
    this.stopAll(false);
    if (this.soloMode && soundMetaList.length > 1) {
      this.soloMode = false;
    }
    await Promise.all(soundMetaList.map((s) => this.start(s)));
    this.emit();
  }

  getSpectrumBars(count = 16) {
    if (!this.analyser || !this.freqData || this.active.size === 0) {
      return new Array(count).fill(0);
    }
    this.analyser.getByteFrequencyData(this.freqData);
    const bars = [];
    const step = Math.max(1, Math.floor(this.freqData.length / count));
    for (let i = 0; i < count; i++) {
      const val = this.freqData[Math.min(i * step, this.freqData.length - 1)] || 0;
      bars.push(val / 255);
    }
    return bars;
  }
}

export const soundEngine = new SoundEngine();
