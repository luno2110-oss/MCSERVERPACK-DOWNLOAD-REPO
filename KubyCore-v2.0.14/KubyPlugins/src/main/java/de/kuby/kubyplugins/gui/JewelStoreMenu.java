package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.JewelStoreService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Juwelenshop für einen Modus: Kategorien oben, Artikel darunter. */
public final class JewelStoreMenu extends GuiMenu {

    static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int[] TABS = {1, 2, 3, 4, 5, 6, 7};

    private final String mode;
    private final int cat;
    /** Geschenk-Modus: für wen gekauft wird (null = für sich selbst). */
    private final java.util.UUID giftTo;
    /** Zweimal klicken beim Verschenken: welcher Artikel scharf ist. */
    private String armed;

    public JewelStoreMenu(KubyPlugins plugin, String mode, int cat) {
        this(plugin, mode, cat, null);
    }

    public JewelStoreMenu(KubyPlugins plugin, String mode, int cat, Player giftTo) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of(giftTo == null ? "juwelenshop" : "geschenk für " + giftTo.getName())), 5);
        this.mode = JewelStoreService.normalize(mode);
        this.cat = cat;
        this.giftTo = giftTo == null ? null : giftTo.getUniqueId();
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 45; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        var cats = plugin.jewelStore().categories(mode);
        for (int i = 0; i < cats.size() && i < TABS.length; i++) {
            var c = cats.get(i);
            ItemStack tab = Items.make(c.icon(), (i == cat ? "<green>" : "<white>") + c.name(), List.of(
                    c.active() ? "<gray>Geöffnet" : "<gray>Demnächst"));
            if (i == cat) glint(tab);
            int idx = i;
            set(TABS[i], tab, e -> new JewelStoreMenu(plugin, mode, idx, giftTo == null ? null : org.bukkit.Bukkit.getPlayer(giftTo)).open(p));
        }
        if (cats.isEmpty()) return;
        var c = cats.get(Math.min(cat, cats.size() - 1));
        if (!c.active()) {
            set(22, Items.make(Material.BARRIER, "<gray>Demnächst verfügbar", List.of(
                    "<dark_gray>Dieser Bereich wird noch vorbereitet")), null);
        } else if (c.type().equals("PACKAGES") && giftTo != null) {
            set(22, Items.make(Material.BARRIER, "<gray>Juwelen kann man nicht verschenken", List.of(
                    "<dark_gray>Verschenken: Ränge, Keys und Pakete")), null);
        } else if (c.type().equals("PACKAGES")) {
            set(22, Items.make(Material.EMERALD, "<green>Juwelen-Pakete ansehen", List.of(
                    "<gray>Juwelen gibt es über den Store")), e -> new JewelShopMenu(plugin).open(p));
        } else if (c.type().equals("ITEMS")) {
            var items = plugin.jewelStore().items(mode, c.id());
            if (items.isEmpty()) {
                set(22, Items.make(Material.BARRIER, "<gray>Noch keine Artikel", List.of()), null);
            }
            Player target = giftTo == null ? null : org.bukkit.Bukkit.getPlayer(giftTo);
            for (int i = 0; i < items.size() && i < CONTENT.length; i++) {
                var it = items.get(i);
                if (giftTo != null) {
                    boolean sure = it.id().equals(armed);
                    String who = target == null ? "?" : target.getName();
                    ItemStack gift = Items.make(it.icon(), "<white>" + it.name() + " <gray>für <light_purple>" + who, List.of(
                            "<gray>" + it.lore(),
                            "",
                            "<gray>Preis: <aqua>" + Msg.coins(it.price()) + " Juwelen <gray>(von dir)",
                            sure ? "<gold><b>Nochmal klicken: verschenken" : "<yellow>Klick: an " + who + " verschenken"));
                    if (sure) glint(gift);
                    set(CONTENT[i], gift, e -> {
                        if (!it.id().equals(armed)) {
                            armed = it.id();
                            Sfx.play(plugin, p, Sfx.Kind.TICK);
                            refresh(p);
                            return;
                        }
                        armed = null;
                        Player t = org.bukkit.Bukkit.getPlayer(giftTo);
                        String err = plugin.jewelStore().buyFor(p, t, mode, it);
                        if (err != null) {
                            Sfx.play(plugin, p, Sfx.Kind.ERROR);
                            Msg.send(p, "<red>" + err);
                        } else {
                            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                            Msg.send(p, "<green>Du hast <white>" + t.getName() + "</white> " + it.name() + " geschenkt <gray>("
                                    + Msg.coins(it.price()) + " Juwelen).");
                            Msg.send(t, "<light_purple><b>Geschenk!</b> <white>" + p.getName() + " <gray>hat dir <white>" + it.name()
                                    + " <gray>geschenkt.");
                            Sfx.play(plugin, t, Sfx.Kind.SUCCESS_BIG);
                        }
                        refresh(p);
                    });
                    continue;
                }
                set(CONTENT[i], Items.make(it.icon(), "<white>" + it.name(), List.of(
                        "<gray>" + it.lore(),
                        "",
                        "<gray>Preis: <aqua>" + Msg.coins(it.price()) + " Juwelen",
                        "<yellow>Klick: kaufen")), e -> {
                    String err = plugin.jewelStore().buy(p, mode, it);
                    if (err != null) {
                        Sfx.play(plugin, p, Sfx.Kind.ERROR);
                        Msg.send(p, "<red>" + err);
                    } else {
                        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                        Msg.send(p, "<green>" + it.name() + " gekauft.");
                    }
                    open(p);
                });
            }
        }
        if (giftTo == null) {
            set(38, Items.make(Material.CAKE, "<light_purple>Verschenken", List.of(
                    "<gray>Ränge, Keys und Pakete für andere",
                    "<gray>Spieler, bezahlt mit deinen Juwelen",
                    "", "<yellow>/juwelenshop gift <Spieler>")), null);
        }
        set(42, Items.make(Material.EMERALD, "<green>Deine Juwelen: <white>" + Msg.coins(plugin.jewels().balance(p.getUniqueId())),
                List.of()), null);
        set(40, Items.make(Material.ARROW, "<gray>Schließen", List.of()), e -> p.closeInventory());
    }

    static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }
}
