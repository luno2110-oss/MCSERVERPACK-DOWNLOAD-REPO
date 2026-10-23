package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.PlaytimeService;
import de.kuby.kubyplugins.service.PlaytimeService.Reward;
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

/** Spielzeit-Belohnungen: jede Belohnung wird nach einer bestimmten Spielzeit freigeschaltet. */
public final class PlaytimeMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public PlaytimeMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("spielzeit")), 5);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 45; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        PlaytimeService pt = plugin.playtime();
        long mins = pt.minutesOf(p.getUniqueId());
        set(4, Items.make(Material.CLOCK, "<aqua>Deine Spielzeit", List.of(
                "<gray>" + mins / 60 + " Std. " + mins % 60 + " Min.",
                "<dark_gray>Belohnungen werden automatisch freigeschaltet")), null);

        List<Reward> rewards = pt.rewards();
        for (int i = 0; i < rewards.size() && i < SLOTS.length; i++) {
            Reward r = rewards.get(i);
            boolean done = pt.isClaimed(p.getUniqueId(), r);
            boolean ready = !done && mins >= r.minutes();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Ab " + r.minutes() + " Minuten Spielzeit");
            lore.add("<gray>Belohnung: " + describe(r));
            lore.add("");
            if (done) {
                lore.add("<green>✔ Abgeholt");
            } else if (ready) {
                lore.add("<yellow>Klick: abholen");
            } else {
                lore.add("<red>Noch " + (r.minutes() - mins) + " Minuten");
            }
            ItemStack item = Items.make(iconOf(r), describeTitle(r), lore);
            if (ready) glint(item);
            set(SLOTS[i], item, e -> {
                String err = pt.claim(p, r);
                if (err != null) {
                    Sfx.play(plugin, p, Sfx.Kind.ERROR);
                    Msg.send(p, "<red>" + err);
                }
                open(p);
            });
        }
        set(40, Items.make(Material.ARROW, "<gray>Zurück zum Menü", List.of()),
                e -> new MainMenu(plugin).open(p));
    }

    private String describe(Reward r) {
        return switch (r.type()) {
            case "COINS" -> "<gold>" + r.value() + "$";
            case "XP" -> "<green>" + r.value() + " XP";
            case "JEWELS" -> "<aqua>" + r.value() + " Juwelen";
            case "TAG" -> {
                String d = plugin.tags().displayOf(r.value());
                yield "Titel " + (d == null ? r.value() : d);
            }
            default -> "<light_purple>Spezial-Belohnung";
        };
    }

    private String describeTitle(Reward r) {
        return switch (r.type()) {
            case "TAG" -> "<gold>Titel";
            case "COINS" -> "<gold>Geld";
            case "XP" -> "<green>XP";
            case "JEWELS" -> "<aqua>Juwelen";
            default -> "<light_purple>Belohnung";
        };
    }

    private static Material iconOf(Reward r) {
        return switch (r.type()) {
            case "COINS" -> Material.GOLD_NUGGET;
            case "XP" -> Material.EXPERIENCE_BOTTLE;
            case "JEWELS" -> Material.EMERALD;
            case "TAG" -> Material.NAME_TAG;
            default -> Material.TRIPWIRE_HOOK;
        };
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }
}
