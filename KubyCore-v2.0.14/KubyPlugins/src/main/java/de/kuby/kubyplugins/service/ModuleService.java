package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Module sind Addons mit fertigen Einstellungen. Ein Hub-Modul legt Border, Zeit, Wetter, Musik und Begrüßung fest.
 * Module liegen in plugins/KubyPlugins/modules/<id>/module.yml und können aus einem Store geladen werden.
 */
public final class ModuleService {

    static final String DEFAULT_HUB = """
            name: Standard-Hub
            type: hub
            settings:
              border-size: 60
              time: 6000
              time-lock: true
              weather: CLEAR
              weather-lock: true
              music: ""
              join-text: "<gold>Willkommen in der Lobby!"
            """;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final KubyPlugins plugin;

    public ModuleService(KubyPlugins plugin) {
        this.plugin = plugin;
        ensureDefault();
    }

    private File dir(String id) {
        return new File(plugin.getDataFolder(), "modules/" + id);
    }

    private void ensureDefault() {
        File f = new File(dir("hub"), "module.yml");
        if (f.isFile()) return;
        try {
            dir("hub").mkdirs();
            Files.writeString(f.toPath(), DEFAULT_HUB);
        } catch (IOException e) {
            plugin.getLogger().warning("Standard-Hub-Modul konnte nicht angelegt werden: " + e.getMessage());
        }
    }

    public boolean exists(String id) {
        return new File(dir(id), "module.yml").isFile();
    }

    public List<String> installed() {
        List<String> ids = new ArrayList<>();
        File[] folders = new File(plugin.getDataFolder(), "modules").listFiles(File::isDirectory);
        if (folders == null) return ids;
        for (File f : folders) if (new File(f, "module.yml").isFile()) ids.add(f.getName());
        return ids;
    }

    /** Schreibt die Einstellungen des Moduls in die Config. Wird beim Erstellen der Hub-Welt benutzt. */
    public void applyHub(String id) {
        YamlConfiguration m = YamlConfiguration.loadConfiguration(new File(dir(id), "module.yml"));
        ConfigurationSection s = m.getConfigurationSection("settings");
        if (s != null) {
            for (String k : s.getKeys(false)) plugin.getConfig().set("hub-settings." + k, s.get(k));
        }
        plugin.getConfig().set("hub-module", id);
        plugin.saveConfig();
    }

    /** Lädt ein Modul aus dem Store herunter. Läuft asynchron und meldet das Ergebnis dem Admin. */
    public void download(Player admin, String id, String sourceArg) {
        String source = sourceArg.toUpperCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) {
            Msg.send(admin, "<red>Ungültige Modul-ID. Erlaubt sind Kleinbuchstaben, Ziffern, - und _.");
            return;
        }
        if (!List.of("GITHUB", "DRIVE", "MEDIAFIRE").contains(source)) {
            Msg.send(admin, "<red>Quelle muss GITHUB, DRIVE oder MEDIAFIRE sein.");
            return;
        }
        Msg.send(admin, "<gray>Lade Modul <white>" + id + "<gray> von " + source + " ...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String message;
            boolean ok;
            try {
                message = fetch(id, source);
                ok = true;
            } catch (Exception ex) {
                message = "Fehler: " + ex.getMessage();
                ok = false;
            }
            String finalMessage = message;
            boolean finalOk = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Sfx.play(plugin, admin, finalOk ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                Msg.send(admin, (finalOk ? "<green>" : "<red>") + finalMessage);
            });
        });
    }

    private String fetch(String id, String source) throws Exception {
        String indexUrl = plugin.getConfig().getString("module-store.index-url", "");
        if (!allowed("GITHUB", indexUrl)) throw new IllegalStateException("Index-URL ist nicht erlaubt.");
        YamlConfiguration index = new YamlConfiguration();
        index.loadFromString(get(indexUrl));
        String url = index.getString("modules." + id + "." + source);
        if (url == null) throw new IllegalStateException("Modul " + id + " ist für " + source + " nicht im Store.");
        if (!allowed(source, url)) throw new IllegalStateException("Diese Adresse ist für " + source + " nicht erlaubt.");

        YamlConfiguration mod = new YamlConfiguration();
        mod.loadFromString(get(url));
        if (!"hub".equalsIgnoreCase(mod.getString("type", ""))) {
            throw new IllegalStateException("Bisher werden nur Hub-Module unterstützt.");
        }
        File d = dir(id);
        d.mkdirs();
        mod.save(new File(d, "module.yml"));
        return "Modul " + id + " installiert. Hub erstellen mit /kp create hub " + id;
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
        if (res.body().length() > 256_000) throw new IOException("Datei ist zu groß.");
        return res.body();
    }

    /** Nur HTTPS und bekannte Hosts. */
    private static boolean allowed(String source, String url) {
        try {
            URI u = URI.create(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) return false;
            String h = u.getHost().toLowerCase(Locale.ROOT);
            return switch (source) {
                case "GITHUB" -> h.equals("github.com") || h.equals("raw.githubusercontent.com")
                        || h.equals("objects.githubusercontent.com");
                case "DRIVE" -> h.equals("drive.google.com") || h.equals("docs.google.com");
                case "MEDIAFIRE" -> h.equals("mediafire.com") || h.endsWith(".mediafire.com");
                default -> false;
            };
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
