package de.kuby.kubyskyblock.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Speichern ohne Ruckler: Die Daten werden auf dem Haupt-Thread in eine neue YamlConfiguration kopiert (schnell), das
 * Umwandeln in Text und Schreiben läuft im Hintergrund auf einem einzigen Thread (Reihenfolge bleibt, eine ältere
 * Fassung überschreibt nie eine neuere). Geschrieben wird erst in eine .tmp-Datei, dann umbenannt: bricht der Server
 * mitten im Schreiben ab, bleibt die alte Datei ganz. Beim Abschalten (Plugin nicht mehr aktiv) wird sofort geschrieben,
 * nachdem alles Offene fertig ist.
 */
public final class DataFiles {

    private static ExecutorService io;

    private DataFiles() {
    }

    private static synchronized ExecutorService io() {
        if (io == null || io.isShutdown()) {
            io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "KubySkyblock-Speichern");
                t.setDaemon(true);
                return t;
            });
        }
        return io;
    }

    /** out darf danach nicht mehr verändert werden (immer eine frisch gebaute Konfiguration übergeben). */
    public static void save(JavaPlugin plugin, YamlConfiguration out, File file) {
        if (plugin.isEnabled()) {
            io().execute(() -> write(plugin, out, file));
        } else {
            flush();
            write(plugin, out, file);
        }
    }

    /** Wartet, bis alle offenen Schreibvorgänge fertig sind (höchstens 20 Sekunden). */
    public static void flush() {
        ExecutorService ex;
        synchronized (DataFiles.class) {
            ex = io;
        }
        if (ex == null || ex.isShutdown()) return;
        try {
            ex.submit(() -> { }).get(20, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Abschalten geht trotzdem weiter
        }
    }

    /** Beim Abschalten: offene Schreibvorgänge abwarten, Thread beenden. */
    public static void shutdown() {
        flush();
        synchronized (DataFiles.class) {
            if (io != null) io.shutdown();
            io = null;
        }
    }

    private static void write(JavaPlugin plugin, YamlConfiguration out, File file) {
        try {
            String data = out.saveToString();
            File dir = file.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning(file.getName() + " konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
