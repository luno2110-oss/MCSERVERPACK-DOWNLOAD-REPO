package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Was die Schalter im Reiter "Chat" tun: öffentlichen Chat ausblenden, Join/Leave- und Todesmeldungen nur an die, die
 * sie sehen wollen, bei vertippten Befehlen den passenden vorschlagen (Chat-Hilfe) und beim Betreten die Neuerungen aus
 * changelog in der config.yml zeigen (je Spieler nur, was er noch nicht gesehen hat).
 */
public final class ChatSettingsListener implements Listener {

    private final KubyPlugins plugin;

    public ChatSettingsListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private boolean on(Player p, String key) {
        return plugin.playerSettings().get(p.getUniqueId(), key);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        e.viewers().removeIf(a -> a instanceof Player v && !v.equals(sender) && !on(v, "chat.public"));
    }

    // =================================================================================================================
    // Join, Leave, Tod
    // =================================================================================================================

    private void spread(Component msg, Player except, String key) {
        if (msg == null) return;
        Bukkit.getConsoleSender().sendMessage(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(except) && on(p, key)) p.sendMessage(msg);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Component msg = e.joinMessage();
        e.joinMessage(null);
        spread(msg, null, "chat.joinleave");
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) changelog(p);
        }, 80L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Component msg = e.quitMessage();
        e.quitMessage(null);
        spread(msg, e.getPlayer(), "chat.joinleave");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Component msg = e.deathMessage();
        if (msg == null) return;
        Player dead = e.getPlayer();
        if (Boolean.FALSE.equals(dead.getWorld().getGameRuleValue(GameRule.SHOW_DEATH_MESSAGES))) return;
        e.deathMessage(null);
        Bukkit.getConsoleSender().sendMessage(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            // die eigene Todesursache sieht man immer
            if (p.equals(dead) || on(p, "chat.deaths")) p.sendMessage(msg);
        }
    }

    // =================================================================================================================
    // Chat-Hilfe
    // =================================================================================================================

    @EventHandler
    public void onUnknown(UnknownCommandEvent e) {
        if (!(e.getSender() instanceof Player p) || !on(p, "chat.help")) return;
        String line = e.getCommandLine().startsWith("/") ? e.getCommandLine().substring(1) : e.getCommandLine();
        String label = line.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        String rest = line.contains(" ") ? line.substring(line.indexOf(' ')) : "";
        String best = suggest(p, label);
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        String help = plugin.getServer().getPluginCommand("hilfe") != null ? "/hilfe" : "/help";
        if (best == null) {
            e.message(Msg.parse("<#FC7A6A>" + (en ? "Unknown command. " : "Unbekannter Befehl. ")
                    + "<gray>" + (en ? "All commands: " : "Alle Befehle: ") + "<click:run_command:'" + help + "'><white>" + help + "</click>"));
            return;
        }
        String full = "/" + best + rest;
        e.message(Msg.parse("<#FC7A6A>" + (en ? "Unknown command. " : "Unbekannter Befehl. ") + "<gray>" + (en ? "Did you mean " : "Meintest du ")
                + "<click:suggest_command:'" + Msg.escape(full).replace("'", "") + "'><hover:show_text:'<gray>"
                + (en ? "Click: insert" : "Klick: einfügen") + "'><white><u>" + Msg.escape(full) + "</u></hover></click><gray>?"));
    }

    private String suggest(Player p, String label) {
        if (label.isEmpty()) return null;
        String best = null;
        int bestD = Integer.MAX_VALUE;
        List<String> seen = new ArrayList<>();
        for (Map.Entry<String, Command> en : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
            String name = en.getKey();
            if (name.contains(":") || seen.contains(name)) continue;
            seen.add(name);
            if (!en.getValue().testPermissionSilent(p)) continue;
            int d = distance(label, name);
            int max = Math.max(1, Math.min(3, label.length() / 3));
            if (d > max) continue;
            if (d < bestD || (d == bestD && best != null && name.length() < best.length())) {
                bestD = d;
                best = name;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    // =================================================================================================================
    // Changelog
    // =================================================================================================================

    /**
     * Einträge aus changelog (Liste "JJJJ-MM-TT: Text"), die neuer sind als der zuletzt gesehene. Neue Spieler sehen
     * höchstens die letzten drei.
     */
    private void changelog(Player p) {
        List<String> all = plugin.getConfig().getStringList("changelog");
        if (all.isEmpty()) return;
        String seen = plugin.playerSettings().text(p.getUniqueId(), "changelog.seen");
        List<String[]> fresh = new ArrayList<>();
        String newest = seen;
        for (String raw : all) {
            int i = raw.indexOf(':');
            if (i < 8) continue;
            String date = raw.substring(0, i).trim();
            if (date.compareTo(seen) > 0) fresh.add(new String[]{date, raw.substring(i + 1).trim()});
            if (date.compareTo(newest) > 0) newest = date;
        }
        plugin.playerSettings().setText(p.getUniqueId(), "changelog.seen", newest);
        if (fresh.isEmpty() || !on(p, "chat.changelog")) return;
        fresh.sort((x, y) -> y[0].compareTo(x[0]));
        if (fresh.size() > 3) fresh = fresh.subList(0, 3);
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        Msg.send(p, "<#B7A6FC><b>" + (en ? "WHAT'S NEW" : "NEU AUF DEM SERVER") + "</b>");
        for (String[] f : fresh) {
            String d = f[0].length() >= 10 ? f[0].substring(8, 10) + "." + f[0].substring(5, 7) + "." : f[0];
            Msg.send(p, "<dark_gray>• <gray>" + d + " <white>" + f[1]);
        }
    }
}
