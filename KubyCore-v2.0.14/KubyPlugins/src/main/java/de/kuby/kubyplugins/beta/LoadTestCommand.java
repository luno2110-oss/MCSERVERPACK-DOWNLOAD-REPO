package de.kuby.kubyplugins.beta;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.UiClicks;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Belastungstest (nur Konsole und Admins, kuby.admin):
 * <ul>
 *   <li>/kubytest klicks [n]: für jeden Spieler online n zufällige Klicks im gerade offenen Menü (wie ein echter Klick,
 *   danach geht es im Folge-Menü weiter); ist keins offen, öffnet er zuerst ein zufälliges Hauptmenü.</li>
 *   <li>/kubytest events: löst alle registrierten Test-Events aus (Quiz, Ziehungen, Event-Kapitel, Turnier …).</li>
 * </ul>
 * Fehler landen mit Stacktrace im Log ("Fehler bei Menü-Klick", "Test-Event fehlgeschlagen") und lassen den
 * Belastungstest in der CI scheitern.
 */
public final class LoadTestCommand implements CommandExecutor {

    /** Menüs, mit denen ein Klick-Durchgang beginnt, wenn gerade keins offen ist. */
    private static final String[] START = {"menu", "shop", "battlepass", "daily", "spielzeit", "settings", "stats", "guide", "friends",
            "team", "markt", "kits", "keys", "cosmetics", "angeln", "angeln pass", "angeln markt", "casino", "cookie", "quests", "jobs",
            "insel", "combo", "skyblock", "baltop", "ranking", "investments", "regeln", "hilfe", "feedback", "erfolge", "support"};

    private final KubyPlugins plugin;

    public LoadTestCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("kuby.admin")) return true;
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "klicks" -> {
                int n = 3;
                try {
                    if (args.length > 1) n = Math.max(1, Math.min(20, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                    // Standard
                }
                int clicks = 0, opened = 0;
                for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                    for (int i = 0; i < n; i++) {
                        if (!p.isOnline()) break;
                        if (UiClicks.randomClick(p)) {
                            clicks++;
                        } else {
                            String cmd = START[ThreadLocalRandom.current().nextInt(START.length)];
                            p.performCommand(cmd);
                            opened++;
                        }
                    }
                }
                plugin.getLogger().info("[Belastungstest] " + clicks + " Menü-Klicks, " + opened + " Menüs geöffnet");
            }
            case "events" -> {
                List<String> done = new ArrayList<>();
                for (Map.Entry<String, Runnable> e : plugin.testEvents().entrySet()) {
                    try {
                        e.getValue().run();
                        done.add(e.getKey());
                    } catch (RuntimeException ex) {
                        plugin.getLogger().severe("Test-Event fehlgeschlagen: " + e.getKey() + ": " + ex);
                        ex.printStackTrace();
                    }
                }
                plugin.getLogger().info("[Belastungstest] Events ausgelöst: " + String.join(", ", done));
            }
            default -> sender.sendMessage("/kubytest klicks [n] | events");
        }
        return true;
    }
}
