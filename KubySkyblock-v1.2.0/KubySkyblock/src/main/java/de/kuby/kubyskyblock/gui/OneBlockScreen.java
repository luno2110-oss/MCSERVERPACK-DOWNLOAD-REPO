package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.OneBlockService;
import de.kuby.kubyskyblock.service.OneBlockService.Platform;
import de.kuby.kubyskyblock.service.OneBlockService.Progress;
import de.kuby.kubyskyblock.service.OneBlockService.Upgrade;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One-Block-Dialoge im Himmels-Look: alle (höchstens drei) Plattformen mit Besitzer und Beanspruchen, und der Block des
 * eigenen Teams mit Block-XP, sechs Upgrades (Glück, Cooldown, Tempo, XP, Geld, Beute) und XP einzahlen.
 */
public final class OneBlockScreen {

    static final int W = 390;
    static final TextColor SKY = IslandScreen.SKY, CLOUD = IslandScreen.CLOUD, MIST = IslandScreen.MIST, GOLD = IslandScreen.GOLD,
            GREEN = IslandScreen.GREEN, RED = IslandScreen.RED, DIM = IslandScreen.DIM, XPC = TextColor.color(0xC8F870);

    private OneBlockScreen() {
    }

    private static KubySkyblock sb() {
        return KubySkyblock.get();
    }

    private static OneBlockService ob() {
        return sb().oneblock();
    }

    private static Consumer<Player> run(Consumer<Player> c) {
        return p -> {
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            c.accept(p);
        };
    }

    private static void feedback(Player p, String err) {
        Sfx.play(sb().core(), p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    static KitData.Sprite art(Upgrade u) {
        return switch (u) {
            case GLUECK -> KitData.Sprite.HB_OB_GLUECK;
            case COOLDOWN -> KitData.Sprite.HB_OB_COOLDOWN;
            case TEMPO -> KitData.Sprite.HB_OB_TEMPO;
            case XP -> KitData.Sprite.HB_OB_XP;
            case GELD -> KitData.Sprite.HB_OB_GELD;
            case BEUTE -> KitData.Sprite.HB_OB_BEUTE;
        };
    }

    public static void teleport(Player p, Platform pf) {
        if (!ob().mayTravel(p, pf)) {
            feedback(p, pf.name() + " musst du erst finden: " + ob().hint(pf) + " vom Spawn.");
            return;
        }
        Location l = ob().spot(pf);
        if (l == null) {
            feedback(p, "Die Skyblock-Welt ist nicht geladen.");
            return;
        }
        p.teleportAsync(l);
        Msg.send(p, "<#9AD8FF>Willkommen am " + pf.name() + ".");
    }

    // =================================================================================================================
    // Alle Plattformen
    // =================================================================================================================

    /** Eine Plattform: aktueller Block, Name, Zeile, Knopf (Text, Stil, Aktion) und Hinreisen. */
    public record Spot(Material block, String name, String status, TextColor statusColor, String button, KitData.Small style,
                       TextColor buttonColor, Consumer<Player> action, Consumer<Player> travel) {}

    public record Overview(String team, String line, long ownXp, List<Spot> spots, Consumer<Player> mine, Consumer<Player> back) {}

    public static void open(Player p) {
        if (!sb().core().uiPack().active(p)) {
            chat(p);
            return;
        }
        OneBlockService s = ob();
        TeamService.Team t = s.team(p.getUniqueId());
        Platform own = s.claimOf(t);
        Progress pr = t == null ? null : s.progress(t);
        World w = org.bukkit.Bukkit.getWorld(sb().core().modes().worldOf(de.kuby.kubyplugins.service.ModeService.SKYBLOCK));
        List<Spot> spots = new ArrayList<>();
        for (Platform pf : s.platforms()) {
            Material m = w != null && w.isChunkLoaded(pf.x >> 4, pf.z >> 4) ? w.getBlockAt(pf.x, pf.y, pf.z).getType() : Material.GRASS_BLOCK;
            Material icon = m == Material.BEDROCK || m.isAir() ? Material.GRASS_BLOCK : m;
            String status, button;
            TextColor sc, bc;
            KitData.Small style;
            Consumer<Player> act;
            boolean known = s.mayTravel(p, pf);
            if (!known) {
                // verteilt auf der Map und noch nicht gefunden: nur ein Hinweis, wo man suchen muss
                status = (pf.claimed() ? "Team " + pf.teamName + " · " : "Gesucht: ") + s.hint(pf);
                sc = MIST;
                button = "Erst finden";
                style = KitData.Small.DARK;
                bc = DIM;
                act = null;
            } else if (!pf.claimed()) {
                status = t != null && own == null ? "Frei · Beanspruchen: " + Msg.coins(s.claimPrice()) + " $" : "Frei · für alle zum Abbauen";
                sc = GREEN;
                if (t != null && own == null) {
                    button = "Beanspruchen";
                    style = KitData.Small.GOLD;
                    bc = GOLD;
                    act = run(pl -> IslandScreen.confirm(pl, pf.name() + " beanspruchen",
                            "Dein Team zahlt " + Msg.coins(s.claimPrice()) + " $. Danach bauen nur noch eure Mitglieder hier ab und jeder Abbau füllt eure Block-XP. "
                                    + "Wer " + s.expiryDays() + " Tage nicht abbaut, verliert den Block wieder (die Upgrades bleiben).",
                            "Beanspruchen", q -> {
                                String err = s.claim(q, pf.index);
                                feedback(q, err);
                                if (err == null) block(q);
                            }));
                } else {
                    button = t == null ? "Nur im Team" : "Ihr habt schon einen";
                    style = KitData.Small.DARK;
                    bc = DIM;
                    act = null;
                }
            } else if (pf == own) {
                status = "Euer Block · Block-Level " + (pr == null ? 0 : pr.total()) + " · " + Msg.coins(pr == null ? 0 : pr.mined) + " abgebaut";
                sc = GOLD;
                button = "Upgrades";
                style = KitData.Small.GREEN;
                bc = CLOUD;
                act = run(OneBlockScreen::block);
            } else {
                status = "Team " + pf.teamName;
                sc = MIST;
                button = "Belegt";
                style = KitData.Small.DARK;
                bc = DIM;
                act = null;
            }
            spots.add(new Spot(icon, pf.name(), status, sc, button, style, bc, act, known ? run(pl -> teleport(pl, pf)) : null));
        }
        String line = t == null ? "Ohne Team: /team erstellen <Name>, dann einen Block beanspruchen"
                : own == null ? "Block-XP " + Msg.coins(pr.xp) + " · Block-Level " + pr.total() + " · noch kein Block"
                : own.name() + " · Block-XP " + Msg.coins(pr.xp) + " · Block-Level " + pr.total();
        UiDialog.show(p, overview(new Overview(t == null ? null : t.name(), line, sb().core().stats().get(p.getUniqueId()).xp, spots,
                t == null ? null : run(OneBlockScreen::block), run(IslandScreen::open))), 330);
    }

    public static Canvas overview(Overview v) {
        Canvas c = new Canvas(W).heading("One-Block", SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_ONEBLOCK.width) / 2, 11 + (32 - KitData.Sprite.HB_ONEBLOCK.height) / 2, KitData.Sprite.HB_ONEBLOCK);
        c.headline(50, 10, "One-Block", SKY);
        c.text(50, 36, Canvas.fit(v.line(), false, 250), v.team() == null ? MIST : CLOUD, false);
        c.textRight(W - 10, 12, Canvas.caps("Deine XP"), MIST, false);
        c.textRight(W - 10, 24, Msg.coins(v.ownXp()), XPC, true);
        if (v.team() != null) c.textRight(W - 10, 36, Canvas.fit("Team " + v.team(), false, 110), GOLD, false);
        int y = 60;
        for (Spot s : v.spots()) {
            c.box(0, y, W, 44, KitData.Box.CLOUD_CARD);
            c.item(10, y + 6, s.block(), true, KitData.Icon.GRASS_BLOCK);
            c.text(48, y + 9, s.name(), CLOUD, true);
            c.text(48, y + 23, Canvas.fit(s.status(), false, W - 48 - 136), s.statusColor(), false);
            int bw = 120;
            c.smallButton(W - 10 - bw, y + 6, bw, s.style(), Canvas.fit(s.button(), true, bw - 10), s.buttonColor(), true);
            if (s.action() != null) c.action(W - 10 - bw, y + 6, bw, KitData.SMALL_H, s.action(), s.button());
            if (s.travel() != null) {
                c.smallButton(W - 10 - bw, y + 6 + KitData.SMALL_H + 2, bw, KitData.Small.DARK, "Hinreisen", SKY, false);
                c.action(W - 10 - bw, y + 6 + KitData.SMALL_H + 2, bw, KitData.SMALL_H, s.travel(), "Zum " + s.name());
            } else {
                c.smallButton(W - 10 - bw, y + 6 + KitData.SMALL_H + 2, bw, KitData.Small.DARK, "Hinreisen gesperrt", DIM, false);
            }
            y += 48;
        }
        if (v.spots().isEmpty()) {
            c.box(0, y, W, 30, KitData.Box.CLOUD_CARD);
            c.text(10, y + 11, "Noch keine One-Block-Plattformen (Admins: /kp skyblock oneblock setup).", MIST, false);
            y += 34;
        }
        c.text(4, y + 2, Canvas.fit("Beansprucht: nur das Team baut ab, jeder Abbau bringt Block-XP.", false, W - 8), DIM, false);
        y += 14;
        int bw = (W - 4) / 2;
        if (v.mine() != null) {
            c.smallButton(0, y, bw, KitData.Small.GOLD, "Upgrades eures Teams", GOLD, true);
            c.action(0, y, bw, KitData.SMALL_H, v.mine(), "Block-XP und Upgrades");
        }
        c.smallButton(bw + 4, y, bw, KitData.Small.DARK, "Zur Insel", CLOUD, false);
        c.action(bw + 4, y, bw, KitData.SMALL_H, v.back(), "Zurück zur Insel");
        return c;
    }

    // =================================================================================================================
    // Block des Teams: Upgrades
    // =================================================================================================================

    public record Card(Upgrade upgrade, int level, int max, String now, String next, long price, boolean jewels, boolean affordable,
                       Consumer<Player> buy) {}

    public record BlockView(String title, String sub, long xp, long mined, int stage, long ownXp, List<Card> cards,
                            Consumer<Player> deposit100, Consumer<Player> deposit1000, Consumer<Player> depositAll,
                            Consumer<Player> travel, Consumer<Player> release, Consumer<Player> back) {}

    public static void block(Player p) {
        OneBlockService s = ob();
        TeamService.Team t = s.team(p.getUniqueId());
        if (t == null) {
            feedback(p, "Der One-Block gehört immer einem Team. Gründe eins mit /team erstellen <Name>.");
            return;
        }
        if (!sb().core().uiPack().active(p)) {
            chat(p);
            return;
        }
        Progress pr = s.progress(t);
        Platform pf = s.claimOf(t);
        List<Card> cards = new ArrayList<>();
        for (Upgrade u : Upgrade.values()) {
            int lvl = pr.level(u), max = s.max(u);
            long price = s.price(pr, u);
            boolean jw = s.jewelStep(pr, u);
            long have = jw ? sb().core().jewels().balance(p.getUniqueId()) : pr.xp;
            cards.add(new Card(u, lvl, max, s.effect(u, lvl), lvl < max ? s.effect(u, lvl + 1) : null, price, jw, price >= 0 && have >= price,
                    run(pl -> {
                        feedback(pl, s.upgrade(pl, u));
                        block(pl);
                    })));
        }
        boolean leader = t.leader().equals(p.getUniqueId());
        Consumer<Player> release = pf == null || !leader ? null : run(pl -> IslandScreen.confirm(pl, pf.name() + " freigeben",
                "Der Block ist danach frei für andere Teams. Eure Block-XP und Upgrades bleiben und gelten wieder, sobald ihr einen Block beansprucht.",
                "Freigeben", q -> feedback(q, s.release(q))));
        UiDialog.show(p, block(new BlockView(pf == null ? "Team " + t.name() : pf.name(),
                pf == null ? "Team " + t.name() + " · noch kein Block beansprucht" : "Team " + t.name() + " · " + Msg.coins(pr.mined) + " abgebaut",
                pr.xp, pr.mined, pr.total(), sb().core().stats().get(p.getUniqueId()).xp, cards,
                run(pl -> {
                    feedback(pl, s.deposit(pl, 100));
                    block(pl);
                }),
                run(pl -> {
                    feedback(pl, s.deposit(pl, 1000));
                    block(pl);
                }),
                run(pl -> {
                    feedback(pl, s.deposit(pl, -1));
                    block(pl);
                }),
                pf == null ? null : run(pl -> teleport(pl, pf)), release, run(OneBlockScreen::open))), 330);
    }

    /** „Selten x2,8“ → „Selten x3,1“ wird zu „x3,1“ (gleiches erstes Wort nur einmal). */
    static String shortNext(String now, String next) {
        int a = now.indexOf(' '), b = next.indexOf(' ');
        if (a > 0 && b > 0 && now.substring(0, a).equals(next.substring(0, b)) && !now.startsWith("+")) return next.substring(b + 1);
        return next;
    }

    public static Canvas block(BlockView v) {
        Canvas c = new Canvas(W).heading("One-Block", SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_ONEBLOCK.width) / 2, 11 + (32 - KitData.Sprite.HB_ONEBLOCK.height) / 2, KitData.Sprite.HB_ONEBLOCK);
        c.text(50, 10, Canvas.fit(v.title(), true, 190), SKY, true);
        c.text(50, 23, Canvas.fit(v.sub(), false, 200), MIST, false);
        c.text(50, 36, "Block-Level " + v.stage(), GOLD, true);
        c.textRight(W - 10, 10, Canvas.caps("Block-XP"), MIST, false);
        c.textRight(W - 10, 22, Msg.coins(v.xp()), XPC, true);
        // Balken: Fortschritt zum günstigsten offenen Upgrade
        long cheapest = Long.MAX_VALUE;
        for (Card cd : v.cards()) if (cd.price() >= 0) cheapest = Math.min(cheapest, cd.price());
        if (cheapest != Long.MAX_VALUE) c.bar(W - 110, 38, 100, Math.min(1, (double) v.xp() / Math.max(1, cheapest)), KitData.Fill.GREEN);
        int y = 60, cw = (W - 4) / 2, ch = 58;
        for (int i = 0; i < v.cards().size(); i++) {
            Card cd = v.cards().get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
            boolean maxed = cd.price() < 0;
            c.box(x, cy, cw, ch, maxed ? KitData.Box.GOLD : KitData.Box.CLOUD_CARD);
            KitData.Sprite a = UpgradeTier.icon(art(cd.upgrade()), cd.level());
            c.sprite(x + 6 + (32 - a.width) / 2, cy + 5 + (32 - a.height) / 2, a);
            String lv = "Lvl " + cd.level() + "/" + cd.max();
            c.text(x + 44, cy + 6, Canvas.fit(cd.upgrade().label, true, cw - 52 - Canvas.width(lv, true) - 4), maxed ? GOLD : CLOUD, true);
            c.textRight(x + cw - 8, cy + 6, lv, maxed ? GOLD : MIST, true);
            int nw = c.text(x + 44, cy + 18, Canvas.fit(cd.next() == null ? "Jetzt: " + cd.now() : cd.now(), false, cw - 52), XPC, false);
            if (cd.next() != null) c.text(x + 44 + nw + 3, cy + 18, Canvas.fit("→ " + shortNext(cd.now(), cd.next()), false, cw - 55 - nw), GOLD, false);
            // Balken in der Farbe der Stufe, rechts daneben die Stufe (1: Level 1-10, 2: bis 15, 3: bis 20)
            c.bar(x + 44, cy + 31, cw - 52 - 46, cd.max() == 0 ? 1 : (double) cd.level() / cd.max(), UpgradeTier.fill(cd.level()));
            UpgradeTier.textRight(c, x + cw - 8, cy + 30, "Stufe " + UpgradeTier.of(cd.level()), cd.level(), false);
            String label = maxed ? "Ausgebaut" : "Upgrade · " + Msg.coins(cd.price()) + (cd.jewels() ? " Juwelen" : " XP");
            c.smallButton(x + 6, cy + ch - KitData.SMALL_H - 4, cw - 12, maxed ? KitData.Small.DARK : cd.affordable() ? KitData.Small.GOLD : KitData.Small.DARK,
                    label, maxed ? GOLD : cd.affordable() ? GOLD : DIM, true);
            if (!maxed) c.action(x + 6, cy + ch - KitData.SMALL_H - 4, cw - 12, KitData.SMALL_H, cd.buy(), cd.upgrade().desc);
        }
        y += ((v.cards().size() + 1) / 2) * (ch + 4) + 2;
        c.box(0, y, W, 24, KitData.Box.CLOUD_CARD);
        c.text(8, y + 8, "Deine XP:", MIST, false);
        c.text(56, y + 8, Msg.coins(v.ownXp()), XPC, true);
        int bw = 74;
        c.smallButton(W - 8 - 3 * bw - 8, y + 4, bw, KitData.Small.DARK, "+100", XPC, true);
        c.action(W - 8 - 3 * bw - 8, y + 4, bw, KitData.SMALL_H, v.deposit100(), "100 eigene XP einzahlen");
        c.smallButton(W - 8 - 2 * bw - 4, y + 4, bw, KitData.Small.DARK, "+1.000", XPC, true);
        c.action(W - 8 - 2 * bw - 4, y + 4, bw, KitData.SMALL_H, v.deposit1000(), "1.000 eigene XP einzahlen");
        c.smallButton(W - 8 - bw, y + 4, bw, KitData.Small.GREEN, "Alles", CLOUD, true);
        c.action(W - 8 - bw, y + 4, bw, KitData.SMALL_H, v.depositAll(), "Alle eigenen XP einzahlen");
        y += 28;
        int fw = (W - 8) / 3;
        if (v.travel() != null) {
            c.smallButton(0, y, fw, KitData.Small.DARK, "Hinreisen", SKY, false);
            c.action(0, y, fw, KitData.SMALL_H, v.travel(), "Zum Block des Teams");
        }
        c.smallButton(fw + 4, y, fw, KitData.Small.DARK, "Alle Blöcke", CLOUD, false);
        c.action(fw + 4, y, fw, KitData.SMALL_H, v.back(), "Alle One-Block-Plattformen");
        if (v.release() != null) {
            c.smallButton(2 * (fw + 4), y, fw, KitData.Small.DARK, "Freigeben", RED, false);
            c.action(2 * (fw + 4), y, fw, KitData.SMALL_H, v.release(), "Block für andere Teams freigeben");
        }
        return c;
    }

    // =================================================================================================================
    // Ohne Pack: Chat
    // =================================================================================================================

    public static void chat(Player p) {
        OneBlockService s = ob();
        TeamService.Team t = s.team(p.getUniqueId());
        Msg.send(p, "<#9AD8FF><b>One-Block</b>");
        for (Platform pf : s.platforms()) {
            if (!s.mayTravel(p, pf)) {
                Msg.send(p, " <white>" + pf.name() + " <dark_gray>· <gray>unentdeckt · " + s.hint(pf) + " vom Spawn");
                continue;
            }
            Msg.send(p, " <white>" + pf.name() + " <dark_gray>· " + (pf.claimed() ? "<gold>Team " + Msg.escape(pf.teamName) : "<green>frei")
                    + " <click:run_command:'/oneblock tp " + (pf.index + 1) + "'><aqua>[Hin]</click>"
                    + (!pf.claimed() && t != null ? " <click:run_command:'/oneblock beanspruchen " + (pf.index + 1) + "'><gold>[Beanspruchen]</click>" : ""));
        }
        if (t != null) {
            Progress pr = s.progress(t);
            Msg.send(p, " <gray>Block-XP: <green>" + Msg.coins(pr.xp) + " <gray>· Stufe <white>" + pr.total() + " <gray>· <white>/oneblock einzahlen <Menge|alles>");
            for (Upgrade u : Upgrade.values()) {
                long price = s.price(pr, u);
                Msg.send(p, "  <white>" + u.label + " Lvl " + pr.level(u) + "/" + s.max(u) + " · Stufe " + UpgradeTier.of(pr.level(u)) + " <gray>(" + s.effect(u, pr.level(u)) + ")"
                        + (price < 0 ? " <gold>ausgebaut" : " <click:run_command:'/oneblock upgrade " + u.key() + "'><gold>[" + Msg.coins(price) + " XP]</click>"));
            }
        }
    }

    // =================================================================================================================
    // SelfTest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        reg.accept("One-Block", () -> overview(new Overview("Kuby", "One-Block 2 · Block-XP 12.340 · Block-Level 75", 4210, List.of(
                new Spot(Material.GRASS_BLOCK, "One-Block 1", "Frei · Beanspruchen: 25.000 $", GREEN, "Beanspruchen", KitData.Small.GOLD, GOLD, no, no),
                new Spot(Material.DIAMOND, "One-Block 2", "Euer Block · Block-Level 75 · 12.345 abgebaut", GOLD, "Upgrades", KitData.Small.GREEN, CLOUD, no, no),
                new Spot(Material.EMERALD, "One-Block 3", "Team " + w, MIST, "Belegt", KitData.Small.DARK, DIM, null, no)), no, no)));
        reg.accept("One-Block verteilt", () -> overview(new Overview("Kuby", "Block-XP 120 · Block-Level 0 · noch kein Block", 4210, List.of(
                new Spot(Material.GRASS_BLOCK, "One-Block 1", "Gesucht: Nordwesten, ca. 14.000 Blöcke", MIST, "Erst finden", KitData.Small.DARK, DIM, null, null),
                new Spot(Material.GRASS_BLOCK, "One-Block 2", "Team " + w + " · Südosten, ca. 20.000 Blöcke", MIST, "Erst finden", KitData.Small.DARK, DIM, null, null),
                new Spot(Material.GRASS_BLOCK, "One-Block 3", "Frei · Beanspruchen: 25.000 $", GREEN, "Beanspruchen", KitData.Small.GOLD, GOLD, no, no)), no, no)));
        reg.accept("One-Block leer", () -> overview(new Overview(null, "Ohne Team: /team erstellen <Name>, dann einen Block beanspruchen", 0,
                List.of(), null, no)));
        List<Card> cards = new ArrayList<>();
        int i = 0;
        int[] levels = {3, 12, 50, 16, 27, 15};
        for (Upgrade u : Upgrade.values()) {
            int lvl = levels[i];
            cards.add(new Card(u, lvl, 50, "Selten x12,8", lvl == 50 ? null : "Selten x13,1", lvl == 50 ? -1 : lvl >= 20 ? 9_999 : 999_999_999L,
                    lvl >= 20, i % 2 == 0, no));
            i++;
        }
        reg.accept("One-Block Upgrades", () -> block(new BlockView(w, "Team " + w + " · 999.999.999 abgebaut", 999_999_999L, 999_999_999L, 55,
                999_999_999L, cards, no, no, no, no, no, no)));
        reg.accept("One-Block Upgrades ohne Block", () -> block(new BlockView("Team Kuby", "Team Kuby · noch kein Block beansprucht", 120, 0, 0,
                35, cards, no, no, no, null, null, no)));
    }
}
