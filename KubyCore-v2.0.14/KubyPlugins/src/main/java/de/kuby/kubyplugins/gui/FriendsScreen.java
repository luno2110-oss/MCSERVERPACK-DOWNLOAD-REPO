package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.Ui;
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
 * /friends auf dem Dialog-Kit (Kopf wie die Hilfe-Seiten): Anfragen annehmen/ablehnen, Freunde mit Online-Status und
 * Ort (Modus · Spawn/Farmwelt/...), Nachspringen, Schreiben, Entfernen (zweimal klicken), Freund hinzufügen und die
 * Schalter für Anfragen und Nachspringen. Ohne Pack gibt es weiter die Truhe (FriendsMenu).
 */
public final class FriendsScreen {

    private static final int W = 390, HEIGHT = 330, ROW = 20;
    private static final TextColor ACCENT = Ui.Theme.GREEN.accent, GRAY = TextColor.color(0xA0A0AC),
            LIGHT = TextColor.color(0xE4E4EA), WHITE = TextColor.color(0xF4F4F4), GOLD = TextColor.color(0xF8D35A),
            DIM = TextColor.color(0x70707A), ON = TextColor.color(0x201400), NAV = TextColor.color(0xC0C4CB);

    private final KubyPlugins plugin;
    /** Zweimal klicken zum Entfernen: Spieler -> Freund, der gerade "Sicher?" zeigt. */
    private final Map<UUID, UUID> armed = new HashMap<>();

    public FriendsScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Öffnet das Dialog-Menü mit Pack, sonst die Truhe. */
    public void open(Player p) {
        if (plugin.uiPack().active(p)) show(p, 0);
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
                String where = !here ? "gerade in " + de.kuby.kubyplugins.service.ModeService.label(mode)
                        : place.isEmpty() ? "online" : place;
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

    /** Kleiner Knopf rechtsbündig bis right, gibt die linke Kante zurück. */
    private static int small(Canvas c, int right, int y, String label, KitData.Small style, TextColor color, Consumer<Player> click,
                             String hover) {
        int w = Canvas.width(label, true) + 14;
        c.smallButton(right - w, y, w, style, label, color, true);
        c.action(right - w, y, w, KitData.SMALL_H, click, hover);
        return right - w;
    }

    private static int button(Canvas c, int x, int y, String label, boolean action, Consumer<Player> click, String hover) {
        int w = Canvas.width(label, action) - 1 + (action ? 24 : 18);
        c.button(x, y, w, action ? KitData.Button.H_GREEN : KitData.Button.H_NAV, label, action ? ON : NAV, action);
        if (click != null) c.action(x, y, w, KitData.BTN_H, click, hover);
        return w;
    }

    /** Eine Zeile der Liste: Name, online, im selben Modus (dann Nachspringen), Ort bzw. zuletzt gesehen. */
    public record Row(String name, boolean online, boolean here, String where) {}

    /** Eine Anfrage: an mich (incoming) oder von mir. */
    public record Req(String name, boolean incoming) {}

    public record View(List<Row> friends, List<Req> requests, int limit, boolean requestsOn, boolean jumpOn, int page,
                       String armed) {}

    public interface Actions {
        Consumer<Player> accept(String name);

        Consumer<Player> deny(String name);

        Consumer<Player> jump(String name);

        Consumer<Player> write(String name);

        Consumer<Player> remove(String name);

        Consumer<Player> add();

        Consumer<Player> toggleRequests();

        Consumer<Player> toggleJump();

        Consumer<Player> page(int page);
    }

    public void show(Player p, int page) {
        UUID me = p.getUniqueId();
        List<Entry> list = entries(p);
        Map<String, UUID> ids = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (Entry e : list) {
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
        UUID sure = armed.get(me);
        String armedName = sure == null ? null : name(sure);
        View v = new View(rows, reqs, plugin.friends().limit(p), plugin.playerSettings().get(me, "friends"),
                plugin.playerSettings().get(me, "jump"), page, armedName);
        int pg = page;
        Actions a = new Actions() {
            public Consumer<Player> accept(String n) {
                return pl -> {
                    String err = plugin.friends().accept(pl.getUniqueId(), ids.get(n));
                    Msg.send(pl, err == null ? "<green>Du und <white>" + n + " <green>seid jetzt Freunde!" : "<red>" + err);
                    Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                    show(pl, pg);
                };
            }

            public Consumer<Player> deny(String n) {
                return pl -> {
                    plugin.friends().deny(pl.getUniqueId(), ids.get(n));
                    show(pl, pg);
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
                    show(pl, pg);
                };
            }

            public Consumer<Player> add() {
                return FriendsScreen.this::askAdd;
            }

            public Consumer<Player> toggleRequests() {
                return pl -> {
                    plugin.playerSettings().toggle(pl.getUniqueId(), "friends");
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    show(pl, pg);
                };
            }

            public Consumer<Player> toggleJump() {
                return pl -> {
                    plugin.playerSettings().toggle(pl.getUniqueId(), "jump");
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    show(pl, pg);
                };
            }

            public Consumer<Player> page(int n) {
                return pl -> show(pl, n);
            }
        };
        UiDialog.show(p, canvas(v, a), HEIGHT);
    }

    /** Nur Darstellung (auch für Vorschauen ohne Server). */
    public static Canvas canvas(View v, Actions a) {
        List<Row> list = v.friends();
        long online = list.stream().filter(Row::online).count();
        Canvas c = new Canvas(W).heading("Freunde", net.kyori.adventure.text.format.TextColor.color(0x5FE07A));
        c.box(0, 0, W, 51, KitData.Box.GREEN);
        c.smallIcon(13, 16, KitData.SmallIcon.H_PEOPLE);
        c.text(41, 15, "FREUNDE", Ui.Theme.GREEN.title, true);
        c.text(41, 28, "Wer online ist, wo, und nachspringen", GRAY, false);
        c.textRight(378, 21, List.of(Span.bold(online + " online", ACCENT), Span.of("  ·  " + list.size() + " / " + v.limit() + " Plätze", GRAY)));
        int y = 53;

        // Anfragen: erst die an mich, dann meine eigenen
        List<Req> reqs = new ArrayList<>(v.requests());
        reqs.sort((x, z) -> Boolean.compare(z.incoming(), x.incoming()));
        int reqRows = Math.min(3, reqs.size());
        if (reqRows > 0) {
            int h = 22 + ROW * reqRows;
            c.box(0, y, W, h, KitData.Box.GREEN);
            c.text(9, y + 9, "ANFRAGEN", ACCENT, true);
            if (reqs.size() > reqRows) c.textRight(378, y + 9, "+" + (reqs.size() - reqRows) + " weitere", GRAY, false);
            for (int i = 0; i < reqRows; i++) {
                int ty = y + 19 + ROW * i;
                Req r = reqs.get(i);
                int left;
                if (r.incoming()) {
                    left = small(c, 380, ty, "Ablehnen", KitData.Small.DARK, LIGHT, a.deny(r.name()), "Anfrage ablehnen");
                    left = small(c, left - 4, ty, "Annehmen", KitData.Small.GREEN, WHITE, a.accept(r.name()), "Anfrage annehmen");
                } else {
                    left = small(c, 380, ty, "Zurückziehen", KitData.Small.DARK, LIGHT, a.deny(r.name()), "Anfrage zurückziehen");
                }
                int nw = c.text(12, ty + 4, Canvas.fit(r.name(), true, 120), WHITE, true);
                c.text(12 + nw + 6, ty + 4, Canvas.fit(r.incoming() ? "möchte dein Freund sein" : "wartet auf Antwort", false,
                        left - 12 - nw - 14), GRAY, false);
            }
            y += h + 3;
        }

        // Freundesliste mit Seiten
        int perPage = Math.max(3, (HEIGHT - y - 22 - 6 - KitData.BTN_H - 4) / ROW);
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(v.page(), pages - 1));
        int from = pg * perPage, to = Math.min(list.size(), from + perPage);
        int rows = Math.max(1, to - from);
        int h = 22 + ROW * rows + 2;
        c.box(0, y, W, h, KitData.Box.GREEN);
        c.text(9, y + 9, "DEINE FREUNDE", ACCENT, true);
        if (pages > 1) c.textRight(378, y + 9, "Seite " + (pg + 1) + " / " + pages, GRAY, false);
        if (list.isEmpty()) {
            c.text(12, y + 23, "Noch keine Freunde. Schick eine Anfrage mit „Freund hinzufügen“.", LIGHT, false);
        }
        for (int i = from; i < to; i++) {
            Row e = list.get(i);
            int ty = y + 19 + ROW * (i - from);
            boolean confirm = e.name().equals(v.armed());
            int left = small(c, 380, ty, confirm ? "Sicher?" : "×", confirm ? KitData.Small.GOLD : KitData.Small.DARK,
                    confirm ? GOLD : GRAY, a.remove(e.name()),
                    confirm ? "Nochmal klicken: " + e.name() + " entfernen" : e.name() + " entfernen (zweimal klicken)");
            if (e.online()) {
                left = small(c, left - 4, ty, "Schreiben", KitData.Small.DARK, LIGHT, a.write(e.name()), "Nachricht an " + e.name());
                if (e.here()) {
                    left = small(c, left - 4, ty, "Nachspringen", KitData.Small.GREEN, WHITE, a.jump(e.name()),
                            "Zu " + e.name() + " teleportieren");
                }
            }
            c.text(12, ty + 4, "●", e.online() ? (e.here() ? ACCENT : GOLD) : DIM, false);
            int nw = c.text(22, ty + 4, Canvas.fit(e.name(), true, 110), e.online() ? WHITE : GRAY, true);
            c.text(22 + nw + 6, ty + 4, Canvas.fit(e.where(), false, left - 22 - nw - 14), e.online() ? LIGHT : DIM, false);
        }
        y += h + 3;

        // Knöpfe
        int bx = button(c, 0, y, "Freund hinzufügen", true, a.add(), "Anfrage an einen Spieler schicken") + 4;
        bx += button(c, bx, y, "Anfragen: " + (v.requestsOn() ? "an" : "aus"), false, a.toggleRequests(),
                v.requestsOn() ? "Andere dürfen dir Anfragen schicken" : "Niemand kann dir gerade Anfragen schicken") + 4;
        button(c, bx, y, "Nachspringen: " + (v.jumpOn() ? "an" : "aus"), false, a.toggleJump(),
                v.jumpOn() ? "Freunde dürfen zu dir springen" : "Niemand kann gerade zu dir springen");
        if (pages > 1) {
            int nw = Canvas.width("›", false) + 18;
            if (pg + 1 < pages) button(c, W - nw, y, "›", false, a.page(pg + 1), "Nächste Seite");
            if (pg > 0) button(c, W - 2 * nw - 4, y, "‹", false, a.page(pg - 1), "Vorige Seite");
        }
        return c;
    }

    private void askAdd(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<#5FE07A><b>Freund hinzufügen"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .inputs(List.of(DialogInput.text("name", Msg.parse("<gray>Spielername")).width(220).maxLength(16).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>Anfrage schicken</b>"), null, 120,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String n = response.getText("name");
                    request(pl, n == null ? "" : n.trim());
                    show(pl, 0);
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) show(pl, 0);
                }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    private void askMessage(Player p, UUID to, String toName, int page) {
        DialogBase base = DialogBase.builder(Msg.parse("<#5FE07A><b>Nachricht an " + Msg.escape(toName)))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .inputs(List.of(DialogInput.text("text", Msg.parse("<gray>Nachricht")).width(300).maxLength(200).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>Senden</b>"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String t = response.getText("text");
                    if (t != null && !t.isBlank()) plugin.messages().send(pl, to, toName, t.trim());
                    show(pl, page);
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) show(pl, page);
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
        Msg.send(p, "<#5FE07A><b>FREUNDE</b> <dark_gray>▶ <gray>" + list.stream().filter(Entry::online).count() + " online, "
                + list.size() + " / " + plugin.friends().limit(p) + " Plätze");
        for (Entry e : list) {
            String jump = e.online && e.here ? " <click:run_command:'/friend jump " + e.name + "'><green>[Nachspringen]</click>" : "";
            Msg.send(p, (e.online ? "<green>● " : "<dark_gray>● ") + "<white>" + e.name + " <gray>" + Msg.escape(e.where) + jump);
        }
        if (list.isEmpty()) Msg.send(p, "<gray>Noch keine Freunde. <white>/friend add <Spieler>");
    }
}
