package de.kuby.kubysmp.gui;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.gui.AdminBase;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Kopfgelder entfernen und laufende Duelle abbrechen. */
public final class AdminCombatMenu extends AdminBase {

    public AdminCombatMenu(KubyPlugins plugin) {
        super(plugin, "Kampf", 4);
    }

    @Override
    protected void build(Player p) {
        int slot = 9;
        for (Map.Entry<UUID, Long> en : KubySMP.get().bounties().all().entrySet()) {
            if (slot > 17) break;
            UUID id = en.getKey();
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "?" : op.getName();
            set(slot++, Items.head(op, "<red>Kopfgeld: <white>" + name, List.of(
                            "<gold>" + Msg.coins(en.getValue()) + "$",
                            "<red>Klick: Kopfgeld entfernen")),
                    e -> {
                        long amount = KubySMP.get().bounties().claim(id);
                        done(p, "<gray>Kopfgeld entfernt (" + Msg.coins(amount) + "$).");
                    });
        }

        slot = 18;
        for (UUID id : KubySMP.get().duels().activeDuelPlayers()) {
            if (slot > 26) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "?" : op.getName();
            set(slot++, Items.head(op, "<gold>Duell: <white>" + name, List.of(
                            "<red>Klick: Duell abbrechen (Einsätze zurück)")),
                    e -> {
                        KubySMP.get().duels().adminCancel(id);
                        done(p, "<gray>Duell abgebrochen.");
                    });
        }
        adminBack(p);
    }
}
