package de.kuby.kubyplugins.bedrock;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Bedrock-Spieler (über Geyser und Floodgate): Erkennung und Menüs als native Bedrock-Formulare. Floodgate und Geyser
 * sind optional (softdepend); ohne sie ist niemand Bedrock-Spieler und alles läuft wie bisher.
 *
 * <p>Config: bedrock.formulare (true) schaltet die Formulare, bedrock.aktiv (true) die ganze Bedrock-Anpassung.
 */
public final class Bedrock {

    private static KubyPlugins plugin;
    /** Offenes Formular je Spieler (Titel), damit sich selbst aktualisierende Menüs nicht jede Sekunde neu senden. */
    private static final Map<UUID, String> OPEN = new ConcurrentHashMap<>();

    private Bedrock() {
    }

    public static void init(KubyPlugins p) {
        plugin = p;
        BedrockAssets.load();
        // nach dem Start (Geyser lädt Erweiterungen und Packs später): Einrichtung für Bedrock prüfen und melden
        p.getServer().getScheduler().runTaskLater(p, () -> {
            List<String> probs = new java.util.ArrayList<>();
            List<String> lines = diagnose(probs);
            if (!geyser() && !floodgate()) return;
            for (String l : lines) p.getLogger().info("[Bedrock] " + l);
            for (String l : probs) p.getLogger().warning("[Bedrock] " + l);
        }, 200L);
    }

    /**
     * Prüft, was Bedrock-Spieler für unsere 3D-Modelle brauchen: Geyser mit eigenen Items, unser Pack und die Mappings,
     * die Erweiterung GeyserDisplayEntity samt Pack (ohne sie zeigt Bedrock keine Item-Anzeigen: Tische, Karten, Keks
     * und Möbel bleiben unsichtbar – so im ersten Test auf einem echten Gerät gesehen).
     */
    public static List<String> diagnose(List<String> problems) {
        List<String> out = new java.util.ArrayList<>();
        if (!geyser()) {
            out.add("Geyser-Spigot läuft nicht auf diesem Server" + (floodgate() ? " (Floodgate ja – Geyser vermutlich auf dem Proxy: "
                    + "dort GeyserDisplayEntity, KubyBedrock.mcpack und KubyBedrock-Items.json prüfen)" : ""));
            return out;
        }
        java.io.File gd = new java.io.File(plugin.getDataFolder().getParentFile(), "Geyser-Spigot");
        List<String> ext;
        try {
            ext = GeyserBridge.extensions();
        } catch (Throwable t) {
            ext = List.of();
        }
        boolean gde = ext.stream().anyMatch(n -> n.toLowerCase(java.util.Locale.ROOT).replace(" ", "").contains("displayentity"));
        out.add("Geyser-Erweiterungen: " + (ext.isEmpty() ? "keine" : String.join(", ", ext)));
        if (!gde) problems.add("GeyserDisplayEntity fehlt: Bedrock-Spieler sehen keine 3D-Modelle (Casino-Tische, Karten, Chips,"
                + " Automaten, Keks, Möbel). Jar nach plugins/Geyser-Spigot/extensions/, GeyserDisplayEntityPack.mcpack nach"
                + " plugins/Geyser-Spigot/packs/ (BEDROCK.md, Schritt 4b), dann Neustart.");
        java.io.File[] packs = new java.io.File(gd, "packs").listFiles();
        List<String> names = new java.util.ArrayList<>();
        if (packs != null) for (java.io.File f : packs) names.add(f.getName());
        out.add("Geyser-Packs: " + (names.isEmpty() ? "keine" : String.join(", ", names)));
        if (names.stream().noneMatch(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith("kubybedrock"))) {
            problems.add("KubyBedrock.mcpack fehlt in plugins/Geyser-Spigot/packs/ (Icons, Glyphen, 3D-Geometrie für Bedrock).");
        }
        if (gde && names.stream().noneMatch(n -> n.toLowerCase(java.util.Locale.ROOT).contains("displayentity"))) {
            problems.add("GeyserDisplayEntityPack.mcpack fehlt in plugins/Geyser-Spigot/packs/ (ohne Pack bleiben die Anzeigen unsichtbar).");
        }
        if (!new java.io.File(gd, "custom_mappings/KubyBedrock-Items.json").exists()) {
            problems.add("KubyBedrock-Items.json fehlt in plugins/Geyser-Spigot/custom_mappings/ (eigene Items und Modelle).");
        }
        return out;
    }

    private static boolean floodgate() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    private static boolean geyser() {
        return Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
    }

    private static boolean enabled() {
        return plugin != null && plugin.getConfig().getBoolean("bedrock.aktiv", true);
    }

    /** Spielt dieser Spieler mit Bedrock (Handy, Konsole, Windows-Edition)? */
    public static boolean is(Player p) {
        if (p == null || !enabled()) return false;
        UUID id = p.getUniqueId();
        try {
            if (floodgate() && FloodgateBridge.isBedrock(id)) return true;
        } catch (Throwable ignored) {
            // andere Floodgate-Version
        }
        try {
            if (geyser() && GeyserBridge.isBedrock(id)) return true;
        } catch (Throwable ignored) {
            // andere Geyser-Version
        }
        return false;
    }

    /** Menüs als Bedrock-Formular (statt Java-Dialog oder Truhe)? */
    public static boolean forms(Player p) {
        return plugin != null && plugin.getConfig().getBoolean("bedrock.formulare", true) && (floodgate() || geyser()) && is(p);
    }

    /**
     * Canvas-Menü als Formular senden. onClose: Spieler hat das Formular geschlossen (X), sonst null. Gibt false zurück,
     * wenn es nicht gesendet werden konnte (dann zeigt der Aufrufer den Java-Weg).
     */
    public static boolean show(Player p, Canvas canvas, Consumer<Player> onClose, boolean fromClick) {
        FormModel.Model m = FormModel.of(canvas);
        String open = OPEN.get(p.getUniqueId());
        // Selbst aktualisierende Menüs (Börse, Crash …): kein neues Formular, solange dasselbe noch offen ist
        if (!fromClick && open != null && open.equals(m.title())) return true;
        try {
            boolean ok = FormSender.send(plugin, p, m, onClose, floodgate());
            if (ok) OPEN.put(p.getUniqueId(), m.title());
            return ok;
        } catch (Throwable t) {
            plugin.getLogger().warning("Bedrock-Formular für " + p.getName() + " fehlgeschlagen: " + t);
            return false;
        }
    }

    /**
     * Hinweisfenster mit einem Knopf (z. B. „Verstanden“): Titel, Text (Bedrock-Farbcodes mit §), sonst nichts.
     * Gibt false zurück, wenn es nicht gesendet werden konnte (kein Bedrock-Spieler, kein Floodgate/Geyser).
     */
    public static boolean notice(Player p, String title, String content, String button) {
        if (plugin == null || !is(p) || !(floodgate() || geyser())) return false;
        FormModel.Model m = new FormModel.Model(title, content, java.util.List.of(new FormModel.Button(button, null, null, null, null)));
        try {
            return FormSender.send(plugin, p, m, null, floodgate());
        } catch (Throwable t) {
            plugin.getLogger().warning("Bedrock-Hinweis für " + p.getName() + " fehlgeschlagen: " + t);
            return false;
        }
    }

    /** Vom FormSender: Formular beantwortet oder geschlossen. */
    static void answered(UUID id) {
        OPEN.remove(id);
    }

    public static void quit(UUID id) {
        OPEN.remove(id);
    }

    public static KubyPlugins plugin() {
        return plugin;
    }
}
