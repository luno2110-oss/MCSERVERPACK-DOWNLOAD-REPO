package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Losbude wie im Clip ("Lose kaufst du mit Chips · Sofort aufdecken, sofort wissen · Mehrere Sorten, mehrere
 * Preisklassen"): Truhe "» LOSBUDE" mit Bronze-, Silber- und Gold-Los, danach das Rubbellos mit neun Feldern.
 * Drei gleiche Symbole gewinnen (Kirsche 1x, Zitrone 2x, Glocke 5x, Stern 10x, Sieben 50x des Lospreises).
 * Das Ergebnis steht beim Kauf fest und wird sofort gutgeschrieben; Aufdecken ist nur die Show.
 * Rückfluss etwa 88 %.
 */
public final class Losbude {

    /** Sorten: Name, Farbe, Preis, Item. */
    public record Kind(String name, String color, long price, String model) {}

    public static final List<Kind> KINDS = List.of(
            new Kind("Bronze-Los", "#E0A060", 100, "los_bronze"),
            new Kind("Silber-Los", "#D8DEE8", 1000, "los_silber"),
            new Kind("Gold-Los", "#F6D23A", 10000, "los_gold"));

    static final String[] SYMBOLS = {"cherry", "lemon", "bell", "star", "seven"};
    static final String[] SYMBOL_NAMES = {"Kirsche", "Zitrone", "Glocke", "Stern", "Sieben"};
    static final int[] MULT = {1, 2, 5, 10, 50};
    /** Gewinnwahrscheinlichkeit je Symbol (sonst Niete). */
    private static final double[] CHANCE = {0.18, 0.11, 0.045, 0.016, 0.002};
    private static final SecureRandom RNG = new SecureRandom();

    private Losbude() {
    }

    /** Gewinnendes Symbol (Index) oder -1 für eine Niete. */
    static int draw() {
        double r = RNG.nextDouble();
        for (int i = SYMBOLS.length - 1; i >= 0; i--) {
            if (r < CHANCE[i]) return i;
            r -= CHANCE[i];
        }
        return -1;
    }

    /** Neun Felder passend zum Ergebnis: bei Gewinn genau dreimal das Symbol, sonst kein Symbol öfter als zweimal. */
    static int[] grid(int win) {
        List<Integer> cells = new ArrayList<>(9);
        int[] count = new int[SYMBOLS.length];
        if (win >= 0) {
            for (int i = 0; i < 3; i++) cells.add(win);
            count[win] = 3;
        }
        while (cells.size() < 9) {
            int s = RNG.nextInt(SYMBOLS.length);
            if (s == win || count[s] >= 2) continue;
            count[s]++;
            cells.add(s);
        }
        Collections.shuffle(cells, RNG);
        int[] g = new int[9];
        for (int i = 0; i < 9; i++) g[i] = cells.get(i);
        return g;
    }

    public static void open(KubyPlugins plugin, Player p) {
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        new Menu(plugin).open(p);
    }

    // =================================================================================================================
    // Auswahl
    // =================================================================================================================

    static final class Menu extends GuiMenu {
        Menu(KubyPlugins plugin) {
            super(plugin, Msg.parse("<dark_gray>»</dark_gray> <gradient:#F2505A:#F6D23A:#5AE878:#5AA8F2:#B060F0><b>"
                    + SmallCaps.of("losbude") + "</b></gradient>"), 4);
        }

        @Override
        protected boolean fillEmpty() {
            return false;
        }

        @Override
        protected void build(Player p) {
            long chips = plugin.casino().chips(p.getUniqueId());
            set(4, Items.make(Material.PAPER, CasinoText.title("#F2C94C", "Losbude"), List.of("",
                    CasinoText.line("Lose kaufst du mit <#F6E23A>Chips"),
                    CasinoText.line("Sofort aufdecken, sofort wissen"),
                    CasinoText.line("Drei gleiche Symbole gewinnen"), "",
                    CasinoText.line("Deine Chips: <#F2C94C>" + CasinoText.chips(chips)))), null);
            for (int i = 0; i < KINDS.size(); i++) {
                Kind k = KINDS.get(i);
                List<String> lore = new ArrayList<>(List.of("",
                        CasinoText.line("Preis: <#F2C94C>" + CasinoText.chips(k.price())),
                        CasinoText.line("Hauptgewinn: <#F6E23A>" + CasinoText.chips(k.price() * MULT[MULT.length - 1])),
                        CasinoText.line("Gewinnchance: etwa 1 zu 3"), ""));
                lore.add(chips >= k.price() ? CasinoText.click("kaufen") : CasinoText.line("<#FF5A5A>So viele Chips hast du nicht"));
                set(20 + 2 * i, KasseMenu.chipItem(k.model(), CasinoText.title(k.color(), k.name()), lore), e -> buy(plugin, p, k));
            }
            List<String> plan = new ArrayList<>(List.of(""));
            for (int i = SYMBOLS.length - 1; i >= 0; i--) {
                plan.add(CasinoText.line("3x " + SYMBOL_NAMES[i] + ": <#F6E23A>" + MULT[i] + "-fach"));
            }
            plan.add("");
            plan.add("<gray>Vielfaches des Lospreises");
            set(31, KasseMenu.chipItem("slot_seven", CasinoText.title("#F2C94C", "Gewinnplan"), plan), null);
        }
    }

    static void buy(KubyPlugins plugin, Player p, Kind k) {
        String closed = plugin.casino().closedMessage();
        if (closed != null) {
            CasinoText.error(p, closed);
            return;
        }
        if (!plugin.casino().takeChips(p.getUniqueId(), k.price())) {
            CasinoText.error(p, "So viele Chips hast du nicht. Tausch sie an der Kasse.");
            return;
        }
        long feed = Math.round(k.price() * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        int win = draw();
        long prize = win < 0 ? 0 : k.price() * MULT[win];
        if (prize > 0) plugin.casino().giveChips(p.getUniqueId(), prize);
        plugin.casinoLog().record("Losbude", p.getUniqueId(), k.price(), prize);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        new Scratch(plugin, k, grid(win), win, prize).open(p);
    }

    // =================================================================================================================
    // Rubbellos
    // =================================================================================================================

    static final class Scratch extends GuiMenu {
        private static final int[] FIELDS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
        private final Kind kind;
        private final int[] grid;
        private final int win;
        private final long prize;
        private final boolean[] open = new boolean[9];
        private boolean told;

        Scratch(KubyPlugins plugin, Kind kind, int[] grid, int win, long prize) {
            super(plugin, Msg.parse("<dark_gray>»</dark_gray> <" + kind.color() + "><b>" + SmallCaps.of(kind.name())
                    + "</b></" + kind.color() + ">"), 5);
            this.kind = kind;
            this.grid = grid;
            this.win = win;
            this.prize = prize;
        }

        @Override
        protected boolean fillEmpty() {
            return false;
        }

        private boolean allOpen() {
            for (boolean b : open) if (!b) return false;
            return true;
        }

        @Override
        protected void build(Player p) {
            for (int i = 0; i < 9; i++) {
                int idx = i;
                if (open[i]) {
                    int s = grid[i];
                    boolean hit = allOpen() && s == win;
                    set(FIELDS[i], KasseMenu.chipItem("slot_" + SYMBOLS[s], CasinoText.title(hit ? "#5AE878" : "#E8E4DC",
                            SYMBOL_NAMES[s]), hit ? List.of("", CasinoText.line("<green>Gewinner-Symbol")) : List.of()), null);
                } else {
                    set(FIELDS[i], KasseMenu.chipItem("los_feld", CasinoText.title("#C8CCD4", "Freirubbeln"),
                            List.of("", CasinoText.click("aufdecken"))), e -> reveal(p, idx));
                }
            }
            set(4, KasseMenu.chipItem(kind.model(), CasinoText.title(kind.color(), kind.name()), List.of("",
                    CasinoText.line("Drei gleiche Symbole gewinnen"),
                    CasinoText.line("Preis: <#F2C94C>" + CasinoText.chips(kind.price())))), null);
            if (!allOpen()) {
                set(24, Items.make(Material.BRUSH, CasinoText.title("#F2C94C", "Alles aufdecken"), List.of("",
                        CasinoText.click("alle Felder aufdecken"))), e -> {
                    for (int i = 0; i < 9; i++) open[i] = true;
                    p.playSound(p.getLocation(), Sound.ITEM_BRUSH_BRUSHING_SAND, 0.8f, 1.2f);
                    finish(p);
                    refresh(p);
                });
            } else {
                set(24, prize > 0
                        ? KasseMenu.chipItem("stack_gold", CasinoText.title("green", "Gewonnen!"), List.of("",
                        CasinoText.line("3x " + SYMBOL_NAMES[win] + ": <#F6E23A>+" + CasinoText.chips(prize))))
                        : Items.make(Material.GRAY_DYE, CasinoText.title("#FF5A5A", "Leider nichts"), List.of("",
                        CasinoText.line("Diesmal keine drei gleichen"))), null);
                set(33, KasseMenu.chipItem(kind.model(), CasinoText.title(kind.color(), "Noch ein " + kind.name()), List.of("",
                        CasinoText.line("Preis: <#F2C94C>" + CasinoText.chips(kind.price())), "", CasinoText.click("kaufen"))),
                        e -> buy(plugin, p, kind));
                set(35, Items.make(Material.ARROW, "<gray>" + SmallCaps.of("zurück zur losbude"), List.of()),
                        e -> new Menu(plugin).open(p));
            }
        }

        private void reveal(Player p, int i) {
            if (open[i]) return;
            open[i] = true;
            p.playSound(p.getLocation(), Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.9f, 1.3f);
            if (allOpen()) finish(p);
            refresh(p);
        }

        private void finish(Player p) {
            if (told) return;
            told = true;
            if (prize > 0) {
                boolean big = prize >= kind.price() * 10;
                p.playSound(p.getLocation(), big ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                CasinoText.send(p, "<green>Gewonnen!</green> 3x " + SYMBOL_NAMES[win] + " · <#F6E23A>+" + CasinoText.chips(prize));
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
                CasinoText.send(p, "Leider nichts – diesmal keine drei gleichen.");
            }
        }

        /** Beim Schließen ohne Aufdecken trotzdem das Ergebnis nennen (gutgeschrieben ist es schon). */
        public void closed(Player p) {
            if (!told) finish(p);
        }
    }
}
