package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

import static de.kuby.kubysurvival.angeln.AngelScreens.*;

/** Fisch-Markt: Karten 2 x 4 mit Fisch, Sternen, Combo, „1 zu N“, Verkäufer und Preis in Juwelen. */
public final class AngelMarketScreen {

    static final int PER_PAGE = 8;
    private static final TextColor JEWEL = TextColor.color(0x5FE3A0), PINK = TextColor.color(0xFF8AD8);

    private AngelMarketScreen() {
    }

    public record Card(String id, FishSpecies species, int stars, FishVariant variant, double kg, double mult, double odds,
                       String seller, long price, boolean own) {}

    public record View(List<Card> cards, int page, int pages, long jewels, int mine, String armed) {}

    public interface Actions {
        Consumer<Player> back();

        Consumer<Player> buy(String id);

        Consumer<Player> cancel(String id);

        Consumer<Player> page(int page);
    }

    public static Canvas canvas(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Angeln", TextColor.color(0x55FCFC));
        head(c, Theme.PURPLE, a.back(), KitData.Icon.EMERALD, "Fisch-Markt", "Extrem seltene Fische gegen Juwelen",
                List.of(Span.of("Juwelen ", GRAY), Span.bold(AngelText.num(v.jewels), JEWEL)));
        int y = 54;
        c.box(0, y, W, 30, KitData.Box.NAVY);
        c.text(10, y + 6, Canvas.fit("Anbieten: Fisch in die Hand, dann /angeln markt anbieten <Juwelen>", false, 370), LIGHT, false);
        c.text(10, y + 17, "Nur Fische mit „1 zu …“ ab 250.000 · deine Angebote: " + v.mine, GRAY, false);
        y += 36;
        if (v.cards.isEmpty()) {
            c.box(0, y, W, 40, KitData.Box.NAVY);
            c.textCenter(W / 2, y + 16, "Gerade bietet niemand einen Fisch an.", GRAY, false);
            return c;
        }
        for (int i = 0; i < v.cards.size() && i < PER_PAGE; i++) {
            Card k = v.cards.get(i);
            int x = (i % 2) * 198, cy = y + (i / 2) * 66;
            c.box(x, cy, 192, 62, k.own ? KitData.Box.TEAL : KitData.Box.NAVY);
            c.icon(x + 8, cy + 8, fishIcon(k.species, true), false);
            TextColor nc = k.variant.special() ? k.variant.color() : k.species.rarity().color();
            c.text(x + 30, cy + 7, Canvas.fit(k.variant.name(k.species), true, 154), nc, true);
            String st = k.stars >= 6 ? "★★★★★★" : "★".repeat(k.stars);
            c.text(x + 30, cy + 18, List.of(Span.of(st, GOLD_T), Span.of(" · " + AngelText.kg(k.kg), LIGHT)));
            c.text(x + 8, cy + 30, Canvas.fit("1 zu " + AngelText.num(Math.round(k.odds)) + (k.mult > 1 ? " · Combo x" + AngelText.dec(k.mult) : ""),
                    false, 176), PINK, false);
            boolean armed = ("buy:" + k.id).equals(v.armed);
            int bw;
            if (k.own) {
                bw = textButtonRight(c, x + 186, cy + 40, KitData.Button.PURPLE_LINE, "Zurücknehmen", TextColor.color(0xD6A4FF), false,
                        a.cancel(k.id), "Angebot zurücknehmen, der Fisch kommt ins Inventar");
            } else {
                bw = textButtonRight(c, x + 186, cy + 40, KitData.Button.PURPLE, armed ? "Nochmal!" : Canvas.num(k.price) + " Juwelen", ON_PURPLE,
                        true, a.buy(k.id), "Zweimal klicken zum Kaufen · " + AngelText.num(k.price) + " Juwelen · Du hast "
                                + AngelText.num(v.jewels));
            }
            c.text(x + 8, cy + 46, Canvas.fit("von " + k.seller, false, Math.max(20, 186 - bw - 8 - 8)), GRAY, false);
        }
        int py = y + 4 * 66 + 2;
        if (v.page > 0) textButton(c, 0, py, KitData.Button.TEAL_LINE, "‹ Zurück", AQUA, false, a.page(v.page - 1), "Vorige Seite");
        c.textCenter(W / 2, py + 7, "Seite " + (v.page + 1) + " / " + Math.max(1, v.pages), GRAY, false);
        if (v.page + 1 < v.pages) textButtonRight(c, W, py, KitData.Button.TEAL_LINE, "Weiter ›", AQUA, false, a.page(v.page + 1), "Nächste Seite");
        return c;
    }
}
