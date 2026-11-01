package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Teleports;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hauptmenü (/menu) als Dialog im Stil eines App-Starters: Kopf mit goldenem Titel auf Nachtblau, Coins und Juwelen,
 * vier Schnellknöpfe (Spawn, Wildnis, Homes, Enderchest), darunter Bereiche mit großen Kacheln (Icon und Name, Text im
 * Tooltip) in der Farbe des Bereichs und unten eine Leiste für Hilfe und Einstellungen. Ohne Pack: Truhe (MainMenu).
 */
public final class MainScreen {

    static final int W = 390, TW = 95, TH = 46, GAP = 3;
    static final TextColor GOLD = TextColor.color(0xF2C84A), CREAM = TextColor.color(0xF4E6C0),
            GRAY = TextColor.color(0x9CA3B4), WHITE = TextColor.color(0xF4F4F4), COINS = TextColor.color(0xF8D35A),
            JEWELS = TextColor.color(0x5FE3A0), DARK = TextColor.color(0x1A1206);

    /** Kachel: Name, Text für den Tooltip, Icon, Klick. */
    public record Tile(String name, String desc, KitData.Icon icon, Consumer<Player> click) {}

    /** Bereich: Überschrift, Farbe der Überschrift, Kasten der Kacheln, Kacheln. */
    public record Section(String title, TextColor color, KitData.Box box, List<Tile> tiles) {}

    /** Schnellknopf: Beschriftung, Icon, Knopf-Stil, Klick. */
    public record Quick(String label, KitData.Icon icon, KitData.Button style, Consumer<Player> click) {}

    public record View(String player, String mode, long coins, long jewels, List<Quick> quick, List<Section> sections,
                       List<Quick> footer) {}

    private final KubyPlugins plugin;

    public MainScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** /menu: Dialog mit Pack, sonst die Truhe. */
    public void open(Player p) {
        if (!plugin.uiPack().active(p)) {
            new MainMenu(plugin).open(p);
            return;
        }
        UiDialog.show(p, canvas(view(p)), 330);
    }

    /** Erst den Dialog schließen, dann öffnen (Truhen und andere Dialoge). */
    private static Consumer<Player> go(Consumer<Player> c) {
        return p -> {
            p.closeDialog();
            c.accept(p);
        };
    }

    private static Consumer<Player> cmd(String command) {
        return go(p -> p.performCommand(command));
    }

    View view(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        String modeName = switch (mode) {
            case ModeService.SURVIVAL -> "Survival";
            case ModeService.SMP -> "SMP";
            case ModeService.SKYBLOCK -> "Skyblock";
            default -> "Lobby";
        };
        List<Quick> quick = List.of(
                new Quick("Spawn", KitData.Icon.COMPASS, KitData.Button.TEAL, go(pl -> Teleports.spawn(plugin, pl))),
                new Quick("Wildnis", KitData.Icon.ENDER_PEARL, KitData.Button.GREEN, go(pl -> Teleports.randomTeleport(plugin, pl))),
                new Quick("Homes", KitData.Icon.RED_BED, KitData.Button.PURPLE, go(pl -> new HomesMenu(plugin).open(pl))),
                new Quick("Enderchest", KitData.Icon.ENDER_CHEST, KitData.Button.DARK,
                        go(pl -> pl.openInventory(plugin.enderchests().open(pl)))));

        List<Tile> daily = new ArrayList<>(List.of(
                new Tile("Shop", "Sektionen, Wochenende und Daily Shop", KitData.Icon.EMERALD, go(pl -> new ShopMenu(plugin).open(pl))),
                new Tile("Markt", "Items kaufen und verkaufen", KitData.Icon.CHEST_BLOCK, go(pl -> new MarketMenu(plugin).open(pl))),
                new Tile("Wirtschaft", "Rangliste der reichsten Spieler", KitData.Icon.GOLD_INGOT, go(pl -> new LeaderboardMenu(plugin).open(pl))),
                new Tile("Täglich", "30-Tage-Kalender mit Belohnungen", KitData.Icon.CLOCK, go(pl -> new DailyMenu(plugin).open(pl))),
                new Tile("Team", "Team verwalten, Kasse und Teamhome", KitData.Icon.WHITE_BANNER, go(pl -> new TeamMenu(plugin).open(pl))),
                new Tile("Tagespot", "Lose kaufen, Ziehung um 19 Uhr", KitData.Icon.GOLD_BLOCK, go(pl -> new DailyPotMenu(plugin).open(pl))),
                new Tile("Duo-RTP", "Zu zweit an einen Zufallsort, Kampf!", KitData.Icon.FIRE_CHARGE, go(pl -> new RtpQueueMenu(plugin).open(pl))),
                new Tile("Freunde", "Freunde, Anfragen und Nachspringen", KitData.Icon.COOKIE, cmd("friends"))));
        List<Tile> progress = new ArrayList<>(List.of(
                new Tile("Battlepass", "100 Stufen, Quests und Belohnungen", KitData.Icon.BOOK, go(pl -> plugin.battlepassScreen().open(pl))),
                new Tile("Spielzeit", "Stufen nach aktiver Spielzeit", KitData.Icon.EXPERIENCE, go(pl -> new PlaytimeScreen(plugin).open(pl))),
                new Tile("Titel", "Wähle deinen Titel neben dem Namen", KitData.Icon.NAME_TAG, go(pl -> new TagsMenu(plugin).open(pl))),
                new Tile("Kits", "Starter- und Rang-Kits abholen", KitData.Icon.IRON_CHESTPLATE, go(pl -> new KitMenu(plugin).open(pl))),
                new Tile("Kosmetik", "Hüte, Spuren und Effekte", KitData.Icon.AMETHYST, go(pl -> new CosmeticsScreen(plugin).open(pl))),
                new Tile("Statistik", "Deine Werte und Ranglisten", KitData.Icon.SPYGLASS, cmd("stats"))));
        List<Tile> mine = new ArrayList<>();
        for (KubyPlugins.MainEntry e : plugin.mainEntries()) {
            Tile t = new Tile(e.name(), e.desc(), Icons.of(e.icon(), KitData.Icon.STAR), go(e.action()));
            if (e.name().equals("Erfolge")) progress.add(t);
            else mine.add(t);
        }
        List<Section> sections = new ArrayList<>();
        if (!mine.isEmpty()) sections.add(new Section(modeName, TextColor.color(0x7FE08A), KitData.Box.GREEN, mine));
        sections.add(new Section("Alltag", TextColor.color(0x64C0FF), KitData.Box.NAVY, daily));
        sections.add(new Section("Fortschritt", TextColor.color(0xD08CFF), KitData.Box.PURPLE, progress));
        List<Quick> footer = List.of(
                new Quick("Guide", KitData.Icon.KNOWLEDGE_BOOK, KitData.Button.GOLD_LINE, cmd("guide")),
                new Quick("Regeln", KitData.Icon.WRITTEN_BOOK, KitData.Button.GOLD_LINE, cmd("regeln")),
                new Quick("Support", KitData.Icon.BELL, KitData.Button.GOLD_LINE, cmd("support")),
                new Quick("Einstellungen", KitData.Icon.COMPARATOR, KitData.Button.GOLD_LINE, cmd("settings")));
        return new View(p.getName(), modeName, plugin.economy().balance(p.getUniqueId()),
                plugin.jewels().balance(p.getUniqueId()), quick, sections, footer);
    }

    /** Nur Darstellung (auch für Vorschau und Selbsttest). */
    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Hauptmenü", GOLD);
        // Kopf: Titel gold, darunter Gruß; rechts Coins und Juwelen
        c.box(0, 0, W, 54, KitData.Box.HUB);
        c.icon(10, 11, KitData.Icon.COMPASS, true);
        c.title(50, 12, "Menü");
        c.text(50, 36, Canvas.fit(Canvas.lit(v.player()), false, 140) + " · " + v.mode(), GRAY, false);
        int nw = c.textRight(372, 14, Msg.coins(v.coins()), COINS, true);
        c.icon(372 - nw - 20, 10, KitData.Icon.GOLD_INGOT, false);
        int jw = c.textRight(372, 32, Msg.coins(v.jewels()), JEWELS, true);
        c.icon(372 - jw - 20, 28, KitData.Icon.EMERALD, false);

        // Schnellknöpfe
        int y = 60;
        quickRow(c, y, v.quick(), WHITE);
        y += 22 + 8;

        // Bereiche mit Kacheln (4 je Reihe)
        for (Section s : v.sections()) {
            c.text(2, y, Canvas.caps(s.title()), s.color(), true);
            c.line(4 + Canvas.width(Canvas.caps(s.title()), true) + 4, y + 3, W - 8 - Canvas.width(Canvas.caps(s.title()), true) - 4,
                    s.color());
            y += 12;
            int i = 0;
            for (Tile t : s.tiles()) {
                int x = (i % 4) * (TW + GAP), ty = y + (i / 4) * (TH + GAP);
                c.box(x, ty, TW, TH, s.box(), Canvas.Z_BUTTON);
                c.icon(x + (TW - 32) / 2, ty + 3, t.icon(), true, Icons.tint(t.icon()));
                c.textCenter(x + TW / 2, ty + 35, Canvas.fit(t.name(), true, TW - 8), CREAM, true);
                c.action(x, ty, TW, TH, t.click(), t.name() + " · " + t.desc());
                i++;
            }
            y += ((s.tiles().size() + 3) / 4) * (TH + GAP) + 6;
        }
        // Fußleiste
        quickRow(c, y, v.footer(), GOLD);
        return c;
    }

    /** Reihe gleich breiter Knöpfe mit Icon und Text. */
    private static void quickRow(Canvas c, int y, List<Quick> qs, TextColor color) {
        if (qs.isEmpty()) return;
        int n = qs.size();
        int w = (W - (n - 1) * GAP) / n;
        for (int i = 0; i < n; i++) {
            Quick q = qs.get(i);
            int x = i * (w + GAP);
            c.button(x, y, w, q.style());
            boolean line = q.style() == KitData.Button.GOLD_LINE;
            // Fußleiste (Umriss-Knöpfe) ohne Fettschrift, damit auch „Einstellungen“ passt
            String label = Canvas.fit(q.label(), !line, w - 16 - 4 - 6);
            int gw = 16 + 4 + Canvas.width(label, !line);
            int gx = x + (w - gw) / 2;
            c.icon(gx, y + 3, q.icon(), false);
            if (line) c.text(gx + 20, y + 7, label, color, false);
            else if (q.style() == KitData.Button.TEAL || q.style() == KitData.Button.GREEN) c.textFlat(gx + 20, y + 7, label, DARK, true);
            else c.text(gx + 20, y + 7, label, WHITE, true);
            c.action(x, y, w, 22, q.click(), q.label());
        }
    }
}
