package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /friends nach der Referenz „Neue-referenzen“: Kopf mit Freunde-Paar, Modus und Plätzen, rechts „Freunde online“;
 * Reiter Freunde, Anfragen (Unter-Reiter Erhalten / Gesendet) und Hinzufügen (Spieler in der Nähe, darunter das
 * Feld „Spielername“ und der Knopf „Anfrage senden“). Leere Listen wie im Original; volle Listen als Karten mit
 * Knöpfen (Nachspringen, Schreiben, Entfernen; Annehmen, Ablehnen, Zurückziehen; Anfrage). Ohne Pack: Truhe.
 */
public final class FriendsScreen {

    private static final int W = 390, HEIGHT = 330, ROW_H = 32, ROW_DY = 36, PER_PAGE = 6;
    private static final TextColor TEAL = TextColor.color(0x7AE5D7), GRAY = TextColor.color(0x9FA1A7),
            BODY = TextColor.color(0xD1D2D5), WHITE = TextColor.color(0xFCFCFC), ON = TextColor.color(0x1C122D),
            GREEN = TextColor.color(0x6DE288), GOLD = TextColor.color(0xFCD45D), DIM = TextColor.color(0x70707A);

    public enum Tab { FRIENDS, REQUESTS, ADD }

    /** Eine Zeile der Freundesliste: Name, online, im selben Modus (dann Nachspringen), Ort bzw. zuletzt gesehen. */
    public record Row(String name, boolean online, boolean here, String where) {}

    /** Eine Anfrage: an mich (incoming) oder von mir. */
    public record Req(String name, boolean incoming) {}

    /** Ein Spieler in der Nähe (Hinzufügen): Name und Entfernung in Blöcken. */
    public record Near(String name, int distance) {}

    public record View(Tab tab, boolean sent, String modeLabel, List<Row> friends, List<Req> requests, List<Near> near,
                       int limit, int page, String armed) {}

    public interface Actions {
        Consumer<Player> tab(Tab tab, boolean sent);

        Consumer<Player> accept(String name);

        Consumer<Player> deny(String name);

        Consumer<Player> jump(String name);

        Consumer<Player> write(String name);

        Consumer<Player> remove(String name);

        Consumer<Player> request(String name);

        Consumer<Player> page(int page);
    }

    private final KubyPlugins plugin;
    /** Zweimal klicken zum Entfernen: Spieler -> Freund, der gerade "Sicher?" zeigt. */
    private final Map<UUID, UUID> armed = new HashMap<>();

    public FriendsScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Öffnet das Dialog-Menü mit Pack, sonst die Truhe. */
    public void open(Player p) {
        if (plugin.uiPack().active(p)) show(p, Tab.FRIENDS, false, 0);
        else new FriendsMenu(plugin).open(p);
    }

    private record Entry(UUID id, String name, boolean online, boolean here, String where) {}

    private List<Entry> entries(Player p) {
        List<Entry> out = new ArrayList<>();
        String myMode = plugin.modes().modeOf(p.getUniqueId());
        for (UUID id : plugin.friends().friendsOf(p.getUniqueId())) {
            Player o = Bukkit.getPlayer(id);
            boolean online = o != null && p.canSee(o);
            if (online) {
                String mode = plugin.modes().modeOf(id);
                boolean here = myMode.equals(mode);
                String place = plugin.textValue("place", id);
                String where = !here ? "gerade in " + ModeService.label(mode) : place.isEmpty() ? "online" : place;
                out.add(new Entry(id, o.getName(), true, here, where));
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                out.add(new Entry(id, op.getName() == null ? "Unbekannt" : op.getName(), false, false, lastSeen(op.getLastSeen())));
            }
        }
        out.sort((a, b) -> a.online != b.online ? (a.online ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private static String lastSeen(long at) {
        if (at <= 0) return "lange nicht gesehen";
        long min = (System.currentTimeMillis() - at) / 60_000;
        if (min < 60) return "zuletzt vor " + Math.max(1, min) + " Min.";
        long h = min / 60;
        if (h < 24) return "zuletzt vor " + h + (h == 1 ? " Stunde" : " Stunden");
        long d = h / 24;
        return "zuletzt vor " + d + (d == 1 ? " Tag" : " Tagen");
    }

    private static String name(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "Unbekannt" : op.getName();
    }

    public void show(Player p, Tab tab, boolean sent, int page) {
        UUID me = p.getUniqueId();
        Map<String, UUID> ids = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (Entry e : entries(p)) {
            rows.add(new Row(e.name, e.online, e.here, e.where));
            ids.put(e.name, e.id);
        }
        List<Req> reqs = new ArrayList<>();
        for (UUID id : plugin.friends().requestsOf(me)) {
            reqs.add(new Req(name(id), true));
            ids.put(name(id), id);
        }
        for (UUID id : plugin.friends().outgoingOf(me)) {
            reqs.add(new Req(name(id), false));
            ids.put(name(id), id);
        }
        // Spieler in der Nähe (gleiche Welt, bis 32 Blöcke), die noch keine Freunde sind
        List<Near> near = new ArrayList<>();
        for (Player o : p.getWorld().getPlayers()) {
            if (o.equals(p) || !p.canSee(o) || plugin.friends().areFriends(me, o.getUniqueId())) continue;
            double d = o.getLocation().distance(p.getLocation());
            if (d <= 32) near.add(new Near(o.getName(), (int) Math.round(d)));
        }
        near.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        UUID sure = armed.get(me);
        View v = new View(tab, sent, ModeService.label(plugin.modes().modeOf(me)), rows, reqs, near,
                plugin.friends().limit(p), page, sure == null ? null : name(sure));
        Actions a = actions(ids, tab, sent, page);
        Canvas c = canvas(v, a);
        if (tab != Tab.ADD) {
            UiDialog.show(p, c, HEIGHT);
            return;
        }
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton send = ActionButton.create(UiDialog.label(KitData.Sprite.FR_MAIL, "Anfrage senden", TEAL), null, 176,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String n = response.getText("name");
                    request(pl, n == null ? "" : n.trim());
                    show(pl, Tab.ADD, false, 0);
                }, once));
        p.showDialog(UiDialog.dialog(p, c, List.of(DialogInput.text("name", Msg.parse("Spielername"))
                .width(258).maxLength(16).initial("").build()), send));
    }

    private Actions actions(Map<String, UUID> ids, Tab tab, boolean sent, int pg) {
        return new Actions() {
            public Consumer<Player> tab(Tab t, boolean s) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    show(pl, t, s, 0);
                };
            }

            public Consumer<Player> accept(String n) {
                return pl -> {
                    String err = plugin.friends().accept(pl.getUniqueId(), ids.get(n));
                    Msg.send(pl, err == null ? "<green>Du und <white>" + n + " <green>seid jetzt Freunde!" : "<red>" + err);
                    Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                    show(pl, tab, sent, pg);
                };
            }

            public Consumer<Player> deny(String n) {
                return pl -> {
                    plugin.friends().deny(pl.getUniqueId(), ids.get(n));
                    show(pl, tab, sent, pg);
                };
            }

            public Consumer<Player> jump(String n) {
                return pl -> {
                    String err = plugin.friends().jump(pl, Bukkit.getPlayer(ids.get(n)));
                    if (err != null) {
                        Msg.send(pl, "<red>" + err);
                        Sfx.play(plugin, pl, Sfx.Kind.ERROR);
                    } else {
                        pl.closeDialog();
                    }
                };
            }

            public Consumer<Player> write(String n) {
                return pl -> askMessage(pl, ids.get(n), n, pg);
            }

            public Consumer<Player> remove(String n) {
                return pl -> {
                    UUID id = ids.get(n);
                    if (id.equals(armed.get(pl.getUniqueId()))) {
                        armed.remove(pl.getUniqueId());
                        plugin.friends().remove(pl.getUniqueId(), id);
                        Msg.send(pl, "<gray>Freundschaft mit <white>" + n + " <gray>beendet.");
                    } else {
                        armed.put(pl.getUniqueId(), id);
                    }
                    show(pl, tab, sent, pg);
                };
            }

            public Consumer<Player> request(String n) {
                return pl -> {
                    FriendsScreen.this.request(pl, n);
                    show(pl, tab, sent, pg);
                };
            }

            public Consumer<Player> page(int n) {
                return pl -> show(pl, tab, sent, n);
            }
        };
    }

    // =================================================================================================================
    // Darstellung

    /** Reiter: gewählt türkis gefüllt mit dunkler Schrift, sonst dunkel mit türkiser Schrift. */
    private static void tab(Canvas c, int x, int w, KitData.Sprite icon, String label, boolean on, Consumer<Player> click) {
        c.box(x, 55, w, 22, on ? KitData.Box.FR_TAB_ON : KitData.Box.FR_TAB_OFF);
        // der Brief ist 13 px hoch und steht einen Pixel tiefer als die 15/16 px hohen Symbole
        c.sprite(x + 8, icon == KitData.Sprite.FR_MAIL ? 59 : 58, icon);
        c.text(x + 30, 62, label, on ? ON : TEAL, on);
        if (!on) c.action(x, 55, w, 22, click, label);
    }

    /** Unter-Reiter (Anfragen): Erhalten n / Gesendet n. */
    private static void sub(Canvas c, int x, String label, boolean on, Consumer<Player> click) {
        c.box(x, 81, 136, 20, on ? KitData.Box.FR_SUB_ON : KitData.Box.FR_SUB_OFF);
        center(c, x + 68, 87, label, on ? ON : TEAL);
        if (!on) c.action(x, 81, 136, 20, click, label);
    }

    /** Fett, mittig um cx wie im Original (bei ungerader Breite ein Pixel weiter links als textCenter). */
    private static void center(Canvas c, int cx, int y, String label, TextColor color) {
        c.text(cx - (Canvas.width(label, true) + 1) / 2, y, label, color, true);
    }

    /** Türkiser Knopf (20 hoch) mit dunkler Schrift, Breite nach Text. Gibt die Breite zurück. */
    private static int teal(Canvas c, int x, int y, String label, Consumer<Player> click, String hover) {
        int w = Canvas.width(label, true) + 16;
        c.box(x, y, w, 20, KitData.Box.FR_BTN, Canvas.Z_BUTTON);
        c.textCenter(x + w / 2, y + 6, label, ON, true);
        c.action(x, y, w, 20, click, hover);
        return w;
    }

    /** Dunkler Knopf (20 hoch) mit türkiser Schrift. Gibt die Breite zurück. */
    private static int dark(Canvas c, int x, int y, String label, TextColor color, Consumer<Player> click, String hover) {
        int w = Canvas.width(label, true) + 16;
        c.box(x, y, w, 20, KitData.Box.FR_SUB_OFF, Canvas.Z_BUTTON);
        c.textCenter(x + w / 2, y + 6, label, color, true);
        c.action(x, y, w, 20, click, hover);
        return w;
    }

    /** Leere Liste wie im Original: Bild, Titel, Text, optional der Knopf „Freund hinzufügen“. */
    private static int empty(Canvas c, int y, KitData.Sprite icon, int ix, int iy, String title, List<String> lines,
                             Consumer<Player> add) {
        int h = add != null ? 78 : 51;
        int ty = add != null ? y + 9 : y + 11;      // ohne Knopf steht der Text etwas tiefer, mittig im Kasten
        c.box(0, y, W, h, KitData.Box.FR_CARD);
        c.sprite(ix, y + iy, icon);
        c.text(62, ty, title, WHITE, true);
        for (int i = 0; i < lines.size(); i++) c.text(62, ty + 12 + 10 * i, lines.get(i), BODY, false);
        if (add != null) {
            c.box(62, y + 45, 136, 20, KitData.Box.FR_BTN, Canvas.Z_BUTTON);
            c.textCenter(130, y + 51, "Freund hinzufügen", ON, true);
            c.action(62, y + 45, 136, 20, add, "Zu „Hinzufügen“");
        }
        return h;
    }

    /** Seiten unten: ‹ Seite n / m ›. */
    private static void pager(Canvas c, int y, int pg, int pages, Actions a) {
        if (pages <= 1) return;
        if (pg > 0) dark(c, 0, y, "‹", TEAL, a.page(pg - 1), "Vorige Seite");
        c.textCenter(195, y + 6, "Seite " + (pg + 1) + " / " + pages, GRAY, false);
        if (pg + 1 < pages) {
            int w = Canvas.width("›", true) + 16;
            dark(c, W - w, y, "›", TEAL, a.page(pg + 1), "Nächste Seite");
        }
    }

    /** Nur Darstellung (auch für Vorschauen ohne Server). */
    public static Canvas canvas(View v, Actions a) {
        List<Row> list = v.friends();
        long online = list.stream().filter(Row::online).count();
        Canvas c = new Canvas(W).heading("Freunde", TEAL);

        // Kopf
        c.box(0, 0, W, 51, KitData.Box.FR_HEAD);
        c.sprite(10, 11, KitData.Sprite.FR_LOGO);
        c.text(50, 16, "FREUNDE", TEAL, true);
        // grüner Punkt (4 x 4, ohne Schatten) vor dem Modus
        c.line(51, 31, 2, GREEN);
        c.line(50, 32, 4, GREEN);
        c.line(50, 33, 4, GREEN);
        c.line(51, 34, 2, GREEN);
        c.text(59, 29, Canvas.fit("Du spielst auf " + v.modeLabel() + " · " + list.size() + " von " + v.limit() + " Plätzen",
                false, 228), GRAY, false);
        c.sprite(290, 12, KitData.Sprite.FR_PEOPLE);
        c.text(312, 11, "Freunde", GRAY, false);
        c.text(312, 21, "online", GRAY, false);
        c.textRight(380, 16, List.of(Canvas.Span.bold(String.valueOf(online), GREEN), Canvas.Span.of("/" + list.size(), GRAY)));

        // Reiter
        long incoming = v.requests().stream().filter(Req::incoming).count();
        tab(c, 0, 126, KitData.Sprite.FR_PEOPLE_TAB, "Freunde", v.tab() == Tab.FRIENDS, a.tab(Tab.FRIENDS, false));
        tab(c, 132, 126, KitData.Sprite.FR_MAIL, incoming > 0 ? "Anfragen (" + incoming + ")" : "Anfragen",
                v.tab() == Tab.REQUESTS, a.tab(Tab.REQUESTS, false));
        tab(c, 264, 126, KitData.Sprite.FR_PLUS, "Hinzufügen", v.tab() == Tab.ADD, a.tab(Tab.ADD, false));

        switch (v.tab()) {
            case FRIENDS -> friends(c, v, a);
            case REQUESTS -> requests(c, v, a);
            case ADD -> add(c, v, a);
        }
        return c;
    }

    private static void friends(Canvas c, View v, Actions a) {
        List<Row> list = v.friends();
        int y = 90;
        if (list.isEmpty()) {
            empty(c, y, KitData.Sprite.FR_ADD, 18, 25, "Noch keine Freunde",
                    Canvas.wrap("Schick jemandem eine Anfrage. Sobald er annimmt, siehst du hier, wann und wo er spielt.", 260),
                    a.tab(Tab.ADD, false));
            return;
        }
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(v.page(), pages - 1));
        for (int i = pg * PER_PAGE; i < Math.min(list.size(), (pg + 1) * PER_PAGE); i++) {
            Row e = list.get(i);
            c.box(0, y, W, ROW_H, KitData.Box.FR_CARD);
            // Knöpfe von rechts
            boolean confirm = e.name().equals(v.armed());
            String rm = confirm ? "Sicher?" : "×";
            int right = 384 - (Canvas.width(rm, true) + 16);
            dark(c, right, y + 6, rm, confirm ? GOLD : GRAY, a.remove(e.name()),
                    confirm ? "Nochmal klicken: " + e.name() + " entfernen" : e.name() + " entfernen (zweimal klicken)");
            if (e.online()) {
                right -= Canvas.width("Schreiben", true) + 20;
                dark(c, right, y + 6, "Schreiben", TEAL, a.write(e.name()), "Nachricht an " + e.name());
                if (e.here()) {
                    right -= Canvas.width("Nachspringen", true) + 20;
                    teal(c, right, y + 6, "Nachspringen", a.jump(e.name()), "Zu " + e.name() + " teleportieren");
                }
            }
            TextColor dot = e.online() ? (e.here() ? GREEN : GOLD) : DIM;
            c.line(12, y + 11, 3, dot);
            c.line(12, y + 12, 3, dot);
            c.line(12, y + 13, 3, dot);
            c.text(20, y + 8, Canvas.fit(e.name(), true, right - 30), e.online() ? WHITE : GRAY, true);
            c.text(20, y + 19, Canvas.fit(e.where(), false, right - 30), e.online() ? BODY : GRAY, false);
            y += ROW_DY;
        }
        pager(c, y, pg, pages, a);
    }

    private static void requests(Canvas c, View v, Actions a) {
        List<Req> in = v.requests().stream().filter(Req::incoming).toList();
        List<Req> out = v.requests().stream().filter(r -> !r.incoming()).toList();
        sub(c, 0, "Erhalten " + in.size(), !v.sent(), a.tab(Tab.REQUESTS, false));
        sub(c, 142, "Gesendet " + out.size(), v.sent(), a.tab(Tab.REQUESTS, true));
        List<Req> list = v.sent() ? out : in;
        int y = 108;
        if (list.isEmpty()) {
            if (v.sent()) {
                empty(c, y, KitData.Sprite.FR_ADD, 18, 25, "Keine Anfrage unterwegs", List.of("Du wartest gerade auf keine Antwort.",
                        "Neue Freunde findest du unter „Hinzufügen“."), a.tab(Tab.ADD, false));
            } else {
                empty(c, y, KitData.Sprite.FR_LETTER, 16, 13, "Keine offenen Anfragen", List.of(
                        "Schickt dir jemand eine Anfrage, landet sie hier.", "Im Chat bekommst du dazu auch eine Nachricht."), null);
            }
            return;
        }
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(v.page(), pages - 1));
        for (int i = pg * PER_PAGE; i < Math.min(list.size(), (pg + 1) * PER_PAGE); i++) {
            Req r = list.get(i);
            c.box(0, y, W, ROW_H, KitData.Box.FR_CARD);
            int right;
            if (r.incoming()) {
                right = 384 - (Canvas.width("Ablehnen", true) + 16);
                dark(c, right, y + 6, "Ablehnen", TEAL, a.deny(r.name()), "Anfrage ablehnen");
                right -= Canvas.width("Annehmen", true) + 20;
                teal(c, right, y + 6, "Annehmen", a.accept(r.name()), "Anfrage annehmen");
            } else {
                right = 384 - (Canvas.width("Zurückziehen", true) + 16);
                dark(c, right, y + 6, "Zurückziehen", TEAL, a.deny(r.name()), "Anfrage zurückziehen");
            }
            c.text(12, y + 8, Canvas.fit(r.name(), true, right - 22), WHITE, true);
            c.text(12, y + 19, r.incoming() ? "möchte dein Freund sein" : "wartet auf Antwort", GRAY, false);
            y += ROW_DY;
        }
        pager(c, y, pg, pages, a);
    }

    private static void add(Canvas c, View v, Actions a) {
        int y = 81;
        if (v.near().isEmpty()) {
            c.box(0, y, W, 33, KitData.Box.FR_CARD);
            c.sprite(12, y + 10, KitData.Sprite.FR_PLANE);
            c.text(38, y + 7, "Gerade ist niemand in deiner Nähe", WHITE, true);
            c.text(38, y + 19, "Tipp unten einen Namen ein, auch wenn er offline ist.", BODY, false);
            return;
        }
        // Spieler in der Nähe: bis zu 4, je mit „Anfrage“
        List<Near> near = v.near().subList(0, Math.min(4, v.near().size()));
        int h = 22 + 20 * near.size() + 4;
        c.box(0, y, W, h, KitData.Box.FR_CARD);
        c.sprite(12, y + 6, KitData.Sprite.FR_PLANE);
        c.text(38, y + 7, "Spieler in deiner Nähe", WHITE, true);
        c.textRight(380, y + 7, "oder unten einen Namen eintippen", GRAY, false);
        for (int i = 0; i < near.size(); i++) {
            Near n = near.get(i);
            int ry = y + 22 + 20 * i;
            int w = Canvas.width("Anfrage", true) + 16;
            teal(c, 384 - w, ry, "Anfrage", a.request(n.name()), "Anfrage an " + n.name() + " senden");
            c.text(38, ry + 6, Canvas.fit(n.name(), true, 200), WHITE, true);
            c.textRight(384 - w - 8, ry + 6, n.distance() + " Blöcke", GRAY, false);
        }
    }

    // =================================================================================================================
    // Nachricht, Anfrage, Chat

    private void askMessage(Player p, UUID to, String toName, int page) {
        DialogBase base = DialogBase.builder(Msg.parse("<#7AE5D7><b>Nachricht an " + Msg.escape(toName)))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .inputs(List.of(DialogInput.text("text", Msg.parse("<gray>Nachricht")).width(300).maxLength(200).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<b>Senden</b>"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String t = response.getText("text");
                    if (t != null && !t.isBlank()) plugin.messages().send(pl, to, toName, t.trim());
                    show(pl, Tab.FRIENDS, false, page);
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) show(pl, Tab.FRIENDS, false, page);
                }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    /** Anfrage per Name (aus /friend add und dem Menü). */
    public void request(Player p, String targetName) {
        UUID target = resolve(targetName);
        if (target == null) {
            Msg.send(p, "<red>Spieler nicht gefunden.");
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            return;
        }
        String err = plugin.friends().request(p.getUniqueId(), target);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            return;
        }
        String n = name(target);
        if (plugin.friends().areFriends(p.getUniqueId(), target)) {
            // Der andere hatte schon gefragt: damit seid ihr jetzt Freunde
            Msg.send(p, "<green>Du und <white>" + n + " <green>seid jetzt Freunde!");
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
            return;
        }
        Msg.send(p, "<green>Anfrage an <white>" + n + " <green>gesendet.");
        Player other = Bukkit.getPlayer(target);
        if (other != null) {
            Msg.send(other, "<gray>Freundesanfrage von <white>" + p.getName() + "<gray>. "
                    + "<click:run_command:'/friend accept " + p.getName() + "'><green>[Annehmen]</click> "
                    + "<click:run_command:'/friend deny " + p.getName() + "'><red>[Ablehnen]</click>");
            Sfx.play(plugin, other, Sfx.Kind.PING);
        }
    }

    /** Spieler-UUID aus Name: online oder aus dem Cache. */
    public static UUID resolve(String name) {
        if (name == null || name.isEmpty() || !name.matches("[A-Za-z0-9_.]{1,16}")) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op == null ? null : op.getUniqueId();
    }

    /** Für /friend list im Chat. */
    public void chatList(Player p) {
        List<Entry> list = entries(p);
        Msg.send(p, "<#7AE5D7><b>FREUNDE</b> <dark_gray>▶ <gray>" + list.stream().filter(Entry::online).count() + " online, "
                + list.size() + " / " + plugin.friends().limit(p) + " Plätze");
        for (Entry e : list) {
            String jump = e.online && e.here ? " <click:run_command:'/friend jump " + e.name + "'><green>[Nachspringen]</click>" : "";
            Msg.send(p, (e.online ? "<green>● " : "<dark_gray>● ") + "<white>" + e.name + " <gray>" + Msg.escape(e.where) + jump);
        }
        if (list.isEmpty()) Msg.send(p, "<gray>Noch keine Freunde. <white>/friend add <Spieler>");
    }
}
