#!/usr/bin/env python3
"""Sprachaufnahmen für den Casino-Rundgang (Vincent, ElevenLabs) ins Pack übernehmen.

Aufruf: python3 tools/add_tour_voice.py <Ordner>   (Dateien <id>.mp3/.wav/.ogg, id z. B. intro, kasse, blackjack …)
Schreibt resourcepack/KubyUI/assets/kuby/sounds/tour/<id>.ogg (mono, leise Ränder abgeschnitten) und trägt
"tour.<id>" in assets/kuby/sounds.json ein (stream, damit lange Sätze nicht ruckeln). Gespielt in CasinoTour.
"""
import json
import os
import subprocess
import sys

PACK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack", "KubyUI", "assets", "kuby")


def main():
    src = sys.argv[1]
    out = os.path.join(PACK, "sounds", "tour")
    os.makedirs(out, exist_ok=True)
    sj = os.path.join(PACK, "sounds.json")
    data = json.load(open(sj, encoding="utf-8")) if os.path.exists(sj) else {}
    for f in sorted(os.listdir(src)):
        name, ext = os.path.splitext(f)
        if ext.lower() not in (".mp3", ".wav", ".ogg", ".flac"):
            continue
        dest = os.path.join(out, name + ".ogg")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", os.path.join(src, f), "-ac", "1", "-ar", "44100",
                        "-af", "silenceremove=start_periods=1:start_threshold=-50dB,areverse,"
                               "silenceremove=start_periods=1:start_threshold=-50dB,areverse,loudnorm=I=-16:TP=-1.5",
                        "-c:a", "libvorbis", "-q:a", "4", dest], check=True)
        data["tour." + name] = {"sounds": [{"name": "kuby:tour/" + name, "stream": True}]}
        print(name, os.path.getsize(dest) // 1024, "KB")
    with open(sj, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)
    # Liste für das Plugin: nur diese Stationen bekommen Stimme (sonst „unknown soundEvent“ beim Client)
    ids = sorted(k[len("tour."):] for k in data if k.startswith("tour."))
    res = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "KubyCore-v2.0.14", "KubyPlugins", "src", "main",
                       "resources", "casino", "tour-voice.txt")
    with open(res, "w", encoding="utf-8") as fh:
        fh.write("\n".join(ids) + "\n")


if __name__ == "__main__":
    main()
