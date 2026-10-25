package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class FriendsMenu extends GuiMenu {

    public FriendsMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Freunde")), 5);
    }

    @Override
    protected void build(Player p) {
        int slot = 0;
        for (UUID id : plugin.friends().friendsOf(p.getUniqueId())) {
            if (slot >= 27) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean online = op.isOnline();
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            Player on = Bukkit.getPlayer(id);
            boolean visible = online && on != null && p.canSee(on);
            set(slot++, Items.head(op, (visible ? "<green>" : "<gray>") + name, List.of(
                            visible ? "<green>Online <gray>· " + plugin.friends().whereIs(on) : "<gray>Offline",
                            visible ? "<gray>Klick: <white>nachspringen" : "",
                            "<gray>Shift + Klick: <red>Freundschaft beenden")),
                    e -> {
                        if (!e.isShiftClick()) {
                            if (!visible) return;
                            String err = plugin.friends().jump(p, Bukkit.getPlayer(id));
                            if (err != null) Msg.send(p, "<red>" + err);
                            else p.closeInventory();
                            return;
                        }
                        plugin.friends().remove(p.getUniqueId(), id);
                        Msg.send(p, "<gray>Freundschaft mit <white>" + name + " <gray>beendet.");
                        open(p);
                    });
        }
        if (slot == 0) {
            set(10, Items.make(org.bukkit.Material.BARRIER, "<gray>" + SmallCaps.of("Keine Freunde"), List.of(
                    "<gray>/friend add <Spieler>")), null);
        }

        int r = 27;
        for (UUID id : plugin.friends().requestsOf(p.getUniqueId())) {
            if (r > 35) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            set(r++, Items.head(op, "<yellow>Anfrage: <white>" + name, List.of(
                            "<green>Klick: annehmen", "<gray>Shift + Klick: <red>ablehnen")),
                    e -> {
                        if (e.isShiftClick()) {
                            plugin.friends().deny(p.getUniqueId(), id);
                            open(p);
                            return;
                        }
                        String err = plugin.friends().accept(p.getUniqueId(), id);
                        Msg.send(p, err == null ? "<green>Ihr seid jetzt befreundet!" : "<red>" + err); Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                        open(p);
                    });
        }
        addBack(p);
    }
}
