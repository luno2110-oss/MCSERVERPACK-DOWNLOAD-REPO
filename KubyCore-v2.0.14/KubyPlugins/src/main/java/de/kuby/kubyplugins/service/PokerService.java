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
 * Texas Hold'em am Poker-Tisch (VIP-Lounge), Spieler gegen Spieler, ohne Abzug durch das Casino.
 * Blinds und Buy-in aus der config.yml (Standard 10/20, 40 bis 200 Big Blinds). Bis zu 6 Plätze.
 * Aktionen per /poker (Buttons im Chat), die eigenen Karten stehen in der Aktionsleiste.
 * Sicherheit: Stacks und Einsätze werden laufend in poker.yml gesichert und nach einem Absturz zurückgegeben.
 * Wer geht, ausloggt oder zu weit weg ist, passt und bekommt seinen Stack zurück.
 */
public final class PokerService {

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

    public PokerService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "poker.yml");
        this.tag = new NamespacedKey(plugin, "kp_poker");
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
    }

    // ---------- Tisch ----------

    public synchronized void setTable(Location block) {
        tisch = block.getWorld().getName() + ";" + block.getBlockX() + ";" + block.getBlockY() + ";" + block.getBlockZ();
        if (display != null) display.remove();
        display = null;
        save();
        render();
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
        render();
    }

    public synchronized boolean isSeated(UUID id) {
        return seat(id) != null;
    }

    // ---------- Hinsetzen und Aufstehen ----------

    public synchronized String sit(Player p, long buyIn) {
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
        if (!plugin.casino().takeChips(id, buyIn)) return "Du hast nicht genug Chips. Tausch sie an der Kasse.";
        seats.add(new Seat(id, buyIn));
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
        seats.clear();
        phase = Phase.WAITING;
        save();
    }

    // ---------- Aktionen ----------

    public synchronized String act(Player p, String action, long amount) {
        Seat s = seat(p.getUniqueId());
        if (s == null) return "Du sitzt nicht am Tisch. Hinsetzen mit /poker sitzen <Chips>.";
        if (phase == Phase.WAITING || turn < 0 || seats.get(turn) != s) return "Du bist gerade nicht dran.";
        long toCall = currentBet - s.bet;
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
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
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
        if (turn >= 0 && System.currentTimeMillis() - turnStarted > limit) {
            Seat s = seats.get(turn);
            boolean canCheck = currentBet - s.bet <= 0;
            s.folded = !canCheck;
            s.last = canCheck ? "Check (Zeit)" : "Fold (Zeit)";
            s.acted = true;
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
            s.inHand = !s.leaving && s.stack > 0;
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
        for (Seat s : seats) {
            if (!s.inHand) continue;
            Player p = Bukkit.getPlayer(s.id);
            if (p != null) {
                Msg.send(p, "<gold>Poker: <gray>Neue Hand. Deine Karten: " + cards(s.hole));
                Sfx.play(plugin, p, Sfx.Kind.OPEN);
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
        for (int i = 0; i < n; i++) board.add(deck.pollFirst());
        announce("<gold>Poker: <gray>Board " + cards(board));
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
            announce("<gold>Poker: <white>" + nameOf(winner.id) + " <gray>gewinnt <gold>" + pot + " Chips<gray>, alle anderen haben gepasst.");
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
    }

    // ---------- Anzeige ----------

    private void render() {
        Location t = table();
        if (t == null || t.getWorld() == null) return;
        if (!t.getWorld().isChunkLoaded(t.getBlockX() >> 4, t.getBlockZ() >> 4)) return;
        if (display == null || !display.isValid()) {
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
        display.text(Msg.parse(sb.toString()));
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
            return new Location(w, Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
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
