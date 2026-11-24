package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Items;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Kristall-Tausch als eigenes Fenster (v2.07, Wunsch des Besitzers: „Custom-UIs für alle, die wir vergessen haben, z. B.
 * den Crystal Trade“): großes Bild Antiker Schutt × 4 → End-Kristall, was du hast und was daraus wird, Knöpfe für 1, 8
 * und alle. Violett-Gold wie der Casino-Admin (End-Kristall). Ohne Grafik-Pack bleibt die Truhe (KristalTradeMenu).
 */
public final class KristalTradeScreen {

    public static final int W = 390, HEIGHT = 230;

    static final TextColor GOLD = TextColor.color(0xF4D46A), GOLD_SOFT = TextColor.color(0xF2DA90), GRAY = TextColor.color(0xB0A6BE),
            DIM = TextColor.color(0x8A7E98), LIGHT = TextColor.color(0xEDE4F4), PINK = TextColor.color(0xF09AE8),
            BTN_GRAY = TextColor.color(0x8E8A84), RED = TextColor.color(0xFF6A6A);

    /** debris = Antiker Schutt im Inventar, price = Schutt je Kristall. */
    public record View(int debris, int price) {
        int possible() {
            return debris / Math.max(1, price);
        }
    }

    public interface Actions {
        /** n = Anzahl Kristalle, -1 = so viele wie möglich. */
        Consumer<Player> trade(int n);

        Consumer<Player> back();
    }

    private KristalTradeScreen() {
    }

    public static Canvas canvas(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Kristall-Tausch", TextColor.color(0xC08CF0));
        c.box(0, 0, W, 51, KitData.Box.CV_HEAD);
        c.item(10, 10, Material.END_CRYSTAL, true, KitData.Icon.STAR);
        c.title(46, 11, "KRISTALL-TAUSCH");
        c.text(47, 33, "Antiker Schutt gegen End-Kristalle", GRAY, false);
        int bw = Canvas.width("‹ Zurück", true) + 14;
        c.smallButton(380 - bw, 9, bw, KitData.Small.VIOLET, "‹ Zurück", LIGHT, true);
        c.action(380 - bw, 9, bw, 16, a.back(), "Zum Menü");

        // Tauschbild: Schutt x4 → Kristall, in der Mitte ein Goldpfeil
        int y = 55;
        c.box(0, y, W, 78, KitData.Box.CV_CARD);
        c.text(10, y + 8, Canvas.caps("Kurs"), GOLD, true);
        int cx = W / 2;
        c.box(cx - 120, y + 22, 72, 44, KitData.Box.CV_TILE, Canvas.Z_BOX + 1);
        c.item(cx - 100, y + 28, Material.ANCIENT_DEBRIS, true, KitData.Icon.STAR);
        c.textRight(cx - 52, y + 52, "×" + v.price(), LIGHT, true);
        c.textCenter(cx, y + 36, "›››", GOLD, true);
        c.box(cx + 48, y + 22, 72, 44, KitData.Box.CV_TILE, Canvas.Z_BOX + 1);
        c.item(cx + 68, y + 28, Material.END_CRYSTAL, true, KitData.Icon.STAR);
        c.textRight(cx + 116, y + 52, "×1", PINK, true);
        c.textRight(380, y + 8, v.price() + " Schutt = 1 Kristall", DIM, false);

        // Bestand
        y += 81;
        c.box(0, y, W, 40, KitData.Box.CV_CARD);
        c.text(10, y + 8, Canvas.caps("Dein Bestand"), GOLD, true);
        c.text(10, y + 22, List.of(Canvas.Span.of("Antiker Schutt ", GRAY), Canvas.Span.bold(String.valueOf(v.debris()), LIGHT),
                Canvas.Span.of("  ›  reicht für ", GRAY), Canvas.Span.bold(v.possible() + (v.possible() == 1 ? " Kristall" : " Kristalle"),
                        v.possible() > 0 ? PINK : RED)), W - 20);
        if (v.debris() % Math.max(1, v.price()) != 0 && v.possible() > 0) {
            c.textRight(380, y + 8, (v.debris() % v.price()) + " Schutt bleiben übrig", DIM, false);
        }

        // Knöpfe
        y += 44;
        int gap = 4, w = (W - 2 * gap) / 3;
        int[][] opts = {{1}, {8}, {-1}};
        for (int i = 0; i < 3; i++) {
            int n = opts[i][0];
            boolean can = n < 0 ? v.possible() > 0 : v.possible() >= n;
            String label = n < 0 ? "Alle tauschen" + (v.possible() > 0 ? " (" + v.possible() + ")" : "") : n + "× tauschen";
            int x = i * (w + gap);
            if (can) {
                c.smallButton(x, y, w, i == 2 ? KitData.Small.GOLD : KitData.Small.VIOLET, label, i == 2 ? GOLD_SOFT : LIGHT, true);
                c.action(x, y, w, 16, a.trade(n), n < 0 ? "So viele Kristalle wie möglich" : n + " End-Kristall" + (n == 1 ? "" : "e") + " für "
                        + n * v.price() + " Antiken Schutt");
            } else {
                c.smallButton(x, y, w, KitData.Small.GOLD_GRAY, label, BTN_GRAY, true);
                c.action(x, y, w, 16, null, "Dafür fehlt dir Antiker Schutt (" + (Math.max(1, n) * v.price()) + " nötig)");
            }
        }
        c.textCenter(W / 2, y + 22, "Nur unveränderter Schutt zählt. Was nicht passt, kommt in die Abholbox.", DIM, false);
        return c;
    }

    /** Fenster öffnen; ohne Grafik-Pack die Truhe. */
    public static void open(KubyPlugins plugin, Player p) {
        if (!plugin.uiPack().active(p)) {
            new KristalTradeMenu(plugin).open(p);
            return;
        }
        int debris = Items.countPlain(p.getInventory(), Material.ANCIENT_DEBRIS);
        UiDialog.show(p, canvas(new View(debris, KristalTradeMenu.PRICE), new Actions() {
            @Override
            public Consumer<Player> trade(int n) {
                return pl -> {
                    KristalTradeMenu.trade(plugin, pl, n);
                    open(plugin, pl);
                };
            }

            @Override
            public Consumer<Player> back() {
                return pl -> new MainMenu(plugin).open(pl);
            }
        }), HEIGHT);
    }

    /** Selbsttest: kein Schutt, Rest, sehr viel. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Actions no = new Actions() {
            public Consumer<Player> trade(int n) {
                return p -> { };
            }

            public Consumer<Player> back() {
                return p -> { };
            }
        };
        reg.accept("Kristall-Tausch leer", () -> canvas(new View(0, 4), no));
        reg.accept("Kristall-Tausch Rest", () -> canvas(new View(23, 4), no));
        reg.accept("Kristall-Tausch viel", () -> canvas(new View(99_999, 4), no));
    }
}
