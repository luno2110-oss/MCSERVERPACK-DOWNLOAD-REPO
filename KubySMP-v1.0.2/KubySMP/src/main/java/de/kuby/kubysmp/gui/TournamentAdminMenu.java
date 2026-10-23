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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Komplette Turnier-Steuerung für Admins: Anmeldung, Start, Teilnehmer, Matches, Preise und Trophäen. */
public final class TournamentAdminMenu extends GuiMenu {

    public TournamentAdminMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_red>" + SmallCaps.of("turnier-verwaltung")), 5);
    }

    @Override
    protected void build(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        TournamentService t = KubySMP.get().tournaments();
        TournamentService.State state = t.state();
        String stateText = switch (state) {
            case CLOSED -> "<dark_gray>kein Turnier";
            case REGISTRATION -> "<green>Anmeldung offen";
            case RUNNING -> "<gold>läuft, Runde " + t.round();
        };

        set(4, Items.make(Material.COMMAND_BLOCK, "<red>" + SmallCaps.of("status"), List.of(
                "<gray>Status: " + stateText,
                "<gray>Angemeldet: <white>" + t.entrants().size(),
                "<gray>Noch im Turnier: <white>" + t.alive().size(),
                "<gray>Preis: <gold>" + Msg.coins(plugin.getConfig().getLong("tournament.reward", 5000)) + "$",
                "<gray>Trophäen pro Sieg: <gold>" + plugin.getConfig().getInt("tournament.trophies-per-win", 50),
                "<gray>Trophäen Titel: <gold>" + plugin.getConfig().getInt("tournament.trophies-champion", 200),
                "<gray>Max. Teilnehmer: <white>" + plugin.getConfig().getInt("tournament.max-players", 16))), null);

        // Teilnehmer (Reihe 2): in der Anmeldung anklicken zum Entfernen
        List<UUID> people = state == TournamentService.State.REGISTRATION
                ? new ArrayList<>(t.entrants()) : t.alive();
        int slot = 9;
        for (UUID id : people) {
            if (slot > 17) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            boolean removable = state == TournamentService.State.REGISTRATION;
            set(slot++, Items.head(op, "<white>" + name, List.of(
                    removable ? "<red>Klick: aus der Anmeldung entfernen" : "<gray>im Turnier")),
                    removable ? e -> result(p, t.kick(id)) : null);
        }
        if (people.isEmpty()) {
            set(13, Items.make(Material.BARRIER, "<gray>Keine Teilnehmer", List.of()), null);
        }

        // Laufende Matches (Reihe 3)
        int m = 18;
        for (UUID[] pair : t.matches()) {
            if (m > 26) break;
            String a = nameOf(pair[0]);
            String b = nameOf(pair[1]);
            set(m++, Items.make(Material.IRON_SWORD, "<gold>" + a + " <gray>vs <gold>" + b, List.of(
                    "<gray>Läuft gerade")), null);
        }
        if (m == 18) {
            set(22, Items.make(Material.GRAY_DYE, "<gray>Keine laufenden Matches", List.of()), null);
        }

        // Steuerung (Reihe 4)
        if (state == TournamentService.State.CLOSED) {
            set(29, Items.make(Material.NETHER_STAR, "<gold>Anmeldung öffnen", List.of()),
                    e -> result(p, t.open()));
        }
        if (state == TournamentService.State.REGISTRATION) {
            set(30, Items.make(Material.DIAMOND, "<green>Turnier starten", List.of(
                    "<gray>Paart die Teilnehmer zufällig")), e -> result(p, t.start()));
        }
        if (state != TournamentService.State.CLOSED) {
            set(31, Items.make(Material.BARRIER, "<red>Turnier abbrechen", List.of(
                    "<gray>Laufende Duelle enden ohne Wertung")), e -> result(p, t.cancel()));
        }

        // Einstellungen (Reihe 5)
        set(33, Items.make(Material.PAPER, "<white>Ankündigung senden", List.of()),
                e -> plugin.prompts().ask(p, "Ankündigung an alle:", text -> {
                    plugin.getServer().broadcast(Msg.parse("<gold>[Turnier] <white>" + Msg.escape(text)));
                    new TournamentAdminMenu(plugin).open(p);
                }));
        set(34, Items.make(Material.GOLD_INGOT, "<gold>Preis ändern", List.of("<gray>$ für den Sieger")),
                e -> askNumber(p, "Neuer Preis in$:", "tournament.reward", 0, 1000000000L));
        set(35, Items.make(Material.EXPERIENCE_BOTTLE, "<aqua>Trophäen pro Sieg", List.of("<gray>Bei jedem gewonnenen Match")),
                e -> askNumber(p, "Trophäen pro Sieg:", "tournament.trophies-per-win", 0, 10000));
        set(36, Items.make(Material.TOTEM_OF_UNDYING, "<gold>Trophäen für den Titel", List.of("<gray>Bonus für den Sieger")),
                e -> askNumber(p, "Trophäen für den Titel:", "tournament.trophies-champion", 0, 100000));
        set(37, Items.make(Material.PLAYER_HEAD, "<white>Max. Teilnehmer", List.of("<gray>Zwischen 2 und 64")),
                e -> askNumber(p, "Max. Teilnehmer (2-64):", "tournament.max-players", 2, 64));

        set(40, Items.make(Material.ARROW, "<gray>Zurück", List.of()),
                e -> new TournamentMenu(plugin).open(p));
    }

    private String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private void result(Player p, String error) {
        Sfx.play(plugin, p, error == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (error != null) Msg.send(p, "<red>" + error);
        new TournamentAdminMenu(plugin).open(p);
    }

    private void askNumber(Player p, String question, String path, long min, long max) {
        plugin.prompts().ask(p, question, text -> {
            try {
                long v = Math.max(min, Math.min(max, Long.parseLong(text.trim())));
                plugin.getConfig().set(path, v);
                plugin.saveConfig();
                Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                Msg.send(p, "<green>Gespeichert: " + v);
            } catch (NumberFormatException ex) {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                Msg.send(p, "<red>Keine gültige Zahl.");
            }
            new TournamentAdminMenu(plugin).open(p);
        });
    }
}
