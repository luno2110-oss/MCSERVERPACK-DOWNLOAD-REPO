package de.kuby.kubyplugins.casino;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Karten, Schlitten (6 Decks) und Handwerte für Blackjack. */
public final class BjCards {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String RANKS = "A,2,3,4,5,6,7,8,9,10,J,Q,K";
    private static final String SUITS = "hdsc";

    private BjCards() {
    }

    /** rank 1 = Ass, 11–13 = Bube, Dame, König; suit h, d, s, c. */
    public record Card(int rank, char suit) {
        public int value() {
            return rank == 1 ? 11 : Math.min(rank, 10);
        }

        public String rankLabel() {
            return RANKS.split(",")[rank - 1];
        }

        /** Item-Modell kuby:casino/card_&lt;rang&gt;&lt;farbe&gt;. */
        public String model() {
            return "card_" + rankLabel().toLowerCase() + suit;
        }

        public String label() {
            String s = switch (suit) {
                case 'h' -> "♥";
                case 'd' -> "♦";
                case 's' -> "♠";
                default -> "♣";
            };
            return rankLabel() + s;
        }

        public boolean red() {
            return suit == 'h' || suit == 'd';
        }
    }

    /** Schlitten mit mehreren Decks; wird nach drei Vierteln neu gemischt. */
    public static final class Shoe {
        private final int decks;
        private final List<Card> cards = new ArrayList<>();
        private int cut;

        public Shoe(int decks) {
            this.decks = Math.max(1, decks);
            shuffle();
        }

        public void shuffle() {
            cards.clear();
            for (int d = 0; d < decks; d++) {
                for (char s : SUITS.toCharArray()) {
                    for (int r = 1; r <= 13; r++) cards.add(new Card(r, s));
                }
            }
            Collections.shuffle(cards, RNG);
            cut = cards.size() / 4;
        }

        public boolean needsShuffle() {
            return cards.size() <= cut;
        }

        public Card draw() {
            if (cards.isEmpty()) shuffle();
            return cards.remove(cards.size() - 1);
        }
    }

    /** Bester Wert einer Hand (Asse zählen 11 oder 1). */
    public static int total(List<Card> hand) {
        int sum = 0, aces = 0;
        for (Card c : hand) {
            sum += c.value();
            if (c.rank() == 1) aces++;
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }

    /** Weich = ein Ass zählt noch 11. */
    public static boolean soft(List<Card> hand) {
        int sum = 0, aces = 0;
        for (Card c : hand) {
            sum += c.value();
            if (c.rank() == 1) aces++;
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return aces > 0;
    }

    public static boolean blackjack(List<Card> hand) {
        return hand.size() == 2 && total(hand) == 21;
    }
}
