"""Casino-Musik als Notenblock-Stücke (wie im Clip: drei Songs, Kategorie Jukebox/Notenblöcke).

Schreibt KubyCore-Ressourcen casino/songs/<id>.txt:
    Zeile 1: Name|Länge in Ticks
    danach:  Tick Instrument Note(0-24) Lautstärke(0-100)
Instrumente sind die Notenblock-Klänge (harp, bass, flute, ...). Note 0 = Fis des Instruments.

Aufruf: python3 tools/build_songs.py <resources-ordner>
Die Stücke sind komponiert (Akkordfolgen, Basslinien, Rhythmen, Melodien von Hand), die Soli werden mit festem Zufall
aus Akkord- und Skalentönen gebaut, damit jeder Build dieselbe Musik ergibt.
"""
import os
import random
import sys

# Grundton (MIDI) der Note 0 je Instrument
BASE = {
    "harp": 54, "bass": 30, "guitar": 42, "flute": 66, "bell": 78, "chime": 78, "xylophone": 78,
    "iron_xylophone": 54, "cow_bell": 66, "didgeridoo": 30, "bit": 54, "banjo": 54, "pling": 54,
    "basedrum": 54, "snare": 54, "hat": 54,
}

NOTE = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6, "G": 7, "G#": 8,
        "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}


def midi(name):
    """'C5' -> 72, 'Bb3' -> 58"""
    n = name[:-1]
    o = int(name[-1])
    return 12 * (o + 1) + NOTE[n]


# Akkorde: Typ -> Intervalle
CHORD = {
    "maj7": [0, 4, 7, 11], "6": [0, 4, 7, 9], "7": [0, 4, 7, 10], "m7": [0, 3, 7, 10], "m6": [0, 3, 7, 9],
    "maj9": [0, 4, 7, 11, 14], "m9": [0, 3, 7, 10, 14], "13": [0, 4, 10, 14, 21], "7b9": [0, 4, 7, 10, 13],
    "m7b5": [0, 3, 6, 10], "7#9": [0, 4, 10, 15], "maj": [0, 4, 7], "dim7": [0, 3, 6, 9],
}


def chord(sym):
    """'Fmaj7' -> (Grundton 0-11, Intervalle)"""
    root = sym[:2] if len(sym) > 1 and sym[1] in "#b" else sym[:1]
    kind = sym[len(root):] or "maj"
    return NOTE[root], CHORD[kind]


class Song:
    def __init__(self, name, ticks_per_bar):
        self.name = name
        self.bar = ticks_per_bar
        self.events = []

    def note(self, tick, instr, m, vol):
        base = BASE[instr]
        i = m - base
        while i < 0:
            i += 12
        while i > 24:
            i -= 12
        self.events.append((int(round(tick)), instr, i, int(vol)))

    def perc(self, tick, instr, pitch, vol):
        self.events.append((int(round(tick)), instr, pitch, int(vol)))

    def write(self, path, length):
        self.events.sort()
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("%s|%d\n" % (self.name, length))
            for t, ins, n, v in self.events:
                if 0 <= t < length and v > 0:
                    fh.write("%d %s %d %d\n" % (t, ins, n, v))


def voicing(sym, low, high, top=None):
    """Akkordtöne zwischen low und high (MIDI), enge Lage."""
    r, iv = chord(sym)
    tones = []
    for octave in range(2, 8):
        for i in iv:
            m = 12 * octave + r + i
            if low <= m <= high:
                tones.append(m)
    tones = sorted(set(tones))
    # höchstens vier Töne, gleichmäßig
    if len(tones) > 4:
        step = len(tones) / 4.0
        tones = [tones[int(k * step)] for k in range(4)]
    return tones


def scale_for(sym):
    r, iv = chord(sym)
    if 3 in iv and 6 in iv:  # halbvermindert
        steps = [0, 1, 3, 5, 6, 8, 10]
    elif 3 in iv:  # Moll: dorisch
        steps = [0, 2, 3, 5, 7, 9, 10]
    elif 10 in iv:  # Dominante: mixolydisch
        steps = [0, 2, 4, 5, 7, 9, 10]
    else:  # Dur
        steps = [0, 2, 4, 7, 9, 11, 14]
    return r, steps


# =====================================================================================================================
# 1) Brisk Casino Swing — F-Dur, Swing, 133 BPM (Schlag = 9 Ticks, Achtel 6 + 3), 2:58
# =====================================================================================================================

def brisk():
    beat = 9
    s = Song("Brisk Casino Swing", beat * 4)
    A = [["F6"], ["D7"], ["Gm7"], ["C7"], ["Fmaj7"], ["D7"], ["Gm7", "C7"], ["F6"]]
    A2 = [["F6"], ["D7"], ["Gm7"], ["C7"], ["Fmaj7"], ["F7"], ["Bbmaj7", "Bbm6"], ["F6", "C7"]]
    B = [["Am7"], ["D7"], ["Gm7"], ["C7"], ["Am7", "D7"], ["Gm7", "C7"], ["Am7", "D7"], ["Gm7", "C7"]]
    # Thema (Achtel-Raster 0..7 je Takt): (Achtel, Ton, Länge in Achteln)
    themeA = [
        [(0, "C5", 1), (1, "A4", 1), (2, "C5", 1), (3, "D5", 1), (4, "C5", 2), (6, "A4", 2)],
        [(0, "A4", 2), (2, "F#4", 1), (3, "A4", 1), (4, "C5", 2), (6, "D5", 2)],
        [(0, "Bb4", 1), (1, "D5", 1), (2, "F5", 2), (4, "D5", 1), (5, "Bb4", 1), (6, "G4", 2)],
        [(0, "E5", 2), (2, "D5", 1), (3, "C5", 1), (4, "Bb4", 2), (6, "G4", 2)],
        [(0, "A4", 1), (1, "C5", 1), (2, "F5", 2), (4, "E5", 1), (5, "F5", 1), (6, "C5", 2)],
        [(0, "D5", 2), (2, "C5", 1), (3, "A4", 1), (4, "F#4", 2), (6, "A4", 2)],
        [(0, "G4", 1), (1, "Bb4", 1), (2, "D5", 2), (4, "E5", 1), (5, "G5", 1), (6, "E5", 2)],
        [(0, "F5", 4), (6, "C5", 1), (7, "D5", 1)],
    ]
    themeA2 = themeA[:4] + [
        [(0, "A4", 1), (1, "C5", 1), (2, "F5", 2), (4, "Eb5", 2), (6, "C5", 2)],
        [(0, "Eb5", 2), (2, "D5", 1), (3, "C5", 1), (4, "A4", 2), (6, "F4", 2)],
        [(0, "D5", 2), (2, "F5", 2), (4, "Db5", 2), (6, "Bb4", 2)],
        [(0, "A4", 3), (4, "G4", 1), (5, "A4", 1), (6, "C5", 2)],
    ]
    themeB = [
        [(0, "E5", 1), (1, "G5", 1), (2, "E5", 1), (3, "C5", 1), (4, "A4", 4)],
        [(0, "F#5", 1), (1, "A5", 1), (2, "F#5", 1), (3, "D5", 1), (4, "C5", 4)],
        [(0, "D5", 1), (1, "F5", 1), (2, "D5", 1), (3, "Bb4", 1), (4, "G4", 4)],
        [(0, "E5", 2), (2, "G5", 2), (4, "Bb5", 2), (6, "G5", 2)],
        [(0, "A5", 2), (2, "G5", 1), (3, "E5", 1), (4, "F#5", 2), (6, "D5", 2)],
        [(0, "G5", 2), (2, "F5", 1), (3, "D5", 1), (4, "E5", 2), (6, "C5", 2)],
        [(0, "C5", 1), (1, "E5", 1), (2, "A5", 2), (4, "F#5", 1), (5, "A5", 1), (6, "C6", 2)],
        [(0, "Bb5", 2), (2, "G5", 2), (4, "E5", 2), (6, "C5", 2)],
    ]
    form = [("intro", [["F6"], ["C7"]], None)]
    head = [("A", A, themeA), ("A2", A2, themeA2), ("B", B, themeB), ("A2", A2, themeA2)]
    form += head + [("solo", A, None), ("solo", A2, None), ("solo", B, None), ("solo", A2, None)] + head
    rnd = random.Random(7)
    prev_root = None

    def at(bar, eighth):
        """Swing-Achtel: gerade auf dem Schlag, ungerade 6 Ticks später."""
        return bar * s.bar + (eighth // 2) * beat + (6 if eighth % 2 else 0)

    bar = 0
    for part, chords, theme in form:
        for bi, bar_chords in enumerate(chords):
            # Akkord je Schlag
            per_beat = [bar_chords[0]] * 2 + [bar_chords[-1]] * 2
            # Schlagzeug: Ride (Hat) auf 1, 2, 2+, 3, 4, 4+; Snare leise auf 2 und 4; Bassdrum weich auf 1 und 3
            for e, v in ((0, 34), (2, 40), (3, 26), (4, 34), (6, 40), (7, 26)):
                s.perc(at(bar, e), "hat", 18 if e % 2 else 16, v)
            for e in (2, 6):
                s.perc(at(bar, e), "snare", 10, 22)
            for e in (0, 4):
                s.perc(at(bar, e), "basedrum", 4, 30)
            # Walking Bass
            for b in range(4):
                sym = per_beat[b]
                r, iv = chord(sym)
                root = 36 + ((r - 0) % 12)  # um C2..B2
                if root < 40:
                    root += 12
                if b == 0:
                    m = root
                elif b == 3:
                    nxt = (chords[bi + 1][0] if bi + 1 < len(chords) else bar_chords[0])
                    nr = chord(nxt)[0]
                    target = 36 + nr
                    if target < 40:
                        target += 12
                    m = target + (1 if rnd.random() < 0.5 else -1)
                else:
                    m = root + [iv[1], iv[2], 12][rnd.randrange(3)] if b == 1 else root + iv[2]
                s.note(bar * s.bar + b * beat, "bass", m, 78)
            # Klavier: Charleston (1 und 2+), dazu kurz auf 4 bei zwei Akkorden
            for e, sym in ((0, per_beat[0]), (3, per_beat[1]), (6, per_beat[3]) if len(bar_chords) > 1 else (5, per_beat[2])):
                for m in voicing(sym, 57, 72):
                    s.note(at(bar, e), "harp", m, 34)
            # Melodie (Thema) oder Solo
            if theme:
                for e, n, ln in theme[bi]:
                    s.note(at(bar, e), "flute", midi(n), 72)
                    if ln >= 2:
                        s.note(at(bar, e), "iron_xylophone", midi(n) - 12, 26)
            elif part == "solo":
                sym = bar_chords[0]
                r, steps = scale_for(sym)
                pos = 0
                cur = 72 + r
                while pos < 8:
                    ln = rnd.choice([1, 1, 1, 2, 2, 3])
                    if rnd.random() < 0.15:
                        pos += ln
                        continue
                    sym = per_beat[min(3, pos // 2)]
                    r, steps = scale_for(sym)
                    cands = [60 + r + st + 12 * k for st in steps for k in (0, 1) if 64 <= 60 + r + st + 12 * k <= 84]
                    cur = min(cands, key=lambda c: abs(c - cur) + rnd.random() * 5)
                    s.note(at(bar, pos), "pling", cur, 60)
                    pos += ln
            bar += 1
    # Schlussakkord
    end = bar * s.bar
    for m in voicing("F6", 57, 76):
        s.note(end, "harp", m, 50)
        s.note(end, "iron_xylophone", m, 30)
    s.note(end, "bass", 41, 80)
    s.note(end, "flute", midi("F5"), 70)
    s.perc(end, "basedrum", 4, 40)
    length = 3560  # 2:58
    return s, max(length, end + 32)


# =====================================================================================================================
# 2) Mid-Night Lounge — a-Moll, Bossa, 100 BPM (Schlag = 12 Ticks, gerade Achtel), 2:55
# =====================================================================================================================

def midnight():
    beat = 12
    s = Song("Mid-Night Lounge", beat * 4)
    prog = [["Am9"], ["Dm9"], ["G13"], ["Cmaj9"], ["Fmaj7"], ["Bm7b5", "E7b9"], ["Am9"], ["E7#9"]]
    themeA = [
        [(0, "E5", 3), (3, "C5", 1), (4, "B4", 2), (6, "A4", 2)],
        [(0, "F5", 3), (3, "E5", 1), (4, "D5", 4)],
        [(0, "B4", 2), (2, "D5", 2), (4, "F5", 2), (6, "E5", 2)],
        [(0, "E5", 6), (6, "G5", 2)],
        [(0, "A5", 3), (3, "G5", 1), (4, "E5", 2), (6, "C5", 2)],
        [(0, "D5", 3), (3, "C5", 1), (4, "B4", 2), (6, "G#4", 2)],
        [(0, "A4", 6), (6, "C5", 1), (7, "E5", 1)],
        [(0, "G#5", 2), (2, "F5", 2), (4, "D5", 2), (6, "B4", 2)],
    ]
    themeB = [
        [(0, "C6", 2), (2, "B5", 2), (4, "A5", 2), (6, "E5", 2)],
        [(0, "F5", 4), (4, "A5", 2), (6, "C6", 2)],
        [(0, "B5", 3), (3, "A5", 1), (4, "G5", 2), (6, "F5", 2)],
        [(0, "E5", 4), (4, "G5", 2), (6, "B5", 2)],
        [(0, "A5", 2), (2, "G5", 2), (4, "E5", 2), (6, "C5", 2)],
        [(0, "D5", 2), (2, "F5", 2), (4, "E5", 2), (6, "D5", 2)],
        [(0, "C5", 3), (3, "B4", 1), (4, "A4", 4)],
        [(0, "B4", 2), (2, "D5", 2), (4, "E5", 4)],
    ]
    form = [("intro", prog[:4]), ("A", prog), ("A", prog), ("B", prog), ("B", prog), ("solo", prog), ("solo", prog),
            ("A", prog), ("A", prog), ("outro", prog[:4])]
    rnd = random.Random(11)
    at = lambda bar, e: bar * s.bar + e * (beat // 2)
    clave = [[0, 3, 6], [2, 4]]
    bar = 0
    total_bars = sum(len(c) for _, c in form)
    for part, chords in form:
        for bi, bar_chords in enumerate(chords):
            fade = 1.0
            if part == "outro":
                fade = max(0.25, 1 - bi / len(chords))
            first, second = bar_chords[0], bar_chords[-1]
            # Percussion: leiser Hat auf allen Achteln, Rimshot (Snare hoch) auf der Clave, Bassdrum Bossa
            for e in range(8):
                s.perc(at(bar, e), "hat", 20 if e % 2 else 17, (22 if e % 2 else 30) * fade)
            for e in clave[bar % 2]:
                s.perc(at(bar, e), "snare", 22, 22 * fade)
            for e in (0, 3, 4, 7):
                s.perc(at(bar, e), "basedrum", 2, (34 if e in (0, 4) else 22) * fade)
            # Bossa-Bass: Grundton 1, Quinte 2+, Grundton 3, Quinte 4+
            for e, sym, fifth in ((0, first, False), (3, first, True), (4, second, False), (7, second, True)):
                r, iv = chord(sym)
                root = 33 + ((r - 9) % 12)  # A1 .. G#2
                m = root + (iv[2] if fifth else 0)
                if m > 52:
                    m -= 12
                s.note(at(bar, e), "bass", m, 80 * fade)
            # Gitarre: synkopiert
            for e, sym in ((0, first), (3, first), (5, second), (6, second)):
                for m in voicing(sym, 50, 66):
                    s.note(at(bar, e), "guitar", m, 30 * fade)
            # Pad: Glocke leise auf 1 mit der Septime/None
            if part in ("A", "B") and bi % 2 == 0:
                r, iv = chord(first)
                s.note(at(bar, 0), "chime", 72 + r + iv[-1], 14)
            if part == "A":
                for e, n, ln in themeA[bi]:
                    s.note(at(bar, e), "flute", midi(n), 70)
            elif part == "B":
                for e, n, ln in themeB[bi]:
                    s.note(at(bar, e), "flute", midi(n), 66)
                    if ln >= 2:
                        s.note(at(bar, e), "iron_xylophone", midi(n) - 12, 30)
            elif part == "solo":
                cur = 69
                pos = 0
                while pos < 8:
                    ln = rnd.choice([1, 2, 2, 3, 4])
                    if rnd.random() < 0.2:
                        pos += ln
                        continue
                    sym = first if pos < 4 else second
                    r, steps = scale_for(sym)
                    cands = [60 + r + st + 12 * k for st in steps for k in (-1, 0, 1) if 62 <= 60 + r + st + 12 * k <= 79]
                    cur = min(cands, key=lambda c: abs(c - cur) + rnd.random() * 6)
                    s.note(at(bar, pos), "iron_xylophone", cur, 64)
                    pos += ln
            bar += 1
    end = bar * s.bar
    for m in voicing("Am9", 52, 67):
        s.note(end, "guitar", m, 36)
    s.note(end, "bass", 33, 70)
    s.note(end, "chime", 83, 20)
    return s, end + 48


# =====================================================================================================================
# 3) Late-night Lounge — d-dorisch, ruhiger Swing, 86 BPM (Schlag = 14 Ticks, Achtel 9 + 5), 3:02
# =====================================================================================================================

def latenight():
    beat = 14
    s = Song("Late-night Lounge", beat * 4)
    prog = [["Dm9"], ["G13"], ["Cmaj9"], ["A7b9"], ["Dm9"], ["G13"], ["Em7", "A7b9"], ["Dm9"]]
    bridge = [["Gm7"], ["C7"], ["Fmaj7"], ["Bbmaj7"], ["Em7"], ["A7"], ["Dm9"], ["A7b9"]]
    themeA = [
        [(0, "A4", 3), (3, "C5", 1), (4, "E5", 2), (6, "D5", 2)],
        [(0, "B4", 6), (6, "A4", 1), (7, "B4", 1)],
        [(0, "C5", 2), (2, "E5", 2), (4, "D5", 2), (6, "E5", 2)],
        [(0, "C#5", 4), (4, "Bb4", 2), (6, "G4", 2)],
        [(0, "A4", 3), (3, "C5", 1), (4, "F5", 2), (6, "E5", 2)],
        [(0, "D5", 4), (4, "B4", 2), (6, "G4", 2)],
        [(0, "G4", 2), (2, "B4", 2), (4, "C#5", 2), (6, "E5", 2)],
        [(0, "D5", 8)],
    ]
    themeB = [
        [(0, "Bb4", 2), (2, "D5", 2), (4, "F5", 4)],
        [(0, "E5", 3), (3, "D5", 1), (4, "C5", 2), (6, "Bb4", 2)],
        [(0, "A4", 2), (2, "C5", 2), (4, "E5", 2), (6, "G5", 2)],
        [(0, "F5", 6), (6, "D5", 2)],
        [(0, "G5", 2), (2, "E5", 2), (4, "D5", 2), (6, "B4", 2)],
        [(0, "C#5", 4), (4, "E5", 2), (6, "G5", 2)],
        [(0, "F5", 3), (3, "E5", 1), (4, "D5", 2), (6, "A4", 2)],
        [(0, "Bb4", 4), (4, "C#5", 4)],
    ]
    form = [("intro", prog[:4]), ("A", prog), ("A", prog), ("B", bridge), ("A", prog), ("solo", prog), ("solo", bridge),
            ("A", prog), ("outro", prog[-4:])]
    rnd = random.Random(23)

    def at(bar, e):
        return bar * s.bar + (e // 2) * beat + (9 if e % 2 else 0)

    bar = 0
    for part, chords in form:
        for bi, bar_chords in enumerate(chords):
            fade = 1.0 if part != "outro" else max(0.3, 1 - bi / 4)
            first, second = bar_chords[0], bar_chords[-1]
            # Besen: Hat leise auf allen Schlägen, Swing-Achtel auf 2+ und 4+, Snare ganz leise auf 2 und 4
            for e, v in ((0, 22), (2, 28), (3, 16), (4, 22), (6, 28), (7, 16)):
                s.perc(at(bar, e), "hat", 14, v * fade)
            for e in (2, 6):
                s.perc(at(bar, e), "snare", 6, 14 * fade)
            # Bass in Halben (Grundton, Quinte), mit Durchgang auf 4+
            r1, iv1 = chord(first)
            r2, iv2 = chord(second)
            root1 = 38 + ((r1 - 2) % 12)
            root2 = 38 + ((r2 - 2) % 12)
            if root1 > 49:
                root1 -= 12
            if root2 > 49:
                root2 -= 12
            s.note(at(bar, 0), "bass", root1, 82 * fade)
            s.note(at(bar, 4), "bass", root2 + (iv2[2] if first == second else 0), 74 * fade)
            s.note(at(bar, 7), "bass", root2 + (iv2[2] if first == second else 0) - 1, 40 * fade)
            # E-Piano (Pling): lange Akkorde auf 1 und 3
            for e, sym in ((0, first), (4, second)):
                for m in voicing(sym, 55, 72):
                    s.note(at(bar, e), "pling", m, 28 * fade)
            if part == "A":
                for e, n, ln in themeA[bi]:
                    s.note(at(bar, e), "iron_xylophone", midi(n), 74)
                    if ln >= 4:
                        s.note(at(bar, e + 2), "iron_xylophone", midi(n), 34)
            elif part == "B":
                for e, n, ln in themeB[bi]:
                    s.note(at(bar, e), "flute", midi(n), 64)
            elif part == "solo":
                cur = 69
                pos = 0
                while pos < 8:
                    ln = rnd.choice([1, 1, 2, 2, 3])
                    if rnd.random() < 0.18:
                        pos += ln
                        continue
                    sym = first if pos < 4 else second
                    r, steps = scale_for(sym)
                    cands = [60 + r + st + 12 * k for st in steps for k in (-1, 0, 1) if 57 <= 60 + r + st + 12 * k <= 77]
                    cur = min(cands, key=lambda c: abs(c - cur) + rnd.random() * 5)
                    s.note(at(bar, pos), "iron_xylophone", cur, 60)
                    pos += ln
            bar += 1
    end = bar * s.bar
    for m in voicing("Dm9", 55, 74):
        s.note(end, "pling", m, 34)
    s.note(end, "bass", 38, 70)
    s.note(end, "iron_xylophone", 74, 40)
    return s, end + 56


SONGS = [("brisk", brisk), ("midnight", midnight), ("latenight", latenight)]


def build(res):
    out = os.path.join(res, "casino", "songs")
    os.makedirs(out, exist_ok=True)
    for sid, fn in SONGS:
        s, length = fn()
        s.write(os.path.join(out, sid + ".txt"), length)
        print("ok %s: %s, %d Ticks (%d:%02d), %d Noten" % (sid, s.name, length, length // 20 // 60, length // 20 % 60,
                                                            len(s.events)))


if __name__ == "__main__":
    build(sys.argv[1])
