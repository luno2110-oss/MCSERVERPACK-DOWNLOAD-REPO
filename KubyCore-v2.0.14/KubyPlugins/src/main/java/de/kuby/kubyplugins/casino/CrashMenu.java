package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Einsatz-Truhe "» CRASH" wie im Clip: oben der Info-Kopf, in der Mitte Chips 10 bis 5.000 (Klick setzt), darunter
 * letzten Einsatz wiederholen, eigener Einsatz und Auszahlen (während der Runde).
 */
public final class CrashMenu extends GuiMenu {

    private static final String[] COLORS = {"white", "pink", "blue", "green", "black", "purple"};
    private static final int[] CHIP_SLOTS = {19, 20, 21, 22, 23, 25};
    private final CrashGame game;

    public CrashMenu(KubyPlugins plugin, CrashGame game) {
        super(plugin, Msg.parse("<dark_gray>»</dark_gray> <#F2A02A><b>" + SmallCaps.of("crash") + "</b></#F2A02A>"), 4);
        this.game = game;
    }

    @Override
    protected boolean fillEmpty() {
        return false;
    }

    private static String k(long v) {
        return v >= 1000 && v % 1000 == 0 ? (v / 1000) + "K" : CasinoText.num(v);
    }

    @Override
    protected void build(Player p) {
        long chips = plugin.casino().chips(p.getUniqueId());
        long bet = game.betOf(p.getUniqueId());
        CrashGame.Phase phase = game.phase();
        boolean running = phase == CrashGame.Phase.RUNNING;

        List<String> info = new ArrayList<>(List.of(""));
        info.add(CasinoText.line(switch (phase) {
            case IDLE -> "Runde: <white>bereit, warte auf Einsätze";
            case BETTING -> "Runde: <white>Start in " + game.secondsLeft() + "s";
            case RUNNING -> "Runde: <green>läuft · " + CrashGame.fmt(game.current());
            case CRASHED -> "Runde: <#FF5A5A>gecrasht, gleich geht's weiter";
        }));
        info.add(CasinoText.line(bet > 0 ? "Dein Einsatz: <#F2C94C>" + CasinoText.chips(bet) : "Dein Einsatz: <dark_gray>noch keiner"));
        info.add(CasinoText.line("Deine Chips: <#F2C94C>" + CasinoText.chips(chips)));
        info.add("");
        info.add("<gray>Setz vor dem Start, der Multiplikator");
        info.add("<gray>steigt, steig rechtzeitig aus.");
        set(4, KasseMenu.chipItem("crash_info", CasinoText.title("#F2A02A", "Crash"), info), null);

        for (int i = 0; i < CrashGame.CHIPS.length; i++) {
            long v = CrashGame.CHIPS[i];
            List<String> lore = new ArrayList<>(List.of(""));
            if (running) lore.add(CasinoText.line("<#FF5A5A>Runde läuft, nächster Einsatz danach"));
            else if (chips < v) lore.add(CasinoText.line("<#FF5A5A>So viele Chips hast du nicht"));
            else lore.add(CasinoText.click("setzen"));
            set(CHIP_SLOTS[i], KasseMenu.chipItem("chip_" + COLORS[i], CasinoText.title("#F2A02A", CasinoText.num(v) + " Chips"), lore),
                    e -> place(p, v));
        }

        long last = game.lastBetOf(p.getUniqueId());
        set(29, KasseMenu.chipItem("repeat", CasinoText.title("#C8B8F8", "Letzter Einsatz"), last > 0
                        ? List.of("", CasinoText.line("Setzt wieder <#F2C94C>" + CasinoText.chips(last)), "", CasinoText.click("wiederholen"))
                        : List.of("", CasinoText.line("<dark_gray>Noch kein Einsatz"))),
                e -> {
                    long l = game.lastBetOf(p.getUniqueId());
                    if (l > 0) place(p, Math.max(0, l - game.betOf(p.getUniqueId())));
                });

        set(31, Items.make(Material.NAME_TAG, CasinoText.title("#F2A02A", "Eigener Einsatz"), List.of("",
                CasinoText.line("Mindestens " + CasinoText.chips(game.minBet())),
                CasinoText.line("Maximaler Einsatz: " + k(game.maxBet())), "",
                CasinoText.click("eingeben"))), e -> {
            p.closeInventory();
            CasinoInput.amount(p, "Crash-Einsatz", List.of("<gray>Wie viele Chips setzt du?",
                            "<gray>Du hast <#F2C94C>" + CasinoText.chips(plugin.casino().chips(p.getUniqueId()))),
                    1, game.maxBet(), "Setzen", (pl, v) -> {
                        String err = game.bet(pl, v);
                        if (err != null) CasinoText.error(pl, err);
                        new CrashMenu(plugin, game).open(pl);
                    }, pl -> new CrashMenu(plugin, game).open(pl));
        });

        boolean live = game.live(p.getUniqueId());
        ItemStack cash = KasseMenu.chipItem("cashout", CasinoText.title("green", "Auszahlen"), live
                ? List.of("", CasinoText.line("Jetzt bei <green>" + CrashGame.fmt(game.current())),
                CasinoText.line("Du bekommst <#F2C94C>" + CasinoText.chips((long) Math.floor(bet * game.current()))), "",
                CasinoText.click("auszahlen"))
                : List.of("", CasinoText.line("<dark_gray>Nur während der Runde"),
                CasinoText.line("<dark_gray>Auch: Sneaken + Klick, /cashout")));
        set(33, cash, e -> {
            String err = game.cashout(p);
            if (err != null) CasinoText.error(p, err);
            refresh(p);
        });
    }

    private void place(Player p, long amount) {
        if (amount <= 0) return;
        String err = game.bet(p, amount);
        if (err != null) CasinoText.error(p, err);
        refresh(p);
    }
}
