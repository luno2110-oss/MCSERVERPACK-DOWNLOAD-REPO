package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Automatische Backups der Spielerdaten: alle backup.intervall-minuten (60) ein ZIP in plugins/KubyPlugins/backups mit
 * den Daten aller Kuby-Plugins (plugins/Kuby*) und den Spielerdaten der Hauptwelt (playerdata, stats, advancements:
 * Inventar und Position im aktuellen Modus). Welten selbst sind zu groß, die sichert der Hoster.
 * <p>
 * Vorher wird alles gespeichert. Aufbewahrung: die letzten backup.behalten (48) und dazu eines je Tag für backup.tage
 * (14) Tage. Wiederherstellen geht nur beim Neustart: /kp backup zurueck merkt das Backup vor, beim nächsten Start
 * spielt der Kern es ein, bevor irgendein Plugin seine Daten lädt (vorher wird der aktuelle Stand noch einmal gesichert).
 */
public final class BackupService {

    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final String MARKER = "wiederherstellen.txt";
    private static final long MAX_FILE = 64L * 1024 * 1024;

    private final KubyPlugins plugin;
    private final File dir;
    private volatile boolean running;
    private volatile String last = "noch keins";
    private long nextAt;

    public BackupService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "backups");
        long every = Math.max(10, plugin.getConfig().getLong("backup.intervall-minuten", 60)) * 60L * 20L;
        // erstes Backup 10 Minuten nach dem Start, dann im Takt
        nextAt = System.currentTimeMillis() + 10 * 60_000L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("backup.aktiv", true)) return;
            nextAt = System.currentTimeMillis() + every / 20 * 1000L;
            run(null, null);
        }, 12_000L, every);
    }

    public File folder() {
        return dir;
    }

    public boolean running() {
        return running;
    }

    public String last() {
        return last;
    }

    public long nextAt() {
        return nextAt;
    }

    /** Backups, neueste zuerst. */
    public List<File> list() {
        File[] fs = dir.listFiles((d, n) -> n.startsWith("kuby-") && n.endsWith(".zip"));
        List<File> out = new ArrayList<>(fs == null ? List.of() : List.of(fs));
        out.sort(Comparator.comparing(File::getName).reversed());
        return out;
    }

    private static File serverRoot(KubyPlugins plugin) {
        return plugin.getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
    }

    /**
     * Backup jetzt (Speichern auf dem Hauptthread, Packen im Hintergrund). grund landet im Dateinamen (z. B.
     * „manuell“). done bekommt eine Meldung. Gibt null zurück, wenn es losging, sonst den Grund.
     */
    public String run(String grund, Consumer<String> done) {
        if (running) return "Es läuft gerade schon ein Backup.";
        running = true;
        try {
            plugin.saveData();
            for (Player p : Bukkit.getOnlinePlayers()) p.saveData();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[Backup] Speichern vorher: " + ex);
        }
        File root = serverRoot(plugin);
        List<File> sources = sources(root);
        String name = "kuby-" + LocalDateTime.now().format(NAME) + (grund == null || grund.isBlank() ? ""
                : "-" + grund.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")) + ".zip";
        File target = new File(dir, name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String msg;
            try {
                dir.mkdirs();
                int n = zip(root, sources, target);
                prune();
                msg = "Backup " + name + " (" + n + " Dateien, " + mb(target.length()) + ")";
                last = name;
                plugin.getLogger().info("[Backup] " + msg);
            } catch (IOException ex) {
                msg = "Backup fehlgeschlagen: " + ex.getMessage();
                plugin.getLogger().warning("[Backup] " + msg);
                if (plugin.alerts() != null) plugin.alerts().raise(AlertService.Kind.BACKUP, null, msg);
                target.delete();
            } finally {
                running = false;
            }
            String m = msg;
            if (done != null) Bukkit.getScheduler().runTask(plugin, () -> done.accept(m));
        });
        return null;
    }

    static String mb(long bytes) {
        return String.format(Locale.GERMANY, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /** Was gesichert wird: Datenordner aller Kuby-Plugins und die Spielerdaten der Hauptwelt. */
    private List<File> sources(File root) {
        List<File> out = new ArrayList<>();
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (!pl.getName().toLowerCase(Locale.ROOT).startsWith("kuby")) continue;
            File f = pl.getDataFolder();
            if (f.isDirectory()) out.add(f.getAbsoluteFile());
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            World w = Bukkit.getWorlds().get(0);
            for (String sub : List.of("playerdata", "stats", "advancements")) {
                File f = new File(w.getWorldFolder(), sub);
                if (f.isDirectory()) out.add(f.getAbsoluteFile());
            }
        }
        return out;
    }

    private int zip(File root, List<File> sources, File target) throws IOException {
        Path base = root.toPath().toAbsolutePath().normalize();
        Path backups = dir.toPath().toAbsolutePath().normalize();
        File tmp = new File(target.getPath() + ".part");
        int count = 0;
        try (OutputStream os = Files.newOutputStream(tmp.toPath()); ZipOutputStream zip = new ZipOutputStream(os)) {
            for (File src : sources) {
                List<Path> files;
                try (Stream<Path> walk = Files.walk(src.toPath())) {
                    files = walk.filter(Files::isRegularFile).toList();
                }
                for (Path f : files) {
                    Path abs = f.toAbsolutePath().normalize();
                    if (abs.startsWith(backups)) continue;
                    String n = abs.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".jar") || n.endsWith(".zip") || n.endsWith(".part") || n.endsWith(".lock")) continue;
                    long size;
                    try {
                        size = Files.size(abs);
                    } catch (IOException ex) {
                        continue;
                    }
                    if (size > MAX_FILE) continue;
                    String entry = base.relativize(abs).toString().replace(File.separatorChar, '/');
                    zip.putNextEntry(new ZipEntry(entry));
                    try (InputStream in = Files.newInputStream(abs)) {
                        in.transferTo(zip);
                    } catch (IOException ex) {
                        // Datei gerade gesperrt oder weg: überspringen
                    }
                    zip.closeEntry();
                    count++;
                }
            }
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return count;
    }

    /** Aufbewahrung: die neuesten backup.behalten, dazu das neueste je Tag für backup.tage Tage. */
    private void prune() {
        int keep = Math.max(1, plugin.getConfig().getInt("backup.behalten", 48));
        int days = Math.max(0, plugin.getConfig().getInt("backup.tage", 14));
        String oldestDay = LocalDateTime.now().minusDays(days).toLocalDate().toString();
        List<File> all = list();
        Set<String> daysKept = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            File f = all.get(i);
            String day = f.getName().substring(5, 15);   // kuby-YYYY-MM-DD
            if (i < keep) {
                daysKept.add(day);
                continue;
            }
            if (day.compareTo(oldestDay) >= 0 && daysKept.add(day)) continue;
            if (!f.delete()) plugin.getLogger().warning("[Backup] Konnte " + f.getName() + " nicht löschen.");
        }
    }

    // =================================================================================================================
    // Wiederherstellen (nur beim Start)
    // =================================================================================================================

    /** Backup für den nächsten Start vormerken. Null bei Erfolg. */
    public String schedule(File backup) {
        try {
            Files.writeString(new File(dir, MARKER).toPath(), backup.getName());
            plugin.getLogger().warning("[Backup] Wiederherstellung von " + backup.getName() + " beim nächsten Start vorgemerkt.");
            return null;
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }

    public String pending() {
        File m = new File(dir, MARKER);
        try {
            return m.isFile() ? Files.readString(m.toPath()).trim() : null;
        } catch (IOException ex) {
            return null;
        }
    }

    public boolean cancel() {
        return new File(dir, MARKER).delete();
    }

    /**
     * Ganz am Anfang von onEnable aufrufen (bevor ein Dienst Daten lädt). Spielt ein vorgemerktes Backup ein: erst
     * Sicherung des aktuellen Stands, dann die Ordner der Kuby-Plugins aus dem Backup leeren und neu füllen,
     * Spielerdaten überschreiben.
     */
    public static void applyPending(KubyPlugins plugin) {
        File dir = new File(plugin.getDataFolder(), "backups");
        File marker = new File(dir, MARKER);
        if (!marker.isFile()) return;
        Logger log = plugin.getLogger();
        try {
            String name = Files.readString(marker.toPath()).trim();
            File zip = new File(dir, name);
            if (!zip.isFile() || name.contains("/") || name.contains("\\")) {
                log.warning("[Backup] Vorgemerktes Backup " + name + " fehlt, nichts wiederhergestellt.");
                return;
            }
            File root = serverRoot(plugin);
            Path base = root.toPath().toAbsolutePath().normalize();
            Path backups = dir.toPath().toAbsolutePath().normalize();
            // 1. aktuellen Stand sichern (die Plugin-Ordner, so wie sie jetzt sind)
            List<File> now = new ArrayList<>();
            File pluginsDir = plugin.getDataFolder().getAbsoluteFile().getParentFile();
            File[] kuby = pluginsDir.listFiles(f -> f.isDirectory() && f.getName().toLowerCase(Locale.ROOT).startsWith("kuby"));
            if (kuby != null) now.addAll(List.of(kuby));
            BackupService tmp = new BackupService(plugin, dir);
            File safety = new File(dir, "kuby-" + LocalDateTime.now().format(NAME) + "-vorwiederherstellung.zip");
            tmp.zip(root, now, safety);
            // 2. welche Plugin-Ordner stecken im Backup? Diese leeren (außer den Backups selbst)
            Set<String> folders = new HashSet<>();
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
                for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                    String[] parts = e.getName().split("/");
                    if (parts.length > 2 && parts[0].equals("plugins")) folders.add(parts[1]);
                }
            }
            for (String f : folders) {
                Path p = base.resolve("plugins").resolve(f).normalize();
                if (!p.startsWith(base) || !Files.isDirectory(p)) continue;
                try (Stream<Path> walk = Files.walk(p)) {
                    for (Path x : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Path abs = x.toAbsolutePath().normalize();
                        if (abs.equals(p) || abs.startsWith(backups) || backups.startsWith(abs)) continue;
                        if (Files.isDirectory(abs)) {
                            try {
                                Files.deleteIfExists(abs);
                            } catch (IOException notEmpty) {
                                // enthält Dateien, die nicht im Backup stecken (siehe unten): bleibt
                            }
                            continue;
                        }
                        // nur, was ein Backup auch enthält: große Dateien, Jars und ZIPs (z. B. Pakete) bleiben liegen
                        String n = abs.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (n.endsWith(".jar") || n.endsWith(".zip") || n.endsWith(".part") || n.endsWith(".lock")) continue;
                        if (Files.size(abs) > MAX_FILE) continue;
                        Files.deleteIfExists(abs);
                    }
                }
            }
            // 3. auspacken (nur innerhalb des Servers)
            int n = 0;
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
                for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                    if (e.isDirectory()) continue;
                    Path out = base.resolve(e.getName()).normalize();
                    if (!out.startsWith(base) || out.startsWith(backups)) continue;
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    n++;
                }
            }
            log.warning("[Backup] Wiederhergestellt: " + name + " (" + n + " Dateien). Vorheriger Stand: " + safety.getName());
        } catch (IOException ex) {
            log.severe("[Backup] Wiederherstellen fehlgeschlagen: " + ex);
        } finally {
            marker.delete();
        }
    }

    /**
     * Selbsttest: Datenordner des Kerns in eine Probe-ZIP packen, in einen Probe-Ordner entpacken und die Anzahl
     * vergleichen. Null = in Ordnung.
     */
    public static String selfCheck(KubyPlugins plugin) {
        File dir = new File(plugin.getDataFolder(), "backups");
        try {
            Path tmp = Files.createTempDirectory("kuby-backup-test");
            File zip = tmp.resolve("probe.zip").toFile();
            int n = new BackupService(plugin, dir).zip(serverRoot(plugin), List.of(plugin.getDataFolder().getAbsoluteFile()), zip);
            if (n == 0) return "keine Dateien gepackt";
            int m = 0;
            Path out = tmp.resolve("aus");
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
                for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                    Path t = out.resolve(e.getName()).normalize();
                    if (!t.startsWith(out)) return "unsicherer Pfad " + e.getName();
                    Files.createDirectories(t.getParent());
                    Files.copy(in, t, StandardCopyOption.REPLACE_EXISTING);
                    m++;
                }
            }
            try (Stream<Path> walk = Files.walk(tmp)) {
                for (Path x : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
            }
            return n == m ? null : n + " gepackt, aber " + m + " entpackt";
        } catch (IOException ex) {
            return ex.toString();
        }
    }

    /** Nur für applyPending (ohne Zeitplan). */
    private BackupService(KubyPlugins plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;
    }
}
