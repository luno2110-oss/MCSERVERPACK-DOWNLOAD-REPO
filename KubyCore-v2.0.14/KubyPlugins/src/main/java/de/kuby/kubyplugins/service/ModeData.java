package de.kuby.kubyplugins.service;

import java.util.Map;
import java.util.UUID;

/**
 * Daten, die pro Spielmodus getrennt sind. Export liest den aktuellen Stand eines Spielers,
 * Import setzt ihn. Import mit null setzt auf den Anfangszustand zurück.
 * Werte nur als einfache Typen, Listen oder Strings, Schlüssel ohne Punkte (Trennzeichen "|").
 */
public interface ModeData {

    String key();

    Map<String, Object> export(UUID id);

    void importData(UUID id, Map<String, Object> data);

    static long lng(Object o) { return o instanceof Number n ? n.longValue() : 0L; }

    static int integer(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    static double dbl(Object o) { return o instanceof Number n ? n.doubleValue() : 0.0; }
}
