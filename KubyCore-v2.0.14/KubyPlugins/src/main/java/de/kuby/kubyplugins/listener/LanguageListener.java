package de.kuby.kubyplugins.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.I18n;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

/**
 * Sprache je Spieler (12.7): setzt den Sprach-Kontext (I18n) für alles, was ein Spieler auslöst – Befehle aller
 * Kuby-Plugins, Klicks in Menüs und Dialogen, Interaktionen –, damit Menüs, Dialoge und Tooltips in seiner Sprache
 * gebaut werden. Dazu die automatische Sprache nach der Client-Sprache, solange er sie nicht selbst gewählt hat.
 */
public final class LanguageListener implements Listener {

    private static final List<Class<? extends Event>> EVENTS = List.of(PlayerInteractEvent.class,
            PlayerInteractEntityEvent.class, PlayerInteractAtEntityEvent.class, InventoryClickEvent.class,
            InventoryDragEvent.class, PlayerCustomClickEvent.class, PrePlayerAttackEntityEvent.class,
            PlayerJoinEvent.class, PlayerPostRespawnEvent.class);

    private final KubyPlugins plugin;

    public LanguageListener(KubyPlugins plugin) {
        this.plugin = plugin;
        for (Class<? extends Event> type : EVENTS) {
            plugin.getServer().getPluginManager().registerEvent(type, this, EventPriority.LOWEST,
                    (l, e) -> {
                        Player p = actor(e);
                        if (p != null) I18n.enter(p);
                    }, plugin, false);
            plugin.getServer().getPluginManager().registerEvent(type, this, EventPriority.MONITOR,
                    (l, e) -> {
                        if (actor(e) != null) I18n.clear();
                    }, plugin, false);
        }
    }

    private static Player actor(Event e) {
        if (e instanceof PlayerEvent pe) return pe.getPlayer();
        if (e instanceof InventoryClickEvent ic) return ic.getWhoClicked() instanceof Player p ? p : null;
        if (e instanceof InventoryDragEvent id) return id.getWhoClicked() instanceof Player p ? p : null;
        if (e instanceof PlayerCustomClickEvent cc) {
            HumanEntity h = cc.getCommonConnection() instanceof io.papermc.paper.connection.PlayerGameConnection g
                    ? g.getPlayer() : null;
            return h instanceof Player p ? p : null;
        }
        return null;
    }

    // =================================================================================================================
    // Automatische Sprache
    // =================================================================================================================

    private static boolean english(Locale l) {
        return l != null && !"de".equalsIgnoreCase(l.getLanguage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // die Client-Sprache kommt kurz nach dem Einloggen
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.playerSettings().autoLanguage(p.getUniqueId(), english(p.locale()));
        }, 40L);
    }

    @EventHandler
    public void onLocale(PlayerLocaleChangeEvent e) {
        plugin.playerSettings().autoLanguage(e.getPlayer().getUniqueId(), english(e.locale()));
    }

    // =================================================================================================================
    // Befehle
    // =================================================================================================================

    /** Nach dem Start: alle Befehle der Kuby-Plugins laufen im Sprach-Kontext des Spielers, der sie eingibt. */
    @EventHandler
    public void onLoad(ServerLoadEvent e) {
        int n = 0;
        for (Plugin pl : plugin.getServer().getPluginManager().getPlugins()) {
            if (!pl.getName().startsWith("Kuby")) continue;
            for (String name : pl.getDescription().getCommands().keySet()) {
                PluginCommand pc = plugin.getServer().getPluginCommand(pl.getName().toLowerCase(Locale.ROOT) + ":" + name);
                if (pc == null) pc = plugin.getServer().getPluginCommand(name);
                if (pc == null || pc.getExecutor() instanceof Wrapped) continue;
                pc.setExecutor(new Wrapped(pc.getExecutor()));
                n++;
            }
        }
        plugin.getLogger().info("Sprache: " + n + " Befehle mit Sprach-Kontext, " + I18n.size() + " englische Vorlagen.");
    }

    private record Wrapped(CommandExecutor inner) implements CommandExecutor {
        @Override
        public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label,
                                 String[] args) {
            if (!(sender instanceof Player p)) return inner.onCommand(sender, cmd, label, args);
            return I18n.as(p, () -> inner.onCommand(sender, cmd, label, args));
        }
    }
}
