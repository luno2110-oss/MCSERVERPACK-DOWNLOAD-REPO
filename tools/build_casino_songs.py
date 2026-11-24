#!/usr/bin/env python3
"""Weitere Casino-Musik (v2.07, Wunsch des Besitzers: „mehr Musik im Casino, die man zufällig beim Spielen, durch Quests
und im Chip-Shop bekommt; Kategorien Klassik und Special, auch Songs, die nicht zum Casino-Vibe passen“).

Alles eigene Kompositionen (Akkordfolgen, Motive, Begleitmuster), als MIDI gebaut und mit fluidsynth und FluidR3_GM
gerendert wie tools/build_casino_jazz.py. Bekannte Titel werden nicht nachgespielt; „Plaza bei Nacht“ trifft die
Stimmung einer nächtlichen Mittelmeer-Piazza mit Akkordeon und Swing-Gitarre, die Melodie ist eigen.

Klassik (Casino):  Plaza bei Nacht (Akkordeon-Swing), Roulette Rumba (Bossa Nova), Neon Boulevard (Cool Jazz, Trompete)
Special:           Pixel-Abenteuer (Chiptune), Sternenstaub (Synthwave), Lagerfeuer (Folk)

Ausgabe wie build_casino_jazz.py: assets/kuby/sounds/music/<id>.ogg, „music.<id>“ in sounds.json und
KubyCore .../resources/casino/songs/<id>.audio mit „Name|Ticks|Sound|Kategorie|Freischalten“.
Aufruf: python3 tools/build_casino_songs.py [id ...]
"""
import json
import os
import random
import subprocess
import sys
import tempfile

import mido

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_casino_jazz as jz  # noqa: E402

TPB = jz.TPB
chord, QUAL, SCALE = jz.chord, jz.QUAL, jz.SCALE
# einfache Dreiklänge und Septakkorde für Pop, Chiptune und Folk
QUAL.update({"": [0, 4, 7], "m": [0, 3, 7], "7": [0, 4, 7, 10]})
SCALE.update({"": [0, 2, 4, 5, 7, 9, 11], "m": [0, 2, 3, 5, 7, 8, 10], "7": [0, 2, 4, 5, 7, 9, 10]})


class Song(jz.Song):
    def __init__(self, bpm, swing, parts):
        super().__init__(bpm, swing)
        self.ev = {k: [] for k in parts}


def bars_of(form):
    bars = []
    for line in form:
        for bar in line.split("|"):
            syms = bar.split()
            bars.append([(0, syms[0])] if len(syms) == 1 else [(0, syms[0]), (2, syms[1])])
    return bars


def chords_at_fn(bars):
    def at(beat):
        b = int(beat // 4) % len(bars)
        cur = bars[b][0][1]
        for off, sym in bars[b]:
            if beat - 4 * int(beat // 4) >= off:
                cur = sym
        return chord(cur)
    return at


def each_chord(bars):
    """(Beat, Länge, Grundton, Qualität, Grundton des nächsten Akkords) je Akkord."""
    for i, bar in enumerate(bars):
        for j, (off, sym) in enumerate(bar):
            root, q = chord(sym)
            length = (bar[j + 1][0] - off) if j + 1 < len(bar) else 4 - off
            nxt = chord(bars[(i + 1) % len(bars)][0][1])[0] if j == len(bar) - 1 else chord(bar[j + 1][1])[0]
            yield 4 * i + off, length, root, q, nxt


def tones(root, q, lo, hi):
    out = []
    for p in range(lo, hi + 1):
        if (p - root) % 12 in {i % 12 for i in QUAL[q]}:
            out.append(p)
    return out


def melody(song, part, rnd, bars, chords_at, center, rhythms, vel=(78, 66), max_dur=1.5, octave=0.0, echo=None, rest=0.15):
    """Melodie in Phrasen zu 4 Takten (je Phrase ein Motiv, in der 2. Hälfte variiert), endet auf Akkordtönen."""
    total = 4 * len(bars)
    beat, pitch = 0, center
    while beat < total - 4:
        motif = [rnd.choice(rhythms) for _ in range(2)]
        contour = [rnd.choice([-2, -1, 1, 2, 3, -3]) for _ in range(8)]
        play = rnd.random() > rest
        for b in range(4 if play else 0):
            if b == 3 and rnd.random() < 0.6:
                pat = [0, 2]                        # Atempause am Phrasenende
            else:
                pat = motif[b % 2]
            for k, off in enumerate(pat):
                t = beat + 4 * b + off
                if t >= total:
                    break
                root, q = chords_at(t)
                scale = {(root + s) % 12 for s in SCALE[q]}
                ct = {(root + i) % 12 for i in QUAL[q]}
                target = pitch + contour[(k + b) % len(contour)]
                if target > center + 9:
                    target -= 7
                if target < center - 7:
                    target += 7
                cand = [p for p in range(target - 2, target + 3) if p % 12 in scale]
                if off in (0, 2) or k == len(pat) - 1:
                    cand = [p for p in cand if p % 12 in ct] or cand
                pitch = min(cand, key=lambda p: abs(p - target)) if cand else target
                dur = min(max_dur, ((pat[k + 1] - off) if k + 1 < len(pat) else 4 - off))
                v = vel[0] if off in (0, 2) else vel[1]
                song.note(part, t, dur * 0.92, pitch, v, rnd)
                if octave and off in (0, 2) and rnd.random() < octave:
                    song.note(part, t, dur * 0.92, pitch - 12, v - 16, rnd)
                if echo:
                    song.note(echo, t + 0.75, dur * 0.8, pitch, v - 30, rnd)
        beat += 16


# =====================================================================================================================
# Stile
# =====================================================================================================================

def style_plaza(p, rnd):
    """Akkordeon-Swing auf einer nächtlichen Piazza: Gitarre „pompe“ (2 und 4), Bass auf 1 und 3, Besen."""
    s = Song(p["bpm"], 0.62, ("lead", "guitar", "bass", "drums", "pad"))
    bars = bars_of(p["form"])
    at = chords_at_fn(bars)
    for beat, length, root, q, nxt in each_chord(bars):
        v = jz.voicing(root, q, None, 52, 66)
        for k in range(int(length)):
            b = beat + k
            if k % 2 == 1:
                for n in v:
                    s.note("guitar", b, 0.32, n, 62, rnd)            # pompe: kurzer Schlag auf 2 und 4
            else:
                base = 38 + (root - 2) % 12
                s.note("bass", b, 0.9, base if k == 0 else base + 7 - (12 if base + 7 > 50 else 0), 84, rnd)
        s.note("pad", beat, length, v[-1] + 12, 26, rnd)
    melody(s, "lead", rnd, bars, at, 74, [[0, 1, 1.5, 2.5, 3], [0, 0.5, 1, 2, 3, 3.5], [0.5, 1, 1.5, 2, 3]], (82, 70), 1.25, 0.25)
    for i in range(len(bars)):
        for k in range(4):
            b = 4 * i + k
            s.note("drums", b, 0.4, 51, 46 if k % 2 else 38, rnd)
            s.note("drums", b, 0.6, 38, 20, rnd)                      # Besen
            if k % 2:
                s.note("drums", b, 0.3, 44, 44, rnd)
        if i % 8 == 7:
            s.note("drums", 4 * i + 3.5, 0.3, 38, 54, rnd)
    return s, 4 * len(bars)


def style_rumba(p, rnd):
    """Bossa Nova: Nylongitarre im Bossa-Muster, Bass Grundton–Quinte mit Vorwegnahme, Rim-Click und Shaker, Flöte."""
    s = Song(p["bpm"], 0.5, ("lead", "guitar", "bass", "drums", "pad"))
    bars = bars_of(p["form"])
    at = chords_at_fn(bars)
    prev = None
    for beat, length, root, q, nxt in each_chord(bars):
        v = jz.voicing(root, q, prev, 52, 68)
        prev = v
        hits = [0, 1.5, 2.5] if length >= 4 else [0, 1.5]
        for h in hits:
            for n in v:
                s.note("guitar", beat + h, 0.45, n, 58, rnd)
        base = 36 + (root - 0) % 12
        s.note("bass", beat, 1.4, base, 82, rnd)
        if length >= 4:
            fifth = base + 7 if base + 7 <= 52 else base - 5
            s.note("bass", beat + 1.5, 0.5, fifth, 70, rnd)
            s.note("bass", beat + 2, 1.4, fifth, 76, rnd)
            s.note("bass", beat + 3.5, 0.5, 36 + nxt % 12, 72, rnd)    # Vorwegnahme
    melody(s, "lead", rnd, bars, at, 76, [[0, 1.5, 2.5, 3], [0.5, 1.5, 2], [0, 1, 2.5, 3.5], [1.5, 2, 3]], (76, 64), 2.0)
    clave = [0, 1.5, 3, 5, 6.5]                                      # über zwei Takte
    for i in range(0, len(bars), 2):
        for c in clave:
            s.note("drums", 4 * i + c, 0.2, 37, 58, rnd)               # Rim-Click
        for k in range(16):
            s.note("drums", 4 * i + 0.5 * k, 0.2, 70, 34 if k % 2 else 46, rnd)   # Maracas
    return s, 4 * len(bars)


def style_cool(p, rnd):
    """Cool Jazz: gedämpfte Trompete, Klavier-Comping, Walking Bass, Ride."""
    s = Song(p["bpm"], 0.64, ("lead", "piano", "bass", "drums", "pad"))
    bars = bars_of(p["form"])
    at = chords_at_fn(bars)
    prev = None
    for beat, length, root, q, nxt in each_chord(bars):
        v = jz.voicing(root, q, prev)
        prev = v
        hits = [(0, 1.0), (1.5, 0.45)] if length >= 4 else [(0, 0.9)]
        if length >= 4 and rnd.random() < 0.4:
            hits = [(0.5, 0.4), (2, 0.9), (3.5, 0.4)]
        for h, d in hits:
            for n in v:
                s.note("piano", beat + h, d, n, 50, rnd)
        base = 33 + (root - 9) % 12
        for k in range(int(length)):
            if k == 0:
                pt = base
            elif k == int(length) - 1:
                pt = 33 + (nxt - 9) % 12 + rnd.choice([-1, 1])
            else:
                pt = rnd.choice([base, base + QUAL[q][1] % 12, base + 7]) + rnd.choice([0, 12])
                if pt > 52:
                    pt -= 12
            s.note("bass", beat + k, 0.9, pt, 84 if k == 0 else 74, rnd)
    melody(s, "lead", rnd, bars, at, 72, [[0, 1, 1.5, 2.5], [0.5, 1, 2, 3], [0, 1.5, 2, 3.5], [1, 1.5, 2.5]], (80, 68), 1.75)
    for i in range(len(bars)):
        for k in range(4):
            b = 4 * i + k
            s.note("drums", b, 0.4, 51, 56 if k % 2 else 48, rnd)
            if k % 2:
                s.note("drums", b, 0.4, 44, 50, rnd)
                s.note("drums", b + 0.5, 0.3, 51, 40, rnd)
    return s, 4 * len(bars)


def style_chip(p, rnd):
    """Chiptune: Rechteck-Melodie, schnelle Arpeggien, pumpender Bass, knappe Trommeln."""
    s = Song(p["bpm"], 0.5, ("lead", "arp", "bass", "drums"))
    bars = bars_of(p["form"])
    at = chords_at_fn(bars)
    for beat, length, root, q, nxt in each_chord(bars):
        ts = tones(root, q, 60, 79)[:4] or [60 + root]
        for k in range(int(length * 4)):
            s.note("arp", beat + 0.25 * k, 0.22, ts[k % len(ts)], 50, rnd)
        base = 36 + root % 12
        for k in range(int(length * 2)):
            s.note("bass", beat + 0.5 * k, 0.4, base + (12 if k % 2 else 0), 82, rnd)
    melody(s, "lead", rnd, bars, at, 76, [[0, 0.5, 1, 1.5, 2, 3], [0, 1, 1.5, 2, 2.5, 3], [0, 0.75, 1.5, 2, 3]], (84, 72), 1.0, rest=0.05)
    for i in range(len(bars)):
        for k in range(4):
            b = 4 * i + k
            s.note("drums", b, 0.2, 36 if k % 2 == 0 else 38, 90 if k % 2 == 0 else 80, rnd)
            s.note("drums", b + 0.5, 0.1, 42, 50, rnd)
        if i % 4 == 3:
            for f in (3.25, 3.5, 3.75):
                s.note("drums", 4 * i + f, 0.1, 38, 70, rnd)
    return s, 4 * len(bars)


def style_synthwave(p, rnd):
    """Synthwave: warme Flächen, Sägezahn-Bass in Achteln, Lead mit Echo, Kick 1 und 3, Snare 2 und 4."""
    s = Song(p["bpm"], 0.5, ("lead", "echo", "pad", "bass", "drums"))
    bars = bars_of(p["form"])
    at = chords_at_fn(bars)
    for beat, length, root, q, nxt in each_chord(bars):
        v = tones(root, q, 55, 72)[:4]
        for n in v:
            s.note("pad", beat, length, n, 44, rnd)
        base = 33 + (root - 9) % 12
        for k in range(int(length * 2)):
            s.note("bass", beat + 0.5 * k, 0.42, base, 80 if k % 2 == 0 else 68, rnd)
    melody(s, "lead", rnd, bars, at, 72, [[0, 1, 2, 2.5], [0, 1.5, 3], [0.5, 1, 2, 3], [0, 2, 3, 3.5]], (80, 70), 2.0, echo="echo", rest=0.2)
    for i in range(len(bars)):
        for k in range(4):
            b = 4 * i + k
            s.note("drums", b, 0.3, 36 if k % 2 == 0 else 40, 92 if k % 2 == 0 else 84, rnd)
            s.note("drums", b, 0.2, 42, 46, rnd)
            s.note("drums", b + 0.5, 0.2, 42, 38, rnd)
        if i % 8 == 7:
            for f in (3, 3.25, 3.5, 3.75):
                s.note("drums", 4 * i + f, 0.2, 45 + int(f * 4) % 5, 70, rnd)   # Tom-Fill
    return s, 4 * len(bars)


def style_folk(p, rnd):
    """Lagerfeuer: Steelstring-Gitarre geschlagen (ab-auf), Pfeife als Melodie, leichter Bass, Shaker."""
    s = Song(p["bpm"], 0.56, ("lead", "guitar", "bass", "drums"))
    bars = bars_of(p["form"])
    at = chords_at_fn(bars)
    strum = [(0, 1, 70), (1, 1, 52), (1.5, 0, 48), (2.5, 0, 56), (3, 1, 50), (3.5, 0, 46)]   # (Beat, ab?, Stärke)
    for beat, length, root, q, nxt in each_chord(bars):
        ts = tones(root, q, 52, 67)[:5] or [52 + root % 12]
        for off, down, vel in strum:
            if off >= length:
                continue
            seq = ts if down else list(reversed(ts))
            for k, n in enumerate(seq):
                s.note("guitar", beat + off + 0.03 * k, 0.45, n, vel - 2 * k, rnd)
        base = 40 + (root - 4) % 12
        s.note("bass", beat, 1.8, base, 70, rnd)
        if length >= 4:
            s.note("bass", beat + 2, 1.8, base + 7 if base + 7 <= 55 else base - 5, 62, rnd)
    melody(s, "lead", rnd, bars, at, 79, [[0, 1, 1.5, 2, 3], [0, 0.5, 1, 2, 3], [0, 1.5, 2, 3]], (72, 62), 1.75, rest=0.25)
    for i in range(len(bars)):
        for k in range(8):
            s.note("drums", 4 * i + 0.5 * k, 0.2, 82, 30 if k % 2 else 40, rnd)   # Shaker
    return s, 4 * len(bars)


STYLES = {"plaza": style_plaza, "rumba": style_rumba, "cool": style_cool, "chip": style_chip, "synthwave": style_synthwave,
          "folk": style_folk}

# Instrumente je Stil: Teil -> (Kanal, GM-Programm, Lautstärke, Hall)
SETUP = {
    "plaza": {"lead": (0, 21, 96, 60), "guitar": (1, 24, 84, 50), "bass": (2, 32, 92, 30), "pad": (3, 48, 40, 80), "drums": (9, 40, 66, 50)},
    "rumba": {"lead": (0, 73, 92, 70), "guitar": (1, 24, 90, 50), "bass": (2, 32, 88, 30), "pad": (3, 89, 30, 80), "drums": (9, 0, 70, 40)},
    "cool": {"lead": (0, 59, 96, 70), "piano": (1, 0, 80, 60), "bass": (2, 32, 92, 30), "pad": (3, 49, 30, 80), "drums": (9, 40, 66, 50)},
    "chip": {"lead": (0, 80, 84, 30), "arp": (1, 81, 50, 30), "bass": (2, 38, 84, 20), "drums": (9, 24, 76, 20)},
    "synthwave": {"lead": (0, 81, 82, 80), "echo": (4, 81, 50, 110), "pad": (1, 89, 70, 100), "bass": (2, 38, 84, 30),
                  "drums": (9, 25, 80, 70)},
    "folk": {"lead": (0, 78, 86, 70), "guitar": (1, 25, 86, 50), "bass": (2, 32, 70, 30), "drums": (9, 0, 50, 40)},
}

PIECES = {
    # Klassik: Casino-Stimmung
    "plaza": dict(name="Plaza bei Nacht", style="plaza", bpm=124, seed=31, cat="klassik", unlock="drop", form=[
        "Dm9|Dm9|Gm9|A7b9|Dm9|Bbmaj7|Em7b5 A7b9|Dm9",
        "Dm9|Dm9|Gm9|C13|Fmaj9|Bbmaj7|Em7b5 A7b9|Dm9",
        "Fmaj7|Fmaj7|Bbmaj7|Bbmaj7|Gm9|C13|Fmaj9|Em7b5 A7b9",
        "Dm9|Dm9|Gm9|A7b9|Dm9 Gm9|Em7b5 A7b9|Dm9|Dm9",
        "Dm9|Dm9|Gm9|A7b9|Dm9|Bbmaj7|Em7b5 A7b9|Dm9",
        "Fmaj7|Fmaj7|Bbmaj7|Bbmaj7|Gm9|C13|Fmaj9|Em7b5 A7b9",
        "Dm9|Dm9|Gm9|A7b9|Dm9 Gm9|Em7b5 A7b9|Dm9|Dm9"]),
    "rumba": dict(name="Roulette Rumba", style="rumba", bpm=128, seed=44, cat="klassik", unlock="quest:runden:100", form=[
        "Cmaj9|Cmaj9|D9|D9|Dm9|G13|Cmaj9|Abmaj7 G7b9",
        "Cmaj9|Cmaj9|D9|D9|Dm9|G13|Em7 A7b9|Dm9 G13",
        "Fmaj9|Fmaj9|Bb13|Bb13|Em9|A7b9|Dm9|G7sus",
        "Cmaj9|Cmaj9|D9|D9|Dm9|G13|Cmaj9|Cmaj9",
        "Cmaj9|Cmaj9|D9|D9|Dm9|G13|Em7 A7b9|Dm9 G13",
        "Fmaj9|Fmaj9|Bb13|Bb13|Em9|A7b9|Dm9|G7sus"]),
    "neon": dict(name="Neon Boulevard", style="cool", bpm=104, seed=52, cat="klassik", unlock="shop:150000", form=[
        "Ebmaj9|Cm9|Fm9|Bb13|Gm7|C7b9|Fm9|Bb13",
        "Ebmaj9|Eb9|Abmaj9|Abm9 Db9|Gm7|C7b9|Fm9 Bb13|Ebmaj9",
        "Am7b5 D7b9|Gm9|Gm7b5 C7b9|Fm9|Abmaj9|Dm7b5 G7b9|Cm9 F13|Fm9 Bb7sus",
        "Ebmaj9|Cm9|Fm9|Bb13|Gm7 C7b9|Fm9 Bb13|Eb6/9|Eb6/9",
        "Ebmaj9|Cm9|Fm9|Bb13|Gm7|C7b9|Fm9|Bb13",
        "Ebmaj9|Eb9|Abmaj9|Abm9 Db9|Gm7|C7b9|Fm9 Bb13|Ebmaj9"]),
    # Special: nicht im Casino-Stil
    "pixel": dict(name="Pixel-Abenteuer", style="chip", bpm=148, seed=63, cat="special", unlock="quest:serie:5", form=[
        "C|Am7|F|G", "C|Am7|F|G", "Am7|F|C|G", "F|G|Em7|Am7",
        "C|Am7|F|G", "C|Am7|F|G", "Am7|F|C|G", "F|G|C|C",
        "F|G|Em7|Am7", "Dm7|G|C|C", "C|Am7|F|G", "F|G|C|C"]),
    "sternenstaub": dict(name="Sternenstaub", style="synthwave", bpm=104, seed=77, cat="special", unlock="drop", form=[
        "Am7|Fmaj7|C|G", "Am7|Fmaj7|C|G", "Dm7|Fmaj7|Am7|G", "Am7|Fmaj7|C|Em7",
        "Fmaj7|G|Am7|Am7", "Fmaj7|G|Cmaj7|Em7", "Am7|Fmaj7|C|G", "Dm7|Fmaj7|Am7|Am7"]),
    "lagerfeuer": dict(name="Lagerfeuer", style="folk", bpm=96, seed=88, cat="special", unlock="shop:100000", form=[
        "G|D|Em7|C", "G|D|C|D", "Em7|C|G|D", "C|D|G|G",
        "G|D|Em7|C", "G|D|C|D", "Em7|C|G|D", "C|D|G|G"]),
}


def write_midi(song, setup, path):
    mid = mido.MidiFile(ticks_per_beat=TPB)
    meta = mido.MidiTrack()
    meta.append(mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(song.bpm), time=0))
    mid.tracks.append(meta)
    for part, (ch, prog, vol, rev) in setup.items():
        if part not in song.ev:
            continue
        tr = mido.MidiTrack()
        tr.append(mido.Message("program_change", channel=ch, program=prog, time=0))
        tr.append(mido.Message("control_change", channel=ch, control=7, value=vol, time=0))
        tr.append(mido.Message("control_change", channel=ch, control=91, value=rev, time=0))
        if part == "echo":
            tr.append(mido.Message("control_change", channel=ch, control=10, value=84, time=0))
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


def ending(s, p, total, rnd):
    """Schluss: Tonika lang ausklingen lassen."""
    root, q = chord(p["form"][0].split("|")[0].split()[0])
    melodic = "piano" if "piano" in s.ev else "guitar" if "guitar" in s.ev else "pad" if "pad" in s.ev else "lead"
    for k, n in enumerate(tones(root, q, 55, 72)[:4]):
        s.note(melodic, total + 0.1 * k, 5, n, 58, rnd)
    if "bass" in s.ev:
        s.note("bass", total, 5, 36 + root % 12, 76, rnd)
    return total + 6


def main(only):
    os.makedirs(os.path.join(jz.PACK, "sounds", "music"), exist_ok=True)
    sj = os.path.join(jz.PACK, "sounds.json")
    data = json.load(open(sj, encoding="utf-8")) if os.path.exists(sj) else {}
    with tempfile.TemporaryDirectory() as tmp:
        for sid, p in PIECES.items():
            if only and sid not in only:
                continue
            rnd = random.Random(p["seed"])
            s, total = STYLES[p["style"]](p, rnd)
            end = ending(s, p, total, rnd)
            mid = os.path.join(tmp, sid + ".mid")
            wav = os.path.join(tmp, sid + ".wav")
            write_midi(s, SETUP[p["style"]], mid)
            subprocess.run(["fluidsynth", "-ni", "-q", "-g", "0.55", "-R", "1", "-C", "1", "-r", "44100",
                            "-F", wav, jz.SF2, mid], check=True)
            ogg = os.path.join(jz.PACK, "sounds", "music", sid + ".ogg")
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-af",
                            "silenceremove=start_periods=1:start_threshold=-60dB,areverse,"
                            "silenceremove=start_periods=1:start_threshold=-60dB,areverse,"
                            "loudnorm=I=-20:TP=-2", "-ar", "44100", "-c:a", "libvorbis",
                            "-q:a", "2", ogg], check=True)
            dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                                        ogg], capture_output=True, text=True, check=True).stdout)
            ticks = int(dur * 20) + 20
            data["music." + sid] = {"sounds": [{"name": "kuby:music/" + sid, "stream": True}]}
            with open(os.path.join(jz.SONGS, sid + ".audio"), "w", encoding="utf-8") as f:
                f.write("%s|%d|kuby:music.%s|%s|%s\n" % (p["name"], ticks, sid, p["cat"], p["unlock"]))
            print(sid, p["name"], "%.0f s" % dur, os.path.getsize(ogg) // 1024, "KB", "(%d Takte)" % (end // 4))
    with open(sj, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)


if __name__ == "__main__":
    main(sys.argv[1:])
