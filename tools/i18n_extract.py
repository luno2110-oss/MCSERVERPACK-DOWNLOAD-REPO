#!/usr/bin/env python3
"""Sammelt alle deutschen Text-Vorlagen aus dem Java-Code der Kuby-Plugins (für die englische Übersetzung).

Eine Vorlage ist eine Kette aus Text-Bausteinen und Werten, wie sie im Code steht: "<green>Tag " + day + " abgeholt"
wird zu "<green>Tag {} abgeholt". Werte am Anfang und Ende fallen weg (sie gehören nicht zum festen Text). Bedingte
Ausdrücke (en ? "a" : "b") sind Werte; ihre Texte werden eigene Vorlagen.

Aufruf: python3 tools/i18n_extract.py [ausgabe.tsv]
Ausgabe: je Zeile eine Vorlage (Tabs und Zeilenumbrüche als \\t und \\n), sortiert; dazu, wie viele schon in der
Übersetzung (KubyCore …/resources/lang/en.tsv) stehen.
"""
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOTS = ["KubyCore-v2.0.14", "KubySMP-v1.0.2", "KubySurvival-v1.0.0", "KubySkyblock-v1.2.0", "KubyJR-Addon-1.0.2",
         "KubyScoreboard-v1.0.2"]
EN = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/resources/lang/en.tsv")

TOKEN = re.compile(r'''
    (?P<ws>\s+)
  | (?P<lc>//[^\n]*)
  | (?P<bc>/\*.*?\*/)
  | (?P<tb>"""(?:\\.|[^\\])*?""")
  | (?P<str>"(?:\\.|[^"\\\n])*")
  | (?P<chr>'(?:\\.|[^'\\\n])+')
  | (?P<num>\d[\w.]*)
  | (?P<id>[A-Za-z_$][\w$]*)
  | (?P<op>>>>=|<<=|>>=|->|::|\+\+|--|&&|\|\||[+\-*/%=!<>&|^~?:]=?|[(){}\[\];,.@])
''', re.S | re.X)

ESC = {"n": "\n", "t": "\t", "\"": "\"", "'": "'", "\\": "\\", "r": "\r", "b": "\b", "f": "\f", "0": "\0"}


def unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "u":
                j = i + 1
                while j < len(s) and s[j] == "u":
                    j += 1
                out.append(chr(int(s[j:j + 4], 16)))
                i = j + 4
                continue
            out.append(ESC.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def tokens(src):
    toks = []
    pos = 0
    while pos < len(src):
        m = TOKEN.match(src, pos)
        if not m:
            pos += 1
            continue
        pos = m.end()
        kind = m.lastgroup
        if kind in ("ws", "lc", "bc"):
            continue
        val = m.group(kind)
        if kind == "str":
            toks.append(("str", unescape(val[1:-1])))
        elif kind == "tb":
            toks.append(("str", unescape(val[3:-3])))
        else:
            toks.append((kind, val))
    return toks


OPEN = {"(": ")", "[": "]", "{": "}"}
CLOSE = {v: k for k, v in OPEN.items()}
STOP = {",", ";", "?", ":", "=", "->", "return", "&&", "||", "==", "!=", "+=", "{", "}", "case", "default", "else",
        "throw", "new"}


def operand_left(toks, i):
    """Anfang des Operanden, der bei Index i endet (i = letzter Token des Operanden)."""
    j = i
    while j >= 0:
        k, v = toks[j]
        if v in CLOSE:
            depth = 0
            while j >= 0:
                if toks[j][1] in CLOSE:
                    depth += 1
                elif toks[j][1] in OPEN:
                    depth -= 1
                    if depth == 0:
                        break
                j -= 1
        # davor ein Name (Methodenaufruf) oder ein Punkt-Zugriff?
        if j - 1 >= 0 and (toks[j - 1][0] == "id" or toks[j - 1][1] == "."):
            j -= 1
            continue
        if j - 1 >= 0 and toks[j - 1][1] in CLOSE and toks[j][1] == ".":
            j -= 1
            continue
        if toks[j][1] == "." and j - 1 >= 0:
            j -= 1
            continue
        if j - 1 >= 0 and toks[j - 1][1] == "new":
            j -= 1
        return j
    return 0


def operand_right(toks, i):
    """Ende des Operanden, der bei Index i beginnt."""
    j = i
    n = len(toks)
    if toks[j][1] in ("-", "!", "(") and toks[j][1] != "(":
        j += 1
    while j < n:
        k, v = toks[j]
        if v in OPEN:
            depth = 0
            while j < n:
                if toks[j][1] in OPEN:
                    depth += 1
                elif toks[j][1] in CLOSE:
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
        if j + 1 < n and (toks[j + 1][1] in (".", "(", "[") or (toks[j][1] == "." and toks[j + 1][0] == "id")):
            j += 1
            continue
        return j
    return n - 1


LOGS = {"warning", "info", "severe", "fine", "finer", "debug", "log", "println", "printf"}


def console_only(toks, start):
    """Text für die Konsole oder eine Exception (sieht kein Spieler): logger.warning("…"), new XyzException("…")."""
    if start >= 2 and toks[start - 1][1] == "(":
        name = toks[start - 2][1]
        if name in LOGS:
            return True
        if name.endswith("Exception") or name.endswith("Error"):
            return True
    return False


def chains(toks):
    """Alle Ketten mit mindestens einem Text: Liste von Teilen (str oder None für einen Wert)."""
    seen = set()
    out = []
    for i, (k, v) in enumerate(toks):
        if k != "str" or i in seen:
            continue
        # nach links bis zum Anfang der Kette
        start = i
        while start - 1 >= 0 and toks[start - 1][1] == "+":
            s = operand_left(toks, start - 2)
            if s < 0:
                break
            start = s
        parts = []
        j = start
        while True:
            e = operand_right(toks, j)
            if e == j and toks[j][0] == "str":
                parts.append(toks[j][1])
                seen.add(j)
            else:
                parts.append(None)
            if e + 1 < len(toks) and toks[e + 1][1] == "+" and e + 2 < len(toks):
                j = e + 2
                continue
            break
        if not console_only(toks, start):
            out.append(parts)
    return out


def template(parts):
    """Teile zu einer Vorlage; Werte am Rand fallen weg, benachbarte Texte werden verbunden."""
    merged = []
    for p in parts:
        if p is None:
            if merged and merged[-1] is None:
                continue
            merged.append(None)
        elif merged and merged[-1] is not None:
            merged[-1] += p
        else:
            merged.append(p)
    while merged and merged[0] is None:
        merged.pop(0)
    while merged and merged[-1] is None:
        merged.pop()
    return "".join("{}" if p is None else p for p in merged)


TAG = re.compile(r"<[^<>]*>")
GERMAN = re.compile(r"[äöüÄÖÜß]|\b(der|die|das|den|dem|des|ein|eine|einen|einem|einer|und|oder|nicht|kein|keine|keinen|"
                    r"ist|sind|bist|hast|hat|haben|wird|werden|wurde|du|dein|deine|deinen|deiner|dir|dich|ich|wir|ihr|"
                    r"sie|mit|für|von|vom|zu|zum|zur|im|auf|aus|bei|nach|noch|schon|jetzt|hier|dass|wenn|als|auch|nur|"
                    r"mehr|alle|alles|klick|klicke|seite|stufe|spieler|abholen|kaufen|verkaufen|zurück|weiter|öffnen|"
                    r"schließen|gesetzt|gelöscht|entfernt|erhalten|gewonnen|verloren|nutzung|befehl|kosten|preis|"
                    r"menge|einstellungen|belohnung|belohnungen|aufgaben|heute|morgen|tage|tag|stunden|minuten|"
                    r"sekunden|bitte|leider|gibt|kannst|musst|darfst|willst|bereits|neu|alt|gold|juwelen|schlüssel|"
                    r"kiste|kisten|truhe|seite|liste|freunde|anfrage|anfragen|teleport|sperre|gesperrt|frei|voll|"
                    r"leer|offen|geschlossen|an|aus|ja|nein|wählen|wähle|ausgewählt|bestätigen|abbrechen|fehler|"
                    r"erfolgreich|benötigt|braucht|gekauft|verkauft|eingelöst|angenommen|abgelehnt|gestartet|"
                    r"beendet|läuft|verfügbar|nicht|unbekannt|ungültig|ungültige|erlaubt|verboten|online|offline|"
                    r"rang|ränge|stufen|punkte|monat|woche|wochen|jahr|jahre|immer|nie|sofort|später|bald|warte|"
                    r"wartet|spielzeit|kopfgeld|duell|turnier|handel|markt|bestellung|bestellungen|lieferung|kasse|"
                    r"bank|konto|einsatz|gewinn|verlust|runde|karte|karten|tisch|platz|plätze|home|homes)\b",
                    re.I)
IDENT = re.compile(r"^[\w.:/#%-]*$")


def user_facing(t):
    plain = TAG.sub("", t).replace("{}", " ")
    if not re.search(r"[A-Za-zÄÖÜäöüß]{2,}", plain):
        return False
    if IDENT.match(t) and not re.search(r"[äöüÄÖÜß]", t):
        return False          # Schlüssel, Pfade, Rechte, Materialnamen
    if re.match(r"^[a-z_.]+(\.[a-z_]+)+$", t):
        return False
    return bool(GERMAN.search(plain))


def collect():
    found = {}
    for root in ROOTS:
        for dp, _, fs in os.walk(os.path.join(REPO, root)):
            if "/test/" in dp:
                continue
            for f in fs:
                if not f.endswith(".java"):
                    continue
                path = os.path.join(dp, f)
                src = open(path, encoding="utf-8").read()
                toks = tokens(src)
                for parts in chains(toks):
                    t = template(parts)
                    if t and user_facing(t):
                        found.setdefault(t, os.path.relpath(path, REPO))
    return found


def load_en(path=EN):
    have = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if not line or line.startswith("#") or "\t" not in line:
                continue
            de, en = line.split("\t", 1)
            have[de] = en
    return have


def enc(s):
    return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")


if __name__ == "__main__":
    found = collect()
    have = load_en()
    out = sys.argv[1] if len(sys.argv) > 1 else None
    missing = [t for t in sorted(found) if enc(t) not in have]
    print(len(found), "Vorlagen,", len(found) - len(missing), "übersetzt,", len(missing), "offen")
    if out:
        with open(out, "w", encoding="utf-8") as f:
            for t in sorted(found, key=lambda x: (found[x], x)):
                f.write(enc(t) + "\t" + found[t] + "\n")
