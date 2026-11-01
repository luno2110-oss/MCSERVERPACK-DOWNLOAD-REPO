package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.TemplateService;
import org.bukkit.entity.Player;

import java.util.List;

/** Wahl der Insel-Vorlage als Truhe (ohne Pack): beim ersten Start oder beim Neustart (reset). */
public final class TemplateMenu extends GuiMenu {

    private final boolean reset;

    public TemplateMenu(KubyPlugins plugin, boolean reset) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of(reset ? "Insel neu starten" : "Wähle deine Insel")), 3);
        this.reset = reset;
    }

    @Override
    protected String chestTheme() {
        return "sky";
    }

    @Override
    protected void build(Player p) {
        List<TemplateService.Template> ts = KubySkyblock.get().templates().all();
        int slot = 10;
        for (TemplateService.Template t : ts) {
            if (slot > 16) break;
            set(slot++, Items.make(t.icon(), "<aqua>" + t.name(), List.of("<gray>" + t.desc(), "",
                    reset ? "<red>Shift + Klick: Insel löschen und neu bauen" : "<yellow>Klick: wählen")), e -> {
                if (reset && !e.isShiftClick()) return;
                p.closeInventory();
                String err = reset ? KubySkyblock.get().islands().reset(p, t.id()) : KubySkyblock.get().islands().create(p, t.id());
                if (err != null) {
                    Sfx.play(plugin, p, Sfx.Kind.ERROR);
                    Msg.send(p, "<red>" + err);
                }
            });
        }
    }
}
