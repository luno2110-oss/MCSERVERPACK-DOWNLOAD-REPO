package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.AchievementService;
import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.service.TagService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Drei Dialoge rund um Fortschritt, jeder mit eigener Optik:
 * Erfolge (/erfolge) als Trophäenschrank in Bronze (Karten mit Balken, erreichte golden), Titel (/tags) als
 * Namensschilder in Pflaume (aktiver Titel hervorgehoben, gesperrte grau) und Kits (/kits) als Waffenkammer in Stahl
 * (eine Zeile je Kit mit Abholen und Vorschau). Ohne Pack: AchievementMenu, TagsMenu, KitMenu.
 */
public final class ProgressScreens {

    static final int W = 390;
    static final TextColor BRONZE = TextColor.color(0xF0B868), CREAM = TextColor.color(0xF4E8D4), TAN = TextColor.color(0xB09878),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xFF7A7A),
            JEWEL = TextColor.color(0x5FE3A0), WHITE = TextColor.color(0xF4F4F4), DIM = TextColor.color(0x6E6458),
            PLUM = TextColor.color(0xF59AD4), ROSE = TextColor.color(0xC08AB0), STEEL = TextColor.color(0xC8D4E0),
            SLATE = TextColor.color(0x8C98A6), DARK = TextColor.color(0x101418), LOCKED = TextColor.color(0x606060);

    // =================================================================================================================
    // Erfolge
    // =================================================================================================================

    /** Erfolg: Name, Text, Icon, Stand, Ziel, Juwelen, erreicht. */
    public record Ach(String name, String desc, KitData.Icon icon, long have, long target, long jewels, boolean done) {}

    public record Achievements(int done, int total, List<Ach> page, int pageNo, int pages, IntFunction<Consumer<Player>> goTo) {}

    // =================================================================================================================
    // Titel
    // =================================================================================================================

    /** Titel: Text ohne Formatierung, Farbe, freigeschaltet, aktiv, Klick. */
    public record Tag(String text, TextColor color, boolean owned, boolean active, Consumer<Player> click) {}

    public record Tags(String current, List<Tag> tags, Consumer<Player> none) {}

    // =================================================================================================================
    // Kits
    // =================================================================================================================

    /** Kit: Name, Icon, Inhalt kurz, Abklingzeit, Rang (null = keiner), Rang erfüllt, Status, abholbar, Klicks. */
    public record KitRow(String name, KitData.Icon icon, String content, String cooldown, String rank, boolean rankOk,
                         String state, boolean ready, Consumer<Player> claim, Consumer<Player> preview) {}

    private final KubyPlugins plugin;

    public ProgressScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // ---- Steuerung ----

    public void achievements(Player p, int page) {
        if (!plugin.uiPack().active(p)) {
            new AchievementMenu(plugin, page).open(p);
            return;
        }
        AchievementService svc = plugin.achievements();
        svc.check(p);
        UUID id = p.getUniqueId();
        List<AchievementService.Achievement> all = svc.all();
        int per = 10, pages = Math.max(1, (all.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<Ach> list = new ArrayList<>();
        for (int i = pg * per; i < Math.min(all.size(), pg * per + per); i++) {
            AchievementService.Achievement a = all.get(i);
            list.add(new Ach(a.name(), a.desc(), Icons.of(a.icon(), KitData.Icon.STAR), Math.min(svc.progress(id, a), a.target()),
                    a.target(), a.jewels(), svc.isDone(id, a.id())));
        }
        UiDialog.show(p, achievements(new Achievements(svc.doneCount(id), all.size(), list, pg, pages,
                n -> pl -> achievements(pl, n))), 330);
    }

    /** Farbe und Text eines Titels (MiniMessage) für die Anzeige. */
    static Tag tagOf(String display, boolean owned, boolean active, Consumer<Player> click) {
        Component comp = Msg.parse(display);
        String text = PlainTextComponentSerializer.plainText().serialize(comp).trim();
        TextColor color = firstColor(comp);
        return new Tag(text.isEmpty() ? "?" : text, color == null ? PLUM : color, owned, active, click);
    }

    private static TextColor firstColor(Component c) {
        if (c.color() != null) return c.color();
        for (Component child : c.children()) {
            TextColor t = firstColor(child);
            if (t != null) return t;
        }
        return null;
    }

    public void tags(Player p) {
        if (!plugin.uiPack().active(p)) {
            new TagsMenu(plugin).open(p);
            return;
        }
        UUID id = p.getUniqueId();
        TagService ts = plugin.tags();
        String current = ts.activeId(id);
        List<Tag> list = new ArrayList<>();
        for (TagService.Tag t : ts.all()) {
            boolean owned = ts.has(id, t.id());
            list.add(tagOf(t.display(), owned, t.id().equals(current), !owned ? null : pl -> {
                String err = ts.setActive(pl.getUniqueId(), t.id());
                Sfx.play(plugin, pl, err == null ? Sfx.Kind.PING : Sfx.Kind.ERROR);
                Msg.send(pl, err == null ? "<green>Titel aktiviert." : "<red>" + err);
                tags(pl);
            }));
        }
        String cur = current == null ? null : PlainTextComponentSerializer.plainText().serialize(Msg.parse(ts.displayOf(current))).trim();
        UiDialog.show(p, tags(new Tags(cur, list, pl -> {
            ts.setActive(pl.getUniqueId(), null);
            Msg.send(pl, "<gray>Titel abgelegt.");
            tags(pl);
        })), 300);
    }

    public void kits(Player p) {
        if (!plugin.uiPack().active(p)) {
            new KitMenu(plugin).open(p);
            return;
        }
        KitService ks = plugin.kits();
        List<KitRow> rows = new ArrayList<>();
        for (KitService.Kit kit : ks.kits()) {
            long left = ks.remaining(p.getUniqueId(), kit);
            boolean rankOk = kit.rank().isEmpty() || ks.allowed(p, kit);
            boolean empty = kit.items().isEmpty();
            String state = empty ? "leer" : !rankOk ? "Rang fehlt" : left > 0 ? "wieder in " + KitService.format(left) : "✔ bereit";
            boolean ready = !empty && rankOk && left <= 0;
            rows.add(new KitRow(kit.name(), Icons.of(kit.icon(), KitData.Icon.IRON_CHESTPLATE), KitService.describe(kit),
                    kit.cooldownMinutes() >= 60 ? (kit.cooldownMinutes() / 60) + " Std." : kit.cooldownMinutes() + " Min.",
                    kit.rank().isEmpty() ? null : KitService.rankName(kit.rank()), rankOk, state, ready, pl -> {
                String err = ks.claim(pl, kit);
                Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                Msg.send(pl, err == null ? "<green>" + kit.name() + " abgeholt!" : "<red>" + err);
                kits(pl);
            }, pl -> {
                pl.closeDialog();
                new KitPreviewMenu(plugin, kit.id()).open(pl);
            }));
        }
        UiDialog.show(p, kits(rows), 300);
    }

    // ---- Darstellung ----

    private static void head(Canvas c, KitData.Box box, KitData.Icon icon, String title, TextColor color, String sub, TextColor subColor) {
        c.box(0, 0, W, 54, box);
        c.icon(10, 11, icon, true);
        c.title(50, 12, title, false, color);
        c.text(50, 36, sub, subColor, false);
    }

    public static Canvas achievements(Achievements v) {
        Canvas c = new Canvas(W).heading("Erfolge", BRONZE);
        head(c, KitData.Box.BRONZE, KitData.Icon.STAR, "Erfolge", BRONZE, "Belohnungen gibt es automatisch", TAN);
        c.textRight(378, 12, v.done() + " / " + v.total(), CREAM, true);
        c.textRight(378, 24, Canvas.caps("erreicht"), TAN, false);
        c.bar(298, 36, 80, v.total() == 0 ? 0 : v.done() / (double) v.total(), KitData.Fill.GOLD);
        int y = 60, cw = (W - 4) / 2, ch = 56;
        for (int i = 0; i < v.page().size(); i++) {
            Ach a = v.page().get(i);
            int x = (i % 2) * (cw + 4), ay = y + (i / 2) * (ch + 4);
            c.box(x, ay, cw, ch, a.done() ? KitData.Box.GOLD : KitData.Box.BRONZE_CARD);
            c.icon(x + 7, ay + 8, a.icon(), false, a.done() ? net.kyori.adventure.text.format.NamedTextColor.WHITE : LOCKED);
            c.text(x + 28, ay + 8, Canvas.fit(a.name(), true, cw - 28 - 8), a.done() ? GOLD : CREAM, true);
            c.text(x + 28, ay + 19, Canvas.fit(a.desc(), false, cw - 28 - 8), TAN, false);
            double f = a.target() <= 0 ? 1 : a.have() / (double) a.target();
            c.bar(x + 8, ay + 32, cw - 16 - 60, f, a.done() ? KitData.Fill.GREEN : KitData.Fill.GOLD);
            c.textRight(x + cw - 8, ay + 32, a.done() ? "✔" : Msg.coins(a.have()) + "/" + Msg.coins(a.target()), a.done() ? GREEN : CREAM, false);
            if (a.jewels() > 0) c.text(x + 8, ay + 42, "+" + a.jewels() + " Juwelen", a.done() ? DIM : JEWEL, false);
        }
        y += ((v.page().size() + 1) / 2) * (ch + 4);
        if (v.pages() > 1) {
            String label = "Seite " + (v.pageNo() + 1) + " / " + v.pages();
            c.textCenter(W / 2, y + 4, label, TAN, false);
            if (v.pageNo() > 0) {
                c.smallButton(W / 2 - 90, y, 40, KitData.Small.DARK, "‹", CREAM, true);
                c.action(W / 2 - 90, y, 40, KitData.SMALL_H, v.goTo().apply(v.pageNo() - 1), "Vorige Seite");
            }
            if (v.pageNo() + 1 < v.pages()) {
                c.smallButton(W / 2 + 50, y, 40, KitData.Small.DARK, "›", CREAM, true);
                c.action(W / 2 + 50, y, 40, KitData.SMALL_H, v.goTo().apply(v.pageNo() + 1), "Nächste Seite");
            }
        }
        return c;
    }

    public static Canvas tags(Tags v) {
        Canvas c = new Canvas(W).heading("Titel", PLUM);
        head(c, KitData.Box.PLUM, KitData.Icon.NAME_TAG, "Titel", PLUM, "Steht neben deinem Namen im Chat und in der Tab-Liste", ROSE);
        int y = 60;
        // aktueller Titel und „ablegen“
        c.box(0, y, W, 30, KitData.Box.PLUM_CARD);
        c.text(10, y + 11, Canvas.caps("Aktiv:"), ROSE, false);
        c.text(52, y + 11, v.current() == null ? "kein Titel" : Canvas.fit(Canvas.lit(v.current()), true, 220),
                v.current() == null ? DIM : CREAM, v.current() != null);
        if (v.current() != null) {
            c.smallButton(W - 76, y + 7, 68, KitData.Small.DARK, "Ablegen", CREAM, false);
            c.action(W - 76, y + 7, 68, KitData.SMALL_H, v.none(), "Titel ablegen");
        }
        y += 30 + 8;
        int cols = 3, cw = (W - 6) / cols, ch = 34;
        for (int i = 0; i < v.tags().size(); i++) {
            Tag t = v.tags().get(i);
            int x = (i % cols) * (cw + 3), ty = y + (i / cols) * (ch + 3);
            c.box(x, ty, cw, ch, t.active() ? KitData.Box.GOLD : t.owned() ? KitData.Box.PLUM_CARD : KitData.Box.GRAY);
            c.icon(x + 6, ty + 9, t.owned() ? KitData.Icon.NAME_TAG : KitData.Icon.BARRIER, false,
                    t.owned() ? net.kyori.adventure.text.format.NamedTextColor.WHITE : LOCKED);
            c.text(x + 26, ty + 7, Canvas.fit(Canvas.lit(t.text()), true, cw - 26 - 6), t.owned() ? t.color() : LOCKED, true);
            c.text(x + 26, ty + 19, t.active() ? "✔ aktiv" : t.owned() ? "Klick: tragen" : "gesperrt", t.active() ? GREEN : t.owned() ? ROSE : DIM, false);
            if (t.click() != null && !t.active()) c.action(x, ty, cw, ch, t.click(), "Titel tragen");
        }
        if (v.tags().isEmpty()) c.text(4, y, "Noch keine Titel. Titel gibt es für Ligen, Erfolge und Events.", ROSE, false);
        return c;
    }

    public static Canvas kits(List<KitRow> rows) {
        Canvas c = new Canvas(W).heading("Kits", STEEL);
        head(c, KitData.Box.STEEL, KitData.Icon.IRON_CHESTPLATE, "Kits", STEEL, "Ausrüstung abholen · Vorschau zeigt den Inhalt", SLATE);
        int y = 60;
        for (KitRow k : rows) {
            c.box(0, y, W, 46, k.ready() ? KitData.Box.STEEL : KitData.Box.STEEL_CARD);
            c.icon(8, y + 7, k.icon(), true);
            c.text(48, y + 8, Canvas.fit(k.name(), true, 180), WHITE, true);
            if (k.rank() != null) c.text(48 + Canvas.width(Canvas.fit(k.name(), true, 180), true) + 8, y + 8,
                    "ab " + k.rank(), k.rankOk() ? GREEN : RED, false);
            c.text(48, y + 20, Canvas.fit(k.content(), false, 220), SLATE, false);
            c.text(48, y + 31, "alle " + k.cooldown() + " · " + k.state(), k.ready() ? GREEN : SLATE, false);
            int bx = W - 8 - 70;
            c.smallButton(bx, y + 6, 70, k.ready() ? KitData.Small.GREEN : KitData.Small.DARK, "Abholen", k.ready() ? WHITE : SLATE, false);
            if (k.ready()) c.action(bx, y + 6, 70, KitData.SMALL_H, k.claim(), "Kit abholen");
            c.smallButton(bx, y + 24, 70, KitData.Small.DARK, "Vorschau", STEEL, false);
            c.action(bx, y + 24, 70, KitData.SMALL_H, k.preview(), "Inhalt ansehen");
            y += 46 + 4;
        }
        if (rows.isEmpty()) c.text(4, y, "Gerade gibt es keine Kits.", SLATE, false);
        return c;
    }
}
