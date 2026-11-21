#!/usr/bin/env python3
"""Roulette-Töne (v2.04) aus einer echten Aufnahme, passend zur Kugel-Physik in RouletteTable.java.

Quelle (Wunsch des Besitzers: „passendere Töne, notfalls aus dem Internet“):
    „Roulette - SoundCloud - Beeld en Geluid.ogg“, Beeld en Geluid (Niederländisches Institut für Bild und Ton),
    https://commons.wikimedia.org/wiki/File:Roulette_-_SoundCloud_-_Beeld_en_Geluid.ogg
    Lizenz CC BY-SA 3.0 (https://creativecommons.org/licenses/by-sa/3.0/). 11 Minuten, 82 echte Würfe:
    Einwurf, Rollen in der Bahn (bis 4,5 s), Klappern über Rauten und Fächer (2–4 s), Liegenbleiben.
    Die Ausschnitte im Pack stehen ebenfalls unter CC BY-SA 3.0 (sounds/casino/roulette/LIZENZ.txt).

Bearbeitung: Brummen entfernt (Hochpass 140 Hz, Kerbfilter 248/371/495/619/743 Hz), Würfe automatisch gefunden
(Pegel über 0,006, Pausen über 0,7 s), Rollende = Grundpegel fällt dauerhaft unter 30 % (ab da nur noch Klicks),
Klicks = Spitzen der 5-ms-Hüllkurve über dem 2,5-fachen Median der letzten 150 ms.

Ereignisse im Pack (alle mono, damit Minecraft sie am Kessel ortet):
* kuby:casino.roulette_roll     8 echte Rollgeräusche ab dem Einwurf, je 4,20 s (Ende = Kugel trifft den Rotor).
                                Das Spiel spielt sie mit Tonhöhe 4,20 s / echte Rolldauer (0,8–1,1), endet also
                                genau beim ersten Aufprall.
* kuby:casino.roulette_diamond  harte erste Aufschläge (Kugel trifft eine Raute)
* kuby:casino.roulette_hit_big  großer Sprung landet: Aufschlag mit Nachklappern (0,32 s)
* kuby:casino.roulette_hit      mittlerer Sprung (0,16 s)
* kuby:casino.roulette_tick     kleines Nachwackeln (0,07 s)
* kuby:casino.roulette_settle   die letzten Klicks, bevor die Kugel im Fach liegt (0,9 s)
* kuby:casino.chip              (nur mit --chips neu): Tonchip auf Tonchip, synthetisch

Braucht ffmpeg mit libvorbis, numpy, scipy.
Aufruf: python3 tools/build_roulette_sfx.py [pack-ordner] [--src aufnahme.mp3|ogg] [--chips]
Ohne --src wird die Aufnahme einmal von Wikimedia geladen (umgewandelte MP3, 22 MB) und im Arbeitsordner gecacht.
"""
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
import wave

import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
rng = np.random.default_rng(1998)

SRC_PAGE = "https://commons.wikimedia.org/wiki/File:Roulette_-_SoundCloud_-_Beeld_en_Geluid.ogg"
SRC_URL = ("https://upload.wikimedia.org/wikipedia/commons/transcoded/e/e5/Roulette_-_SoundCloud_-_Beeld_en_Geluid.ogg/"
           "Roulette_-_SoundCloud_-_Beeld_en_Geluid.ogg.mp3")
ROLL_LEN = 4.20          # muss zu RouletteTable.ROLL_SECONDS passen
COUNTS = {"diamond": 5, "hit_big": 8, "hit": 10, "tick": 10, "settle": 6}

LICENSE = """Roulette-Töne (roll, diamond, hit_big, hit, tick, settle)

Ausschnitte aus „Roulette - SoundCloud - Beeld en Geluid.ogg“ von Beeld en Geluid
(Niederländisches Institut für Bild und Ton), Wikimedia Commons:
https://commons.wikimedia.org/wiki/File:Roulette_-_SoundCloud_-_Beeld_en_Geluid.ogg
Lizenz: Creative Commons Namensnennung – Weitergabe unter gleichen Bedingungen 3.0 (CC BY-SA 3.0)
https://creativecommons.org/licenses/by-sa/3.0/deed.de

Änderungen: Netzbrummen herausgefiltert, in einzelne Ereignisse geschnitten (Rollen, Aufschläge, Liegenbleiben),
mono, Pegel angeglichen, Rollgeräusche auf 4,20 s gekürzt. Diese bearbeiteten Ausschnitte stehen ebenfalls
unter CC BY-SA 3.0. Werkzeug: tools/build_roulette_sfx.py

Sounds cut from "Roulette - SoundCloud - Beeld en Geluid.ogg" by Beeld en Geluid, licensed CC BY-SA 3.0;
the excerpts are likewise licensed CC BY-SA 3.0.
"""


# ---------------------------------------------------------------------------------------------------------------------
# Laden und säubern
# ---------------------------------------------------------------------------------------------------------------------

def load(src):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "a.wav")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", src, "-ac", "1", "-ar", str(SR), wav], check=True)
        with wave.open(wav) as w:
            x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float64) / 32768
    from scipy import signal
    y = signal.sosfiltfilt(signal.butter(4, 140, "hp", fs=SR, output="sos"), x)
    for f0 in (247.6, 371.4, 495.2, 619.1, 743.0):
        b, a = signal.iirnotch(f0, 30, fs=SR)
        y = signal.filtfilt(b, a, y)
    return y


def fetch(cache_dir):
    path = os.path.join(cache_dir, "roulette_beeld_en_geluid.mp3")
    if not os.path.exists(path):
        req = urllib.request.Request(SRC_URL, headers={"User-Agent": "KubyPlugins-SoundBuild/1.0 (resource pack build tool)"})
        with urllib.request.urlopen(req, timeout=300) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


# ---------------------------------------------------------------------------------------------------------------------
# Würfe, Rollende, Klicks
# ---------------------------------------------------------------------------------------------------------------------

def segment(y):
    hop = int(0.005 * SR)
    k = int(0.05 * SR)
    rms = np.sqrt(np.convolve(y ** 2, np.ones(k) / k, "same")[::hop])
    # 5-ms-Hüllkurve: Energie mit Nachlauf (Pol 0,9), Skala etwa 3,16 x RMS
    from scipy import signal
    env = np.sqrt(signal.lfilter([1], [1, -0.9], y ** 2)[::hop] + 1e-12)
    act = rms > 0.006
    spins, i, n = [], 0, len(act)
    while i < n:
        if act[i]:
            j, last = i, i
            while j < n and (j - last) * 0.005 < 0.7:
                if act[j]:
                    last = j
                j += 1
            if (last - i) * 0.005 > 1.2:
                spins.append((i * 0.005, last * 0.005))
            i = j
        else:
            i += 1
    out = []
    for a, b in spins:
        ia, ib = int(a / 0.005), int(b / 0.005)
        e = env[ia:ib]
        # Rollende: Grundpegel (20-%-Quantil der 10-ms-RMS in 200 ms) fällt dauerhaft unter 30 % seines Maximums
        seg = y[int(a * SR):int(b * SR)]
        h10 = int(0.01 * SR)
        fr = np.array([np.sqrt(np.mean(seg[q:q + h10] ** 2)) for q in range(0, len(seg) - h10, h10)])
        fl = np.array([np.percentile(fr[max(0, q - 10):q + 10], 20) for q in range(len(fr))])
        pk = fl.max()
        imax = int(np.argmax(fl[:max(1, len(fl) // 2)]))
        roll_end = None
        for q in range(imax, len(fl) - 20):
            if fl[q] < 0.3 * pk and np.all(fl[q:q + 20] < 0.35 * pk):
                roll_end = q * 0.01
                break
        clicks = []
        for t in range(30, len(e) - 2):
            base = np.median(e[max(0, t - 30):t])
            if e[t] > 2.5 * base and e[t] >= e[t - 2:t + 3].max() and e[t] > 0.02 and (not clicks or (t * 0.005 + a) - clicks[-1][0] > 0.03):
                clicks.append((a + t * 0.005, float(e[t]), float(base)))
        if roll_end is not None:
            clicks = [c for c in clicks if c[0] - a >= roll_end - 0.05]
        out.append({"start": a, "end": b, "roll_end": roll_end, "roll_peak": float(pk), "clicks": clicks})
    return out


def onset(y, t):
    """Genauer Einsatz eines Klicks: erste Probe über 30 % des örtlichen Maximums vor der Hüllkurven-Spitze."""
    a, b = int((t - 0.02) * SR), int((t + 0.008) * SR)
    seg = np.abs(y[a:b])
    m = seg.max()
    idx = int(np.argmax(seg > 0.3 * m))
    return (a + idx) / SR


def cut(y, t0, dur, fade_in=0.002, fade_out=0.4):
    a = int(t0 * SR)
    x = y[a:a + int(dur * SR)].copy()
    fi = max(1, int(fade_in * SR))
    x[:fi] *= np.linspace(0, 1, fi)
    fo = max(1, int(len(x) * fade_out))
    x[-fo:] *= np.cos(np.linspace(0, np.pi / 2, fo)) ** 2
    return x


def pick(cands, n):
    """Die besten n (Signal zu Grund), höchstens zwei aus demselben Wurf, damit es abwechslungsreich klingt."""
    cands = sorted(cands, key=lambda c: -c[0])
    used, out = {}, []
    for score, spin, x in cands:
        if used.get(spin, 0) >= 2:
            continue
        used[spin] = used.get(spin, 0) + 1
        out.append(x)
        if len(out) == n:
            break
    return out


def click_quality(y, o, x):
    """Güte eines Klick-Ausschnitts oder None: Spitze in den ersten 15 ms, impulsiv (Scheitelfaktor der ersten 60 ms
    mindestens 5, Rollrauschen liegt bei 3–4) und davor 10 ms Ruhe. Sonst ist es ein Rauschfetzen aus der Rollphase."""
    m = np.max(np.abs(x))
    head = np.abs(x[:int(0.015 * SR)])
    if head.max() < 0.8 * m:
        return None
    first = x[:int(0.06 * SR)]
    crest = m / (np.sqrt(np.mean(first ** 2)) + 1e-9)
    pre = y[int((o - 0.012) * SR):int((o - 0.002) * SR)]
    quiet = np.sqrt(np.mean(pre ** 2)) / (m + 1e-9)
    if crest < 5 or quiet > 0.12:
        return None
    return crest * (0.12 - quiet)


def harvest(y, spins):
    got = {k: [] for k in COUNTS}
    rolls = []
    for si, s in enumerate(spins):
        if s["roll_end"] is None:
            continue            # Rollende nicht erkannt: Klicks und Rollen wären nicht sauber getrennt
        cl = s["clicks"]
        if s["roll_end"] is not None and s["roll_end"] >= ROLL_LEN + 0.02:
            t0 = s["start"] - 0.04
            x = cut(y, t0, ROLL_LEN, fade_in=0.004, fade_out=0.03)
            rolls.append((s["roll_peak"], si, x))
        for i, (t, amp, base) in enumerate(cl):
            pre = t - cl[i - 1][0] if i > 0 else 1.0
            post = cl[i + 1][0] - t if i + 1 < len(cl) else 1.0
            o = onset(y, t) - 0.002

            def take(group, dur, fade):
                x = cut(y, o, dur, fade_out=fade)
                q = click_quality(y, o, x)
                if q is not None:
                    got[group].append((q, si, x))
            if i == 0 and amp >= 0.35:
                take("diamond", 0.22, 0.6)
            if amp >= 0.5 and pre >= 0.1:
                take("hit_big", 0.32, 0.5)
            elif 0.25 <= amp < 0.5 and pre >= 0.08:
                take("hit", 0.16, 0.5)
            elif 0.08 <= amp < 0.22 and pre >= 0.06 and post >= 0.07:
                take("tick", 0.07, 0.6)
        if len(cl) >= 6:
            last = cl[-1][0]
            # Beginn: ein Klick 0,5–0,8 s vor dem letzten, mit Pause davor (sauberer Einsatz)
            starts = [c for j, c in enumerate(cl) if 0.5 <= last - c[0] <= 0.8 and (j == 0 or c[0] - cl[j - 1][0] >= 0.06)]
            if starts:
                t = starts[0][0]
                o = onset(y, t) - 0.002
                x = cut(y, o, (last - o) + 0.3, fade_out=0.3)
                q = click_quality(y, o, x[:int(0.2 * SR)])
                tail = np.abs(y[int((last + 0.35) * SR):int((last + 0.45) * SR)]).max()
                # danach still (Kugel liegt), und der Ausschnitt besteht aus Klicks, nicht aus Rauschen
                if q is not None and tail < 0.05 * np.max(np.abs(x)) * 4:
                    got["settle"].append((q, si, x))
    sel = {k: pick(v, COUNTS[k]) for k, v in got.items()}
    sel["roll"] = [x for _, _, x in sorted(rolls, key=lambda r: -r[0])]
    return sel


# ---------------------------------------------------------------------------------------------------------------------
# Chips (synthetisch, unverändert seit v1.98)
# ---------------------------------------------------------------------------------------------------------------------

def band(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.clip((f - lo * 0.7) / (lo * 0.3 + 1e-9), 0, 1) * np.clip((hi * 1.3 - f) / (hi * 0.3), 0, 1)
    return np.fft.irfft(spec * g, len(x))


def ring(n, partials, tau):
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


def chip(seed):
    r = np.random.default_rng(seed)
    n = int(0.12 * SR)
    p = 1 + (r.random() - 0.5) * 0.18
    a = click(3500 * p, 1100 * p, 0.12)
    b = np.zeros(n)
    add(b, 0.022 + 0.01 * r.random(), click(4300 * p, 1300 * p, 0.06), 0.35)
    return a + b


# ---------------------------------------------------------------------------------------------------------------------
# Schreiben
# ---------------------------------------------------------------------------------------------------------------------

def write_ogg(x, path, peak=0.7):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "a.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", path], check=True)


def write_rms(x, path, rms_db):
    """Rollgeräusche auf gleiche Lautheit (RMS), damit kein Wurf lauter rollt als ein anderer."""
    r = np.sqrt(np.mean(x ** 2)) + 1e-9
    g = 10 ** (rms_db / 20) / r
    y = x * g
    if np.max(np.abs(y)) > 0.95:
        y *= 0.95 / np.max(np.abs(y))
    write_ogg(y, path, peak=np.max(np.abs(y)))


# Spitzenpegel je Gruppe (das Spiel regelt die Lautstärke zusätzlich nach Sprunghöhe)
PEAK = {"diamond": 0.9, "hit_big": 0.85, "hit": 0.6, "tick": 0.32, "settle": 0.6}


def main(argv):
    pack = os.path.join(os.path.dirname(HERE), "resourcepack/KubyUI")
    src, chips = None, False
    args = list(argv)
    while args:
        a = args.pop(0)
        if a == "--src":
            src = args.pop(0)
        elif a == "--chips":
            chips = True
        else:
            pack = a
    if src is None:
        src = fetch(tempfile.gettempdir())
    y = load(src)
    spins = segment(y)
    sel = harvest(y, spins)
    snd = os.path.join(pack, "assets/kuby/sounds/casino/roulette")
    os.makedirs(snd, exist_ok=True)
    for f in os.listdir(snd):
        if f.endswith(".ogg"):
            os.remove(os.path.join(snd, f))
    sj = os.path.join(pack, "assets/kuby/sounds.json")
    data = json.load(open(sj, encoding="utf-8")) if os.path.exists(sj) else {}
    data.pop("casino.roulette_spin", None)
    old = os.path.join(pack, "assets/kuby/sounds/casino/roulette_spin.ogg")
    if os.path.exists(old):
        os.remove(old)
    report = []
    for name, xs in sel.items():
        names = []
        for i, x in enumerate(xs):
            fn = "%s%d.ogg" % (name, i + 1)
            if name == "roll":
                write_rms(x, os.path.join(snd, fn), -21.0)
            else:
                write_ogg(x, os.path.join(snd, fn), PEAK[name])
            names.append({"name": "kuby:casino/roulette/%s%d" % (name, i + 1)})
        data["casino.roulette_" + name] = {"sounds": names}
        report.append("%s %d" % (name, len(xs)))
    with open(os.path.join(snd, "LIZENZ.txt"), "w", encoding="utf-8") as fh:
        fh.write(LICENSE)
    if chips:
        cdir = os.path.join(pack, "assets/kuby/sounds/casino")
        for i in range(3):
            write_ogg(chip(70 + i), os.path.join(cdir, "chip%d.ogg" % (i + 1)))
        data["casino.chip"] = {"sounds": [{"name": "kuby:casino/chip%d" % (i + 1)} for i in range(3)]}
    with open(sj, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1, ensure_ascii=False)
    print("Würfe in der Aufnahme: %d; im Pack: %s" % (len(spins), ", ".join(report)))


if __name__ == "__main__":
    main(sys.argv[1:])
