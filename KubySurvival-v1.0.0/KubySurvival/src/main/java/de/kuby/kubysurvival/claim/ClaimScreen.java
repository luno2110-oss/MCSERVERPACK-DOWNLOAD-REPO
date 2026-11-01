package de.kuby.kubysurvival.claim;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /claim als Dialog in Wiesen-Optik: Kopf mit freien Claim-Blöcken, Knöpfe Claim-Axt und Hilfe, eigene Grundstücke als
 * Zeilen mit einer kleinen Grundriss-Skizze (Seitenverhältnis), Größe, Mitte und „Rand zeigen“; vertraute Spieler als
 * Namens-Chips (Klick entzieht das Vertrauen); vier Schalter für die Rechte von Fremden. Ohne Pack: ClaimMenu.
 */
public final class ClaimScreen {

    static final int W = 390;
    static final TextColor LEAF = TextColor.color(0x9BE36A), CREAM = TextColor.color(0xEEF4DC),
            SAGE = TextColor.color(0x9DB08A), WHITE = TextColor.color(0xF4F4F4), GOLD = TextColor.color(0xF8D35A),
            GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xFF7A7A), DARK = TextColor.color(0x10180A),
            DIM = TextColor.color(0x6E7F60);

    /** Grundstück: Nummer, Welt, Breite, Länge, Fläche, Mitte, Klick (Rand zeigen). */
    public record Plot(int number, String world, int width, int length, long area, int cx, int cz, Consumer<Player> show) {}

    /** Recht für Fremde: Icon, Text, erlaubt, Klick. */
    public record Flag(KitData.Icon icon, String label, boolean on, Consumer<Player> toggle) {}

    /** Vertrauter Spieler: Name, Klick (entziehen). */
    public record Friend(String name, Consumer<Player> remove) {}

    public record View(long free, long perHour, int max, List<Plot> plots, List<Friend> trusted, List<Flag> flags,
                       Consumer<Player> axe, Consumer<Player> help) {}

    private static final String[][] FLAG_INFO = {
            {"tueren", "Türen und Tore"}, {"knoepfe", "Knöpfe und Hebel"}, {"kisten", "Kisten und Öfen"}, {"tiere", "Tiere"}};
    private static final KitData.Icon[] FLAG_ICONS = {KitData.Icon.OAK_DOOR, KitData.Icon.LEVER, KitData.Icon.CHEST_BLOCK,
            KitData.Icon.WHEAT};

    private final KubySurvival addon;

    public ClaimScreen(KubySurvival addon) {
        this.addon = addon;
    }

    public void open(Player p) {
        if (!addon.core().uiPack().active(p)) {
            new ClaimMenu(addon).open(p);
            return;
        }
        UiDialog.show(p, canvas(view(p)), 330);
    }

    private View view(Player p) {
        ClaimService claims = addon.claims();
        UUID id = p.getUniqueId();
        List<Plot> plots = new ArrayList<>();
        int n = 1;
        for (ClaimService.Claim c : claims.claimsOf(id)) {
            int cx = (c.x1() + c.x2()) / 2, cz = (c.z1() + c.z2()) / 2;
            plots.add(new Plot(n++, worldName(c.world()), c.width(), c.length(), c.area(), cx, cz, pl -> {
                pl.closeDialog();
                addon.claimTool().show(pl, c.x1(), c.z1(), c.x2(), c.z2());
                Msg.send(pl, "<green>Der Rand wird 10 Sekunden angezeigt. <gray>(Mitte " + cx + ", " + cz + ")");
            }));
        }
        List<Friend> friends = new ArrayList<>();
        for (UUID t : claims.trusted(id)) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(t);
            String name = op.getName() == null ? "?" : op.getName();
            friends.add(new Friend(name, pl -> {
                claims.untrust(pl.getUniqueId(), t);
                Msg.send(pl, "<green>" + name + " darf auf deinen Grundstücken nicht mehr bauen.");
                open(pl);
            }));
        }
        List<Flag> flags = new ArrayList<>();
        for (int i = 0; i < FLAG_INFO.length; i++) {
            String flag = FLAG_INFO[i][0];
            flags.add(new Flag(FLAG_ICONS[i], FLAG_INFO[i][1], claims.allows(id, flag), pl -> {
                boolean now = claims.toggle(pl.getUniqueId(), flag);
                pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, now ? 1.4f : 0.9f);
                open(pl);
            }));
        }
        var cfg = addon.core().getConfig();
        return new View(claims.available(id), cfg.getLong("landclaim.blocks-per-hour", 100), cfg.getInt("landclaim.max-claims", 10),
                plots, friends, flags, pl -> {
            pl.closeDialog();
            pl.performCommand("claim axt");
        }, pl -> {
            pl.closeDialog();
            pl.performCommand("hilfe grundstuecke");
        });
    }

    private static String worldName(String world) {
        if (world == null) return "";
        String w = world.toLowerCase(java.util.Locale.ROOT);
        if (w.contains("bau")) return "Bauwelt";
        if (w.contains("farm")) return "Farmwelt";
        if (w.contains("nether")) return "Nether";
        if (w.contains("end")) return "End";
        return world;
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Grundstücke", LEAF);
        // Kopf
        c.box(0, 0, W, 54, KitData.Box.MEADOW);
        c.icon(10, 11, KitData.Icon.GRASS_BLOCK, true);
        c.title(50, 12, "Land", false, LEAF);
        c.text(50, 36, v.plots().size() + " / " + v.max() + " Grundstücke", SAGE, false);
        c.textRight(378, 10, Msg.coins(v.free()), CREAM, true);
        c.textRight(378, 21, Canvas.caps("freie Claim-Blöcke"), SAGE, false);
        c.textRight(378, 36, "+" + Msg.coins(v.perHour()) + " je Stunde Spielzeit", DIM, false);

        // Knöpfe
        int y = 60;
        int bw = (W - 3) / 2;
        c.button(0, y, bw, KitData.Button.GOLD);
        int gw = 16 + 4 + Canvas.width("Claim-Axt holen", true);
        c.icon((bw - gw) / 2, y + 3, KitData.Icon.GOLDEN_AXE, false);
        c.textFlat((bw - gw) / 2 + 20, y + 7, "Claim-Axt holen", DARK, true);
        c.action(0, y, bw, 22, v.axe(), "Claim-Axt: Linksklick Ecke 1, Rechtsklick Ecke 2, dann /claim ok");
        c.button(bw + 3, y, bw, KitData.Button.DARK);
        gw = 16 + 4 + Canvas.width("Alle Befehle", true);
        c.icon(bw + 3 + (bw - gw) / 2, y + 3, KitData.Icon.BOOK_PLAIN, false);
        c.text(bw + 3 + (bw - gw) / 2 + 20, y + 7, "Alle Befehle", WHITE, true);
        c.action(bw + 3, y, bw, 22, v.help(), "Hilfe zu Grundstücken");
        y += 22 + 8;

        // eigene Grundstücke
        y = section(c, y, "Deine Grundstücke");
        if (v.plots().isEmpty()) {
            c.box(0, y, W, 34, KitData.Box.MOSS);
            c.text(10, y + 8, "Noch kein Grundstück.", CREAM, true);
            c.text(10, y + 20, "Hol dir die Claim-Axt und stecke dein Land ab: 10×10 = 100 Blöcke.", SAGE, false);
            y += 34 + 6;
        }
        for (Plot pl : v.plots()) {
            c.box(0, y, W, 30, KitData.Box.MOSS);
            sketch(c, 8, y + 5, pl.width(), pl.length());
            c.text(36, y + 6, "Grundstück " + pl.number(), CREAM, true);
            c.text(36, y + 18, Canvas.fit(pl.width() + "×" + pl.length() + " · " + Msg.coins(pl.area()) + " Blöcke"
                    + (pl.world().isEmpty() ? "" : " · " + pl.world()), false, 200), SAGE, false);
            c.textRight(300, y + 12, "Mitte " + pl.cx() + ", " + pl.cz(), DIM, false);
            c.smallButton(310, y + 7, 74, KitData.Small.GREEN, "Rand zeigen", WHITE, false);
            c.action(0, y, W, 30, pl.show(), "Rand 10 Sekunden anzeigen");
            y += 30 + 3;
        }
        y += 3;

        // vertraute Spieler
        y = section(c, y, "Vertraute Spieler");
        if (v.trusted().isEmpty()) {
            for (String line : Canvas.wrap("Noch niemand. Mit /claim trust <Spieler> darf jemand auf allen deinen Grundstücken bauen.", W - 8)) {
                c.text(4, y, line, SAGE, false);
                y += 10;
            }
            y += 4;
        } else {
            int x = 0;
            for (Friend f : v.trusted()) {
                String name = Canvas.fit(Canvas.lit(f.name()), false, 90);
                int w = Canvas.width(name, false) + 22;
                if (x + w > W) {
                    x = 0;
                    y += KitData.SMALL_H + 3;
                }
                c.smallButton(x, y, w, KitData.Small.DARK, name + " ×", CREAM, false);
                c.action(x, y, w, KitData.SMALL_H, f.remove(), f.name() + " das Vertrauen entziehen");
                x += w + 3;
            }
            y += KitData.SMALL_H + 6;
        }

        // Rechte für Fremde: 2 x 2 Schalter
        y = section(c, y, "Das dürfen Fremde bei dir");
        int fw = (W - 3) / 2;
        for (int i = 0; i < v.flags().size(); i++) {
            Flag f = v.flags().get(i);
            int x = (i % 2) * (fw + 3), fy = y + (i / 2) * 31;
            c.box(x, fy, fw, 28, f.on() ? KitData.Box.MOSS : KitData.Box.GRAY);
            c.icon(x + 7, fy + 6, f.icon(), false);
            c.text(x + 29, fy + 10, Canvas.fit(f.label(), true, fw - 29 - 60), f.on() ? CREAM : SAGE, true);
            c.smallButton(x + fw - 56, fy + 6, 50, f.on() ? KitData.Small.GREEN : KitData.Small.DARK,
                    f.on() ? "erlaubt" : "verboten", f.on() ? WHITE : RED, false);
            c.action(x, fy, fw, 28, f.toggle(), f.label() + ": " + (f.on() ? "verbieten" : "erlauben"));
        }
        return c;
    }

    /** Selbsttest: viele Grundstücke, lange Namen, große Zahlen; leer. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        List<Plot> plots = new ArrayList<>();
        for (int i = 1; i <= 10; i++) plots.add(new Plot(i, "WWWWWWWWWWWWWWWW", 9999 / i, i, 99_999_999L, -29_999_999, 29_999_999, no));
        List<Friend> fr = new ArrayList<>();
        for (int i = 0; i < 12; i++) fr.add(new Friend("_WWWWWWWWWWWWWWW", no));
        List<Flag> fl = new ArrayList<>();
        for (int i = 0; i < 4; i++) fl.add(new Flag(FLAG_ICONS[i], "W".repeat(30), i % 2 == 0, no));
        reg.accept("Grundstücke voll", () -> canvas(new View(999_999_999L, 99_999, 10, plots, fr, fl, no, no)));
        reg.accept("Grundstücke leer", () -> canvas(new View(0, 0, 0, List.of(), List.of(), List.of(), no, no)));
    }

    /** Überschrift mit Linie, gibt die nächste Zeile zurück. */
    private static int section(Canvas c, int y, String title) {
        String t = Canvas.caps(title);
        int tw = Canvas.width(t, true);
        c.text(2, y, t, LEAF, true);
        c.line(tw + 8, y + 3, W - tw - 10, DIM);
        return y + 12;
    }

    /** Grundriss-Skizze in einem Feld von 20 x 20: Rechteck im Seitenverhältnis des Grundstücks. */
    private static void sketch(Canvas c, int x, int y, int w, int l) {
        int max = Math.max(1, Math.max(w, l));
        int sw = Math.max(4, Math.round(20f * w / max)), sh = Math.max(4, Math.round(20f * l / max));
        int sx = x + (20 - sw) / 2, sy = y + (20 - sh) / 2;
        c.line(sx, sy, sw, LEAF);
        c.line(sx, sy + sh - 1, sw, LEAF);
        for (int i = 1; i < sh - 1; i++) {
            c.line(sx, sy + i, 1, LEAF);
            c.line(sx + sw - 1, sy + i, 1, LEAF);
        }
    }
}
