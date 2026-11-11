package de.kuby.kubysmp.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.staff.KadminScreens;
import de.kuby.kubyplugins.staff.KadminScreens.Btn;
import de.kuby.kubyplugins.staff.KadminScreens.Page;
import de.kuby.kubyplugins.staff.KadminScreens.Row;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysmp.KubySMP;
import de.kuby.kubysmp.service.TournamentService;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SMP im Kadmin als Dialog (statt Truhe): Kampf (Kopfgelder, laufende Duelle) und Turnier-Steuerung. Ohne Pack die alten Truhen. */
public final class SmpAdminScreens {

    private static final TextColor GOLD = TextColor.color(0xF2DA90), RED = TextColor.color(0xFF5A5A), GREEN = TextColor.color(0x6BF07A),
            GRAY = TextColor.color(0xACA48E);

    private final KubyPlugins plugin;

    public SmpAdminScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private KadminScreens k() {
        return plugin.staffMenus() == null ? null : plugin.staffMenus().screens();
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private void back(Player p) {
        if (plugin.staffMenus() != null) plugin.staffMenus().kadmin(p);
    }

    public void combat(Player p) {
        if (k() == null || !plugin.uiPack().active(p)) {
            new AdminCombatMenu(plugin).open(p);
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, Long> en : KubySMP.get().bounties().all().entrySet()) {
            UUID id = en.getKey();
            rows.add(new Row(Material.PLAYER_HEAD, "Kopfgeld auf " + name(id), GOLD, null, Canvas.num(en.getValue()) + " $", GOLD,
                    List.of(new Btn("Entfernen", false, pl -> k().confirm(pl, "Kopfgeld entfernen", "Das Kopfgeld auf " + name(id) + " verfällt.", "Entfernen", q -> {
                        long amount = KubySMP.get().bounties().claim(id);
                        Sfx.play(plugin, q, Sfx.Kind.SUCCESS);
                        Msg.send(q, "<gray>Kopfgeld entfernt (" + Msg.coins(amount) + " $).");
                        combat(q);
                    }, this::combat), "Kopfgeld entfernen")), null, null));
        }
        List<UUID> seen = new ArrayList<>();
        for (UUID id : KubySMP.get().duels().activeDuelPlayers()) {
            if (seen.contains(id)) continue;
            seen.add(id);
            rows.add(new Row(Material.IRON_SWORD, "Duell: " + name(id), RED, "läuft gerade", null, null,
                    List.of(new Btn("Abbrechen", false, pl -> k().confirm(pl, "Duell abbrechen", "Beide bekommen Inventar und Einsatz zurück.", "Abbrechen", q -> {
                        KubySMP.get().duels().adminCancel(id);
                        Sfx.play(plugin, q, Sfx.Kind.SUCCESS);
                        Msg.send(q, "<gray>Duell abgebrochen.");
                        combat(q);
                    }, this::combat), "Duell abbrechen (Einsätze zurück)")), null, null));
        }
        k().show(p, new Page(KitData.Icon.IRON_SWORD, "Kampf", "Kopfgelder und laufende Duelle", List.of(), rows.subList(0, Math.min(rows.size(), 16)),
                0, 1, null, null, this::back, "Keine Kopfgelder und keine laufenden Duelle."));
    }

    public void tournament(Player p) {
        if (k() == null || !plugin.uiPack().active(p)) {
            new TournamentAdminMenu(plugin).open(p);
            return;
        }
        TournamentService t = KubySMP.get().tournaments();
        TournamentService.State state = t.state();
        String stateText = switch (state) {
            case CLOSED -> "kein Turnier";
            case REGISTRATION -> "Anmeldung offen";
            case RUNNING -> "läuft, Runde " + t.round();
        };
        List<Btn> top = new ArrayList<>();
        if (state == TournamentService.State.CLOSED) top.add(new Btn("Anmeldung öffnen", true, pl -> result(pl, t.open()), "Spieler können sich anmelden"));
        if (state == TournamentService.State.REGISTRATION) top.add(new Btn("Starten", true, pl -> result(pl, t.start()), "Paart die Teilnehmer zufällig"));
        if (state != TournamentService.State.CLOSED) top.add(new Btn("Abbrechen", false, pl -> k().confirm(pl, "Turnier abbrechen",
                "Laufende Duelle enden ohne Wertung.", "Abbrechen", q -> result(q, t.cancel()), this::tournament), "Turnier abbrechen"));
        top.add(new Btn("Ankündigung", false, pl -> k().ask(pl, "Ankündigung", "Text an alle Spieler", "", 200, (q, text) -> {
            if (text != null && !text.isBlank()) plugin.getServer().broadcast(Msg.parse("<gold>[Turnier] <white>" + Msg.escape(text.trim())));
            tournament(q);
        }, this::tournament), "Nachricht an alle"));
        List<Row> rows = new ArrayList<>();
        rows.add(k().numberRow(p, Material.GOLD_INGOT, "Preis", "$ für den Sieger", "tournament.reward", 0, 1_000_000_000L, this::tournament));
        rows.add(k().numberRow(p, Material.EXPERIENCE_BOTTLE, "Trophäen pro Sieg", "Bei jedem gewonnenen Match", "tournament.trophies-per-win", 0, 10_000, this::tournament));
        rows.add(k().numberRow(p, Material.TOTEM_OF_UNDYING, "Trophäen für den Titel", "Bonus für den Sieger", "tournament.trophies-champion", 0, 100_000, this::tournament));
        rows.add(k().numberRow(p, Material.PLAYER_HEAD, "Max. Teilnehmer", "Zwischen 2 und 64", "tournament.max-players", 2, 64, this::tournament));
        for (UUID[] pair : t.matches()) {
            rows.add(new Row(Material.IRON_SWORD, name(pair[0]) + " gegen " + name(pair[1]), GOLD, "Match läuft", null, null, List.of(), null, null));
        }
        boolean reg = state == TournamentService.State.REGISTRATION;
        for (UUID id : reg ? new ArrayList<>(t.entrants()) : t.alive()) {
            rows.add(new Row(Material.PLAYER_HEAD, name(id), reg ? GRAY : GREEN, reg ? "angemeldet" : "noch im Turnier", null, null,
                    reg ? List.of(new Btn("Entfernen", false, pl -> result(pl, t.kick(id)), "Aus der Anmeldung entfernen")) : List.of(), null, null));
        }
        k().show(p, new Page(KitData.Icon.STAR, "Turnier", stateText + " · " + t.entrants().size() + " angemeldet · "
                + t.alive().size() + " im Turnier", top, rows.subList(0, Math.min(rows.size(), 16)), 0, 1, null, null, this::back, null));
    }

    private void result(Player p, String error) {
        Sfx.play(plugin, p, error == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (error != null) Msg.send(p, "<red>" + error);
        tournament(p);
    }
}
