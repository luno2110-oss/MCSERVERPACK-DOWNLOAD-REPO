package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.BiomeIslandService;
import de.kuby.kubyskyblock.service.BiomeIslandService.BIsland;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.IslandService.Island;
import de.kuby.kubyskyblock.service.LimitService;
import de.kuby.kubyskyblock.service.TopRewardService;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Weitere Insel-Dialoge im Himmels-Look: Inseln besuchen (offene Inseln und Biom-Inseln), Preise der Top-Inseln und
 * Limits der eigenen Insel. Ohne Pack: Listen im Chat mit Klick-Befehlen.
 */
public final class IslandExtraScreen {

    static final int W = IslandScreen.W;
    static final TextColor SKY = IslandScreen.SKY, CLOUD = IslandScreen.CLOUD, MIST = IslandScreen.MIST, GOLD = IslandScreen.GOLD,
            GREEN = IslandScreen.GREEN, RED = IslandScreen.RED, DIM = IslandScreen.DIM;
    static final int PER_PAGE = 8;

    private IslandExtraScreen() {
    }

    private static KubySkyblock sb() {
        return KubySkyblock.get();
    }

    private static Consumer<Player> run(Consumer<Player> c) {
        return p -> {
            p.closeDialog();
            c.accept(p);
        };
    }

    private static void feedback(Player p, String err) {
        Sfx.play(sb().core(), p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    /** Kopf: Bild, Titel, Unterzeile, Zurück. */
    private static Canvas head(String title, String sub, KitData.Sprite icon, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading(title, SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - icon.width) / 2, 11 + (32 - icon.height) / 2, icon);
        c.headline(50, 12, title, SKY);
        c.text(50, 36, Canvas.fit(sub, false, W - 50 - 90), MIST, false);
        c.smallButton(W - 8 - 70, 18, 70, KitData.Small.DARK, "Zurück", CLOUD, false);
        if (back != null) c.action(W - 8 - 70, 18, 70, KitData.SMALL_H, back, "Zurück");
        return c;
    }

    // =================================================================================================================
    // Besuchen
    // =================================================================================================================

    /** Zeile der Besuchsliste: Icon (null = Platz-Nummer), Name, Unterzeile, Farbe der Unterzeile, bis zu zwei Knöpfe. */
    public record Row(int rank, KitData.Icon icon, String name, String sub, TextColor subColor, boolean mine,
                      String b1, KitData.Small s1, Consumer<Player> a1, String b2, KitData.Small s2, Consumer<Player> a2) {}

    public record VisitView(int tab, List<Row> rows, int page, int pages, String empty, Consumer<Player> tab0, Consumer<Player> tab1,
                            Consumer<Player> prev, Consumer<Player> next, Consumer<Player> random, Consumer<Player> back) {}

    /** Bild je Biom: das typische Tier (Spawn-Ei), dafür baut man die Biom-Inseln ja. */
    private static final Map<String, KitData.Icon> BIOME_ICON = Map.ofEntries(
            Map.entry("jungle", KitData.Icon.EGG_PARROT), Map.entry("desert", KitData.Icon.EGG_CAMEL), Map.entry("mushroom_fields", KitData.Icon.EGG_COW),
            Map.entry("cherry_grove", KitData.Icon.P_CHERRY_LEAVES), Map.entry("swamp", KitData.Icon.EGG_FROG), Map.entry("snowy_taiga", KitData.Icon.EGG_FOX),
            Map.entry("savanna", KitData.Icon.EGG_HORSE), Map.entry("mangrove_swamp", KitData.Icon.LILY), Map.entry("dark_forest", KitData.Icon.OAK_LOG),
            Map.entry("badlands", KitData.Icon.EGG_ARMADILLO), Map.entry("flower_forest", KitData.Icon.EGG_BEE), Map.entry("bamboo_jungle", KitData.Icon.EGG_PANDA),
            Map.entry("pale_garden", KitData.Icon.P_SOUL));

    public static void visit(Player p) {
        visit(p, 0, 0);
    }

    public static void visit(Player p, int tab, int page) {
        IslandService is = sb().islands();
        BiomeIslandService bs = sb().biomes();
        if (!sb().core().uiPack().active(p)) {
            visitChat(p, tab);
            return;
        }
        List<Row> rows = new ArrayList<>();
        int total;
        Island mine = is.islandOf(p.getUniqueId());
        if (tab == 0) {
            List<Island> open = is.openIslands(p);
            total = open.size();
            int pg = clampPage(page, total);
            for (int k = pg * PER_PAGE; k < Math.min(total, pg * PER_PAGE + PER_PAGE); k++) {
                Island i = open.get(k);
                int on = is.online(i);
                rows.add(new Row(k + 1, null, i.name, "von " + IslandService.nameOf(i.owner) + " · Level " + Msg.coins(is.levelOf(i))
                        + (on > 0 ? " · " + on + " online" : ""), MIST, false,
                        "Besuchen", KitData.Small.GREEN, run(pl -> feedback(pl, is.visit(pl, i))), null, null, null));
            }
            page = pg;
        } else {
            List<BIsland> all = bs.all();
            total = all.size();
            int pg = clampPage(page, total);
            boolean canClaim = mine != null && is.has(mine, p.getUniqueId(), IslandService.Right.UPGRADES) && bs.of(mine.slot).size() < bs.perIsland();
            long coins = sb().core().economy().balance(p.getUniqueId());
            for (int k = pg * PER_PAGE; k < Math.min(total, pg * PER_PAGE + PER_PAGE); k++) {
                BIsland b = all.get(k);
                Island owner = b.owner > 0 ? is.bySlot(b.owner) : null;
                boolean own = owner != null && owner == mine;
                String sub;
                TextColor col;
                if (own) {
                    sub = "gehört deiner Insel";
                    col = GOLD;
                } else if (owner != null) {
                    sub = "gehört " + owner.name;
                    col = MIST;
                } else {
                    sub = "frei · " + Msg.coins(bs.price()) + " $";
                    col = GREEN;
                }
                int pgf = pg;
                Consumer<Player> fly = run(pl -> feedback(pl, bs.teleport(pl, b.id)));
                Consumer<Player> act2 = null;
                String l2 = null;
                KitData.Small s2 = null;
                if (own && is.canManage(mine, p.getUniqueId())) {
                    l2 = "Freigeben";
                    s2 = KitData.Small.DARK;
                    act2 = run(pl -> IslandScreen.confirm(pl, "Biom-Insel freigeben", BiomeIslandService.label(b.biome) + " #" + b.id
                            + " freigeben? Das Geld gibt es nicht zurück.", "Freigeben", q -> {
                        feedback(q, bs.release(q, b.id));
                        visit(q, 1, pgf);
                    }));
                } else if (owner == null && canClaim) {
                    l2 = "Beanspruchen";
                    s2 = coins >= bs.price() ? KitData.Small.GOLD : KitData.Small.DARK;
                    act2 = run(pl -> IslandScreen.confirm(pl, "Biom-Insel beanspruchen", BiomeIslandService.label(b.biome) + " #" + b.id + " für "
                            + Msg.coins(bs.price()) + " $ zu deiner Insel holen?", "Beanspruchen", q -> {
                        feedback(q, bs.claim(q, b.id));
                        visit(q, 1, pgf);
                    }));
                }
                rows.add(new Row(k + 1, BIOME_ICON.getOrDefault(b.biome, KitData.Icon.GRASS_BLOCK),
                        BiomeIslandService.label(b.biome) + " #" + b.id, sub + " · " + b.x + " / " + b.z, col, own,
                        "Hin", KitData.Small.DARK, fly, l2, s2, act2));
            }
            page = pg;
        }
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE), pg = page;
        List<Island> open = tab == 0 ? is.openIslands(p) : List.of();
        UiDialog.show(p, visitCanvas(new VisitView(tab, rows, pg, pages,
                tab == 0 ? "Gerade ist keine andere Insel für Besucher offen." : "Es gibt noch keine Biom-Inseln (Admin: /kp skyblock biominseln).",
                run(pl -> visit(pl, 0, 0)), run(pl -> visit(pl, 1, 0)),
                pg > 0 ? run(pl -> visit(pl, tab, pg - 1)) : null, pg < pages - 1 ? run(pl -> visit(pl, tab, pg + 1)) : null,
                !open.isEmpty() ? run(pl -> feedback(pl, is.visit(pl, open.get(ThreadLocalRandom.current().nextInt(open.size()))))) : null,
                run(IslandScreen::open))), 330);
    }

    private static int clampPage(int page, int total) {
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        return Math.max(0, Math.min(page, pages - 1));
    }

    public static Canvas visitCanvas(VisitView v) {
        Canvas c = head("Besuchen", v.tab() == 0 ? "Offene Inseln anderer Spieler, beste zuerst" : "Inseln mit eigenem Biom und seinen Tieren",
                v.tab() == 0 ? KitData.Sprite.HB_IS_BESUCH : KitData.Sprite.HB_IS_BIOM, v.back());
        int y = 60, tw = (W - 4) / 2;
        String[] tabs = {"Offene Inseln", "Biom-Inseln"};
        @SuppressWarnings("unchecked")
        Consumer<Player>[] ta = new Consumer[]{v.tab0(), v.tab1()};
        for (int k = 0; k < 2; k++) {
            boolean on = k == v.tab();
            c.smallButton(k * (tw + 4), y, tw, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, tabs[k], on ? CLOUD : MIST, true);
            if (!on && ta[k] != null) c.action(k * (tw + 4), y, tw, KitData.SMALL_H, ta[k], tabs[k]);
        }
        y += KitData.SMALL_H + 6;
        for (Row r : v.rows()) {
            int h = 30;
            c.box(0, y, W, h, r.mine() ? KitData.Box.GOLD : KitData.Box.CLOUD_CARD);
            if (r.icon() != null) c.icon(8, y + 7, r.icon(), false);
            else c.textCenter(16, y + 11, "#" + r.rank(), r.rank() <= 3 ? GOLD : MIST, true);
            int right = W - 8;
            int bw1 = r.b1() == null ? 0 : Math.max(44, Canvas.width(r.b1(), true) + 16), bw2 = r.b2() == null ? 0 : Math.max(44, Canvas.width(r.b2(), true) + 16);
            int bx2 = right - bw2, bx1 = bx2 - (bw2 > 0 ? 4 : 0) - bw1;
            if (r.b2() != null) {
                c.smallButton(bx2, y + 7, bw2, r.s2(), r.b2(), r.s2() == KitData.Small.DARK ? RED : r.s2() == KitData.Small.GOLD ? GOLD : CLOUD, true);
                if (r.a2() != null) c.action(bx2, y + 7, bw2, KitData.SMALL_H, r.a2(), r.b2());
            }
            if (r.b1() != null) {
                c.smallButton(bx1, y + 7, bw1, r.s1(), r.b1(), CLOUD, true);
                if (r.a1() != null) c.action(bx1, y + 7, bw1, KitData.SMALL_H, r.a1(), r.b1() + ": " + r.name());
            }
            int tx = 32, tmax = bx1 - 6 - tx;
            c.text(tx, y + 5, Canvas.fit(Canvas.lit(r.name()), true, tmax), r.mine() ? GOLD : CLOUD, true);
            c.text(tx, y + 17, Canvas.fit(Canvas.lit(r.sub()), false, tmax), r.subColor(), false);
            y += h + 3;
        }
        if (v.rows().isEmpty()) {
            c.box(0, y, W, 24, KitData.Box.CLOUD_CARD);
            c.text(10, y + 8, Canvas.fit(v.empty(), false, W - 20), DIM, false);
            y += 28;
        }
        y += 3;
        if (v.random() != null) {
            c.smallButton(0, y, 130, KitData.Small.GREEN, "Zufällige Insel", CLOUD, true);
            c.action(0, y, 130, KitData.SMALL_H, v.random(), "Eine zufällige offene Insel besuchen");
        }
        if (v.pages() > 1) {
            c.smallButton(W - 150, y, 40, KitData.Small.DARK, "«", v.prev() == null ? DIM : CLOUD, true);
            if (v.prev() != null) c.action(W - 150, y, 40, KitData.SMALL_H, v.prev(), "Vorige Seite");
            c.textCenter(W - 80, y + 4, (v.page() + 1) + "/" + v.pages(), MIST, false);
            c.smallButton(W - 40, y, 40, KitData.Small.DARK, "»", v.next() == null ? DIM : CLOUD, true);
            if (v.next() != null) c.action(W - 40, y, 40, KitData.SMALL_H, v.next(), "Nächste Seite");
        }
        return c;
    }

    static void visitChat(Player p, int tab) {
        IslandService is = sb().islands();
        if (tab == 0) {
            Msg.send(p, "<#9AD8FF><b>Offene Inseln</b> <click:run_command:'/insel biom'><gray>[Biom-Inseln]</click>");
            List<Island> open = is.openIslands(p);
            for (Island i : open.subList(0, Math.min(10, open.size()))) {
                Msg.send(p, " <click:run_command:'/insel besuchen " + IslandService.nameOf(i.owner) + "'><white>" + Msg.escape(i.name)
                        + " <gray>(" + IslandService.nameOf(i.owner) + ", Level " + is.levelOf(i) + ") <green>[Besuchen]</click>");
            }
            if (open.isEmpty()) Msg.send(p, " <gray>Gerade ist keine andere Insel offen.");
            return;
        }
        Msg.send(p, "<#9AD8FF><b>Biom-Inseln</b> <gray>(" + Msg.coins(sb().biomes().price()) + " $, /insel biom beanspruchen <Nr>)");
        for (BIsland b : sb().biomes().all()) {
            Island o = b.owner > 0 ? is.bySlot(b.owner) : null;
            Msg.send(p, " <click:run_command:'/insel biom hin " + b.id + "'><white>#" + b.id + " " + BiomeIslandService.label(b.biome) + "</click> <gray>"
                    + (o == null ? "<green>frei" : "gehört " + Msg.escape(o.name)));
        }
    }

    // =================================================================================================================
    // Preise
    // =================================================================================================================

    public record PrizeRow(String label, int rank, String text) {}

    public record PrizeView(List<PrizeRow> week, List<PrizeRow> month, String weekIn, String monthIn, List<String> lastWeek,
                            List<String> lastMonth, String own, Consumer<Player> back) {}

    public static void prizes(Player p) {
        TopRewardService t = sb().rewards();
        IslandService is = sb().islands();
        Island mine = is.islandOf(p.getUniqueId());
        long now = System.currentTimeMillis();
        String own = mine == null ? null : "Deine Insel: Platz #" + is.rank(mine) + " · Level " + Msg.coins(is.levelOf(mine));
        if (!sb().core().uiPack().active(p)) {
            Msg.send(p, "<gold><b>Preise der Top-Inseln</b>");
            for (String per : List.of(TopRewardService.WEEK, TopRewardService.MONTH)) {
                Msg.send(p, " <#9AD8FF>" + (per.equals(TopRewardService.WEEK) ? "Woche" : "Monat") + " <gray>(in " + KitService.format(t.next(per) - now) + ")");
                for (TopRewardService.Prize pr : t.prizes(per)) Msg.send(p, "  <white>" + pr.label() + ": <gold>" + pr.text());
            }
            if (own != null) Msg.send(p, " <gray>" + own);
            return;
        }
        UiDialog.show(p, prizeCanvas(new PrizeView(rows(t, TopRewardService.WEEK), rows(t, TopRewardService.MONTH),
                "in " + KitService.format(t.next(TopRewardService.WEEK) - now), "in " + KitService.format(t.next(TopRewardService.MONTH) - now),
                winners(t, TopRewardService.WEEK), winners(t, TopRewardService.MONTH), own, run(IslandScreen::top))), 330);
    }

    private static List<PrizeRow> rows(TopRewardService t, String period) {
        List<PrizeRow> out = new ArrayList<>();
        for (TopRewardService.Prize pr : t.prizes(period)) out.add(new PrizeRow(pr.label(), pr.from(), pr.text()));
        return out;
    }

    private static List<String> winners(TopRewardService t, String period) {
        List<String> out = new ArrayList<>();
        for (TopRewardService.Winner w : t.winners(period)) {
            if (out.size() >= 3) break;
            out.add("#" + w.rank() + " " + w.name());
        }
        return out;
    }

    public static Canvas prizeCanvas(PrizeView v) {
        Canvas c = head("Preise", "Wochen- und Monatspreise der Top-Inseln", KitData.Sprite.HB_IS_POKAL, v.back());
        int y = 60, cw = (W - 4) / 2;
        int hW = column(c, 0, y, cw, "Woche", v.weekIn(), v.week(), v.lastWeek());
        int hM = column(c, cw + 4, y, cw, "Monat", v.monthIn(), v.month(), v.lastMonth());
        y += Math.max(hW, hM) + 6;
        for (String l : Canvas.wrap("Coins und Juwelen bekommt jedes Mitglied der Insel, der Booster gilt für alle Generatoren der Insel. "
                + "Gezählt wird das Insel-Level zur Zeit der Auszahlung.", W - 8)) {
            c.text(4, y, l, DIM, false);
            y += 10;
        }
        if (v.own() != null) c.text(4, y + 4, Canvas.fit(v.own(), true, W - 8), GOLD, true);
        return c;
    }

    private static int column(Canvas c, int x, int y, int w, String title, String in, List<PrizeRow> rows, List<String> last) {
        KitData.Sprite[] medals = {KitData.Sprite.QU_M_GOLD, KitData.Sprite.QU_M_SILVER, KitData.Sprite.QU_M_BRONZE};
        int h = 22;
        List<List<String>> wrapped = new ArrayList<>();
        for (PrizeRow r : rows) {
            List<String> l = Canvas.wrap(r.text(), w - 40);
            wrapped.add(l.subList(0, Math.min(2, l.size())));
            h += 13 + 10 * Math.min(2, l.size()) + 4;
        }
        if (rows.isEmpty()) h += 14;
        h += 18 + 10 * Math.max(1, last.size()) + 4;
        c.box(x, y, w, h, KitData.Box.CLOUD_CARD);
        c.text(x + 8, y + 7, Canvas.caps(title), SKY, true);
        c.textRight(x + w - 8, y + 7, in, GOLD, false);
        int ry = y + 22;
        for (int k = 0; k < rows.size(); k++) {
            PrizeRow r = rows.get(k);
            if (r.rank() >= 1 && r.rank() <= 3) {
                KitData.Sprite m = medals[r.rank() - 1];
                c.sprite(x + 6 + (18 - m.width) / 2, ry + 2, m);
            } else {
                c.textCenter(x + 15, ry + 3, "#" + r.rank(), MIST, true);
            }
            c.text(x + 30, ry + 1, Canvas.fit(r.label(), true, w - 38), CLOUD, true);
            int ly = ry + 13;
            for (String l : wrapped.get(k)) {
                c.text(x + 30, ly, l, GOLD, false);
                ly += 10;
            }
            ry = ly + 4;
        }
        if (rows.isEmpty()) {
            c.text(x + 8, ry, "Keine Preise eingestellt.", DIM, false);
            ry += 14;
        }
        c.text(x + 8, ry + 2, Canvas.caps("Letzte Gewinner"), MIST, false);
        ry += 16;
        if (last.isEmpty()) c.text(x + 8, ry, "noch keine", DIM, false);
        for (String l : last) {
            c.text(x + 8, ry, Canvas.fit(Canvas.lit(l), false, w - 16), CLOUD, false);
            ry += 10;
        }
        return h;
    }

    // =================================================================================================================
    // Limits
    // =================================================================================================================

    public record LimitView(String island, int level, String bonus, List<LimitService.Usage> usage, String next, long price, boolean affordable,
                            Consumer<Player> buy, Consumer<Player> back) {}

    public static void limits(Player p) {
        IslandService is = sb().islands();
        Island i = is.islandOf(p.getUniqueId());
        if (i == null) {
            feedback(p, "Du hast keine Insel.");
            return;
        }
        List<LimitService.Usage> us = sb().limits().usage(i);
        if (!sb().core().uiPack().active(p)) {
            Msg.send(p, "<#9AD8FF><b>Limits</b> <gray>(Upgrade Stufe " + i.limit + ", +" + Math.round(sb().limits().bonus(i) * 100) + " %)");
            for (LimitService.Usage u : us) Msg.send(p, " <white>" + u.name() + ": " + (u.used() >= u.max() ? "<red>" : "<green>") + u.used() + "/" + u.max());
            return;
        }
        long price = is.price(i, IslandService.Upgrade.LIMIT);
        long coins = sb().core().economy().balance(p.getUniqueId());
        boolean may = is.has(i, p.getUniqueId(), IslandService.Right.UPGRADES);
        double per = sb().getConfig().getDouble("limits.bonus-je-stufe", 0.25);
        UiDialog.show(p, limitCanvas(new LimitView(i.name, i.limit, "+" + Math.round(sb().limits().bonus(i) * 100) + " %", us,
                price < 0 ? null : "Stufe " + (i.limit + 1) + ": +" + Math.round((i.limit + 1) * per * 100) + " %", Math.max(0, price),
                price >= 0 && coins >= price, price >= 0 && may ? run(pl -> {
                    feedback(pl, is.upgrade(pl, IslandService.Upgrade.LIMIT));
                    limits(pl);
                }) : null, run(IslandScreen::open))), 330);
    }

    public static Canvas limitCanvas(LimitView v) {
        Canvas c = head("Limits", "Grenzen gegen Lag · " + v.island(), KitData.Sprite.HB_IS_LIMITS, v.back());
        int y = 60;
        int blocks = 0;
        for (LimitService.Usage u : v.usage()) if (!isMob(u.name())) blocks++;
        y = table(c, y, "Blöcke", v.usage().subList(0, blocks));
        y = table(c, y + 6, "Wesen", v.usage().subList(blocks, v.usage().size()));
        y += 6;
        c.box(0, y, W, 40, KitData.Box.CLOUD_CARD);
        c.sprite(6 + (32 - KitData.Sprite.HB_IS_LIMITS.width) / 2, y + 4 + (32 - KitData.Sprite.HB_IS_LIMITS.height) / 2, KitData.Sprite.HB_IS_LIMITS);
        c.text(44, y + 7, "Upgrade Limits · Stufe " + v.level() + " (" + v.bonus() + ")", CLOUD, true);
        if (v.next() == null) {
            c.text(44, y + 21, Canvas.caps("Höchste Stufe"), GREEN, false);
        } else {
            c.text(44, y + 21, Canvas.fit("→ " + v.next() + " · " + Msg.coins(v.price()) + " $", false, W - 44 - 80), v.affordable() ? GREEN : RED, false);
            c.smallButton(W - 8 - 62, y + 12, 62, v.affordable() && v.buy() != null ? KitData.Small.GOLD : KitData.Small.DARK, "Kaufen",
                    v.affordable() && v.buy() != null ? GOLD : DIM, true);
            if (v.buy() != null && v.affordable()) c.action(W - 8 - 62, y + 12, 62, KitData.SMALL_H, v.buy(), "Limits verbessern");
        }
        return c;
    }

    private static boolean isMob(String name) {
        for (LimitService.Kind k : LimitService.Kind.values()) if (k.label.equals(name)) return true;
        return false;
    }

    private static int table(Canvas c, int y, String title, List<LimitService.Usage> rows) {
        c.text(2, y, Canvas.caps(title), SKY, true);
        y += 12;
        int h = rows.isEmpty() ? 24 : 6 + rows.size() * 16 + 4;
        c.box(0, y, W, h, KitData.Box.CLOUD_CARD);
        int ry = y + 6;
        for (LimitService.Usage u : rows) {
            double f = u.max() <= 0 ? 1 : Math.min(1, (double) u.used() / u.max());
            c.text(10, ry + 3, Canvas.fit(u.name(), true, 148), CLOUD, true);
            c.bar(165, ry + 4, W - 165 - 100, f, f >= 1 ? KitData.Fill.RED : f >= 0.8 ? KitData.Fill.GOLD : KitData.Fill.GREEN);
            c.textRight(W - 10, ry + 3, u.counted() ? Msg.coins(u.used()) + " / " + Msg.coins(u.max()) : "… / " + Msg.coins(u.max()), f >= 1 ? RED : MIST, false);
            ry += 16;
        }
        if (rows.isEmpty()) c.text(10, ry + 3, "Keine Grenzen eingestellt.", DIM, false);
        return y + h;
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        List<Row> rows = new ArrayList<>();
        for (int k = 0; k < PER_PAGE; k++) {
            rows.add(new Row(k + 1, k % 2 == 0 ? null : KitData.Icon.GRASS_BLOCK, w, w + w, k % 3 == 0 ? GOLD : MIST, k == 2,
                    "Besuchen", KitData.Small.GREEN, no, k % 2 == 0 ? "Beanspruchen" : null, KitData.Small.GOLD, k % 2 == 0 ? no : null));
        }
        reg.accept("Insel Besuchen", () -> visitCanvas(new VisitView(0, rows, 99, 999, "", no, no, no, no, no, no)));
        reg.accept("Insel Besuchen leer", () -> visitCanvas(new VisitView(1, List.of(), 0, 1,
                "Es gibt noch keine Biom-Inseln (Admin: /kp skyblock biominseln).", no, no, null, null, null, no)));
        List<PrizeRow> pr = List.of(new PrizeRow("Platz 1", 1, "999.999.999 $ · 999.999 Juwelen · Booster x3 23h 59m 59s"),
                new PrizeRow("Platz 2", 2, w), new PrizeRow("Platz 3", 3, w), new PrizeRow("Platz 4–10", 4, w));
        List<String> last = List.of("#1 " + w, "#2 " + w, "#3 " + w);
        reg.accept("Insel Preise", () -> prizeCanvas(new PrizeView(pr, pr, "in 99T 23h 59m", "in 99T 23h 59m", last, last, w + w, no)));
        reg.accept("Insel Preise leer", () -> prizeCanvas(new PrizeView(List.of(), List.of(), "in 1h", "in 1h", List.of(), List.of(), null, no)));
        List<LimitService.Usage> us = new ArrayList<>();
        for (String n : List.of(w, "Kolben", "Beobachter", "Werfer & Spender", "Verstärker & Komparatoren")) us.add(new LimitService.Usage(n, 99_999, 99_999, true));
        for (LimitService.Kind k : LimitService.Kind.values()) us.add(new LimitService.Usage(k.label, 50, 60, k.ordinal() % 2 == 0));
        reg.accept("Insel Limits", () -> limitCanvas(new LimitView(w, 99, "+999 %", us, "Stufe 99: +999 %", 999_999_999L, true, no, no)));
        reg.accept("Insel Limits max", () -> limitCanvas(new LimitView("Insel", 4, "+100 %", us.subList(5, us.size()), null, 0, false, null, no)));
    }
}
