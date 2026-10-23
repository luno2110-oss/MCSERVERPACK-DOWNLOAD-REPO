package de.kp.jumprun.holo;

import java.util.UUID;

/**
 * Eine Zeile der Bestenliste.
 */
public record Row(UUID uuid, String name, String value) {
}
