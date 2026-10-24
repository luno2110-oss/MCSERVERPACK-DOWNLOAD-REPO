package de.kuby.kubyplugins.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** Testmenü für das Dialog-Kit (/kadmin kittest): Nachbau des Fischbuchs aus der Referenz, Kacheln klickbar. */
public final class KitDemo {

    private static final TextColor CYAN = TextColor.color(0x62CDFF), GRAY = TextColor.color(0xA0A0A8),
            AQUA = TextColor.color(0x6FE3D2), DARK = TextColor.color(0x10201E), LIGHT = TextColor.color(0xE0E0E0);

    private KitDemo() {
    }

    public static void open(Player player, int tab) {
        Canvas c = new Canvas(390);
        c.box(0, 0, 390, 52, KitData.Box.NAVY);
        c.back(11, 9);
        c.action(11, 9, 20, 20, p -> p.closeDialog(), "Zurück");
        c.icon(40, 10, KitData.Icon.BOOK, true);
        c.text(78, 12, "FLÜSSE & SEEN", CYAN, true);
        c.text(78, 25, "6/8 gefangen · volle Seite: 3.000$ + 2 Tokens", GRAY, false);
        c.textRight(380, 15, "9/48", AQUA, true);
        String[] tabs = {"Flüsse & Seen", "Ozean", "Warme Gewässer", "Kalte Gewässer", "Sumpf & Höhlen", "Mythen & Zone"};
        int x = 0, y = 54;
        for (int i = 0; i < tabs.length; i++) {
            int w = Canvas.width(tabs[i], i == tab) - 1 + 18;
            if (x + w > 390) {
                x = 0;
                y += 27;
            }
            boolean on = i == tab;
            c.button(x, y, w, on ? KitData.Button.TEAL : KitData.Button.DARK, tabs[i], on ? DARK : LIGHT, on);
            int target = i;
            c.action(x, y, w, 22, p -> open(p, target), tabs[i]);
            x += w + 4;
        }
        String[][] fish = {{"Rotauge", "Gewöhnlich · 9x · bis 0,43 kg", "AAAAAA"},
                {"Flussbarsch", "Gewöhnlich · 9x · bis 0,81 kg", "AAAAAA"}, {"Bachforelle", "Ungewöhnlich · 5x", "55FF55"},
                {"Karpfen", "Ungewöhnlich · 3x", "55FF55"}, {"Zander", "Selten · 1x · bis 3,41 kg", "5555FF"},
                {"Hecht", "Selten · 1x · bis 8,41 kg", "5555FF"}, {"???", "Episch · noch offen", "AA00AA"},
                {"???", "Legendär · noch offen", "FFAA00"}};
        for (int i = 0; i < fish.length; i++) {
            int tx = (i % 2) * 198, ty = 117 + (i / 2) * 36;
            c.box(tx, ty, 192, 34, KitData.Box.NAVY);
            c.icon(tx + 9, ty + 9, KitData.Icon.COD, false);
            c.text(tx + 34, ty + 6, fish[i][0], TextColor.fromHexString("#" + fish[i][2]), true);
            c.text(tx + 34, ty + 18, fish[i][1], GRAY, false);
            c.text(tx + 178, ty + 13, "›", CYAN, false);
            String name = fish[i][0];
            c.action(tx, ty, 192, 34, p -> p.sendMessage(Component.text("Kachel geklickt: " + name, NamedTextColor.AQUA)),
                    "Mehr zu diesem Fisch");
        }
        c.bar(0, 268, 120, 0.35, KitData.Fill.GOLD);
        c.text(126, 268, "Balken-Test 35 %", GRAY, false);
        UiDialog.show(player, c, 280);
    }
}
