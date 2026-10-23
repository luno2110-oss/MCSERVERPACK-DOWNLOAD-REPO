package de.kuby.kubysurvival.fishing;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Fischbuch: jede Fischart mit Anzahl, Rekord und Wert. Noch nicht gefangene Arten bleiben verdeckt. */
public final class FishBookMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final KubySurvival addon;

    public FishBookMenu(KubySurvival addon, KubyPlugins core) {
        super(core, Msg.parse("<dark_aqua>Fischbuch"), 4);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        FishingService fishing = addon.fishing();
        FishingService.Angler a = fishing.angler(p.getUniqueId());
        List<FishingService.FishType> types = fishing.types();
        int found = 0;
        for (int i = 0; i < types.size() && i < SLOTS.length; i++) {
            FishingService.FishType t = types.get(i);
            int count = a.caught.getOrDefault(t.id(), 0);
            if (count > 0) found++;
            String rarity = "<gray>Seltenheit: " + "<yellow>" + "★".repeat(1 + t.index() * 4 / Math.max(1, types.size() - 1));
            if (count == 0) {
                set(SLOTS[i], Items.make(Material.GRAY_DYE, "<dark_gray>???", List.of(rarity, "<gray>Noch nicht gefangen.")), null);
            } else {
                set(SLOTS[i], Items.make(t.material(), t.display(), List.of(
                        rarity,
                        "<gray>Gefangen: <white>" + count + "x",
                        "<gray>Dein Rekord: <white>" + String.format(Locale.GERMANY, "%.2f kg", a.best.getOrDefault(t.id(), 0.0)),
                        "<gray>Gewicht: <white>" + String.format(Locale.GERMANY, "%.2f bis %.2f kg", t.minKg(), t.maxKg()),
                        "<gray>Grundwert: <gold>" + Msg.coins(t.price()) + " Coins")), null);
            }
        }
        set(4, Items.make(Material.KNOWLEDGE_BOOK, "<aqua>Fortschritt", List.of(
                "<white>" + found + " <gray>von <white>" + types.size() + " <gray>Fischarten entdeckt")), null);
        set(31, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> new AngelMenu(addon, plugin).open(p));
    }
}
