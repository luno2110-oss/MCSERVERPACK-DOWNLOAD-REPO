#!/usr/bin/env python3
"""Roulette-Töne (v1.98), synthetisch (numpy) und zeitgleich mit der Kessel-Animation in RouletteTable.java:

* kuby:casino.roulette_spin  (8,4 s, mono, also mit Richtung im Raum)
    0,00 s  Croupier stößt den Rotor an (Holzklopfen, leises Wischen)
    0,50 s  Kugel wird eingeworfen (heller Klick)        = RouletteTable.T_LAUNCH (10 Ticks)
    0,5–6,0 s  Kugel rollt in der Bahn: Zischen + Grollen, wird langsamer, pulsiert mit jeder Runde
    6,0 s   Kugel verlässt die Bahn                      = T_DROP (120 Ticks)
    6,25 / 6,8 s  trifft zwei Rauten (metallisch)        = Aufschläge in der Animation
    7,15 / 7,35 / 7,5 s  springt über die Stege, fällt ins Fach  = T_LAND (150 Ticks)
    danach kurzes Nachklappern
* kuby:casino.chip  (drei Varianten): Tonchip auf Tonchip, kurz und hell

Mono, damit Minecraft den Ton am Kessel ortet. Braucht ffmpeg mit libvorbis.
Aufruf: python3 tools/build_roulette_sfx.py [pack-ordner]
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
rng = np.random.default_rng(1998)

LAUNCH, DROP, LAND = 0.5, 6.0, 7.5
DIAMONDS = (6.25, 6.8)
FRETS = ((7.15, 0.5), (7.35, 0.42), (7.5, 0.46), (7.58, 0.16), (7.66, 0.1), (7.71, 0.05))


def band(x, lo, hi):
    """Bandpass über FFT (für Rauschen genügt das)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.clip((f - lo * 0.7) / (lo * 0.3 + 1e-9), 0, 1) * np.clip((hi * 1.3 - f) / (hi * 0.3), 0, 1)
    return np.fft.irfft(spec * g, len(x))


def ring(n, partials, tau):
    """Gedämpfte Teiltöne: [(Frequenz, Lautstärke)], tau in s."""
    t = np.arange(n) / SR
    out = np.zeros(n)
    for k, (f, a) in enumerate(partials):
        out += a * np.sin(2 * np.pi * f * t + k) * np.exp(-t / (tau * (1 - 0.15 * k)))
    return out


def add(buf, at, x, gain=1.0):
    i = int(at * SR)
    j = min(len(buf), i + len(x))
    if j > i:
        buf[i:j] += gain * x[:j - i]


def click(bright=3200.0, body=650.0, n=0.05):
    m = int(n * SR)
    burst = rng.standard_normal(m) * np.exp(-np.arange(m) / SR / 0.0018)
    burst = band(burst, 1500, 9000)
    return burst * 0.6 + ring(m, [(bright, 0.5), (bright * 1.63, 0.3), (body, 0.35)], 0.014)


def spin():
    dur = 8.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    # Rotor anstoßen: Holzklopfen und ein Wischen
    knock = ring(int(0.25 * SR), [(140, 0.8), (310, 0.3)], 0.05) + band(rng.standard_normal(int(0.25 * SR)), 80, 600) * \
        np.exp(-np.arange(int(0.25 * SR)) / SR / 0.03) * 0.4
    add(out, 0.04, knock, 0.55)
    sw = int(0.7 * SR)
    swish = band(rng.standard_normal(sw), 300, 1600) * np.sin(np.linspace(0, np.pi, sw)) ** 2
    add(out, 0.0, swish, 0.12)
    # Einwurf
    add(out, LAUNCH, click(3600, 900), 0.7)
    # Rollen: Geschwindigkeit fällt linear (wie die Animation), Puls je Umdrehung, Bahn-Unebenheiten als feine Klicks
    s = np.clip(t - LAUNCH, 0, None)
    speed = np.clip(1 - s / (LAND - LAUNCH), 0, 1)
    active = (t >= LAUNCH) & (t < DROP + 0.3)
    fade = np.clip((DROP + 0.3 - t) / 0.3, 0, 1) * np.clip((t - LAUNCH) / 0.08, 0, 1)
    rev = np.cumsum(1.33 * speed) / SR
    pulse = 0.68 + 0.32 * np.cos(2 * np.pi * rev)
    hiss = np.zeros(n)
    seg = int(0.5 * SR)
    for a in range(0, n, seg):     # Zischen wandert mit der Geschwindigkeit nach unten
        b = min(n, a + seg + 2048)
        f = speed[min(a + seg // 2, n - 1)]
        lo, hi = 1800 + 2200 * f, 5200 + 3500 * f
        piece = band(rng.standard_normal(b - a), lo, hi)
        w = np.ones(b - a)
        w[:1024] = np.linspace(0, 1, 1024)
        w[-1024:] = np.linspace(1, 0, 1024)
        hiss[a:b] += piece * w
    rumble = band(rng.standard_normal(n), 60, 420)
    roll = (0.3 * hiss * speed ** 0.6 + 0.6 * rumble * speed ** 0.8) * pulse * fade * active
    out += roll
    k = 0.0
    while k < DROP:   # feine Unebenheiten: häufiger, je schneller
        sp = max(0.05, 1 - (k - LAUNCH) / (LAND - LAUNCH)) if k > LAUNCH else 0
        if sp > 0:
            add(out, k, click(4200 + 900 * rng.random(), 1200, 0.02), 0.05 * sp)
        k += 0.03 + 0.25 * (1 - sp) * rng.random() + 0.04 * rng.random()
    # Rauten (Metall) und Stege (Holz/Kunststoff)
    for d, g in zip(DIAMONDS, (0.75, 0.55)):
        m = int(0.18 * SR)
        add(out, d, ring(m, [(2650, 0.6), (4120, 0.45), (6310, 0.3)], 0.045) + click(5200, 1500, 0.18) * 0.4, g)
    for at, g in FRETS:
        add(out, at, click(1900 + 600 * rng.random(), 620, 0.06), g)
    # Rotor-Lager: ganz leises Brummen, klingt aus
    hum = (np.sin(2 * np.pi * 92 * t) * 0.5 + band(rng.standard_normal(n), 70, 260)) * 0.05 * np.exp(-t / 6)
    out += hum
    # kleiner Raum (kurzer Nachhall)
    ir_n = int(0.22 * SR)
    ir = rng.standard_normal(ir_n) * np.exp(-np.arange(ir_n) / SR / 0.05) * 0.12
    ir[0] = 1.0
    out = np.convolve(out, ir)[:n]
    tail = np.clip((dur - t) / 0.4, 0, 1)
    return out * tail


def chip(seed):
    r = np.random.default_rng(seed)
    n = int(0.12 * SR)
    p = 1 + (r.random() - 0.5) * 0.18
    a = click(3500 * p, 1100 * p, 0.12)
    b = np.zeros(n)
    add(b, 0.022 + 0.01 * r.random(), click(4300 * p, 1300 * p, 0.06), 0.35)
    return a + b


def write_ogg(x, path):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.7
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "a.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", path], check=True)


def main(pack):
    snd = os.path.join(pack, "assets/kuby/sounds/casino")
    os.makedirs(snd, exist_ok=True)
    write_ogg(spin(), os.path.join(snd, "roulette_spin.ogg"))
    for i in range(3):
        write_ogg(chip(70 + i), os.path.join(snd, "chip%d.ogg" % (i + 1)))
    sj = os.path.join(pack, "assets/kuby/sounds.json")
    data = json.load(open(sj, encoding="utf-8")) if os.path.exists(sj) else {}
    data["casino.roulette_spin"] = {"sounds": [{"name": "kuby:casino/roulette_spin"}]}
    data["casino.chip"] = {"sounds": [{"name": "kuby:casino/chip%d" % (i + 1)} for i in range(3)]}
    with open(sj, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI"))
