package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CosmeticService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Kosmetik-Übersicht: Spuren oben, Kill-Effekte darunter. Seltenheit über Farbe und Icon. */
public final class CosmeticsMenu extends GuiMenu {

    private static final int[] TRAIL_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] KILL_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public CosmeticsMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("kosmetik")), 6);
    }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        for (int i = 0; i < 54; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        set(9, Items.make(Material.AMETHYST_SHARD, "<light_purple>Spuren", List.of(
                "<gray>Folgen dir beim Laufen")), null);
        set(27, Items.make(Material.WITHER_SKELETON_SKULL, "<red>Kill-Effekte", List.of(
                "<gray>Erscheinen am Ort des Gegners")), null);

        int t = 0;
        int k = 0;
        for (CosmeticService.Item it : plugin.cosmetics().catalogue()) {
            boolean trail = it.kind().equals("TRAIL");
            int slot;
            if (trail) {
                if (t >= TRAIL_SLOTS.length) continue;
                slot = TRAIL_SLOTS[t++];
            } else {
                if (k >= KILL_SLOTS.length) continue;
                slot = KILL_SLOTS[k++];
            }
            set(slot, display(it, id, trail), e -> onClick(p, it));
        }

        set(45, Items.make(Material.ARROW, "<gray>Zurück zum Menü", List.of()),
                e -> new MainMenu(plugin).open(p));
        String trailName = nameOf(plugin.cosmetics().activeId(id, "TRAIL"));
        String killName = nameOf(plugin.cosmetics().activeId(id, "KILL"));
        set(47, Items.make(Material.ENDER_PEARL, "<light_purple>Teleport-Effekte", List.of(
                "<gray>Effekte für jeden Teleport")),
                e -> new TeleportEffectMenu(plugin).open(p));
        set(49, Items.make(Material.BOOK, "<gold>Aktiv", List.of(
                "<gray>Spur: <white>" + trailName,
                "<gray>Kill-Effekt: <white>" + killName)), null);
    }

    private String nameOf(String itemId) {
        if (itemId == null) return "keine";
        CosmeticService.Item it = plugin.cosmetics().find(itemId);
        return it == null ? "keine" : it.label();
    }

    private ItemStack display(CosmeticService.Item it, UUID id, boolean trail) {
        boolean owned = plugin.cosmetics().owns(id, it);
        boolean active = it.id().equals(plugin.cosmetics().activeId(id, it.kind()));
        boolean locked = !owned && plugin.battlepass().level(id) < it.bpLevel();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Seltenheit: " + it.rarity().color + it.rarity().label);
        lore.add("<gray>Art: <white>" + (trail ? "Spur" : "Kill-Effekt"));
        lore.add("");
        if (owned) {
            lore.add("<green>✔ Freigeschaltet");
            lore.add(active ? "<yellow>Klick: ablegen" : "<yellow>Klick: anlegen");
        } else if (locked) {
            lore.add("<red>✘ Battlepass-Stufe " + it.bpLevel() + " nötig");
        } else {
            lore.add("<gold>Kosten: " + priceText(it));
            lore.add("<yellow>Klick: kaufen");
        }
        Material icon = owned ? Material.NAME_TAG : locked ? Material.BARRIER : dyeOf(it.rarity());
        ItemStack item = Items.make(icon, it.rarity().color + it.label(), lore);
        if (active) glint(item);
        return item;
    }

    private static String priceText(CosmeticService.Item it) {
        if (it.price() <= 0 || it.currency() == CosmeticService.Currency.NONE) return "kostenlos";
        return switch (it.currency()) {
            case MONEY -> Msg.coins(it.price()) + "$";
            case XP -> Msg.coins(it.price()) + " XP";
            case JEWELS -> Msg.coins(it.price()) + " Juwelen";
            default -> "kostenlos";
        };
    }

    private static Material dyeOf(CosmeticService.Rarity r) {
        return switch (r) {
            case COMMON -> Material.GRAY_DYE;
            case RARE -> Material.LIGHT_BLUE_DYE;
            case EPIC -> Material.PURPLE_DYE;
            case LEGENDARY -> Material.ORANGE_DYE;
        };
    }

    private void onClick(Player p, CosmeticService.Item it) {
        if (!plugin.cosmetics().owns(p.getUniqueId(), it)) {
            String err = plugin.cosmetics().buy(p, it);
            if (err != null) {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                Msg.send(p, "<red>" + err);
            } else {
                Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                Msg.send(p, "<green>" + it.label() + " freigeschaltet!");
            }
        } else {
            plugin.cosmetics().toggle(p.getUniqueId(), it);
            Sfx.play(plugin, p, Sfx.Kind.PING);
        }
        open(p);
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }
}
