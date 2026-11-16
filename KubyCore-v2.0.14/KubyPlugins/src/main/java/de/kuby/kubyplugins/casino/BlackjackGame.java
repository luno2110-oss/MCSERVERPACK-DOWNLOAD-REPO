package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoFx;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Eine Blackjack-Runde an einem Tisch mit fünf Plätzen. Hinsetzen, Chips aus dem Rack aufs Feld legen; der erste
 * Einsatz startet den Countdown. Dann wird ausgeteilt (Dealer: eine Karte offen, eine verdeckt), jeder Platz ist
 * nacheinander dran (Karte, Halten, Doppeln, Teilen, mit Zeitbalken), danach zieht der Dealer bis 17 und es wird
 * abgerechnet. Blackjack zahlt 3:2, Gewinn 1:1, Unentschieden gibt den Einsatz zurück.
 */
public final class BlackjackGame {

    public static final long[] RACK = {10, 50, 100, 500, 1000, 5000};
    /** Chips am weinroten High-Roller-Tisch. */
    public static final long[] RACK_HR = {500, 1000, 5000, 10000, 50000, 100000};
    /** "blackjack" oder "blackjack_hr" (High-Roller): Serie, Limits und Abrechnung je Tischart. */
    final String GAME;

    private enum Phase { IDLE, BETTING, DEALING, PLAYING, DEALER, SETTLE }

    private static final class Hand {
        final List<BjCards.Card> cards = new ArrayList<>();
        long bet;
        boolean done, doubled, split;
        int index;
    }

    private static final class Seat {
        UUID player;
        String name;
        final List<Long> stack = new ArrayList<>();
        final List<Long> lastStack = new ArrayList<>();
        long[] rackValues = RACK;
        boolean jewels;
        long bet;
        final List<Hand> hands = new ArrayList<>();
        int active;
        boolean playing;
    }

    private final KubyPlugins plugin;
    public final BlackjackTable table;
    public final String name;
    private final BjCards.Shoe shoe = new BjCards.Shoe(6);
    private final Seat[] seats = new Seat[BlackjackTable.SEATS];
    private final List<BjCards.Card> dealer = new ArrayList<>();
    private ItemDisplay holeCard;
    private Phase phase = Phase.IDLE;
    private int countdown;
    private int turn = -1;
    private int turnTime;
    private int turnMax;

    public BlackjackGame(KubyPlugins plugin, BlackjackTable table, String name) {
        this.plugin = plugin;
        this.table = table;
        this.name = name;
        this.GAME = "blackjack" + table.tier().suffix;
        for (int i = 0; i < seats.length; i++) seats[i] = new Seat();
    }

    // =================================================================================================================
    // Plätze
    // =================================================================================================================

    public int seatOf(UUID id) {
        for (int i = 0; i < seats.length; i++) if (id.equals(seats[i].player)) return i;
        return -1;
    }

    public boolean free(int seat) {
        return seats[seat].player == null;
    }

    private boolean usable(int seat) {
        return free(seat) && !seats[seat].playing;
    }

    public int freeSeatNear(Location l) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < seats.length; i++) {
            if (!usable(i)) continue;
            double d = table.seatLocation(i).distanceSquared(l);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    public void sit(Player p, int seat) {
        if (!free(seat)) {
            CasinoText.error(p, "Der Platz ist besetzt.");
            return;
        }
        if (seats[seat].playing) {
            CasinoText.error(p, "Hier wird gerade noch abgerechnet. Gleich ist der Platz frei.");
            return;
        }
        Seat s = seats[seat];
        s.player = p.getUniqueId();
        s.name = p.getName();
        table.seatVehicle(seat).addPassenger(p);
        table.seatText(seat, Component.empty());
        p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.7f, 1.2f);
        if (phase == Phase.IDLE || phase == Phase.BETTING) {
            showRack(seat);
            p.sendActionBar(Msg.parse("<gray>Du sitzt am <white>" + name + "</white>. Leg Chips aus dem Rack auf dein Feld."
                    + (plugin.casinoPlay().golden() ? " <gold>✦ Goldene Zeit: Gewinne x" + de.kuby.kubyplugins.service.CasinoPlay.fmt(plugin.casinoPlay().goldenFactor()) : "")));
            int st = plugin.casinoPlay().streak(p.getUniqueId());
            if (st >= 1) table.streak(seat, Msg.parse("<#FF8A3D>🔥 Serie " + st + " <gray>· nächster Sieg <white>x"
                    + de.kuby.kubyplugins.service.CasinoPlay.fmt(plugin.casinoPlay().streakMult(GAME, st))));
        } else {
            p.sendActionBar(Msg.parse("<gray>Du sitzt am <white>" + name + "</white>. Die nächste Runde beginnt gleich."));
        }
    }

    /** Aufstehen (auch Ausloggen, Tod, Weggehen). Laufende Hände werden zu Ende gespielt wie Halten. */
    public void leave(UUID id, boolean message) {
        int seat = seatOf(id);
        if (seat < 0) return;
        Seat s = seats[seat];
        if (phase == Phase.IDLE || phase == Phase.BETTING) {
            if (s.bet > 0) plugin.casinoPlay().refund(id, GAME, s.bet);
            s.bet = 0;
            s.stack.clear();
            table.bet(seat, s.stack, 0);
        } else if (s.playing) {
            for (Hand h : s.hands) h.done = true;
            if (turn == seat) {
                table.clearActions(seat);
                next();
            }
        }
        table.clearRack(seat);
        table.clearControls(seat);
        table.streak(seat, Component.empty());
        table.removeVehicle(seat);
        s.player = null;
        s.name = null;
        if (!s.playing) clearSeat(seat);
        freeSeatText(seat);
        Player p = Bukkit.getPlayer(id);
        if (p != null && message) {
            CasinoText.send(p, phase == Phase.IDLE || phase == Phase.BETTING ? "Du bist aufgestanden."
                    : "Du bist aufgestanden. Die Runde wird fertig abgerechnet.");
        }
        if (phase == Phase.BETTING && totalBets() == 0) {
            phase = Phase.IDLE;
            idleText();
        }
        save();
    }

    private void freeSeatText(int seat) {
        table.seatText(seat, Component.text().append(Component.text("Freier Platz", TextColor.color(0xF2B829)))
                .append(Component.newline()).append(Component.text("Rechtsklick zum Hinsetzen", NamedTextColor.GRAY)).build());
    }

    private void idleText() {
        table.dealerText(Component.text().append(Component.text("Dealer", TextColor.color(0xF2B829)).decorate(TextDecoration.BOLD))
                .append(Component.newline()).append(Component.text("Rechtsklick · Spielen", NamedTextColor.GRAY)).build());
    }

    /** Nach dem Aufbau: Texte setzen, sitzende Spieler wieder auf die Hocker. */
    public void refreshDisplays() {
        idleText();
        for (int i = 0; i < seats.length; i++) {
            if (free(i)) freeSeatText(i);
            else {
                Player p = Bukkit.getPlayer(seats[i].player);
                if (p != null) table.seatVehicle(i).addPassenger(p);
                if (phase == Phase.IDLE || phase == Phase.BETTING) showRack(i);
                table.bet(i, seats[i].stack, seats[i].bet, seats[i].jewels);
            }
        }
    }

    // =================================================================================================================
    // Einsätze
    // =================================================================================================================

    private long maxBet() {
        return table.tier().maxBet(plugin);
    }

    private long minBet() {
        return table.tier().minBet(plugin);
    }

    private long totalBets() {
        long t = 0;
        for (Seat s : seats) t += s.bet;
        return t;
    }

    public void chip(Player p, int seat, int index) {
        if (seats[seat].player == null || !seats[seat].player.equals(p.getUniqueId())) return;
        if (phase != Phase.IDLE && phase != Phase.BETTING) {
            CasinoText.error(p, "Die Runde läuft schon.");
            return;
        }
        String closed = plugin.casino().closedMessage();
        if (closed != null) {
            CasinoText.error(p, closed);
            return;
        }
        Seat s = seats[seat];
        if (index < 0 || index >= s.rackValues.length) return;
        long v = s.rackValues[index];
        if (!s.jewels && s.bet + v > plugin.casinoPlay().maxFor(p.getUniqueId(), maxBet())) {
            CasinoText.error(p, "Höchsteinsatz " + CasinoText.num(plugin.casinoPlay().maxFor(p.getUniqueId(), maxBet())) + " Chips.");
            return;
        }
        String err = plugin.casinoPlay().take(p, GAME, v);
        if (err != null) {
            CasinoText.error(p, err);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
            return;
        }
        s.bet += v;
        s.stack.add(v);
        table.flyChip(seat, index, s.rackValues.length, v, s.jewels);
        later(5, () -> table.bet(seat, s.stack, s.bet, s.jewels));
        chipSound(table.field(seat), s.stack.size());
        showControls(seat);
        if (phase == Phase.IDLE) {
            phase = Phase.BETTING;
            countdown = (int) plugin.getConfig().getLong("casino.blackjack.bet-seconds", 8);
        } else if (countdown < 3) {
            countdown = 3;
        }
        save();
    }

    // =================================================================================================================
    // Ablauf
    // =================================================================================================================

    /** Jede Sekunde vom Dienst aufgerufen. */
    public void tick() {
        if (phase == Phase.BETTING && table.dealer() != null) {
            List<org.bukkit.Location> faces = new ArrayList<>();
            for (Seat st : seats) {
                Player sp = st.player == null ? null : Bukkit.getPlayer(st.player);
                if (sp != null) faces.add(sp.getEyeLocation());
            }
            table.dealer().idle(faces);
        }
        if (phase == Phase.BETTING) {
            if (countdown > 0) {
                table.dealerText(Component.text().append(Component.text("Runde beginnt in ", NamedTextColor.GRAY))
                        .append(Component.text(countdown + " s", NamedTextColor.WHITE)).build());
                for (int i = 0; i < seats.length; i++) {
                    Player p = seats[i].player == null ? null : Bukkit.getPlayer(seats[i].player);
                    if (p == null) continue;
                    p.sendActionBar(Msg.parse(seats[i].bet > 0
                            ? "<gray>Einsatz <#F2C94C>" + CasinoText.chips(seats[i].bet) + "</#F2C94C> · Runde beginnt in <white>" + countdown + " s"
                            : "<gray>Leg Chips aufs Feld · Runde beginnt in <white>" + countdown + " s"));
                }
                if (countdown <= 3) playAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.4f);
                countdown--;
                return;
            }
            if (totalBets() < minBet()) {
                phase = Phase.IDLE;
                idleText();
                return;
            }
            deal();
        } else if (phase == Phase.PLAYING && turn >= 0) {
            turnTime--;
            table.timer(turn, Math.max(0, (double) turnTime / turnMax));
            Player p = seats[turn].player == null ? null : Bukkit.getPlayer(seats[turn].player);
            if (p != null && turnTime > 0) {
                Hand cur = seats[turn].hands.get(seats[turn].active);
                p.sendActionBar(Msg.parse("<gray>Du hast <white><b>" + label(cur.cards) + "</b></white> · Bank zeigt <white>"
                        + dealer.get(0).value() + "</white> · " + (turnTime <= 5 ? "<#FF6B6B>" : "<white>") + turnTime + " s"));
                if (turnTime <= 5) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.0f + (5 - turnTime) * 0.15f);
            }
            if (turnTime <= 0) {
                Hand h = seats[turn].hands.get(seats[turn].active);
                h.done = true;
                if (p != null) CasinoText.send(p, "Zeit um – du hältst bei " + BjCards.total(h.cards) + ".");
                table.clearActions(turn);
                next();
            }
        }
    }

    private void deal() {
        phase = Phase.DEALING;
        if (shoe.needsShuffle()) {
            shoe.shuffle();
            table.dealerText(Component.text("Neues Deck wird gemischt", NamedTextColor.GRAY));
            table.dealer().shuffle();
            for (int i = 0; i < 6; i++) later(i * 2L, () -> playAll(Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.5f + (float) Math.random() * 0.4f));
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            table.clearRack(i);
            table.clearControls(i);
            if (s.bet > 0) {
                s.lastStack.clear();
                s.lastStack.addAll(s.stack);
            }
            if ((s.jewels ? s.bet > 0 : s.bet >= minBet()) && s.player != null) {
                s.playing = true;
                Hand h = new Hand();
                h.bet = s.bet;
                s.hands.clear();
                s.hands.add(h);
                s.active = 0;
                order.add(i);
            } else if (s.bet > 0 && s.player != null) {
                // zu wenig gesetzt: zurück
                plugin.casinoPlay().refund(s.player, GAME, s.bet);
                s.bet = 0;
                s.stack.clear();
                table.bet(i, s.stack, 0);
            }
        }
        table.dealerText(Component.text("Karten werden ausgeteilt", NamedTextColor.GRAY));
        dealer.clear();
        holeCard = null;
        // Reihenfolge: Spieler, Dealer offen, Spieler, Dealer verdeckt
        List<Runnable> steps = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            for (int seat : order) {
                steps.add(() -> {
                    Hand h = seats[seat].hands.get(0);
                    BjCards.Card c = shoe.draw();
                    h.cards.add(c);
                    table.card(seat, 0, h.cards.size() - 1, c, 1);
                    later(10, () -> table.total(seat, label(h.cards)));
                    table.dealer().deal(table.cardsAt(seat));
                    cardSound();
                });
            }
            boolean hidden = round == 1;
            steps.add(() -> {
                BjCards.Card c = shoe.draw();
                dealer.add(c);
                ItemDisplay e = table.card(BlackjackTable.SEATS, 0, dealer.size() - 1, hidden ? null : c, 1);
                if (hidden) holeCard = e;
                later(10, () -> table.total(BlackjackTable.SEATS, hidden ? String.valueOf(dealer.get(0).value()) : label(dealer)));
                table.dealerGesture();
                cardSound();
            });
        }
        run(steps, 9, this::afterDeal);
    }

    private void afterDeal() {
        if (BjCards.blackjack(dealer)) {
            table.reveal(holeCard, dealer.get(1));
            table.total(BlackjackTable.SEATS, label(dealer));
            table.dealerText(Component.text("Blackjack der Bank!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            for (Seat s : seats) for (Hand h : s.hands) h.done = true;
            later(30, this::settle);
            return;
        }
        for (int i = 0; i < seats.length; i++) {
            for (Hand h : seats[i].hands) {
                if (BjCards.blackjack(h.cards)) {
                    h.done = true;
                    table.glow(i, 0xFFD34D);
                    Player bp = seats[i].player == null ? null : Bukkit.getPlayer(seats[i].player);
                    if (bp != null) {
                        bp.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<gradient:#FFE070:#F2A21C><b>BLACKJACK!</b></gradient>"),
                                Msg.parse("<gray>zahlt <white>3 zu 2</white>"), net.kyori.adventure.title.Title.Times.times(
                                        java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1200), java.time.Duration.ofMillis(300))));
                        bp.playSound(bp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
                    }
                    de.kuby.kubyplugins.util.Fx.particle(plugin, table.cardsAt(i).add(0, 0.3, 0), org.bukkit.Particle.FIREWORK, 18, 0.25, 0.2, 0.25, 0.04);
                    final int bjSeat = i;
                    later(12, () -> table.dealer().applaud(table.cardsAt(bjSeat)));
                }
            }
        }
        phase = Phase.PLAYING;
        turn = -1;
        next();
    }

    /** Nächste offene Hand (gleicher Platz zuerst), sonst der Dealer. */
    private void next() {
        if (phase != Phase.PLAYING) return;
        for (int i = Math.max(0, turn); i < seats.length; i++) {
            Seat s = seats[i];
            if (!s.playing) continue;
            for (int h = 0; h < s.hands.size(); h++) {
                if (!s.hands.get(h).done) {
                    s.active = h;
                    turn = i;
                    prompt(i);
                    return;
                }
            }
        }
        turn = -1;
        dealerPlay();
    }

    private void prompt(int seat) {
        Seat s = seats[seat];
        Hand h = s.hands.get(s.active);
        Player p = s.player == null ? null : Bukkit.getPlayer(s.player);
        if (p == null) {
            h.done = true;
            next();
            return;
        }
        long chips = plugin.casinoPlay().balance(p.getUniqueId(), GAME);
        boolean two = h.cards.size() == 2;
        boolean canSplit = two && s.hands.size() == 1 && h.cards.get(0).value() == h.cards.get(1).value() && chips >= h.bet;
        boolean canDouble = two && chips >= h.bet && !(h.split && h.cards.get(0).rank() == 1);
        table.actions(seat, new boolean[]{true, true, canDouble, canSplit});
        turnMax = (int) plugin.getConfig().getLong("casino.blackjack.turn-seconds", 15);
        turnTime = turnMax;
        table.dealerText(Component.text().append(Component.text("Karte für ", NamedTextColor.GRAY))
                .append(Component.text(s.name, NamedTextColor.WHITE)).append(Component.text("?", NamedTextColor.GRAY)).build());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
        table.dealer().prompt(table.cardsAt(seat));
        // Tipp nach der Grundstrategie, dezent in der Actionbar
        p.sendActionBar(Msg.parse("<gray>Du hast <white><b>" + label(h.cards) + "</b></white> · Bank zeigt <white>"
                + dealer.get(0).value() + "</white> <dark_gray>· <gray>Tipp: <#9AE6B4>" + hint(h, canDouble, canSplit)));
    }

    /** Aktionsfeld angeklickt: 0 Karte, 1 Halten, 2 Doppeln, 3 Teilen. */
    public void action(Player p, int seat, int action) {
        if (phase != Phase.PLAYING || turn != seat || !p.getUniqueId().equals(seats[seat].player)) return;
        Seat s = seats[seat];
        Hand h = s.hands.get(s.active);
        switch (action) {
            case 0 -> {
                draw(seat, h);
                int t = BjCards.total(h.cards);
                if (t > 21) {
                    h.done = true;
                    later(12, () -> {
                        table.bust(seat);
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 0.7f);
                        p.sendActionBar(Msg.parse("<#FF6B6B><b>Überkauft!</b> <gray>" + t));
                    });
                } else if (t == 21) {
                    h.done = true;
                    later(12, () -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f));
                }
            }
            case 1 -> h.done = true;
            case 2 -> {
                if (h.cards.size() != 2) return;
                String err = plugin.casinoPlay().take(p, GAME, h.bet);
                if (err != null) {
                    CasinoText.error(p, err);
                    return;
                }
                s.bet += h.bet;
                h.bet *= 2;
                h.doubled = true;
                s.stack.add(h.bet / 2);
                table.bet(seat, s.stack, s.bet, s.jewels);
                chipSound(table.field(seat), s.stack.size());
                draw(seat, h);
                h.done = true;
            }
            case 3 -> {
                if (h.cards.size() != 2 || s.hands.size() != 1 || h.cards.get(0).value() != h.cards.get(1).value()) return;
                String err = plugin.casinoPlay().take(p, GAME, h.bet);
                if (err != null) {
                    CasinoText.error(p, err);
                    return;
                }
                s.bet += h.bet;
                s.stack.add(h.bet);
                table.bet(seat, s.stack, s.bet, s.jewels);
                chipSound(table.field(seat), s.stack.size());
                Hand second = new Hand();
                second.bet = h.bet;
                second.split = true;
                second.index = 1;
                second.cards.add(h.cards.remove(1));
                h.split = true;
                s.hands.add(second);
                redrawSeat(seat);
                draw(seat, h);
                draw(seat, second);
                boolean aces = h.cards.get(0).rank() == 1;
                if (aces) {
                    h.done = true;
                    second.done = true;
                }
                if (BjCards.total(h.cards) == 21) h.done = true;
                if (BjCards.total(second.cards) == 21) second.done = true;
            }
            default -> {
                return;
            }
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
        save();
        table.clearActions(seat);
        if (h.done) {
            later(14, this::next);
        } else {
            prompt(seat);
        }
    }

    private void draw(int seat, Hand h) {
        BjCards.Card c = shoe.draw();
        h.cards.add(c);
        table.card(seat, h.index, h.cards.size() - 1, c, seats[seat].hands.size());
        Seat st = seats[seat];
        later(10, () -> table.total(seat, handsLabel(st)));
        table.dealerGesture();
        cardSound();
    }

    private void redrawSeat(int seat) {
        // Karten des Platzes neu legen (nach dem Teilen)
        Seat s = seats[seat];
        table.clearCards();
        for (int i = 0; i < seats.length; i++) {
            for (Hand h : seats[i].hands) {
                for (int k = 0; k < h.cards.size(); k++) table.card(i, h.index, k, h.cards.get(k), seats[i].hands.size());
            }
            if (!seats[i].hands.isEmpty()) table.total(i, handsLabel(seats[i]));
        }
        for (int k = 0; k < dealer.size(); k++) {
            ItemDisplay e = table.card(BlackjackTable.SEATS, 0, k, k == 1 && holeCard != null ? null : dealer.get(k), 1);
            if (k == 1 && holeCard != null) holeCard = e;
        }
        table.total(BlackjackTable.SEATS, String.valueOf(dealer.get(0).value()));
        if (s.hands.isEmpty()) table.total(seat, "");
    }

    private void dealerPlay() {
        phase = Phase.DEALER;
        boolean anyLive = false;
        for (Seat s : seats) {
            for (Hand h : s.hands) {
                if (BjCards.total(h.cards) <= 21 && !(BjCards.blackjack(h.cards) && !h.split)) anyLive = true;
            }
        }
        if (holeCard != null) table.reveal(holeCard, dealer.get(1));
        later(6, () -> table.total(BlackjackTable.SEATS, label(dealer)));
        playAll(Sound.ITEM_BOOK_PUT, 0.7f, 1.4f);
        if (!anyLive) {
            later(14, this::settle);
            return;
        }
        table.dealerText(Component.text("Die Bank zieht", NamedTextColor.GRAY));
        dealerStep();
    }

    private void dealerStep() {
        int t = BjCards.total(dealer);
        if (t >= 17) {
            later(14, this::settle);
            return;
        }
        later(18, () -> {
            BjCards.Card c = shoe.draw();
            dealer.add(c);
            table.card(BlackjackTable.SEATS, 0, dealer.size() - 1, c, 1);
            later(10, () -> {
                table.total(BlackjackTable.SEATS, label(dealer));
                if (BjCards.total(dealer) > 21) {
                    table.bust(BlackjackTable.SEATS);
                    table.dealerText(Component.text("BANK ÜBERKAUFT!", TextColor.color(0x7CFF9A)).decorate(TextDecoration.BOLD));
                    later(6, () -> table.dealer().shakeHead());
                    playAll(Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                }
            });
            table.dealerGesture();
            cardSound();
            dealerStep();
        });
    }

    private void settle() {
        phase = Phase.SETTLE;
        int dt = BjCards.total(dealer);
        boolean dealerBj = BjCards.blackjack(dealer);
        int players = 0;
        Component single = null;
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            if (!s.playing) continue;
            players++;
            long staked = 0, paid = 0;
            String best = null;
            String vs = null;
            for (Hand h : s.hands) {
                int pt = BjCards.total(h.cards);
                boolean bj = BjCards.blackjack(h.cards) && !h.split;
                long pay;
                String res;
                if (pt > 21) {
                    pay = 0;
                    res = "Überkauft";
                } else if (bj && !dealerBj) {
                    pay = h.bet + h.bet * 3 / 2;
                    res = "Blackjack!";
                } else if (dealerBj && !bj) {
                    pay = 0;
                    res = "Verloren";
                } else if (bj) {
                    pay = h.bet;
                    res = "Unentschieden";
                } else if (dt > 21 || pt > dt) {
                    pay = h.bet * 2;
                    res = "Gewonnen";
                } else if (pt == dt) {
                    pay = h.bet;
                    res = "Unentschieden";
                } else {
                    pay = 0;
                    res = "Verloren";
                }
                staked += h.bet;
                paid += pay;
                if (best == null || rank(res) > rank(best)) best = res;
                vs = pt + " gegen " + (dt > 21 ? "über 21" : String.valueOf(dt));
            }
            UUID id = s.player != null ? s.player : null;
            UUID owner = id != null ? id : lastOwner(i);
            de.kuby.kubyplugins.service.CasinoPlay.Outcome o = owner == null ? null : plugin.casinoPlay().settle(owner, GAME, staked, paid);
            long payout = o == null ? paid : o.payout();
            long net = payout - staked;
            String unit = s.jewels ? " ◆" : "";
            TextColor col = net > 0 ? TextColor.color(0x4BE0C8) : net == 0 ? NamedTextColor.GRAY : TextColor.color(0xFF5A5A);
            String head = best.equals("Blackjack!") ? "BLACKJACK!" : best.equals("Gewonnen") ? "GEWONNEN" : best.equals("Unentschieden")
                    ? "UNENTSCHIEDEN" : best.equals("Überkauft") ? "ÜBERKAUFT" : "VERLOREN";
            Component result = Component.text().append(Component.text(head, best.equals("Blackjack!") ? TextColor.color(0xFFD34D) : col)
                    .decorate(TextDecoration.BOLD)).append(Component.newline()).append(Component.text(vs, NamedTextColor.WHITE)).build();
            // am Platz nur Ergebnis und Betrag (zwei Zeilen): im echten Client überlagerten sich sonst die Texte der Nachbarplätze;
            // „19 gegen 17“ steht im Chat und beim Einzelspieler über dem Dealer
            Component headOnly = Component.text(head, best.equals("Blackjack!") ? TextColor.color(0xFFD34D) : col).decorate(TextDecoration.BOLD);
            table.result(i, Component.text().append(headOnly).append(Component.newline())
                    .append(net > 0 ? Component.text("+" + CasinoText.num(net) + unit, NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                            : net < 0 ? Component.text("-" + CasinoText.num(-net) + unit, TextColor.color(0xFF6B6B))
                            : Component.text("Einsatz zurück", NamedTextColor.GRAY)).build());
            if (o != null) {
                String bl = de.kuby.kubyplugins.service.CasinoPlay.bonusLine(o);
                if (o.win()) table.streak(i, Msg.parse(bl.isEmpty() ? "<gray>Nächster Sieg: <white>x"
                        + de.kuby.kubyplugins.service.CasinoPlay.fmt(plugin.casinoPlay().streakMult(GAME, o.streak())) : bl));
                else if (!o.push()) table.streak(i, Msg.parse(o.cashback() > 0 ? "<aqua>+" + CasinoText.num(o.cashback()) + " Cashback" : ""));
            }
            if (net > 0) table.glow(i, best.equals("Blackjack!") ? 0xFFD34D : 0x7CFF9A);
            single = result;
            table.sweepChips(i, payout > 0);
            final int ps = i;
            final boolean won = net > 0;
            later(8 + 20L * (players - 1), () -> {
                if (won) table.dealer().push(table.cardsAt(ps));
                else table.dealer().collect(table.cardsAt(ps));
            });
            Player p = owner == null ? null : Bukkit.getPlayer(owner);
            if (p != null && o != null) {
                String u = de.kuby.kubyplugins.service.CasinoPlay.unit(o.cur(), payout);
                CasinoText.send(p, "<white>" + best + "</white> · " + vs + (payout > 0 ? " · zurück <#F2C94C>" + u
                        + "</#F2C94C>" + (net > 0 ? " <green>(+" + CasinoText.num(net) + ")" : "")
                        : " · <#FF5A5A>-" + de.kuby.kubyplugins.service.CasinoPlay.unit(o.cur(), staked)));
                plugin.casinoPlay().feedback(p, o, table.cardsAt(i).add(0, 0.4, 0));
                if (net > 0) {
                    for (int k = 0; k < 5; k++) later(22 + k * 2L, () -> p.playSound(table.field(seatOfOwner(p)), Sound.BLOCK_CHAIN_PLACE, 0.5f, 1.6f + (float) Math.random() * 0.3f));
                } else if (net < 0) {
                    later(4, () -> p.playSound(p.getLocation(), Sound.BLOCK_SAND_STEP, 0.6f, 0.8f));
                }
                // schwebender Banner nur beim Blackjack (hoch über allem), sonst nur der Gewinn-Ton – der Betrag steht am Platz
                if (net > 0 && best.startsWith("Blackjack")) CasinoFx.show(plugin, owner, table.cardsAt(i).add(0, 1.35, 0), CasinoFx.Result.BIG_WIN, net);
                else if (net > 0) CasinoFx.sound(plugin, owner, CasinoFx.Result.WIN);
            }
        }
        if (players == 1 && single != null) table.dealerText(single);
        else table.dealerText(Component.text().append(Component.text("Bank: ", NamedTextColor.GRAY))
                .append(Component.text(dt > 21 ? "über 21" : String.valueOf(dt), NamedTextColor.WHITE)).build());
        save();
        later(70, table::collectCards);
        later(74, () -> table.dealer().bow());
        later(90, this::cleanup);
    }

    private int seatOfOwner(Player p) {
        int i = seatOf(p.getUniqueId());
        return i < 0 ? 0 : i;
    }

    private static int rank(String r) {
        return switch (r) {
            case "Blackjack!" -> 4;
            case "Gewonnen" -> 3;
            case "Unentschieden" -> 2;
            default -> 1;
        };
    }

    /** Wer zuletzt auf dem Platz saß (für Auszahlung nach dem Aufstehen). */
    private final UUID[] owners = new UUID[BlackjackTable.SEATS];

    private UUID lastOwner(int seat) {
        return owners[seat];
    }

    private void cleanup() {
        table.clearCards();
        dealer.clear();
        holeCard = null;
        for (int i = 0; i < seats.length; i++) {
            table.result(i, Component.empty());
            table.clearChips(i);
            clearSeat(i);
            if (!free(i)) {
                showRack(i);
                int st = plugin.casinoPlay().streak(seats[i].player);
                table.streak(i, st >= 1 ? Msg.parse("<#FF8A3D>🔥 Serie " + st + " <gray>· nächster Sieg <white>x"
                        + de.kuby.kubyplugins.service.CasinoPlay.fmt(plugin.casinoPlay().streakMult(GAME, st))) : Component.empty());
            } else {
                table.streak(i, Component.empty());
            }
        }
        phase = Phase.IDLE;
        idleText();
        save();
    }

    private void clearSeat(int i) {
        Seat s = seats[i];
        s.hands.clear();
        s.bet = 0;
        s.stack.clear();
        s.playing = false;
        s.active = 0;
    }

    // =================================================================================================================
    // Hilfen
    // =================================================================================================================

    private static String label(List<BjCards.Card> cards) {
        int t = BjCards.total(cards);
        if (BjCards.blackjack(cards)) return "BJ";
        return BjCards.soft(cards) && t < 21 ? (t - 10) + "/" + t : String.valueOf(t);
    }

    private static String handsLabel(Seat s) {
        if (s.hands.size() == 1) return label(s.hands.get(0).cards);
        return label(s.hands.get(0).cards) + " | " + label(s.hands.get(1).cards);
    }

    private void cardSound() {
        playAll(Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.4f + (float) Math.random() * 0.2f);
        playAll(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.12f, 2.0f);
    }

    /** Chip-Klacken: höher, je höher der Stapel. */
    private void chipSound(Location at, int height) {
        if (at.getWorld() == null) return;
        float pitch = Math.min(2.0f, 1.3f + 0.05f * height);
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.7f, pitch);
        at.getWorld().playSound(at, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.35f, 1.9f);
    }

    private void playAll(Sound sound, float vol, float pitch) {
        Location l = table.origin();
        if (l.getWorld() != null) l.getWorld().playSound(l.add(0, 1, 0), sound, vol, pitch);
    }

    private void run(List<Runnable> steps, int every, Runnable after) {
        for (int i = 0; i < steps.size(); i++) later((long) every * (i + 1), steps.get(i));
        later((long) every * (steps.size() + 1) + 4, after);
    }

    private void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    /** Offene Einsätze (für die Sicherung nach einem Absturz). */
    public List<String> escrow() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            UUID id = s.player != null ? s.player : owners[i];
            if (id != null) owners[i] = id;
            if (phase != Phase.SETTLE && s.bet > 0 && id != null) out.add(id + ";" + s.bet);
        }
        return out;
    }

    /** Beim Herunterfahren: alle offenen Einsätze zurück. */
    public void shutdown() {
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            UUID id = s.player != null ? s.player : owners[i];
            if (phase != Phase.SETTLE && s.bet > 0 && id != null) plugin.casinoPlay().refund(id, GAME, s.bet);
            s.bet = 0;
            if (s.player != null) table.removeVehicle(i);
        }
        table.removeAll();
    }

    private void save() {
        for (int i = 0; i < seats.length; i++) if (seats[i].player != null) owners[i] = seats[i].player;
        plugin.blackjack().saveEscrow();
    }

    public boolean idle() {
        return phase == Phase.IDLE;
    }

    // =================================================================================================================
    // Rack, Bedienknöpfe, Hover
    // =================================================================================================================

    private void showRack(int seat) {
        Seat s = seats[seat];
        if (s.player == null) return;
        s.jewels = plugin.casinoPlay().cur(s.player, GAME) == de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS;
        s.rackValues = plugin.casinoPlay().rack(s.player, GAME, table.tier().rack);
        table.rack(seat, s.rackValues, s.jewels);
        showControls(seat);
    }

    private void showControls(int seat) {
        Seat s = seats[seat];
        Player p = s.player == null ? null : Bukkit.getPlayer(s.player);
        if (p == null || (phase != Phase.IDLE && phase != Phase.BETTING)) {
            table.clearControls(seat);
            return;
        }
        boolean vip = plugin.casinoPlay().vip(p);
        table.controls(seat, s.bet == 0 && !s.lastStack.isEmpty(), s.bet > 0, vip && s.bet == 0 ? s.jewels : null);
    }

    /** Knopf auf der Armauflage: repeat, clear oder currency. */
    public void control(Player p, int seat, String kind) {
        Seat s = seats[seat];
        if (s.player == null || !s.player.equals(p.getUniqueId())) return;
        if (phase != Phase.IDLE && phase != Phase.BETTING) return;
        switch (kind) {
            case "repeat" -> {
                List<Long> again = new ArrayList<>(s.lastStack);
                for (Long v : again) {
                    int idx = -1;
                    for (int k = 0; k < s.rackValues.length; k++) if (s.rackValues[k] == v) idx = k;
                    if (idx < 0) continue;
                    int before = s.stack.size();
                    chip(p, seat, idx);
                    if (s.stack.size() == before) break;
                }
            }
            case "clear" -> {
                if (s.bet <= 0) return;
                plugin.casinoPlay().refund(p.getUniqueId(), GAME, s.bet);
                CasinoText.send(p, "Einsatz zurückgenommen: <#F2C94C>+" + de.kuby.kubyplugins.service.CasinoPlay.unit(
                        s.jewels ? de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS : de.kuby.kubyplugins.service.CasinoPlay.Cur.CHIPS, s.bet));
                s.bet = 0;
                s.stack.clear();
                table.bet(seat, s.stack, 0);
                p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.2f);
                if (phase == Phase.BETTING && totalBets() == 0) {
                    phase = Phase.IDLE;
                    idleText();
                }
                showControls(seat);
                save();
            }
            case "currency" -> {
                String err = plugin.casinoPlay().toggle(p);
                if (err != null) {
                    CasinoText.error(p, err);
                    return;
                }
                boolean jw = plugin.casinoPlay().mode(p.getUniqueId()) == de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS;
                s.lastStack.clear();
                showRack(seat);
                p.playSound(p.getLocation(), jw ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.4f);
                p.sendActionBar(Msg.parse(jw ? "<#7FE3FF>◆ Du spielst jetzt mit Juwelen <gray>(" + CasinoText.num(plugin.casinoPlay().jewelMin())
                        + " bis " + CasinoText.num(plugin.casinoPlay().jewelMax()) + " je Runde, " + plugin.casinoPlay().volumePeriodLabel() + " frei: "
                        + CasinoText.num(plugin.casinoPlay().volumeLeft(p.getUniqueId())) + ")" : "<#F2C94C>● Du spielst jetzt mit Chips"));
            }
            default -> {
            }
        }
    }

    /** Alle 2 Ticks: Hover für sitzende Spieler (Knopf unter dem Blick hebt sich). */
    public void hoverTick() {
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            if (s.player == null) continue;
            Player p = Bukkit.getPlayer(s.player);
            if (p == null) continue;
            if (table.hover(i, p.getEyeLocation(), p.getEyeLocation().getDirection())) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, 1.9f);
            }
        }
    }

    /** Spieltest: setzt und spielt für die Test-Spieler wie ein Spieler mit Klicks (Rack-Chip, Aktion nach Tipp). */
    public void autoPlay(java.util.Set<UUID> ids) {
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            if (s.player == null || !ids.contains(s.player)) continue;
            Player p = Bukkit.getPlayer(s.player);
            if (p == null) continue;
            if ((phase == Phase.IDLE || phase == Phase.BETTING) && s.bet == 0) chip(p, i, 2);
        }
        if (phase == Phase.PLAYING && turn >= 0 && seats[turn].player != null && ids.contains(seats[turn].player)) {
            Seat s = seats[turn];
            Player p = Bukkit.getPlayer(s.player);
            if (p == null) return;
            Hand h = s.hands.get(s.active);
            long bal = plugin.casinoPlay().balance(p.getUniqueId(), GAME);
            boolean two = h.cards.size() == 2;
            boolean canSplit = two && s.hands.size() == 1 && h.cards.get(0).value() == h.cards.get(1).value() && bal >= h.bet;
            boolean canDouble = two && bal >= h.bet;
            String hint = hint(h, canDouble, canSplit);
            int a = switch (hint) {
                case "Halten" -> 1;
                case "Doppeln" -> 2;
                case "Teilen" -> 3;
                default -> 0;
            };
            plugin.getLogger().info("[Casino-Test] blackjack " + p.getName() + " hat " + label(h.cards) + ", Bank zeigt "
                    + dealer.get(0).value() + " -> " + hint);
            action(p, turn, a);
        }
    }

    public int seated() {
        int n = 0;
        for (Seat s : seats) if (s.player != null) n++;
        return n;
    }

    /** Grundstrategie (vereinfacht) als Tipp. */
    private String hint(Hand h, boolean canDouble, boolean canSplit) {
        int t = BjCards.total(h.cards);
        int up = dealer.isEmpty() ? 10 : dealer.get(0).value();
        boolean soft = BjCards.soft(h.cards);
        if (canSplit) {
            int v = h.cards.get(0).value();
            if (v == 11 || v == 8) return "Teilen";
            if (v == 9 && up != 7 && up < 10) return "Teilen";
            if ((v == 2 || v == 3 || v == 7) && up <= 7) return "Teilen";
            if (v == 6 && up <= 6) return "Teilen";
        }
        if (canDouble && !soft && (t == 11 || (t == 10 && up <= 9) || (t == 9 && up >= 3 && up <= 6))) return "Doppeln";
        if (soft) {
            if (t >= 19) return "Halten";
            if (t == 18) return up >= 9 ? "Karte" : "Halten";
            return "Karte";
        }
        if (t >= 17) return "Halten";
        if (t >= 13) return up <= 6 ? "Halten" : "Karte";
        if (t == 12) return up >= 4 && up <= 6 ? "Halten" : "Karte";
        return "Karte";
    }
}
