package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guide (SMP-Hilfe) auf dem Dialog-Kit wie in der Referenz: Kopf mit Buch, „GUIDE“, Seitenname und „Seite n/12“,
 * links die Liste aller Seiten (aktive grün), rechts der Text der Seite. Inhalt aus der config.yml (guide.pages):
 * <ul>
 *   <li>{@code # Überschrift} (gelb, fett), {@code > Punkt} (grauer Pfeil), leere Zeile, sonst Absatz (bricht um)</li>
 *   <li>{@code > /befehl <Arg> - Text}: Befehl gelb, Strich dunkelgrau, Text grau</li>
 *   <li>im Text: {@code `/befehl`} gelb, {@code [Text](seite)} Link auf eine Seite, {@code [Text](discord)} oder
 *       {@code [Text](https://…)} Link nach außen, {@code {discord}} = Discord-Link ohne https://</li>
 * </ul>
 * Ohne Grafik-Pack kommt die Seite als Text in den Chat.
 */
public final class GuideScreen {

    static final int W = 390, HEIGHT = 280, MAX_PAGES = 12;
    /** Textbereich rechts: linker Rand, rechter Rand, erste Zeile, letzte mögliche Zeile. */
    static final int TX = 144, TR = 381, TY = 81, TY_MAX = 262, LINE = 9;

    private static final TextColor GREEN = TextColor.color(0x6DE288), SUB = TextColor.color(0x9FA1A7),
            DARK = TextColor.color(0x1C122D), NAV = TextColor.color(0xD1D2D5), BODY = TextColor.color(0xA8A8A8),
            YELLOW = TextColor.color(0xFCFC54), DIM = TextColor.color(0x545454), AQUA = TextColor.color(0x54FCFC),
            WHITE = TextColor.color(0xFCFCFC), TIP_CLICK = TextColor.color(0xFCFC54);
    /** Lage der Navigations-Icons nav00 … nav11 im Knopf (aus der Referenz). */
    private static final int[][] ICON_POS = {{7, 5}, {6, 5}, {7, 6}, {7, 5}, {7, 6}, {6, 5}, {6, 5}, {6, 5}, {6, 5},
            {7, 5}, {6, 6}, {7, 5}};

    /** Eine Seite: Kennung, Name in der Liste, Icon (nav00 … nav11), Zeilen. */
    public record Page(String id, String name, String icon, List<String> lines) {}

    /** Ein Stück Text: Farbe, fett, Link (Seiten-ID oder URL, unterstrichen), Befehl (Tooltip). */
    record Seg(String text, TextColor color, boolean bold, String link, String command) {
        static Seg of(String text, TextColor color) {
            return new Seg(text, color, false, null, null);
        }
    }

    public interface Actions {
        Consumer<Player> page(int index);

        /** Link nach außen (Discord); leer = keiner. */
        String url(String target);
    }

    private final KubyPlugins plugin;

    public GuideScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // =================================================================================================================
    // Inhalt
    // =================================================================================================================

    /** Seiten aus der config.yml (höchstens zwölf, englisch mit name-en und lines-en, wenn vorhanden). */
    public List<Page> pages(boolean en) {
        List<Page> out = new ArrayList<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("guide.pages");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ConfigurationSection p = s.getConfigurationSection(id);
            if (p == null || out.size() >= MAX_PAGES) continue;
            String name = en && p.isString("name-en") ? p.getString("name-en") : p.getString("name", id);
            List<String> lines = en && p.isList("lines-en") ? p.getStringList("lines-en") : p.getStringList("lines");
            out.add(new Page(id, name, p.getString("icon", "nav00"), lines));
        }
        return out;
    }

    /** Index der Seite mit dieser Kennung oder diesem Namen (Anfang genügt, Umlaute egal), sonst -1. */
    public static int find(List<Page> pages, String key) {
        if (key == null || key.isBlank()) return 0;
        try {
            int n = Integer.parseInt(key.trim());
            return n >= 1 && n <= pages.size() ? n - 1 : -1;
        } catch (NumberFormatException ignored) {
            // Name oder Kennung
        }
        String k = norm(key);
        for (int i = 0; i < pages.size(); i++) {
            if (norm(pages.get(i).id()).equals(k)) return i;
        }
        for (int i = 0; i < pages.size(); i++) {
            if (norm(pages.get(i).name()).startsWith(k)) return i;
        }
        return -1;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[^a-z0-9]", "");
    }

    private String discord() {
        String g = plugin.getConfig().getString("guide.discord", "");
        return g == null || g.isBlank() ? plugin.getConfig().getString("discord.invite", "") : g;
    }

    /** Discord-Link ohne https:// (für {discord}). */
    static String shortUrl(String url) {
        return url == null ? "" : url.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /** Öffnet die Seite (Nummer, Kennung oder Name; null = erste). False, wenn es die Seite nicht gibt. */
    public boolean open(Player p, String key) {
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        List<Page> pages = pages(en);
        int i = find(pages, key);
        if (i < 0 || pages.isEmpty()) return false;
        open(p, i);
        return true;
    }

    public void open(Player p, int index) {
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        List<Page> pages = pages(en);
        if (pages.isEmpty()) return;
        int i = Math.floorMod(index, pages.size());
        if (!plugin.uiPack().active(p)) {
            chat(p, pages, i, en);
            return;
        }
        UiDialog.show(p, canvas(pages, i, en, discord(), actions()), HEIGHT);
    }

    private Actions actions() {
        return new Actions() {
            public Consumer<Player> page(int index) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    open(pl, index);
                };
            }

            public String url(String target) {
                return "discord".equalsIgnoreCase(target) ? discord() : target;
            }
        };
    }

    /** Ohne Grafik-Pack: Seite als Text im Chat, darunter die Liste zum Weiterklicken. */
    private void chat(Player p, List<Page> pages, int i, boolean en) {
        Page page = pages.get(i);
        p.sendMessage(Msg.parse("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        p.sendMessage(Msg.parse("<#6DE288><b>Guide</b> <dark_gray>» <white>" + Msg.escape(page.name()) + " <gray>("
                + (i + 1) + "/" + pages.size() + ")"));
        for (String raw : page.lines()) {
            TextComponent.Builder b = Component.text();
            for (Seg s : parse(raw, discord())) {
                Component c = Component.text(s.text(), s.color());
                if (s.bold()) c = c.decorate(TextDecoration.BOLD);
                if (s.link() != null) {
                    c = c.decorate(TextDecoration.UNDERLINED);
                    int target = find(pages, s.link());
                    if (target >= 0) c = c.clickEvent(ClickEvent.runCommand("/guide " + (target + 1)));
                    else if (!actions().url(s.link()).isBlank()) c = c.clickEvent(ClickEvent.openUrl(actions().url(s.link())));
                }
                if (s.command() != null) c = c.clickEvent(ClickEvent.suggestCommand(commandOf(s.command())));
                b.append(c);
            }
            p.sendMessage(b.build());
        }
        TextComponent.Builder nav = Component.text();
        for (int k = 0; k < pages.size(); k++) {
            if (k > 0) nav.append(Component.text(" · ", TextColor.color(0x545454)));
            nav.append(Component.text(pages.get(k).name(), k == i ? GREEN : SUB)
                    .clickEvent(ClickEvent.runCommand("/guide " + (k + 1))));
        }
        p.sendMessage(nav.build());
    }

    /** Befehl ohne Platzhalter, zum Eintippen: "/sethome <Name>" -> "/sethome ". */
    static String commandOf(String shown) {
        int cut = shown.length();
        for (char c : new char[]{'<', '['}) {
            int i = shown.indexOf(c);
            if (i >= 0) cut = Math.min(cut, i);
        }
        String s = shown.substring(0, cut);
        return s.endsWith(" ") ? s : cut < shown.length() ? s + " " : s;
    }

    // =================================================================================================================
    // Text zerlegen
    // =================================================================================================================

    private static final Pattern INLINE = Pattern.compile("`([^`]+)`|\\[([^\\]]+)\\]\\(([^)]+)\\)| - ");

    /** Eine Zeile in Stücke: Überschrift, Punkt oder Absatz. */
    static List<Seg> parse(String raw, String discord) {
        String line = raw.replace("{discord}", shortUrl(discord));
        List<Seg> out = new ArrayList<>();
        if (line.startsWith("# ")) {
            out.add(new Seg(line.substring(2), YELLOW, true, null, null));
            return out;
        }
        if (line.startsWith("> ")) {
            out.add(Seg.of(">", DIM));
            out.add(Seg.of(" ", BODY));
            String rest = line.substring(2);
            int dash = rest.indexOf(" - ");
            if (rest.startsWith("/") && dash > 0) {
                String cmd = rest.substring(0, dash);
                out.add(new Seg(cmd, YELLOW, false, null, cmd));
                rest = rest.substring(dash);
            }
            inline(rest, out);
            return out;
        }
        inline(line, out);
        return out;
    }

    private static void inline(String text, List<Seg> out) {
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.add(Seg.of(text.substring(last, m.start()), BODY));
            if (m.group(1) != null) {
                out.add(new Seg(m.group(1), YELLOW, false, null, m.group(1)));
            } else if (m.group(2) != null) {
                String target = m.group(3).trim();
                boolean external = target.equalsIgnoreCase("discord") || target.startsWith("http");
                out.add(new Seg(m.group(2), external ? AQUA : YELLOW, false, target, null));
            } else {
                out.add(Seg.of(" ", BODY));
                out.add(Seg.of("-", DIM));
                out.add(Seg.of(" ", BODY));
            }
            last = m.end();
        }
        if (last < text.length()) out.add(Seg.of(text.substring(last), BODY));
    }

    /** Ein gesetztes Stück: Position in der Zeile. */
    record Placed(Seg seg, int x, int line) {}

    /**
     * Setzt die Zeilen in den Textbereich: Wörter umbrechen bei der rechten Kante, Folgezeilen von Punkten unter dem
     * Text. Gibt die Stücke mit Position und die Zahl der belegten Zeilen zurück.
     */
    static List<Placed> layout(List<String> lines, String discord, int[] usedLines) {
        List<Placed> out = new ArrayList<>();
        int row = 0;
        for (String raw : lines) {
            List<Seg> segs = parse(raw, discord);
            if (segs.isEmpty()) {
                row++;
                continue;
            }
            int indent = raw.startsWith("> ") ? Canvas.width(">", false) + Canvas.width(" ", false) : 0;
            int x = TX;
            for (Seg s : segs) {
                // in Wörter (mit folgendem Leerzeichen) zerlegen, Links und Befehle bleiben am Stück
                List<String> words = new ArrayList<>();
                if (s.link() != null || s.command() != null) {
                    words.add(s.text());
                } else {
                    Matcher w = Pattern.compile("\\S+\\s*|\\s+").matcher(s.text());
                    while (w.find()) words.add(w.group());
                }
                for (String word : words) {
                    String ink = word.stripTrailing();
                    int ww = Canvas.width(ink, s.bold());
                    if (x > TX + indent && x + ww - 2 > TR && !ink.isEmpty()) {
                        row++;
                        x = TX + indent;
                    }
                    out.add(new Placed(new Seg(word, s.color(), s.bold(), s.link(), s.command()), x, row));
                    // fette Wörter, Abstände normal breit (wie in der Referenz)
                    x += Canvas.width(ink, s.bold()) + Canvas.width(word.substring(ink.length()), false);
                }
            }
            row++;
        }
        usedLines[0] = row;
        return out;
    }

    // =================================================================================================================
    // Zeichnen
    // =================================================================================================================

    /** Seitenname in Kapitälchen: Anfangsbuchstaben groß, Rest klein (Ü als großes Ü). */
    static String smallCaps(String name) {
        StringBuilder b = new StringBuilder();
        for (String word : name.split(" ", -1)) {
            if (!b.isEmpty()) b.append(' ');
            if (word.isEmpty()) continue;
            b.append(word.substring(0, 1).toUpperCase(Locale.GERMAN)).append(Canvas.caps(word.substring(1)));
        }
        return b.toString();
    }

    public static Canvas canvas(List<Page> pages, int index, boolean en, String discord, Actions a) {
        Page page = pages.get(index);
        Canvas c = new Canvas(W).heading("Guide", SUB);
        // Kopf
        c.box(0, 0, W, 51, KitData.Box.GD_HEAD);
        c.sprite(10, 10, KitData.Sprite.GD_BOOK);
        c.text(50, 16, "GUIDE", GREEN, true);
        c.text(50, 29, page.name(), SUB, false);
        c.textRight(380, 22, (en ? "Page " : "Seite ") + (index + 1) + "/" + pages.size(), SUB, false);
        // Liste
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            int y = 58 + 18 * i;
            boolean on = i == index;
            c.box(0, y, 127, 17, on ? KitData.Box.GD_NAV_ON : KitData.Box.GD_NAV);
            int icon = iconIndex(p.icon());
            c.sprite(ICON_POS[icon][0], y + ICON_POS[icon][1], NAV_ICONS[icon]);
            c.text(19, y + (on ? 6 : 5), Canvas.fit(p.name(), on, 104), on ? DARK : NAV, on);
            if (a != null && !on) c.action(0, y, 127, 17, a.page(i), Component.text(p.name(), WHITE));
        }
        // Inhalt
        c.box(134, 54, 256, 222, KitData.Box.GD_CONTENT);
        c.text(TX, 64, smallCaps(page.name()), GREEN, true);
        int[] used = new int[1];
        for (Placed pl : layout(page.lines(), discord, used)) {
            int y = TY + LINE * pl.line();
            if (y > TY_MAX) break;
            Seg s = pl.seg();
            int w = c.text(pl.x(), y, s.text(), s.color(), s.bold());
            if (s.link() != null) {
                int target = find(pages, s.link());
                c.underline(pl.x(), y, w, s.color());
                if (a != null) {
                    if (target >= 0) {
                        c.action(pl.x() - 1, y - 1, w + 1, LINE + 1, a.page(target),
                                tip(pages.get(target).name(), en ? "Click: open page" : "Klick: Seite öffnen"));
                    } else if (!a.url(s.link()).isBlank()) {
                        c.actionUrl(pl.x() - 1, y - 1, w + 1, LINE + 1, a.url(s.link()),
                                tip(shortUrl(a.url(s.link())), en ? "Click: open link" : "Klick: Link öffnen"));
                    }
                }
            } else if (s.command() != null && a != null) {
                c.action(pl.x(), y - 1, w, LINE + 1, pl2 -> { },
                        tip(s.command(), en ? "Type it in the chat" : "Im Chat eingeben"));
            }
        }
        return c;
    }

    /** Zeilen, die eine Seite belegt (Selbsttest: alles muss in den Kasten passen). */
    public static int usedLines(Page page, String discord) {
        int[] used = new int[1];
        layout(page.lines(), discord, used);
        return used[0];
    }

    public static int maxLines() {
        return (TY_MAX - TY) / LINE + 1;
    }

    private static final KitData.Sprite[] NAV_ICONS = {KitData.Sprite.GD_NAV00, KitData.Sprite.GD_NAV01,
            KitData.Sprite.GD_NAV02, KitData.Sprite.GD_NAV03, KitData.Sprite.GD_NAV04, KitData.Sprite.GD_NAV05,
            KitData.Sprite.GD_NAV06, KitData.Sprite.GD_NAV07, KitData.Sprite.GD_NAV08, KitData.Sprite.GD_NAV09,
            KitData.Sprite.GD_NAV10, KitData.Sprite.GD_NAV11};

    private static int iconIndex(String icon) {
        try {
            int i = Integer.parseInt(icon.replaceAll("[^0-9]", ""));
            return i >= 0 && i < NAV_ICONS.length ? i : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Component tip(String title, String click) {
        return Component.text().append(Component.text(title, WHITE).decorate(TextDecoration.BOLD))
                .append(Component.newline()).append(Component.text(click, TIP_CLICK)).build();
    }

    /** Seiten ohne Spieler (Selbsttest, Vorschau). */
    public List<Page> sample() {
        return pages(false);
    }

    public String discordLink() {
        return discord();
    }
}
