package de.kuby.kubysurvival.fishing;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /angeln: Level, Rute verbessern, Fische verkaufen, Fischbuch, Bestenliste und Turnier. */
public final class AngelMenu extends GuiMenu {

    private final KubySurvival addon;

    public AngelMenu(KubySurvival addon, KubyPlugins core) {
        super(core, Msg.parse("<dark_aqua>Angeln"), 5);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        FishingService fishing = addon.fishing();
        FishingService.Angler a = fishing.angler(p.getUniqueId());
        int level = FishingService.levelOf(a.xp);
        long next = FishingService.xpFor(level + 1);

        set(4, Items.head(p, "<aqua>" + p.getName(), List.of(
                "<gray>Angel-Level: <white>" + level,
                "<gray>XP: <white>" + a.xp + " <dark_gray>/ " + next,
                "<gray>Rute: <white>Stufe " + a.rod + " <dark_gray>/ " + fishing.maxRod(),
                "<gray>Gefangen: <white>" + a.total() + " Fische")), null);

        set(20, Items.make(Material.KNOWLEDGE_BOOK, "<aqua>Fischbuch", List.of(
                "<gray>Alle Fischarten, deine Fänge", "<gray>und deine Rekorde.", "", "<yellow>Klick zum Öffnen")),
                e -> new FishBookMenu(addon, plugin).open(p));

        long cost = fishing.rodUpgradeCost(a.rod);
        List<String> rodLore = new ArrayList<>(List.of(
                "<gray>Stufe <white>" + a.rod + " <dark_gray>/ " + fishing.maxRod(),
                "<gray>Jede Stufe: seltene Fische öfter,", "<gray>Fische beißen schneller.", ""));
        rodLore.add(cost > 0 ? "<yellow>Klick: verbessern für <gold>" + Msg.coins(cost) + " Coins" : "<green>Höchste Stufe erreicht");
        set(22, Items.make(Material.FISHING_ROD, "<aqua>Rute verbessern", rodLore), e -> {
            String err = fishing.upgradeRod(p);
            if (err != null) Msg.send(p, "<red>" + err);
            open(p);
        });

        long value = fishing.inventoryValue(p);
        set(24, Items.make(Material.EMERALD, "<green>Alle Fische verkaufen", List.of(
                "<gray>Wert im Inventar: <gold>" + Msg.coins(value) + " Coins", "",
                value > 0 ? "<yellow>Klick zum Verkaufen" : "<dark_gray>Keine Fische dabei")), e -> {
            String err = fishing.sellAll(p);
            if (err != null) Msg.send(p, "<red>" + err);
            open(p);
        });

        set(30, Items.make(Material.GOLD_INGOT, "<gold>Bestenliste", List.of(
                "<gray>Die besten Angler und", "<gray>die schwersten Fänge.", "", "<yellow>Klick zum Öffnen")),
                e -> new AngelTopMenu(addon, plugin).open(p));

        List<String> tLore = new ArrayList<>();
        if (fishing.tournamentRunning()) {
            long left = fishing.tournamentSecondsLeft();
            tLore.add("<green>Läuft! <gray>Noch " + left / 60 + ":" + String.format("%02d", left % 60));
            tLore.add("<gray>Dein Gewicht: <white>" + String.format(Locale.GERMANY, "%.2f kg", fishing.tournamentWeight(p.getUniqueId())));
            tLore.add("");
            int rank = 1;
            for (Map.Entry<UUID, Double> e : fishing.tournamentTop(3)) {
                String name = org.bukkit.Bukkit.getOfflinePlayer(e.getKey()).getName();
                tLore.add("<gold>" + rank++ + ". <white>" + (name == null ? "?" : name)
                        + " <gray>" + String.format(Locale.GERMANY, "%.2f kg", e.getValue()));
            }
        } else {
            tLore.add("<gray>Gerade läuft kein Turnier.");
            tLore.add("<gray>Admins starten eins mit");
            tLore.add("<white>/kp angel turnier start <Minuten>");
        }
        set(32, Items.make(Material.CLOCK, "<aqua>Angel-Turnier", tLore), null);

        addBack(p);
    }
}
