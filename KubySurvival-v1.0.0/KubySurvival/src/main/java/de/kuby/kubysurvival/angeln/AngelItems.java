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
 * Fisch-Items: eigene Textur (kuby:fish/<id>, Varianten kuby:fish/<id>_silber|gold|diamant), Name in Seltenheitsfarbe,
 * Sterne, Gewicht, Wert. Gleiche Art, Sterne, Gewicht (gerundet, siehe itemKg) und Variante stapeln sich. Nicht essbar,
 * nicht zu braten (siehe AngelListener).
 */
public final class AngelItems {

    private static final NamespacedKey KEY_ID = new NamespacedKey("kubysurvival", "angel_fish");
    private static final NamespacedKey KEY_STARS = new NamespacedKey("kubysurvival", "angel_stars");
    private static final NamespacedKey KEY_KG = new NamespacedKey("kubysurvival", "angel_kg");
    private static final NamespacedKey KEY_VARIANT = new NamespacedKey("kubysurvival", "angel_variant");

    /** Gelesener Fisch: Art, Sterne, Gewicht (alte Items ohne Gewicht: Mitte des Stern-Bereichs) und Variante. */
    public record FishItem(FishSpecies species, int stars, double kg, FishVariant variant) {}

    /** Gewicht auf dem Item, gröber gerundet, damit sich gleiche Fische stapeln: unter 1 kg 0,05, unter 10 kg 0,1, unter 100 kg 1, sonst 10. */
    public static double itemKg(double kg) {
        double step = kg < 1 ? 0.05 : kg < 10 ? 0.1 : kg < 100 ? 1 : 10;
        double r = Math.round(kg / step) * step;
        return Math.round(Math.max(step, r) * 100) / 100.0;
    }

    /** Typisches Gewicht für einen Stern-Wert (für alte Items ohne Gewicht). */
    static double kgForStars(FishSpecies s, int stars) {
        double q = switch (stars) {
            case 1 -> 0.2;
            case 2 -> 0.47;
            case 3 -> 0.7;
            case 4 -> 0.87;
            default -> 0.97;
        };
        return s.minKg() + (s.maxKg() - s.minKg()) * Math.pow(q, 1.6);
    }

    private AngelItems() {
    }

    public static ItemStack fish(FishSpecies s, int stars, int amount) {
        return fish(s, stars, kgForStars(s, stars), FishVariant.NORMAL, amount);
    }

    public static ItemStack fish(FishSpecies s, int stars, double kg, FishVariant v, int amount) {
        double ikg = itemKg(kg);
        ItemStack item = new ItemStack(Material.COD, amount);
        item.unsetData(DataComponentTypes.FOOD);
        item.unsetData(DataComponentTypes.CONSUMABLE);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", v.model(s)));
        if (v.special()) {
            meta.displayName(Msg.item("<b><gradient:" + v.hex() + ":" + v.light + ":" + v.hex() + ">" + Msg.escape(v.name(s))
                    + "</gradient></b>"));
            meta.setEnchantmentGlintOverride(true);
        } else {
            meta.displayName(Component.text(s.name(), s.rarity().color()).decoration(TextDecoration.ITALIC, false));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text()
                .append(Component.text("★".repeat(stars), NamedTextColor.YELLOW))
                .append(Component.text("★".repeat(5 - stars), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false).build());
        if (v.special()) lore.add(line("Variante: ", v.label + " (Wert x" + v.mult + ")", v.color()));
        lore.add(line("Seltenheit: ", s.rarity().label, s.rarity().color()));
        lore.add(line("Gewicht: ", AngelText.kg(ikg), NamedTextColor.WHITE));
        lore.add(line("Gewässer: ", s.water().label, NamedTextColor.AQUA));
        lore.add(line("Wert: ", AngelText.money(AngelService.value(s, stars, ikg, v)) + " (ohne Boni)", NamedTextColor.GOLD));
        lore.add(Component.empty());
        lore.add(Msg.item("<dark_gray>Verkaufen beim Fischhändler · /angeln"));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_ID, PersistentDataType.STRING, s.id());
        pdc.set(KEY_STARS, PersistentDataType.INTEGER, stars);
        pdc.set(KEY_KG, PersistentDataType.DOUBLE, ikg);
        if (v.special()) pdc.set(KEY_VARIANT, PersistentDataType.STRING, v.id);
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
        int st = stars == null ? 1 : Math.max(1, Math.min(5, stars));
        Double kg = pdc.get(KEY_KG, PersistentDataType.DOUBLE);
        return new FishItem(s, st, kg == null ? kgForStars(s, st) : kg, FishVariant.byId(pdc.get(KEY_VARIANT, PersistentDataType.STRING)));
    }

    public static boolean isFish(ItemStack it) {
        return read(it) != null;
    }

    private static final NamespacedKey KEY_ROD = new NamespacedKey("kubysurvival", "angel_rod");

    /** Angel aus dem Angel-Shop: eigenes Modell (eingeholt/ausgeworfen), Name in ihrer Farbe, Vorteile in der Beschreibung. */
    public static ItemStack rod(Rod r) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "angeln/rod_" + r.id));
        meta.displayName(Component.text(r.label, r.textColor()).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, r.jewels));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Glück: ", "+" + AngelText.dec(r.luck) + " %", NamedTextColor.GREEN));
        if (r.faster > 0) lore.add(line("Wartezeit: ", "-" + AngelText.dec(r.faster) + " %", NamedTextColor.GREEN));
        lore.add(Msg.item("<#F2DA90>✦ " + Msg.escape(r.perk)));
        lore.add(Component.empty());
        lore.add(Msg.item("<dark_gray>Vorteile gelten beim Angeln mit dieser Angel · /angeln"));
        meta.lore(lore);
        if (r.unbreakable()) meta.setUnbreakable(true);
        if (r.jewels) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(KEY_ROD, PersistentDataType.STRING, r.id);
        item.setItemMeta(meta);
        return item;
    }

    /** Welche Shop-Angel das Item ist (null = keine). */
    public static Rod rodOf(ItemStack it) {
        if (it == null || it.getType() != Material.FISHING_ROD || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(KEY_ROD, PersistentDataType.STRING);
        return id == null ? null : Rod.byId(id);
    }
}
