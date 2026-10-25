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

/** Teleport-Effekte auswählen und kaufen. */
public final class TeleportEffectMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public TeleportEffectMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("teleport-effekte")), 4);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 36; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        UUID id = p.getUniqueId();
        int slot = 0;
        for (CosmeticService.Item it : plugin.cosmetics().catalogue(p.getUniqueId())) {
            if (!it.kind().equals("TELEPORT")) continue;
            if (slot >= SLOTS.length) break;
            boolean owned = plugin.cosmetics().owns(id, it);
            boolean active = it.id().equals(plugin.cosmetics().activeId(id, "TELEPORT"));
            boolean locked = !owned && plugin.battlepass().level(id) < it.bpLevel();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Seltenheit: " + it.rarity().color + it.rarity().label);
            lore.add("");
            if (owned) {
                lore.add("<green>✔ Freigeschaltet");
                lore.add(active ? "<yellow>Klick: ablegen" : "<yellow>Klick: anlegen");
            } else if (locked) {
                lore.add("<red>✘ Battlepass-Stufe " + it.bpLevel() + " nötig");
            } else {
                lore.add("<gold>Kosten: " + price(it));
                lore.add("<yellow>Klick: kaufen");
            }
            ItemStack item = Items.make(owned ? Material.NAME_TAG : (locked ? Material.BARRIER : Material.ENDER_PEARL),
                    it.rarity().color + it.label(), lore);
            if (active) glint(item);
            set(SLOTS[slot++], item, e -> onClick(p, it));
        }
        set(31, Items.make(Material.ARROW, "<gray>Zurück zur Kosmetik", List.of()),
                e -> new CosmeticsMenu(plugin).open(p));
    }

    private String price(CosmeticService.Item it) {
        if (it.price() <= 0 || it.currency() == CosmeticService.Currency.NONE) return "kostenlos";
        return switch (it.currency()) {
            case MONEY -> Msg.coins(it.price()) + "$";
            case XP -> Msg.coins(it.price()) + " XP";
            case JEWELS -> Msg.coins(it.price()) + " Juwelen";
            default -> "kostenlos";
        };
    }

    private void onClick(Player p, CosmeticService.Item it) {
        if (!plugin.cosmetics().owns(p.getUniqueId(), it)) {
            String err = plugin.cosmetics().buy(p, it);
            Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
            Msg.send(p, err == null ? "<green>" + it.label() + " freigeschaltet!" : "<red>" + err);
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
