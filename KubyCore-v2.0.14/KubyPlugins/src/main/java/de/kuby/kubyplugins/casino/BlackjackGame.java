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
            table.rack(seat, RACK);
            p.sendActionBar(Msg.parse("<gray>Du sitzt am <white>" + name + "</white>. Leg Chips aus dem Rack auf dein Feld."));
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
            if (s.bet > 0) plugin.casino().giveChips(id, s.bet);
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
                if (phase == Phase.IDLE || phase == Phase.BETTING) table.rack(i, RACK);
                table.bet(i, seats[i].stack, seats[i].bet);
            }
        }
    }

    // =================================================================================================================
    // Einsätze
    // =================================================================================================================

    private long maxBet() {
        return plugin.getConfig().getLong("casino.blackjack.max-bet", 10_000);
    }

    private long minBet() {
        return plugin.getConfig().getLong("casino.blackjack.min-bet", 10);
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
        long v = RACK[index];
        Seat s = seats[seat];
        if (s.bet + v > maxBet()) {
            CasinoText.error(p, "Höchsteinsatz " + CasinoText.num(maxBet()) + " Chips.");
            return;
        }
        if (!plugin.casino().takeChips(p.getUniqueId(), v)) {
            CasinoText.error(p, "So viele Chips hast du nicht. Tausch sie an der Kasse.");
            return;
        }
        long feed = Math.round(v * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        s.bet += v;
        s.stack.add(v);
        table.bet(seat, s.stack, s.bet);
        p.playSound(table.field(seat), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.8f, 1.5f);
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
                p.sendActionBar(Msg.parse("<gray>Dein Zug: <green>Karte</green>, <green>Halten</green>, <green>Doppeln</green>"
                        + " oder <green>Teilen</green> · <white>" + turnTime + " s"));
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
        if (shoe.needsShuffle()) shoe.shuffle();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < seats.length; i++) {
            Seat s = seats[i];
            table.clearRack(i);
            if (s.bet >= minBet() && s.player != null) {
                s.playing = true;
                Hand h = new Hand();
                h.bet = s.bet;
                s.hands.clear();
                s.hands.add(h);
                s.active = 0;
                order.add(i);
            } else if (s.bet > 0 && s.player != null) {
                // zu wenig gesetzt: zurück
                plugin.casino().giveChips(s.player, s.bet);
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
                    table.total(seat, label(h.cards));
                    cardSound();
                });
            }
            boolean hidden = round == 1;
            steps.add(() -> {
                BjCards.Card c = shoe.draw();
                dealer.add(c);
                ItemDisplay e = table.card(BlackjackTable.SEATS, 0, dealer.size() - 1, hidden ? null : c, 1);
                if (hidden) holeCard = e;
                table.total(BlackjackTable.SEATS, hidden ? String.valueOf(dealer.get(0).value()) : label(dealer));
                cardSound();
            });
        }
        run(steps, 7, this::afterDeal);
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
        for (Seat s : seats) {
            for (Hand h : s.hands) if (BjCards.blackjack(h.cards)) h.done = true;
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
        long chips = plugin.casino().chips(p.getUniqueId());
        boolean two = h.cards.size() == 2;
        boolean canSplit = two && s.hands.size() == 1 && h.cards.get(0).value() == h.cards.get(1).value() && chips >= h.bet;
        boolean canDouble = two && chips >= h.bet && !(h.split && h.cards.get(0).rank() == 1);
        table.actions(seat, new boolean[]{true, true, canDouble, canSplit});
        turnMax = (int) plugin.getConfig().getLong("casino.blackjack.turn-seconds", 15);
        turnTime = turnMax;
        table.dealerText(Component.text().append(Component.text("Karte für ", NamedTextColor.GRAY))
                .append(Component.text(s.name, NamedTextColor.WHITE)).append(Component.text("?", NamedTextColor.GRAY)).build());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
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
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                } else if (t == 21) {
                    h.done = true;
                }
            }
            case 1 -> h.done = true;
            case 2 -> {
                if (h.cards.size() != 2 || !plugin.casino().takeChips(p.getUniqueId(), h.bet)) return;
                s.bet += h.bet;
                h.bet *= 2;
                h.doubled = true;
                s.stack.add(h.bet / 2);
                table.bet(seat, s.stack, s.bet);
                draw(seat, h);
                h.done = true;
            }
            case 3 -> {
                if (h.cards.size() != 2 || s.hands.size() != 1 || h.cards.get(0).value() != h.cards.get(1).value()) return;
                if (!plugin.casino().takeChips(p.getUniqueId(), h.bet)) return;
                s.bet += h.bet;
                s.stack.add(h.bet);
                table.bet(seat, s.stack, s.bet);
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
            later(6, this::next);
        } else {
            prompt(seat);
        }
    }

    private void draw(int seat, Hand h) {
        BjCards.Card c = shoe.draw();
        h.cards.add(c);
        table.card(seat, h.index, h.cards.size() - 1, c, seats[seat].hands.size());
        table.total(seat, handsLabel(seats[seat]));
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
        table.total(BlackjackTable.SEATS, label(dealer));
        cardSound();
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
        later(14, () -> {
            BjCards.Card c = shoe.draw();
            dealer.add(c);
            table.card(BlackjackTable.SEATS, 0, dealer.size() - 1, c, 1);
            table.total(BlackjackTable.SEATS, label(dealer));
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
            if (owner != null && paid > 0) plugin.casino().giveChips(owner, paid);
            if (owner != null) plugin.casinoLog().record("Blackjack", owner, staked, paid);
            long net = paid - staked;
            TextColor col = net > 0 ? TextColor.color(0x4BE0C8) : net == 0 ? NamedTextColor.GRAY : TextColor.color(0xFF5A5A);
            Component result = Component.text().append(Component.text(best, col).decorate(TextDecoration.BOLD))
                    .append(Component.newline()).append(Component.text(vs, NamedTextColor.WHITE)).build();
            table.result(i, Component.text().append(result).append(Component.newline())
                    .append(paid > 0 ? Component.text("zurück " + CasinoText.chips(paid), TextColor.color(0xF2C94C))
                            : Component.empty())
                    .append(net > 0 ? Component.text(" +" + CasinoText.num(net), NamedTextColor.GREEN) : Component.empty()).build());
            single = result;
            table.sweepChips(i, paid > 0);
            Player p = owner == null ? null : Bukkit.getPlayer(owner);
            if (p != null) {
                CasinoText.send(p, "<white>" + best + "</white> · " + vs + (paid > 0 ? " · zurück <#F2C94C>" + CasinoText.chips(paid)
                        + "</#F2C94C>" + (net > 0 ? " <green>(+" + CasinoText.num(net) + ")" : "") : " · <#FF5A5A>-" + CasinoText.chips(staked)));
                CasinoFx.show(plugin, owner, table.field(i).add(0, 0.8, 0), net > 0 ? (best.startsWith("Blackjack")
                        ? CasinoFx.Result.BIG_WIN : CasinoFx.Result.WIN) : net == 0 ? CasinoFx.Result.PUSH : CasinoFx.Result.LOSE, net);
            }
        }
        if (players == 1 && single != null) table.dealerText(single);
        else table.dealerText(Component.text().append(Component.text("Bank: ", NamedTextColor.GRAY))
                .append(Component.text(dt > 21 ? "über 21" : String.valueOf(dt), NamedTextColor.WHITE)).build());
        save();
        later(80, this::cleanup);
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
            if (!free(i)) table.rack(i, RACK);
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
        playAll(Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.3f);
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
            if (phase != Phase.SETTLE && s.bet > 0 && id != null) plugin.casino().giveChips(id, s.bet);
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
}
