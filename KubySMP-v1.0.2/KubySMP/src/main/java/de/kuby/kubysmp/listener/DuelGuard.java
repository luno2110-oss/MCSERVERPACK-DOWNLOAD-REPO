package de.kuby.kubysmp.listener;

import de.kuby.kubysmp.KubySMP;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.List;
import java.util.Locale;

/**
 * Regeln im Duell: Kit-Items bleiben im Duell. Gesperrt sind Befehle (außer Chat-Befehlen aus duel.erlaubte-befehle,
 * z. B. /msg), Wegwerfen und alle Behälter (Kisten, Enderkiste, Shops, Handel). Früher ließen sich Kit-Items
 * wegwerfen, verkaufen oder einlagern, und mit /home oder /spawn konnte man die Arena samt Kit verlassen.
 */
public final class DuelGuard implements Listener {

    private final KubyPlugins plugin;

    public DuelGuard(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static boolean dueling(Player p) {
        return KubySMP.get().duels().isDueling(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!dueling(p) || p.hasPermission("kuby.admin")) return;
        String msg = e.getMessage();
        String cmd = msg.length() > 1 ? msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT) : "";
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        List<String> ok = plugin.getConfig().getStringList("duel.erlaubte-befehle");
        if (ok.isEmpty()) ok = List.of("msg", "tell", "w", "r", "reply", "report", "regeln", "rules");
        if (ok.contains(cmd)) return;
        e.setCancelled(true);
        Msg.send(p, "<red>Im Duell geht das nicht. <gray>Erst das Duell beenden.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!dueling(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !dueling(p)) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.PLAYER || t == InventoryType.CRAFTING) return;
        e.setCancelled(true);
        Msg.send(p, "<red>Im Duell kannst du keine Behälter öffnen.");
    }
}
