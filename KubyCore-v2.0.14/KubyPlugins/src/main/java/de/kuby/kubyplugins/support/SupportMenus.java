package de.kuby.kubyplugins.support;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.support.SupportService.Status;
import de.kuby.kubyplugins.support.SupportService.Ticket;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * /support als Dialog-Menüs im Stil der Hilfe-Seiten: deine Tickets, für Helfer offene und eigene Fälle, fürs Team die
 * weitergegebenen. Ticket-Ansicht mit Verlauf, Antworten, Übernehmen, An Team geben, Gelöst mit Sternen, Schließen.
 */
public final class SupportMenus implements CommandExecutor, Listener {

    private static final int W = 390, HEIGHT = 330;
    private static final TextColor ACCENT = TextColor.color(0xFF6478), GRAY = TextColor.color(0xA0A0AC),
            LIGHT = TextColor.color(0xE4E4EA), WHITE = TextColor.color(0xF4F4F4), GOLD = TextColor.color(0xF8D35A),
            GREEN = TextColor.color(0x6CE86E), CYAN = TextColor.color(0x5FE3C4), ON = TextColor.color(0x201400),
            NAV = TextColor.color(0xC0C4CB);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.of("Europe/Berlin"));

    private final KubyPlugins plugin;
    private final SupportService support;

    public SupportMenus(KubyPlugins plugin, SupportService support) {
        this.plugin = plugin;
        this.support = support;
    }

    private void err(Player p, String e) {
        SupportService.send(p, "<red>" + Msg.escape(e));
        Sfx.play(plugin, p, Sfx.Kind.ERROR);
    }

    private static void head(Canvas c, String title, String sub, Consumer<Player> back, List<Span> right) {
        c.box(0, 0, W, 51, KitData.Box.RED);
        int ix = 13, tx = 41;
        if (back != null) {
            c.backHelp(8, 17);
            c.action(8, 17, 22, 22, back, "Zurück");
            ix = 43;
            tx = 74;
        }
        c.smallIcon(ix, back != null ? 18 : 16, KitData.SmallIcon.H_LIFEBUOY);
        int room = W - tx - 12 - (right == null ? 0 : Canvas.width(right) + 8);
        c.text(tx, 15, Canvas.fit(title, true, room), ACCENT, true);
        c.text(tx, 28, Canvas.fit(sub, false, room), GRAY, false);
        if (right != null) c.textRight(378, 21, right);
    }

    private static int button(Canvas c, int x, int y, String label, boolean action, Consumer<Player> click, String hover) {
        int w = Canvas.width(label, action) - 1 + (action ? 24 : 18);
        c.button(x, y, w, action ? KitData.Button.H_RED : KitData.Button.H_NAV, label, action ? ON : NAV, action);
        if (click != null) c.action(x, y, w, KitData.BTN_H, click, hover);
        return w;
    }

    private static TextColor statusColor(Status s) {
        return switch (s) {
            case OFFEN -> GOLD;
            case IN_ARBEIT -> CYAN;
            case TEAM -> ACCENT;
            case GELOEST -> GREEN;
            case GESCHLOSSEN -> GRAY;
        };
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    public void main(Player p, String tab) {
        boolean helper = support.canHelp(p);
        boolean team = support.isTeam(p);
        Canvas c = new Canvas(W);
        int unread = support.unreadOf(p.getUniqueId());
        head(c, "SUPPORT", "Fragen stellen, Hilfe bekommen", null,
                unread > 0 ? List.of(Span.bold(unread + " neue Antwort" + (unread == 1 ? "" : "en"), GOLD)) : null);
        int y = 53;
        // Reiter
        List<String[]> tabs = new ArrayList<>();
        tabs.add(new String[]{"mine", "Meine Tickets"});
        if (helper) {
            tabs.add(new String[]{"offen", "Offen (" + support.queue(p, "offen").size() + ")"});
            tabs.add(new String[]{"meine", "Meine Fälle (" + support.queue(p, "meine").size() + ")"});
        }
        if (team) tabs.add(new String[]{"team", "Team (" + support.queue(p, "team").size() + ")"});
        int x = 0;
        if (tabs.size() > 1) {
            for (String[] t : tabs) {
                boolean on = t[0].equals(tab);
                int w = Canvas.width(t[1], true) + 12;
                c.smallButton(x, y, w, on ? KitData.Small.GREEN : KitData.Small.DARK, t[1], on ? WHITE : LIGHT, true);
                if (!on) c.action(x, y, w, 16, pl -> main(pl, t[0]), t[1]);
                x += w + 3;
            }
            y += 20;
        }
        List<Ticket> list = tab.equals("mine") ? support.of(p.getUniqueId()) : support.queue(p, tab);
        if (list.isEmpty()) {
            c.box(0, y, W, 43, KitData.Box.RED);
            c.text(9, y + 10, tab.equals("mine") ? "KEINE TICKETS" : "NICHTS ZU TUN", ACCENT, true);
            c.text(12, y + 24, tab.equals("mine") ? "Du hast eine Frage? Schreib ein neues Ticket." : "Gerade wartet hier kein Ticket.",
                    LIGHT, false);
            y += 46;
        }
        for (int i = 0; i < list.size() && i < 6; i++) {
            Ticket t = list.get(i);
            c.box(0, y, W, 34, KitData.Box.RED);
            String who = tab.equals("mine") ? (t.helper == null ? "noch kein Helfer" : "Helfer: " + SupportService.nameOf(t.helper))
                    : "von " + SupportService.nameOf(t.author);
            c.text(10, y + 7, List.of(Span.bold("#" + t.id + " ", GRAY), Span.bold(Canvas.fit(t.title, true, 230), WHITE)));
            c.text(10, y + 20, WHEN.format(Instant.ofEpochMilli(t.created)) + " · " + who + " · " + t.messages.size() + " Nachrichten", GRAY, false);
            c.textRight(380, y + 7, t.unread && tab.equals("mine") ? "neue Antwort" : t.status.label,
                    t.unread && tab.equals("mine") ? GOLD : statusColor(t.status), true);
            c.textRight(380, y + 20, "Öffnen ›", LIGHT, false);
            c.action(0, y, W, 34, pl -> ticket(pl, t.id), "Ticket öffnen");
            y += 37;
        }
        y += 2;
        int bx = button(c, 0, y, "Neues Ticket", true, this::askNew, "Frage stellen");
        bx += 4;
        bx += button(c, bx, y, "Beste Helfer", false, this::top, "Rangliste der Helfer") + 4;
        if (!support.isHelper(p.getUniqueId())) button(c, bx, y, "Helfer werden", false, pl -> becomeHelper(pl), "Selbst helfen");
        UiDialog.show(p, c, HEIGHT);
    }

    private void becomeHelper(Player p) {
        String e = support.becomeHelper(p);
        if (e != null) err(p, e);
        else {
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
            SupportService.send(p, "Du bist jetzt <white>Helfer</white>. Neue Tickets siehst du unter „Offen“, du bekommst eine Nachricht bei jedem neuen Ticket.");
        }
        main(p, support.canHelp(p) ? "offen" : "mine");
    }

    // =================================================================================================================
    // Ticket
    // =================================================================================================================

    public void ticket(Player p, int id) {
        Ticket t = support.get(id);
        boolean author = t != null && t.author.equals(p.getUniqueId());
        if (t == null || (!author && !support.canHelp(p))) {
            err(p, "Dieses Ticket gibt es nicht.");
            return;
        }
        if (author) support.markRead(t);
        boolean mine = p.getUniqueId().equals(t.helper);
        boolean team = support.isTeam(p);
        Canvas c = new Canvas(W);
        head(c, "#" + t.id + " " + t.title.toUpperCase(Locale.ROOT), "von " + SupportService.nameOf(t.author) + " · "
                + WHEN.format(Instant.ofEpochMilli(t.created)), pl -> main(pl, author ? "mine" : "offen"),
                List.of(Span.bold(t.status.label, statusColor(t.status))));
        int y = 53;
        // Verlauf: die letzten Nachrichten, die passen
        List<String[]> lines = new ArrayList<>();
        for (SupportService.Message m : t.messages) {
            String name = m.role().equals("system") ? "" : SupportService.nameOf(m.author());
            String role = switch (m.role()) {
                case "helfer" -> "Helfer";
                case "team" -> "Team";
                case "spieler" -> "";
                default -> "";
            };
            lines.add(new String[]{"H", name, role, WHEN.format(Instant.ofEpochMilli(m.time())), m.role()});
            for (String w : Canvas.wrap(m.text(), W - 30)) lines.add(new String[]{"T", w, m.role()});
        }
        int max = 16;
        if (lines.size() > max) lines = lines.subList(lines.size() - max, lines.size());
        int h = 24 + 11 * lines.size() + 4;
        c.box(0, y, W, h, KitData.Box.RED);
        c.text(9, y + 10, "VERLAUF", ACCENT, true);
        int ly = y + 24;
        for (String[] l : lines) {
            if (l[0].equals("H")) {
                if (l[4].equals("system")) {
                    c.text(12, ly, "• " + l[3], GRAY, false);
                } else {
                    List<Span> s = new ArrayList<>();
                    s.add(Span.bold(l[1], l[4].equals("spieler") ? WHITE : CYAN));
                    if (!l[2].isEmpty()) s.add(Span.of(" · " + l[2], l[4].equals("team") ? ACCENT : CYAN));
                    s.add(Span.of("  " + l[3], GRAY));
                    c.text(12, ly, s);
                }
            } else {
                c.text(l[2].equals("system") ? 22 : 18, ly, l[1], l[2].equals("system") ? GRAY : LIGHT, false);
            }
            ly += 11;
        }
        y += h + 3;
        int bx = 0;
        if (t.status.open()) {
            if (author || mine || team) bx += button(c, bx, y, "Antworten", true, pl -> askReply(pl, id), "Nachricht schreiben") + 4;
            if (!author && support.canHelp(p) && (t.status == Status.OFFEN || (t.status == Status.TEAM && team))) {
                bx += button(c, bx, y, "Übernehmen", !(author || mine || team), pl -> {
                    String e = support.claim(pl, support.get(id));
                    if (e != null) err(pl, e);
                    ticket(pl, id);
                }, "Ich kümmere mich") + 4;
            }
            if (!author && (mine || team) && t.status != Status.TEAM) {
                bx += button(c, bx, y, "An Team geben", false, pl -> {
                    String e = support.escalate(pl, support.get(id));
                    if (e != null) err(pl, e);
                    ticket(pl, id);
                }, "Komme nicht weiter") + 4;
            }
            if (author) bx += button(c, bx, y, "Gelöst", false, pl -> stars(pl, id), "Frage gelöst, Sterne vergeben") + 4;
            if (author || team) button(c, bx, y, "Schließen", false, pl -> {
                String e = support.close(pl, support.get(id));
                if (e != null) err(pl, e);
                ticket(pl, id);
            }, author ? "Ticket zurückziehen" : "Ticket schließen");
        } else if (t.stars > 0) {
            c.text(4, y + 6, List.of(Span.of("Bewertung: ", GRAY), Span.bold("★".repeat(t.stars), GOLD), Span.of("☆".repeat(5 - t.stars), GRAY)));
        }
        UiDialog.show(p, c, HEIGHT);
    }

    private void stars(Player p, int id) {
        Ticket t = support.get(id);
        if (t == null) return;
        Canvas c = new Canvas(W);
        head(c, "WIE GUT WURDE DIR GEHOLFEN?", "Ticket #" + id + (t.helper == null ? "" : " · Helfer: " + SupportService.nameOf(t.helper)),
                pl -> ticket(pl, id), null);
        int y = 53;
        c.box(0, y, W, 60, KitData.Box.RED);
        c.text(9, y + 10, "STERNE VERGEBEN", ACCENT, true);
        int x = 12;
        for (int s = 1; s <= 5; s++) {
            int stars = s;
            String label = "★".repeat(s);
            int w = Canvas.width(label, true) + 16;
            c.button(x, y + 28, w, s >= 4 ? KitData.Button.H_GOLD : KitData.Button.H_NAV, label, s >= 4 ? ON : GOLD, true);
            c.action(x, y + 28, w, KitData.BTN_H, pl -> {
                String e = support.solve(pl, support.get(id), stars);
                if (e != null) err(pl, e);
                else {
                    Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
                    SupportService.send(pl, "Danke! Dein Ticket ist als gelöst markiert.");
                }
                ticket(pl, id);
            }, stars + (stars == 1 ? " Stern" : " Sterne"));
            x += w + 4;
        }
        UiDialog.show(p, c, 160);
    }

    // =================================================================================================================
    // Beste Helfer
    // =================================================================================================================

    public void top(Player p) {
        Canvas c = new Canvas(W);
        head(c, "BESTE HELFER", "Durchschnitt der Sterne, dann gelöste Tickets", pl -> main(pl, "mine"), null);
        int y = 53;
        List<SupportService.HelperStat> top = support.top(10);
        int h = 24 + 11 * Math.max(1, top.size()) + 4;
        c.box(0, y, W, h, KitData.Box.RED);
        c.text(9, y + 10, "RANGLISTE", ACCENT, true);
        if (top.isEmpty()) c.text(12, y + 24, "Noch hat niemand ein Ticket gelöst.", GRAY, false);
        TextColor[] medal = {TextColor.color(0xFFC83A), TextColor.color(0xC8CCD8), TextColor.color(0xE0884A)};
        for (int i = 0; i < top.size(); i++) {
            SupportService.HelperStat s = top.get(i);
            int ly = y + 24 + 11 * i;
            c.text(12, ly, "#" + (i + 1), i < 3 ? medal[i] : GRAY, true);
            c.text(40, ly, SupportService.nameOf(s.id()), WHITE, false);
            String avg = s.ratings() == 0 ? "-" : String.format(Locale.GERMANY, "%.1f", s.average());
            c.textRight(378, ly, List.of(Span.bold(avg + " ★", GOLD), Span.of("  " + s.solved() + " gelöst", GRAY)));
        }
        UiDialog.show(p, c, HEIGHT);
    }

    // =================================================================================================================
    // Eingaben
    // =================================================================================================================

    private void askNew(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<#FF6478><b>Neues Ticket"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>Ein kurzer Titel und deine Frage. Ein Helfer antwortet dir,\n"
                        + "<gray>die Antwort kommt im Chat. Spieler melden oder Strafen: <white>/discord"), 300)))
                .inputs(List.of(DialogInput.text("titel", Msg.parse("<gray>Titel")).width(300).maxLength(40).initial("").build(),
                        DialogInput.text("frage", Msg.parse("<gray>Deine Frage")).width(300).maxLength(500).initial("")
                                .multiline(TextDialogInput.MultilineOptions.create(6, 90)).build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>Absenden</b>"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String e = support.create(pl, response.getText("titel"), response.getText("frage"));
                    if (e != null) err(pl, e);
                    else {
                        Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
                        SupportService.send(pl, "Ticket geschrieben. Sobald ein Helfer antwortet, bekommst du eine Nachricht.");
                    }
                    main(pl, "mine");
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) main(pl, "mine");
                }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    private void askReply(Player p, int id) {
        DialogBase base = DialogBase.builder(Msg.parse("<#FF6478><b>Antworten · #" + id))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .inputs(List.of(DialogInput.text("text", Msg.parse("<gray>Nachricht")).width(300).maxLength(500).initial("")
                        .multiline(TextDialogInput.MultilineOptions.create(6, 90)).build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>Senden</b>"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    Ticket t = support.get(id);
                    String e = t == null ? "Dieses Ticket gibt es nicht mehr." : support.reply(pl, t, response.getText("text"));
                    if (e != null) err(pl, e);
                    ticket(pl, id);
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) ticket(pl, id);
                }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    // =================================================================================================================
    // Befehle und Join
    // =================================================================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (cmd.getName().equalsIgnoreCase("discord")) {
            String link = plugin.getConfig().getString("discord.invite", "https://discord.gg/");
            SupportService.send(p, "Unser Discord: <click:open_url:'" + link + "'><#5865F2><u>" + Msg.escape(link) + "</u></click>");
            Msg.send(p, "<gray>Dort gibt es Neuigkeiten und Tickets für Meldungen an das Team. Verlinken: <white>/verify");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "new", "neu" -> askNew(p);
            case "top" -> top(p);
            case "helper", "helfer" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("remove") && support.isTeam(p)) {
                    var op = org.bukkit.Bukkit.getOfflinePlayerIfCached(args[2]);
                    if (op == null) err(p, "Diesen Spieler kenne ich nicht.");
                    else {
                        support.removeHelper(op.getUniqueId());
                        SupportService.send(p, args[2] + " ist kein Helfer mehr.");
                    }
                } else if (support.isHelper(p.getUniqueId()) && args.length >= 2 && args[1].equalsIgnoreCase("aufhoeren")) {
                    support.leaveHelper(p.getUniqueId());
                    SupportService.send(p, "Du bist kein Helfer mehr.");
                } else {
                    becomeHelper(p);
                }
            }
            case "ticket" -> {
                try {
                    ticket(p, Integer.parseInt(args.length > 1 ? args[1] : ""));
                } catch (NumberFormatException e) {
                    err(p, "/support ticket <Nummer>");
                }
            }
            default -> main(p, support.canHelp(p) && support.queue(p, "meine").size() + support.queue(p, "offen").size() > 0
                    && support.of(p.getUniqueId()).isEmpty() ? "offen" : "mine");
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int unread = support.unreadOf(p.getUniqueId());
            if (unread > 0) SupportService.send(p, "Du hast " + unread + " neue Antwort" + (unread == 1 ? "" : "en")
                    + " im Support. <click:run_command:'/support'><#FF6478>[Öffnen]</click>");
            if (support.canHelp(p)) {
                int open = support.queue(p, "offen").size();
                if (open > 0) SupportService.send(p, open + " Ticket" + (open == 1 ? " wartet" : "s warten") + " auf einen Helfer. "
                        + "<click:run_command:'/support'><#FF6478>[Ansehen]</click>");
            }
        }, 60L);
    }
}
