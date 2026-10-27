package de.kuby.kubyplugins.support;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.support.SupportScreen.Item;
import de.kuby.kubyplugins.support.SupportService.Status;
import de.kuby.kubyplugins.support.SupportService.Ticket;
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
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /support mit Grafik-Pack als Dialoge nach der Referenz (SupportScreen): Übersicht, Neues Ticket, Meine Tickets,
 * Bestenliste, Helfer werden; für Helfer die Ticket-Liste, dazu Ticket mit Verlauf, Antworten, Übernehmen, An Team
 * geben, Gelöst mit Sternen, Schließen. Ohne Pack: Text im Chat und einfache Eingabe-Dialoge.
 */
public final class SupportMenus implements CommandExecutor, Listener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.of("Europe/Berlin"));

    private final KubyPlugins plugin;
    private final SupportService support;
    private final SupportScreen.Actions actions = new SupportScreen.Actions() {
        public Consumer<Player> home() {
            return SupportMenus.this::main;
        }

        public Consumer<Player> ask() {
            return SupportMenus.this::askNew;
        }

        public Consumer<Player> mine() {
            return SupportMenus.this::mine;
        }

        public Consumer<Player> board(boolean week) {
            return pl -> top(pl, week);
        }

        public Consumer<Player> become() {
            return SupportMenus.this::become;
        }

        public Consumer<Player> join() {
            return SupportMenus.this::becomeHelper;
        }

        public Consumer<Player> verify() {
            return pl -> {
                pl.closeDialog();
                pl.performCommand("verify");
            };
        }

        public Consumer<Player> queue(String tab) {
            return pl -> SupportMenus.this.queue(pl, tab);
        }

        public Consumer<Player> ticket(int id) {
            return pl -> SupportMenus.this.ticket(pl, id);
        }

        public Consumer<Player> reply(int id) {
            return pl -> askReply(pl, id);
        }

        public Consumer<Player> claim(int id) {
            return pl -> act(pl, id, t -> support.claim(pl, t));
        }

        public Consumer<Player> escalate(int id) {
            return pl -> act(pl, id, t -> support.escalate(pl, t));
        }

        public Consumer<Player> rate(int id) {
            return pl -> SupportMenus.this.rate(pl, id);
        }

        public Consumer<Player> solve(int id, int stars) {
            return pl -> {
                Ticket t = support.get(id);
                String e = t == null ? "Dieses Ticket gibt es nicht mehr." : support.solve(pl, t, stars);
                if (e != null) err(pl, e);
                else {
                    Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
                    SupportService.send(pl, "Danke! Dein Ticket ist als gelöst markiert.");
                }
                SupportMenus.this.ticket(pl, id);
            };
        }

        public Consumer<Player> close(int id) {
            return pl -> act(pl, id, t -> support.close(pl, t));
        }
    };

    public SupportMenus(KubyPlugins plugin, SupportService support) {
        this.plugin = plugin;
        this.support = support;
    }

    private void err(Player p, String e) {
        SupportService.send(p, "<red>" + Msg.escape(e));
        Sfx.play(plugin, p, Sfx.Kind.ERROR);
    }

    private boolean pack(Player p) {
        return plugin.uiPack().active(p);
    }

    /** Führt eine Ticket-Aktion aus (Fehler in den Chat) und zeigt das Ticket wieder. */
    private void act(Player p, int id, java.util.function.Function<Ticket, String> action) {
        Ticket t = support.get(id);
        String e = t == null ? "Dieses Ticket gibt es nicht mehr." : action.apply(t);
        if (e != null) err(p, e);
        ticket(p, id);
    }

    private Item item(Ticket t, boolean forAuthor) {
        int n = t.messages.size();
        String who = forAuthor ? (t.helper == null ? "noch kein Helfer" : "Helfer: " + SupportService.nameOf(t.helper))
                : "von " + SupportService.nameOf(t.author);
        String info = WHEN.format(Instant.ofEpochMilli(t.created)) + " · " + who + " · " + n + (n == 1 ? " Nachricht" : " Nachrichten");
        boolean fresh = forAuthor && t.unread;
        return new Item(t.id, t.title, fresh ? "neue Antwort" : t.status.label, SupportScreen.stateColor(t.status), info, fresh);
    }

    private String discordUrl() {
        String url = plugin.getConfig().getString("support.discord-ticket", "");
        return url == null || url.isBlank() ? plugin.getConfig().getString("discord.invite", "") : url;
    }

    // =================================================================================================================
    // Seiten
    // =================================================================================================================

    public void main(Player p) {
        if (!pack(p)) {
            chat(p);
            return;
        }
        UUID me = p.getUniqueId();
        List<Ticket> own = support.of(me);
        List<Item> latest = new ArrayList<>();
        for (int i = 0; i < own.size() && i < 3; i++) latest.add(item(own.get(i), true));
        SupportScreen.Home h = new SupportScreen.Home(support.openCount(me), support.maxOpen(), latest, own.size(),
                support.unreadOf(me), support.canHelp(p), support.queue(p, "offen").size(), support.helperHours(), discordUrl());
        UiDialog.show(p, SupportScreen.home(h, actions), SupportScreen.HEIGHT);
    }

    public void mine(Player p) {
        if (!pack(p)) {
            chat(p);
            return;
        }
        List<Item> items = new ArrayList<>();
        for (Ticket t : support.of(p.getUniqueId())) items.add(item(t, true));
        UiDialog.show(p, SupportScreen.mine(support.openCount(p.getUniqueId()), support.maxOpen(), items, actions), SupportScreen.HEIGHT);
    }

    public void queue(Player p, String tab) {
        if (!support.canHelp(p)) {
            become(p);
            return;
        }
        boolean team = support.isTeam(p);
        if (tab.equals("team") && !team) tab = "offen";
        List<Item> items = new ArrayList<>();
        for (Ticket t : support.queue(p, tab)) items.add(item(t, false));
        int[] counts = {support.queue(p, "offen").size(), support.queue(p, "meine").size(), team ? support.queue(p, "team").size() : 0};
        if (!pack(p)) {
            SupportService.send(p, items.isEmpty() ? "Gerade wartet hier kein Ticket." : items.size() + " Ticket(s):");
            for (Item it : items) Msg.send(p, "<gray>#" + it.id() + " <white>" + Msg.escape(it.title()) + " <dark_gray>· <gray>"
                    + Msg.escape(it.info()) + " <click:run_command:'/support ticket " + it.id() + "'><#B7A6FC>[Öffnen]</click>");
            return;
        }
        UiDialog.show(p, SupportScreen.queue(new SupportScreen.Queue(tab, team, counts, items), actions), SupportScreen.HEIGHT);
    }

    public void become(Player p) {
        UUID id = p.getUniqueId();
        SupportScreen.Become b = new SupportScreen.Become(plugin.playtime().minutesOf(id), support.helperHours(),
                support.discordOk(id), support.isHelper(id));
        if (!pack(p)) {
            SupportService.send(p, "Helfer werden: " + support.helperHours() + " Stunden Spielzeit (du: " + b.minutes() / 60
                    + ") und Discord verlinkt. <click:run_command:'/support helfer'><#B7A6FC>[Jetzt Helfer werden]</click>");
            return;
        }
        UiDialog.show(p, SupportScreen.become(b, actions), SupportScreen.HEIGHT);
    }

    public void top(Player p, boolean week) {
        List<SupportService.Score> scores = support.ranking(week, 10);
        List<SupportScreen.Place> places = new ArrayList<>();
        for (SupportService.Score s : scores) places.add(new SupportScreen.Place(SupportService.nameOf(s.id()), s.points()));
        UUID me = p.getUniqueId();
        String footer;
        if (!support.canHelp(p)) footer = "Du bist kein Ingame-Helfer.";
        else {
            int pts = support.pointsOf(me, week);
            int rank = 0;
            for (int i = 0; i < scores.size(); i++) if (scores.get(i).id().equals(me)) rank = i + 1;
            footer = (week ? "Diese Woche: " : "Seit dem Start: ") + pts + (pts == 1 ? " Punkt" : " Punkte")
                    + (rank > 0 ? " · Platz " + rank : "");
        }
        if (!pack(p)) {
            SupportService.send(p, week ? "Bestenliste dieser Woche:" : "Bestenliste seit dem Start:");
            for (int i = 0; i < places.size(); i++) {
                Msg.send(p, "<gray>#" + (i + 1) + " <white>" + places.get(i).name() + " <gold>" + places.get(i).points() + " Punkte");
            }
            if (places.isEmpty()) Msg.send(p, "<gray>Noch hat niemand ein Ticket gelöst.");
            return;
        }
        UiDialog.show(p, SupportScreen.board(new SupportScreen.Board(week, places, footer), actions), SupportScreen.HEIGHT);
    }

    private void becomeHelper(Player p) {
        String e = support.becomeHelper(p);
        if (e != null) {
            err(p, e);
            become(p);
            return;
        }
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
        SupportService.send(p, "Du bist jetzt <white>Helfer</white>. Neue Tickets siehst du unter „Helfen“, du bekommst eine Nachricht bei jedem neuen Ticket.");
        queue(p, "offen");
    }

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
        List<SupportScreen.Line> lines = new ArrayList<>();
        for (SupportService.Message m : t.messages) {
            String name = m.role().equals("system") ? m.text() : SupportService.nameOf(m.author());
            lines.add(new SupportScreen.Line(name, m.role(), WHEN.format(Instant.ofEpochMilli(m.time())), m.text()));
        }
        boolean open = t.status.open();
        SupportScreen.Detail d = new SupportScreen.Detail(t.id, t.title, SupportService.nameOf(t.author),
                WHEN.format(Instant.ofEpochMilli(t.created)), t.status.label, SupportScreen.stateColor(t.status), author, lines,
                t.stars,
                open && (author || mine || team),
                open && !author && support.canHelp(p) && (t.status == Status.OFFEN || (t.status == Status.TEAM && team)),
                open && !author && (mine || team) && t.status != Status.TEAM,
                open && author,
                open && (author || team));
        if (!pack(p)) {
            SupportService.send(p, "Ticket <white>#" + t.id + " " + Msg.escape(t.title) + "</white> · " + t.status.label);
            for (SupportScreen.Line l : lines) {
                Msg.send(p, l.role().equals("system") ? "<dark_gray>• " + Msg.escape(l.text())
                        : "<white>" + l.name() + "<gray>: " + Msg.escape(l.text()));
            }
            if (d.reply()) Msg.send(p, "<click:run_command:'/support antwort " + t.id + "'><#B7A6FC>[Antworten]</click>");
            return;
        }
        UiDialog.show(p, SupportScreen.ticket(d, actions), SupportScreen.HEIGHT);
    }

    private void rate(Player p, int id) {
        Ticket t = support.get(id);
        if (t == null) return;
        UiDialog.show(p, SupportScreen.rate(id, t.helper == null ? null : SupportService.nameOf(t.helper), actions), SupportScreen.HEIGHT);
    }

    /** Ohne Pack: deine Tickets im Chat mit Klicks. */
    private void chat(Player p) {
        List<Ticket> own = support.of(p.getUniqueId());
        SupportService.send(p, own.isEmpty() ? "Du hast noch kein Ticket." : "Deine Tickets:");
        for (int i = 0; i < own.size() && i < 6; i++) {
            Ticket t = own.get(i);
            Msg.send(p, "<gray>#" + t.id + " <white>" + Msg.escape(t.title) + " <dark_gray>· <gray>" + (t.unread ? "<gold>neue Antwort" : t.status.label)
                    + " <click:run_command:'/support ticket " + t.id + "'><#B7A6FC>[Öffnen]</click>");
        }
        Msg.send(p, "<click:run_command:'/support new'><#B7A6FC>[Neues Ticket]</click> <click:run_command:'/support top'><gold>[Bestenliste]</click>"
                + (support.canHelp(p) ? " <click:run_command:'/support helfen'><green>[Helfen]</click>" : " <click:run_command:'/support helfer'><green>[Helfer werden]</click>"));
    }

    // =================================================================================================================
    // Eingaben
    // =================================================================================================================

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();

    private void askNew(Player p) {
        String title = "Titel · kurz, worum es geht (bis 40 Zeichen)", question = "Deine Frage · so genau wie möglich (bis 200 Zeichen)";
        DialogAction submit = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String e = support.create(pl, response.getText("titel"), response.getText("frage"));
            if (e != null) err(pl, e);
            else {
                Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
                SupportService.send(pl, "Ticket geschrieben. Sobald ein Helfer antwortet, bekommst du eine Nachricht.");
            }
            mine(pl);
        }, ONCE);
        if (!pack(p)) {
            plain(p, "<#B7A6FC><b>Neues Ticket", "<gray>Ein kurzer Titel und deine Frage. Spieler melden oder Strafen: <white>/discord",
                    List.of(field("titel", title, 300, 40, false), field("frage", question, 300, 200, true)), "Absenden", submit,
                    SupportMenus.this::main);
            return;
        }
        List<DialogInput> inputs = List.of(field("titel", title, 400, 40, false), field("frage", question, 400, 200, true));
        ActionButton send = ActionButton.create(UiDialog.plate(KitData.Sprite.SU_PLATE_PURPLE, KitData.Sprite.SU_PENCIL,
                "Abschicken", SupportScreen.ON), null, 199, submit);
        ActionButton back = ActionButton.create(UiDialog.plate(KitData.Sprite.SU_PLATE_GRAY, null, "‹ Zurück", SupportScreen.ON),
                null, 199, DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) main(pl);
                }, ONCE));
        p.showDialog(UiDialog.dialog(p, SupportScreen.ask(actions), inputs, List.of(send, back), 2));
    }

    private void askReply(Player p, int id) {
        Ticket t = support.get(id);
        if (t == null) return;
        DialogAction submit = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            Ticket tk = support.get(id);
            String e = tk == null ? "Dieses Ticket gibt es nicht mehr." : support.reply(pl, tk, response.getText("text"));
            if (e != null) err(pl, e);
            ticket(pl, id);
        }, ONCE);
        String label = "Nachricht · bis 200 Zeichen";
        if (!pack(p)) {
            plain(p, "<#B7A6FC><b>Antworten · #" + id, null, List.of(field("text", label, 300, 200, true)), "Senden", submit,
                    pl -> ticket(pl, id));
            return;
        }
        ActionButton send = ActionButton.create(UiDialog.plate(KitData.Sprite.SU_PLATE_PURPLE, KitData.Sprite.SU_PENCIL,
                "Senden", SupportScreen.ON), null, 199, submit);
        ActionButton back = ActionButton.create(UiDialog.plate(KitData.Sprite.SU_PLATE_GRAY, null, "‹ Zurück", SupportScreen.ON),
                null, 199, DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) ticket(pl, id);
                }, ONCE));
        p.showDialog(UiDialog.dialog(p, SupportScreen.reply(id, t.title, t.author.equals(p.getUniqueId()), actions),
                List.of(field("text", label, 400, 200, true)), List.of(send, back), 2));
    }

    /** Eingabefeld mit grauer Beschriftung; mehrzeilig 52 px hoch wie im Original. */
    private static DialogInput field(String key, String label, int width, int max, boolean multi) {
        TextDialogInput.Builder b = DialogInput.text(key, Msg.parse("<#9FA1A7>" + Msg.escape(de.kuby.kubyplugins.util.I18n.ctx(label))))
                .width(width).maxLength(max).initial("");
        if (multi) b.multiline(TextDialogInput.MultilineOptions.create(5, 52));
        return b.build();
    }

    /** Ohne Pack: einfacher Bestätigungs-Dialog mit Feldern. */
    private void plain(Player p, String title, String text, List<DialogInput> inputs, String yes, DialogAction submit, Consumer<Player> cancel) {
        DialogBase.Builder base = DialogBase.builder(Msg.parse(title))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .inputs(inputs);
        if (text != null) base.body(List.of(DialogBody.plainMessage(Msg.parse(text), 300)));
        ActionButton ok = ActionButton.create(Msg.parse("<green><b>" + yes + "</b>"), null, 100, submit);
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) cancel.accept(pl);
                }, ONCE));
        DialogBase built = base.build();
        p.showDialog(Dialog.create(b -> b.empty().base(built).type(DialogType.confirmation(ok, no))));
    }

    // =================================================================================================================
    // Befehle und Join
    // =================================================================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (cmd.getName().equalsIgnoreCase("discord")) {
            plugin.discordStatue().sendInfo(p);
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "new", "neu" -> askNew(p);
            case "top" -> top(p, true);
            case "meine", "mine" -> mine(p);
            case "helfen", "queue" -> queue(p, "offen");
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
                } else if (pack(p) && args.length == 1) {
                    become(p);
                } else {
                    becomeHelper(p);
                }
            }
            case "ticket", "antwort" -> {
                try {
                    int id = Integer.parseInt(args.length > 1 ? args[1] : "");
                    if (sub.equals("antwort")) askReply(p, id);
                    else ticket(p, id);
                } catch (NumberFormatException e) {
                    err(p, "/support ticket <Nummer>");
                }
            }
            default -> main(p);
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
                    + " im Support. <click:run_command:'/support meine'><#FF6478>[Öffnen]</click>");
            if (support.canHelp(p)) {
                int open = support.queue(p, "offen").size();
                if (open > 0) SupportService.send(p, open + " Ticket" + (open == 1 ? " wartet" : "s warten") + " auf einen Helfer. "
                        + "<click:run_command:'/support helfen'><#FF6478>[Ansehen]</click>");
            }
        }, 60L);
    }
}
