package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.PlayerSettings;
import de.kuby.kubyplugins.service.PlayerSettings.Choice;
import de.kuby.kubyplugins.service.PlayerSettings.Option;
import de.kuby.kubyplugins.service.PlayerSettings.Tab;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /settings auf dem Dialog-Kit wie in der Referenz: Kopf mit Zähler "Schalter an", fünf farbige Reiter (Chat,
 * Anfragen, Mitteilungen, Anzeige, Sonstiges), darunter die Schalter als Kacheln zu zweit (AN/AUS-Schalter oder
 * Mehrfach-Schalter mit Stellung) und unten Sprache und Auto-Übersetzen. Jede Kachel erklärt sich im Tooltip; ein
 * Klick schaltet sofort um. Welche Schalter erscheinen, hängt vom Modus ab (Survival hat z. B. Heiratsanträge statt
 * Duelle). Deutsch und Englisch.
 */
public final class SettingsScreen {

    static final int W = 390, HEIGHT = 270;
    private static final int TAB_Y = 54, TAB_W = 74, TAB_H = 34, TAB_STEP = 79, BOX_Y = 90, TILE_W = 187, TILE_H = 24;
    private static final TextColor TITLE = TextColor.color(0xB7A6FC), SUB = TextColor.color(0x9FA1A7),
            COUNT = TextColor.color(0x6DE288), DARK = TextColor.color(0x1C122D), WHITE = TextColor.color(0xFCFCFC),
            OFF = TextColor.color(0xD1D2D5), NAV = TextColor.color(0xC0C4CB), TIP_TEXT = TextColor.color(0xD1D2D5),
            TIP_OPTION = TextColor.color(0x9FA1A7), TIP_BULLET = TextColor.color(0x797D84), TIP_MARK = TextColor.color(0xFCC63C),
            TIP_CLICK = TextColor.color(0xFCFC54), TIP_WARN = TextColor.color(0xFC7A6A);

    /** Aussehen der Reiter (Reihenfolge wie PlayerSettings.Tab). */
    enum Look {
        CHAT("Chat", "Chat", TextColor.color(0x7BC4FC), KitData.SmallIcon.SET_CHAT, KitData.SmallIcon.SET_CHAT_SECTION,
                KitData.Box.SET_BLUE, KitData.Box.SETF_BLUE, KitData.Small.SET_BLUE,
                KitData.SmallIcon.SET_ON_BLUE_L, KitData.SmallIcon.SET_ON_BLUE_R, KitData.SmallIcon.SET_ON_EN_BLUE_L,
                KitData.SmallIcon.SET_ON_EN_BLUE_R),
        REQUESTS("Anfragen", "Requests", TextColor.color(0xFCC965), KitData.SmallIcon.SET_ENVELOPE,
                KitData.SmallIcon.SET_ENVELOPE, KitData.Box.SET_ORANGE, KitData.Box.SETF_ORANGE, KitData.Small.SET_ORANGE,
                KitData.SmallIcon.SET_ON_ORANGE_L, KitData.SmallIcon.SET_ON_ORANGE_R,
                KitData.SmallIcon.SET_ON_EN_ORANGE_L, KitData.SmallIcon.SET_ON_EN_ORANGE_R),
        NOTIFY("Mitteilungen", "Alerts", TextColor.color(0xFCD45D), KitData.SmallIcon.SET_BELL, KitData.SmallIcon.SET_BELL,
                KitData.Box.SET_GOLD, KitData.Box.SETF_GOLD, KitData.Small.SET_GOLD,
                KitData.SmallIcon.SET_ON_GOLD_L, KitData.SmallIcon.SET_ON_GOLD_R, KitData.SmallIcon.SET_ON_EN_GOLD_L,
                KitData.SmallIcon.SET_ON_EN_GOLD_R),
        DISPLAY("Anzeige", "Display", TextColor.color(0x7AE5D7), KitData.SmallIcon.SET_EYE, KitData.SmallIcon.SET_EYE,
                KitData.Box.SET_TEAL, KitData.Box.SETF_TEAL, KitData.Small.SET_TEAL,
                KitData.SmallIcon.SET_ON_TEAL_L, KitData.SmallIcon.SET_ON_TEAL_R, KitData.SmallIcon.SET_ON_EN_TEAL_L,
                KitData.SmallIcon.SET_ON_EN_TEAL_R),
        OTHER("Sonstiges", "Other", TextColor.color(0xB7A6FC), KitData.SmallIcon.SET_GEAR, KitData.SmallIcon.SET_GEAR,
                KitData.Box.SET_VIOLET, KitData.Box.SETF_VIOLET, KitData.Small.SET_VIOLET,
                KitData.SmallIcon.SET_ON_VIOLET_L, KitData.SmallIcon.SET_ON_VIOLET_R,
                KitData.SmallIcon.SET_ON_EN_VIOLET_L, KitData.SmallIcon.SET_ON_EN_VIOLET_R);

        final String de, en;
        final TextColor color;
        final KitData.SmallIcon icon, section, onL, onR, onEnL, onEnR;
        final KitData.Box box, filled;
        final KitData.Small pill;

        Look(String de, String en, TextColor color, KitData.SmallIcon icon, KitData.SmallIcon section, KitData.Box box,
             KitData.Box filled, KitData.Small pill, KitData.SmallIcon onL, KitData.SmallIcon onR,
             KitData.SmallIcon onEnL, KitData.SmallIcon onEnR) {
            this.de = de;
            this.en = en;
            this.color = color;
            this.icon = icon;
            this.section = section;
            this.box = box;
            this.filled = filled;
            this.pill = pill;
            this.onL = onL;
            this.onR = onR;
            this.onEnL = onEnL;
            this.onEnR = onEnR;
        }

        String label(boolean en) {
            return en ? this.en : de;
        }
    }

    /** Eine Kachel: choice null = AN/AUS-Schalter, sonst die Beschriftung der Stellung. */
    public record Tile(String key, String label, String desc, boolean on, String choice, List<String> choices,
                       int current, String warning) {}

    /**
     * on/total: Schalter an (alle Reiter); translate: Auto-Übersetzen an; translateReady: auf dem Server eingerichtet.
     */
    public record View(Tab tab, List<Tile> tiles, int on, int total, boolean english, String language,
                       boolean translate, boolean translateReady) {}

    public interface Actions {
        Consumer<Player> tab(Tab tab);

        Consumer<Player> click(String key);

        Consumer<Player> language();

        Consumer<Player> translate();
    }

    private final KubyPlugins plugin;

    public SettingsScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    public void open(Player p, Tab tab) {
        UUID id = p.getUniqueId();
        PlayerSettings s = plugin.playerSettings();
        String mode = plugin.modes().modeOf(id);
        boolean en = s.english(id);
        if (!plugin.uiPack().active(p)) {
            openChat(p, tab, mode, en);
            return;
        }
        UiDialog.show(p, canvas(view(id, tab, mode), new Actions() {
            public Consumer<Player> tab(Tab t) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    open(pl, t);
                };
            }

            public Consumer<Player> click(String key) {
                return pl -> {
                    if (!PlayerSettings.OPTIONS.get(key).shownIn(plugin.modes().modeOf(pl.getUniqueId()))) return;
                    if (PlayerSettings.OPTIONS.get(key).multi()) s.cycle(pl.getUniqueId(), key);
                    else s.toggle(pl.getUniqueId(), key);
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    open(pl, tab);
                };
            }

            public Consumer<Player> language() {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    openLanguage(pl, tab);
                };
            }

            public Consumer<Player> translate() {
                return pl -> {
                    if (!plugin.translator().ready()) {
                        Sfx.play(plugin, pl, Sfx.Kind.ERROR);
                        return;
                    }
                    s.toggle(pl.getUniqueId(), "translate");
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    open(pl, tab);
                };
            }
        }), HEIGHT);
    }

    /** Daten eines Reiters für einen Spieler im Modus mode (auch für den Selbsttest). */
    public View view(UUID id, Tab tab, String mode) {
        PlayerSettings s = plugin.playerSettings();
        boolean en = s.english(id);
        List<Tile> tiles = new ArrayList<>();
        for (Option o : PlayerSettings.shown(tab, mode)) tiles.add(tile(o, id, en, mode));
        int on = 0, total = 0;
        for (Tab t : Tab.values()) {
            for (Option o : PlayerSettings.shown(t, mode)) {
                total++;
                if (s.get(id, o.key())) on++;
            }
        }
        return new View(tab, tiles, on, total, en, language(s.choice(id, "lang"), en), s.get(id, "translate"),
                plugin.translator().ready());
    }

    private Tile tile(Option o, UUID id, boolean en, String mode) {
        PlayerSettings s = plugin.playerSettings();
        boolean on = s.get(id, o.key());
        String warning = null;
        if (o.key().equals("autotpa") && !s.get(id, "tpa") && !s.get(id, "tpahere")) {
            warning = en ? "TPA and TPAhere requests are off." : "TPA- und TPAhere-Anfragen sind aus.";
        }
        if (!o.multi()) return new Tile(o.key(), o.name(en), o.desc(en), on, null, null, -1, warning);
        String cur = s.choice(id, o.key());
        List<String> names = new ArrayList<>();
        int index = 0;
        List<Choice> choices = o.choices();
        if (o.key().equals("autotpa") && !PlayerSettings.OPTIONS.get("team.invites").shownIn(mode)) {
            choices = choices.subList(0, 2);
        }
        for (int i = 0; i < choices.size(); i++) {
            names.add(en ? choices.get(i).en() : choices.get(i).de());
            if (choices.get(i).id().equals(cur)) index = i;
        }
        return new Tile(o.key(), o.name(en), o.desc(en), on, names.get(index), names, index, warning);
    }

    static String language(String code, boolean en) {
        for (Choice c : PlayerSettings.LANGUAGES) if (c.id().equals(code)) return en ? c.en() : c.de();
        return "Deutsch";
    }

    /** Ohne Grafik-Pack: Schalter als Chat-Zeilen zum Anklicken. */
    private void openChat(Player p, Tab tab, String mode, boolean en) {
        View v = view(p.getUniqueId(), tab, mode);
        StringBuilder tabs = new StringBuilder();
        for (Tab t : Tab.values()) {
            Look l = Look.values()[t.ordinal()];
            tabs.append(t == tab ? "<white><b>" + l.label(en) + "</b></white>"
                    : "<click:run_command:'/settings " + t.name().toLowerCase() + "'><gray>" + l.label(en) + "</gray></click>").append("  ");
        }
        Msg.send(p, "<#B7A6FC><b>" + (en ? "SETTINGS" : "EINSTELLUNGEN") + "</b> <dark_gray>▶ " + tabs);
        for (Tile t : v.tiles()) {
            String state = t.choice() != null ? t.choice() : t.on() ? (en ? "ON" : "AN") : (en ? "OFF" : "AUS");
            Msg.send(p, "<dark_gray>• <click:run_command:'/settings " + t.key() + "'><hover:show_text:'<gray>"
                    + Msg.escape(t.desc()) + "'>" + (t.on() ? "<green>" : "<red>") + Msg.escape(t.label()) + ": "
                    + Msg.escape(state) + "</hover></click>");
        }
    }

    // =================================================================================================================
    // Sprache
    // =================================================================================================================

    public void openLanguage(Player p, Tab back) {
        UUID id = p.getUniqueId();
        PlayerSettings s = plugin.playerSettings();
        boolean en = s.english(id);
        List<String> names = new ArrayList<>();
        int current = 0;
        for (int i = 0; i < PlayerSettings.LANGUAGES.size(); i++) {
            Choice c = PlayerSettings.LANGUAGES.get(i);
            names.add(c.de());
            if (c.id().equals(s.choice(id, "lang"))) current = i;
        }
        UiDialog.show(p, languageCanvas(names, current, en, i -> pl -> {
            s.setChoice(pl.getUniqueId(), "lang", PlayerSettings.LANGUAGES.get(i).id());
            Sfx.play(plugin, pl, Sfx.Kind.CLICK);
            openLanguage(pl, back);
        }, pl -> {
            Sfx.play(plugin, pl, Sfx.Kind.CLICK);
            open(pl, back);
        }), HEIGHT);
    }

    // =================================================================================================================
    // Zeichnen
    // =================================================================================================================

    /** Text mittig um cx (bei ungerader Breite 1 px weiter links, wie in der Referenz). */
    private static void center(Canvas c, int cx, int y, String text, TextColor color, boolean bold) {
        int w = Canvas.width(Canvas.clean(text), bold);
        c.text(cx - (w + 1) / 2, y, text, color, bold);
    }

    private static int iconX(int cx, KitData.SmallIcon icon) {
        return cx - (icon.advance - 1) / 2;
    }

    private static void head(Canvas c, String title, String sub, List<Span> right) {
        c.box(0, 0, W, 51, KitData.Box.SET_HEAD);
        c.icon(10, 9, KitData.Icon.SET_GEAR_BIG, true);
        c.text(50, 16, title, TITLE, true);
        c.text(50, 29, sub, SUB, false);
        if (right != null) c.textRight(380, 22, right);
    }

    /** Nur Darstellung (auch für Vorschauen ohne Server). */
    public static Canvas canvas(View v, Actions a) {
        boolean en = v.english();
        Canvas c = new Canvas(W);
        head(c, en ? "SETTINGS" : "EINSTELLUNGEN", en ? "One click switches instantly" : "Ein Klick schaltet sofort um",
                List.of(Span.bold(String.valueOf(v.on()), COUNT), Span.of("/" + v.total(), SUB)));
        c.smallIcon(277, 18, KitData.SmallIcon.SET_CHECK);
        c.text(298, 17, en ? "Switches" : "Schalter", SUB, false);
        c.text(298, 27, en ? "on" : "an", SUB, false);

        // Reiter
        for (Tab t : Tab.values()) {
            Look l = Look.values()[t.ordinal()];
            int x = t.ordinal() * TAB_STEP, cx = x + 37;
            boolean active = t == v.tab();
            c.box(x, TAB_Y, TAB_W, TAB_H, active ? l.filled : KitData.Box.SET_GRAY);
            c.smallIcon(iconX(cx, l.icon), 57 + (16 - l.icon.height) / 2, l.icon);
            // aktiv fett, wenn es in den Reiter passt (sonst normal, wie "Mitteilungen" in der Referenz)
            boolean bold = active && Canvas.width(l.label(en), true) <= 64;
            center(c, cx, 75, l.label(en), active ? DARK : l.color, bold);
            if (!active && a != null) c.action(x, TAB_Y, TAB_W, TAB_H, a.tab(t), tabTip(l, en));
        }

        // Inhalt
        Look look = Look.values()[v.tab().ordinal()];
        int rows = Math.max(1, (v.tiles().size() + 1) / 2);
        int boxH = 33 + rows * 27;
        c.box(0, BOX_Y, W, boxH, look.box);
        c.smallIcon(9 + (16 - (look.section.advance - 1)) / 2, 97 + (16 - look.section.height) / 2, look.section);
        c.text(30, 101, Canvas.caps(look.label(en)), look.color, true);
        c.textRight(380, 101, en ? "Hover for details" : "Fahr drüber für Details", SUB, false);
        for (int i = 0; i < v.tiles().size(); i++) {
            Tile t = v.tiles().get(i);
            int x = i % 2 == 0 ? 6 : 197, y = 117 + 27 * (i / 2);
            c.box(x, y, TILE_W, TILE_H, t.on() ? look.box : KitData.Box.SET_GRAY, 1);
            // Mehrfach-Schalter: 64 breit wie in der Referenz, bei langen Stellungen breiter (rechter Rand bleibt)
            int pillW = t.choice() == null ? 0 : Math.min(120, Math.max(64, Canvas.width(t.choice(), false) + 16));
            int labelMax = t.choice() != null ? 172 - pillW - 8 : 128;
            c.text(x + 8, y + 8, Canvas.fit(t.label(), false, labelMax), t.on() ? WHITE : OFF, false);
            if (t.choice() == null) {
                KitData.SmallIcon left = t.on() ? (en ? look.onEnL : look.onL) : (en ? KitData.SmallIcon.SET_OFF_EN_L : KitData.SmallIcon.SET_OFF_L);
                KitData.SmallIcon right = t.on() ? (en ? look.onEnR : look.onR) : (en ? KitData.SmallIcon.SET_OFF_EN_R : KitData.SmallIcon.SET_OFF_R);
                c.smallIcon(x + 140, y + 5, left);
                c.smallIcon(x + 160, y + 5, right);
            } else {
                String label = Canvas.fit(t.choice(), false, pillW - 8);
                int px = x + 180 - pillW;
                c.smallButton(px, y + 4, pillW, t.on() ? look.pill : KitData.Small.SET_GRAY);
                center(c, px + pillW / 2, y + 8, label, t.on() ? DARK : OFF, false);
            }
            if (a != null) c.action(x, y, TILE_W, TILE_H, a.click(t.key()), tip(t, en));
        }

        // unten
        int by = BOX_Y + boxH + 12;
        bottom(c, 0, by, "Sprache · Language", en ? "Open own page" : "Eigene Seite öffnen",
                a == null ? null : a.language(), languageTip(v, en));
        String tr = en ? "Auto-translate · " + (v.translate() ? "On" : "Off") : "Auto-Übersetzen · " + (v.translate() ? "An" : "Aus");
        bottom(c, 198, by, tr, en ? "Click: toggle" : "Klick: umschalten", a == null ? null : a.translate(), translateTip(v, tr, en));
        return c;
    }

    private static void bottom(Canvas c, int x, int y, String title, String sub, Consumer<Player> click, Component tip) {
        c.box(x, y, 192, 27, KitData.Box.SET_BUTTON);
        c.smallIcon(x + 10, y + 5, KitData.SmallIcon.SET_GEAR_BUTTON);
        c.text(x + 34, y + 4, Canvas.fit(title, true, 140), NAV, true);
        c.text(x + 34, y + 14, Canvas.fit(sub, false, 140), SUB, false);
        c.text(x + 178, y + 10, "›", NAV, true);
        if (click != null) c.action(x, y, 192, 27, click, tip);
    }

    /** Sprachseite: Kopf, eine Kachel je Sprache und zurück zu den Einstellungen. */
    public static Canvas languageCanvas(List<String> languages, int current, boolean en,
                                        java.util.function.IntFunction<Consumer<Player>> pick, Consumer<Player> back) {
        Canvas c = new Canvas(W);
        head(c, "SPRACHE · LANGUAGE", en ? "Menus and messages" : "Menüs und Meldungen", null);
        Look look = Look.OTHER;
        int rows = Math.max(1, (languages.size() + 1) / 2);
        int boxH = 33 + rows * 27;
        c.box(0, 54, W, boxH, look.box);
        c.smallIcon(9, 61, KitData.SmallIcon.SET_GEAR);
        c.text(30, 65, Canvas.caps(en ? "Language" : "Sprache"), look.color, true);
        c.textRight(380, 65, en ? "Click to choose" : "Klick zum Wählen", SUB, false);
        for (int i = 0; i < languages.size(); i++) {
            int x = i % 2 == 0 ? 6 : 197, y = 81 + 27 * (i / 2);
            boolean on = i == current;
            c.box(x, y, TILE_W, TILE_H, on ? look.box : KitData.Box.SET_GRAY, 1);
            c.text(x + 8, y + 8, languages.get(i), on ? WHITE : OFF, on);
            c.smallIcon(x + 140, y + 5, on ? (en ? look.onEnL : look.onL) : (en ? KitData.SmallIcon.SET_OFF_EN_L : KitData.SmallIcon.SET_OFF_L));
            c.smallIcon(x + 160, y + 5, on ? (en ? look.onEnR : look.onR) : (en ? KitData.SmallIcon.SET_OFF_EN_R : KitData.SmallIcon.SET_OFF_R));
            if (pick != null && !on) c.action(x, y, TILE_W, TILE_H, pick.apply(i), Component.text(languages.get(i), WHITE)
                    .append(Component.newline()).append(Component.text(en ? "Click: choose" : "Klick: wählen", TIP_CLICK)));
        }
        int by = 54 + boxH + 12;
        bottom(c, 0, by, en ? "Back" : "Zurück", en ? "To the settings" : "Zu den Einstellungen", back,
                Component.text(en ? "Back to the settings" : "Zurück zu den Einstellungen", WHITE));
        return c;
    }

    // =================================================================================================================
    // Tooltips
    // =================================================================================================================

    private static Component tabTip(Look l, boolean en) {
        return Component.text(l.label(en), l.color).append(Component.newline())
                .append(Component.text(en ? "Click: open" : "Klick: öffnen", TIP_CLICK));
    }

    private static void desc(TextComponent.Builder b, String text) {
        for (String line : Canvas.wrap(text, 200)) b.append(Component.newline()).append(Component.text(line, TIP_TEXT));
    }

    static Component tip(Tile t, boolean en) {
        TextComponent.Builder b = Component.text();
        String title = t.choice() == null ? t.label() : t.label() + (en ? " display" : "-Anzeige");
        if (t.key().equals("autotpa")) title = t.label();
        b.append(Component.text(title, WHITE).decorate(TextDecoration.BOLD));
        desc(b, t.desc());
        if (t.choice() != null) {
            b.append(Component.newline());
            for (int i = 0; i < t.choices().size(); i++) {
                boolean cur = i == t.current();
                b.append(Component.newline())
                        .append(Component.text(cur ? "▸ " : "• ", cur ? TIP_MARK : TIP_BULLET))
                        .append(Component.text(t.choices().get(i), cur ? WHITE : TIP_OPTION));
            }
        } else {
            b.append(Component.newline()).append(Component.text(en ? "Status: " : "Status: ", TIP_OPTION))
                    .append(Component.text(t.on() ? (en ? "on" : "an") : (en ? "off" : "aus"), t.on() ? TextColor.color(0x6DE288) : TIP_WARN));
        }
        if (t.warning() != null) b.append(Component.newline()).append(Component.text(t.warning(), TIP_WARN));
        b.append(Component.newline()).append(Component.newline());
        b.append(Component.text(t.choice() != null ? (en ? "Click: next setting" : "Klick: nächste Stellung")
                : (en ? "Click: toggle" : "Klick: umschalten"), TIP_CLICK));
        return b.build();
    }

    private static Component languageTip(View v, boolean en) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text("Sprache · Language", WHITE));
        desc(b, en ? "Current: " + v.language() + ". Two languages to choose from."
                : "Aktuell: " + v.language() + ". Zwei Sprachen zur Wahl.");
        b.append(Component.newline()).append(Component.newline())
                .append(Component.text(en ? "Click: open" : "Klick: öffnen", TIP_CLICK));
        return b.build();
    }

    private static Component translateTip(View v, String title, boolean en) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(title, WHITE));
        desc(b, en ? "Translates messages from other players." : "Übersetzt Nachrichten anderer Spieler.");
        if (!v.translateReady()) {
            b.append(Component.newline()).append(Component.text(en ? "Not set up on this server." : "Auf diesem Server nicht eingerichtet.", TIP_WARN));
        }
        b.append(Component.newline()).append(Component.newline())
                .append(Component.text("▸ ", TIP_MARK))
                .append(Component.text(en ? "Click to toggle" : "Klicke zum Umschalten", TIP_CLICK));
        return b.build();
    }
}
