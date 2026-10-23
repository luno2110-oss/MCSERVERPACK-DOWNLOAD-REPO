package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Meilensteine mit Stufen. Klick holt die nächste erreichte Stufe ab. */
public final class MilestoneMenu extends GuiMenu {

    private static final Material[] ICONS = {Material.EXPERIENCE_BOTTLE, Material.IRON_PICKAXE, Material.COD, Material.CLOCK,
            Material.WRITABLE_BOOK, Material.BLAZE_POWDER, Material.GRASS_BLOCK, Material.EMERALD, Material.GOLD_INGOT};

    private final KubySurvival addon;

    public MilestoneMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<gold>Meilensteine"), 4);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        QuestService q = addon.quests();
        UUID id = p.getUniqueId();
        List<QuestService.Mile> list = q.milestones();
        for (int i = 0; i < list.size() && i < 18; i++) {
            QuestService.Mile m = list.get(i);
            int next = q.nextTier(id, m);
            long value = q.stat(m.stat(), id);
            boolean all = next > m.tiers().length;
            boolean ready = !all && value >= m.tiers()[next - 1];
            List<String> lore = new ArrayList<>();
            for (int t = 1; t <= m.tiers().length; t++) {
                String mark = t < next ? "<green>✔" : (value >= m.tiers()[t - 1] ? "<gold>!" : "<dark_gray>○");
                lore.add(mark + " <gray>Stufe " + QuestService.roman(t) + ": <white>" + Msg.coins(m.tiers()[t - 1])
                        + " <dark_gray>(" + QuestService.rewardText(m.coins() * t, m.tokens() * t, 0) + "<dark_gray>)");
            }
            lore.add("");
            lore.add("<gray>Dein Wert: <white>" + Msg.coins(value));
            lore.add(all ? "<green>Alle Stufen geschafft" : ready ? "<gold>Klick: Stufe " + QuestService.roman(next) + " abholen"
                    : "<gray>Nächste Stufe bei <white>" + Msg.coins(m.tiers()[next - 1]));
            int slot = 10 + i + (i >= 7 ? 2 : 0);
            set(slot, Items.make(all ? Material.LIME_STAINED_GLASS_PANE : ready ? Material.CHEST : ICONS[i % ICONS.length],
                    (all ? "<green>" : "<gold>") + m.name() + (next > 1 ? " " + QuestService.roman(Math.min(next - 1, m.tiers().length)) : ""), lore), e -> {
                if (!ready) return;
                String err = q.claim(p, "m:" + m.id() + ":" + next);
                if (err != null) Msg.send(p, "<red>" + err);
                open(p);
            });
        }
        set(31, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> new QuestMenu(addon).open(p));
    }
}
