package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.DailyService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class DailyMenu extends GuiMenu {

    private static final int CLAIM_SLOT = 25;
    private static final int PROGRESS_SLOT = 7;

    public DailyMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Tageskalender")), 6);
    }

    @Override
    protected void build(Player p) {
        DailyService daily = plugin.daily();
        int done = daily.claimedDays(p.getUniqueId());
        boolean canClaim = daily.canClaim(p.getUniqueId());

        // 30 Tage in 5 Reihen à 6 Tage, Spalten 7 und 8 bleiben für Info und Aktion frei
        for (int day = 1; day <= DailyService.CYCLE; day++) {
            int i = day - 1;
            int slot = (i / 6) * 9 + (i % 6);

            Material mat;
            String color;
            if (day <= done) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                color = "<green>";
            } else if (day == done + 1 && canClaim) {
                mat = Material.YELLOW_STAINED_GLASS_PANE;
                color = "<gold>";
            } else {
                mat = Material.GRAY_STAINED_GLASS_PANE;
                color = "<dark_gray>";
            }

            set(slot, Items.make(mat, color + SmallCaps.of("Tag") + " " + day, List.of(
                    "<gray>Belohnung: <gold>" + Msg.coins(daily.rewardFor(day)) + "$")), null);
        }

        set(PROGRESS_SLOT, Items.make(Material.BOOK, "<aqua>" + SmallCaps.of("Dein Fortschritt"), List.of(
                "<gray>Tage: <white>" + done + "<gray>/<white>" + DailyService.CYCLE)), null);

        if (canClaim) {
            set(CLAIM_SLOT, Items.make(Material.EMERALD, "<green>" + SmallCaps.of("Heute abholen"), List.of(
                    "<gray>Klick zum Einsammeln")), e -> claim(p));
        } else {
            set(CLAIM_SLOT, Items.make(Material.CLOCK, "<yellow>" + SmallCaps.of("Schon abgeholt"), List.of(
                    "<gray>Morgen wieder vorbeischauen")), null);
        }

        addBack(p);
    }

    private void claim(Player p) {
        if (!plugin.discord().allow(p, "require-for-daily")) return;
        int day = plugin.daily().claim(p.getUniqueId());
        if (day == 0) {
            Msg.send(p, "<red>Heute schon abgeholt.");
        } else {
            long reward = Math.round(plugin.daily().rewardFor(day) * plugin.booster().multiplier());
            plugin.economy().deposit(p.getUniqueId(), reward);
            if (day % 10 == 0) {
                plugin.jewels().add(p.getUniqueId(), 25);
                Msg.send(p, "<aqua>Meilenstein-Bonus: +25 Juwelen!");
            } else if (java.util.concurrent.ThreadLocalRandom.current().nextInt(5) == 0) {
                plugin.stats().get(p.getUniqueId()).xp += 50;
                Msg.send(p, "<green>Glücksbonus: +50 XP!");
            }
            Msg.send(p, "<green>Tag " + day + " abgeholt: <gold>+" + Msg.coins(reward) + "$"); Sfx.play(plugin, p, Sfx.Kind.COIN); plugin.battlepass().progress(p.getUniqueId(), "daily", 1);
        }
        open(p);
    }
}
