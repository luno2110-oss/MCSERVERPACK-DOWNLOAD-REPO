package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService.Island;
import de.kuby.kubyskyblock.service.StageService;
import de.kuby.kubyskyblock.service.StageService.Req;
import de.kuby.kubyskyblock.service.StageService.Stage;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog „Stufen“ (/insel stufen, Kachel Stufen): Nether- und End-Stufe mit Stand, allen Bedingungen zum Freischalten
 * (Haken oder was noch fehlt) und Knopf zum Freischalten oder Hinreisen.
 */
public final class StageScreen {

    static final int W = IslandScreen.W;
    static final TextColor SKY = IslandScreen.SKY, CLOUD = IslandScreen.CLOUD, MIST = IslandScreen.MIST, GOLD = IslandScreen.GOLD,
            GREEN = IslandScreen.GREEN, RED = IslandScreen.RED, DIM = IslandScreen.DIM;
    static final TextColor NETHER = TextColor.color(0xFF7A4A), END = TextColor.color(0xC98CFF);

    private StageScreen() {
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

    /** Eine Stufe: Name, Farbe, Bild, freigeschaltet?, Stand, Beschreibung, Bedingungen, Knopf (Text, Stil, Aktion). */
    public record Card(String name, TextColor color, KitData.Sprite icon, boolean unlocked, String status, String desc, List<Req> reqs,
                       String button, KitData.Small style, Consumer<Player> action) {}

    public record View(String island, List<Card> cards, Consumer<Player> back) {}

    static KitData.Sprite icon(Stage s) {
        return s == Stage.NETHER ? KitData.Sprite.HB_ST_NETHER : KitData.Sprite.HB_ST_END;
    }

    static String desc(Stage s) {
        return s == Stage.NETHER ? "Eigene Nether-Insel: Basalt-Generator, Lohen- und Netherit-Generator"
                : "Eigene End-Insel: Endstein-Generator, Ender- und Shulker-Generator";
    }

    public static void open(Player p) {
        Island i = sb().islands().islandOf(p.getUniqueId());
        if (i == null) {
            feedback(p, "Du hast keine Insel.");
            return;
        }
        if (!sb().core().uiPack().active(p)) {
            chat(p, i);
            return;
        }
        StageService ss = sb().stages();
        boolean manage = sb().islands().canManage(i, p.getUniqueId());
        List<Card> cards = new ArrayList<>();
        for (Stage s : Stage.values()) {
            boolean un = ss.unlocked(i, s);
            boolean ready = !un && ss.ready(i, s);
            String button;
            KitData.Small style;
            Consumer<Player> act = null;
            if (un) {
                button = "Hinreisen";
                style = KitData.Small.GREEN;
                act = run(pl -> feedback(pl, ss.travel(pl, s)));
            } else if (ready && manage) {
                button = "Freischalten";
                style = KitData.Small.GOLD;
                act = run(pl -> {
                    String err = ss.unlock(pl, s);
                    feedback(pl, err);
                    if (err == null) open(pl);
                });
            } else {
                button = ready ? "Nur Besitzer" : "Gesperrt";
                style = KitData.Small.DARK;
            }
            cards.add(new Card(s.label + "-Stufe", s == Stage.NETHER ? NETHER : END, icon(s), un, ss.progressText(i, s), desc(s),
                    un ? List.of() : ss.requirements(i, s), button, style, act));
        }
        UiDialog.show(p, canvas(new View(i.name, cards, run(IslandScreen::open))), 330);
    }

    /** Ohne Pack: Stand und Bedingungen im Chat. */
    static void chat(Player p, Island i) {
        StageService ss = sb().stages();
        for (Stage s : Stage.values()) {
            Msg.send(p, "<" + s.color + "><b>" + s.label + "-Stufe</b> <gray>· " + ss.progressText(i, s));
            if (ss.unlocked(i, s)) {
                Msg.send(p, " <click:run_command:'/insel " + s.key + "'><green>[Hinreisen]</click>");
                continue;
            }
            for (Req r : ss.requirements(i, s)) Msg.send(p, " " + (r.done() ? "<green>✔ " : "<red>✘ ") + "<white>" + Msg.escape(r.text()) + " <gray>" + r.state());
            if (ss.ready(i, s)) Msg.send(p, " <click:run_command:'/insel freischalten " + s.key + "'><gold>[Freischalten]</click>");
        }
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Stufen", NETHER);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_ST_NETHER.width) / 2, 11 + (32 - KitData.Sprite.HB_ST_NETHER.height) / 2, KitData.Sprite.HB_ST_NETHER);
        c.headline(50, 12, "Stufen", NETHER);
        c.text(50, 36, Canvas.fit("Nether und End · " + v.island(), false, W - 50 - 90), MIST, false);
        c.smallButton(W - 8 - 70, 18, 70, KitData.Small.DARK, "Zurück", CLOUD, false);
        if (v.back() != null) c.action(W - 8 - 70, 18, 70, KitData.SMALL_H, v.back(), "Zurück");
        int y = 60;
        for (Card cd : v.cards()) {
            int rows = (cd.reqs().size() + 1) / 2;
            int h = 50 + (rows > 0 ? 11 + rows * 12 : 0) + 24;
            c.box(0, y, W, h, cd.unlocked() ? KitData.Box.GOLD : KitData.Box.CLOUD_CARD);
            c.sprite(8 + (32 - cd.icon().width) / 2, y + 8 + (32 - cd.icon().height) / 2, cd.icon());
            c.text(48, y + 9, cd.name(), cd.color(), true);
            c.textRight(W - 10, y + 9, cd.status(), cd.unlocked() ? GREEN : MIST, false);
            List<String> dl = Canvas.wrap(cd.desc(), W - 58);
            for (int k = 0; k < Math.min(2, dl.size()); k++) c.text(48, y + 22 + 10 * k, dl.get(k), MIST, false);
            int ry = y + 50;
            if (rows > 0) {
                c.text(10, ry, Canvas.caps("Bedingungen"), cd.color(), false);
                ry += 11;
                int cw = (W - 20) / 2;
                for (int k = 0; k < cd.reqs().size(); k++) {
                    Req r = cd.reqs().get(k);
                    int x = 10 + (k % 2) * cw, yy = ry + (k / 2) * 12;
                    int mw = c.text(x, yy, r.done() ? "✔" : "✘", r.done() ? GREEN : RED, true);
                    String st = r.done() ? "" : r.state();
                    int sw = st.isEmpty() ? 0 : Canvas.width(st, false) + 6;
                    c.text(x + mw + 4, yy, Canvas.fit(r.text(), false, cw - mw - 8 - sw), r.done() ? CLOUD : MIST, false);
                    if (!st.isEmpty()) c.textRight(x + cw - 6, yy, st, RED, false);
                }
                ry += rows * 12;
            }
            int bw = 92;
            c.smallButton(W - 8 - bw, y + h - KitData.SMALL_H - 6, bw, cd.style(), cd.button(),
                    cd.action() == null ? DIM : cd.style() == KitData.Small.GOLD ? GOLD : CLOUD, true);
            if (cd.action() != null) c.action(W - 8 - bw, y + h - KitData.SMALL_H - 6, bw, KitData.SMALL_H, cd.action(), cd.button());
            y += h + 4;
        }
        return c;
    }

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        List<Req> nether = List.of(new Req("Geld-Generator", true, "✔"), new Req("XP-Generator", true, "✔"),
                new Req("Erz-Generator", false, "Level 7/10"), new Req("Holz-Generator", true, "✔"), new Req("Farm-Generator", false, "fehlt"),
                new Req("Mob-Generator", true, "✔"), new Req("Nether-Generator", false, "fehlt"), new Req("Edelstein-Generator", false, "Level 9/10"),
                new Req("Alle Generatoren auf Level 10", false, "99 darunter"));
        List<Req> end = List.of(new Req("Nether-Stufe freigeschaltet", false, "gesperrt"), new Req("Lohen-Generator", false, "fehlt"),
                new Req("Netherit-Generator", false, "fehlt"), new Req("Alle Nether-Generatoren auf Level 10", false, "keine"));
        reg.accept("Stufen gesperrt", () -> canvas(new View(w, List.of(
                new Card("Nether-Stufe", NETHER, icon(Stage.NETHER), false, "4/9 Bedingungen", desc(Stage.NETHER), nether, "Gesperrt", KitData.Small.DARK, null),
                new Card("End-Stufe", END, icon(Stage.END), false, "0/4 Bedingungen", desc(Stage.END), end, "Gesperrt", KitData.Small.DARK, null)), no)));
        reg.accept("Stufen Nether offen", () -> canvas(new View("Insel", List.of(
                new Card("Nether-Stufe", NETHER, icon(Stage.NETHER), true, "Freigeschaltet", desc(Stage.NETHER), List.of(), "Hinreisen", KitData.Small.GREEN, no),
                new Card("End-Stufe", END, icon(Stage.END), false, "4/4 Bedingungen", desc(Stage.END),
                        List.of(new Req("Nether-Stufe freigeschaltet", true, "✔"), new Req("Lohen-Generator", true, "✔"),
                                new Req("Netherit-Generator", true, "✔"), new Req("Alle Nether-Generatoren auf Level 10", true, "✔")),
                        "Freischalten", KitData.Small.GOLD, no)), no)));
    }
}
