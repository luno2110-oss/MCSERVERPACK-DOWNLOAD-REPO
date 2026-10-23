package de.kuby.kubysurvival.pets;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** /pet: eigene Haustiere (Lieblinge zuerst) rufen, umbenennen, markieren; gesperrte mit Quelle oder zum Kaufen. */
public final class PetMenu extends GuiMenu {

    private final KubySurvival addon;

    public PetMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<gold>Haustiere"), 6);
        this.addon = addon;
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
        int slot = 0;
        for (PetService.Kind k : list) {
            if (slot >= 45) break;
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
            }
            String title = (own ? (out ? "<green>" : "<white>") : "<dark_gray>") + Msg.escape(own ? ps.displayName(id, k) : k.name())
                    + (fav ? " <gold>★" : "") + (out ? " <green>(gerufen)" : "");
            set(slot++, Items.make(own ? egg(k) : Material.GRAY_DYE, title, lore), e -> {
                if (!own) {
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
        addBack(p);
    }
}
