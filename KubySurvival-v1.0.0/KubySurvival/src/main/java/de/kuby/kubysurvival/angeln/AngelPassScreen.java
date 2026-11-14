package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

import static de.kuby.kubysurvival.angeln.AngelScreens.*;

/**
 * Angelpass-Dialog: ohne Pass die Vorteile und der Kauf-Knopf, mit Pass Stufe, Fortschritt, Stufe kaufen, alles
 * abholen und die Belohnungen in Seiten zu 15 (5 x 3 Karten). Kein Gratis-Pfad.
 */
public final class AngelPassScreen {

    static final int PER_PAGE = 15;
    private static final TextColor JEWEL = TextColor.color(0x5FE3A0), PINK = TextColor.color(0xFF8AD8);

    private AngelPassScreen() {
    }

    public record View(boolean owned, int level, long xp, long perLevel, int claimable, long price, long levelPrice, long jewels,
                       int page, boolean[] claimed, String armed) {}

    public interface Actions {
        Consumer<Player> back();

        Consumer<Player> buy();

        Consumer<Player> buyLevel();

        Consumer<Player> claim();

        Consumer<Player> page(int page);
    }

    public static Canvas canvas(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Angeln", TextColor.color(0x55FCFC));
        head(c, Theme.PURPLE, a.back(), KitData.Icon.ROD, "Angelpass", v.owned ? "Stufe " + v.level + " von " + AngelPass.MAX
                        : "75 Stufen · drei besondere Angeln",
                List.of(Span.of("Juwelen ", GRAY), Span.bold(AngelText.num(v.jewels), JEWEL)));

        // Vorteile (immer sichtbar)
        int y = 54;
        c.box(0, y, W, 72, v.owned ? KitData.Box.GREEN : KitData.Box.PURPLE);
        c.text(10, y + 9, v.owned ? "Deine Pass-Vorteile (immer aktiv)" : "Mit dem Angelpass immer aktiv", v.owned ? GREEN : PINK, true);
        c.text(12, y + 23, List.of(Span.bold("+150 %", GOLD_T), Span.of(" Gewinn beim Händler", LIGHT)));
        c.text(200, y + 23, List.of(Span.bold("2x", GOLD_T), Span.of(" so schnelles Angeln", LIGHT)));
        c.text(12, y + 36, List.of(Span.bold("-50 %", GOLD_T), Span.of(" auf Coin- und Token-Preise", LIGHT)));
        c.text(200, y + 36, List.of(Span.bold("2x", GOLD_T), Span.of(" Glück überall", LIGHT)));
        c.text(12, y + 49, Canvas.fit("Stufe 25 Goldfieber-Angel · 50 Sturm-Angel · 75 Kosmos-Angel", false, 366), LIGHT, false);
        y += 78;

        if (!v.owned) {
            c.box(0, y, W, 60, KitData.Box.NAVY);
            c.text(10, y + 9, "Angelpass kaufen", CYAN, true);
            c.text(12, y + 23, "Einmal kaufen, Stufen mit Angel-Punkten aus jedem Fang.", LIGHT, false);
            c.text(12, y + 36, "Kein Gratis-Pfad: Belohnungen nur mit Pass.", GRAY, false);
            boolean armed = "pass".equals(v.armed);
            textButtonRight(c, 380, y + 30, KitData.Button.PURPLE, armed ? "Nochmal klicken!" : "Kaufen · " + AngelText.num(v.price) + " Juwelen",
                    ON_PURPLE, true, a.buy(), "Zweimal klicken zum Kaufen · Du hast " + AngelText.num(v.jewels) + " Juwelen");
            y += 66;
        } else {
            c.box(0, y, W, 46, KitData.Box.NAVY);
            int lw = c.text(12, y + 9, "Stufe " + v.level, WHITE, true);
            boolean max = v.level >= AngelPass.MAX;
            long into = max ? v.perLevel : v.xp - (long) v.level * v.perLevel;
            c.bar(Math.max(70, 12 + lw + 10), y + 9, 120, (double) into / v.perLevel, KitData.Fill.GOLD);
            c.text(Math.max(70, 12 + lw + 10) + 126, y + 9, max ? "Alle Stufen erreicht" : AngelText.num(into) + " / "
                    + AngelText.num(v.perLevel) + " Punkte", LIGHT, false);
            int bx = 12;
            if (!max) {
                boolean armed = "level".equals(v.armed);
                bx += textButton(c, bx, y + 20, KitData.Button.PURPLE, armed ? "Nochmal klicken!" : "Stufe kaufen · "
                        + AngelText.num(v.levelPrice) + " Juwelen", ON_PURPLE, true, a.buyLevel(), "Zweimal klicken: eine Stufe kaufen") + 6;
            }
            if (v.claimable > 0) {
                textButton(c, bx, y + 20, KitData.Button.GREEN, "Alles abholen (" + v.claimable + ")", ON_GREEN, true, a.claim(),
                        "Alle erreichten Belohnungen abholen");
            }
            y += 52;
        }

        // Belohnungen: 15 je Seite
        int pages = (AngelPass.MAX + PER_PAGE - 1) / PER_PAGE;
        int page = Math.max(0, Math.min(pages - 1, v.page));
        int cw = 74, ch = 52, gap = 4;
        for (int i = 0; i < PER_PAGE; i++) {
            int lv = page * PER_PAGE + i + 1;
            if (lv > AngelPass.MAX) break;
            AngelPass.Reward r = AngelPass.reward(lv);
            int x = (i % 5) * (cw + gap), cy = y + (i / 5) * (ch + gap);
            boolean reached = v.owned && lv <= v.level;
            boolean got = v.claimed != null && lv - 1 < v.claimed.length && v.claimed[lv - 1];
            KitData.Box box = r.kind() == AngelPass.Kind.ROD ? KitData.Box.GOLD : got ? KitData.Box.GRAY : reached ? KitData.Box.GREEN
                    : KitData.Box.NAVY;
            c.box(x, cy, cw, ch, box);
            c.text(x + 6, cy + 6, String.valueOf(lv), reached ? GOLD_T : GRAY, true);
            if (got) c.textRight(x + cw - 6, cy + 6, "✔", GREEN, true);
            c.icon(x + (cw - 16) / 2, cy + 16, r.icon(), false);
            String label = switch (r.kind()) {
                case MONEY -> Canvas.num(r.amount()) + " $";
                case POINTS -> Canvas.num(r.amount()) + " P";
                case BOOST -> "Boost " + r.amount() + " Min";
                case ROD -> r.rod().label.replace("-Angel", "");
            };
            c.textCenter(x + cw / 2, cy + 37, Canvas.fit(label, r.kind() == AngelPass.Kind.ROD, cw - 8),
                    r.kind() == AngelPass.Kind.ROD ? GOLD_T : LIGHT, r.kind() == AngelPass.Kind.ROD);
            String hover = "Stufe " + lv + ": " + r.label() + (r.kind() == AngelPass.Kind.ROD ? " · " + r.rod().perk : "");
            c.action(x, cy, cw, ch, null, hover);
        }
        int py = y + 3 * (ch + gap) + 2;
        if (page > 0) textButton(c, 0, py, KitData.Button.TEAL_LINE, "‹ Stufe " + ((page - 1) * PER_PAGE + 1) + "–" + (page * PER_PAGE),
                AQUA, false, a.page(page - 1), "Vorige Seite");
        c.textCenter(W / 2, py + 7, "Seite " + (page + 1) + " / " + pages, GRAY, false);
        if (page + 1 < pages) textButtonRight(c, W, py, KitData.Button.TEAL_LINE, "Stufe " + ((page + 1) * PER_PAGE + 1) + "–"
                + Math.min(AngelPass.MAX, (page + 2) * PER_PAGE) + " ›", AQUA, false, a.page(page + 1), "Nächste Seite");
        return c;
    }
}
