#!/usr/bin/env python3
"""Echter Minecraft-Java-Client für Bildschirmfotos aus dem Spiel in der CI (Vanilla, mit --shader auch Fabric + Sodium
+ Iris und ein Shaderpack von Modrinth, das gleich eingeschaltet ist).

Lädt Client-Jar, Bibliotheken (mit LWJGL-Natives für Linux) und den Asset-Index von Mojang, schreibt options.txt
(keine Pause ohne Fokus, kein Barrierefreiheits-Dialog, leise) und servers.dat mit dem Testserver, für den das
Server-Resource-Pack (KubyUI) ohne Rückfrage angenommen wird. Gibt die Startzeile als launch.sh aus.

Aufruf: python3 prepare.py <Version> <Zielordner> <Server-Adresse> [--shader <Modrinth-Shaderpack>]
"""
import concurrent.futures
import hashlib
import json
import os
import struct
import sys
import urllib.parse
import urllib.request
import uuid

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def fetch(url, dest=None):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "KubyPlugins-CI"}), timeout=60) as r:
        data = r.read()
    if dest:
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as f:
            f.write(data)
    return data


def allowed(rules):
    """Mojang-Regeln für Linux auswerten (ohne Features)."""
    if not rules:
        return True
    ok = False
    for r in rules:
        if "features" in r:
            continue
        os_ = r.get("os", {})
        match = not os_ or os_.get("name") == "linux"
        if match:
            ok = r["action"] == "allow"
    return ok


# ---------- NBT (unkomprimiert) für servers.dat ----------

def _s(t):
    b = t.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def servers_dat(path, address):
    entry = b"\x08" + _s("ip") + _s(address)
    entry += b"\x08" + _s("name") + _s("Kuby Test")
    entry += b"\x01" + _s("acceptTextures") + b"\x01"     # Server-Pack ohne Rückfrage annehmen
    entry += b"\x01" + _s("hidden") + b"\x00"
    entry += b"\x00"
    body = b"\x09" + _s("servers") + b"\x0a" + struct.pack(">i", 1) + entry
    data = b"\x0a" + _s("") + body + b"\x00"
    with open(path, "wb") as f:
        f.write(data)


def modrinth(slug, version, loaders, dest_dir):
    """Neueste Version eines Modrinth-Projekts (Mod oder Shaderpack) laden. version None: ohne Spielversion filtern."""
    q = {"loaders": json.dumps(loaders)}
    if version:
        q["game_versions"] = json.dumps([version])
    vs = json.loads(fetch("https://api.modrinth.com/v2/project/%s/version?%s" % (slug, urllib.parse.urlencode(q))))
    if not vs:
        raise SystemExit("Modrinth: keine Version von %s für %s %s" % (slug, version or "-", loaders))
    pick = ([v for v in vs if v.get("version_type") == "release"] or vs)[0]
    f = next((x for x in pick["files"] if x.get("primary")), pick["files"][0])
    dest = os.path.join(dest_dir, f["filename"])
    if not os.path.exists(dest):
        fetch(f["url"], dest)
    print("Modrinth %s: %s (%s)" % (slug, f["filename"], pick.get("version_number")))
    return f["filename"]


def fabric(version, lib_dir):
    """Fabric-Loader-Profil: Bibliotheken (vor die Vanilla-Bibliotheken), Hauptklasse und JVM-Argumente."""
    loaders = json.loads(fetch("https://meta.fabricmc.net/v2/versions/loader/%s" % version))
    loader = next((x for x in loaders if x["loader"].get("stable")), loaders[0])["loader"]["version"]
    prof = json.loads(fetch("https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json" % (version, loader)))
    cp = []
    for lib in prof["libraries"]:
        group, art, ver = lib["name"].split(":")[:3]
        path = "%s/%s/%s/%s-%s.jar" % (group.replace(".", "/"), art, ver, art, ver)
        dest = os.path.join(lib_dir, path)
        if not os.path.exists(dest):
            fetch(lib.get("url", "https://maven.fabricmc.net/").rstrip("/") + "/" + path, dest)
        cp.append(dest)
    jvm = [a for a in prof.get("arguments", {}).get("jvm", []) if isinstance(a, str)]
    print("Fabric-Loader", loader)
    return cp, prof["mainClass"], jvm


def main():
    version, out, address = sys.argv[1], sys.argv[2], sys.argv[3]
    shader = sys.argv[sys.argv.index("--shader") + 1] if "--shader" in sys.argv else None
    os.makedirs(out, exist_ok=True)
    man = json.loads(fetch(MANIFEST))
    v = next(x for x in man["versions"] if x["id"] == version)
    vj = json.loads(fetch(v["url"]))
    lib_dir = os.path.join(out, "libraries")
    cp = []
    for lib in vj["libraries"]:
        if not allowed(lib.get("rules")):
            continue
        art = lib.get("downloads", {}).get("artifact")
        if not art:
            continue
        dest = os.path.join(lib_dir, art["path"])
        if not os.path.exists(dest):
            fetch(art["url"], dest)
        cp.append(dest)
    client = os.path.join(out, "versions", version, version + ".jar")
    if not os.path.exists(client):
        fetch(vj["downloads"]["client"]["url"], client)
    cp.append(client)
    # Asset-Index und alle Objekte (Sprachen, Töne, eingebaute Packs; der Client stürzt ab, wenn welche fehlen)
    idx = vj["assetIndex"]
    idx_path = os.path.join(out, "assets", "indexes", idx["id"] + ".json")
    index = json.loads(fetch(idx["url"], idx_path))

    def obj(h):
        dest = os.path.join(out, "assets", "objects", h[:2], h)
        if not os.path.exists(dest):
            for attempt in range(4):
                try:
                    fetch("https://resources.download.minecraft.net/%s/%s" % (h[:2], h), dest)
                    break
                except Exception:
                    if attempt == 3:
                        raise
    hashes = sorted({o["hash"] for o in index["objects"].values()})
    with concurrent.futures.ThreadPoolExecutor(32) as ex:
        list(ex.map(obj, hashes))
    print("Assets:", len(hashes))
    game = os.path.join(out, "game")
    os.makedirs(game, exist_ok=True)
    with open(os.path.join(game, "options.txt"), "w") as f:
        f.write("\n".join([
            "onboardAccessibility:false",
            "pauseOnLostFocus:false",
            "skipMultiplayerWarning:true",
            "tutorialStep:none",
            "joinedFirstServer:true",
            "lang:de_de",
            "renderDistance:6",
            "simulationDistance:5",
            "maxFps:30",
            "graphicsMode:1",
            "guiScale:2",
            "fov:0.0",
            "renderClouds:\"false\"",
            "narrator:0",
            "soundCategory_master:0.8",   # Ton für die Videos (virtuelle Soundkarte in der CI)
            "soundCategory_music:0.0",
            "soundCategory_ambient:0.2",
            "soundCategory_weather:0.0",
            "chatOpacity:0.0",
            "chatVisibility:2",
            "showSubtitles:false",
            "realmsNotifications:false",
            "",
        ]))
    servers_dat(os.path.join(game, "servers.dat"), address)
    main_class, jvm = vj["mainClass"], []
    if shader:
        # Fabric + Fabric API + Sodium + Iris, Shaderpack gleich an (config/iris.properties)
        fcp, main_class, jvm = fabric(version, lib_dir)
        cp = fcp + cp
        mods = os.path.join(game, "mods")
        os.makedirs(mods, exist_ok=True)
        for slug in ("fabric-api", "sodium", "iris"):
            modrinth(slug, version, ["fabric"], mods)
        packs = os.path.join(game, "shaderpacks")
        os.makedirs(packs, exist_ok=True)
        pack = modrinth(shader, None, ["iris"], packs)
        os.makedirs(os.path.join(game, "config"), exist_ok=True)
        with open(os.path.join(game, "config", "iris.properties"), "w") as f:
            f.write("enableShaders=true\nshaderPack=%s\nenableDebugOptions=false\ndisableUpdateMessage=true\n" % pack)
        with open(os.path.join(game, "options.txt"), "a") as f:
            f.write("renderDistance:4\nsimulationDistance:5\n")
    name = "Kamera"
    uid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3).hex
    args = ["java", "-Xmx3G" if shader else "-Xmx2G", "-Dorg.lwjgl.util.NoChecks=true"] + jvm + ["-cp", ":".join(cp), main_class,
            "--username", name, "--version", version, "--gameDir", game, "--assetsDir", os.path.join(out, "assets"),
            "--assetIndex", idx["id"], "--uuid", uid, "--accessToken", "0", "--userType", "legacy",
            "--versionType", "release", "--width", "1600", "--height", "900", "--quickPlayMultiplayer", address]
    with open(os.path.join(out, "launch.sh"), "w") as f:
        f.write("#!/bin/bash\nexec " + " ".join("'%s'" % a for a in args) + "\n")
    os.chmod(os.path.join(out, "launch.sh"), 0o755)
    print("Client %s bereit: %d Bibliotheken, Index %s" % (version, len(cp) - 1, idx["id"]))


if __name__ == "__main__":
    main()
