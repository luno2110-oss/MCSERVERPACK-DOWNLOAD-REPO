package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.JewelStoreService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Editor für den Juwelenshop: Kategorien aktivieren, Artikel hinzufügen oder entfernen. */
public final class JewelStoreEditorMenu extends GuiMenu {

    private final String mode;
    private final int cat;

    public JewelStoreEditorMenu(KubyPlugins plugin, String mode, int cat) {
        super(plugin, Msg.parse("<dark_gray>Juwelenshop-Editor " + JewelStoreService.normalize(mode)), 5);
        this.mode = JewelStoreService.normalize(mode);
        this.cat = cat;
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 45; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        var cats = plugin.jewelStore().categories(mode);
        for (int i = 0; i < cats.size() && i < JewelStoreMenu.TABS.length; i++) {
            var c = cats.get(i);
            int idx = i;
            set(JewelStoreMenu.TABS[i], Items.make(c.icon(), "<white>" + c.name(), List.of(
                    c.active() ? "<green>Aktiv" : "<red>Inaktiv (Barriere)",
                    "<yellow>Klick: auswählen")), e -> new JewelStoreEditorMenu(plugin, mode, idx).open(p));
        }
        if (cats.isEmpty()) return;
        var c = cats.get(Math.min(cat, cats.size() - 1));
        int idxCat = Math.min(cat, cats.size() - 1);

        set(22, Items.make(c.active() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<yellow>Kategorie " + c.name() + ": " + (c.active() ? "<green>aktiv" : "<red>inaktiv"), List.of(
                        "<gray>Klick: umschalten")), e -> {
            plugin.jewelStore().toggle(mode, idxCat);
            Sfx.play(plugin, p, Sfx.Kind.CLICK);
            open(p);
        });

        if (c.type().equals("ITEMS")) {
            var items = plugin.jewelStore().items(mode, c.id());
            for (int i = 0; i < items.size() && i < JewelStoreMenu.CONTENT.length; i++) {
                var it = items.get(i);
                int itemIdx = i;
                set(JewelStoreMenu.CONTENT[i], Items.make(it.icon(), "<white>" + it.name(), List.of(
                        "<gray>Preis: <aqua>" + it.price() + " Juwelen",
                        "<gray>Befehl: <dark_gray>" + it.command(),
                        "",
                        "<red>Shift + Klick: entfernen")), e -> {
                    if (!e.isShiftClick()) return;
                    plugin.jewelStore().removeItem(mode, c.id(), itemIdx);
                    Sfx.play(plugin, p, Sfx.Kind.FAIL);
                    open(p);
                });
            }
            set(40, Items.make(Material.LIME_DYE, "<green>Artikel hinzufügen", List.of(
                    "<gray>Format im Chat:",
                    "<dark_gray>id;Name;ICON;Preis;Befehl;Lore",
                    "<gray>Befehl mit %player%")), e -> {
                plugin.prompts().ask(p, "Artikel eingeben (id;Name;ICON;Preis;Befehl;Lore):", text -> {
                    String err = plugin.jewelStore().addItem(mode, c.id(), text);
                    Msg.send(p, err == null ? "<green>Artikel hinzugefügt." : "<red>" + err);
                    open(p);
                });
                p.closeInventory();
            });
        } else {
            set(22, Items.make(Material.PAPER, "<gray>Kein Artikel-Inhalt", List.of(
                    "<dark_gray>Diese Kategorie hat keine Artikel")), null);
        }
        set(44, Items.make(Material.ARROW, "<gray>Schließen", List.of()), e -> p.closeInventory());
    }
}
