package de.kuby.kubysmp.gui;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.gui.GuiMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class DuelMenu extends GuiMenu {

    public DuelMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Duelle")), 4);
    }

    @Override
    protected void build(Player p) {
        UUID from = KubySMP.get().duels().pendingChallengerOf(p.getUniqueId());
        if (from != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(from);
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            set(4, Items.head(op, "<yellow>Herausforderung: <white>" + name, List.of(
                    "<green>Klick: annehmen")), e -> {
                String err = KubySMP.get().duels().accept(p);
                if (err != null) Msg.send(p, "<red>" + err);
                open(p);
            });
        }

        int slot = 9;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (slot > 26) break;
            if (other.equals(p)) continue;
            boolean busy = KubySMP.get().duels().isDueling(other.getUniqueId());
            set(slot++, Items.head(other, "<white>" + other.getName(), List.of(
                            busy ? "<red>Im Duell" : "<green>Klick: herausfordern (ohne Einsatz)",
                            "<gray>Mit Einsatz: <white>/duell " + other.getName() + " <Betrag>")),
                    e -> {
                        if (busy) {
                            Msg.send(p, "<red>Dieser Spieler ist gerade im Duell.");
                            return;
                        }
                        String err = KubySMP.get().duels().challenge(p, other, 0);
                        Msg.send(p, err == null
                                ? "<green>Herausforderung an <white>" + other.getName() + " <green>gesendet."
                                : "<red>" + err);
                        open(p);
                    });
        }
        if (slot == 9) {
            set(18, Items.make(Material.BARRIER, "<gray>" + SmallCaps.of("Niemand online"), List.of()), null);
        }
        addBack(p);
    }
}
