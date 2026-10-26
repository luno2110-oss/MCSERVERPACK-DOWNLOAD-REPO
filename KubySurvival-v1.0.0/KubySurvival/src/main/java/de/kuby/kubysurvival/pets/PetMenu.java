package de.kuby.kubysurvival.pets;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * /pet: eigene Haustiere (Lieblinge zuerst) rufen, umbenennen, markieren; gesperrte mit Quelle oder zum Kaufen.
 * 45 je Seite; Shop-Pets zeigen ihr Bild aus dem Pack.
 */
public final class PetMenu extends GuiMenu {

    private static final int PER_PAGE = 45;
    private final KubySurvival addon;
    private final int page;

    public PetMenu(KubySurvival addon) {
        this(addon, 0);
    }

    public PetMenu(KubySurvival addon, int page) {
        super(addon.core(), Msg.parse("<gold>Haustiere"), 6);
        this.addon = addon;
        this.page = Math.max(0, page);
    }

    /** Symbol: Shop-Pets ihr Bild, sonst das Spawn-Ei. */
    static ItemStack icon(PetService.Kind k, ItemStack base) {
        if (k.model() == null || k.model().isBlank()) return base;
        ItemMeta meta = base.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(k.model()));
        base.setItemMeta(meta);
        return base;
    }

    static Material egg(PetService.Kind k) {
        Material m = Material.matchMaterial(k.type().name() + "_SPAWN_EGG");
        return m == null ? Material.NAME_TAG : m;
    }

    @Override
    protected void build(Player p) {
        PetService ps = addon.pets();
        UUID id = p.getUniqueId();
        String activeId = ps.activeKind(id);
        List<PetService.Kind> list = new ArrayList<>(ps.kinds());
        list.sort(Comparator.comparing((PetService.Kind k) -> !ps.owns(p, k))
                .thenComparing(k -> !ps.favorite(id, k))
                .thenComparing(PetService.Kind::name));
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.min(page, pages - 1);
        int slot = 0;
        for (PetService.Kind k : list.subList(pg * PER_PAGE, Math.min(list.size(), (pg + 1) * PER_PAGE))) {
            boolean own = ps.owns(p, k);
            boolean fav = own && ps.favorite(id, k);
            boolean out = k.id().equals(activeId);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + (k.rideable() ? "Reitbar" : "Begleiter") + (k.baby() ? " · klein" : ""));
            if (own) {
                if (!ps.displayName(id, k).equals(k.name())) lore.add("<gray>Art: <white>" + k.name());
                lore.add("");
                lore.add(out ? "<yellow>Linksklick: wegschicken" : "<yellow>Linksklick: rufen");
                lore.add("<yellow>Rechtsklick: " + (fav ? "kein Liebling mehr" : "als Liebling markieren"));
                lore.add("<yellow>Shift + Linksklick: umbenennen");
            } else {
                lore.add("");
                lore.add("<gray>Woher: <white>" + k.source());
                if (k.price() > 0) lore.add("<yellow>Klick: kaufen für <gold>" + Msg.coins(k.price()) + " Coins");
                if (k.jewels() > 0) lore.add("<yellow>Klick: im Juwelenshop ansehen <dark_gray>(" + Msg.coins(k.jewels()) + " Juwelen)");
            }
            String title = (own ? (out ? "<green>" : "<white>") : "<dark_gray>") + Msg.escape(own ? ps.displayName(id, k) : k.name())
                    + (fav ? " <gold>★" : "") + (out ? " <green>(gerufen)" : "");
            ItemStack it = Items.make(own ? egg(k) : Material.GRAY_DYE, title, lore);
            set(slot++, own ? icon(k, it) : it, e -> {
                if (!own) {
                    if (k.jewels() > 0) {
                        p.closeInventory();
                        p.performCommand("juwelenshop pets");
                        return;
                    }
                    if (k.price() <= 0) return;
                    String err = ps.buy(p, k);
                    Msg.send(p, err == null ? "<green>" + k.name() + " gehört jetzt dir!" : "<red>" + err);
                    open(p);
                    return;
                }
                ClickType c = e.getClick();
                if (c == ClickType.RIGHT || c == ClickType.SHIFT_RIGHT) {
                    ps.toggleFavorite(id, k);
                    open(p);
                } else if (c == ClickType.SHIFT_LEFT) {
                    addon.core().prompts().ask(p, "Neuer Name für " + ps.displayName(id, k) + "?", text -> {
                        String err = ps.rename(id, k, text);
                        Msg.send(p, err == null ? "<green>Dein Haustier heißt jetzt <gold>" + Msg.escape(text) : "<red>" + err);
                    });
                } else if (out) {
                    ps.dismiss(id);
                    open(p);
                } else {
                    String err = ps.summon(p, k);
                    if (err != null) Msg.send(p, "<red>" + err);
                    p.closeInventory();
                }
            });
        }
        if (activeId != null) {
            PetService.Kind k = ps.kind(activeId);
            if (k != null && k.rideable()) {
                set(47, Items.make(Material.SADDLE, "<white>Aufsteigen", List.of("<gray>/pet ride", "", "<yellow>Klick")), e -> {
                    p.closeInventory();
                    String err = ps.ride(p);
                    if (err != null) Msg.send(p, "<red>" + err);
                });
            }
            set(51, Items.make(Material.BARRIER, "<red>Haustier wegschicken", List.of("<gray>/pet dismiss", "", "<yellow>Klick")), e -> {
                ps.dismiss(id);
                open(p);
            });
        }
        if (pg > 0) set(45, Items.make(Material.ARROW, "<white>« Vorige Seite", List.of("<gray>Seite " + pg + " von " + pages)),
                e -> new PetMenu(addon, pg - 1).open(p));
        if (pg + 1 < pages) set(53, Items.make(Material.ARROW, "<white>Nächste Seite »", List.of("<gray>Seite " + (pg + 2) + " von " + pages)),
                e -> new PetMenu(addon, pg + 1).open(p));
        addBack(p);
    }
}
