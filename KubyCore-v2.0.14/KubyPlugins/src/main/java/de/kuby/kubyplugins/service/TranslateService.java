package de.kuby.kubyplugins.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.kuby.kubyplugins.KubyPlugins;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Auto-Übersetzen (Schalter unten in /settings): unter Chat-Nachrichten in einer anderen Sprache erscheint für
 * Spieler mit eingeschaltetem Schalter eine Übersetzung in ihre Sprache (Deutsch oder Englisch).
 *
 * <p>Braucht einen Übersetzungsdienst in der config.yml: translate.provider "deepl" (Schlüssel von deepl.com,
 * kostenlos bis 500.000 Zeichen im Monat) oder "libre" (LibreTranslate, eigene oder öffentliche Instanz mit url).
 * Ohne Dienst ist der Schalter grau und lässt sich nicht einschalten.
 */
public final class TranslateService implements Listener {

    private static final TextColor LINE = TextColor.color(0x9FA1A7), MARK = TextColor.color(0x7BC4FC);
    private static final int CACHE = 200;

    private record Result(String text, String source) {}

    private final KubyPlugins plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Result> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
            return size() > CACHE;
        }
    };

    public TranslateService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private String provider() {
        return plugin.getConfig().getString("translate.provider", "none").toLowerCase(Locale.ROOT);
    }

    /** Auf dem Server eingerichtet? */
    public boolean ready() {
        String p = provider();
        if (p.equals("deepl")) return !plugin.getConfig().getString("translate.key", "").isBlank();
        if (p.equals("libre")) return !plugin.getConfig().getString("translate.url", "").isBlank();
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!ready()) return;
        String text = PlainTextComponentSerializer.plainText().serialize(e.originalMessage()).trim();
        if (text.length() < 4 || text.startsWith("/")) return;
        Player sender = e.getPlayer();
        Map<String, List<UUID>> byLang = new HashMap<>();
        for (Audience a : e.viewers()) {
            if (!(a instanceof Player v) || v.equals(sender)) continue;
            if (!plugin.playerSettings().get(v.getUniqueId(), "translate")) continue;
            byLang.computeIfAbsent(plugin.playerSettings().english(v.getUniqueId()) ? "en" : "de", k -> new ArrayList<>())
                    .add(v.getUniqueId());
        }
        for (Map.Entry<String, List<UUID>> en : byLang.entrySet()) {
            String target = en.getKey();
            translate(text, target).thenAccept(r -> {
                if (r == null || r.source().equalsIgnoreCase(target) || r.text().equalsIgnoreCase(text)) return;
                Component line = Component.text("  ↳ ", MARK)
                        .append(Component.text("[" + r.source().toUpperCase(Locale.ROOT) + "→" + target.toUpperCase(Locale.ROOT) + "] ",
                                NamedTextColor.DARK_GRAY))
                        .append(Component.text(r.text(), LINE));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (UUID id : en.getValue()) {
                        Player v = Bukkit.getPlayer(id);
                        if (v != null) v.sendMessage(line);
                    }
                });
            });
        }
    }

    /** Übersetzung (mit erkannter Ausgangssprache) oder null bei Fehlern. */
    public CompletableFuture<Result> translate(String text, String target) {
        String key = target + "|" + text;
        synchronized (cache) {
            Result r = cache.get(key);
            if (r != null) return CompletableFuture.completedFuture(r);
        }
        HttpRequest req;
        try {
            req = request(text, target);
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(null);
        }
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).handle((resp, err) -> {
            if (err != null || resp.statusCode() / 100 != 2) {
                if (err == null) plugin.getLogger().fine("Übersetzen: HTTP " + resp.statusCode());
                return null;
            }
            Result r = parse(resp.body());
            if (r != null) synchronized (cache) {
                cache.put(key, r);
            }
            return r;
        });
    }

    private HttpRequest request(String text, String target) {
        String k = plugin.getConfig().getString("translate.key", "");
        if (provider().equals("deepl")) {
            String url = plugin.getConfig().getString("translate.url", "");
            if (url.isBlank()) url = k.endsWith(":fx") ? "https://api-free.deepl.com/v2/translate" : "https://api.deepl.com/v2/translate";
            String body = "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&target_lang=" + target.toUpperCase(Locale.ROOT);
            return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("Authorization", "DeepL-Auth-Key " + k)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        }
        String url = plugin.getConfig().getString("translate.url", "");
        if (!url.endsWith("/translate")) url = url.replaceAll("/+$", "") + "/translate";
        JsonObject o = new JsonObject();
        o.addProperty("q", text);
        o.addProperty("source", "auto");
        o.addProperty("target", target);
        o.addProperty("format", "text");
        if (!k.isBlank()) o.addProperty("api_key", k);
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(o.toString())).build();
    }

    private Result parse(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (o.has("translations")) {
                JsonArray a = o.getAsJsonArray("translations");
                if (a.isEmpty()) return null;
                JsonObject t = a.get(0).getAsJsonObject();
                return new Result(t.get("text").getAsString(), t.get("detected_source_language").getAsString());
            }
            String src = o.has("detectedLanguage") ? o.getAsJsonObject("detectedLanguage").get("language").getAsString() : "?";
            return new Result(o.get("translatedText").getAsString(), src);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
