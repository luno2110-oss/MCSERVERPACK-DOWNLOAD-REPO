package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.IslandService.Island;
import de.kuby.kubyskyblock.service.IslandService.Upgrade;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /insel ohne Pack: Truhe mit Zuhause, Besuchern, Upgrades, Mitgliedern, Level und Top-Inseln. */
public final class IslandMenu extends GuiMenu {

    public IslandMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Insel")), 4);
    }

    @Override
    protected String chestTheme() {
        return "sky";
    }

    private void result(Player p, String err) {
        Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    @Override
    protected void build(Player p) {
        IslandService is = KubySkyblock.get().islands();
        Island i = is.islandOf(p.getUniqueId());
        if (i == null) return;
        boolean owner = p.getUniqueId().equals(i.owner);
        set(4, Items.make(Material.GRASS_BLOCK, "<aqua>" + Msg.escape(i.name), List.of(
                "<gray>Level: <white>" + is.levelOf(i) + " <dark_gray>(" + i.points + " Punkte)",
                "<gray>Platz: <white>#" + is.rank(i),
                "<gray>Größe: <white>" + (2 * is.radius(i) + 1) + " x " + (2 * is.radius(i) + 1),
                "<gray>Besucher: <white>" + (i.open ? "erlaubt" : "gesperrt"))), null);
        set(10, Items.make(Material.RED_BED, "<green>Nach Hause", List.of()), e -> {
            p.closeInventory();
            is.home(p, i);
        });
        set(11, Items.make(Material.COMPASS, "<green>Zuhause hier setzen", List.of()), e -> {
            result(p, is.setHome(p));
            refresh(p);
        });
        if (owner) set(12, Items.make(i.open ? Material.OAK_DOOR : Material.IRON_DOOR, "<yellow>Besucher: " + (i.open ? "an" : "aus"),
                List.of("<gray>Klick: umschalten")), e -> {
            result(p, is.toggleOpen(p));
            refresh(p);
        });
        set(13, Items.make(Material.GOLD_INGOT, "<gold>Top-Inseln", List.of()), e -> {
            p.closeInventory();
            IslandScreen.top(p);
        });
        set(14, Items.make(Material.EXPERIENCE_BOTTLE, "<aqua>Level zählen", List.of()), e -> {
            p.closeInventory();
            String err = is.recalc(i, pts -> Msg.send(p, "<aqua>Insel-Level: <white>" + is.levelOf(i) + " <gray>(" + pts + " Punkte)"));
            if (err != null) result(p, err);
        });
        int slot = 19;
        for (Upgrade u : Upgrade.values()) {
            long price = is.price(i, u);
            Material m = switch (u) {
                case SIZE -> Material.GRASS_BLOCK;
                case SLOTS -> Material.PLAYER_HEAD;
                case GENERATOR -> Material.COBBLESTONE;
                case GROWTH -> Material.WHEAT;
                case SPAWN -> Material.ZOMBIE_SPAWN_EGG;
                case LIMIT -> Material.HOPPER;
            };
            String name = switch (u) {
                case SIZE -> "Größe";
                case SLOTS -> "Mitglieder";
                case GENERATOR -> "Generator";
                case GROWTH -> "Wachstum";
                case SPAWN -> "Spawnrate";
                case LIMIT -> "Limits";
            };
            set(slot++, Items.make(m, "<gold>" + name + " <gray>" + UpgradeTier.label(is.level(i, u), is.maxLevel(u)), List.of(price < 0
                    ? "<green>Höchstes Level" : "<gray>Nächstes Level: <gold>" + Msg.coins(price) + " $", "<yellow>Klick: kaufen")), e -> {
                result(p, is.upgrade(p, u));
                refresh(p);
            });
        }
        int ms = 28;
        for (UUID m : i.everyone()) {
            if (ms > 30) break;
            boolean isOwner = m.equals(i.owner);
            List<String> lore = new ArrayList<>();
            lore.add(isOwner ? "<gold>Besitzer" : "<gray>Mitglied");
            if (owner && !isOwner) lore.add("<red>Shift + Klick: entfernen");
            set(ms++, Items.make(Material.PLAYER_HEAD, "<white>" + IslandService.nameOf(m), lore), owner && !isOwner ? e -> {
                if (e.isShiftClick()) {
                    result(p, is.kick(p, m));
                    refresh(p);
                }
            } : null);
        }
        set(31, Items.make(Material.BOOK, "<gray>Befehle", List.of(
                "<white>/insel einladen <Spieler>", "<white>/insel annehmen", "<white>/insel verlassen",
                "<white>/insel besuchen <Spieler>", "<white>/insel name <Name>", "<white>/insel neustart", "<white>/insel aufgeben")), null);
    }
}
