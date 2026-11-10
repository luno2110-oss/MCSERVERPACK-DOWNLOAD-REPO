package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.InfoBar;
import de.kuby.kubyplugins.service.InfoBar.Design;
import de.kuby.kubyplugins.service.InfoBar.Element;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /bossbar mit Grafik-Pack nach der Referenz „Neue-referenzen“: Kopf mit Schild und „Leiste An/Aus“, Bereich „Design“
 * mit Standard und Leiste aus-/einblenden, je Design ein Knopf und eine Vorschau der Leiste mit den eigenen Werten (Geld
 * und Job), darunter die Elemente mit Pfeilen (Reihenfolge), Symbol, Name, Beschreibung und Auge (sichtbar).
 * Die Vorschau zeichnet dieselben Teile wie die Leiste oben (extract_infobar.py), mit denselben Abständen.
 */
public final class InfoBarScreen {

    public static final int W = 390, HEIGHT = 350;
    private static final TextColor BLUE = TextColor.color(0x62CAFC), GRAY = TextColor.color(0x9FA1A7),
            BODY = TextColor.color(0xD1D2D5), WHITE = TextColor.color(0xFCFCFC), DIM = TextColor.color(0x797D84),
            GREEN = TextColor.color(0x6DE288), RED = TextColor.color(0xF97785), GOLD = TextColor.color(0xFCD45D),
            NAV = TextColor.color(0xC0C4CB), ON = TextColor.color(0x201400), DOT = TextColor.color(0x545454);
    private static final int ROW0 = 218, ROW_DY = 18, DESIGN0 = 89, DESIGN_DY = 27;

    /** Werte für die Vorschau: Geld und Juwelen (fertig geschrieben), Job, Insel (Skyblock). */
    public record Sample(String coins, String jewels, InfoBar.JobLine job, InfoBar.IslandLine island) {
        public Sample(String coins, String jewels, InfoBar.JobLine job) {
            this(coins, jewels, job, InfoBar.IslandLine.NONE);
        }
    }

    /** sky: Leiste in Skyblock (eigene Designs und Elemente). */
    public record View(boolean on, Design design, List<Element> order, List<Boolean> visible, Sample sample, boolean sky) {
        public View(boolean on, Design design, List<Element> order, List<Boolean> visible, Sample sample) {
            this(on, design, order, visible, sample, design.sky);
        }
    }

    public interface Actions {
        Consumer<Player> design(Design d);

        Consumer<Player> reset();

        Consumer<Player> toggleBar();

        Consumer<Player> move(Element e, int dir);

        Consumer<Player> toggle(Element e);
    }

    private final KubyPlugins plugin;

    public InfoBarScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void show(Player p) {
        UUID id = p.getUniqueId();
        InfoBar bar = plugin.infoBar();
        boolean sky = bar.sky(p);
        List<Element> order = bar.order(id, sky);
        List<Boolean> visible = new ArrayList<>();
        for (Element e : order) visible.add(bar.visible(id, e, sky));
        View v = new View(bar.on(id), bar.design(id, sky), order, visible,
                new Sample(number(plugin.economy().balance(id)), number(plugin.jewels().balance(id)), InfoBar.job(p),
                        sky ? InfoBar.island(p) : InfoBar.IslandLine.NONE), sky);
        UiDialog.show(p, canvas(v, actions(sky)), HEIGHT);
    }

    private Actions actions(boolean sky) {
        InfoBar bar = plugin.infoBar();
        return new Actions() {
            public Consumer<Player> design(Design d) {
                return pl -> {
                    bar.design(pl.getUniqueId(), d);
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    show(pl);
                };
            }

            public Consumer<Player> reset() {
                return pl -> {
                    bar.reset(pl.getUniqueId(), sky);
                    Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
                    show(pl);
                };
            }

            public Consumer<Player> toggleBar() {
                return pl -> {
                    bar.on(pl.getUniqueId(), !bar.on(pl.getUniqueId()));
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    show(pl);
                };
            }

            public Consumer<Player> move(Element e, int dir) {
                return pl -> {
                    bar.move(pl.getUniqueId(), e, dir, sky);
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    show(pl);
                };
            }

            public Consumer<Player> toggle(Element e) {
                return pl -> {
                    bar.toggle(pl.getUniqueId(), e, sky);
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    show(pl);
                };
            }
        };
    }

    public static Canvas canvas(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Info-Leiste", WHITE);
        // Kopf
        c.box(0, 0, W, 52, KitData.Box.IB_HEAD);
        c.sprite(11, 13, KitData.Sprite.IB_SIGN);
        c.text(47, 16, upper(v.sky() ? "Insel-Leiste" : "Info-Leiste"), BLUE, true);
        c.text(47, 29, "Was oben in deiner Leiste steht, und wo.", GRAY, false);
        // „Leiste“ endet 6 px vor „An“ / „Aus“ wie im Original
        String now = v.on() ? (de.kuby.kubyplugins.util.I18n.context() ? "On" : "An") : "Aus";
        int state = 380 - c.textRight(380, 22, now, v.on() ? GREEN : RED, true);
        c.textRight(state - 6, 22, "Leiste", GRAY, false);

        // Design
        c.box(0, 54, W, 142, KitData.Box.IB_PANEL);
        c.text(10, 68, upper("Design"), BLUE, true);
        c.box(177, 62, 78, 20, KitData.Box.IB_GOLD, Canvas.Z_BUTTON);
        c.sprite(185, 65, KitData.Sprite.IB_REFRESH);
        c.text(201, 68, "Standard", GOLD, false);
        c.action(177, 62, 78, 20, a.reset(), "Design, Reihenfolge und Elemente zurücksetzen");
        c.box(259, 62, 123, 20, KitData.Box.IB_GRAY, Canvas.Z_BUTTON);
        c.sprite(267, v.on() ? 65 : 66, v.on() ? KitData.Sprite.IB_HIDE : KitData.Sprite.IB_EYE_ON);
        c.text(283, 68, v.on() ? "Leiste ausblenden" : "Leiste einblenden", NAV, false);
        c.action(259, 62, 123, 20, a.toggleBar(), v.on() ? "Info-Leiste aus" : "Info-Leiste an");
        List<Design> ds = Design.group(v.sky());
        for (int i = 0; i < ds.size(); i++) {
            Design d = ds.get(i);
            int y = DESIGN0 + DESIGN_DY * i;
            boolean sel = d == v.design();
            c.box(10, y, 64, 20, sel ? KitData.Box.IB_BTN_ON : KitData.Box.IB_BTN_OFF, Canvas.Z_BUTTON);
            String label = d.label;
            c.text(10 + (64 - Canvas.width(Canvas.clean(label), sel)) / 2, y + 6, label, sel ? ON : WHITE, sel);
            int x = 80;
            x += bar(c, x, y, d, money(v.sample(), d)) + 4;
            bar(c, x, y, d, v.sky() ? island(v.sample().island(), d) : job(v.sample().job(), d));
            c.action(10, y, W - 14, 20, a.design(d), "Design " + label);
        }

        // Elemente
        c.box(0, 207, W, 142, KitData.Box.IB_PANEL);
        List<Element> order = v.order();
        for (int i = 0; i < order.size(); i++) {
            Element e = order.get(i);
            boolean vis = v.visible().get(i);
            int y = ROW0 + ROW_DY * i;
            boolean up = i > 0, down = i + 1 < order.size();
            c.sprite(13, y, up ? KitData.Sprite.IB_UP_ON : KitData.Sprite.IB_UP_OFF);
            c.sprite(29, y, down ? KitData.Sprite.IB_DOWN_ON : KitData.Sprite.IB_DOWN_OFF);
            if (up) c.action(9, y - 4, 14, 16, a.move(e, -1), "Nach oben");
            if (down) c.action(25, y - 4, 14, 16, a.move(e, 1), "Nach unten");
            KitData.Sprite icon = icon(e);
            c.sprite(icon == KitData.Sprite.IB_EL_TIME || icon == KitData.Sprite.IB_EL_COINS
                    || icon == KitData.Sprite.IB_EL_JEWELS ? 46 : 45, y - 2, icon);
            c.text(63, y, e.label, vis ? WHITE : BODY, true);
            c.text(139, y, e.desc, vis ? BODY : DIM, false);
            c.sprite(368, vis ? y + 2 : y, vis ? KitData.Sprite.IB_EYE_ON : KitData.Sprite.IB_EYE_OFF);
            c.action(60, y - 4, W - 66, 16, a.toggle(e), vis ? "Ausblenden" : "Einblenden");
        }
        return c;
    }

    private static KitData.Sprite icon(Element e) {
        return switch (e) {
            case TIME -> KitData.Sprite.IB_EL_TIME;
            case COINS -> KitData.Sprite.IB_EL_COINS;
            case JEWELS -> KitData.Sprite.IB_EL_JEWELS;
            case JOB -> KitData.Sprite.IB_EL_JOB;
            case PLACE -> KitData.Sprite.IB_EL_PLACE;
            case QUEST -> KitData.Sprite.IB_EL_QUEST;
            case SIDE -> KitData.Sprite.IB_EL_SIDE;
            case ISLAND -> KitData.Sprite.IBS_EL_ISLAND;
            case XP -> KitData.Sprite.IBS_EL_XP;
            case GEN -> KitData.Sprite.IBS_EL_GEN;
        };
    }

    // =================================================================================================================
    // Vorschau der Leiste (gleiche Maße wie oben im Spiel)
    // =================================================================================================================

    /** Ein Stück Inhalt: Grafik (dy unter der Oberkante, Breite mit Abstand) oder Text. */
    private record Piece(KitData.Sprite icon, int dy, String text, TextColor color, int width) {
        static Piece icon(KitData.Sprite s, int dy, int gap) {
            return new Piece(s, dy, null, null, s.width + gap);
        }

        static Piece text(String t, TextColor color) {
            return new Piece(null, 0, t, color, Canvas.width(Canvas.clean(t), false));
        }

        static Piece gap(int px) {
            return new Piece(null, 0, null, null, px);
        }
    }

    /** Farben der Schrift: helle Designs (Wald, Wolke) dunkel ohne Schatten, die anderen hell mit Schatten. */
    private static TextColor col(Design d, int wald, int dark) {
        return TextColor.color(d.dark ? dark : wald);
    }

    private static List<Piece> money(Sample s, Design d) {
        TextColor t = col(d, d.sky ? 0x2D3846 : 0x493326, 0xFCFCFC);
        return List.of(Piece.icon(KitData.Sprite.IB_HUD_COIN, 4, 4), Piece.text(s.coins(), t), Piece.gap(8),
                Piece.icon(KitData.Sprite.IB_HUD_GEM, 4, 5), Piece.text(s.jewels(), t));
    }

    /** Insel wie in der Skyblock-Leiste (SkyblockHud): Level, Fortschritt, Platz. */
    private static List<Piece> island(InfoBar.IslandLine i, Design d) {
        TextColor value = col(d, 0x2D3846, 0xFCFCFC), gray = col(d, 0x5E6B80, 0xA8A8A8);
        List<Piece> out = new ArrayList<>();
        out.add(Piece.icon(KitData.Sprite.IBS_HUD_ISLAND, 4, 4));
        if (!i.has()) {
            out.add(Piece.text("Keine Insel", gray));
            out.add(Piece.text(" · ", DOT));
            out.add(Piece.text("/insel", col(d, 0x2E7D32, 0x7CE36C)));
            return out;
        }
        out.add(Piece.text("Lv ", gray));
        out.add(Piece.text(number(i.level()), value));
        out.add(Piece.text(" · ", DOT));
        out.add(Piece.text(i.percent() + "%", col(d, 0x8A6D00, 0xFCFC54)));
        if (i.rank() > 0) {
            out.add(Piece.text(" · ", DOT));
            out.add(Piece.text("#" + i.rank(), col(d, 0xA0522D, 0xFFB45A)));
        }
        return out;
    }

    private static List<Piece> job(InfoBar.JobLine j, Design d) {
        TextColor name = col(d, 0x2E8D8D, 0x54FCFC), gray = col(d, 0x717171, 0xA8A8A8);
        List<Piece> out = new ArrayList<>();
        out.add(Piece.icon(KitData.Sprite.IB_HUD_ROD, 3, 4));
        if (j.name() == null) {
            out.add(Piece.text("Kein Job", gray));
            out.add(Piece.text(" · ", DOT));
            out.add(Piece.text("/job", name));
            return out;
        }
        out.add(Piece.text(j.name(), name));
        out.add(Piece.text(" Lv ", gray));
        out.add(Piece.text(String.valueOf(j.level()), col(d, 0x493326, 0xFCFCFC)));
        out.add(Piece.text(" · ", DOT));
        out.add(Piece.text(j.percent() + "%", col(d, 0x7B7B28, 0xFCFC54)));
        out.add(Piece.text(" · ", DOT));
        out.add(Piece.text(number(j.earned()), col(d, 0x339C33, 0x54FC54)));
        out.add(Piece.text("/" + number(j.cap()), gray));
        return out;
    }

    /** Zeichnet eine Leiste ab x (Oberkante des Mittelstücks top). Gibt die Breite zurück. */
    private static int bar(Canvas c, int x, int top, Design d, List<Piece> pieces) {
        int content = 0;
        for (Piece p : pieces) content += p.width();
        int body = content + 1;
        c.sprite(x, top - d.up, capL(d));
        c.strip(x + d.cap, top, strip(d), body);
        c.sprite(x + d.cap + body, top - d.up, capR(d));
        int cx = x + d.cap + 1;
        for (Piece p : pieces) {
            if (p.icon() != null) c.sprite(cx, top + p.dy(), p.icon());
            else if (p.text() != null) {
                if (d.dark) c.text(cx, top + 5, p.text(), p.color(), false);
                else c.textFlat(cx, top + 5, p.text(), p.color(), false);
            }
            cx += p.width();
        }
        return 2 * d.cap + body;
    }

    private static KitData.Sprite capL(Design d) {
        return switch (d) {
            case WALD -> KitData.Sprite.IB_CAP_L_WALD;
            case KRISTALL -> KitData.Sprite.IB_CAP_L_KRISTALL;
            case KRONE -> KitData.Sprite.IB_CAP_L_KRONE;
            case FROST -> KitData.Sprite.IB_CAP_L_FROST;
            case INSEL -> KitData.Sprite.IBS_CAP_L_INSEL;
            case WOLKE -> KitData.Sprite.IBS_CAP_L_WOLKE;
            case NETHER -> KitData.Sprite.IBS_CAP_L_NETHER;
            case ENDE -> KitData.Sprite.IBS_CAP_L_ENDE;
        };
    }

    private static KitData.Sprite capR(Design d) {
        return switch (d) {
            case WALD -> KitData.Sprite.IB_CAP_R_WALD;
            case KRISTALL -> KitData.Sprite.IB_CAP_R_KRISTALL;
            case KRONE -> KitData.Sprite.IB_CAP_R_KRONE;
            case FROST -> KitData.Sprite.IB_CAP_R_FROST;
            case INSEL -> KitData.Sprite.IBS_CAP_R_INSEL;
            case WOLKE -> KitData.Sprite.IBS_CAP_R_WOLKE;
            case NETHER -> KitData.Sprite.IBS_CAP_R_NETHER;
            case ENDE -> KitData.Sprite.IBS_CAP_R_ENDE;
        };
    }

    private static KitData.Strip strip(Design d) {
        return switch (d) {
            case WALD -> KitData.Strip.IB_BODY_WALD;
            case KRISTALL -> KitData.Strip.IB_BODY_KRISTALL;
            case KRONE -> KitData.Strip.IB_BODY_KRONE;
            case FROST -> KitData.Strip.IB_BODY_FROST;
            case INSEL -> KitData.Strip.IBS_BODY_INSEL;
            case WOLKE -> KitData.Strip.IBS_BODY_WOLKE;
            case NETHER -> KitData.Strip.IBS_BODY_NETHER;
            case ENDE -> KitData.Strip.IBS_BODY_ENDE;
        };
    }

    public static String number(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    private static String upper(String text) {
        return de.kuby.kubyplugins.util.I18n.ctx(text).toUpperCase(Locale.GERMAN);
    }
}
