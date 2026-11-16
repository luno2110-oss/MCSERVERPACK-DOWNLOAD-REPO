#!/usr/bin/env python3
"""Zwei luxuriöse Piano-Jazz-Stücke für die Casino-Jukebox (Flügel, Kontrabass, Besen-Schlagzeug, leise Streicher).

Komponiert als MIDI (Akkordfolgen mit Stimmführung, Rootless-Voicings, Walking Bass, Swing-Achtel, Melodie aus
Motiven) und mit fluidsynth und FluidR3_GM gerendert. Ausgabe:
  resourcepack/KubyUI/assets/kuby/sounds/music/<id>.ogg  und  "music.<id>" in assets/kuby/sounds.json
  KubyCore .../resources/casino/songs/<id>.audio  (Name|Länge in Ticks|Sound), damit die Jukebox sie kennt

Aufruf: python3 tools/build_casino_jazz.py   (braucht fluidsynth, ffmpeg, mido, /usr/share/sounds/sf2/FluidR3_GM.sf2)
"""
import json
import os
import random
import subprocess
import tempfile

import mido

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PACK = os.path.join(ROOT, "resourcepack", "KubyUI", "assets", "kuby")
SONGS = os.path.join(ROOT, "KubyCore-v2.0.14", "KubyPlugins", "src", "main", "resources", "casino", "songs")
SF2 = "/usr/share/sounds/sf2/FluidR3_GM.sf2"
TPB = 480                                    # Ticks je Viertel

NOTE = {"C": 0, "Db": 1, "D": 2, "Eb": 3, "E": 4, "F": 5, "Gb": 6, "G": 7, "Ab": 8, "A": 9, "Bb": 10, "B": 11}
QUAL = {
    "maj9": [0, 4, 7, 11, 14], "maj7": [0, 4, 7, 11], "6/9": [0, 4, 7, 9, 14],
    "m9": [0, 3, 7, 10, 14], "m7": [0, 3, 7, 10], "m11": [0, 3, 7, 10, 14, 17],
    "13": [0, 4, 10, 14, 21], "9": [0, 4, 7, 10, 14], "7b9": [0, 4, 7, 10, 13], "13#11": [0, 4, 10, 14, 18, 21],
    "7sus": [0, 5, 7, 10, 14], "m7b5": [0, 3, 6, 10], "7#9": [0, 4, 7, 10, 15],
}
SCALE = {  # Akkord-Skala für die Melodie (Halbtöne über dem Grundton)
    "maj9": [0, 2, 4, 7, 9, 11], "maj7": [0, 2, 4, 7, 9, 11], "6/9": [0, 2, 4, 7, 9],
    "m9": [0, 2, 3, 5, 7, 9, 10], "m7": [0, 2, 3, 5, 7, 9, 10], "m11": [0, 2, 3, 5, 7, 10],
    "13": [0, 2, 4, 7, 9, 10], "9": [0, 2, 4, 7, 9, 10], "7b9": [0, 1, 4, 7, 8, 10], "13#11": [0, 2, 4, 6, 7, 9, 10],
    "7sus": [0, 2, 5, 7, 9, 10], "m7b5": [0, 1, 3, 5, 6, 8, 10], "7#9": [0, 3, 4, 7, 8, 10],
}


def chord(sym):
    for n in sorted(NOTE, key=len, reverse=True):
        if sym.startswith(n):
            return NOTE[n], sym[len(n):]
    raise ValueError(sym)


class Song:
    def __init__(self, bpm, swing):
        self.bpm, self.swing = bpm, swing
        self.ev = {k: [] for k in ("piano", "bass", "drums", "pad")}

    def t(self, beat):
        """Beat (Viertel, auch Achtel 0.5) in MIDI-Ticks, Achtel geswingt."""
        whole = int(beat)
        frac = beat - whole
        if abs(frac - 0.5) < 1e-6:
            frac = self.swing
        return int(round((whole + frac) * TPB))

    def note(self, part, beat, dur, pitch, vel, rnd):
        start = max(0, self.t(beat) + rnd.randint(-12, 12))
        end = max(start + 20, self.t(beat + dur) - 10)
        self.ev[part].append((start, end, pitch, max(1, min(127, vel + rnd.randint(-6, 6)))))


def voicing(root, q, prev, lo=50, hi=70):
    """Rootless-Voicing (ohne Grundton, ohne Quinte wo möglich) möglichst nah am vorigen Griff."""
    iv = [i for i in QUAL[q] if i % 12 not in (0, 7)] or QUAL[q][1:]
    iv = iv[:4]
    best, score = None, 1e9
    for base in range(lo - 12, hi):
        if (base - root) % 12:
            continue
        notes = sorted(base + i for i in iv)
        while notes[0] < lo:
            notes = sorted(notes[1:] + [notes[0] + 12])
        if notes[-1] > hi + 4:
            continue
        s = sum(abs(a - b) for a, b in zip(notes, prev)) if prev else abs(sum(notes) / len(notes) - 60)
        if s < score:
            best, score = notes, s
    return best


def melody_phrase(song, rnd, bars, start_beat, chords_at, center, density):
    """Eine Phrase: Motiv aus Rhythmus + Kontur, endet auf einem Akkordton (Terz/None)."""
    rhythms = [[0, 1, 1.5, 2.5, 3], [0.5, 1, 2, 2.5], [0, 0.5, 1, 1.5, 2, 3], [1, 1.5, 2, 3.5], [0, 1.5, 2, 2.5, 3.5]]
    pitch = center
    for b in range(bars):
        bar_beat = start_beat + 4 * b
        pat = rnd.choice(rhythms) if rnd.random() < density else [0, 2]
        for k, off in enumerate(pat):
            beat = bar_beat + off
            root, q = chords_at(beat)
            scale = sorted({(root + s) % 12 for s in SCALE[q]})
            tones = {(root + i) % 12 for i in QUAL[q]}
            step = rnd.choice([-2, -1, -1, 1, 1, 2, 3, -3])
            target = pitch + step
            # in Lage halten, Richtung Mitte ziehen
            if target > center + 9:
                target -= 5
            if target < center - 7:
                target += 5
            cand = [p for p in range(target - 2, target + 3) if p % 12 in scale]
            if k == len(pat) - 1 or off in (0, 2):
                strong = [p for p in cand if p % 12 in tones]
                cand = strong or cand
            pitch = min(cand, key=lambda p: abs(p - target)) if cand else target
            dur = (pat[k + 1] - off) if k + 1 < len(pat) else (4 - off)
            dur = min(dur, 1.5)
            vel = 74 if off in (0, 2) else 64
            song.note("piano", beat, dur * 0.95, pitch, vel, rnd)
            # Oktav-Verdopplung für den „Lounge“-Glanz, nur auf betonten Tönen
            if off in (0, 2) and rnd.random() < 0.35:
                song.note("piano", beat, dur * 0.95, pitch - 12, vel - 18, rnd)
        # gelegentlich ein abwärts laufendes Arpeggio als Füllung am Phrasenende
    return pitch


def build(form, bpm, swing, seed, ballad):
    rnd = random.Random(seed)
    s = Song(bpm, swing)
    bars = []   # (Akkorde je Takt: Liste von (Beat-Offset, Symbol))
    for line in form:
        for bar in line.split("|"):
            syms = bar.split()
            if len(syms) == 1:
                bars.append([(0, syms[0])])
            else:
                bars.append([(0, syms[0]), (2, syms[1])])

    def chords_at(beat):
        b = int(beat // 4) % len(bars)
        cur = bars[b][0][1]
        for off, sym in bars[b]:
            if beat - 4 * int(beat // 4) >= off:
                cur = sym
        return chord(cur)

    prev = None
    total_beats = 4 * len(bars)
    # ---- Klavier links: Voicings (Ballade: lang und arpeggiert, Swing: Charleston-Comping) ----
    for i, bar in enumerate(bars):
        for j, (off, sym) in enumerate(bar):
            root, q = chord(sym)
            v = voicing(root, q, prev)
            prev = v
            beat = 4 * i + off
            length = (bar[j + 1][0] - off) if j + 1 < len(bar) else 4 - off
            if ballad:
                for k, p in enumerate(v):
                    s.note("piano", beat + 0.12 * k, length - 0.12 * k, p, 52 + 4 * k, rnd)
                s.note("pad", beat, length, v[0] + 12, 34, rnd)
                s.note("pad", beat, length, v[-1] + 12, 30, rnd)
            else:
                hits = [(0, 1.0), (1.5, 0.45)] if length >= 4 else [(0, 0.9)]
                if length >= 4 and rnd.random() < 0.35:
                    hits = [(0.5, 0.4), (2, 0.9), (3.5, 0.4)]
                for h, d in hits:
                    for p in v:
                        s.note("piano", beat + h, d, p, 56, rnd)
    # ---- Bass ----
    for i, bar in enumerate(bars):
        for j, (off, sym) in enumerate(bar):
            root, q = chord(sym)
            length = (bar[j + 1][0] - off) if j + 1 < len(bar) else 4 - off
            nxt_root = chord(bars[(i + 1) % len(bars)][0][1])[0] if j == len(bar) - 1 else chord(bar[j + 1][1])[0]
            base = 33 + (root - 9) % 12       # E1..D#2
            if ballad:
                s.note("bass", 4 * i + off, min(2, length) * 0.95, base, 82, rnd)
                if length >= 4:
                    fifth = base + 7 if base + 7 <= 50 else base - 5
                    s.note("bass", 4 * i + off + 2, 1.9, fifth, 72, rnd)
            else:
                steps = int(length)
                tones = [base, base + QUAL[q][1] % 12, base + 7, None]
                for k in range(steps):
                    if k == 0:
                        p = base
                    elif k == steps - 1:
                        target = 33 + (nxt_root - 9) % 12
                        p = target + rnd.choice([-1, 1])        # chromatische Annäherung
                    else:
                        p = rnd.choice([t for t in tones if t is not None]) + rnd.choice([0, 0, 12])
                        if p > 52:
                            p -= 12
                    s.note("bass", 4 * i + off + k, 0.9, p, 86 if k == 0 else 76, rnd)
    # ---- Melodie: Phrasen über 4 Takte, mit Atempausen ----
    beat = 0
    center = 74 if ballad else 72
    while beat < total_beats - 4:
        n = min(4, (total_beats - beat) // 4)
        if rnd.random() < 0.85:
            melody_phrase(s, rnd, max(1, n - 1), beat, chords_at, center, 0.5 if ballad else 0.75)
        beat += 4 * n
    # ---- Schlagzeug (Besen-Kit): Ride, Hi-Hat mit dem Fuß auf 2 und 4, Besen-Wischen ----
    for i in range(len(bars)):
        for k in range(4):
            b = 4 * i + k
            if ballad:
                s.note("drums", b, 0.9, 59, 36 if k % 2 else 44, rnd)       # Ride-Glocke leise
                if k % 2:
                    s.note("drums", b, 0.5, 44, 40, rnd)
                s.note("drums", b, 0.9, 38, 22, rnd)                        # Besen auf der Snare
            else:
                s.note("drums", b, 0.4, 51, 58 if k % 2 else 50, rnd)
                if k % 2:
                    s.note("drums", b, 0.4, 44, 52, rnd)
                    s.note("drums", b + 0.5, 0.3, 51, 42, rnd)
                s.note("drums", b, 0.4, 38, 26, rnd)
        if i % 8 == 7 and not ballad:
            s.note("drums", 4 * i + 3.5, 0.3, 38, 60, rnd)                  # kleiner Fill vor dem Abschnitt
    # ---- Schluss: Fermate auf dem Tonika-Akkord ----
    root, q = chord(form[0].split("|")[0].split()[0])
    end = total_beats
    for k, p in enumerate(voicing(root, q, prev, 55, 76) + [root + 36 + 12]):
        s.note("piano", end + 0.15 * k, 6, p, 60, rnd)
    s.note("bass", end, 6, 33 + (root - 9) % 12, 80, rnd)
    s.note("pad", end, 6, voicing(root, q, None)[0] + 12, 30, rnd)
    return s, end + 7


def write_midi(song, path):
    mid = mido.MidiFile(ticks_per_beat=TPB)
    meta = mido.MidiTrack()
    meta.append(mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(song.bpm), time=0))
    mid.tracks.append(meta)
    setup = {"piano": (0, 0, 100), "bass": (1, 32, 92), "drums": (9, 40, 70), "pad": (2, 49, 46)}
    for part, (ch, prog, vol) in setup.items():
        tr = mido.MidiTrack()
        tr.append(mido.Message("program_change", channel=ch, program=prog, time=0))
        tr.append(mido.Message("control_change", channel=ch, control=7, value=vol, time=0))
        tr.append(mido.Message("control_change", channel=ch, control=91, value=70 if part != "bass" else 40, time=0))
        if part == "piano":
            tr.append(mido.Message("control_change", channel=ch, control=10, value=58, time=0))
        msgs = []
        for st, en, p, v in song.ev[part]:
            msgs.append((st, 1, mido.Message("note_on", channel=ch, note=p, velocity=v)))
            msgs.append((en, 0, mido.Message("note_off", channel=ch, note=p, velocity=0)))
        msgs.sort(key=lambda m: (m[0], m[1]))
        last = 0
        for t, _, m in msgs:
            m.time = t - last
            last = t
            tr.append(m)
        mid.tracks.append(tr)
    mid.save(path)


PIECES = {
    # Ballade in Des-Dur, AABA, 32 Takte, Flügel mit Streicherteppich
    "velvet": dict(name="Velvet Lounge", bpm=64, swing=0.62, seed=7, ballad=True, form=[
        "Dbmaj9|Bbm9|Ebm9|Ab13|Dbmaj9|Gb13#11|Fm7 Bb7b9|Ebm9 Ab13",
        "Dbmaj9|Bbm9|Ebm9|Ab13|Fm7|Bb7b9|Ebm9 Ab13|Dbmaj9",
        "Gbmaj9|Gm7b5 C7b9|Fm9|Bbm9 Eb13|Ebm9|Ab13|Dbmaj9|Ebm9 Ab7sus",
        "Dbmaj9|Bbm9|Ebm9|Ab13|Fm7 Bb7b9|Ebm9 Ab13|Dbmaj9|Dbmaj9"]),
    # mittleres Swing-Tempo in F, zwei Chorusse
    "golden": dict(name="Golden Hour Swing", bpm=116, swing=0.66, seed=21, ballad=False, form=[
        "Fmaj9|Dm9|Gm9|C13|Am7|D7b9|Gm9|C13",
        "Fmaj9|F9|Bbmaj9|Bbm9 Eb9|Am7|D7b9|Gm9 C13|Fmaj9",
        "Am7b5 D7b9|Gm9|Gm7b5 C7b9|Fmaj9|Bbmaj9|Em7b5 A7b9|Dm9 G13|Gm9 C7sus",
        "Fmaj9|Dm9|Gm9|C13|Am7 D7b9|Gm9 C13|F6/9|F6/9",
        "Fmaj9|Dm9|Gm9|C13|Am7|D7b9|Gm9|C13",
        "Fmaj9|F9|Bbmaj9|Bbm9 Eb9|Am7|D7b9|Gm9 C13|Fmaj9",
        "Am7b5 D7b9|Gm9|Gm7b5 C7b9|Fmaj9|Bbmaj9|Em7b5 A7b9|Dm9 G13|Gm9 C7sus",
        "Fmaj9|Dm9|Gm9|C13|Am7 D7b9|Gm9 C13|F6/9|F6/9"]),
}


def main():
    os.makedirs(os.path.join(PACK, "sounds", "music"), exist_ok=True)
    sj = os.path.join(PACK, "sounds.json")
    data = json.load(open(sj, encoding="utf-8")) if os.path.exists(sj) else {}
    with tempfile.TemporaryDirectory() as tmp:
        for sid, p in PIECES.items():
            song, beats = build(p["form"], p["bpm"], p["swing"], p["seed"], p["ballad"])
            mid = os.path.join(tmp, sid + ".mid")
            wav = os.path.join(tmp, sid + ".wav")
            write_midi(song, mid)
            subprocess.run(["fluidsynth", "-ni", "-q", "-g", "0.55", "-R", "1", "-C", "1", "-r", "44100",
                            "-F", wav, SF2, mid], check=True)
            ogg = os.path.join(PACK, "sounds", "music", sid + ".ogg")
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-af",
                            "silenceremove=start_periods=1:start_threshold=-60dB,areverse,"
                            "silenceremove=start_periods=1:start_threshold=-60dB,areverse,"
                            "loudnorm=I=-20:TP=-2", "-ar", "44100", "-c:a", "libvorbis",
                            "-q:a", "3", ogg], check=True)
            dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                                        ogg], capture_output=True, text=True, check=True).stdout)
            ticks = int(dur * 20) + 20
            data["music." + sid] = {"sounds": [{"name": "kuby:music/" + sid, "stream": True}]}
            with open(os.path.join(SONGS, sid + ".audio"), "w", encoding="utf-8") as f:
                f.write("%s|%d|kuby:music.%s\n" % (p["name"], ticks, sid))
            print(sid, p["name"], "%.0f s" % dur, os.path.getsize(ogg) // 1024, "KB")
    with open(sj, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)


if __name__ == "__main__":
    main()
