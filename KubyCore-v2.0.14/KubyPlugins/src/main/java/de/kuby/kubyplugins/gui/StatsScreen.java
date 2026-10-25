package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /stats [Spieler] auf dem Dialog-Kit: Kacheln mit Icon, Bezeichnung und Wert für den Modus, in dem man gerade ist
 * (Survival: Spielzeit, Coins, Level, Job, abgebaut, Tode, Monster, Fische, Quests, Tokens, Grundstücke, Votes;
 * SMP: Kills, Tode, K/D, Trophäen, Liga, XP, Coins, Spielzeit, Votes), auch für Offline-Spieler. Unten die Ranglisten.
 */
public final class StatsScreen {

    private static final int W = 390, HEIGHT = 330, TW = 127, TH = 38;
    private static final TextColor ACCENT = Ui.Theme.GRAY.accent, GRAY = TextColor.color(0xA0A0AC),
            WHITE = TextColor.color(0xF4F4F4), NAV = TextColor.color(0xC0C4CB), GREEN = TextColor.color(0x6CE86E);

    public record Cell(KitData.Icon icon, String label, String value, TextColor color) {}

    /** Ranglisten-Knopf: Beschriftung und Befehl. */
    public record Board(String label, String command) {}

    public record View(String name, String sub, boolean online, List<Cell> cells, List<Board> boards) {}

    private final KubyPlugins plugin;

    public StatsScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private long stat(String key, UUID id) {
        return plugin.hasStat(key) ? plugin.statValue(key, id) : -1;
    }

    private static String n(long v) {
        return Msg.coins(v);
    }

    public void open(Player viewer, OfflinePlayer target) {
        UUID id = target.getUniqueId();
        String mode = plugin.modes().modeOf(viewer.getUniqueId());
        if (ModeService.HUB.equals(mode)) mode = ModeService.SURVIVAL;
        List<Cell> cells = new ArrayList<>();
        TextColor gold = TextColor.color(0xF8D35A), purple = TextColor.color(0xD08CFF), red = TextColor.color(0xFF6A6A),
                cyan = TextColor.color(0x64D8FF), green = TextColor.color(0x5FE3A0);
        List<Board> boards = new ArrayList<>();
        boards.add(new Board("Reichste", "baltop"));
        if (ModeService.SURVIVAL.equals(mode)) {
            cells.add(new Cell(KitData.Icon.CLOCK, "Spielzeit", PlaytimeScreen.duration(plugin.playtime().activeMinutes(id, mode)), WHITE));
            cells.add(new Cell(KitData.Icon.GOLD_NUGGET, "Coins", n(plugin.modes().moneyIn(id, mode)), gold));
            long lvl = stat("player_level", id);
            if (lvl >= 0) cells.add(new Cell(KitData.Icon.EXPERIENCE, "Level", n(lvl), GREEN));
            String job = plugin.textValue("job", id);
            long jl = stat("job_level", id);
            cells.add(new Cell(KitData.Icon.GOLDEN_AXE, "Job", job.isEmpty() ? "keiner" : job + (jl > 0 ? " " + jl : ""), WHITE));
            long mined = stat("blocks_mined", id);
            if (mined >= 0) cells.add(new Cell(KitData.Icon.IRON_PICKAXE, "Abgebaut", n(mined), WHITE));
            long died = stat("deaths_survival", id);
            if (died >= 0) cells.add(new Cell(KitData.Icon.TOTEM, "Gestorben", n(died), red));
            long mobs = stat("mobs_killed", id);
            if (mobs >= 0) cells.add(new Cell(KitData.Icon.SPIDER_EYE, "Monster besiegt", n(mobs), WHITE));
            long fish = stat("fish_caught", id);
            if (fish >= 0) cells.add(new Cell(KitData.Icon.COD, "Fische", n(fish), cyan));
            long quests = stat("quests_done", id);
            if (quests >= 0) cells.add(new Cell(KitData.Icon.WRITTEN_BOOK, "Quests", n(quests), WHITE));
            long tokens = stat("tokens", id);
            if (tokens >= 0) cells.add(new Cell(KitData.Icon.AMETHYST, "Tokens", n(tokens), purple));
            long claims = stat("claims", id);
            if (claims >= 0) cells.add(new Cell(KitData.Icon.MAP, "Grundstücke", n(claims), WHITE));
            cells.add(new Cell(KitData.Icon.PAPER, "Votes", n(stat("votes", id)), green));
            if (plugin.getServer().getPluginCommand("level") != null) boards.add(new Board("Level", "level top"));
            if (plugin.getServer().getPluginCommand("quest") != null) boards.add(new Board("Quests", "quest top"));
            boards.add(new Board("Casino", "casino stats"));
        } else {
            StatsService.Stats s = plugin.stats().get(id);
            cells.add(new Cell(KitData.Icon.DIAMOND, "Kills", n(s.kills), GREEN));
            cells.add(new Cell(KitData.Icon.TOTEM, "Tode", n(s.deaths), red));
            cells.add(new Cell(KitData.Icon.STAR, "K/D", String.format(Locale.GERMANY, "%.2f", s.deaths == 0 ? s.kills : (double) s.kills / s.deaths), WHITE));
            cells.add(new Cell(KitData.Icon.GOLD_INGOT, "Trophäen", n(s.trophies), gold));
            cells.add(new Cell(KitData.Icon.NAME_TAG, "Liga", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(Msg.parse(StatsService.league(s.trophies))), WHITE));
            cells.add(new Cell(KitData.Icon.EXPERIENCE, "XP", n(s.xp), GREEN));
            cells.add(new Cell(KitData.Icon.GOLD_NUGGET, "Coins", n(plugin.modes().moneyIn(id, mode)), gold));
            cells.add(new Cell(KitData.Icon.CLOCK, "Spielzeit", PlaytimeScreen.duration(plugin.playtime().minutesOf(id)), WHITE));
            cells.add(new Cell(KitData.Icon.PAPER, "Votes", n(stat("votes", id)), green));
            boards.add(new Board("Trophäen", "ranking"));
            boards.add(new Board("Casino", "casino stats"));
        }
        String name = target.getName() == null ? "Unbekannt" : target.getName();
        Player on = target.getPlayer();
        boolean online = on != null && viewer.canSee(on);
        String sub = online ? "Gerade online in " + ModeService.label(plugin.modes().modeOf(id))
                : target.getLastSeen() > 0 ? "Zuletzt online " + ago(target.getLastSeen()) : "Noch nie online";
        View v = new View(name, sub, online, cells, boards);
        if (!plugin.uiPack().active(viewer)) {
            Msg.send(viewer, "<gray><b>STATISTIK</b> <dark_gray>▶ <white>" + name + " <gray>(" + ModeService.label(mode) + ")");
            for (Cell c : cells) Msg.send(viewer, "<dark_gray>• <gray>" + c.label() + ": <white>" + Msg.escape(c.value()));
            return;
        }
        UiDialog.show(viewer, canvas(v, cmd -> pl -> {
            pl.closeDialog();
            pl.performCommand(cmd);
        }), HEIGHT);
    }

    private static String ago(long at) {
        long min = (System.currentTimeMillis() - at) / 60_000;
        if (min < 60) return "vor " + Math.max(1, min) + " Min.";
        long h = min / 60;
        if (h < 24) return "vor " + h + (h == 1 ? " Stunde" : " Stunden");
        long d = h / 24;
        return "vor " + d + (d == 1 ? " Tag" : " Tagen");
    }

    /** Nur Darstellung (auch für Vorschauen ohne Server). */
    public static Canvas canvas(View v, java.util.function.Function<String, Consumer<Player>> run) {
        Canvas c = new Canvas(W);
        c.box(0, 0, W, 51, KitData.Box.GRAY);
        c.smallIcon(13, 16, KitData.SmallIcon.H_CHART);
        c.text(41, 15, "STATISTIK", Ui.Theme.GRAY.title, true);
        c.text(41, 28, v.sub(), GRAY, false);
        c.textRight(378, 21, List.of(Span.bold(v.name(), v.online() ? GREEN : WHITE)));
        int y = 53;
        List<Cell> cells = v.cells();
        for (int i = 0; i < cells.size() && i < 15; i++) {
            Cell cell = cells.get(i);
            int x = (i % 3) * (TW + 4), ty = y + (i / 3) * (TH + 3);
            c.box(x, ty, TW, TH, KitData.Box.GRAY);
            c.icon(x + 8, ty + 11, cell.icon(), false);
            c.text(x + 30, ty + 8, cell.label(), GRAY, false);
            c.text(x + 30, ty + 20, Canvas.fit(cell.value(), true, TW - 36), cell.color(), true);
        }
        y += ((Math.min(cells.size(), 15) + 2) / 3) * (TH + 3) + 2;
        c.text(4, y + 1, "RANGLISTEN", ACCENT, true);
        y += 13;
        int x = 0;
        for (Board b : v.boards()) {
            int w = Canvas.width(b.label(), false) - 1 + 18;
            c.button(x, y, w, KitData.Button.H_NAV, b.label(), NAV, false);
            c.action(x, y, w, KitData.BTN_H, run.apply(b.command()), "/" + b.command());
            x += w + 4;
        }
        return c;
    }
}
