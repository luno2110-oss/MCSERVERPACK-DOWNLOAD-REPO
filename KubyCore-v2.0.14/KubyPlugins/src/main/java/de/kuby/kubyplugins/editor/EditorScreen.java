package de.kuby.kubyplugins.editor;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.staff.KadminScreens;
import de.kuby.kubyplugins.staff.KadminScreens.Btn;
import de.kuby.kubyplugins.staff.KadminScreens.Page;
import de.kuby.kubyplugins.staff.KadminScreens.Row;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /kp editor: Übersicht der Kategorien, je Kategorie die Werkzeuge mit „Holen“ (platzierbares Item) oder „Ausführen“.
 * Große Eingriffe (z. B. Skyblock einrichten) brauchen einen zweiten Klick. Ohne Pack: Liste im Chat mit Klick-Befehlen.
 */
public final class EditorScreen {

    private final KubyPlugins plugin;
    /** Werkzeug, das auf den zweiten Klick wartet: Spieler -> kategorie/werkzeug. */
    private final Map<UUID, String> pending = new HashMap<>();

    public EditorScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** /kp editor [kategorie] [werkzeug] */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        if (args.length >= 3) {
            EditorService.Category c = plugin.editor().category(args[1].toLowerCase(Locale.ROOT));
            EditorService.Tool t = c == null ? null : find(c, args[2]);
            if (t == null) {
                Msg.send(p, "<red>Unbekanntes Werkzeug. <gray>/kp editor");
                return;
            }
            t.action().accept(p);
            return;
        }
        if (args.length == 2) {
            EditorService.Category c = plugin.editor().category(args[1].toLowerCase(Locale.ROOT));
            if (c == null) Msg.send(p, "<red>Unbekannte Kategorie. <gray>/kp editor");
            else category(p, c.id(), 0);
            return;
        }
        main(p);
    }

    private static EditorService.Tool find(EditorService.Category c, String id) {
        for (EditorService.Tool t : c.tools().get()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }

    public void main(Player p) {
        pending.remove(p.getUniqueId());
        List<EditorService.Category> cats = plugin.editor().categories();
        if (!plugin.uiPack().active(p)) {
            Msg.send(p, "<gold><b>EDITOR</b> <dark_gray>▶ <gray>Kategorien:");
            for (EditorService.Category c : cats) {
                Msg.send(p, "<dark_gray>• <click:run_command:'/kp editor " + c.id() + "'><yellow>" + Msg.escape(c.name())
                        + "</click> <gray>" + Msg.escape(c.desc()));
            }
            return;
        }
        UiDialog.show(p, KadminScreens.render(mainPage(cats, id -> pl -> category(pl, id, 0))), KadminScreens.HEIGHT);
    }

    public static Page mainPage(List<EditorService.Category> cats, java.util.function.Function<String, java.util.function.Consumer<Player>> open) {
        List<Row> rows = new ArrayList<>();
        for (EditorService.Category c : cats) {
            int n = c.tools().get().size();
            rows.add(new Row(c.icon(), c.name(), null, c.desc(), n + (n == 1 ? " Eintrag" : " Einträge"), null,
                    List.of(new Btn("Öffnen ›", true, open.apply(c.id()), c.name() + " öffnen")), open.apply(c.id()), c.name() + " öffnen"));
        }
        return new Page(KitData.Icon.BRUSH, "Editor", "Kisten, Modelle und Werkzeuge zum Aufbauen", List.of(), rows, 0, 1,
                null, null, null, "Noch keine Kategorien.");
    }

    public void category(Player p, String id, int page) {
        EditorService.Category c = plugin.editor().category(id);
        if (c == null) {
            main(p);
            return;
        }
        List<EditorService.Tool> tools = c.tools().get();
        if (!plugin.uiPack().active(p)) {
            Msg.send(p, "<gold><b>EDITOR</b> <dark_gray>▶ <yellow>" + Msg.escape(c.name()));
            for (EditorService.Tool t : tools) {
                Msg.send(p, "<dark_gray>• <click:run_command:'/kp editor " + c.id() + " " + t.id() + "'><white>" + Msg.escape(t.name())
                        + "</click> <gray>" + Msg.escape(t.desc()));
            }
            return;
        }
        UiDialog.show(p, KadminScreens.render(categoryPage(c, page, pending.get(p.getUniqueId()))), KadminScreens.HEIGHT);
    }

    /** Seite einer Kategorie; {@code wait}: Werkzeug (kategorie/id), das auf „Sicher? Ja!“ wartet. */
    public Page categoryPage(EditorService.Category c, int page, String wait) {
        String id = c.id();
        List<EditorService.Tool> tools = c.tools().get();
        int pages = Math.max(1, (tools.size() + KadminScreens.PER - 1) / KadminScreens.PER);
        int pg = Math.max(0, Math.min(pages - 1, page));
        List<Row> rows = new ArrayList<>();
        for (int i = pg * KadminScreens.PER; i < Math.min(tools.size(), (pg + 1) * KadminScreens.PER); i++) {
            EditorService.Tool t = tools.get(i);
            String key = c.id() + "/" + t.id();
            boolean armed = key.equals(wait);
            Btn b;
            if (t.confirm() && !armed) {
                b = new Btn("Ausführen", false, pl -> {
                    pending.put(pl.getUniqueId(), key);
                    category(pl, id, pg);
                }, "Großer Eingriff: danach noch einmal bestätigen");
            } else {
                b = new Btn(armed ? "Sicher? Ja!" : t.confirm() ? "Ausführen" : "Holen", true, pl -> {
                    pending.remove(pl.getUniqueId());
                    UiDialog.close(pl);
                    t.action().accept(pl);
                }, t.desc());
            }
            rows.add(new Row(t.icon(), t.name(), armed ? net.kyori.adventure.text.format.TextColor.color(0xFF9A4A) : null,
                    t.desc(), null, null, List.of(b), null, t.desc()));
        }
        return new Page(KitData.Icon.BRUSH, "Editor · " + c.name(), c.desc(), List.of(), rows, pg, pages,
                pg > 0 ? pl -> category(pl, id, pg - 1) : null, pg < pages - 1 ? pl -> category(pl, id, pg + 1) : null,
                this::main, "Hier gibt es noch nichts.");
    }
}
