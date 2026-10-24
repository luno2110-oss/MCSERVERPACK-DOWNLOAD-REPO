package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fisch-Items: eigene Textur (kuby:fish/<id>), Name in Seltenheitsfarbe, Sterne. Gleiche Art und Sterne stapeln sich;
 * das Gewicht zählt nur beim Fang (Rekorde). Nicht essbar, nicht zu braten (siehe AngelListener).
 */
public final class AngelItems {

    private static final NamespacedKey KEY_ID = new NamespacedKey("kubysurvival", "angel_fish");
    private static final NamespacedKey KEY_STARS = new NamespacedKey("kubysurvival", "angel_stars");

    /** Gelesener Fisch: Art und Sterne. */
    public record FishItem(FishSpecies species, int stars) {}

    private AngelItems() {
    }

    public static ItemStack fish(FishSpecies s, int stars, int amount) {
        ItemStack item = new ItemStack(Material.COD, amount);
        item.unsetData(DataComponentTypes.FOOD);
        item.unsetData(DataComponentTypes.CONSUMABLE);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "fish/" + s.id()));
        meta.displayName(Component.text(s.name(), s.rarity().color()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text()
                .append(Component.text("★".repeat(stars), NamedTextColor.YELLOW))
                .append(Component.text("★".repeat(5 - stars), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false).build());
        lore.add(line("Seltenheit: ", s.rarity().label, s.rarity().color()));
        lore.add(line("Gewässer: ", s.water().label, NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Msg.item("<dark_gray>Verkaufen beim Fischhändler · /angeln"));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_ID, PersistentDataType.STRING, s.id());
        pdc.set(KEY_STARS, PersistentDataType.INTEGER, stars);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String label, String value, TextColor color) {
        return Component.text().append(Component.text(label, NamedTextColor.GRAY)).append(Component.text(value, color))
                .decoration(TextDecoration.ITALIC, false).build();
    }

    public static FishItem read(ItemStack it) {
        if (it == null || it.getType() != Material.COD || !it.hasItemMeta()) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(KEY_ID, PersistentDataType.STRING);
        if (id == null) return null;
        FishSpecies s = FishSpecies.byId(id);
        if (s == null) return null;
        Integer stars = pdc.get(KEY_STARS, PersistentDataType.INTEGER);
        return new FishItem(s, stars == null ? 1 : Math.max(1, Math.min(5, stars)));
    }

    public static boolean isFish(ItemStack it) {
        return read(it) != null;
    }
}
