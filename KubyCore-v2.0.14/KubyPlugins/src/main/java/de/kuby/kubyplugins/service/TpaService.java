package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Teleport-Anfragen: /tpa Spieler (der Anfragende springt zum Ziel) und /tpahere Spieler (das Ziel springt zum
 * Anfragenden), dann /tpaccept oder /tpadeny. Ob jemand Anfragen bekommt, steht in /settings (TPA-, TPAhere-Anfragen);
 * Auto-TPA nimmt Anfragen von Freunden (oder Freunden und Team) sofort an.
 */
public final class TpaService {

    private static final long TIMEOUT_MS = 60_000L;

    private record Req(UUID from, long at, boolean here) {}

    private final KubyPlugins plugin;
    private final Map<UUID, Req> requests = new HashMap<>();

    public TpaService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String request(Player from, Player to) {
        return request(from, to, false);
    }

    /** here = /tpahere: das Ziel kommt zum Anfragenden. Gibt null bei Erfolg zurück. */
    public synchronized String request(Player from, Player to, boolean here) {
        if (from.equals(to)) return "Du kannst dich nicht zu dir selbst teleportieren.";
        if (busy(from) || busy(to)) return "Einer der beiden ist gerade beschäftigt.";
        boolean friends = plugin.friends().areFriends(from.getUniqueId(), to.getUniqueId());
        String key = here ? "tpahere" : "tpa";
        if (!plugin.playerSettings().get(to.getUniqueId(), key) && !friends && !from.hasPermission("kuby.admin")) {
            return to.getName() + " nimmt gerade keine " + (here ? "TPAhere" : "Teleport") + "-Anfragen an.";
        }
        requests.put(to.getUniqueId(), new Req(from.getUniqueId(), System.currentTimeMillis(), here));
        if (autoAccept(from, to, friends)) {
            Msg.send(from, "<gray>" + to.getName() + " nimmt deine Anfrage automatisch an.");
            Msg.send(to, "<gray>Auto-TPA: Anfrage von <white>" + from.getName() + " <gray>angenommen.");
            accept(to);
            return null;
        }
        Msg.send(to, "<gold>" + from.getName() + (here ? " <gray>möchte dich zu sich holen. " : " <gray>möchte zu dir teleportieren. ")
                + "<click:run_command:'/tpaccept'><green>[Annehmen]</green></click> <click:run_command:'/tpadeny'><red>[Ablehnen]</red></click>");
        Sfx.play(plugin, to, Sfx.Kind.PING);
        Msg.send(from, "<gray>Anfrage an <white>" + to.getName() + " <gray>gesendet.");
        return null;
    }

    private boolean autoAccept(Player from, Player to, boolean friends) {
        String mode = plugin.playerSettings().choice(to.getUniqueId(), "autotpa");
        if (mode.equals("off")) return false;
        if (friends) return true;
        if (!mode.equals("team")) return false;
        TeamService.Team a = plugin.teams().of(from.getUniqueId()), b = plugin.teams().of(to.getUniqueId());
        return a != null && a == b;
    }

    public synchronized String accept(Player to) {
        Req r = requests.remove(to.getUniqueId());
        if (r == null) return "Keine offene Anfrage.";
        if (System.currentTimeMillis() - r.at() > TIMEOUT_MS) return "Die Anfrage ist abgelaufen.";
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) return "Der Anfragende ist offline.";
        if (busy(from) || busy(to)) return "Einer der beiden ist gerade beschäftigt.";
        Msg.send(to, "<green>Anfrage angenommen.");
        if (r.here()) Teleports.delayed(plugin, to, from.getLocation(), from.getName());
        else Teleports.delayed(plugin, from, to.getLocation(), to.getName());
        return null;
    }
    public synchronized String deny(Player to) {
        Req r = requests.remove(to.getUniqueId());
        if (r == null) return "Keine offene Anfrage.";
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) Msg.send(from, "<red>" + to.getName() + " hat deine Anfrage abgelehnt.");
        Msg.send(to, "<gray>Anfrage abgelehnt.");
        return null;
    }

    private boolean busy(Player p) {
        return plugin.trades().inTrade(p.getUniqueId()) || plugin.pvp().isDueling(p.getUniqueId())
                || plugin.pvp().inCombat(p.getUniqueId());
    }
}
