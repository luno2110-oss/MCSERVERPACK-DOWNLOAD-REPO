package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /quest: Tages- und Wochen-Quests, Geschichte, Team-Quest, geheime Quests, Meilensteine, Serie und Abholen. */
public final class QuestMenu extends GuiMenu {

    private final KubySurvival addon;

    public QuestMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<gold>Quests"), 6);
        this.addon = addon;
    }

    static String bar(long done, long goal) {
        int pct = (int) Math.min(100, done * 100 / Math.max(1, goal));
        int filled = pct / 10;
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(10 - filled) + " <gray>" + Msg.coins(done) + "/" + Msg.coins(goal);
    }

    static String untilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Berlin"));
        Duration d = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay());
        return d.toHours() + " Std. " + d.toMinutesPart() + " Min.";
    }

    @Override
    protected void build(Player p) {
        QuestService q = addon.quests();
        UUID id = p.getUniqueId();
        q.checkStats(p);
        List<String> open = q.claimable(id);

        set(4, Items.head(p, "<gold>Deine Quests", List.of(
                "<gray>Serie: <white>" + q.streak(id) + " Tage <dark_gray>(beste: " + q.bestStreak(id) + ")",
                "<gray>Geschafft: <white>" + q.total(id) + " Quests",
                "<gray>Abholbereit: <gold>" + open.size(), "",
                "<dark_gray>Hol jeden Tag mindestens eine",
                "<dark_gray>Tages-Quest ab, dann wächst deine Serie.")), null);
        set(8, Items.make(open.isEmpty() ? Material.CHEST : Material.CHEST_MINECART,
                (open.isEmpty() ? "<gray>" : "<gold>") + "Alles abholen", List.of(
                        open.isEmpty() ? "<gray>Gerade ist nichts fertig." : "<gray>" + open.size() + " Belohnung(en) warten.",
                        "", "<yellow>Klick")), e -> {
            int n = q.claimAll(p);
            if (n == 0) Msg.send(p, "<gray>Gerade gibt es nichts abzuholen.");
            open(p);
        });
        set(0, Items.make(Material.GOLDEN_HELMET, "<yellow>Rangliste", List.of("<gray>Wer die meisten Quests geschafft hat", "", "<yellow>Klick")), e -> {
            p.closeInventory();
            p.performCommand("quest top");
        });

        set(9, Items.make(Material.SUNFLOWER, "<yellow>Tages-Quests", List.of("<gray>Neue in <white>" + untilMidnight())), null);
        List<QuestService.Def> dailies = q.dailies(id);
        for (int i = 0; i < dailies.size() && i < 8; i++) quest(p, 10 + i, "d:" + dailies.get(i).id(), dailies.get(i), "<yellow>");
        set(18, Items.make(Material.CLOCK, "<aqua>Wochen-Quests", List.of("<gray>Neue jeden Montag")), null);
        List<QuestService.Def> weeklies = q.weeklies(id);
        for (int i = 0; i < weeklies.size() && i < 8; i++) quest(p, 19 + i, "w:" + weeklies.get(i).id(), weeklies.get(i), "<aqua>");

        QuestService.Def chapter = q.storyChapter(id);
        if (chapter != null) {
            quest(p, 28, "s:" + chapter.id(), chapter, "<gold>Kapitel " + (q.storyIndex(id) + 1) + "/" + q.storySize() + ": ");
        } else {
            set(28, Items.make(Material.WRITTEN_BOOK, "<gold>Die Geschichte", List.of("<green>Alle Kapitel geschafft!")), null);
        }
        QuestService.Def td = q.teamDef(id);
        if (td != null) {
            quest(p, 30, "t:" + td.id(), td, "<green>Team: ");
        } else {
            set(30, Items.make(Material.WHITE_BANNER, "<green>Team-Quest", List.of("<gray>Nur mit einem Survival-Team.",
                    "<gray>Jede Woche eine gemeinsame Aufgabe.", "", "<white>/team create <Name>")), null);
        }
        List<QuestService.Def> found = q.secretsFound(id);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Gefunden: <white>" + found.size() + " <dark_gray>/ " + q.secretCount());
        lore.add("");
        for (QuestService.Def s : found) {
            lore.add((q.isClaimed(id, "x:" + s.id()) ? "<green>✔ " : "<gold>! ") + "<white>" + s.name() + " <dark_gray>– " + s.task());
        }
        if (found.size() < q.secretCount()) lore.add("<dark_gray>??? Der Rest bleibt geheim.");
        set(32, Items.make(Material.ENDER_EYE, "<light_purple>Geheime Quests", lore), e -> {
            if (q.claimAll(p) > 0) open(p);
        });
        set(34, Items.make(Material.NETHER_STAR, "<gold>Meilensteine", List.of("<gray>Stufen für Level, Spielzeit,", "<gray>Fische, Serie und mehr", "", "<yellow>Klick")),
                e -> new MilestoneMenu(addon).open(p));

        boolean off = "-".equals(q.trackedKey(id));
        set(45, Items.make(off ? Material.GRAY_DYE : Material.LIME_DYE, "<white>Anzeige oben: " + (off ? "<red>aus" : "<green>an"), List.of(
                "<gray>Die verfolgte Quest steht oben", "<gray>am Bildschirm. Ohne Auswahl", "<gray>das aktuelle Kapitel.", "", "<yellow>Klick zum Umschalten")), e -> {
            q.setTracked(id, off ? null : "-");
            open(p);
        });
        addBack(p);
    }

    private void quest(Player p, int slot, String key, QuestService.Def def, String prefix) {
        QuestService q = addon.quests();
        UUID id = p.getUniqueId();
        boolean done = q.isDone(id, key);
        boolean claimed = q.isClaimed(id, key);
        boolean tracked = key.equals(q.trackedKey(id));
        List<String> lore = new ArrayList<>();
        lore.add("<white>" + def.task());
        lore.add(bar(q.progressOf(id, key), def.amount()));
        lore.add("");
        lore.add("<gray>Belohnung: " + QuestService.rewardText(def.coins(), def.tokens(), def.xp()));
        lore.add("");
        if (claimed) {
            lore.add("<green>✔ Abgeholt");
        } else if (done) {
            lore.add("<gold>Klick: Belohnung abholen");
        } else {
            lore.add(tracked ? "<green>Wird oben angezeigt <gray>(Klick: nicht mehr)" : "<yellow>Klick: oben anzeigen");
        }
        ItemStack it = Items.make(claimed ? Material.LIME_STAINED_GLASS_PANE : done ? Material.CHEST : def.icon(),
                prefix + (claimed ? "<gray>" : "<white>") + def.name() + (tracked ? " <green>◆" : ""), lore);
        set(slot, it, e -> {
            if (claimed) return;
            if (done) {
                String err = q.claim(p, key);
                if (err != null) Msg.send(p, "<red>" + err);
            } else {
                q.setTracked(id, tracked ? null : key);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            }
            open(p);
        });
    }
}
