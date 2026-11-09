package de.kuby.kubyplugins.staff;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.staff.StaffScreen.Frame;
import de.kuby.kubyplugins.staff.StaffService.Channel;
import de.kuby.kubyplugins.staff.StaffService.Report;
import de.kuby.kubyplugins.staff.StaffService.ReportStatus;
import de.kuby.kubyplugins.staff.StaffService.Role;
import de.kuby.kubyplugins.support.SupportService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Team-Bereich: /teambereich (/tb, /staff), /sc &lt;Text&gt; (#allgemein, oder /sc #kanal Text) und der Kadmin-Dialog.
 * Ohne Grafik-Pack gibt es eine kurze Übersicht im Chat mit Klicks.
 */
public final class StaffMenus implements CommandExecutor, TabCompleter, Listener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Berlin"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Berlin"));
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();

    private final KubyPlugins plugin;
    private final StaffService staff;
    private final StaffScreen.Nav nav = key -> p -> handle(p, key);
    /** Zuletzt geöffneter Kanal und Meldungs-Filter je Teamler. */
    private final Map<UUID, Channel> lastChannel = new HashMap<>();
    private final Map<UUID, Boolean> showAll = new HashMap<>();

    public StaffMenus(KubyPlugins plugin, StaffService staff) {
        this.plugin = plugin;
        this.staff = staff;
    }

    private boolean pack(Player p) {
        return plugin.uiPack().active(p);
    }

    private void err(Player p, String e) {
        Msg.send(p, "<#F2B829><b>TEAM</b> <dark_gray>▶ <red>" + Msg.escape(e));
        Sfx.play(plugin, p, Sfx.Kind.ERROR);
    }

    private void ok(Player p, String m) {
        Msg.send(p, "<#F2B829><b>TEAM</b> <dark_gray>▶ <#E8E0D8>" + m);
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    private void handle(Player p, String key) {
        if (!staff.isStaff(p)) {
            err(p, "Der Team-Bereich ist nur für das Team.");
            UiDialog.close(p);
            return;
        }
        String[] k = key.split(":", 3);
        String a = k.length > 1 ? k[1] : "", b = k.length > 2 ? k[2] : "";
        switch (k[0]) {
            case "home" -> home(p);
            case "kanal" -> channel(p, a.isEmpty() ? lastChannel.getOrDefault(p.getUniqueId(), Channel.ALLGEMEIN) : Channel.of(a), num(b, 0));
            case "schreiben" -> write(p, Channel.of(a));
            case "stumm" -> {
                staff.toggleMute(p.getUniqueId());
                channel(p, Channel.of(a), 0);
            }
            case "meldungen" -> {
                if (!a.isEmpty()) showAll.put(p.getUniqueId(), a.equals("alle"));
                reports(p, num(b, 0));
            }
            case "meldung" -> report(p, num(a, -1));
            case "uebernehmen" -> {
                Report r = staff.reportById(num(a, -1));
                if (r != null && r.status == ReportStatus.OFFEN) {
                    staff.claim(p, r);
                    Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                }
                report(p, num(a, -1));
            }
            case "erledigt" -> askDone(p, num(a, -1));
            case "oeffnen" -> {
                Report r = staff.reportById(num(a, -1));
                if (r != null) staff.reopen(r);
                report(p, num(a, -1));
            }
            case "ort" -> {
                Report r = staff.reportById(num(a, -1));
                World w = r == null ? null : Bukkit.getWorld(r.world);
                if (w == null) err(p, "Diese Welt gibt es nicht mehr.");
                else {
                    UiDialog.close(p);
                    p.teleportAsync(new Location(w, r.x + 0.5, r.y, r.z + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch()));
                    Sfx.play(plugin, p, Sfx.Kind.CHIME);
                }
            }
            case "gegen" -> {
                UUID id = uuid(a);
                if (id != null) against(p, id);
            }
            case "tickets" -> tickets(p, a.isEmpty() ? null : a, num(b, 0));
            case "ticket" -> plugin.supportMenus().ticket(p, num(a, -1));
            case "spieler" -> players(p, num(a, 0));
            case "akte" -> {
                UUID id = uuid(a);
                if (id != null) akte(p, id);
            }
            case "suchen" -> askSearch(p);
            case "hin", "her", "inv", "ec", "warn", "kick", "adminp" -> playerAction(p, k[0], uuid(a));
            case "kadmin" -> kadmin(p);
            case "k" -> kadminAction(p, a);
            default -> home(p);
        }
    }

    // =================================================================================================================
    // Seiten
    // =================================================================================================================

    Frame frame(Player p) {
        Role r = staff.role(p);
        SupportService s = plugin.support();
        boolean tickets = s != null && s.canHelp(p);
        int team = tickets && s.isTeam(p) ? s.byStatus(SupportService.Status.TEAM).size() : 0;
        return new Frame(r == null ? "Team" : r.label, r == null ? StaffScreen.GRAY : StaffScreen.color(r.color),
                staff.onlineStaff().size(), staff.unreadAll(p), staff.openReports(), team, tickets, r == Role.ADMIN);
    }

    private List<StaffScreen.ChannelRow> channelRows(Player p) {
        List<StaffScreen.ChannelRow> out = new ArrayList<>();
        for (Channel c : Channel.values()) {
            if (staff.canRead(p, c)) out.add(new StaffScreen.ChannelRow(c.key, c.desc, staff.unread(p, c), staff.canWrite(p, c)));
        }
        return out;
    }

    private StaffScreen.Member member(Player o) {
        Role r = staff.role(o);
        return new StaffScreen.Member(o.getName(), r == null ? "Spieler" : r.label, r == null ? StaffScreen.LIGHT : StaffScreen.color(r.color),
                plugin.modes().modeOf(o.getUniqueId()), "akte:" + o.getUniqueId());
    }

    public void home(Player p) {
        if (!staff.isStaff(p)) {
            err(p, "Der Team-Bereich ist nur für das Team.");
            return;
        }
        if (!pack(p)) {
            chat(p);
            return;
        }
        SupportService s = plugin.support();
        int team = s == null ? 0 : s.byStatus(SupportService.Status.TEAM).size();
        int open = s == null ? 0 : s.byStatus(SupportService.Status.OFFEN).size();
        List<StaffScreen.Member> members = new ArrayList<>();
        for (Player o : staff.onlineStaff()) members.add(member(o));
        List<StaffScreen.Feed> feed = new ArrayList<>();
        for (StaffService.Event e : staff.events(20)) feed.add(new StaffScreen.Feed(e.kind(), e.text(), StaffService.ago(e.time())));
        StaffScreen.Home v = new StaffScreen.Home(frame(p), channelRows(p), staff.openReports(), staff.inWork(), team, open, members, feed);
        UiDialog.show(p, StaffScreen.home(v, nav), StaffScreen.HEIGHT);
    }

    private List<StaffScreen.Line> lines(Channel c) {
        List<StaffScreen.Line> out = new ArrayList<>();
        for (StaffService.Message m : staff.messages(c)) {
            Player o = Bukkit.getPlayer(m.author());
            Role r = o == null ? null : staff.role(o);
            out.add(new StaffScreen.Line(StaffService.nameOf(m.author()), r == null ? StaffScreen.GOLD_SOFT : StaffScreen.color(r.color),
                    StaffService.ago(m.time()), m.text()));
        }
        return out;
    }

    public void channel(Player p, Channel c, int offset) {
        if (c == null || !staff.canRead(p, c)) c = Channel.ALLGEMEIN;
        lastChannel.put(p.getUniqueId(), c);
        staff.markRead(p, c);
        List<StaffScreen.Line> all = lines(c);
        if (!pack(p)) {
            Msg.send(p, "<#F2B829><b>#" + c.key + "</b> <gray>" + c.desc);
            for (int i = Math.max(0, all.size() - 10); i < all.size(); i++) {
                StaffScreen.Line l = all.get(i);
                Msg.send(p, "<dark_gray>" + l.ago() + " <" + l.color().asHexString() + ">" + Msg.escape(l.author()) + "<dark_gray>: <#E8E0D8>"
                        + Msg.escape(l.text()));
            }
            if (staff.canWrite(p, c)) Msg.send(p, "<gray>Schreiben: <#F2B829>/sc #" + c.key + " <Text>");
            return;
        }
        offset = Math.max(0, Math.min(offset, Math.max(0, all.size() - 1)));
        List<StaffScreen.Line> shown = all.subList(0, all.size() - offset);
        StaffScreen.ChannelView v = new StaffScreen.ChannelView(frame(p), channelRows(p), c.key, c.desc, staff.canWrite(p, c),
                staff.muted(p.getUniqueId()), new ArrayList<>(shown), offset);
        UiDialog.show(p, StaffScreen.channel(v, nav), StaffScreen.HEIGHT);
    }

    private void write(Player p, Channel c) {
        if (c == null || !staff.canWrite(p, c)) {
            err(p, "Hier darfst du nicht schreiben.");
            return;
        }
        Channel ch = c;
        ask(p, StaffScreen.write(c.key, c.desc, lines(c)), "Nachricht · bis 256 Zeichen", 256, true, "Senden", (pl, text) -> {
            String e = staff.post(pl, ch, text);
            if (e != null) err(pl, e);
            else Sfx.play(plugin, pl, Sfx.Kind.TICK);
            channel(pl, ch, 0);
        }, pl -> channel(pl, ch, 0));
    }

    public void reports(Player p, int page) {
        boolean all = showAll.getOrDefault(p.getUniqueId(), false);
        List<Report> list = staff.reports(!all);
        int per = StaffScreen.REPORTS_PER_PAGE;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        List<StaffScreen.ReportRow> rows = new ArrayList<>();
        for (int i = page * per; i < list.size() && i < (page + 1) * per; i++) rows.add(row(list.get(i)));
        if (!pack(p)) {
            chatReports(p, list);
            return;
        }
        StaffScreen.Reports v = new StaffScreen.Reports(frame(p), !all, rows, page, pages, staff.openReports(), staff.inWork(), staff.totalReports());
        UiDialog.show(p, StaffScreen.reports(v, nav), StaffScreen.HEIGHT);
    }

    private static TextColor statusColor(ReportStatus s) {
        return switch (s) {
            case OFFEN -> StaffScreen.RED;
            case IN_ARBEIT -> StaffScreen.GOLD;
            case ERLEDIGT -> StaffScreen.GREEN;
        };
    }

    private static String statusLabel(Report r) {
        return r.status == ReportStatus.IN_ARBEIT && r.handler != null ? "bei " + StaffService.nameOf(r.handler) : r.status.label;
    }

    private StaffScreen.ReportRow row(Report r) {
        return new StaffScreen.ReportRow(r.id, StaffService.nameOf(r.target), StaffService.nameOf(r.reporter), r.reason,
                statusLabel(r), statusColor(r.status), StaffService.ago(r.time));
    }

    public void report(Player p, int id) {
        Report r = staff.reportById(id);
        if (r == null) {
            err(p, "Diese Meldung gibt es nicht.");
            reports(p, 0);
            return;
        }
        if (!pack(p)) {
            chatReports(p, List.of(r));
            return;
        }
        boolean online = Bukkit.getPlayer(r.target) != null;
        StaffScreen.ReportDetail v = new StaffScreen.ReportDetail(frame(p), r.id, StaffService.nameOf(r.target), online,
                r.target.toString(), StaffService.nameOf(r.reporter), WHEN.format(Instant.ofEpochMilli(r.time)), StaffService.ago(r.time),
                r.world + " · " + r.x + " " + r.y + " " + r.z, r.reason, r.status.label, statusColor(r.status),
                r.handler == null ? null : StaffService.nameOf(r.handler), r.note, staff.reportsAgainst(r.target).size(),
                r.status == ReportStatus.OFFEN, r.status != ReportStatus.ERLEDIGT, r.status == ReportStatus.ERLEDIGT);
        UiDialog.show(p, StaffScreen.report(v, nav), StaffScreen.HEIGHT);
    }

    private void askDone(Player p, int id) {
        Report r = staff.reportById(id);
        if (r == null) return;
        Canvas head = StaffScreen.prompt(KitData.Icon.BELL, "MELDUNG #" + id, "Abschließen · " + StaffService.nameOf(r.target),
                List.of("Grund: " + r.reason, "Kurz notieren, was du gemacht hast (z. B. „verwarnt“, „nichts gefunden“). Darf leer bleiben."));
        ask(p, head, "Notiz · bis 120 Zeichen", 120, false, "Erledigt", (pl, text) -> {
            Report rr = staff.reportById(id);
            if (rr != null) {
                staff.done(pl, rr, text);
                ok(pl, "Meldung #" + id + " ist erledigt.");
            }
            report(pl, id);
        }, pl -> report(pl, id));
    }

    /** Alle Meldungen gegen einen Spieler (Liste „Alle“, gefiltert). */
    private void against(Player p, UUID target) {
        List<Report> list = staff.reportsAgainst(target);
        if (!pack(p)) {
            chatReports(p, list);
            return;
        }
        List<StaffScreen.ReportRow> rows = new ArrayList<>();
        for (int i = 0; i < list.size() && i < StaffScreen.REPORTS_PER_PAGE; i++) rows.add(row(list.get(i)));
        StaffScreen.Reports v = new StaffScreen.Reports(frame(p), false, rows, 0, 1, staff.openReports(), staff.inWork(), staff.totalReports());
        UiDialog.show(p, StaffScreen.reports(v, nav), StaffScreen.HEIGHT);
    }

    public void tickets(Player p, String tab, int page) {
        SupportService s = plugin.support();
        if (s == null || !s.canHelp(p)) {
            err(p, "Tickets sehen Moderatoren, Admins und Ingame-Helfer.");
            home(p);
            return;
        }
        boolean team = s.isTeam(p);
        if (tab == null) tab = team && !s.byStatus(SupportService.Status.TEAM).isEmpty() ? "team" : "offen";
        if (tab.equals("team") && !team) tab = "offen";
        SupportService.Status st = switch (tab) {
            case "team" -> SupportService.Status.TEAM;
            case "arbeit" -> SupportService.Status.IN_ARBEIT;
            default -> SupportService.Status.OFFEN;
        };
        List<SupportService.Ticket> list = s.byStatus(st);
        int per = StaffScreen.TICKETS_PER_PAGE;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        List<StaffScreen.TicketRow> rows = new ArrayList<>();
        for (int i = page * per; i < list.size() && i < (page + 1) * per; i++) {
            SupportService.Ticket t = list.get(i);
            int n = t.messages.size();
            String info = "von " + SupportService.nameOf(t.author) + " · " + (t.helper == null ? "noch kein Helfer" : "Helfer: "
                    + SupportService.nameOf(t.helper)) + " · " + n + (n == 1 ? " Nachricht" : " Nachrichten");
            TextColor col = switch (t.status) {
                case TEAM -> StaffScreen.RED;
                case IN_ARBEIT -> StaffScreen.GOLD;
                default -> StaffScreen.GREEN;
            };
            long last = n == 0 ? t.created : t.messages.get(n - 1).time();
            rows.add(new StaffScreen.TicketRow(t.id, t.title, t.status.label, col, info, StaffService.ago(last)));
        }
        if (!pack(p)) {
            Msg.send(p, "<#F2B829><b>Tickets</b> <gray>(" + st.label + ")");
            if (rows.isEmpty()) Msg.send(p, "<gray>Hier wartet gerade kein Ticket.");
            for (StaffScreen.TicketRow r : rows) Msg.send(p, "<gray>#" + r.id() + " <white>" + Msg.escape(r.title()) + " <dark_gray>· <gray>"
                    + Msg.escape(r.info()) + " <click:run_command:'/support ticket " + r.id() + "'><#F2B829>[Öffnen]</click>");
            return;
        }
        List<StaffScreen.Member> helpers = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!s.canHelp(o)) continue;
            Role r = staff.role(o);
            helpers.add(new StaffScreen.Member(o.getName(), r == null ? "Helfer" : r.label, r == null ? StaffScreen.LIGHT : StaffScreen.color(r.color),
                    String.valueOf(s.pointsOf(o.getUniqueId(), true)), "akte:" + o.getUniqueId()));
        }
        helpers.sort((x, y) -> Integer.parseInt(y.info()) - Integer.parseInt(x.info()));
        StaffScreen.Tickets v = new StaffScreen.Tickets(frame(p), tab, team, s.byStatus(SupportService.Status.TEAM).size(),
                s.byStatus(SupportService.Status.OFFEN).size(), s.byStatus(SupportService.Status.IN_ARBEIT).size(), rows, page, pages, helpers);
        UiDialog.show(p, StaffScreen.tickets(v, nav), StaffScreen.HEIGHT);
    }

    public void players(Player p, int page) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort((x, y) -> {
            int r = staff.openAgainst(y.getUniqueId()) - staff.openAgainst(x.getUniqueId());
            return r != 0 ? r : x.getName().compareToIgnoreCase(y.getName());
        });
        int per = StaffScreen.PLAYERS_PER_PAGE;
        int pages = Math.max(1, (online.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        if (!pack(p)) {
            Msg.send(p, "<#F2B829><b>Spieler online</b> <gray>(" + online.size() + ")");
            StringBuilder sb = new StringBuilder();
            for (Player o : online) sb.append("<click:run_command:'/tb spieler ").append(o.getName()).append("'><white>")
                    .append(o.getName()).append("</click><dark_gray>, ");
            Msg.send(p, sb.toString());
            return;
        }
        List<StaffScreen.PlayerRow> rows = new ArrayList<>();
        for (int i = page * per; i < online.size() && i < (page + 1) * per; i++) {
            Player o = online.get(i);
            Role r = staff.role(o);
            rows.add(new StaffScreen.PlayerRow(o.getName(), r == null ? StaffScreen.WHITE : StaffScreen.color(r.color),
                    plugin.modes().modeOf(o.getUniqueId()), staff.openAgainst(o.getUniqueId()), staff.warns(o.getUniqueId()).size(),
                    "akte:" + o.getUniqueId()));
        }
        UiDialog.show(p, StaffScreen.players(new StaffScreen.Players(frame(p), rows, page, pages, online.size()), nav), StaffScreen.HEIGHT);
    }

    private static String hours(long minutes) {
        long h = minutes / 60, m = minutes % 60;
        return h > 0 ? h + " Std. " + m + " Min." : m + " Min.";
    }

    public void akte(Player p, UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName() == null ? "?" : op.getName();
        Player online = op.getPlayer();
        Role mine = staff.role(p);
        Role theirs = online == null ? null : staff.role(online);
        List<String[]> facts = new ArrayList<>();
        facts.add(new String[]{"Rolle", theirs == null ? "Spieler" : theirs.label});
        facts.add(new String[]{"Modus", plugin.modes().modeOf(id)});
        facts.add(new String[]{"Spielzeit", hours(plugin.playtime().minutesOf(id))});
        facts.add(new String[]{"Coins", de.kuby.kubyplugins.casino.CasinoScreens.num(plugin.economy().balance(id))});
        facts.add(new String[]{"Juwelen", de.kuby.kubyplugins.casino.CasinoScreens.num(plugin.jewels().balance(id))});
        facts.add(new String[]{"Erster Join", op.getFirstPlayed() > 0 ? DAY.format(Instant.ofEpochMilli(op.getFirstPlayed())) : "-"});
        facts.add(new String[]{"Zuletzt", online != null ? "jetzt online" : op.getLastSeen() > 0 ? StaffService.ago(op.getLastSeen()) : "-"});
        List<Report> reps = staff.reportsAgainst(id);
        List<String[]> reports = new ArrayList<>();
        for (Report r : reps) reports.add(new String[]{"#" + r.id + " " + r.reason, StaffService.ago(r.time)});
        List<StaffService.Warn> ws = staff.warns(id);
        List<String[]> warns = new ArrayList<>();
        for (StaffService.Warn w : ws) warns.add(new String[]{w.reason(), StaffService.ago(w.time())});
        String status;
        if (online != null) {
            Location l = online.getLocation();
            status = "online · " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        } else {
            status = op.getLastSeen() > 0 ? "offline · zuletzt " + StaffService.ago(op.getLastSeen()) : "offline";
        }
        if (!pack(p)) {
            Msg.send(p, "<#F2B829><b>" + Msg.escape(name) + "</b> <gray>" + Msg.escape(status));
            for (String[] f : facts) Msg.send(p, "<gray>" + f[0] + ": <white>" + Msg.escape(f[1]));
            Msg.send(p, "<gray>Meldungen: <white>" + reps.size() + " <gray>· Verwarnungen: <white>" + ws.size());
            return;
        }
        boolean mod = mine != null && mine.ordinal() >= Role.MODERATOR.ordinal();
        StaffScreen.Akte v = new StaffScreen.Akte(frame(p), name, id.toString(), online != null, status, facts, reports, reps.size(),
                warns, ws.size(), mod, mine == Role.ADMIN, p.getUniqueId().equals(id));
        UiDialog.show(p, StaffScreen.akte(v, nav), StaffScreen.HEIGHT);
    }

    private void askSearch(Player p) {
        Canvas head = StaffScreen.prompt(KitData.Icon.NAME_TAG, "SPIELER SUCHEN", "Auch Spieler, die gerade offline sind",
                List.of("Genauen Spielernamen eingeben. Gefunden werden alle, die schon einmal auf dem Server waren."));
        ask(p, head, "Spielername", 16, false, "Suchen", (pl, text) -> {
            OfflinePlayer op = text == null || text.isBlank() ? null : Bukkit.getOfflinePlayerIfCached(text.trim());
            if (op == null) {
                err(pl, "Diesen Spieler kennen wir nicht.");
                players(pl, 0);
            } else akte(pl, op.getUniqueId());
        }, pl -> players(pl, 0));
    }

    // =================================================================================================================
    // Aktionen an Spielern
    // =================================================================================================================

    private void playerAction(Player p, String what, UUID id) {
        if (id == null) return;
        Role mine = staff.role(p);
        boolean mod = mine != null && mine.ordinal() >= Role.MODERATOR.ordinal();
        Player t = Bukkit.getPlayer(id);
        String name = StaffService.nameOf(id);
        switch (what) {
            case "hin" -> {
                if (t == null) {
                    err(p, name + " ist nicht online.");
                    return;
                }
                UiDialog.close(p);
                p.teleportAsync(t.getLocation());
                Sfx.play(plugin, p, Sfx.Kind.CHIME);
            }
            case "her" -> {
                if (!mod || t == null) return;
                t.teleportAsync(p.getLocation());
                ok(p, Msg.escape(name) + " ist auf dem Weg zu dir.");
                akte(p, id);
            }
            case "inv" -> {
                if (!mod || t == null) return;
                if (mine == Role.ADMIN) {
                    p.openInventory(t.getInventory());
                } else {
                    List<ItemStack> items = new ArrayList<>();
                    ItemStack[] inv = t.getInventory().getContents();
                    for (int i = 9; i < 36 && i < inv.length; i++) items.add(inv[i]);
                    for (int i = 0; i < 9 && i < inv.length; i++) items.add(inv[i]);
                    ItemStack[] armor = t.getInventory().getArmorContents();
                    for (int i = armor.length - 1; i >= 0; i--) items.add(armor[i]);
                    items.add(t.getInventory().getItemInOffHand());
                    new View(plugin, "Inventar von " + name, items, pl -> akte(pl, id)).open(p);
                }
            }
            case "ec" -> {
                if (!mod) return;
                ItemStack[] stored = plugin.enderchests().exportState(id).items();
                new View(plugin, "Enderkiste von " + name, Arrays.asList(stored), pl -> akte(pl, id)).open(p);
            }
            case "warn" -> {
                if (!mod || p.getUniqueId().equals(id)) return;
                Canvas head = StaffScreen.prompt(KitData.Icon.BELL, "VERWARNEN", name + " bekommt die Verwarnung als Titel und im Chat",
                        List.of("Bisher " + staff.warns(id).size() + " Verwarnungen. Bitte kurz und sachlich, z. B. „Spam im Chat (§3)“."));
                ask(p, head, "Grund · bis 120 Zeichen", 120, false, "Verwarnen", (pl, text) -> {
                    staff.warn(pl, id, text);
                    akte(pl, id);
                }, pl -> akte(pl, id));
            }
            case "kick" -> {
                if (!mod || t == null) return;
                Canvas head = StaffScreen.prompt(KitData.Icon.BARRIER, "KICKEN", name + " wird vom Server geworfen",
                        List.of("Der Grund steht auf dem Bildschirm des Spielers und im Feed des Team-Bereichs."));
                ask(p, head, "Grund · bis 120 Zeichen", 120, false, "Kicken", (pl, text) -> {
                    String e = staff.kick(pl, id, text);
                    if (e != null) err(pl, e);
                    akte(pl, id);
                }, pl -> akte(pl, id));
            }
            case "adminp" -> {
                if (mine != Role.ADMIN) return;
                new de.kuby.kubyplugins.gui.AdminPlayerMenu(plugin, id, name).open(p);
            }
            default -> {
            }
        }
    }

    /** Nur ansehen: Inhalt in einer Truhe, Klicks gesperrt; Pfeil unten führt zurück zur Akte. */
    private static final class View extends GuiMenu {
        private final List<ItemStack> items;
        private final Consumer<Player> back;

        View(KubyPlugins plugin, String title, List<ItemStack> items, Consumer<Player> back) {
            super(plugin, Msg.parse("<dark_gray>" + Msg.escape(title) + " (nur ansehen)"), 6);
            this.items = items;
            this.back = back;
        }

        @Override
        protected boolean fillEmpty() {
            return false;
        }

        @Override
        protected void build(Player player) {
            for (int i = 0; i < items.size() && i < 45; i++) {
                ItemStack it = items.get(i);
                if (it != null && !it.getType().isAir()) set(i, it.clone(), null);
            }
            set(49, Items.make(Material.ARROW, "<gray>Zurück zur Akte", List.of()), e -> back.accept(player));
        }
    }

    // =================================================================================================================
    // Kadmin
    // =================================================================================================================

    public void kadmin(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            err(p, "Keine Berechtigung.");
            return;
        }
        if (!pack(p)) {
            new de.kuby.kubyplugins.gui.AdminMenu(plugin).open(p);
            return;
        }
        List<StaffScreen.Tile> tiles = new ArrayList<>(StaffScreen.coreTiles());
        List<KubyPlugins.AdminButton> addons = plugin.adminButtons();
        for (int i = 0; i < addons.size(); i++) {
            KubyPlugins.AdminButton b = addons.get(i);
            tiles.add(new StaffScreen.Tile("Verwaltung", b.icon(), b.name(), b.desc(), "addon" + i));
        }
        StaffScreen.Kadmin v = new StaffScreen.Kadmin(frame(p), plugin.getPluginMeta().getVersion(), Bukkit.getOnlinePlayers().size(), tiles);
        UiDialog.show(p, StaffScreen.kadmin(v, nav), StaffScreen.HEIGHT);
    }

    private void kadminAction(Player p, String key) {
        if (!p.hasPermission("kuby.admin")) return;
        switch (key) {
            case "truhe" -> new de.kuby.kubyplugins.gui.AdminMenu(plugin).open(p);
            case "speichern" -> {
                plugin.saveAll();
                staff.save();
                ok(p, "Alles gespeichert.");
                kadmin(p);
            }
            case "reload" -> {
                plugin.reloadConfig();
                Msg.setPrefix(plugin.getConfig().getString("prefix", ""));
                ok(p, "Config neu geladen.");
                kadmin(p);
            }
            case "quiz" -> {
                plugin.quiz().ask();
                ok(p, "Quiz gestartet.");
                kadmin(p);
            }
            case "schluessel" -> {
                new de.kuby.kubyplugins.service.KeyAllTimer(plugin).giveAll();
                ok(p, "Universal-Schlüssel verteilt.");
                kadmin(p);
            }
            case "ankuendigung" -> {
                Canvas head = StaffScreen.prompt(KitData.Icon.OAK_SIGN, "ANKÜNDIGUNG", "Geht an alle Spieler online (Chat und Titel)",
                        List.of("Kurz und klar, z. B. „Neustart in 5 Minuten“. Steht auch in #ankündigungen."));
                ask(p, head, "Text · bis 200 Zeichen", 200, true, "Senden", (pl, text) -> {
                    if (text == null || text.isBlank()) {
                        err(pl, "Der Text ist leer.");
                    } else {
                        String t = text.trim();
                        Bukkit.getServer().broadcast(Msg.parse("<#F2B829><b>ANKÜNDIGUNG</b> <dark_gray>▶ <white>" + Msg.escape(t)));
                        for (Player o : Bukkit.getOnlinePlayers()) {
                            o.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<#F2B829><b>Ankündigung"),
                                    Msg.parse("<white>" + Msg.escape(t.length() > 60 ? t.substring(0, 60) + "…" : t))));
                            Sfx.play(plugin, o, Sfx.Kind.CHIME);
                        }
                        staff.post(pl, Channel.ANKUENDIGUNGEN, t);
                    }
                    kadmin(pl);
                }, this::kadmin);
            }
            case "einstellungen" -> new de.kuby.kubyplugins.gui.SettingsMenu(plugin, -1).open(p);
            case "shop" -> new de.kuby.kubyplugins.gui.ShopEditorMenu(plugin).open(p);
            case "preise" -> new de.kuby.kubyplugins.gui.WorthEditorMenu(plugin).open(p);
            case "kits" -> new de.kuby.kubyplugins.gui.ConfigEditorMenu(plugin, "kits", 0).open(p);
            case "battlepass" -> new de.kuby.kubyplugins.gui.BattlepassEditorMenu(plugin, 0).open(p);
            case "casino" -> de.kuby.kubyplugins.service.CasinoAdmin.open(plugin, p);
            case "markt" -> new de.kuby.kubyplugins.gui.AdminMarketMenu(plugin).open(p);
            case "spieler" -> players(p, 0);
            case "teams" -> new de.kuby.kubyplugins.gui.AdminTeamsMenu(plugin).open(p);
            case "config" -> new de.kuby.kubyplugins.gui.ConfigEditorMenu(plugin, "", 0).open(p);
            default -> {
                if (key.startsWith("addon")) {
                    int i = num(key.substring(5), -1);
                    List<KubyPlugins.AdminButton> addons = plugin.adminButtons();
                    if (i >= 0 && i < addons.size()) addons.get(i).action().accept(p);
                }
            }
        }
    }

    // =================================================================================================================
    // Eingaben
    // =================================================================================================================

    /** Canvas-Kopf, ein Textfeld, Knöpfe „ok“ (gold) und „Zurück“; ohne Pack ein einfacher Dialog. */
    private void ask(Player p, Canvas head, String label, int max, boolean multi, String okLabel, BiConsumer<Player, String> onOk,
                     Consumer<Player> onBack) {
        TextDialogInput.Builder in = DialogInput.text("text", Msg.parse("<#ACA48E>" + Msg.escape(label)))
                .width(pack(p) ? 400 : 300).maxLength(max).initial("");
        if (multi) in.multiline(TextDialogInput.MultilineOptions.create(4, 48));
        List<DialogInput> inputs = List.of(in.build());
        DialogAction submit = DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) onOk.accept(pl, response.getText("text"));
        }, ONCE);
        DialogAction back = DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) onBack.accept(pl);
        }, ONCE);
        ActionButton yes = ActionButton.create(Msg.parse("<#F4D46A><b>" + Msg.escape(okLabel) + "</b>"), null, 150, submit);
        ActionButton no = ActionButton.create(Msg.parse("<#ACA48E>‹ Zurück"), null, 150, back);
        if (pack(p)) {
            UiDialog.open(p, UiDialog.dialog(p, head, inputs, List.of(yes, no), 2));
            return;
        }
        DialogBase base = DialogBase.builder(Msg.parse("<#F2B829><b>Team-Bereich"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>" + Msg.escape(label)), 300)))
                .inputs(inputs)
                .build();
        UiDialog.open(p, Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    // =================================================================================================================
    // Ohne Pack: Chat
    // =================================================================================================================

    private void chat(Player p) {
        Role r = staff.role(p);
        Msg.send(p, "<#F2B829><b>TEAM-BEREICH</b> <gray>· " + (r == null ? "" : "<" + r.color + ">" + r.label + " <gray>· ")
                + staff.onlineStaff().size() + " online");
        Msg.send(p, "<gray>Offene Meldungen: <white>" + staff.openReports() + " <gray>· neue Nachrichten: <white>" + staff.unreadAll(p));
        StringBuilder ch = new StringBuilder("<gray>Kanäle: ");
        for (Channel c : Channel.values()) {
            if (!staff.canRead(p, c)) continue;
            int n = staff.unread(p, c);
            ch.append("<click:run_command:'/tb kanal ").append(c.key).append("'><#F2B829>#").append(c.key)
                    .append(n > 0 ? " <white>(" + n + ")" : "").append("</click> ");
        }
        Msg.send(p, ch.toString());
        chatReports(p, staff.reports(true).subList(0, Math.min(5, staff.reports(true).size())));
        Msg.send(p, "<dark_gray>Mit Grafik-Pack gibt es den ganzen Team-Bereich als Fenster. Schreiben: /sc <Text>");
    }

    private void chatReports(Player p, List<Report> list) {
        if (list.isEmpty()) Msg.send(p, "<gray>Keine offenen Meldungen.");
        for (Report r : list) {
            Msg.send(p, "<gray>#" + r.id + " <white>" + Msg.escape(StaffService.nameOf(r.target)) + " <gray>(" + Msg.escape(r.reason) + ") · "
                    + r.status.label + " · " + StaffService.ago(r.time) + " <click:run_command:'/tb ort " + r.id + "'><#F2B829>[Hin]</click>"
                    + (r.status != ReportStatus.ERLEDIGT ? " <click:run_command:'/tb erledigt " + r.id + "'><green>[Erledigt]</click>" : ""));
        }
    }

    // =================================================================================================================
    // Befehle und Join
    // =================================================================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur im Spiel.");
            return true;
        }
        if (!staff.isStaff(p)) {
            err(p, "Der Team-Bereich ist nur für das Team.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("sc")) {
            if (args.length == 0) {
                channel(p, Channel.ALLGEMEIN, 0);
                return true;
            }
            Channel c = Channel.ALLGEMEIN;
            int from = 0;
            if (args[0].startsWith("#")) {
                Channel o = Channel.of(args[0].substring(1));
                if (o == null) {
                    err(p, "Diesen Kanal gibt es nicht. Kanäle: #allgemein, #moderation, #admin, #ankündigungen");
                    return true;
                }
                c = o;
                from = 1;
            }
            String e = staff.post(p, c, String.join(" ", Arrays.copyOfRange(args, from, args.length)));
            if (e != null) err(p, e);
            else Msg.send(p, "<#F2B829><b>#" + c.key + "</b> <dark_gray>▶ <gray>gesendet.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String a1 = args.length > 1 ? args[1] : "";
        switch (sub) {
            case "kanal", "channel" -> channel(p, a1.isEmpty() ? Channel.ALLGEMEIN : Channel.of(a1.replace("#", "")), 0);
            case "meldungen", "reports" -> reports(p, 0);
            case "meldung", "report" -> report(p, num(a1, -1));
            case "ort", "erledigt" -> {
                Report r = staff.reportById(num(a1, -1));
                if (r == null) err(p, "Diese Meldung gibt es nicht.");
                else if (sub.equals("ort")) handle(p, "ort:" + r.id);
                else if (pack(p)) askDone(p, r.id);
                else {
                    staff.done(p, r, "");
                    ok(p, "Meldung #" + r.id + " ist erledigt.");
                }
            }
            case "tickets" -> tickets(p, a1.isEmpty() ? null : a1, 0);
            case "ticket" -> plugin.supportMenus().ticket(p, num(a1, -1));
            case "spieler", "player" -> {
                if (a1.isEmpty()) players(p, 0);
                else {
                    OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(a1);
                    if (op == null) err(p, "Diesen Spieler kennen wir nicht.");
                    else akte(p, op.getUniqueId());
                }
            }
            case "kadmin", "admin" -> kadmin(p);
            default -> home(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player p) || !staff.isStaff(p)) return out;
        if (command.getName().equalsIgnoreCase("sc")) {
            if (args.length == 1 && args[0].startsWith("#")) {
                for (Channel c : Channel.values()) if (staff.canWrite(p, c) && ("#" + c.key).startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("#" + c.key);
            }
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("kanal", "meldungen", "meldung", "tickets", "spieler", "kadmin")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kanal")) {
            for (Channel c : Channel.values()) if (staff.canRead(p, c) && c.key.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(c.key);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spieler")) {
            for (Player o : Bukkit.getOnlinePlayers()) if (o.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(o.getName());
        }
        return out;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) staff.greet(p);
        }, 80L);
    }
}
