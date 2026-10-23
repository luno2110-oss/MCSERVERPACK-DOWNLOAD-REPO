package de.kuby.kubysurvival.fishing;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Bestenliste: Top 10 Angler nach XP und der Server-Rekord je Fischart. */
public final class AngelTopMenu extends GuiMenu {

    private static final int[] TOP_SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 22, 23};

    private final KubySurvival addon;

    public AngelTopMenu(KubySurvival addon, KubyPlugins core) {
        super(core, Msg.parse("<dark_aqua>Angel-Bestenliste"), 5);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        FishingService fishing = addon.fishing();
        List<Map.Entry<UUID, FishingService.Angler>> top = fishing.topAnglers(TOP_SLOTS.length);
        for (int i = 0; i < top.size(); i++) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(top.get(i).getKey());
            FishingService.Angler a = top.get(i).getValue();
            set(TOP_SLOTS[i], Items.head(op, "<gold>" + (i + 1) + ". <white>" + (op.getName() == null ? "?" : op.getName()), List.of(
                    "<gray>Angel-Level: <white>" + FishingService.levelOf(a.xp),
                    "<gray>XP: <white>" + a.xp,
                    "<gray>Gefangen: <white>" + a.total())), null);
        }
        List<String> records = new ArrayList<>();
        for (FishingService.FishType t : fishing.types()) {
            Map.Entry<UUID, Double> r = fishing.recordOf(t.id());
            if (r == null) {
                records.add(t.display() + " <dark_gray>— noch keiner");
            } else {
                String name = Bukkit.getOfflinePlayer(r.getKey()).getName();
                records.add(t.display() + " <white>" + String.format(Locale.GERMANY, "%.2f kg", r.getValue())
                        + " <gray>(" + (name == null ? "?" : name) + ")");
            }
        }
        set(4, Items.make(Material.TROPICAL_FISH, "<aqua>Server-Rekorde", records), null);
        set(40, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> new AngelMenu(addon, plugin).open(p));
    }
}
