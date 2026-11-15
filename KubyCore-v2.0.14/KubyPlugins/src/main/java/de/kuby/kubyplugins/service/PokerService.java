package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Texas Hold'em am Poker-Tisch, Spieler gegen Spieler, ohne Abzug durch das Casino.
 * Blinds und Buy-in aus der config.yml (Standard 10/20, 40 bis 200 Big Blinds). Sechs feste Plätze am echten Tisch
 * ({@link de.kuby.kubyplugins.casino.PokerTable}): Hocker anklicken = hinsetzen, die Handkarten liegen vor dem Platz
 * (nur für den Besitzer offen), Aktionen per Knopf auf dem Filz (Passen, Check/Mitgehen, Erhöhen mit −/+/½ Pot/Pot,
 * All-in) oder /poker. Regeln: Dealer-Button wandert, Small/Big Blind (Heads-up: Dealer = Small Blind), Mindest-
 * erhöhung = letzte volle Erhöhung, ein zu kleines All-in öffnet die Setzrunde nicht neu, Side-Pots, geteilte Pots
 * (Rest-Chip links vom Dealer), Board läuft bei All-in durch, Showdown mit Handbewertung (beste 5 aus 7), Verlierer
 * legen verdeckt ab, wer alleine übrig ist, darf zeigen. Pause (Platz behalten, Hände aussetzen), Nachkaufen bis zum
 * Maximum zwischen den Händen. Zeit pro Zug (Standard 30 s), danach Check bzw. Passen.
 * Sicherheit: Stacks und Einsätze werden laufend in poker.yml gesichert und nach einem Absturz zurückgegeben.
 * Wer geht, ausloggt oder zu weit weg ist, passt und bekommt seinen Stack zurück.
 */
public final class PokerService implements org.bukkit.event.Listener {

    private static final int MAX_SEATS = 6;
    private static final String SURVIVAL = "survival";
    private static final double RANGE = 10;
    private static final long PAUSE_MS = 6000;
    private static final String[] CATEGORY = {"Höchste Karte", "Paar", "Zwei Paare", "Drilling", "Straße", "Flush",
            "Full House", "Vierling", "Straight Flush"};

    private record Card(int rank, char suit) {
        String label() {
            String r = switch (rank) {
                case 14 -> "A";
                case 13 -> "K";
                case 12 -> "Q";
                case 11 -> "J";
                default -> String.valueOf(rank);
            };
            return (suit == '♥' || suit == '♦' ? "<red>" : "<white>") + r + suit + "<reset>";
        }
    }

    private static final class Seat {
        final UUID id;
        int pos;
        boolean sitOut;
        boolean raising;
        long raiseTo;
        boolean mayShow;
        long stack;
        long bet;
        long committed;
        boolean inHand;
        boolean folded;
        boolean allIn;
        boolean acted;
        boolean leaving;
        String last = "";
        final List<Card> hole = new ArrayList<>();

        Seat(UUID id, long stack) {
            this.id = id;
            this.stack = stack;
        }
    }

    private enum Phase { WAITING, PREFLOP, FLOP, TURN, RIVER }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final List<Seat> seats = new ArrayList<>();
    private final List<Card> board = new ArrayList<>();
    private Deque<Card> deck = new ArrayDeque<>();
    private Phase phase = Phase.WAITING;
    private int dealer = -1;
    private int turn = -1;
    private long currentBet;
    private long minRaise;
    private long turnStarted;
    private long nextHandAt;
    private String tisch;
    private TextDisplay display;
    private de.kuby.kubyplugins.casino.PokerTable desk;
    private final NamespacedKey ptag;
    private final NamespacedKey pclick;

    public PokerService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "poker.yml");
        this.tag = new NamespacedKey(plugin, "kp_poker");
        this.ptag = new NamespacedKey(plugin, "kp_poker_t");
        this.pclick = new NamespacedKey(plugin, "kp_poker_c");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        tisch = yml.getString("tisch");
        // Nach einem Absturz: alles, was am Tisch lag, zurückgeben.
        ConfigurationSection escrow = yml.getConfigurationSection("escrow");
        if (escrow != null) {
            for (String key : escrow.getKeys(false)) {
                try {
                    long amount = escrow.getLong(key);
                    if (amount > 0) plugin.casino().giveChips(UUID.fromString(key), amount);
                } catch (IllegalArgumentException ignored) {
                }
            }
            plugin.getLogger().info("Poker: Stacks vom letzten Lauf zurückgegeben.");
        }
        save();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::hoverTick, 42L, 2L);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ---------- Tisch ----------

    /** Tisch setzen: Mitte = Block unter dem Admin, Dealerin an der Seite, auf die er schaut (Blick zur Mitte). */
    public synchronized void setTable(Location at) {
        if (desk != null) desk.removeAll();
        float yaw = Math.round((at.getYaw() + 180f) / 90f) * 90f;
        tisch = at.getWorld().getName() + ";" + at.getBlockX() + ";" + at.getBlockY() + ";" + at.getBlockZ() + ";" + yaw;
        if (display != null) display.remove();
        display = null;
        desk = null;
        save();
        buildDesk();
        render();
    }

    private de.kuby.kubyplugins.casino.PokerTable desk() {
        if (desk != null) return desk;
        Location t = table();
        if (t == null) return null;
        String[] p = tisch.split(";");
        float yaw = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
        desk = new de.kuby.kubyplugins.casino.PokerTable(plugin, "pk", t, yaw, ptag, pclick);
        return desk;
    }

    private void buildDesk() {
        de.kuby.kubyplugins.casino.PokerTable d = desk();
        if (d == null || d.origin().getWorld() == null) return;
        Location o = d.origin();
        if (!o.getWorld().isChunkLoaded(o.getBlockX() >> 4, o.getBlockZ() >> 4)) return;
        d.build();
        for (Seat s : seats) {
            Player p = Bukkit.getPlayer(s.id);
            if (p != null && !s.leaving) {
                d.vehicle(s.pos).addPassenger(p);
                d.ownPlate(s.pos, p, true);
            }
        }
    }

    public boolean hasTable() {
        return table() != null;
    }

    /** Entfernt Reste der letzten Sitzung und zeigt den Tisch. */
    public synchronized void respawn() {
        Location t = table();
        if (t == null || t.getWorld() == null) return;
        for (TextDisplay d : t.getWorld().getNearbyEntitiesByType(TextDisplay.class, t, 12)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
        }
        display = null;
        buildDesk();
        render();
    }

    public synchronized boolean isSeated(UUID id) {
        return seat(id) != null;
    }

    // ---------- Hinsetzen und Aufstehen ----------

    public synchronized String sit(Player p, long buyIn) {
        return sit(p, buyIn, -1);
    }

    /** Hinsetzen an Platz pos (-1 = der nächste freie Platz). */
    public synchronized String sit(Player p, long buyIn, int pos) {
        UUID id = p.getUniqueId();
        Location t = table();
        if (t == null) return "Der Poker-Tisch ist noch nicht eingerichtet.";
        String closed = plugin.casino().closedMessage();
        if (closed != null) return closed;
        if (!SURVIVAL.equals(plugin.modes().modeOf(id))) return "Poker gibt es nur in Survival.";
        if (!plugin.casinoVip().allowed(p)) return plugin.casinoVip().denyMessage();
        if (!near(p, t)) return "Geh näher an den Poker-Tisch.";
        if (seat(id) != null) return "Du sitzt schon am Tisch.";
        if (activeSeats() >= MAX_SEATS) return "Der Tisch ist voll (" + MAX_SEATS + " Plätze).";
        long bb = bigBlind();
        long min = bb * plugin.getConfig().getLong("casino.poker.min-buyin-bb", 40);
        long max = bb * plugin.getConfig().getLong("casino.poker.max-buyin-bb", 200);
        if (buyIn < min || buyIn > max) return "Buy-in zwischen " + min + " und " + max + " Chips.";
        int at = pos >= 0 && free(pos) ? pos : nearestFree(p.getLocation());
        if (at < 0) return "Alle Plätze sind besetzt.";
        if (!plugin.casino().takeChips(id, buyIn)) return "Du hast nicht genug Chips. Tausch sie an der Kasse.";
        Seat ns = new Seat(id, buyIn);
        ns.pos = at;
        seats.add(ns);
        seats.sort(java.util.Comparator.comparingInt(x -> x.pos));
        de.kuby.kubyplugins.casino.PokerTable d = desk();
        if (d != null && d.ready()) {
            d.vehicle(at).addPassenger(p);
            d.ownPlate(at, p, true);
        }
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_WOOL_PLACE, 0.7f, 1.2f);
        p.sendActionBar(Msg.parse("<gray>Du sitzt am Pokertisch · Stack <white>" + buyIn + "</white> · aufstehen mit Schleichen"));
        if (phase == Phase.WAITING) nextHandAt = Math.max(nextHandAt, System.currentTimeMillis() + 3000);
        announce("<gold>Poker: <white>" + nameOf(id) + " <gray>setzt sich mit " + buyIn + " Chips an den Tisch.");
        save();
        render();
        return null;
    }

    /** Aufstehen: laufendes Blatt wird gepasst, der Stack geht zurück auf das Chip-Konto. */
    public synchronized String stand(UUID id) {
        Seat s = seat(id);
        if (s == null) return "Du sitzt nicht am Tisch.";
        boolean myTurn = phase != Phase.WAITING && turn >= 0 && seats.get(turn) == s;
        if (s.inHand && !s.folded) {
            s.folded = true;
            s.last = "Fold";
        }
        long stack = s.stack;
        s.stack = 0;
        s.leaving = true;
        if (stack > 0) plugin.casino().giveChips(id, stack);
        Player p = Bukkit.getPlayer(id);
        if (p != null) Msg.send(p, "<green>Du stehst auf und nimmst " + stack + " Chips mit.");
        if (desk != null) {
            desk.removeVehicle(s.pos);
            desk.ownPlate(s.pos, p, false);
            desk.clearButtons(s.pos);
            desk.hideHint(s.pos, p);
            if (s.inHand) desk.muck(s.pos);
        }
        announce("<gold>Poker: <white>" + nameOf(id) + " <gray>steht auf.");
        if (phase == Phase.WAITING) {
            seats.remove(s);
        } else if (myTurn) {
            proceed();
        } else if (s.inHand) {
            // Nicht dran: nur prüfen, ob die Hand oder die Setzrunde damit entschieden ist.
            if (countLive() <= 1) {
                finishUncontested();
            } else if (roundComplete()) {
                nextStreet();
            }
        }
        save();
        render();
        return null;
    }

    /** Alle Stacks und Einsätze zurückgeben (Herunterfahren). */
    public synchronized void shutdown() {
        for (Seat s : seats) {
            long back = s.stack + (s.inHand ? s.committed : 0);
            if (back > 0) plugin.casino().giveChips(s.id, back);
        }
        for (Seat s : seats) if (desk != null) desk.removeVehicle(s.pos);
        seats.clear();
        phase = Phase.WAITING;
        if (desk != null) desk.removeAll();
        save();
    }

    // ---------- Aktionen ----------

    public synchronized String act(Player p, String action, long amount) {
        Seat s = seat(p.getUniqueId());
        if (s == null) return "Du sitzt nicht am Tisch. Hinsetzen mit /poker sitzen <Chips>.";
        switch (action) {
            case "sitout" -> {
                s.sitOut = !s.sitOut;
                Msg.send(p, s.sitOut ? "<gray>Pause: du setzt ab der nächsten Hand aus und behältst deinen Platz."
                        : "<green>Du spielst ab der nächsten Hand wieder mit.");
                render();
                return null;
            }
            case "show" -> {
                if (!s.mayShow) return "Zeigen geht nur direkt nach einem Gewinn ohne Showdown.";
                s.mayShow = false;
                if (desk != null) desk.reveal(s.pos);
                announce("<gold>Poker: <white>" + nameOf(s.id) + " <gray>zeigt " + cards(s.hole));
                return null;
            }
            default -> {
            }
        }
        if (phase == Phase.WAITING || turn < 0 || seats.get(turn) != s) return "Du bist gerade nicht dran.";
        long toCall = currentBet - s.bet;
        long minTo = Math.min(currentBet + minRaise, s.bet + s.stack);
        long maxTo = s.bet + s.stack;
        switch (action) {
            case "raisemode" -> {
                s.raising = true;
                s.raiseTo = Math.max(minTo, Math.min(maxTo, currentBet + Math.max(minRaise, pot() / 2)));
                showButtons(s);
                return null;
            }
            case "minus", "plus", "half", "pot" -> {
                long bb = bigBlind();
                long v = switch (action) {
                    case "minus" -> s.raiseTo - bb;
                    case "plus" -> s.raiseTo + bb;
                    case "half" -> currentBet + toCall + (pot() + toCall) / 2;
                    default -> currentBet + toCall + pot() + toCall;
                };
                s.raiseTo = Math.max(minTo, Math.min(maxTo, v));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.5f, 1.6f);
                showButtons(s);
                return null;
            }
            case "back" -> {
                s.raising = false;
                showButtons(s);
                return null;
            }
            case "ok" -> {
                s.raising = false;
                action = "raise";
                amount = s.raiseTo;
            }
            default -> {
            }
        }
        switch (action) {
            case "fold" -> {
                s.folded = true;
                s.last = "Fold";
            }
            case "check" -> {
                if (toCall > 0) return "Du musst mitgehen (/poker call) oder passen (/poker fold).";
                s.last = "Check";
            }
            case "call" -> {
                if (toCall <= 0) {
                    s.last = "Check";
                } else {
                    pay(s, Math.min(toCall, s.stack));
                    s.last = s.allIn ? "All-in" : "Call " + s.bet;
                }
            }
            case "allin" -> {
                String err = raiseTo(s, s.bet + s.stack);
                if (err != null) return err;
            }
            case "raise" -> {
                String err = raiseTo(s, amount);
                if (err != null) return err;
            }
            default -> {
                return "Unbekannte Aktion. Möglich: fold, check, call, raise <Betrag>, allin";
            }
        }
        s.acted = true;
        s.raising = false;
        actionSound(s, p);
        if (desk != null) {
            desk.clearButtons(s.pos);
            desk.bet(s.pos, s.bet, false);
            if (s.folded) desk.muck(s.pos);
        }
        proceed();
        save();
        render();
        return null;
    }

    /** Erhöht den eigenen Einsatz in dieser Runde auf "to" (nicht um). */
    private String raiseTo(Seat s, long to) {
        long maxTo = s.bet + s.stack;
        if (to > maxTo) to = maxTo;
        if (to <= currentBet) {
            if (to == maxTo) {
                // All-in, das nur zum Mitgehen reicht.
                pay(s, to - s.bet);
                s.last = "All-in " + s.bet;
                return null;
            }
            return "Erhöhen geht nur über " + currentBet + ". Mitgehen mit /poker call.";
        }
        long raiseSize = to - currentBet;
        if (raiseSize < minRaise && to < maxTo) {
            return "Mindestens auf " + (currentBet + minRaise) + " erhöhen (oder All-in).";
        }
        pay(s, to - s.bet);
        if (raiseSize >= minRaise) {
            minRaise = raiseSize;
            // Volle Erhöhung: alle anderen müssen neu entscheiden.
            for (Seat o : seats) if (o != s) o.acted = false;
        }
        currentBet = to;
        s.last = s.allIn ? "All-in " + to : "Raise " + to;
        return null;
    }

    private void pay(Seat s, long amount) {
        amount = Math.max(0, Math.min(amount, s.stack));
        s.stack -= amount;
        s.bet += amount;
        s.committed += amount;
        if (s.stack == 0) s.allIn = true;
    }

    // ---------- Ablauf ----------

    private synchronized void tick() {
        // Weggegangene oder ausgeloggte Spieler stehen auf.
        Location t = table();
        for (Seat s : new ArrayList<>(seats)) {
            if (s.leaving) continue;
            Player p = Bukkit.getPlayer(s.id);
            if (p == null || t == null || !near(p, t) || !SURVIVAL.equals(plugin.modes().modeOf(s.id))) {
                stand(s.id);
            }
        }
        if (phase == Phase.WAITING) {
            if (eligible() >= 2 && System.currentTimeMillis() >= nextHandAt) startHand();
            return;
        }
        long limit = plugin.getConfig().getLong("casino.poker.turn-seconds", 30) * 1000;
        if (turn >= 0 && desk != null) {
            double left = 1 - (System.currentTimeMillis() - turnStarted) / (double) limit;
            desk.timer(seats.get(turn).pos, Math.max(0, left));
            Player tp = Bukkit.getPlayer(seats.get(turn).id);
            long secs = Math.max(0, (limit - (System.currentTimeMillis() - turnStarted)) / 1000);
            if (tp != null && secs <= 5) tp.playSound(tp.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.0f + (5 - secs) * 0.15f);
        }
        if (turn >= 0 && System.currentTimeMillis() - turnStarted > limit) {
            Seat s = seats.get(turn);
            boolean canCheck = currentBet - s.bet <= 0;
            s.folded = !canCheck;
            s.last = canCheck ? "Check (Zeit)" : "Fold (Zeit)";
            s.acted = true;
            if (desk != null) {
                desk.clearButtons(s.pos);
                if (s.folded) desk.muck(s.pos);
            }
            proceed();
            save();
        }
        for (Seat s : seats) {
            if (!s.inHand || s.folded) continue;
            Player p = Bukkit.getPlayer(s.id);
            if (p != null) p.sendActionBar(Msg.parse("<gold>Deine Karten: " + cards(s.hole)
                    + " <gray>· Stack <white>" + s.stack + (seats.indexOf(s) == turn ? " <gold>· Du bist dran!" : "")));
        }
        render();
    }

    private void startHand() {
        for (Seat s : seats) {
            s.mayShow = false;
            s.raising = false;
            s.inHand = !s.leaving && !s.sitOut && s.stack > 0;
            s.folded = false;
            s.allIn = false;
            s.acted = false;
            s.bet = 0;
            s.committed = 0;
            s.last = "";
            s.hole.clear();
        }
        dealer = nextIndex(dealer, s -> s.inHand);
        board.clear();
        deck = freshDeck();
        for (int round = 0; round < 2; round++) {
            for (int i = 1; i <= seats.size(); i++) {
                Seat s = seats.get((dealer + i) % seats.size());
                if (s.inHand) s.hole.add(deck.pollFirst());
            }
        }
        long sbAmount = smallBlind();
        long bbAmount = bigBlind();
        boolean headsUp = eligibleInHand() == 2;
        int sb = headsUp ? dealer : nextIndex(dealer, s -> s.inHand);
        int bb = nextIndex(sb, s -> s.inHand);
        pay(seats.get(sb), sbAmount);
        seats.get(sb).last = "Small Blind";
        pay(seats.get(bb), bbAmount);
        seats.get(bb).last = "Big Blind";
        currentBet = Math.max(seats.get(sb).bet, seats.get(bb).bet);
        minRaise = bbAmount;
        phase = Phase.PREFLOP;
        turn = bb;
        if (desk != null && desk.ready()) {
            desk.clearBoard();
            desk.pot(0, null, false);
            desk.dealerButton(seats.get(dealer).pos);
            for (int i = 0; i < 6; i++) playAt(org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.5f + i * 0.05f, i * 2L);
            int k = 0;
            for (Seat s : seats) {
                desk.clearHole(s.pos);
                desk.bet(s.pos, s.bet, false);
                if (!s.inHand) continue;
                Seat fs = s;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (desk == null || !fs.inHand) return;
                    desk.holeCards(fs.pos, fs.id, new String[]{model(fs.hole.get(0)), model(fs.hole.get(1))});
                    desk.dealerGesture();
                    playAt(org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.4f, 0);
                }, 12L + 4L * k++);
            }
        }
        for (Seat s : seats) {
            if (!s.inHand) continue;
            Player p = Bukkit.getPlayer(s.id);
            if (p != null) {
                Msg.send(p, "<gold>Poker: <gray>Neue Hand. Deine Karten: " + cards(s.hole));
                Sfx.play(plugin, p, Sfx.Kind.OPEN);
                updateHint(s);
            }
        }
        advanceTurn();
        save();
        render();
    }

    /** Nach jeder Aktion: Hand vorbei, nächste Straße oder nächster Spieler. */
    private void proceed() {
        if (phase == Phase.WAITING) return;
        if (countLive() <= 1) {
            finishUncontested();
            return;
        }
        if (roundComplete()) {
            nextStreet();
        } else {
            advanceTurn();
        }
    }

    private boolean roundComplete() {
        for (Seat s : seats) {
            if (!s.inHand || s.folded || s.allIn) continue;
            if (!s.acted || s.bet < currentBet) return false;
        }
        return true;
    }

    /** Sucht den nächsten Spieler, der handeln muss, und fordert ihn auf. */
    private void advanceTurn() {
        int start = turn;
        for (int i = 1; i <= seats.size(); i++) {
            int idx = (start + i) % seats.size();
            Seat s = seats.get(idx);
            if (!s.inHand || s.folded || s.allIn) continue;
            if (!s.acted || s.bet < currentBet) {
                turn = idx;
                turnStarted = System.currentTimeMillis();
                prompt(s);
                return;
            }
        }
        nextStreet();
    }

    private void nextStreet() {
        if (desk != null) {
            desk.collectBets();
            playAt(org.bukkit.Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.3f, 2);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (desk != null) desk.pot(pot(), sidePotText(), false);
            }, 10L);
        }
        for (Seat s : seats) {
            s.bet = 0;
            s.acted = false;
            if (s.inHand && !s.folded && !s.allIn) s.last = "";
        }
        currentBet = 0;
        minRaise = bigBlind();
        switch (phase) {
            case PREFLOP -> {
                deal(3);
                phase = Phase.FLOP;
            }
            case FLOP -> {
                deal(1);
                phase = Phase.TURN;
            }
            case TURN -> {
                deal(1);
                phase = Phase.RIVER;
            }
            default -> {
                showdown();
                return;
            }
        }
        // Können höchstens noch einer setzen, läuft das Board bis zum Ende durch.
        int canBet = 0;
        for (Seat s : seats) if (s.inHand && !s.folded && !s.allIn) canBet++;
        if (canBet <= 1) {
            nextStreet();
            return;
        }
        turn = dealer;
        advanceTurn();
    }

    private void deal(int n) {
        deck.pollFirst(); // Burn
        for (int i = 0; i < n; i++) {
            Card c = deck.pollFirst();
            board.add(c);
            int k = board.size() - 1;
            if (desk != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (desk != null) {
                        desk.boardCard(k, model(c));
                        desk.dealerGesture();
                    }
                }, 14L + 5L * i);
            }
        }
        announce("<gold>Poker: <gray>Board " + cards(board));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Seat s : seats) if (s.inHand && !s.folded) updateHint(s);
        }, 30L);
    }

    private void finishUncontested() {
        Seat winner = null;
        long pot = 0;
        for (Seat s : seats) {
            if (!s.inHand) continue;
            pot += s.committed;
            if (!s.folded) winner = s;
        }
        Map<Seat, Long> won = new HashMap<>();
        if (winner != null) {
            if (winner.leaving) {
                plugin.casino().giveChips(winner.id, pot);
            } else {
                winner.stack += pot;
            }
            won.put(winner, pot);
            winner.mayShow = true;
            announce("<gold>Poker: <white>" + nameOf(winner.id) + " <gray>gewinnt <gold>" + pot + " Chips<gray>, alle anderen haben gepasst.");
            Player wp = Bukkit.getPlayer(winner.id);
            if (wp != null) Msg.send(wp, "<click:run_command:'/poker zeigen'><hover:show_text:'Deine Karten allen zeigen'><aqua>[Karten zeigen]</aqua></hover></click>");
            if (desk != null) {
                desk.collectBets();
                desk.pot(pot, null, false);
                Seat w = winner;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (desk != null) desk.pushPot(w.pos);
                }, 12L);
                desk.buttons(w.pos, new String[]{"show"}, null);
            }
        }
        endHand(won);
    }

    private void showdown() {
        List<Seat> contenders = new ArrayList<>();
        for (Seat s : seats) if (s.inHand && !s.folded) contenders.add(s);
        Map<Seat, Long> score = new HashMap<>();
        for (Seat s : contenders) {
            List<Card> seven = new ArrayList<>(s.hole);
            seven.addAll(board);
            score.put(s, best(seven));
            announce("<gold>Poker: <white>" + nameOf(s.id) + " <gray>zeigt " + cards(s.hole)
                    + " <gray>· " + CATEGORY[(int) (score.get(s) >> 20)]);
            if (desk != null) desk.reveal(s.pos);
        }
        if (desk != null) {
            desk.collectBets();
            desk.pot(pot(), sidePotText(), false);
            playAt(org.bukkit.Sound.BLOCK_NOTE_BLOCK_SNARE, 0.7f, 1.0f, 0);
        }
        // Side-Pots: Schicht für Schicht nach dem kleinsten Einsatz der Verbliebenen.
        Map<Seat, Long> left = new HashMap<>();
        for (Seat s : seats) if (s.inHand) left.put(s, s.committed);
        Map<Seat, Long> won = new HashMap<>();
        while (true) {
            long level = Long.MAX_VALUE;
            for (Seat s : contenders) if (left.get(s) > 0) level = Math.min(level, left.get(s));
            if (level == Long.MAX_VALUE) break;
            // Berechtigt sind alle Verbliebenen, die in dieser Schicht noch etwas offen haben.
            List<Seat> eligible = new ArrayList<>();
            for (Seat s : contenders) if (left.get(s) > 0) eligible.add(s);
            long pot = 0;
            for (Map.Entry<Seat, Long> e : left.entrySet()) {
                long take = Math.min(e.getValue(), level);
                pot += take;
                e.setValue(e.getValue() - take);
            }
            award(pot, eligible, score, won);
            // Wer schon ganz ausgezahlt ist, ist bei den höheren Schichten nicht mehr dabei.
            contenders.removeIf(s -> left.get(s) == 0);
        }
        long rest = 0;
        for (long v : left.values()) rest += v;
        if (rest > 0) {
            List<Seat> all = new ArrayList<>(score.keySet());
            award(rest, all, score, won);
        }
        endHand(won);
    }

    private void award(long pot, List<Seat> eligible, Map<Seat, Long> score, Map<Seat, Long> won) {
        if (pot <= 0 || eligible.isEmpty()) return;
        long best = Long.MIN_VALUE;
        for (Seat s : eligible) best = Math.max(best, score.get(s));
        List<Seat> winners = new ArrayList<>();
        // Reihenfolge links vom Dealer, damit ein Rest-Chip fair verteilt wird.
        for (int i = 1; i <= seats.size(); i++) {
            Seat s = seats.get((dealer + i) % seats.size());
            if (eligible.contains(s) && score.get(s) == best) winners.add(s);
        }
        long share = pot / winners.size();
        long remainder = pot % winners.size();
        for (int i = 0; i < winners.size(); i++) {
            Seat w = winners.get(i);
            long amount = share + (i == 0 ? remainder : 0);
            if (w.leaving) {
                plugin.casino().giveChips(w.id, amount);
            } else {
                w.stack += amount;
            }
            won.merge(w, amount, Long::sum);
            announce("<gold>Poker: <white>" + nameOf(w.id) + " <gray>gewinnt <gold>" + amount + " Chips <gray>mit "
                    + CATEGORY[(int) (best >> 20)]);
            if (desk != null) {
                desk.glowHole(w.pos, 0xFFD34D);
                Seat ws = w;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (desk != null) desk.pushPot(ws.pos);
                }, 30L);
                desk.dealerText(Msg.parse("<gradient:#FFE070:#F2A21C><b>" + CATEGORY[(int) (best >> 20)].toUpperCase() + "</b></gradient>"
                        + "\n<white>" + nameOf(w.id) + " <gold>+" + amount));
                Player wp = Bukkit.getPlayer(w.id);
                if (wp != null) {
                    wp.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<gradient:#FFE070:#F2A21C><b>POT GEWONNEN</b></gradient>"),
                            Msg.parse("<white>" + CATEGORY[(int) (best >> 20)] + " <gold>+" + amount + " Chips")));
                    wp.playSound(wp.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
                }
            }
        }
    }

    private void endHand(Map<Seat, Long> won) {
        Location t = table();
        Location fxAt = t == null ? null : t.clone().add(0, 3.2, 0);
        for (Seat s : seats) {
            if (!s.inHand) continue;
            long paid = won.getOrDefault(s, 0L);
            plugin.casinoLog().record("Poker", s.id, s.committed, paid);
            long net = paid - s.committed;
            CasinoFx.show(plugin, s.id, fxAt, net > 0 ? CasinoFx.Result.WIN : net == 0 ? CasinoFx.Result.PUSH : CasinoFx.Result.LOSE, net);
            s.inHand = false;
            s.committed = 0;
            s.bet = 0;
        }
        for (Seat s : new ArrayList<>(seats)) {
            if (s.leaving) {
                seats.remove(s);
            } else if (s.stack <= 0) {
                seats.remove(s);
                Player p = Bukkit.getPlayer(s.id);
                if (p != null) Msg.send(p, "<red>Dein Stack ist leer. Du kannst dich mit /poker sitzen neu einkaufen.");
            }
        }
        if (dealer >= seats.size()) dealer = -1;
        phase = Phase.WAITING;
        turn = -1;
        board.clear();
        nextHandAt = System.currentTimeMillis() + PAUSE_MS;
        if (desk != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (desk == null || phase != Phase.WAITING) return;
                desk.clearBoard();
                for (int i = 0; i < de.kuby.kubyplugins.casino.PokerTable.SEATS; i++) {
                    desk.clearHole(i);
                    desk.clearButtons(i);
                    desk.bet(i, 0, false);
                }
                desk.pot(0, null, false);
                for (Seat s : seats) desk.hideHint(s.pos, Bukkit.getPlayer(s.id));
            }, 90L);
        }
        save();
        render();
    }

    private void prompt(Seat s) {
        Player p = Bukkit.getPlayer(s.id);
        if (p == null) return;
        long toCall = currentBet - s.bet;
        long minTo = Math.min(currentBet + minRaise, s.bet + s.stack);
        long pot = pot();
        String call = toCall > 0
                ? "<click:run_command:'/poker call'><hover:show_text:'Mitgehen'><green>[Call " + Math.min(toCall, s.stack) + "]</green></hover></click>"
                : "<click:run_command:'/poker check'><hover:show_text:'Schieben'><green>[Check]</green></hover></click>";
        String raise = s.stack > toCall
                ? " <click:run_command:'/poker raise " + minTo + "'><hover:show_text:'Mindesterhöhung'><yellow>[Raise auf " + minTo + "]</yellow></hover></click>"
                + " <click:suggest_command:'/poker raise '><hover:show_text:'Betrag selbst eingeben'><yellow>[Raise …]</yellow></hover></click>"
                : "";
        Msg.send(p, "<gold>Poker: <white>Du bist dran. <gray>Zu zahlen: <white>" + Math.max(0, toCall)
                + " <gray>· Pot: <white>" + pot + " <gray>· Deine Karten: " + cards(s.hole));
        Msg.send(p, "<click:run_command:'/poker fold'><hover:show_text:'Passen'><red>[Fold]</red></hover></click> " + call + raise
                + " <click:run_command:'/poker allin'><hover:show_text:'Alles setzen'><gold>[All-in " + s.stack + "]</gold></hover></click>");
        Sfx.play(plugin, p, Sfx.Kind.PING);
        s.raising = false;
        showButtons(s);
        if (desk != null) desk.dealerText(Msg.parse("<gray>" + nameOf(s.id) + " ist dran"));
    }

    // ---------- Anzeige ----------

    private void render() {
        Location t = table();
        if (t == null || t.getWorld() == null) return;
        if (!t.getWorld().isChunkLoaded(t.getBlockX() >> 4, t.getBlockZ() >> 4)) return;
        if (desk != null && desk.ready()) {
            if (display != null && display.isValid()) display.remove();
            display = null;
        } else if (display == null || !display.isValid()) {
            display = t.getWorld().spawn(t.clone().add(0, 2.0, 0), TextDisplay.class, e -> {
                e.setBillboard(Display.Billboard.VERTICAL);
                e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
        }
        StringBuilder sb = new StringBuilder("<gold><b>POKER</b> <gray>Blinds " + smallBlind() + "/" + bigBlind());
        if (phase == Phase.WAITING) {
            sb.append("\n<gray>").append(eligible() < 2 ? "Warte auf Spieler · /poker sitzen" : "Nächste Hand gleich ...");
        } else {
            sb.append("\n<white>Board: ").append(board.isEmpty() ? "<gray>—" : cards(board)).append(" <gray>· Pot <gold>").append(pot());
        }
        for (int i = 0; i < seats.size(); i++) {
            Seat s = seats.get(i);
            if (s.leaving) continue;
            sb.append("\n").append(i == dealer && phase != Phase.WAITING ? "<gold>(D) " : "")
                    .append(s.folded ? "<dark_gray>" : "<white>").append(nameOf(s.id))
                    .append(" <gray>").append(s.stack);
            if (s.bet > 0) sb.append(" · Einsatz ").append(s.bet);
            if (!s.last.isEmpty()) sb.append(" · ").append(s.last);
            if (i == turn && phase != Phase.WAITING) sb.append(" <gold>◀");
        }
        if (display != null && display.isValid()) display.text(Msg.parse(sb.toString()));
        if (desk != null && desk.ready()) {
            boolean[] used = new boolean[de.kuby.kubyplugins.casino.PokerTable.SEATS];
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (s.leaving) continue;
                used[s.pos] = true;
                String head = (i == turn && phase != Phase.WAITING ? "<gold>▶ " : "") + (s.folded ? "<dark_gray>" : "<white>") + nameOf(s.id);
                String line2 = "<#F2C94C>" + s.stack + " <gray>Chips" + (s.sitOut ? " <dark_gray>· Pause" : "")
                        + (s.last.isEmpty() ? "" : " <dark_gray>· <aqua>" + s.last);
                desk.plate(s.pos, Msg.parse(head + "\n" + line2));
            }
            for (int k = 0; k < used.length; k++) {
                if (!used[k]) desk.plate(k, Msg.parse("<#F2B829>Freier Platz\n<gray>Rechtsklick zum Hinsetzen"));
            }
            if (phase == Phase.WAITING) {
                desk.dealerText(Msg.parse("<gold><b>POKER</b>\n<gray>Blinds " + smallBlind() + "/" + bigBlind() + " · "
                        + (eligible() < 2 ? "Warte auf Spieler" : "Nächste Hand gleich")));
            }
        }
    }

    // ---------- Tisch: Knöpfe, Hinweise, Klicks ----------

    private void showButtons(Seat s) {
        if (desk == null || !desk.ready()) return;
        long toCall = currentBet - s.bet;
        if (s.raising) {
            desk.buttons(s.pos, new String[]{"back", "minus", "half", "pot", "plus", "ok"},
                    Msg.parse("<gray>Erhöhen auf <gold><b>" + s.raiseTo + "</b>"));
            return;
        }
        boolean canRaise = s.stack > toCall && s.bet + s.stack > currentBet;
        desk.buttons(s.pos, new String[]{"fold", toCall > 0 ? "call" : "check", canRaise ? "raise" : null, "allin"},
                toCall > 0 ? Msg.parse("<gray>Mitgehen <white>" + Math.min(toCall, s.stack)) : null);
    }

    private void updateHint(Seat s) {
        if (desk == null || s.hole.size() < 2) return;
        List<Card> all = new ArrayList<>(s.hole);
        all.addAll(board);
        String label;
        if (all.size() >= 5) {
            label = CATEGORY[(int) (best(all) >> 20)];
        } else {
            label = s.hole.get(0).rank() == s.hole.get(1).rank() ? "Paar" : "Höchste Karte";
        }
        desk.hint(s.pos, Bukkit.getPlayer(s.id), Msg.parse("<aqua>" + label));
    }

    private String sidePotText() {
        List<Long> levels = new ArrayList<>();
        for (Seat s : seats) if (s.inHand && !s.folded && s.allIn) levels.add(s.committed);
        if (levels.isEmpty()) return "";
        levels.sort(Long::compare);
        return levels.size() + " Side-Pot" + (levels.size() > 1 ? "s" : "");
    }

    private static String model(Card c) {
        String r = switch (c.rank()) {
            case 14 -> "a";
            case 13 -> "k";
            case 12 -> "q";
            case 11 -> "j";
            default -> String.valueOf(c.rank());
        };
        char s = switch (c.suit()) {
            case '♥' -> 'h';
            case '♦' -> 'd';
            case '♠' -> 's';
            default -> 'c';
        };
        return r + s;
    }

    private void actionSound(Seat s, Player p) {
        String a = s.last;
        org.bukkit.Sound snd = a.startsWith("Fold") ? org.bukkit.Sound.ITEM_BOOK_PAGE_TURN
                : a.startsWith("Check") ? org.bukkit.Sound.BLOCK_WOOD_HIT
                : a.startsWith("All-in") ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASEDRUM
                : org.bukkit.Sound.BLOCK_CHAIN_PLACE;
        playAt(snd, 0.8f, a.startsWith("Check") ? 0.8f : 1.2f, 0);
        if (a.startsWith("Check")) playAt(org.bukkit.Sound.BLOCK_WOOD_HIT, 0.8f, 0.8f, 3);
        if (a.startsWith("All-in")) {
            for (Seat o : seats) {
                Player op = Bukkit.getPlayer(o.id);
                if (op != null) op.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<gradient:#AA52F0:#FF4D8D><b>ALL-IN!</b></gradient>"),
                        Msg.parse("<white>" + nameOf(s.id) + " <gray>setzt alles")));
            }
        }
    }

    private void playAt(org.bukkit.Sound snd, float vol, float pitch, long delay) {
        Location t = table();
        if (t == null || t.getWorld() == null) return;
        Location at = t.clone().add(0, 1, 0);
        if (delay <= 0) at.getWorld().playSound(at, snd, vol, pitch);
        else plugin.getServer().getScheduler().runTaskLater(plugin, () -> at.getWorld().playSound(at, snd, vol, pitch), delay);
    }

    private boolean free(int pos) {
        for (Seat s : seats) if (s.pos == pos && !s.leaving) return false;
        return pos >= 0 && pos < de.kuby.kubyplugins.casino.PokerTable.SEATS;
    }

    private int nearestFree(Location l) {
        de.kuby.kubyplugins.casino.PokerTable d = desk();
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < de.kuby.kubyplugins.casino.PokerTable.SEATS; i++) {
            if (!free(i)) continue;
            double dist = d == null ? i : d.seatLocation(i).distanceSquared(l);
            if (dist < bd) {
                bd = dist;
                best = i;
            }
        }
        return best;
    }

    /** Nachkaufen zwischen den Händen bis zum Maximum. */
    public synchronized String topUp(Player p, long amount) {
        Seat s = seat(p.getUniqueId());
        if (s == null) return "Du sitzt nicht am Tisch.";
        if (s.inHand && phase != Phase.WAITING) return "Nachkaufen geht nur zwischen den Händen.";
        long max = bigBlind() * plugin.getConfig().getLong("casino.poker.max-buyin-bb", 200);
        long add = Math.min(amount, max - s.stack);
        if (add <= 0) return "Dein Stack ist schon beim Maximum (" + max + ").";
        if (!plugin.casino().takeChips(p.getUniqueId(), add)) return "Du hast nicht genug Chips.";
        s.stack += add;
        save();
        render();
        Msg.send(p, "<green>+" + add + " Chips nachgekauft. Stack: " + s.stack);
        return null;
    }

    private void handle(Player p, org.bukkit.entity.Entity e) {
        String v = e.getPersistentDataContainer().get(pclick, PersistentDataType.STRING);
        if (v == null) return;
        String[] parts = v.split(":");
        String err = null;
        switch (parts[1]) {
            case "seat" -> {
                Seat cur = seat(p.getUniqueId());
                if (cur != null) return;
                int pos = Integer.parseInt(parts[2]);
                if (!free(pos)) {
                    err = "Der Platz ist besetzt.";
                } else {
                    long bb = bigBlind();
                    long min = bb * plugin.getConfig().getLong("casino.poker.min-buyin-bb", 40);
                    long max = bb * plugin.getConfig().getLong("casino.poker.max-buyin-bb", 200);
                    StringBuilder sb = new StringBuilder("<gold>Poker: <gray>Mit wie vielen Chips setzt du dich? ");
                    for (long amt : new long[]{min, bb * 100, max}) {
                        sb.append("<click:run_command:'/poker sitzen ").append(amt).append(" ").append(pos).append("'><hover:show_text:'")
                                .append(amt).append(" Chips'><green>[").append(amt).append("]</green></hover></click> ");
                    }
                    Msg.send(p, sb.toString());
                }
            }
            case "act" -> {
                String a = parts[3];
                err = act(p, a.equals("raise") ? "raisemode" : a, 0);
            }
            case "dealer" -> {
                if (seat(p.getUniqueId()) == null) {
                    int pos = nearestFree(p.getLocation());
                    if (pos < 0) err = "Alle Plätze sind besetzt.";
                    else err = sit(p, bigBlind() * 100, pos);
                }
            }
            default -> {
            }
        }
        if (err != null) Msg.send(p, "<red>" + err);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(pclick, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        synchronized (this) {
            handle(e.getPlayer(), e.getRightClicked());
        }
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (!e.getAttacked().getPersistentDataContainer().has(pclick, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        synchronized (this) {
            handle(e.getPlayer(), e.getAttacked());
        }
    }

    @org.bukkit.event.EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String v = e.getDismounted().getPersistentDataContainer().get(ptag, PersistentDataType.STRING);
        if (v == null || !v.endsWith(":vehicle")) return;
        synchronized (this) {
            if (seat(p.getUniqueId()) != null) stand(p.getUniqueId());
        }
    }

    @org.bukkit.event.EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent e) {
        Location t = table();
        if (t == null || !e.getWorld().equals(t.getWorld())) return;
        if ((t.getBlockX() >> 4) != e.getChunk().getX() || (t.getBlockZ() >> 4) != e.getChunk().getZ()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                if (desk() != null && !desk.ready()) {
                    buildDesk();
                    render();
                }
            }
        }, 5L);
    }

    private synchronized void hoverTick() {
        if (desk == null || !desk.ready() || turn < 0 || phase == Phase.WAITING) return;
        for (Seat s : seats) {
            Player p = Bukkit.getPlayer(s.id);
            if (p == null) continue;
            if (desk.hover(s.pos, p.getEyeLocation(), p.getEyeLocation().getDirection())) {
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, 1.9f);
            }
        }
    }

    // ---------- Karten und Auswertung ----------

    private Deque<Card> freshDeck() {
        List<Card> cards = new ArrayList<>();
        for (char suit : new char[]{'♠', '♥', '♦', '♣'}) {
            for (int rank = 2; rank <= 14; rank++) cards.add(new Card(rank, suit));
        }
        Collections.shuffle(cards);
        return new ArrayDeque<>(cards);
    }

    /** Bestes Blatt aus 7 Karten. Höhere Zahl = besseres Blatt; die Kategorie steht ab Bit 20. */
    private static long best(List<Card> seven) {
        long best = -1;
        int n = seven.size();
        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++)
                for (int c = b + 1; c < n; c++)
                    for (int d = c + 1; d < n; d++)
                        for (int e = d + 1; e < n; e++) {
                            best = Math.max(best, score(List.of(seven.get(a), seven.get(b), seven.get(c), seven.get(d), seven.get(e))));
                        }
        return best;
    }

    private static long score(List<Card> five) {
        int[] count = new int[15];
        boolean flush = true;
        for (Card c : five) {
            count[c.rank()]++;
            if (c.suit() != five.get(0).suit()) flush = false;
        }
        // Ränge nach Häufigkeit, dann Höhe sortiert.
        List<Integer> order = new ArrayList<>();
        for (int r = 14; r >= 2; r--) if (count[r] > 0) order.add(r);
        order.sort((x, y) -> count[y] != count[x] ? Integer.compare(count[y], count[x]) : Integer.compare(y, x));
        int straightHigh = 0;
        if (order.size() == 5) {
            int hi = order.get(0);
            int lo = order.get(4);
            if (hi - lo == 4) straightHigh = hi;
            if (hi == 14 && order.get(1) == 5) straightHigh = 5; // A-2-3-4-5
        }
        int category;
        List<Integer> ranks = order;
        if (straightHigh > 0 && flush) {
            category = 8;
            ranks = List.of(straightHigh);
        } else if (count[order.get(0)] == 4) {
            category = 7;
        } else if (count[order.get(0)] == 3 && order.size() == 2) {
            category = 6;
        } else if (flush) {
            category = 5;
        } else if (straightHigh > 0) {
            category = 4;
            ranks = List.of(straightHigh);
        } else if (count[order.get(0)] == 3) {
            category = 3;
        } else if (count[order.get(0)] == 2 && count[order.get(1)] == 2) {
            category = 2;
        } else if (count[order.get(0)] == 2) {
            category = 1;
        } else {
            category = 0;
        }
        long value = 0;
        for (int i = 0; i < 5; i++) value = value * 16 + (i < ranks.size() ? ranks.get(i) : 0);
        return ((long) category << 20) | value;
    }

    // ---------- Hilfen ----------

    private long smallBlind() {
        return plugin.getConfig().getLong("casino.poker.small-blind", 10);
    }

    private long bigBlind() {
        return plugin.getConfig().getLong("casino.poker.big-blind", 20);
    }

    private long pot() {
        long pot = 0;
        for (Seat s : seats) if (s.inHand) pot += s.committed;
        return pot;
    }

    private int countLive() {
        int n = 0;
        for (Seat s : seats) if (s.inHand && !s.folded) n++;
        return n;
    }

    private int eligible() {
        int n = 0;
        for (Seat s : seats) if (!s.leaving && s.stack > 0) n++;
        return n;
    }

    private int eligibleInHand() {
        int n = 0;
        for (Seat s : seats) if (s.inHand) n++;
        return n;
    }

    private int activeSeats() {
        int n = 0;
        for (Seat s : seats) if (!s.leaving) n++;
        return n;
    }

    private int nextIndex(int from, java.util.function.Predicate<Seat> ok) {
        for (int i = 1; i <= seats.size(); i++) {
            int idx = Math.floorMod(from + i, seats.size());
            if (ok.test(seats.get(idx))) return idx;
        }
        return Math.max(0, from);
    }

    private Seat seat(UUID id) {
        for (Seat s : seats) if (s.id.equals(id) && !s.leaving) return s;
        return null;
    }

    private boolean near(Player p, Location t) {
        return p.getWorld().equals(t.getWorld()) && p.getLocation().distanceSquared(t) <= RANGE * RANGE;
    }

    private String cards(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) sb.append(c.label()).append(" ");
        return sb.toString().trim();
    }

    private void announce(String mini) {
        for (Seat s : seats) {
            Player p = Bukkit.getPlayer(s.id);
            if (p != null) Msg.send(p, mini);
        }
    }

    private String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "?" : name;
    }

    private Location table() {
        if (tisch == null) return null;
        String[] p = tisch.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, (int) Double.parseDouble(p[1]) + 0.5, (int) Double.parseDouble(p[2]), (int) Double.parseDouble(p[3]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Sichert Tisch und alles, was gerade am Tisch liegt (Stack + Einsatz der laufenden Hand). */
    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        if (tisch != null) out.set("tisch", tisch);
        for (Seat s : seats) {
            long held = s.stack + (s.inHand ? s.committed : 0);
            if (held > 0) out.set("escrow." + s.id, held);
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("poker.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
