#!/usr/bin/env python3
"""
High-fidelity procedural acoustic vehicle sound synthesizer.
Generates 29 seamlessly loopable MP3 audio assets for OmniDrive Vehicle Sounds.
"""

import os
import math
import numpy as np
from scipy import signal
import av

SR = 44100
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "frontend", "public", "audio")
os.makedirs(OUT_DIR, exist_ok=True)


def make_noise(n, color="pink", seed=42):
    rng = np.random.default_rng(seed)
    white = rng.standard_normal(n)
    if color == "white":
        return white
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    freqs[0] = 1.0
    spec = np.fft.rfft(white)
    if color == "pink":
        spec /= np.sqrt(freqs)
    elif color == "brown":
        spec /= freqs
    out = np.fft.irfft(spec, n=n)
    out /= (np.max(np.abs(out)) + 1e-8)
    return out


def bandpass(x, low, high, order=3):
    nyq = SR * 0.5
    low_n = max(1e-4, min(0.99, low / nyq))
    high_n = max(low_n + 1e-4, min(0.99, high / nyq))
    sos = signal.butter(order, [low_n, high_n], btype="bandpass", output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, cutoff, order=3):
    nyq = SR * 0.5
    c = max(1e-4, min(0.99, cutoff / nyq))
    sos = signal.butter(order, c, btype="lowpass", output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, cutoff, order=2):
    nyq = SR * 0.5
    c = max(1e-4, min(0.99, cutoff / nyq))
    sos = signal.butter(order, c, btype="highpass", output="sos")
    return signal.sosfilt(sos, x)


def resonant_peak(x, center_hz, q=4.0, gain=2.0):
    b, a = signal.iirpeak(min(center_hz / (SR * 0.5), 0.98), q)
    return x + gain * signal.lfilter(b, a, x)


def simple_reverb(x, delays_ms=(23, 37, 53, 79), decays=(0.35, 0.25, 0.18, 0.12)):
    out = x.copy()
    for d_ms, dec in zip(delays_ms, decays):
        d = int(d_ms * SR / 1000)
        if d < len(x):
            padded = np.zeros_like(x)
            padded[d:] = x[:-d]
            out += dec * lowpass(padded, 3500, order=2)
    return out


def seamless_loop(x, fade_sec=0.25):
    """Equal-power wrap-around crossfade so end connects smoothly to start."""
    fade_n = int(fade_sec * SR)
    if fade_n * 2 >= len(x):
        return x
    body = x[:-fade_n].copy()
    tail = x[-fade_n:]
    t = np.linspace(0, np.pi * 0.5, fade_n)
    fade_in = np.sin(t)
    fade_out = np.cos(t)
    body[:fade_n] = body[:fade_n] * fade_in + tail * fade_out
    return body


def normalize_and_save(name, audio, target_peak=0.90, make_loop=True, fade_sec=0.28):
    if make_loop:
        audio = seamless_loop(audio, fade_sec=fade_sec)
    audio = audio - np.mean(audio)
    peak = np.max(np.abs(audio))
    if peak > 1e-6:
        audio = audio / peak
    # Gentle soft-knee loudness boost so transient-heavy sounds (flutter, horns, clacks) stay punchy
    rms = float(np.sqrt(np.mean(audio**2)))
    if 1e-4 < rms < 0.23:
        gain = min(2.4, 0.24 / rms)
        audio = np.tanh(audio * gain) / np.tanh(gain)
    peak = np.max(np.abs(audio))
    if peak > 1e-6:
        audio = audio * (target_peak / peak)
    audio = np.clip(audio, -0.98, 0.98).astype(np.float32)

    out_path = os.path.join(OUT_DIR, f"{name}.mp3")
    c = av.open(out_path, "w")
    stream = c.add_stream("libmp3lame", rate=SR, layout="mono")
    stream.bit_rate = 160000
    chunk_size = 1152 * 4
    for i in range(0, len(audio), chunk_size):
        chunk = audio[i : i + chunk_size]
        frame = av.AudioFrame.from_ndarray(chunk.reshape(1, -1), format="fltp", layout="mono")
        frame.sample_rate = SR
        for pkt in stream.encode(frame):
            c.mux(pkt)
    for pkt in stream.encode(None):
        c.mux(pkt)
    c.close()
    dur = len(audio) / SR
    rms = float(np.sqrt(np.mean(audio**2)))
    print(f"  [OK] {name}.mp3 | {dur:.2f}s | RMS={rms:.3f} | {os.path.getsize(out_path)//1024} KB")


def combustion_engine(rpm_curve, cylinders=4, warmth=0.6, rasp=0.4, seed=1):
    """Realistic 4-stroke internal combustion acoustic pulse model."""
    n = len(rpm_curve)
    firing_freq = rpm_curve * (cylinders / 120.0)
    # Add natural combustion cycle-to-cycle micro-jitter
    jitter = 1.0 + 0.018 * lowpass(make_noise(n, "white", seed=seed), 45)
    phase = np.cumsum((firing_freq * jitter) / SR) % 1.0

    # Asymmetric combustion exhaust pressure pulse
    pulse = np.exp(-((phase - 0.14) / 0.085) ** 2) - 0.38 * np.exp(-((phase - 0.34) / 0.13) ** 2)
    # Sub-fundamental + 2nd + 3rd order harmonics for mechanical richness
    sub = 0.35 * np.sin(2 * np.pi * phase * 0.5)
    h2 = 0.30 * np.sin(2 * np.pi * phase * 2.0 + 0.4)
    h3 = 0.18 * rasp * np.sin(2 * np.pi * phase * 3.0 + 0.9)
    raw = pulse + sub + h2 + h3

    # Multi-band exhaust body resonance
    body_low = resonant_peak(lowpass(raw, 420, order=3), 95, q=2.5, gain=1.6 * warmth)
    body_mid = bandpass(raw, 180, 950, order=2) * (0.7 + 0.5 * rasp)
    exhaust_rasp = bandpass(np.tanh(raw * (1.8 + 1.5 * rasp)), 600, 2600, order=2) * 0.45 * rasp

    # Intake air induction & mechanical valvetrain hiss scaled by RPM
    norm_rpm = np.clip((rpm_curve - np.min(rpm_curve)) / (np.ptp(rpm_curve) + 1e-5), 0.15, 1.0)
    intake = bandpass(make_noise(n, "pink", seed=seed + 10), 400, 3200) * (0.18 * norm_rpm)
    mech = bandpass(make_noise(n, "white", seed=seed + 20), 2200, 6500) * (0.04 * norm_rpm)

    sig = np.tanh((body_low + body_mid + exhaust_rasp) * 0.85) + intake + mech
    return sig


def build_all():
    print("Synthesizing studio vehicle sound library...")

    # 1. TOYOTA SUPRA MK4 (2JZ-GTE) ENGINE LOOP
    dur = 5.4
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    # Deep idle -> hard boost pull -> shift -> second pull -> decel
    rpm = (
        1400
        + 3800 * np.exp(-((t - 1.55) / 0.65) ** 2) * (t < 1.8)
        + 3600 * ((t >= 1.8) & (t < 2.25)) * np.linspace(1, 0.72, n)[:n]
    )
    # Cleaner handcrafted piecewise RPM curve
    knots_t = [0.0, 0.5, 1.8, 2.05, 3.4, 3.85, 4.7, 5.4]
    knots_rpm = [1250, 1500, 5800, 3900, 6600, 2800, 1650, 1250]
    rpm = np.interp(t, knots_t, knots_rpm)
    eng = combustion_engine(rpm, cylinders=6, warmth=0.85, rasp=0.65, seed=101)
    # Twin-turbo spool whine tracking boost (high RPM)
    boost = np.clip((rpm - 2600) / 4000.0, 0.0, 1.0) ** 1.3
    turbo_freq = 1900 + 3800 * boost
    turbo_phase = np.cumsum(turbo_freq / SR)
    turbo_whine = (0.16 * boost) * (np.sin(2 * np.pi * turbo_phase) + 0.4 * np.sin(4 * np.pi * turbo_phase))
    turbo_air = bandpass(make_noise(n, "pink", seed=102), 2000, 6500) * (0.22 * boost)
    # Lift-off overrun crackles around t=2.0 and t=3.6..4.3
    pop_mask = np.exp(-((t - 1.98) / 0.12) ** 2) + np.exp(-((t - 3.75) / 0.35) ** 2)
    pops = np.zeros(n)
    rng = np.random.default_rng(103)
    for pt in [1.92, 1.99, 3.55, 3.66, 3.78, 3.92, 4.08]:
        idx = int(pt * SR)
        L = int(0.06 * SR)
        if idx + L < n:
            burst = rng.standard_normal(L) * np.exp(-np.linspace(0, 12, L))
            pops[idx : idx + L] += bandpass(burst, 250, 2200) * 1.4
    supra = eng + turbo_whine + turbo_air + pops * 0.7
    normalize_and_save("supra_engine", supra)

    # 2. SUPRA TURBO FLUTTER (STUTUTUTU!) + BOOST PULL
    dur = 4.6
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    knots_t = [0.0, 0.4, 1.5, 1.65, 2.5, 3.2, 4.1, 4.6]
    knots_rpm = [1600, 2100, 6500, 3100, 2400, 6200, 2600, 1600]
    rpm = np.interp(t, knots_t, knots_rpm)
    eng = combustion_engine(rpm, cylinders=6, warmth=0.75, rasp=0.72, seed=104)
    boost = np.clip((rpm - 2400) / 4100.0, 0.0, 1.0)
    spool_f = 2100 + 4200 * boost
    spool = (0.20 * boost) * np.sin(2 * np.pi * np.cumsum(spool_f / SR))

    # Signature STUTUTU compressor surge flutter at t=1.55 and t=3.25
    flutter = np.zeros(n)
    for lift_t in [1.55, 3.25]:
        rel = t - lift_t
        active = (rel >= 0) & (rel < 1.1)
        env = np.where(active, np.exp(-rel * 3.4), 0.0)
        # Rapid 16Hz -> 10Hz decaying flutter chops
        chop_freq = np.where(active, 17.5 - 6.0 * rel, 15.0)
        chop_phase = np.cumsum(chop_freq / SR) % 1.0
        chop = np.exp(-((chop_phase - 0.15) / 0.18) ** 2)
        chirp_f = 2600 + 1300 * np.exp(-rel * 2.5) + 450 * np.sin(2 * np.pi * chop_phase)
        chirp_sig = (
            0.55 * np.sin(2 * np.pi * np.cumsum(chirp_f / SR))
            + 0.35 * np.sin(2 * np.pi * np.cumsum((chirp_f * 1.48) / SR))
            + 0.45 * bandpass(make_noise(n, "white", seed=105), 1800, 5200)
        )
        flutter += env * chop * chirp_sig * 1.35

    normalize_and_save("supra_turbo_flutter", eng * 0.72 + spool + flutter)

    # 3. SUPERCAR V10 / V12 EXOTIC SCREAM
    dur = 5.2
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    knots_t = [0.0, 0.5, 1.6, 1.75, 2.85, 3.05, 4.1, 4.6, 5.2]
    knots_rpm = [2200, 3200, 8400, 5800, 8600, 5500, 8200, 3200, 2200]
    rpm = np.interp(t, knots_t, knots_rpm)
    v10 = combustion_engine(rpm, cylinders=10, warmth=0.45, rasp=0.92, seed=106)
    # Add F1 metallic exhaust resonance
    v10 = resonant_peak(v10, 1150, q=3.2, gain=0.8) + resonant_peak(v10, 2400, q=4.0, gain=0.45) * 0.3
    normalize_and_save("supercar_v10", simple_reverb(v10, (18, 31), (0.22, 0.14)))

    # 4. SUPERCAR LAUNCH & REVS
    dur = 4.5
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    knots_t = [0.0, 0.3, 0.8, 1.15, 1.6, 2.2, 2.55, 3.3, 3.9, 4.5]
    knots_rpm = [1900, 7600, 2800, 8200, 2600, 7900, 3100, 6800, 2400, 1900]
    rpm = np.interp(t, knots_t, knots_rpm)
    v12 = combustion_engine(rpm, cylinders=12, warmth=0.5, rasp=0.95, seed=107)
    normalize_and_save("supercar_launch", simple_reverb(v12, (20, 35), (0.25, 0.15)))

    # 5. CAR DRIVING SOUND (Cruising + Gear Shifts + Tire/Road Hum)
    dur = 5.6
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    knots_t = [0.0, 0.6, 1.9, 2.15, 3.5, 3.75, 4.9, 5.6]
    knots_rpm = [1400, 2100, 3900, 2300, 3700, 2200, 2500, 1400]
    rpm = np.interp(t, knots_t, knots_rpm)
    car_eng = combustion_engine(rpm, cylinders=4, warmth=0.78, rasp=0.30, seed=108)
    # Tire asphalt road noise & cabin wind rush
    speed_env = np.clip((t / 3.5), 0.35, 1.0) * (1.0 - 0.35 * np.maximum(0, (t - 4.8) / 0.8))
    road_rumble = bandpass(make_noise(n, "brown", seed=109), 45, 280) * 0.65 * speed_env
    wind = bandpass(make_noise(n, "pink", seed=110), 280, 1400) * 0.20 * speed_env
    normalize_and_save("car_driving", car_eng * 0.72 + road_rumble + wind)

    # 6. CAR HORN (Dual-Tone Diaphragm Beep-Beep Loop)
    dur = 2.6
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    env = np.zeros(n)
    for start, end in [(0.12, 0.42), (0.58, 1.28), (1.75, 2.05)]:
        mask = (t >= start) & (t <= end)
        attack = np.clip((t - start) / 0.018, 0, 1)
        rel = np.clip((end - t) / 0.035, 0, 1)
        env = np.maximum(env, np.where(mask, attack * rel, 0))
    # Classic dual-tone F#4/A#4-ish car horn (~415 Hz + 520 Hz) with brass harmonics
    tone = np.zeros(n)
    for f0 in [412.0, 518.0]:
        for k, amp in [(1, 1.0), (2, 0.65), (3, 0.55), (4, 0.38), (5, 0.25), (6, 0.14)]:
            tone += amp * np.sin(2 * np.pi * f0 * k * t)
    horn = bandpass(tone, 320, 3200, order=2)
    horn = resonant_peak(horn, 1350, q=2.5, gain=1.1)
    horn = simple_reverb(horn * env, (28, 49, 77), (0.30, 0.20, 0.12))
    normalize_and_save("car_horn", horn, fade_sec=0.10)

    # 7. BIKE ENGINE SOUND (Sportbike Inline-4 High RPM)
    dur = 5.0
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    knots_t = [0.0, 0.4, 1.35, 1.5, 2.5, 2.7, 3.6, 4.3, 5.0]
    knots_rpm = [2200, 3500, 11800, 7200, 12600, 6800, 10500, 3200, 2200]
    rpm = np.interp(t, knots_t, knots_rpm)
    bike = combustion_engine(rpm, cylinders=4, warmth=0.42, rasp=0.88, seed=111)
    # Chain & straight-cut gearbox whine
    gear_whine = 0.09 * np.sin(2 * np.pi * np.cumsum((rpm * 0.22) / SR))
    normalize_and_save("bike_engine", bike + gear_whine)

    # 8. BIKE HORN (Sharp Single Disc Horn)
    dur = 2.2
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    env = np.zeros(n)
    for start, end in [(0.12, 0.36), (0.48, 0.72), (1.05, 1.55)]:
        mask = (t >= start) & (t <= end)
        env = np.maximum(env, np.where(mask, np.clip((t - start) / 0.01, 0, 1) * np.clip((end - t) / 0.025, 0, 1), 0))
    tone = sum(amp * np.sin(2 * np.pi * 495.0 * k * t) for k, amp in [(1, 0.9), (2, 0.8), (3, 0.7), (4, 0.5), (5, 0.35), (6, 0.2)])
    b_horn = resonant_peak(bandpass(tone, 420, 3600), 1750, q=3.0, gain=1.4)
    normalize_and_save("bike_horn", simple_reverb(b_horn * env, (22, 41), (0.22, 0.14)), fade_sec=0.08)

    # 9. DIRT BIKE (2-Stroke Snappy Braap)
    dur = 4.4
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    knots_t = [0.0, 0.3, 0.7, 1.1, 1.7, 2.0, 2.8, 3.3, 4.0, 4.4]
    knots_rpm = [1900, 7200, 2800, 8400, 3100, 8900, 2900, 7500, 2300, 1900]
    rpm = np.interp(t, knots_t, knots_rpm)
    # 2-stroke fires twice as often per RPM -> pass cylinders=2 * 2 = 4 equivalent or cylinders=2 at 2x rpm
    dirt = combustion_engine(rpm * 1.4, cylinders=2, warmth=0.35, rasp=0.98, seed=112)
    dirt = resonant_peak(dirt, 780, q=2.5, gain=1.2)
    normalize_and_save("dirtbike", dirt)

    # 10. POLICE CAR SIREN (Authentic Wail + Yelp Cycle)
    dur = 6.0
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    # First 3.5s: slow Wail (640 -> 1520 -> 640); Next 2.5s: fast Yelp
    wail = 1080 + 440 * np.sin(2 * np.pi * (t / 3.5) - np.pi / 2)
    yelp = 1080 + 430 * np.sin(2 * np.pi * (t * 2.8))
    blend = np.clip((t - 3.2) / 0.3, 0, 1) * np.clip((5.7 - t) / 0.3, 0, 1)
    freq = wail * (1 - blend) + yelp * blend
    ph = np.cumsum(freq / SR)
    # Rich bullhorn waveform (fundamental + odd/even harmonics)
    raw = np.sin(2 * np.pi * ph) + 0.45 * np.sin(4 * np.pi * ph) + 0.32 * np.sin(6 * np.pi * ph) + 0.15 * np.sin(8 * np.pi * ph)
    siren = resonant_peak(bandpass(np.tanh(raw * 1.3), 500, 4200), 1650, q=2.2, gain=1.3)
    siren = simple_reverb(siren, (35, 68, 110), (0.32, 0.22, 0.14))
    normalize_and_save("police_siren", siren)

    # 11. POLICE CAR V8 PURSUIT ENGINE
    dur = 5.0
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = np.interp(t, [0, 0.6, 1.9, 2.2, 3.6, 4.3, 5.0], [1200, 2400, 4900, 3200, 5200, 2100, 1200])
    v8 = combustion_engine(rpm, cylinders=8, warmth=0.90, rasp=0.58, seed=113)
    road = bandpass(make_noise(n, "brown", seed=114), 50, 320) * 0.45
    normalize_and_save("police_engine", v8 + road)

    # 12. AMBULANCE SIREN (Hi-Lo + Urgent Yelp Rescue)
    dur = 5.6
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    # Hi-Lo alternating 780 Hz / 585 Hz for first 3.2s, then rapid Yelp for 2.4s
    hilo_cycle = ((t * 1.55) % 1.0) < 0.5
    hilo_f = np.where(hilo_cycle, 780.0, 585.0)
    # Smooth pitch glide at transitions
    hilo_f = lowpass(hilo_f, 38, order=2)
    yelp_f = 960 + 360 * np.sin(2 * np.pi * (t * 3.1))
    mix = np.clip((t - 3.0) / 0.2, 0, 1) * np.clip((5.35 - t) / 0.2, 0, 1)
    freq = hilo_f * (1 - mix) + yelp_f * mix
    ph = np.cumsum(freq / SR)
    raw = np.sin(2 * np.pi * ph) + 0.40 * np.sin(4 * np.pi * ph) + 0.28 * np.sin(6 * np.pi * ph)
    amb_siren = simple_reverb(resonant_peak(bandpass(np.tanh(raw * 1.2), 450, 3800), 1450, q=2.0, gain=1.1), (32, 61, 95), (0.30, 0.20, 0.12))
    normalize_and_save("ambulance_siren", amb_siren)

    # 13. AMBULANCE DIESEL VAN ENGINE
    dur = 4.8
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = np.interp(t, [0, 0.8, 2.2, 3.4, 4.2, 4.8], [950, 1650, 2600, 2200, 1300, 950])
    amb_eng = combustion_engine(rpm, cylinders=6, warmth=0.85, rasp=0.38, seed=115)
    diesel_ping = bandpass(make_noise(n, "white", seed=116), 1800, 3800) * 0.08
    normalize_and_save("ambulance_engine", amb_eng + diesel_ping)

    # 14. FIRE BRIGADE SIREN (Federal Q2B Mechanical Coaster + Air Horn)
    dur = 6.0
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    # Mechanical siren spins up slow to 1120Hz, coasts down to 360Hz, spins up again
    q_freq = np.interp(t, [0.0, 1.8, 3.6, 4.9, 6.0], [340, 1140, 420, 1080, 340])
    ph = np.cumsum(q_freq / SR)
    # Mechanical rotor harmonics + air rush
    q_siren = np.sin(2 * np.pi * ph) + 0.55 * np.sin(4 * np.pi * ph) + 0.35 * np.sin(6 * np.pi * ph) + 0.2 * np.sin(8 * np.pi * ph)
    q_siren = resonant_peak(bandpass(np.tanh(q_siren * 1.3), 260, 4200), 1200, q=2.2, gain=1.1)
    # Add deep fire truck diesel underneath + 2 quick air horn blasts
    diesel = combustion_engine(np.full(n, 1150.0), cylinders=6, warmth=0.95, rasp=0.4, seed=117) * 0.35
    ah_env = np.where(((t > 2.4) & (t < 2.85)) | ((t > 3.0) & (t < 3.45)), 1.0, 0.0)
    ah_env = lowpass(ah_env, 35, order=2)
    ah_tone = sum(np.sin(2 * np.pi * f * t) + 0.5 * np.sin(4 * np.pi * f * t) for f in [195.0, 246.0])
    fire = simple_reverb(q_siren * 0.85 + diesel + ah_tone * ah_env * 0.45, (35, 70), (0.28, 0.18))
    normalize_and_save("firebrigade_siren", fire)

    # 15. FIRE TRUCK DIESEL & WATER PUMP
    dur = 5.0
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = np.interp(t, [0, 1.0, 2.5, 3.8, 5.0], [850, 1850, 2100, 1500, 850])
    f_eng = combustion_engine(rpm, cylinders=8, warmth=0.95, rasp=0.45, seed=118)
    pump_whine = 0.12 * np.sin(2 * np.pi * 620 * t) + bandpass(make_noise(n, "pink", seed=119), 500, 2500) * 0.18
    normalize_and_save("firebrigade_engine", f_eng + pump_whine)

    # 16. SEMI TRUCK DIESEL & JAKE BRAKE
    dur = 5.4
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = np.interp(t, [0, 0.7, 2.0, 2.4, 3.6, 4.7, 5.4], [680, 950, 1750, 1200, 1650, 800, 680])
    truck = combustion_engine(rpm, cylinders=6, warmth=1.0, rasp=0.52, seed=120)
    # Jake brake choppy compression release growl around t=3.6..4.5
    jake_mask = np.exp(-((t - 4.0) / 0.35) ** 2)
    jake_pulse = np.sin(2 * np.pi * 42 * t) > 0
    truck += jake_mask * jake_pulse * bandpass(make_noise(n, "brown", seed=121), 80, 650) * 0.85
    # Subtle diesel turbo whistle
    truck += 0.08 * np.clip((rpm - 1100) / 700, 0, 1) * np.sin(2 * np.pi * np.cumsum((1800 + rpm * 0.9) / SR))
    normalize_and_save("truck_engine", truck)

    # 17. TRUCK AIR HORN (Massive Twin-Trumpet Brass Chord)
    dur = 3.2
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    env = np.zeros(n)
    for start, end in [(0.15, 1.35), (1.75, 2.45)]:
        mask = (t >= start) & (t <= end)
        env = np.maximum(env, np.where(mask, np.clip((t - start) / 0.04, 0, 1) * np.clip((end - t) / 0.09, 0, 1), 0))
    chord = np.zeros(n)
    for f0 in [185.0, 233.0, 277.0]:
        for k, amp in [(1, 1.0), (2, 0.75), (3, 0.55), (4, 0.35), (5, 0.22)]:
            chord += amp * np.sin(2 * np.pi * f0 * k * t)
    air_hiss = bandpass(make_noise(n, "pink", seed=122), 1200, 4800) * 0.14
    air_horn = simple_reverb((bandpass(chord, 140, 2800) * 0.45 + air_hiss) * env, (45, 90, 140), (0.35, 0.24, 0.15))
    normalize_and_save("truck_airhorn", air_horn, fade_sec=0.14)

    # 18. CITY BUS ENGINE + PNEUMATIC AIR BRAKE HISS
    dur = 5.4
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = np.interp(t, [0, 0.9, 2.3, 3.3, 4.3, 5.4], [700, 1450, 1800, 780, 720, 700])
    bus = combustion_engine(rpm, cylinders=6, warmth=0.95, rasp=0.35, seed=123)
    # Pneumatic air brake / door release psshhh at t=3.4 and t=4.2
    hiss_env = np.exp(-((t - 3.45) / 0.18) ** 2) * (t >= 3.3) + 0.8 * np.exp(-((t - 4.35) / 0.22) ** 2) * (t >= 4.2)
    hiss = bandpass(make_noise(n, "white", seed=124), 1600, 6800) * hiss_env * 0.75
    normalize_and_save("bus_engine", bus * 0.85 + hiss)

    # 19. BUS HORN
    dur = 2.6
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    env = np.where((t > 0.15) & (t < 0.95), np.clip((t - 0.15) / 0.03, 0, 1) * np.clip((0.95 - t) / 0.06, 0, 1), 0)
    env += np.where((t > 1.3) & (t < 1.85), np.clip((t - 1.3) / 0.03, 0, 1) * np.clip((1.85 - t) / 0.06, 0, 1), 0)
    tone = sum(amp * np.sin(2 * np.pi * f * k * t) for f in [262.0, 330.0] for k, amp in [(1, 1.0), (2, 0.6), (3, 0.4), (4, 0.25)])
    normalize_and_save("bus_horn", simple_reverb(bandpass(tone, 220, 2600) * env, (35, 65), (0.28, 0.18)), fade_sec=0.12)

    # 20. TRACTOR / BULLDOZER DIESEL
    dur = 4.8
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = 780 + 260 * np.sin(2 * np.pi * t / 4.8) + 180 * np.exp(-((t - 2.2) / 0.6) ** 2)
    trac = combustion_engine(rpm, cylinders=4, warmth=1.0, rasp=0.58, seed=125)
    hyd = 0.08 * np.sin(2 * np.pi * 410 * t)
    normalize_and_save("tractor_engine", trac + hyd)

    # 21. HELICOPTER (Twin-Blade BVI Whop-Whop-Whop + Turboshaft Whine)
    dur = 5.2
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    blade_hz = 14.5 + 0.6 * np.sin(2 * np.pi * t / 5.2)
    b_ph = np.cumsum(blade_hz / SR) % 1.0
    # Sharp blade-vortex interaction slap pulse + heavy sub thump
    slap = np.exp(-((b_ph - 0.12) / 0.065) ** 2) - 0.45 * np.exp(-((b_ph - 0.24) / 0.11) ** 2)
    thump = lowpass(slap, 260, order=2) * 1.6
    # Modulated rotor downwash swoosh
    wash_mod = 0.4 + 0.9 * np.exp(-((b_ph - 0.18) / 0.22) ** 2)
    wash = bandpass(make_noise(n, "pink", seed=126), 120, 1400) * wash_mod * 0.85
    # Tail rotor + turboshaft turbine whine
    tail_rotor = 0.14 * np.sin(2 * np.pi * 68.0 * t)
    turbine = 0.11 * np.sin(2 * np.pi * 2380.0 * t) + 0.06 * np.sin(2 * np.pi * 4760.0 * t)
    heli = simple_reverb(np.tanh((thump + wash + tail_rotor) * 0.9) + turbine, (40, 85), (0.25, 0.15))
    normalize_and_save("helicopter", heli)

    # 22. PASSENGER JET PLANE (Turbofan Spool + Cabin/Runway Jet Roar)
    dur = 6.0
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    thrust = 0.55 + 0.45 * np.sin(2 * np.pi * (t / 6.0) - np.pi / 2)
    # Deep jet exhaust broadband roar
    jet_roar = bandpass(make_noise(n, "brown", seed=127), 38, 650) * (0.65 + 0.7 * thrust)
    jet_air = bandpass(make_noise(n, "pink", seed=128), 500, 4200) * (0.25 + 0.45 * thrust)
    # N1 fan buzzsaw + compressor harmonics
    fan_f = 105 + 65 * thrust
    fan_ph = np.cumsum(fan_f / SR)
    fan = 0.16 * thrust * (np.sin(2 * np.pi * fan_ph) + 0.6 * np.sin(4 * np.pi * fan_ph) + 0.35 * np.sin(6 * np.pi * fan_ph))
    comp = 0.08 * thrust * np.sin(2 * np.pi * np.cumsum((1800 + 1100 * thrust) / SR))
    normalize_and_save("plane_jet", jet_roar + jet_air + fan + comp)

    # 23. FIGHTER JET AFTERBURNER (Supersonic Crackle & Flyby)
    dur = 5.6
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    flyby = 0.35 + 0.85 * np.exp(-((t - 2.6) / 1.1) ** 2)
    raw_noise = make_noise(n, "pink", seed=129)
    # Mach shock-cell crackle via asymmetric nonlinear shaping of mid-band turbulence
    crackle = np.tanh(bandpass(raw_noise, 300, 3500) * 4.5) * 0.55
    sub_boom = bandpass(make_noise(n, "brown", seed=130), 32, 220) * 1.2
    whine = 0.14 * np.sin(2 * np.pi * np.cumsum((3400 - 1500 * np.clip(t / 5.6, 0, 1)) / SR))
    fjet = (crackle + sub_boom) * flyby + whine * (flyby * 0.7)
    normalize_and_save("fighter_jet", simple_reverb(fjet, (50, 110), (0.25, 0.15)))

    # 24. PROPELLER PLANE (Radial / Twin-Prop Aircraft)
    dur = 4.8
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = 1650 + 250 * np.sin(2 * np.pi * t / 4.8)
    prop_eng = combustion_engine(rpm, cylinders=6, warmth=0.85, rasp=0.45, seed=131)
    # Rhythmic propeller blade-pass chop (~78 Hz)
    prop_chop = 0.45 * np.sin(2 * np.pi * 76.0 * t) + bandpass(make_noise(n, "pink", seed=132), 180, 1100) * (0.3 + 0.25 * np.sin(2 * np.pi * 76.0 * t))
    normalize_and_save("prop_plane", prop_eng * 0.65 + prop_chop)

    # 25. QUADCOPTER DRONE (4 Detuned Brushless Motors)
    dur = 4.4
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    wobble = 1.0 + 0.08 * np.sin(2 * np.pi * t / 2.2) + 0.03 * np.sin(2 * np.pi * t * 1.7)
    drone = np.zeros(n)
    for f0 in [218.0, 225.0, 232.0, 239.0]:
        ph = np.cumsum((f0 * wobble) / SR)
        drone += 0.35 * np.sin(2 * np.pi * ph) + 0.28 * np.sin(4 * np.pi * ph) + 0.18 * np.sin(6 * np.pi * ph) + 0.10 * np.sin(8 * np.pi * ph)
    tip_hiss = bandpass(make_noise(n, "white", seed=133), 2400, 7500) * 0.20 * wobble
    normalize_and_save("drone", bandpass(drone, 160, 4500) * 0.6 + tip_hiss)

    # 26. TRAIN LOCOMOTIVE (Diesel-Electric Chug + Steel Rail Clack)
    dur = 5.2
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    chug = combustion_engine(np.full(n, 740.0), cylinders=12, warmth=0.95, rasp=0.35, seed=134) * 0.65
    # Characteristic rail joint clack-clack ... clack-clack
    clacks = np.zeros(n)
    rng = np.random.default_rng(135)
    for ct in [0.45, 0.62, 1.75, 1.92, 3.05, 3.22, 4.35, 4.52]:
        idx = int(ct * SR)
        L = int(0.05 * SR)
        if idx + L < n:
            imp = rng.standard_normal(L) * np.exp(-np.linspace(0, 14, L))
            clacks[idx : idx + L] += bandpass(imp, 350, 2800) * 1.5
    rumble = bandpass(make_noise(n, "brown", seed=136), 35, 190) * 0.65
    normalize_and_save("train_engine", chug + clacks + rumble)

    # 27. TRAIN HORN (Iconic 5-Chime Locomotive Chord)
    dur = 3.8
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    env = np.zeros(n)
    for start, end in [(0.18, 1.15), (1.45, 2.05), (2.35, 3.35)]:
        mask = (t >= start) & (t <= end)
        env = np.maximum(env, np.where(mask, np.clip((t - start) / 0.05, 0, 1) * np.clip((end - t) / 0.12, 0, 1), 0))
    chord = sum(amp * np.sin(2 * np.pi * f * k * t) for f in [261.6, 311.1, 392.0, 466.2] for k, amp in [(1, 1.0), (2, 0.55), (3, 0.35)])
    normalize_and_save("train_horn", simple_reverb(bandpass(chord, 200, 3000) * env, (55, 110, 180), (0.4, 0.28, 0.18)), fade_sec=0.16)

    # 28. SPEEDBOAT V8 & WATER WAKE
    dur = 4.8
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    rpm = np.interp(t, [0, 0.7, 2.0, 3.5, 4.8], [1600, 2800, 4600, 3800, 1600])
    boat = combustion_engine(rpm, cylinders=8, warmth=0.82, rasp=0.55, seed=137) * 0.75
    wake = bandpass(make_noise(n, "pink", seed=138), 350, 4500) * (0.45 + 0.25 * np.sin(2 * np.pi * 2.2 * t))
    normalize_and_save("speedboat", boat + wake)

    # 29. CARGO SHIP FOGHORN
    dur = 4.2
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    env = np.where((t > 0.25) & (t < 3.1), np.clip((t - 0.25) / 0.18, 0, 1) * np.clip((3.1 - t) / 0.45, 0, 1), 0)
    fog = sum(amp * np.sin(2 * np.pi * f * k * t) for f in [68.0, 102.0] for k, amp in [(1, 1.0), (2, 0.7), (3, 0.45), (4, 0.25)])
    normalize_and_save("ship_horn", simple_reverb(bandpass(fog, 50, 900) * env, (80, 160, 260), (0.45, 0.32, 0.22)), fade_sec=0.20)

    # 30. HYPER EV / SCI-FI HOVERCRAFT
    dur = 4.8
    n = int(dur * SR)
    t = np.linspace(0, dur, n, endpoint=False)
    sweep = 0.5 + 0.5 * np.sin(2 * np.pi * (t / 4.8) - np.pi / 2)
    f_inv = 220 + 480 * sweep
    ph = np.cumsum(f_inv / SR)
    ev = (
        0.5 * np.sin(2 * np.pi * ph)
        + 0.3 * np.sin(2 * np.pi * ph * 1.5)
        + 0.25 * np.sin(2 * np.pi * ph * 3.0)
        + 0.18 * np.sin(2 * np.pi * np.cumsum((1200 + 2200 * sweep) / SR))
    )
    ion_air = bandpass(make_noise(n, "pink", seed=139), 400, 2800) * (0.2 + 0.25 * sweep)
    normalize_and_save("futuristic_ev", ev * 0.65 + ion_air)

    print(f"\nDone! Generated 30 MP3 sound assets in {OUT_DIR}")


if __name__ == "__main__":
    build_all()
