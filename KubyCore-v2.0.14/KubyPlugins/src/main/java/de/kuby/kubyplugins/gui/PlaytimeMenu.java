package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.PlaytimeService;
import de.kuby.kubyplugins.service.PlaytimeService.Prize;
import de.kuby.kubyplugins.service.PlaytimeService.Stage;
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

/** Spielzeit-Stufen als Truhe (ohne UI-Pack): jede Stufe ein Item, Klick holt ab, unten „Alle abholen“ und Seiten. */
public final class PlaytimeMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private int page;

    public PlaytimeMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("spielzeit")), 5);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 45; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        PlaytimeService pt = plugin.playtime();
        String mode = plugin.modes().modeOf(p.getUniqueId());
        long act = pt.activeMinutes(p.getUniqueId(), mode);
        String blocked = pt.blocked(p);
        List<String> head = new ArrayList<>();
        head.add("<gray>Aktiv gespielt: <white>" + PlaytimeScreen.duration(act));
        head.add("<dark_gray>Wer sich nicht bewegt, zählt nicht.");
        if (blocked != null) head.add("<red>" + blocked);
        set(4, Items.make(Material.CLOCK, "<#F2C84A>Deine Spielzeit", head), null);

        List<Stage> stages = pt.stages(mode);
        int pages = Math.max(1, (stages.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * SLOTS.length;
        for (int i = 0; from + i < stages.size() && i < SLOTS.length; i++) {
            Stage s = stages.get(from + i);
            boolean done = pt.isClaimed(p.getUniqueId(), mode, s);
            boolean ready = !done && act >= s.minutes();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Ab " + s.hours() + " Stunden aktiver Spielzeit");
            for (Prize pr : s.prizes()) lore.add("<dark_gray>• <white>" + pt.describe(pr));
            lore.add("");
            if (done) lore.add("<green>✔ Abgeholt");
            else if (ready) lore.add(blocked == null ? "<yellow>Klick: abholen" : "<red>Erst Discord verlinken (/verify)");
            else lore.add("<red>Noch " + PlaytimeScreen.duration(s.minutes() - act));
            ItemStack item = Items.make(done ? Material.MINECART : ready ? Material.CHEST_MINECART : Material.CHEST,
                    (done ? "<gray>" : "<#F2C84A>") + s.number() + ". " + s.title(), lore);
            if (ready) {
                ItemMeta meta = item.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
            set(SLOTS[i], item, e -> {
                if (!ready) return;
                String err = pt.claim(p, s);
                if (err != null) {
                    Sfx.play(plugin, p, Sfx.Kind.ERROR);
                    Msg.send(p, "<red>" + err);
                } else {
                    Sfx.play(plugin, p, Sfx.Kind.LEVEL_UP);
                }
                open(p);
            });
        }
        int n = pt.ready(p.getUniqueId(), mode).size();
        set(40, Items.make(n > 0 ? Material.HOPPER : Material.GRAY_DYE, n > 0 ? "<#F2C84A>Alle abholen (" + n + ")" : "<gray>Alle abholen",
                List.of(n > 0 ? "<gray>Holt alle fertigen Stufen ab" : "<gray>Gerade ist keine Stufe fertig")), e -> {
            if (n == 0) return;
            int got = pt.claimAll(p);
            if (got < 0) Msg.send(p, "<red>" + pt.blocked(p));
            else Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
            open(p);
        });
        set(36, Items.make(Material.ARROW, "<gray>Zurück zum Menü", List.of()), e -> new MainMenu(plugin).open(p));
        if (page > 0) set(39, Items.make(Material.SPECTRAL_ARROW, "<gray>Vorige Seite", List.of()), e -> {
            page--;
            open(p);
        });
        if (page + 1 < pages) set(41, Items.make(Material.SPECTRAL_ARROW, "<gray>Nächste Seite", List.of()), e -> {
            page++;
            open(p);
        });
    }
}
