package h3d;

import de.kuby.kubyplugins.casino.BjCards;
import de.kuby.kubyplugins.casino.BlackjackTable;
import de.kuby.kubyplugins.casino.RouletteTable;
import de.kuby.kubyplugins.casino.SlotMachine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.io.FileWriter;
import java.util.List;

/** Baut eine Casino-Szene mit den echten Klassen auf und schreibt alle Anzeigen als JSON. Aufruf: Scene <name> <out.json> */
public final class Scene {
    public static void main(String[] args) throws Exception {
        World w = (World) Mock.world();
        NamespacedKey tag = new NamespacedKey("kuby", "tag");
        NamespacedKey click = new NamespacedKey("kuby", "click");
        switch (args[0]) {
            case "slots" -> {
                double width = args.length > 2 ? Double.parseDouble(args[2]) : 2.7;
                SlotMachine m = new SlotMachine("s1", new Location(w, 0.5, 1.5, 0.03), 0f, width, 1,
                        new Location(w, 0.5, 0, 1.6), tag, click);
                String[][] start = {{"cherry", "seven", "bell"}, {"lemon", "seven", "diamond"}, {"star", "seven", "coin"}};
                m.build(start);
                m.panel(new long[]{10, 50, 100, 500, 1000, 5000}, 2);
                m.holo(20080, 10, 5000);
                m.seat();
            }
            case "blackjack" -> {
                BlackjackTable t = new BlackjackTable(Mock.plugin(), "b1", new Location(w, 0.5, 0, 0.5), 0f, 3.0, tag, click);
                t.build();
                t.card(2, 0, 0, new BjCards.Card(13, 'c'), 1);
                t.card(2, 0, 1, new BjCards.Card(4, 'd'), 1);
                t.card(1, 0, 0, new BjCards.Card(1, 'h'), 1);
                t.card(1, 0, 1, new BjCards.Card(9, 's'), 1);
                t.card(5, 0, 0, new BjCards.Card(6, 'h'), 1);
                t.card(5, 0, 1, null, 1);
                t.total(2, "14");
                t.total(1, "20");
                t.bet(2, List.of(100L, 100L, 50L), 250);
                t.rack(2, new long[]{10, 50, 100, 500, 1000, 5000});
                t.actions(2, new boolean[]{true, true, true, false});
                t.timer(2, 0.6);
                t.dealerText(Component.text("Dealer", NamedTextColor.GOLD));
                t.seatText(0, Component.text("Freier Platz", NamedTextColor.GOLD));
                Mock.runLater();
            }
            case "roulette" -> {
                RouletteTable t = new RouletteTable(tag);
                t.geometry(new Location(w, 0.5, 1.0, 0.5), 3.75, 2.75, 0f);
                t.build();
                t.center(Component.text("ROULETTE", NamedTextColor.WHITE).append(Component.newline()).append(Component.text("Warte auf Einsätze..", NamedTextColor.YELLOW)));
                t.chip("zahl", 17, 100);
                t.chip("zahl", 17, 50);
                t.chip("rot", 0, 500);
                t.chip("dutzend2", 0, 1000);
                t.light(RouletteTable.position(17), true);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
        try (FileWriter fw = new FileWriter(args[1])) {
            fw.write(Mock.dump().toString());
        }
        System.out.println(Mock.ENTITIES.stream().filter(e -> !e.removed).count() + " Anzeigen");
    }
}
