#!/usr/bin/env python3
"""Eigene Sounds für Cutscenes (kuby:cine.*), komplett synthetisch (numpy), als OGG ins Pack.

Stil: wuchtig und filmisch (tiefer Bass, harter Anschlag, langer Nachhall). Stereo-Dateien spielt Minecraft ohne
Richtung ab ("im Kopf"), darum alles in Stereo mit breitem Hall.
Braucht ffmpeg mit libvorbis. Aufruf: python3 tools/build_cine_sounds.py [pack-ordner]
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")
SND = os.path.join(OUT, "assets/kuby/sounds/cine")
rng = np.random.default_rng(1234)


def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def noise(sec):
    return rng.standard_normal(int(sec * SR))


def env(n, a, d, sustain=1.0, release=None):
    """Hüllkurve: Anstieg a s, Abfall auf sustain in d s, am Ende release s ausklingen."""
    t = np.arange(n) / SR
    e = np.minimum(1, t / max(a, 1e-4))
    if d:
        e = np.where(t > a, sustain + (1 - sustain) * np.exp(-(t - a) / d), e)
    if release:
        tail = np.clip((n / SR - t) / release, 0, 1)
        e *= tail
    return e


def decay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def spectral(x, gain_fn, nfft=2048, hop=512):
    """Zeitabhängiger Filter über STFT: gain_fn(freqs, sekunde) -> Verstärkung je Frequenz."""
    win = np.hanning(nfft)
    pad = np.concatenate([np.zeros(nfft), x, np.zeros(nfft)])
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    freqs = np.fft.rfftfreq(nfft, 1 / SR)
    for start in range(0, len(pad) - nfft, hop):
        seg = pad[start:start + nfft] * win
        sec = max(0.0, (start - nfft) / SR)
        spec = np.fft.rfft(seg) * gain_fn(freqs, sec)
        out[start:start + nfft] += np.fft.irfft(spec, nfft) * win
        norm[start:start + nfft] += win ** 2
    out = out / np.maximum(norm, 1e-6)
    return out[nfft:nfft + len(x)]


def lowpass(x, fc, q=0.7):
    return spectral(x, lambda f, s: 1 / np.sqrt(1 + (f / fc) ** (2 * 2)))


def highpass(x, fc):
    return spectral(x, lambda f, s: 1 / np.sqrt(1 + (fc / np.maximum(f, 1)) ** 4))


def bandpass(x, fc_fn, bw):
    return spectral(x, lambda f, s: np.exp(-0.5 * ((f - fc_fn(s)) / bw) ** 2))


def reverb(x, sec=3.0, damp=4000, wet=0.35, pre=0.02):
    """Stereo-Hall: abklingendes Rauschen als Impulsantwort (je Kanal anders), Höhen klingen schneller ab."""
    n = int(sec * SR)
    # trockenes Signal weich enden lassen (sonst knackt es, wo es aufhört)
    k = min(len(x) // 3, int(0.3 * SR))
    x = x.copy()
    x[-k:] *= np.linspace(1, 0, k) ** 2
    outs = []
    for ch in range(2):
        ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / (sec / 6.9))
        ir = spectral(ir, lambda f, s: np.exp(-f / damp * (0.3 + s)))
        ir = np.concatenate([np.zeros(int(pre * SR)), ir])
        ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
        m = len(x) + len(ir)
        y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)
        outs.append(y)
    dry = np.concatenate([x, np.zeros(len(outs[0]) - len(x))])
    return np.stack([dry * (1 - wet) + outs[0] * wet * 2.2, dry * (1 - wet) + outs[1] * wet * 2.2], axis=1)


def stereo(x, width=0.0):
    if x.ndim == 2:
        return x
    if width <= 0:
        return np.stack([x, x], axis=1)
    d = int(0.012 * SR)
    r = np.concatenate([np.zeros(d), x[:-d]])
    return np.stack([x * (1 - width / 2) + r * width / 2, x], axis=1)


def master(x, drive=1.0, peak=0.93):
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * drive * 1.6) if drive else x
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def fade(x, tin=0.005, tout=0.08):
    n = len(x)
    e = np.ones(n)
    a, b = int(tin * SR), int(tout * SR)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if b:
        e[-b:] *= np.linspace(1, 0, b)
    return x * (e[:, None] if x.ndim == 2 else e)


def saw(freq_t, sec):
    """Sägezahn mit zeitabhängiger Frequenz (Array oder Zahl), weich (bandbegrenzt durch Summe der Obertöne)."""
    n = int(sec * SR)
    f = np.broadcast_to(np.asarray(freq_t, dtype=float), (n,)) if np.ndim(freq_t) else np.full(n, float(freq_t))
    ph = np.cumsum(f) / SR * 2 * np.pi
    out = np.zeros(n)
    for k in range(1, 24):
        mask = f * k < SR / 2.2
        out += np.where(mask, np.sin(ph * k) / k, 0)
    return out * 0.6


def sine(freq_t, sec, phase=0.0):
    n = int(sec * SR)
    f = np.broadcast_to(np.asarray(freq_t, dtype=float), (n,)) if np.ndim(freq_t) else np.full(n, float(freq_t))
    return np.sin(np.cumsum(f) / SR * 2 * np.pi + phase)


# =====================================================================================================================
# Sounds
# =====================================================================================================================

def boom(sec, f0, f1, drop, sub_amp=1.0, crack=0.6, body=0.8):
    """Tiefer Einschlag: Bass fällt von f0 auf f1, Rauschstoß, harter Anschlag."""
    t = t_axis(sec)
    n = len(t)
    f = f1 + (f0 - f1) * np.exp(-t / drop)
    sub = sine(f, sec) * decay(n, sec * 0.28) * sub_amp
    nb = lowpass(noise(sec), 900) * decay(n, 0.35) * body
    cr = highpass(noise(sec), 2500) * decay(n, 0.03) * crack
    return sub + nb + cr


def s_impact():
    x = boom(6.0, 95, 26, 0.35, 1.2, 0.8, 1.0)
    x += boom(6.0, 180, 48, 0.08, 0.4, 0.0, 0.3)
    x = master(x, 1.4)
    return fade(reverb(x, 4.5, 2500, 0.42), 0.0, 0.8)


def s_impact_small():
    x = boom(3.0, 120, 40, 0.18, 0.9, 0.6, 0.6)
    return fade(reverb(master(x, 1.2), 2.4, 3000, 0.35), 0.0, 0.5)


def s_rumble():
    sec = 6.0
    t = t_axis(sec)
    x = lowpass(noise(sec), 110) * 6
    lfo = 0.6 + 0.4 * np.abs(np.sin(t * 2.1) * np.sin(t * 0.77 + 1))
    x *= lfo
    for _ in range(14):
        p = int(rng.uniform(0.2, 5.5) * SR)
        b = highpass(noise(0.08), 1500) * decay(int(0.08 * SR), 0.015) * rng.uniform(0.2, 0.6)
        x[p:p + len(b)] += b
    x *= env(len(t), 0.4, 0, 1, 1.5)
    return reverb(master(x, 1.1, 0.9), 2.5, 1500, 0.3)


def s_wind():
    sec = 10.0
    t = t_axis(sec)
    x = noise(sec)
    x = spectral(x, lambda f, s: np.exp(-0.5 * ((f - (450 + 250 * np.sin(s * 0.9) + 120 * np.sin(s * 2.3))) / 260) ** 2) * 0.9
                 + 0.15 / (1 + f / 300))
    x *= (0.55 + 0.45 * np.sin(t * 0.6 + 0.5) ** 2) * env(len(t), 1.5, 0, 1, 2.5)
    return stereo(master(x, 0, 0.6), 0.9)


def s_drone():
    sec = 9.0
    t = t_axis(sec)
    x = sum(saw(f * (1 + 0.003 * np.sin(t * (0.3 + i))), sec) for i, f in enumerate((55, 55.4, 82.4, 110.2)))
    x = spectral(x, lambda f, s: 1 / (1 + (f / (240 + 160 * min(1, s / 6))) ** 4))
    x += sine(41.2, sec) * 0.8
    x *= env(len(t), 3.0, 0, 1, 2.5)
    return reverb(master(x, 1.0, 0.8), 3.5, 1800, 0.4)


def s_step():
    sec = 0.45
    n = int(sec * SR)
    t = t_axis(sec)
    thump = sine(68 * np.exp(-t / 0.05) + 42, sec) * decay(n, 0.07)
    crunch = bandpass(noise(sec), lambda s: 2600, 1400) * decay(n, 0.05) * 0.6
    x = master(thump + crunch, 1.2, 0.9)
    return fade(reverb(x, 0.8, 4000, 0.15), 0, 0.05)


def s_heartbeat():
    sec = 0.9
    n = int(sec * SR)
    x = np.zeros(n)
    for off, amp in ((0.0, 1.0), (0.24, 0.7)):
        tt = t_axis(sec - off)
        b = sine(52 * np.exp(-tt / 0.06) + 38, sec - off) * decay(len(tt), 0.09) * amp
        x[int(off * SR):] += b
    return reverb(master(x, 1.3, 0.95), 1.2, 1200, 0.2)


def s_grip():
    sec = 0.7
    n = int(sec * SR)
    t = t_axis(sec)
    creak = bandpass(noise(sec), lambda s: 1700 + 400 * np.sin(s * 40), 500) * (0.5 + 0.5 * np.sign(np.sin(t * 2 * np.pi * 34)))
    creak *= env(n, 0.05, 0, 1, 0.2) * 0.7
    ring = (sine(1240, sec) * 0.4 + sine(2930, sec) * 0.25) * decay(n, 0.25) * 0.5
    return reverb(master(creak + ring, 0.8, 0.85), 1.0, 5000, 0.2)


def s_tinnitus():
    sec = 6.0
    t = t_axis(sec)
    x = sine(6200, sec) * 0.6 + sine(6206, sec) * 0.4
    x *= env(len(t), 1.2, 0, 1, 2.5) * 0.25
    return stereo(x, 0.3)


def s_whoosh():
    sec = 1.3
    t = t_axis(sec)
    x = spectral(noise(sec), lambda f, s: np.exp(-0.5 * ((f - (300 + 2400 * np.sin(np.pi * min(1, s / 1.1)))) / 500) ** 2))
    x *= np.sin(np.pi * np.clip(t / 1.2, 0, 1)) ** 2
    x = master(x, 0.8, 0.9)
    pan = np.clip(t / sec, 0, 1)
    return np.stack([x * (1 - pan * 0.7), x * (0.3 + pan * 0.7)], axis=1)


def s_grind():
    sec = 3.0
    n = int(sec * SR)
    t = t_axis(sec)
    peaks = (900, 1430, 2210, 3170, 4400)
    rise = 1 + 0.25 * t / sec
    x = spectral(noise(sec), lambda f, s: sum(np.exp(-0.5 * ((f - p * (1 + 0.25 * s / sec)) / 35) ** 2) for p in peaks) * 3
                 + 0.25 / (1 + f / 200))
    jitter = 0.6 + 0.4 * np.abs(np.sin(t * 2 * np.pi * 11 + np.sin(t * 7)))
    x *= jitter * env(n, 0.15, 0, 1, 0.4)
    low = lowpass(noise(sec), 160) * 2 * env(n, 0.3, 0, 1, 0.5)
    del rise
    return reverb(master(x + low, 1.1, 0.9), 1.8, 3500, 0.25)


def s_shing():
    sec = 2.6
    n = int(sec * SR)
    base = 880
    x = np.zeros(n)
    for ratio, amp, tau in ((1.0, 0.5, 1.4), (2.76, 0.35, 1.0), (5.4, 0.25, 0.7), (8.93, 0.18, 0.45), (13.3, 0.12, 0.3)):
        x += sine(base * ratio, sec) * amp * decay(n, tau)
    x += highpass(noise(sec), 5000) * decay(n, 0.04) * 0.5
    x += sine(7040 * (1 + 0.002 * np.sin(t_axis(sec) * 30)), sec) * decay(n, 0.9) * 0.08
    return reverb(master(x, 0.6, 0.85), 3.0, 7000, 0.4)


def s_ascend():
    sec = 3.6
    n = int(sec * SR)
    t = t_axis(sec)
    x = spectral(noise(sec), lambda f, s: np.exp(-0.5 * ((f - (200 + 3200 * (s / sec) ** 2)) / (300 + 600 * s / sec)) ** 2))
    x *= (t / sec) ** 1.5 * 2
    tone = sine(80 + 520 * (t / sec) ** 2, sec) * 0.35 * (t / sec)
    rumble = lowpass(noise(sec), 120) * 3 * (1 - t / sec)
    x = master(x + tone + rumble, 1.0, 0.92)
    return fade(stereo(x, 0.8), 0.2, 0.15)


def s_charge():
    sec = 3.3
    n = int(sec * SR)
    t = t_axis(sec)
    p = (t / sec) ** 1.6
    whine = saw(200 + 1000 * p + 8 * np.sin(t * 2 * np.pi * (5 + 20 * p)), sec) * (0.3 + 0.7 * p)
    whine = spectral(whine, lambda f, s: 1 / (1 + (f / (1500 + 4000 * s / sec)) ** 4))
    sh = sum(sine(f * (1 + 0.5 * p), sec) * 0.12 for f in (1760, 2217, 2637, 3520)) * (0.5 + 0.5 * np.sin(t * 2 * np.pi * (4 + 22 * p)))
    trem = 0.7 + 0.3 * np.sin(t * 2 * np.pi * (3 + 30 * p))
    sub = sine(45 + 30 * p, sec) * 0.6 * p
    x = (whine + sh) * trem + sub
    x *= np.clip((sec - t) / 0.03, 0, 1)
    return reverb(master(x, 1.0, 0.9), 2.0, 5000, 0.3)


def s_beam():
    sec = 4.6
    n = int(sec * SR)
    t = t_axis(sec)
    zap = sine(200 + 2800 * np.exp(-t / 0.06), sec) * decay(n, 0.25) * 0.8
    roar = lowpass(noise(sec), 700) * 2.5 * (0.7 + 0.3 * np.sin(t * 2 * np.pi * 9))
    stack = sum(saw(f, sec) for f in (55, 82.4, 110)) * 0.5
    stack = spectral(stack, lambda f, s: 1 / (1 + (f / 900) ** 4))
    body = (roar + stack) * env(n, 0.04, 0, 1, 1.4)
    crack = highpass(noise(sec), 3000) * decay(n, 0.05)
    x = master(zap + body + crack, 1.5, 0.95)
    return reverb(x, 3.5, 2500, 0.4)


def vowel_pad(chord, sec, formants=((800, 90), (1150, 110), (2900, 160)), attack=2.0, vib=5.2):
    t = t_axis(sec)
    x = np.zeros(len(t))
    for i, f in enumerate(chord):
        for det in (-0.004, 0.0, 0.005):
            fr = f * (1 + det) * (1 + 0.004 * np.sin(t * 2 * np.pi * vib + i))
            x += saw(fr, sec)
    x = spectral(x, lambda fq, s: sum(np.exp(-0.5 * ((fq - c) / bw) ** 2) * (1.0 if k == 0 else 0.6 if k == 1 else 0.3)
                                      for k, (c, bw) in enumerate(formants)) + 0.05)
    return x * env(len(t), attack, 0, 1, 2.0)


def s_choir():
    sec = 7.5
    # d-Moll mit None: feierlich, etwas bedrohlich
    x = vowel_pad((146.8, 174.6, 220.0, 293.7), sec, attack=2.5)
    return reverb(master(x, 0, 0.8), 4.0, 3500, 0.5)


def s_resolve():
    sec = 9.5
    t = t_axis(sec)
    n = len(t)
    pad = vowel_pad((146.8, 220.0, 293.7, 370.0, 659.3), sec, formants=((700, 120), (1220, 140), (2600, 200)), attack=1.2, vib=4.6)
    bells = np.zeros(n)
    for k, f in enumerate((587.3, 740.0, 880.0, 1174.7)):
        off = int((0.15 + 0.32 * k) * SR)
        tt = sec - off / SR
        b = (sine(f, tt) * 0.5 + sine(f * 2.01, tt) * 0.2 + sine(f * 3.98, tt) * 0.08) * decay(int(tt * SR), 1.8)
        bells[off:off + len(b)] += b[: n - off]
    x = master(pad, 0, 0.7) + bells * 0.5
    return reverb(master(x, 0, 0.85), 4.5, 6000, 0.45)


def s_stinger():
    sec = 4.0
    n = int(sec * SR)
    t = t_axis(sec)
    hit = boom(sec, 90, 40, 0.12, 1.0, 0.4, 0.6)
    brass = sum(saw(f, sec) for f in (73.4, 110.0, 146.8, 185.0, 220.0))
    brass = spectral(brass, lambda f, s: 1 / (1 + (f / (300 + 2600 * np.exp(-s / 0.35))) ** 4))
    brass *= env(n, 0.015, 0.8, 0.35, 1.5)
    x = master(hit * 0.9 + brass * 0.7, 1.1, 0.95)
    return reverb(x, 3.5, 3000, 0.4)


SOUNDS = {
    "impact": s_impact, "impact_small": s_impact_small, "rumble": s_rumble, "wind": s_wind, "drone": s_drone,
    "step": s_step, "heartbeat": s_heartbeat, "grip": s_grip, "tinnitus": s_tinnitus, "whoosh": s_whoosh,
    "grind": s_grind, "shing": s_shing, "ascend": s_ascend, "charge": s_charge, "beam": s_beam, "choir": s_choir,
    "resolve": s_resolve, "stinger": s_stinger,
}
STREAM = {"wind", "drone", "choir", "resolve", "rumble"}


def write_ogg(name, x):
    x = stereo(np.asarray(x, dtype=float))
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.97
    pcm = (x * 32767).astype("<i2")
    os.makedirs(SND, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        tmp = f.name
    with wave.open(tmp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    out = os.path.join(SND, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "3", out], check=True)
    os.unlink(tmp)
    return out


def main():
    only = set(sys.argv[2:])
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        p = write_ogg(name, fn())
        print(name, os.path.getsize(p) // 1024, "KB")
    sj = os.path.join(OUT, "assets/kuby/sounds.json")
    data = {}
    if os.path.exists(sj):
        with open(sj, encoding="utf-8") as f:
            data = json.load(f)
    for name in SOUNDS:
        data["cine." + name] = {"sounds": [{"name": "kuby:cine/" + name, "stream": name in STREAM}]}
    with open(sj, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)


if __name__ == "__main__":
    main()
