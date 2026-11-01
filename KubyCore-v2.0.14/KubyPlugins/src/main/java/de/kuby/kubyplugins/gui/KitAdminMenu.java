package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Kits verwalten (Admins, /kits bearbeiten): alle Kits mit Rang, Abklingzeit und Modi; Klick öffnet den Editor. */
public final class KitAdminMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    public KitAdminMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Kits bearbeiten")), 6);
    }

    @Override
    protected String chestTheme() {
        return "steel";
    }

    @Override
    protected void build(Player p) {
        List<KitService.Kit> kits = plugin.kits().kits();
        for (int i = 0; i < kits.size() && i < SLOTS.length; i++) {
            KitService.Kit kit = kits.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>ID: <white>" + kit.id());
            lore.add("<gray>Inhalt: <white>" + KitService.describe(kit));
            lore.add("<gray>Rang: <white>" + (kit.rank().isEmpty() ? "für alle" : "ab " + KitService.rankName(kit.rank())));
            lore.add("<gray>Abklingzeit: <white>" + KitEditMenu.minutes(kit.cooldownMinutes()));
            lore.add("<gray>Modi: <white>" + KitEditMenu.modesText(kit.modes()));
            lore.add("");
            lore.add("<yellow>Klick: bearbeiten");
            set(SLOTS[i], Items.make(kit.icon(), "<gold>" + kit.name(), lore), e -> new KitEditMenu(plugin, kit.id()).open(p));
        }
        if (kits.size() < SLOTS.length) {
            set(49, Items.make(Material.LIME_DYE, "<green>Neues Kit", List.of("<gray>Legt ein leeres Kit an und öffnet den Editor.")),
                    e -> new KitEditMenu(plugin, plugin.kits().create().id()).open(p));
        }
        set(45, Items.make(Material.ARROW, "<gray>" + SmallCaps.of("Zurück"), List.of("<gray>Zu den Kits")),
                e -> new ProgressScreens(plugin).kits(p));
        set(4, Items.make(Material.BOOK, "<gold>Kits bearbeiten", List.of(
                "<gray>Klick auf ein Kit öffnet den Editor:",
                "<gray>Items hineinlegen, Name, Icon, Rang,",
                "<gray>Abklingzeit und Modi einstellen.",
                "<gray>Gespeichert in kits-config.yml.")), null);
    }
}
