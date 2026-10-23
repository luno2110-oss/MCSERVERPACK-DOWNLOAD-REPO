package de.kuby.kubysurvival.farmpass;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Farmpass-Haustiere: die 4 Pass-Haustiere ansehen und pro bezahltem Monat eins zum Behalten wählen. */
public final class FarmpassPetMenu extends GuiMenu {

    private final KubySurvival addon;

    public FarmpassPetMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<dark_green>Farmpass-Haustiere"), 5);
        this.addon = addon;
    }

    private String nice(String id) {
        var k = addon.pets() == null ? null : addon.pets().kind(id);
        if (k != null) return k.name();
        String s = id.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void build(Player p) {
        FarmpassService fp = addon.farmpass();
        UUID id = p.getUniqueId();
        boolean active = fp.active(id);
        set(4, Items.make(Material.LEAD, "<gold>Haustiere", List.of(
                "<gray>Rufen und reiten im", "<gray>Haustier-Menü (<white>/pet<gray>).",
                "<gray>Auswahl offen: <white>" + fp.petPicksLeft(id))), null);
        int slot = 10;
        for (String pet : fp.passPets()) {
            set(slot++, Items.make(Material.NAME_TAG, (active ? "<green>" : "<gray>") + nice(pet), List.of(
                    "<gray>Nur mit aktivem Farmpass", active ? "<green>Freigeschaltet" : "<red>Pass nicht aktiv")), null);
        }
        List<String> chosen = fp.chosenPets(id);
        int picks = fp.petPicksLeft(id);
        slot = 19;
        for (String pet : fp.monthlyPets()) {
            if (slot > 34) break;
            boolean have = chosen.contains(pet);
            set(slot, Items.make(have ? Material.LIME_DYE : Material.EGG, (have ? "<green>" : "<white>") + nice(pet), List.of(
                    "<gray>Monats-Haustier, bleibt für immer", "",
                    have ? "<green>Gehört dir" : picks > 0 ? "<yellow>Klick: dieses wählen" : "<gray>Keine Auswahl offen")), e -> {
                if (have || picks <= 0) return;
                String err = fp.choosePet(id, pet);
                Msg.send(p, err == null ? "<green>" + nice(pet) + " gehört jetzt dir!" : "<red>" + err);
                open(p);
            });
            slot += slot % 9 == 7 ? 3 : 1;
        }
        set(40, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> new FarmpassMenu(addon).open(p));
    }
}
