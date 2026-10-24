package de.kuby.kubysurvival.marry;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.casino.CasinoInput;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Öffnet die Menüs für Heiraten und Bank (MarryScreens) mit den Daten aus den Services und führt die Klicks aus. */
public final class MarryMenus {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.of("Europe/Berlin"));

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final MarryService marry;
    private final BankService bank;
    /** Zweimal klicken bei Scheidung, Auflösen, Verlassen, Entfernen und Antrag: was scharf ist und seit wann. */
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();

    public MarryMenus(KubySurvival addon, KubyPlugins core, MarryService marry, BankService bank) {
        this.addon = addon;
        this.core = core;
        this.marry = marry;
        this.bank = bank;
    }

    private void show(Player p, Canvas c) {
        UiDialog.show(p, c, MarryScreens.HEIGHT);
    }

    private boolean survival(Player p) {
        if (ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return true;
        MarryService.send(p, "<red>Das gibt es nur in Survival.");
        return false;
    }

    private void err(Player p, String err) {
        MarryService.send(p, "<red>" + Msg.escape(err));
        Sfx.play(core, p, Sfx.Kind.ERROR);
    }

    private String armedFor(Player p) {
        Long at = armedAt.get(p.getUniqueId());
        if (at == null || System.currentTimeMillis() - at > 6000) return null;
        return armed.get(p.getUniqueId());
    }

    /** true beim zweiten Klick auf dasselbe innerhalb von 6 Sekunden, sonst wird scharf gestellt. */
    private boolean confirm(Player p, String what) {
        if (what.equals(armedFor(p))) {
            armed.remove(p.getUniqueId());
            armedAt.remove(p.getUniqueId());
            return true;
        }
        armed.put(p.getUniqueId(), what);
        armedAt.put(p.getUniqueId(), System.currentTimeMillis());
        Sfx.play(core, p, Sfx.Kind.TICK);
        return false;
    }

    // =================================================================================================================
    // Heiraten
    // =================================================================================================================

    public void marry(Player p) {
        if (!survival(p)) return;
        UUID me = p.getUniqueId();
        UUID other = marry.partnerOf(me);
        String partner = null, sinceText = null, status = null;
        boolean online = false;
        Long balance = null;
        if (other != null) {
            partner = MarryService.name(other);
            long since = marry.since(me);
            long days = Math.max(0, (System.currentTimeMillis() - since) / (24L * 60 * 60 * 1000));
            sinceText = MarryService.date(since) + " · " + (days == 0 ? "seit heute" : days == 1 ? "seit 1 Tag" : "seit " + days + " Tagen");
            Player o = Bukkit.getPlayer(other);
            online = o != null && ModeService.SURVIVAL.equals(core.modes().modeOf(other));
            status = o == null ? "Gerade offline" : online ? "Online in Survival" : "Online, aber nicht in Survival";
            BankService.Account acc = bank.marriage(me);
            if (acc != null) balance = acc.balance;
        }
        MarryService.Proposal in = marry.incomingOf(me), out = marry.outgoingOf(me);
        List<String> candidates = new ArrayList<>();
        if (other == null) {
            for (UUID f : core.friends().friendsOf(me)) {
                Player fp = Bukkit.getPlayer(f);
                if (fp == null || marry.partnerOf(f) != null || marry.blockedUntil(f) > 0) continue;
                if (!ModeService.SURVIVAL.equals(core.modes().modeOf(f))) continue;
                candidates.add(fp.getName());
            }
            candidates.sort(String.CASE_INSENSITIVE_ORDER);
        }
        long blocked = marry.blockedUntil(me);
        MarryScreens.MarryView v = new MarryScreens.MarryView(partner, sinceText, status, online, balance,
                in == null ? null : new MarryScreens.Pending(MarryService.name(in.from()), marry.minutesLeft(in)),
                out == null ? null : new MarryScreens.Pending(MarryService.name(out.to()), marry.minutesLeft(out)),
                candidates, marry.cost(), blocked > 0 ? "Nach deiner Scheidung darfst du ab " + MarryService.date(blocked)
                + " wieder heiraten." : null, armedFor(p));
        show(p, MarryScreens.marry(v, new MarryScreens.MarryActions() {
            @Override
            public Consumer<Player> tp() {
                return pl -> {
                    String e = marry.tp(pl);
                    if (e != null) err(pl, e);
                };
            }

            @Override
            public Consumer<Player> church() {
                return pl -> {
                    String e = marry.toChurch(pl);
                    if (e != null) err(pl, e);
                };
            }

            @Override
            public Consumer<Player> bank() {
                return MarryMenus.this::bank;
            }

            @Override
            public Consumer<Player> divorce() {
                return pl -> {
                    if (confirm(pl, "divorce")) {
                        String e = marry.divorce(pl);
                        if (e != null) err(pl, e);
                    }
                    marry(pl);
                };
            }

            @Override
            public Consumer<Player> deny() {
                return pl -> {
                    String e = marry.deny(pl);
                    if (e != null) err(pl, e);
                    marry(pl);
                };
            }

            @Override
            public Consumer<Player> cancel() {
                return pl -> {
                    String e = marry.cancel(pl);
                    if (e != null) err(pl, e);
                    marry(pl);
                };
            }

            @Override
            public Consumer<Player> propose(String name) {
                return pl -> {
                    if (confirm(pl, "propose:" + name)) {
                        String e = marry.propose(pl, Bukkit.getPlayerExact(name));
                        if (e != null) err(pl, e);
                    }
                    marry(pl);
                };
            }
        }));
    }

    // =================================================================================================================
    // Bank
    // =================================================================================================================

    private MarryScreens.AccountView view(BankService.Account a, UUID me) {
        List<String> members = new ArrayList<>();
        for (UUID m : a.members) members.add(BankService.nameOf(m));
        return new MarryScreens.AccountView(a.id, a.name, a.type == BankService.Type.EHE, a.balance, members,
                BankService.nameOf(a.owner), a.owner.equals(me));
    }

    public void bank(Player p) {
        if (!survival(p)) return;
        UUID me = p.getUniqueId();
        boolean atBank = bank.atBank(p);
        List<MarryScreens.AccountView> list = new ArrayList<>();
        int friends = 0;
        for (BankService.Account a : bank.of(me)) {
            list.add(view(a, me));
            if (a.type == BankService.Type.FREUNDE) friends++;
        }
        UUID partner = marry.partnerOf(me);
        boolean canOpenMarriage = partner != null && bank.marriage(me) == null;
        boolean full = friends >= bank.maxFriendAccounts();
        boolean canCreate = atBank && !full;
        String hint = full ? "Du bist schon in " + bank.maxFriendAccounts() + " Konten mit Freunden."
                : atBank ? "Bis zu " + bank.maxMembers() + " Freunde, alle dürfen ein- und auszahlen"
                : "Nur an der Bank am Spawn";
        MarryScreens.BankView v = new MarryScreens.BankView(core.economy().balance(me), atBank, bank.limitAway(), list,
                canOpenMarriage, canCreate, hint, bank.bankPoint() != null);
        show(p, MarryScreens.bank(v, new MarryScreens.BankActions() {
            @Override
            public Consumer<Player> open(String id) {
                return pl -> account(pl, id);
            }

            @Override
            public Consumer<Player> openMarriage() {
                return pl -> {
                    String e = bank.openMarriage(pl, marry.partnerOf(pl.getUniqueId()));
                    if (e != null) err(pl, e);
                    else {
                        Sfx.play(core, pl, Sfx.Kind.SUCCESS);
                        UUID o = marry.partnerOf(pl.getUniqueId());
                        Player op = o == null ? null : Bukkit.getPlayer(o);
                        if (op != null) MarryService.send(op, "<white>" + pl.getName() + "</white> hat euer Ehe-Konto eröffnet. <white>/bank");
                    }
                    bank(pl);
                };
            }

            @Override
            public Consumer<Player> create() {
                return MarryMenus.this::askName;
            }

            @Override
            public Consumer<Player> toBank() {
                return pl -> {
                    if (bank.bankPoint() == null) {
                        err(pl, "Die Bank ist noch nicht eingerichtet.");
                        return;
                    }
                    pl.closeDialog();
                    de.kuby.kubyplugins.util.Teleports.delayed(core, pl, bank.bankPoint(), "Bank");
                };
            }

            @Override
            public Consumer<Player> marry() {
                return MarryMenus.this::marry;
            }
        }));
    }

    public void account(Player p, String id) {
        if (!survival(p)) return;
        BankService.Account a = bank.get(id);
        UUID me = p.getUniqueId();
        if (a == null || !a.has(me)) {
            err(p, "Dieses Konto gibt es nicht mehr.");
            bank(p);
            return;
        }
        boolean atBank = bank.atBank(p);
        List<MarryScreens.LogLine> log = new ArrayList<>();
        for (BankService.Entry e : a.log) {
            log.add(new MarryScreens.LogLine(WHEN.format(Instant.ofEpochMilli(e.time())), BankService.nameOf(e.who()), e.amount()));
            if (log.size() >= 6) break;
        }
        boolean canManage = a.type == BankService.Type.FREUNDE && a.owner.equals(me) && atBank;
        List<String> addable = new ArrayList<>();
        if (canManage && a.members.size() < bank.maxMembers()) {
            for (UUID f : core.friends().friendsOf(me)) {
                if (a.has(f)) continue;
                OfflinePlayer op = Bukkit.getOfflinePlayer(f);
                if (op.getName() != null) addable.add(op.getName());
            }
            addable.sort(String.CASE_INSENSITIVE_ORDER);
        }
        MarryScreens.AccountDetail d = new MarryScreens.AccountDetail(view(a, me), core.economy().balance(me), atBank,
                bank.limitAway(), log, canManage, addable, armedFor(p));
        show(p, MarryScreens.account(d, new MarryScreens.AccountActions() {
            @Override
            public Consumer<Player> deposit(long amount) {
                return pl -> book(pl, id, amount, true);
            }

            @Override
            public Consumer<Player> withdraw(long amount) {
                return pl -> book(pl, id, amount, false);
            }

            @Override
            public Consumer<Player> add(String name) {
                return pl -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
                    String e = op == null ? "Diesen Spieler kenne ich nicht." : bank.addMember(pl, bank.get(id), op.getUniqueId());
                    if (e != null) err(pl, e);
                    else Sfx.play(core, pl, Sfx.Kind.SUCCESS);
                    account(pl, id);
                };
            }

            @Override
            public Consumer<Player> remove(String name) {
                return pl -> {
                    if (confirm(pl, "remove:" + name)) {
                        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
                        String e = op == null ? "Diesen Spieler kenne ich nicht." : bank.removeMember(pl, bank.get(id), op.getUniqueId());
                        if (e != null) err(pl, e);
                    }
                    account(pl, id);
                };
            }

            @Override
            public Consumer<Player> leave() {
                return pl -> {
                    if (!confirm(pl, "leave")) {
                        account(pl, id);
                        return;
                    }
                    String e = bank.leave(pl, bank.get(id));
                    if (e != null) err(pl, e);
                    else MarryService.send(pl, "Du hast das Konto verlassen.");
                    bank(pl);
                };
            }

            @Override
            public Consumer<Player> close() {
                return pl -> {
                    if (!confirm(pl, "close")) {
                        account(pl, id);
                        return;
                    }
                    String e = bank.close(pl, bank.get(id));
                    if (e != null) {
                        err(pl, e);
                        account(pl, id);
                    } else {
                        MarryService.send(pl, "Konto aufgelöst, das Guthaben wurde verteilt.");
                        bank(pl);
                    }
                };
            }

            @Override
            public Consumer<Player> back() {
                return MarryMenus.this::bank;
            }
        }));
    }

    /** amount &gt; 0 fest, -1 alles (bis zum Limit unterwegs), 0 Betrag eingeben. */
    private void book(Player p, String id, long amount, boolean in) {
        BankService.Account a = bank.get(id);
        if (a == null) {
            err(p, "Dieses Konto gibt es nicht mehr.");
            bank(p);
            return;
        }
        long max = in ? core.economy().balance(p.getUniqueId()) : a.balance;
        if (!bank.atBank(p)) max = Math.min(max, bank.limitAway());
        if (amount == 0) {
            if (max <= 0) {
                err(p, in ? "Du hast keine Coins zum Einzahlen." : "Auf dem Konto ist nichts.");
                account(p, id);
                return;
            }
            CasinoInput.amount(p, in ? "Einzahlen" : "Abheben", List.of("<gray>Wie viele Coins möchtest du "
                            + (in ? "einzahlen" : "abheben") + "?", "<gray>Höchstens <gold>" + Msg.coins(max) + "</gold>."),
                    1, max, in ? "Einzahlen" : "Abheben", (pl, v) -> book(pl, id, v, in), pl -> account(pl, id));
            return;
        }
        long value = amount == -1 ? max : amount;
        if (value <= 0) {
            err(p, in ? "Du hast keine Coins zum Einzahlen." : "Auf dem Konto ist nichts.");
            account(p, id);
            return;
        }
        String e = in ? bank.deposit(p, a, value) : bank.withdraw(p, a, value);
        if (e != null) err(p, e);
        else {
            Sfx.play(core, p, Sfx.Kind.COIN);
            MarryService.send(p, (in ? "Eingezahlt: <gold>" : "Abgehoben: <gold>") + Msg.coins(value) + " Coins</gold> <gray>("
                    + Msg.escape(a.name) + ", Stand <gold>" + Msg.coins(a.balance) + "</gold>)");
        }
        account(p, id);
    }

    /** Name für ein neues Konto mit Freunden (Dialog mit Textfeld). */
    private void askName(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<#F8D35A><b>Neues Konto mit Freunden"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>Wie soll das Konto heißen?\n<gray>3 bis 20 Zeichen, z. B. <white>Farm-Kasse"), 260)))
                .inputs(List.of(DialogInput.text("name", Msg.parse("<gray>Name")).width(200).maxLength(20).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>Eröffnen</b>"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String e = bank.create(pl, response.getText("name"));
                    if (e != null) err(pl, e);
                    else {
                        Sfx.play(core, pl, Sfx.Kind.SUCCESS);
                        MarryService.send(pl, "Konto eröffnet. Freunde nimmst du im Konto auf.");
                    }
                    bank(pl);
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) bank(pl);
                }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }
}
