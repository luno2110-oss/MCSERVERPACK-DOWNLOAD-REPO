package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.IslandService.Island;
import de.kuby.kubyskyblock.service.IslandService.Upgrade;
import de.kuby.kubyskyblock.service.TemplateService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /insel als Dialog im Himmels-Look: Kopf mit Name, Level und Platz; Schnellknöpfe (Nach Hause, Zuhause hier, Besucher,
 * Top-Inseln); drei Upgrade-Karten (Größe, Mitglieder, Generator) mit Preis; Mitglieder mit Einladen und Entfernen;
 * Insel-Aktionen (Level zählen, Checkliste, Neu starten, Aufgeben). Dazu Top-Inseln, Wahl der Vorlage beim Start und
 * Rückfragen. Ohne Pack: IslandMenu (Truhe).
 */
public final class IslandScreen {

    static final int W = 390;
    static final TextColor SKY = TextColor.color(0x9AD8FF), CLOUD = TextColor.color(0xF2FAFF), MIST = TextColor.color(0x8AA8C0),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xF27A7A),
            DIM = TextColor.color(0x5E7488), DARK = TextColor.color(0x10202E);

    /** Upgrade-Karte: Name, Bild, jetzt, nächste Stufe (null = höchste), Preis, bezahlbar, Klick (null = keiner). */
    public record Up(String name, KitData.Sprite icon, String now, String next, long price, boolean affordable, Consumer<Player> buy) {}

    /** Mitglied: Name, Besitzer, online, Entfernen (null = nicht erlaubt). */
    public record Member(String name, boolean owner, boolean online, Consumer<Player> kick) {}

    public record View(String name, long level, int rank, long points, long nextLevel, boolean open, boolean isOwner, long coins,
                       List<Up> ups, List<Member> members, int limit, Consumer<Player> home, Consumer<Player> setHome,
                       Consumer<Player> toggle, Consumer<Player> top, Consumer<Player> invite, Consumer<Player> leave,
                       Consumer<Player> recalc, Consumer<Player> progress, Consumer<Player> reset, Consumer<Player> abandon,
                       String resetHint) {}

    public record TopRow(int rank, String name, String owner, long level, long points, boolean mine) {}

    public record Choice(String name, String desc, KitData.Icon icon, Consumer<Player> pick) {}

    private IslandScreen() {
    }

    private static KubySkyblock sb() {
        return KubySkyblock.get();
    }

    private static Consumer<Player> run(Consumer<Player> action) {
        return p -> {
            p.closeDialog();
            action.accept(p);
        };
    }

    private static void feedback(Player p, String err) {
        Sfx.play(sb().core(), p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /** Insel-Menü; ohne Insel die Wahl der Vorlage. */
    public static void open(Player p) {
        IslandService is = sb().islands();
        Island i = is.islandOf(p.getUniqueId());
        if (i == null) {
            choose(p);
            return;
        }
        if (!sb().core().uiPack().active(p)) {
            new IslandMenu(sb().core()).open(p);
            return;
        }
        UUID id = p.getUniqueId();
        boolean owner = id.equals(i.owner);
        long coins = sb().core().economy().balance(id);
        List<Up> ups = new ArrayList<>();
        for (Upgrade u : Upgrade.values()) {
            long price = is.price(i, u);
            String now, next;
            KitData.Sprite icon;
            String name;
            switch (u) {
                case SIZE -> {
                    name = "Größe";
                    icon = KitData.Sprite.HB_GROESSE;
                    int r = is.radius(i), rn = is.radius(i.size + 1);
                    now = (2 * r + 1) + " x " + (2 * r + 1);
                    next = price < 0 ? null : (2 * rn + 1) + " x " + (2 * rn + 1);
                }
                case SLOTS -> {
                    name = "Mitglieder";
                    icon = KitData.Sprite.HB_MITGLIEDER;
                    now = is.memberLimit(i) + " Plätze";
                    Island probe = i;
                    probe.slots++;
                    int nl = is.memberLimit(probe);
                    probe.slots--;
                    next = price < 0 ? null : nl + " Plätze";
                }
                default -> {
                    name = "Generator";
                    icon = KitData.Sprite.HB_GENERATOR;
                    now = "Stufe " + (i.generator + 1);
                    next = price < 0 ? null : "Stufe " + (i.generator + 2) + ": " + newOres(is, i.generator + 1);
                }
            }
            ups.add(new Up(name, icon, now, next, Math.max(0, price), price >= 0 && coins >= price, price < 0 ? null : run(pl -> {
                feedback(pl, is.upgrade(pl, u));
                open(pl);
            })));
        }
        List<Member> ms = new ArrayList<>();
        for (UUID m : i.everyone()) {
            boolean isOwner = m.equals(i.owner);
            ms.add(new Member(IslandService.nameOf(m), isOwner, Bukkit.getPlayer(m) != null, owner && !isOwner ? run(pl -> confirm(pl,
                    "Mitglied entfernen", IslandService.nameOf(m) + " von der Insel entfernen?", "Entfernen", q -> {
                        feedback(q, is.kick(q, m));
                        open(q);
                    })) : null));
        }
        long left = is.resetCooldownLeft(i);
        UiDialog.show(p, canvas(new View(i.name, is.levelOf(i), is.rank(i), i.points, (is.levelOf(i) + 1) * is.perLevel(), i.open, owner,
                coins, ups, ms, is.memberLimit(i) + 1,
                run(pl -> is.home(pl, i)),
                run(pl -> {
                    feedback(pl, is.setHome(pl));
                    open(pl);
                }),
                owner ? run(pl -> {
                    feedback(pl, is.toggleOpen(pl));
                    open(pl);
                }) : null,
                run(IslandScreen::top),
                owner ? run(IslandScreen::askInvite) : null,
                owner ? null : run(pl -> confirm(pl, "Insel verlassen", "Willst du " + i.name + " wirklich verlassen?", "Verlassen", q ->
                        feedback(q, is.leave(q)))),
                run(pl -> {
                    String err = is.recalc(i, pts -> {
                        Msg.send(pl, "<#9AD8FF>Insel-Level: <white>" + is.levelOf(i) + " <gray>(" + Msg.coins(pts) + " Punkte)");
                        open(pl);
                    });
                    if (err != null) feedback(pl, err);
                }),
                run(pl -> new SkyScreen(sb().core()).open(pl)),
                owner && left == 0 ? run(IslandScreen::chooseReset) : null,
                owner ? run(pl -> confirm(pl, "Insel aufgeben", i.members.isEmpty()
                        ? "Deine Insel wird geleert und ist dann weg. Wirklich aufgeben?"
                        : "Die Insel geht an " + IslandService.nameOf(i.members.iterator().next()) + ". Wirklich aufgeben?", "Aufgeben", q ->
                        feedback(q, is.abandon(q)))) : null,
                owner && left > 0 ? "Neustart in " + de.kuby.kubyplugins.service.KitService.format(left) : null)), 330);
    }

    /** Welche Blöcke ab dieser Generator-Stufe neu dazukommen (kurz). */
    private static String newOres(IslandService is, int level) {
        var ls = is.generatorLevels();
        if (level >= ls.size()) return "";
        List<String> out = new ArrayList<>();
        for (Material m : ls.get(level).weights().keySet()) {
            if (level == 0 || !ls.get(level - 1).weights().containsKey(m)) out.add(nice(m));
        }
        return out.isEmpty() ? "mehr Erze" : String.join(", ", out);
    }

    static String nice(Material m) {
        String n = m.name().toLowerCase().replace("_ore", "").replace("deepslate_", "").replace('_', ' ');
        return switch (n) {
            case "coal" -> "Kohle";
            case "iron" -> "Eisen";
            case "copper" -> "Kupfer";
            case "gold" -> "Gold";
            case "redstone" -> "Redstone";
            case "lapis" -> "Lapis";
            case "diamond" -> "Diamant";
            case "emerald" -> "Smaragd";
            case "ancient debris" -> "Antiker Schrott";
            case "stone" -> "Stein";
            case "cobblestone" -> "Bruchstein";
            default -> Character.toUpperCase(n.charAt(0)) + n.substring(1);
        };
    }

    /** Top-Inseln. */
    public static void top(Player p) {
        IslandService is = sb().islands();
        Island mine = is.islandOf(p.getUniqueId());
        List<TopRow> rows = new ArrayList<>();
        int r = 1;
        for (Island i : is.top(10)) {
            rows.add(new TopRow(r++, i.name, IslandService.nameOf(i.owner), is.levelOf(i), i.points, i == mine));
        }
        TopRow own = mine == null ? null : new TopRow(is.rank(mine), mine.name, IslandService.nameOf(mine.owner), is.levelOf(mine),
                mine.points, true);
        if (!sb().core().uiPack().active(p)) {
            Msg.send(p, "<#9AD8FF><b>Top-Inseln</b>");
            for (TopRow t : rows) Msg.send(p, " <gold>#" + t.rank() + " <white>" + Msg.escape(t.name()) + " <gray>(" + t.owner() + ") Level "
                    + t.level());
            return;
        }
        UiDialog.show(p, top(rows, own, run(IslandScreen::open)), 330);
    }

    /** Wahl der Insel-Vorlage (erste Insel). */
    public static void choose(Player p) {
        List<Choice> cs = new ArrayList<>();
        for (TemplateService.Template t : sb().templates().all()) {
            cs.add(new Choice(t.name(), t.desc(), Icons.of(t.icon(), KitData.Icon.GRASS_BLOCK), run(pl -> {
                if (!ModeService.SKYBLOCK.equals(sb().core().modes().modeOf(pl.getUniqueId()))) {
                    Msg.send(pl, "<red>Das geht nur im Skyblock-Modus.");
                    return;
                }
                String err = sb().islands().create(pl, t.id());
                if (err != null) feedback(pl, err);
            })));
        }
        if (!sb().core().uiPack().active(p)) {
            new TemplateMenu(sb().core(), false).open(p);
            return;
        }
        UiDialog.show(p, choose(cs, "Wähle deine Insel", "Du kannst sie später mit „Neu starten“ wechseln"), 330);
    }

    /** Neustart: Vorlage wählen, dann Rückfrage. */
    private static void chooseReset(Player p) {
        List<Choice> cs = new ArrayList<>();
        for (TemplateService.Template t : sb().templates().all()) {
            cs.add(new Choice(t.name(), t.desc(), Icons.of(t.icon(), KitData.Icon.GRASS_BLOCK), run(pl -> confirm(pl, "Insel neu starten",
                    "Alles auf der Insel wird gelöscht und neu als „" + t.name() + "“ gebaut. Upgrades und Mitglieder bleiben.", "Neu starten",
                    q -> feedback(q, sb().islands().reset(q, t.id()))))));
        }
        UiDialog.show(p, choose(cs, "Insel neu starten", "Wähle die neue Vorlage"), 330);
    }

    /** Rückfrage mit Ja und Nein. */
    public static void confirm(Player p, String title, String text, String yes, Consumer<Player> onYes) {
        if (!sb().core().uiPack().active(p)) {
            onYes.accept(p);
            return;
        }
        Canvas c = new Canvas(W).heading(title, SKY);
        c.box(0, 0, W, 72, KitData.Box.CLOUD);
        c.text(12, 10, Canvas.caps(title), SKY, true);
        List<String> lines = Canvas.wrap(text, W - 24);
        int y = 26;
        for (String l : lines) {
            c.text(12, y, l, CLOUD, false);
            y += 10;
        }
        int by = Math.max(52, y + 4);
        c.smallButton(W - 12 - 100, by, 100, KitData.Small.GOLD, yes, GOLD, true);
        c.action(W - 12 - 100, by, 100, KitData.SMALL_H, run(onYes), yes);
        c.smallButton(W - 12 - 210, by, 100, KitData.Small.DARK, "Abbrechen", CLOUD, false);
        c.action(W - 12 - 210, by, 100, KitData.SMALL_H, run(IslandScreen::open), "Abbrechen");
        UiDialog.show(p, c, 330);
    }

    /** Spieler einladen: Name eingeben. */
    public static void askInvite(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<#9AD8FF><b>Auf die Insel einladen</b>"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>Name des Spielers (muss online sein)."), 240)))
                .inputs(List.of(DialogInput.text("name", Msg.parse("<gray>Spieler")).width(200).maxLength(16).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton ok = ActionButton.create(Msg.parse("<green><b>Einladen</b>"), null, 100, DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String n = response.getText("name");
            Player t = n == null ? null : Bukkit.getPlayerExact(n.trim());
            if (t == null) feedback(pl, "Dieser Spieler ist nicht online.");
            else {
                String err = sb().islands().invite(pl, t);
                feedback(pl, err);
                if (err == null) Msg.send(pl, "<green>Einladung an " + t.getName() + " geschickt.");
            }
            open(pl);
        }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) open(pl);
        }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(ok, no))));
    }

    // =================================================================================================================
    // Darstellung
    // =================================================================================================================

    /** Knopf mit Icon (16 px) und Text; style null = gesperrt (grau). */
    private static void quick(Canvas c, int x, int y, int w, KitData.Button style, KitData.Sprite icon, String label, Consumer<Player> click) {
        c.button(x, y, w, style == null ? KitData.Button.DARK : style);
        String l = Canvas.fit(label, true, w - 26);
        int gw = 16 + 4 + Canvas.width(l, true);
        int gx = x + (w - gw) / 2;
        c.sprite(gx + (16 - icon.width) / 2, y + 3 + (16 - icon.height) / 2, icon);
        if (style == KitData.Button.TEAL || style == KitData.Button.GREEN || style == KitData.Button.GOLD) c.textFlat(gx + 20, y + 7, l, DARK, true);
        else c.text(gx + 20, y + 7, l, style == null ? DIM : CLOUD, true);
        if (click != null) c.action(x, y, w, 22, click, label);
    }

    private static int section(Canvas c, int y, String title, String right) {
        String t = Canvas.caps(title);
        c.text(2, y, t, SKY, true);
        int tw = Canvas.width(t, true);
        int rw = right == null ? 0 : Canvas.width(right, false) + 8;
        c.line(4 + tw + 4, y + 3, W - 8 - tw - 4 - rw, SKY);
        if (right != null) c.textRight(W - 2, y, right, MIST, false);
        return y + 12;
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Insel", SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_INSEL.width) / 2, 11, KitData.Sprite.HB_INSEL);
        c.title(50, 9, "Insel", false, SKY);
        c.text(50, 33, Canvas.fit(Canvas.lit(v.name()), true, 200), CLOUD, true);
        c.text(50, 43, "Level " + Msg.coins(v.level()) + " · " + Msg.coins(v.points()) + " / " + Msg.coins(v.nextLevel()) + " Punkte", MIST, false);
        c.textRight(378, 10, v.rank() > 0 ? "#" + v.rank() : "–", GOLD, true);
        c.textRight(378, 21, Canvas.caps("Platz"), MIST, false);
        c.textRight(378, 38, Msg.coins(v.coins()) + " $", GOLD, false);

        int y = 60, qw = (W - 9) / 4;
        quick(c, 0, y, qw, KitData.Button.TEAL, KitData.Sprite.HB_S_HOMES, "Nach Hause", v.home());
        quick(c, qw + 3, y, qw, KitData.Button.GREEN, KitData.Sprite.QU_COMPASS, "Home hier", v.setHome());
        quick(c, 2 * (qw + 3), y, qw, v.toggle() == null ? null : v.open() ? KitData.Button.PURPLE : KitData.Button.RED,
                KitData.Sprite.FR_PEOPLE, v.open() ? "Besuch: an" : "Besuch: aus", v.toggle());
        quick(c, 3 * (qw + 3), y, qw, KitData.Button.GOLD, KitData.Sprite.QU_S_RANK, "Top-Inseln", v.top());
        y += 22 + 10;

        // Upgrades
        y = section(c, y, "Upgrades", null);
        int cw = (W - 8) / 3, ch = 82;
        for (int k = 0; k < v.ups().size(); k++) {
            Up u = v.ups().get(k);
            int x = k * (cw + 4);
            c.box(x, y, cw, ch, KitData.Box.CLOUD_CARD);
            c.sprite(x + 6 + (32 - u.icon().width) / 2, y + 6 + (32 - u.icon().height) / 2, u.icon());
            c.text(x + 42, y + 8, Canvas.fit(u.name(), true, cw - 48), CLOUD, true);
            c.text(x + 42, y + 20, Canvas.fit(u.now(), false, cw - 48), MIST, false);
            if (u.next() == null) {
                c.text(x + 8, y + 46, Canvas.caps("Höchste Stufe"), GREEN, false);
            } else {
                List<String> nl = Canvas.wrap("→ " + u.next(), cw - 16);
                for (int l = 0; l < Math.min(2, nl.size()); l++) c.text(x + 8, y + 42 + 10 * l, nl.get(l), GREEN, false);
                c.text(x + 8, y + ch - 14, Msg.coins(u.price()) + " $", u.affordable() ? GOLD : RED, true);
                int bw = 52;
                c.smallButton(x + cw - 6 - bw, y + ch - 20, bw, u.affordable() ? KitData.Small.GOLD : KitData.Small.DARK, "Kaufen",
                        u.affordable() ? GOLD : DIM, true);
                if (u.buy() != null && u.affordable()) c.action(x + cw - 6 - bw, y + ch - 20, bw, KitData.SMALL_H, u.buy(), u.name() + " verbessern");
            }
        }
        y += ch + 10;

        // Mitglieder
        y = section(c, y, "Mitglieder", v.members().size() + "/" + v.limit());
        int rows = v.members().size();
        int mh = 8 + rows * 16 + 26;
        c.box(0, y, W, mh, KitData.Box.CLOUD_CARD);
        int my = y + 8;
        for (Member m : v.members()) {
            c.text(10, my + 3, m.online() ? "●" : "○", m.online() ? GREEN : DIM, false);
            int nw = c.text(22, my + 3, Canvas.fit(Canvas.lit(m.name()), true, 180), CLOUD, true);
            c.text(22 + nw + 6, my + 3, m.owner() ? "Besitzer" : "Mitglied", m.owner() ? GOLD : MIST, false);
            if (m.kick() != null) {
                c.smallButton(W - 8 - 70, my, 70, KitData.Small.DARK, "Entfernen", RED, false);
                c.action(W - 8 - 70, my, 70, KitData.SMALL_H, m.kick(), "Mitglied entfernen");
            }
            my += 16;
        }
        if (v.invite() != null) {
            c.smallButton(10, my + 4, 100, KitData.Small.GREEN, "Einladen", CLOUD, true);
            c.action(10, my + 4, 100, KitData.SMALL_H, v.invite(), "Spieler einladen");
        }
        if (v.leave() != null) {
            c.smallButton(10, my + 4, 100, KitData.Small.DARK, "Verlassen", RED, false);
            c.action(10, my + 4, 100, KitData.SMALL_H, v.leave(), "Insel verlassen");
        }
        y += mh + 10;

        // Insel
        y = section(c, y, "Insel", v.resetHint());
        int bw = (W - 9) / 4;
        String[] labels = {"Level zählen", "Checkliste", "Neu starten", "Aufgeben"};
        @SuppressWarnings("unchecked")
        Consumer<Player>[] acts = new Consumer[]{v.recalc(), v.progress(), v.reset(), v.abandon()};
        for (int k = 0; k < 4; k++) {
            int x = k * (bw + 3);
            boolean on = acts[k] != null;
            KitData.Small st = !on ? KitData.Small.DARK : k >= 2 ? KitData.Small.DARK : KitData.Small.GOLD;
            c.smallButton(x, y, bw, st, labels[k], !on ? DIM : k >= 2 ? RED : GOLD, on && k < 2);
            if (on) c.action(x, y, bw, KitData.SMALL_H, acts[k], labels[k]);
        }
        return c;
    }

    public static Canvas top(List<TopRow> rows, TopRow own, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading("Top-Inseln", SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.QU_RANK_HEAD.width) / 2, 11 + (32 - KitData.Sprite.QU_RANK_HEAD.height) / 2, KitData.Sprite.QU_RANK_HEAD);
        c.title(50, 12, "Top-Inseln", false, SKY);
        c.text(50, 36, "Level nach Punkten aller Blöcke", MIST, false);
        c.smallButton(W - 8 - 70, 18, 70, KitData.Small.DARK, "Zurück", CLOUD, false);
        c.action(W - 8 - 70, 18, 70, KitData.SMALL_H, back, "Zurück");
        int y = 60;
        KitData.Sprite[] medals = {KitData.Sprite.QU_M_GOLD, KitData.Sprite.QU_M_SILVER, KitData.Sprite.QU_M_BRONZE};
        for (TopRow t : rows) y = topRow(c, y, t, medals);
        if (rows.isEmpty()) {
            c.text(4, y, "Noch keine Inseln.", MIST, false);
            y += 14;
        }
        if (own != null && own.rank() > rows.size()) {
            y += 6;
            topRow(c, y, own, medals);
        }
        return c;
    }

    private static int topRow(Canvas c, int y, TopRow t, KitData.Sprite[] medals) {
        int h = t.rank() <= 3 ? 32 : 22;
        c.box(0, y, W, h, t.mine() ? KitData.Box.GOLD : KitData.Box.CLOUD_CARD);
        if (t.rank() <= 3) c.sprite(8 + (18 - medals[t.rank() - 1].width) / 2, y + (h - medals[t.rank() - 1].height) / 2, medals[t.rank() - 1]);
        else c.textCenter(17, y + 7, "#" + t.rank(), MIST, true);
        c.text(34, y + (h == 32 ? 7 : 7), Canvas.fit(Canvas.lit(t.name()), true, 210), t.mine() ? GOLD : CLOUD, true);
        if (h == 32) c.text(34, y + 18, Canvas.lit(t.owner()), MIST, false);
        c.textRight(W - 10, y + (h == 32 ? 7 : 7), "Level " + Msg.coins(t.level()), GOLD, true);
        if (h == 32) c.textRight(W - 10, y + 18, Msg.coins(t.points()) + " Punkte", MIST, false);
        return y + h + 3;
    }

    public static Canvas choose(List<Choice> choices, String title, String sub) {
        Canvas c = new Canvas(W).heading(title, SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_INSEL.width) / 2, 11, KitData.Sprite.HB_INSEL);
        c.title(50, 12, title, false, SKY);
        c.text(50, 36, sub, MIST, false);
        int y = 60, cw = (W - 4) / 2, ch = 64;
        for (int k = 0; k < choices.size(); k++) {
            Choice ch1 = choices.get(k);
            int x = (k % 2) * (cw + 4), cy = y + (k / 2) * (ch + 4);
            c.box(x, cy, cw, ch, KitData.Box.CLOUD_CARD);
            c.icon(x + 8, cy + 8, ch1.icon(), true);
            c.text(x + 48, cy + 9, Canvas.fit(ch1.name(), true, cw - 56), CLOUD, true);
            List<String> dl = Canvas.wrap(ch1.desc(), cw - 56);
            for (int l = 0; l < Math.min(2, dl.size()); l++) c.text(x + 48, cy + 21 + 10 * l, dl.get(l), MIST, false);
            c.smallButton(x + cw - 8 - 70, cy + ch - 20, 70, KitData.Small.GOLD, "Wählen", GOLD, true);
            c.action(x, cy, cw, ch, ch1.pick(), ch1.name() + " wählen");
        }
        if (choices.isEmpty()) c.text(4, y, "Es gibt noch keine Insel-Vorlage.", MIST, false);
        return c;
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        List<Up> ups = List.of(new Up("Größe", KitData.Sprite.HB_GROESSE, "999 x 999", "999 x 999", 999_999_999L, true, no),
                new Up("Mitglieder", KitData.Sprite.HB_MITGLIEDER, "99 Plätze", "99 Plätze", 999_999_999L, false, no),
                new Up("Generator", KitData.Sprite.HB_GENERATOR, "Stufe 99", "Stufe 99: Diamant, Smaragd, Antiker Schrott, Lapis", 1, true, no));
        List<Member> ms = new ArrayList<>();
        for (int k = 0; k < 11; k++) ms.add(new Member("W".repeat(16), k == 0, k % 2 == 0, k == 0 ? null : no));
        reg.accept("Insel", () -> canvas(new View(w, 999_999L, 99_999, 999_999_999L, 999_999_999L, true, true, 999_999_999L, ups, ms, 11,
                no, no, no, no, no, null, no, no, no, no, "Neustart in 23h 59m 59s")));
        reg.accept("Insel Mitglied", () -> canvas(new View("Insel", 0, 0, 0, 100, false, false, 0,
                List.of(new Up("Größe", KitData.Sprite.HB_GROESSE, "31 x 31", null, 0, false, null),
                        new Up("Mitglieder", KitData.Sprite.HB_MITGLIEDER, "2 Plätze", null, 0, false, null),
                        new Up("Generator", KitData.Sprite.HB_GENERATOR, "Stufe 1", null, 0, false, null)),
                List.of(new Member("Luno", true, true, null), new Member("Kuby", false, false, null)), 3,
                no, no, null, no, null, no, no, no, null, null, null)));
        List<TopRow> rows = new ArrayList<>();
        for (int k = 1; k <= 10; k++) rows.add(new TopRow(k, w, "W".repeat(16), 999_999_999L, 999_999_999L, k == 4));
        reg.accept("Insel Top", () -> top(rows, new TopRow(99_999, w, "W".repeat(16), 1, 1, true), no));
        reg.accept("Insel Top leer", () -> top(List.of(), null, no));
        List<Choice> cs = new ArrayList<>();
        for (int k = 0; k < 7; k++) cs.add(new Choice(w, "W".repeat(120), KitData.Icon.GRASS_BLOCK, no));
        reg.accept("Insel Vorlagen", () -> choose(cs, "Wähle deine Insel", "Du kannst sie später mit „Neu starten“ wechseln"));
    }
}
