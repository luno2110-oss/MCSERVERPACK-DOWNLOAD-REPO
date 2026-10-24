#!/bin/bash
# 3D-Vorschau der Casino-Anzeigen (nur zum Entwickeln).
# Aufruf: tools/preview3d/run.sh <slots|blackjack|roulette> <out.json>
# Braucht: KUBY_API = Ordner mit den API-Jars (Release "api-jars"), KUBY_CLASSES = kompilierte KubyCore-Klassen.
# Danach: KUBY_VANILLA=<vanilla-assets> python3 tools/preview3d/r3d.py <out.json> bild.png "cam=0.5,1.6,3" "look=0.5,1.2,0"
set -e
D=$(cd "$(dirname "$0")" && pwd)
CP=$(ls "$KUBY_API"/*.jar | tr '\n' ':')
OUT=${KUBY_PREVIEW_CLASSES:-/tmp/kuby-preview3d}
mkdir -p "$OUT"
javac -proc:none -nowarn -d "$OUT" -cp "$CP$KUBY_CLASSES" $(find "$D/shadow" "$D/src" -name "*.java") 2>&1 | grep -v "JAVA_TOOL\|^Note" || true
java -cp "$OUT:$KUBY_CLASSES:$CP" h3d.Scene "$@" 2>&1 | grep -v JAVA_TOOL
