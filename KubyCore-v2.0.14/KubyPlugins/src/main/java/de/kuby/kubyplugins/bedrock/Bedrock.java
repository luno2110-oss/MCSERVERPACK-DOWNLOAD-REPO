package de.kuby.kubyplugins.bedrock;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
