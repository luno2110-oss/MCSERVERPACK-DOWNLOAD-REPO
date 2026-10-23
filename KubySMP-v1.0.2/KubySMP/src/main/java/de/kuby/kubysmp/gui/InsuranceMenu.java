package de.kuby.kubysmp.gui;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.gui.GuiMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubysmp.service.RaidInsuranceService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Übersicht der eigenen Versicherungen. Shift-Klick kündigt (ohne Erstattung). */
public final class InsuranceMenu extends GuiMenu {

    public InsuranceMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>Raid-Versicherung"), 4);
    }

    @Override
    protected void build(Player p) {
        RaidInsuranceService ins = KubySMP.get().insurance();
        set(4, Items.make(Material.SHIELD, "<aqua>Raid-Versicherung", List.of(
                "<gray>Auszahlung bei Raid: <gold>" + ins.payoutPercent() + " % <gray>des Werts",
                "<gray>Prämie: <gold>" + ins.premiumPercent() + " % <gray>des Werts",
                "<gray>Laufzeit: <white>" + ins.days() + " Tag(e)",
                "<gray>Versichern: <white>/versichern <gray>(Blick auf die Kiste)")), null);

        int slot = 9;
        for (RaidInsuranceService.Policy pol : ins.ownedBy(p.getUniqueId())) {
            if (slot > 26) break;
            long hours = Math.max(0, (pol.expires() - System.currentTimeMillis()) / 3_600_000L);
            set(slot++, Items.make(Material.CHEST, "<gold>Kiste " + pol.x() + " / " + pol.y() + " / " + pol.z(), List.of(
                    "<gray>Welt: <white>" + pol.world(),
                    "<gray>Versicherter Wert: <gold>" + Msg.coins(pol.value()) + "$",
                    "<gray>Auszahlung: <gold>" + Msg.coins(ins.payoutOf(pol)) + "$",
                    "<gray>Läuft ab in <white>" + hours + " h",
                    "<red>Shift + Klick: kündigen (ohne Erstattung)")),
                    e -> {
                        if (!e.isShiftClick()) return;
                        String err = ins.cancel(p.getUniqueId(), pol);
                        Sfx.play(plugin, p, err == null ? Sfx.Kind.CLICK : Sfx.Kind.ERROR);
                        Msg.send(p, err == null ? "<gray>Versicherung gekündigt." : "<red>" + err);
                        open(p);
                    });
        }
        if (slot == 9) {
            set(22, Items.make(Material.BARRIER, "<gray>Keine versicherten Kisten", List.of(
                    "<gray>Schau auf eine Kiste und tippe /versichern")), null);
        }
        addBack(p);
    }
}
