#!/usr/bin/env python3
"""Sprachzeilen der Casino-Figuren (v2.07, Wunsch des Besitzers: „einzelne Voicelines so wie in GTA“) ins Pack übernehmen.

Je Stimme ein Take (ElevenLabs, alle Sätze mit Leerzeile getrennt, siehe LINES). Das Werkzeug schneidet den Take an den
N-1 längsten Pausen in N Sätze, macht daraus mono-OGG (Ränder gekürzt, Lautheit angeglichen) unter
assets/kuby/sounds/voice/<stimme>_<n>.ogg, trägt „voice.<stimme>_<n>“ in sounds.json ein und schreibt die Liste
resources/casino/voice.txt (Schlüssel|Stimme|Text) für das Plugin: People.say() spielt den Satz, wenn der Text passt.

Aufruf: python3 tools/add_npc_voice.py <Ordner>   (Unterordner je Stimme mit take.mp3, z. B. gast_m/take.mp3)
"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, "..", "resourcepack", "KubyUI", "assets", "kuby")
RES = os.path.join(HERE, "..", "KubyCore-v2.0.14", "KubyPlugins", "src", "main", "resources", "casino", "voice.txt")

# Stimme -> Sätze in Take-Reihenfolge (Texte genau wie in People.java bzw. CasinoNpc, sonst spielt kein Ton)
LINES = {
    "sec": ["Guten Abend.", "Hey! Nicht rennen!", "Alles ruhig am Eingang.", "Verstanden. Bin auf Position.",
            "Willkommen im Casino."],
    "gast_m": ["Prost!", "Jaaa! Na also!", "Ohh … knapp daneben.", "Hahaha, nicht dein Ernst!", "Ein Bier, bitte!",
               "Ja, Schatz, ich bin gleich zu Hause!", "Wow! Nicht schlecht!"],
    "gast_w": ["Prost!", "Wahnsinn!", "Wo bleiben die denn?", "Zwei Martini, bitte!", "Hahaha, hör auf!",
               "Da seid ihr ja! Endlich!"],
    "pech": ["Alles weg … die ganze Miete.", "Hey … hast du ein paar Chips übrig?", "Nur noch eine Runde, dann hol ich alles zurück.",
             "Gib mir was ab!"],
    "croupier": ["Faites vos jeux!", "Rien ne va plus.", "Bonsoir. Ein Platz ist noch frei."],
    "kasse": ["Willkommen! Chips gefällig?", "Hallo! Coins rein, Chips raus."],
    "losbude": ["Lose, Lose! Heute schon Glück gehabt?", "Hauptgewinn noch nicht gezogen!", "Wer wagt, gewinnt!"],
}


# Feste Schnittpunkte (Sekunden) für Takes, in denen Lachen oder gedehnte Wörter eigene Pausen haben; abgeleitet aus
# der Pausenlage (silencedetect) und der Silbenzahl je Satz. Ohne Eintrag: die n-1 längsten Pausen.
CUTS = {
    "gast_m": [0.83, 3.14, 5.40, 7.89, 9.67, 11.65],
    "gast_w": [0.87, 1.97, 3.39, 5.04, 7.22],
    "pech": [3.20, 6.00, 8.99],
    "croupier": [1.50, 3.50],
    "kasse": [1.73],
    "losbude": [2.40, 4.30],
}


def silences(path):
    out = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af", "silencedetect=noise=-38dB:d=0.12", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", out)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", out)]
    dur = float(re.search(r"Duration: (\d+):(\d+):([\d.]+)", out).group(3)) + 60 * float(
        re.search(r"Duration: (\d+):(\d+):", out).group(2))
    return list(zip(starts, ends + [dur] * (len(starts) - len(ends)))), dur


def split(path, n, voice=None):
    """Schnittpunkte (fest aus CUTS oder Mitte der n-1 längsten inneren Pausen)."""
    sil, dur = silences(path)
    if voice in CUTS:
        bounds = [0.0] + CUTS[voice] + [dur]
        return list(zip(bounds[:-1], bounds[1:]))
    inner = [(e - s, (s + e) / 2) for s, e in sil if s > 0.15 and e < dur - 0.1]
    cuts = sorted(m for _, m in sorted(inner, reverse=True)[:n - 1])
    if len(cuts) != n - 1:
        raise SystemExit("%s: %d Pausen gefunden, %d gebraucht" % (path, len(cuts), n - 1))
    bounds = [0.0] + cuts + [dur]
    return list(zip(bounds[:-1], bounds[1:]))


def main():
    src = sys.argv[1]
    out = os.path.join(PACK, "sounds", "voice")
    os.makedirs(out, exist_ok=True)
    sj = os.path.join(PACK, "sounds.json")
    data = json.load(open(sj, encoding="utf-8")) if os.path.exists(sj) else {}
    rows = []
    for voice, lines in LINES.items():
        take = os.path.join(src, voice, "take.mp3")
        if not os.path.exists(take):
            print("fehlt:", voice)
            continue
        for i, (a, b) in enumerate(split(take, len(lines), voice)):
            key = "%s_%d" % (voice, i + 1)
            dest = os.path.join(out, key + ".ogg")
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", "%.3f" % a, "-to", "%.3f" % b, "-i", take,
                            "-ac", "1", "-ar", "44100", "-af",
                            "silenceremove=start_periods=1:start_threshold=-45dB,areverse,"
                            "silenceremove=start_periods=1:start_threshold=-45dB,areverse,loudnorm=I=-17:TP=-1.5",
                            "-c:a", "libvorbis", "-q:a", "3", dest], check=True)
            data["voice." + key] = {"sounds": [{"name": "kuby:voice/" + key, "attenuation_distance": 14}]}
            rows.append("%s|%s|%s" % (key, voice, lines[i]))
            print(key, "%.2f-%.2f" % (a, b), os.path.getsize(dest) // 1024, "KB", lines[i])
    with open(sj, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)
    os.makedirs(os.path.dirname(RES), exist_ok=True)
    old = []
    if os.path.exists(RES):
        have = {r.split("|")[0] for r in rows}
        old = [l.rstrip("\n") for l in open(RES, encoding="utf-8") if l.strip() and l.split("|")[0] not in have]
    with open(RES, "w", encoding="utf-8") as fh:
        fh.write("\n".join(sorted(old + rows)) + "\n")


if __name__ == "__main__":
    main()
