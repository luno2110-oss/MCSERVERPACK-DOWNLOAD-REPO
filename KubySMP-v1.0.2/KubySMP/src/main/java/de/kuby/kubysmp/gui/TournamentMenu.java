package de.kuby.kubysmp.gui;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.gui.GuiMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubysmp.service.TournamentService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Turnier-Übersicht für alle: anmelden und zuschauen. Admins haben zusätzlich die Verwaltung. */
public final class TournamentMenu extends GuiMenu {

    public TournamentMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Turnier")), 4);
    }

    @Override
    protected void build(Player p) {
        TournamentService t = KubySMP.get().tournaments();
        UUID me = p.getUniqueId();
        Set<UUID> entrants = t.entrants();
        TournamentService.State state = t.state();
        String stateText = switch (state) {
            case CLOSED -> "<dark_gray>kein Turnier";
            case REGISTRATION -> "<green>Anmeldung offen";
            case RUNNING -> "<gold>läuft, Runde " + t.round();
        };

        set(4, Items.make(Material.TOTEM_OF_UNDYING, "<gold>" + SmallCaps.of("Turnier"), List.of(
                "<gray>Status: " + stateText,
                "<gray>Teilnehmer: <white>" + entrants.size(),
                "<gray>Preis: <gold>" + Msg.coins(plugin.getConfig().getLong("tournament.reward", 5000)) + "$",
                "<gray>Siege bringen <gold>" + plugin.getConfig().getInt("tournament.trophies-per-win", 50) + " Trophäen")), null);

        int slot = 9;
        for (UUID id : entrants) {
            if (slot > 26) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            set(slot++, Items.head(op, "<white>" + name, List.of("<gray>angemeldet")), null);
        }

        if (state == TournamentService.State.REGISTRATION) {
            boolean joined = entrants.contains(me);
            set(30, joined
                    ? Items.make(Material.RED_DYE, "<red>Abmelden", List.of())
                    : Items.make(Material.LIME_DYE, "<green>Anmelden", List.of("<gray>Klick: dem Turnier beitreten")),
                    e -> result(p, joined ? t.leave(me) : t.join(p)));
        }

        if (p.hasPermission("kuby.admin")) {
            set(32, Items.make(Material.COMMAND_BLOCK, "<red>Verwaltung", List.of("<gray>Nur für Admins")),
                    e -> new TournamentAdminMenu(plugin).open(p));
        }
        addBack(p);
    }

    private void result(Player p, String error) {
        Sfx.play(plugin, p, error == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (error != null) Msg.send(p, "<red>" + error);
        open(p);
    }
}
