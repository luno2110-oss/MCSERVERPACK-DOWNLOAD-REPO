package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /job: acht Berufe (wählen, Level, Aufgaben), Spielerlevel, Stundenlimit, Wochenauftrag und Token-Shop. */
public final class JobMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    private final KubySurvival addon;

    public JobMenu(KubySurvival addon, KubyPlugins core) {
        super(core, Msg.parse("<dark_green>Jobs"), 6);
        this.addon = addon;
    }

    static String bar(int percent) {
        int filled = Math.max(0, Math.min(10, percent / 10));
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(10 - filled) + " <gray>" + percent + "%";
    }

    @Override
    protected void build(Player p) {
        JobService jobs = addon.jobs();
        JobService.Worker w = jobs.worker(p.getUniqueId());
        int level = JobService.playerLevel(w);
        long from = JobService.playerXpFor(level);
        long to = JobService.playerXpFor(level + 1);
        int pct = (int) Math.min(99, (w.totalXp - from) * 100 / Math.max(1, to - from));

        set(4, Items.head(p, "<gold>Level " + level, List.of(
                bar(pct),
                "<gray>XP: <white>" + Msg.coins(w.totalXp) + " <dark_gray>/ " + Msg.coins(to),
                "<gray>Tokens: <light_purple>" + w.tokens,
                "", "<dark_gray>Jeder Aufstieg bringt Tokens.")), null);

        long wait = jobs.cooldownMinutes(p.getUniqueId());
        Job[] all = Job.values();
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            Job j = all[i];
            int jl = jobs.jobLevel(w, j);
            List<String> lore = new ArrayList<>();
            for (String t : j.tasks) lore.add("<gray>▪ " + t);
            lore.add("");
            lore.add("<gray>Level <white>" + jl + " <dark_gray>/ " + jobs.maxLevel());
            lore.add(bar(jobs.progress(w, j)));
            lore.add("");
            if (w.job == j) {
                lore.add("<green>Dein aktueller Job");
            } else if (wait > 0) {
                lore.add("<red>Wechsel in " + wait + " Min. möglich");
            } else {
                lore.add("<yellow>Klick: diesen Job wählen");
                if (w.job != null) lore.add("<dark_gray>Danach " + addon.core().getConfig().getLong("jobs.switch-cooldown-minutes", 30)
                        + " Min. Wartezeit, dein Level bleibt.");
            }
            String name = j.color + j.display + (w.job == j ? " <green>✔" : "");
            set(SLOTS[i], Items.make(j.icon, name, lore), e -> {
                String err = jobs.choose(p, j);
                if (err != null) {
                    Msg.send(p, "<red>" + err);
                } else {
                    Msg.send(p, "<green>Du bist jetzt " + j.color + j.display + "<green>.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
                }
                open(p);
            });
        }

        long cap = jobs.hourlyCap(w);
        set(38, Items.make(Material.CLOCK, "<yellow>Stundenlimit", List.of(
                "<gray>Diese Stunde: <gold>" + Msg.coins(w.earnedHour) + " <dark_gray>/ " + Msg.coins(cap) + " Coins",
                "<gray>Neu in <white>" + JobService.minutesToNextHour() + " Min.",
                "", "<dark_gray>Mit jedem Job-Level steigt das", "<dark_gray>Limit und die Aufgaben zahlen mehr.")), null);
        set(40, Items.make(Material.AMETHYST_SHARD, "<light_purple>Token-Shop", List.of(
                "<gray>Du hast <light_purple>" + w.tokens + " Tokens<gray>.", "", "<yellow>Klick zum Öffnen")),
                e -> new TokenShopMenu(addon, plugin).open(p));
        set(42, Items.make(Material.WRITABLE_BOOK, "<gold>Wochenauftrag", orderLore(jobs, w)), null);
        addBack(p);
    }

    static List<String> orderLore(JobService jobs, JobService.Worker w) {
        List<String> lore = new ArrayList<>();
        if (w.orderJob == null || w.orderGoal <= 0) {
            lore.add("<gray>Wähle einen Job, dann bekommst");
            lore.add("<gray>du deinen Auftrag für diese Woche.");
            return lore;
        }
        lore.add("<gray>Sammle <white>" + Msg.coins(w.orderGoal) + " Job-XP <gray>als " + w.orderJob.color + w.orderJob.display);
        lore.add(bar((int) Math.min(100, w.orderDone * 100 / Math.max(1, w.orderGoal))));
        lore.add("");
        if (w.orderFinished) {
            lore.add("<green>Erledigt! Nächste Woche gibt es einen neuen.");
        } else {
            lore.add("<gray>Belohnung: <gold>" + Msg.coins(jobs.orderCoins()) + " Coins <light_purple>+" + jobs.orderTokens() + " Tokens");
            if (w.job != w.orderJob) lore.add("<red>Zählt nur, solange du " + w.orderJob.display + " bist.");
        }
        return lore;
    }
}
