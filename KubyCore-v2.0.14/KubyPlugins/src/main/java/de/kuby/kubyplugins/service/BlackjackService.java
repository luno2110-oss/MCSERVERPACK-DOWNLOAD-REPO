package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Blackjack am Casino-Tisch. Rechtsklick auf den Tisch startet eine Runde, der Einsatz kommt aus den Chips.
 * Karten sind schwebende Texte über dem Tisch. Ziehen und Halten per /casino hit und /casino stand.
 * Am Tisch sitzt immer nur ein Spieler. Wer geht, stirbt oder den Modus wechseln will, bekommt die Runde
 * sofort zu Ende gespielt (wie Halten), damit man keine Karten gegen einen Abbruch tauschen kann.
 */
public final class BlackjackService {

    private static final String SURVIVAL = "survival";
    private static final double WALK_AWAY = 8;
    private static final long RESULT_SHOWN_TICKS = 120L;

    private record Card(int rank, char suit) {
        int value() {
            return rank == 1 ? 11 : Math.min(rank, 10);
        }

        String label() {
            String r = switch (rank) {
                case 1 -> "A";
                case 11 -> "J";
                case 12 -> "Q";
                case 13 -> "K";
                default -> String.valueOf(rank);
            };
            String color = suit == '♥' || suit == '♦' ? "<red>" : "<white>";
            return color + r + suit;
        }
    }

    private static final class Game {
        final UUID player;
        final long bet;
        final Deque<Card> deck;
        final List<Card> hand = new ArrayList<>();
        final List<Card> dealer = new ArrayList<>();
        final List<TextDisplay> displays = new ArrayList<>();
        boolean revealed;

        Game(UUID player, long bet, Deque<Card> deck) {
            this.player = player;
            this.bet = bet;
            this.deck = deck;
        }
    }

    private final KubyPlugins plugin;
    private final NamespacedKey tag;
    private Game game;

    public BlackjackService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "kp_blackjack");
    }

    /** Entfernt Karten-Anzeigen, die vor einem Neustart übrig geblieben sind. */
    public synchronized void respawn() {
        Location t = plugin.casino().tableLocation();
        if (t == null || t.getWorld() == null) return;
        for (TextDisplay d : t.getWorld().getNearbyEntitiesByType(TextDisplay.class, t, 12)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
        }
    }

    public synchronized boolean inGame(UUID id) {
        return game != null && game.player.equals(id);
    }

    public boolean isTable(Location l) {
        Location t = plugin.casino().tableLocation();
        return t != null && l.getWorld() != null && l.getWorld().equals(t.getWorld())
                && l.getBlockX() == t.getBlockX() && l.getBlockY() == t.getBlockY() && l.getBlockZ() == t.getBlockZ();
    }

    /** Startet eine Runde für den Spieler. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String start(Player p) {
        UUID id = p.getUniqueId();
        if (game != null) return game.player.equals(id) ? null : "Der Tisch ist gerade belegt.";
        if (!SURVIVAL.equals(plugin.modes().modeOf(id))) return "Blackjack gibt es nur in Survival.";
        if (plugin.casino().tableLocation() == null) return "Der Tisch ist noch nicht eingerichtet.";
        long bet = plugin.getConfig().getLong("casino.blackjack.bet", 1);
        if (!plugin.casino().takeChips(id, bet)) {
            return "Du brauchst " + bet + " Chip(s) für eine Runde. Tausch sie am Kassentisch.";
        }
        Game g = new Game(id, bet, freshDeck());
        game = g;
        g.hand.add(draw(g));
        g.dealer.add(draw(g));
        g.hand.add(draw(g));
        g.dealer.add(draw(g));
        long feed = Math.round(bet * plugin.casino().price()
                * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        if (total(g.hand) == 21) {
            playOut(g);
        } else {
            render(g, hint(bet));
        }
        return null;
    }

    /** Zieht eine Karte. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String hit(UUID id) {
        if (!inGame(id)) return "Du sitzt gerade nicht am Tisch.";
        if (total(game.hand) >= 21) return "Du kannst nicht mehr ziehen.";
        game.hand.add(draw(game));
        if (total(game.hand) > 21) {
            game.revealed = true;
            settle(game);
        } else {
            render(game, hint(game.bet));
        }
        return null;
    }

    /** Hält. Der Dealer zieht danach. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String stand(UUID id) {
        if (!inGame(id)) return "Du sitzt gerade nicht am Tisch.";
        playOut(game);
        return null;
    }

    /** Spielt eine laufende Runde für den Spieler zu Ende (beim Gehen, Sterben, Ausloggen). */
    public synchronized void leave(UUID id) {
        if (inGame(id)) playOut(game);
    }

    /** Beendet laufende Runden beim Herunterfahren, damit kein Einsatz verloren geht. */
    public synchronized void shutdown() {
        if (game != null) playOut(game);
    }

    /** Prüft, ob der Spieler vom Tisch weggeht. Dann endet die Runde. */
    public synchronized void checkAway(Player p, Location to) {
        if (!inGame(p.getUniqueId())) return;
        Location t = plugin.casino().tableLocation();
        if (t == null || to.getWorld() == null || !to.getWorld().equals(t.getWorld())
                || to.distanceSquared(t) > WALK_AWAY * WALK_AWAY) {
            leave(p.getUniqueId());
        }
    }

    private String hint(long bet) {
        return "<gray>Einsatz " + bet + " Chip(s)  ·  /casino hit  ·  /casino stand";
    }

    private void playOut(Game g) {
        if (total(g.hand) <= 21) {
            while (total(g.dealer) < 17) g.dealer.add(draw(g));
        }
        g.revealed = true;
        settle(g);
    }

    private void settle(Game g) {
        int p = total(g.hand);
        int d = total(g.dealer);
        boolean playerNatural = g.hand.size() == 2 && p == 21;
        boolean dealerNatural = g.dealer.size() == 2 && d == 21;
        long bet = g.bet;
        long payout;
        String result;
        if (p > 21) {
            payout = 0;
            result = "<red>Über 21, verloren. -" + bet + " Chip(s)";
        } else if (playerNatural && !dealerNatural) {
            payout = bet + bet * 3 / 2;
            result = "<gold>Blackjack! <green>+" + (payout - bet) + " Chip(s)";
        } else if (dealerNatural && !playerNatural) {
            payout = 0;
            result = "<red>Dealer hat Blackjack. -" + bet + " Chip(s)";
        } else if (d > 21 || p > d) {
            payout = 2 * bet;
            result = "<green>Gewonnen! +" + bet + " Chip(s)";
        } else if (p == d) {
            payout = bet;
            result = "<gray>Unentschieden, Einsatz zurück.";
        } else {
            payout = 0;
            result = "<red>Verloren. -" + bet + " Chip(s)";
        }
        plugin.casino().giveChips(g.player, payout);
        g.revealed = true;
        render(g, result);
        game = null;
        List<TextDisplay> shown = new ArrayList<>(g.displays);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> shown.forEach(Entity::remove), RESULT_SHOWN_TICKS);
        Player online = Bukkit.getPlayer(g.player);
        if (online != null) {
            Msg.send(online, result + "  <gray>Chips: " + plugin.casino().chips(g.player));
        }
    }

    private Deque<Card> freshDeck() {
        List<Card> cards = new ArrayList<>();
        for (char suit : new char[]{'♠', '♥', '♦', '♣'}) {
            for (int rank = 1; rank <= 13; rank++) cards.add(new Card(rank, suit));
        }
        Collections.shuffle(cards);
        return new ArrayDeque<>(cards);
    }

    private Card draw(Game g) {
        if (g.deck.isEmpty()) g.deck.addAll(freshDeck());
        return g.deck.pollFirst();
    }

    /** Summe mit Assen als 1 oder 11, je nachdem, was passt. */
    private static int total(List<Card> cards) {
        int sum = 0;
        int aces = 0;
        for (Card c : cards) {
            sum += c.value();
            if (c.rank() == 1) aces++;
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }

    /** Baut alle Anzeigen der Runde neu auf. Die verdeckte Dealer-Karte bleibt verdeckt, bis die Runde endet. */
    private void render(Game g, String status) {
        Location t = plugin.casino().tableLocation();
        if (t == null || t.getWorld() == null) return;
        for (TextDisplay d : g.displays) d.remove();
        g.displays.clear();
        Location base = t.clone().add(0, 1.0, 0);
        spawn(g, base.clone().add(0, 1.0, 0), status);
        spawnRow(g, base.clone().add(0, 0.3, -0.6), g.dealer, !g.revealed);
        spawnRow(g, base.clone().add(0, 0.3, 0.6), g.hand, false);
    }

    private void spawnRow(Game g, Location row, List<Card> cards, boolean hideFirst) {
        int n = cards.size();
        for (int i = 0; i < n; i++) {
            Location at = row.clone().add((i - (n - 1) / 2.0) * 0.45, 0, 0);
            String text = hideFirst && i == 0 ? "<gray>??" : cards.get(i).label();
            spawn(g, at, text);
        }
    }

    private void spawn(Game g, Location at, String mini) {
        World w = at.getWorld();
        TextDisplay d = w.spawn(at, TextDisplay.class, e -> {
            e.text(Msg.parse(mini));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        g.displays.add(d);
    }
}
