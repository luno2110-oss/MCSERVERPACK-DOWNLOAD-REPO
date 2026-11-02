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

    /** Kachel: Name, Text für den Tooltip, Icon, eigene Grafik (vor dem Icon, darf fehlen), Klick. */
    public record Tile(String name, String desc, KitData.Icon icon, KitData.Sprite art, Consumer<Player> click, boolean alert) {
        public Tile(String name, String desc, KitData.Icon icon, Consumer<Player> click) {
            this(name, desc, icon, MainScreen.art(name), click, false);
        }

        public Tile(String name, String desc, KitData.Icon icon, KitData.Sprite art, Consumer<Player> click) {
            this(name, desc, icon, art, click, false);
        }
    }

    /** Bereich: Überschrift, Farbe der Überschrift, Kasten der Kacheln, Kacheln. */
    public record Section(String title, TextColor color, KitData.Box box, List<Tile> tiles) {}

    /** Schnellknopf: Beschriftung, Icon, eigene Grafik bis 16 px (darf fehlen), Knopf-Stil, Klick. */
    public record Quick(String label, KitData.Icon icon, KitData.Sprite art, KitData.Button style, Consumer<Player> click) {
        public Quick(String label, KitData.Icon icon, KitData.Button style, Consumer<Player> click) {
            this(label, icon, MainScreen.art(label), style, click);
        }
    }

    /**
     * Eigene Grafik je Eintrag (Pixel-Art aus tools/build_hub_icons.py und den Referenz-Grafiken). Gilt auch für
     * Einträge der Addons (gleicher Name); ohne Treffer bleibt das Icon.
     */
    public static KitData.Sprite art(String name) {
        return switch (name) {
            // Survival
            case "Angeln" -> KitData.Sprite.HB_ANGELN;
            case "Jobs" -> KitData.Sprite.HB_JOBS;
            case "Quests" -> KitData.Sprite.QU_LOGO;
            case "Haustiere" -> KitData.Sprite.HB_HAUSTIERE;
            case "Farmpass" -> KitData.Sprite.JSS_FP_ICON;
            case "Spieler-Warps" -> KitData.Sprite.HB_PORTAL;
            case "Grundstücke" -> KitData.Sprite.HB_GRUNDSTUECKE;
            case "Heiraten" -> KitData.Sprite.HB_HEIRATEN;
            case "Bank" -> KitData.Sprite.HB_BANK;
            // Skyblock
            case "Insel" -> KitData.Sprite.HB_INSEL;
            case "Top-Inseln" -> KitData.Sprite.QU_RANK_HEAD;
            case "Checkliste" -> KitData.Sprite.QU_T_SIDE;
            case "Generatoren" -> KitData.Sprite.GN_GELD_GOLD;
            case "One-Block" -> KitData.Sprite.HB_ONEBLOCK;
            case "Cookie Clicker" -> KitData.Sprite.CK_COOKIE;
            // SMP
            case "Duelle" -> KitData.Sprite.HB_DUELLE;
            case "Turnier" -> KitData.Sprite.HB_TURNIER;
            case "Trophäen" -> KitData.Sprite.QU_RANK_HEAD;
            case "Versicherung" -> KitData.Sprite.HB_VERSICHERUNG;
            // Alltag
            case "Shop" -> KitData.Sprite.HB_SHOP;
            case "Markt" -> KitData.Sprite.HB_MARKT;
            case "Wirtschaft" -> KitData.Sprite.HB_WIRTSCHAFT;
            case "Börse" -> KitData.Sprite.HB_BOERSE;
            case "Täglich" -> KitData.Sprite.QU_T_WEEKLY;
            case "Team" -> KitData.Sprite.QU_T_TEAM;
            case "Tagespot" -> KitData.Sprite.HB_TAGESPOT;
            case "Duo-RTP" -> KitData.Sprite.HB_DUO_RTP;
            case "Freunde" -> KitData.Sprite.FR_LOGO;
            // Fortschritt
            case "Battlepass" -> KitData.Sprite.JS_PASS;
            case "Spielzeit" -> KitData.Sprite.HB_SPIELZEIT;
            case "Titel" -> KitData.Sprite.HB_TITEL;
            case "Kits" -> KitData.Sprite.QU_BOX_HEAD;
            case "Kosmetik" -> KitData.Sprite.HB_KOSMETIK;
            case "Statistik" -> KitData.Sprite.PF_AVATAR;
            case "Erfolge" -> KitData.Sprite.QU_M_GOLD;
            // Schnellknöpfe und Fußleiste (16 px)
            case "Spawn" -> KitData.Sprite.QU_COMPASS;
            case "Wildnis" -> KitData.Sprite.HB_S_WILDNIS;
            case "Homes" -> KitData.Sprite.HB_S_HOMES;
            case "Enderchest" -> KitData.Sprite.HB_S_ENDERCHEST;
            case "Guide" -> KitData.Sprite.QU_S_HELP;
            case "Regeln" -> KitData.Sprite.PF_Q_QUESTS;
            case "Support" -> KitData.Sprite.SU_DISCORD;
            default -> null;
        };
    }

    /** Menü eines Modus: Kopf-Grafik und Farbe des Modus (null = Standard). */
    public record View(String player, String mode, long coins, long jewels, List<Quick> quick, List<Section> sections,
                       List<Quick> footer, KitData.Sprite head, TextColor modeColor) {
        public View(String player, String mode, long coins, long jewels, List<Quick> quick, List<Section> sections, List<Quick> footer) {
            this(player, mode, coins, jewels, quick, sections, footer, null, null);
        }
    }

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

    /**
     * Menü je Modus: SMP (Kampf, Battlepass, Duo-RTP), Survival (Farmen, Farmpass, Heiraten, Haustiere ...), Skyblock
     * (Insel, Top-Inseln, Checkliste) und Lobby. Einträge der Addons erscheinen nur in ihrem Modus.
     */
    View view(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        boolean smp = ModeService.SMP.equals(mode), surv = ModeService.SURVIVAL.equals(mode), sky = ModeService.SKYBLOCK.equals(mode);
        boolean hub = !smp && !surv && !sky;
        String modeName = smp ? "SMP" : surv ? "Survival" : sky ? "Skyblock" : "Lobby";
        TextColor modeColor = smp ? TextColor.color(0xFF7A6A) : surv ? TextColor.color(0x7FE08A) : sky ? TextColor.color(0x9AD8FF)
                : TextColor.color(0xF2C84A);
        KitData.Sprite head = smp ? KitData.Sprite.HB_DUELLE : surv ? KitData.Sprite.HB_GRUNDSTUECKE : sky ? KitData.Sprite.HB_INSEL
                : KitData.Sprite.HB_MENU;

        List<Quick> quick = new ArrayList<>();
        quick.add(new Quick("Spawn", KitData.Icon.COMPASS, KitData.Button.TEAL, go(pl -> Teleports.spawn(plugin, pl))));
        if (sky) {
            quick.add(new Quick("Insel", KitData.Icon.GRASS_BLOCK, KitData.Sprite.HB_S_HOMES, KitData.Button.GREEN, cmd("insel home")));
            quick.add(new Quick("Top-Inseln", KitData.Icon.GOLD_INGOT, KitData.Sprite.QU_S_RANK, KitData.Button.PURPLE, cmd("insel top")));
        } else if (!hub) {
            quick.add(new Quick("Wildnis", KitData.Icon.ENDER_PEARL, KitData.Button.GREEN, go(pl -> Teleports.randomTeleport(plugin, pl))));
            quick.add(new Quick("Homes", KitData.Icon.RED_BED, KitData.Button.PURPLE, go(pl -> new HomesScreen(plugin).open(pl))));
        } else {
            quick.add(new Quick("Modus wählen", KitData.Icon.COMPASS, KitData.Sprite.QU_COMPASS, KitData.Button.GREEN,
                    go(pl -> new ModeSelectMenu(plugin).open(pl))));
        }
        if (!hub) quick.add(new Quick("Enderchest", KitData.Icon.ENDER_CHEST, KitData.Button.DARK,
                go(pl -> pl.openInventory(plugin.enderchests().open(pl)))));

        // Modus: Einträge der Addons, dazu die Kern-Funktionen dieses Modus
        List<Tile> mine = new ArrayList<>();
        List<Tile> progress = new ArrayList<>();
        for (KubyPlugins.MainEntry e : plugin.mainEntries()) {
            if (!e.visibleIn(mode)) continue;
            Tile t = new Tile(e.name(), e.desc(), Icons.of(e.icon(), KitData.Icon.STAR), MainScreen.art(e.name()), go(e.action()),
                    plugin.menuAlert(e.name(), p));
            if (e.mode() == null) progress.add(t);
            else mine.add(t);
        }
        if (smp) {
            mine.add(new Tile("Battlepass", "100 Stufen, Quests und Belohnungen", KitData.Icon.BOOK, go(pl -> plugin.battlepassScreen().open(pl))));
            mine.add(new Tile("Duo-RTP", "Zu zweit an einen Zufallsort, Kampf!", KitData.Icon.FIRE_CHARGE, go(pl -> new ExtraScreens(plugin).rtp(pl))));
        }

        List<Tile> daily = new ArrayList<>();
        if (!hub) {
            daily.add(new Tile("Shop", "Sektionen, Wochenende und Daily Shop", KitData.Icon.EMERALD, go(pl -> new TradeScreens(plugin).shop(pl))));
            daily.add(new Tile("Markt", "Items kaufen und verkaufen", KitData.Icon.CHEST_BLOCK, go(pl -> new TradeScreens(plugin).market(pl))));
            daily.add(new Tile("Wirtschaft", "Rangliste der reichsten Spieler", KitData.Icon.GOLD_INGOT, go(pl -> new TopListScreen(plugin).money(pl))));
            daily.add(new Tile("Börse", "Anteile an Shop-Items kaufen und verkaufen", KitData.Icon.EMERALD, go(pl -> new ExtraScreens(plugin).investments(pl))));
        }
        daily.add(new Tile("Täglich", "30-Tage-Kalender mit Belohnungen", KitData.Icon.CLOCK, go(pl -> new DailyScreens(plugin).calendar(pl))));
        if (!hub) daily.add(new Tile("Team", "Team verwalten, Kasse und Teamhome", KitData.Icon.WHITE_BANNER, go(pl -> new TeamScreen(plugin).open(pl))));
        daily.add(new Tile("Tagespot", "Lose kaufen, Ziehung um 19 Uhr", KitData.Icon.GOLD_BLOCK, go(pl -> new DailyScreens(plugin).pot(pl))));
        daily.add(new Tile("Freunde", "Freunde, Anfragen und Nachspringen", KitData.Icon.COOKIE, cmd("friends")));

        List<Tile> prog = new ArrayList<>();
        prog.add(new Tile("Spielzeit", "Stufen nach aktiver Spielzeit", KitData.Icon.EXPERIENCE, go(pl -> new PlaytimeScreen(plugin).open(pl))));
        prog.add(new Tile("Titel", "Wähle deinen Titel neben dem Namen", KitData.Icon.NAME_TAG, go(pl -> new ProgressScreens(plugin).tags(pl))));
        if (!hub) prog.add(new Tile("Kits", "Starter- und Rang-Kits abholen", KitData.Icon.IRON_CHESTPLATE, go(pl -> new ProgressScreens(plugin).kits(pl))));
        prog.add(new Tile("Kosmetik", "Hüte, Spuren und Effekte", KitData.Icon.AMETHYST, go(pl -> new CosmeticsScreen(plugin).open(pl))));
        prog.add(new Tile("Statistik", "Deine Werte und Ranglisten", KitData.Icon.SPYGLASS, cmd("stats")));
        prog.addAll(progress);

        List<Section> sections = new ArrayList<>();
        KitData.Box modeBox = smp ? KitData.Box.RED : sky ? KitData.Box.TEAL : KitData.Box.GREEN;
        if (!mine.isEmpty()) sections.add(new Section(modeName, modeColor, modeBox, mine));
        sections.add(new Section("Alltag", TextColor.color(0x64C0FF), KitData.Box.NAVY, daily));
        sections.add(new Section("Fortschritt", TextColor.color(0xD08CFF), KitData.Box.PURPLE, prog));
        List<Quick> footer = List.of(
                new Quick("Guide", KitData.Icon.KNOWLEDGE_BOOK, KitData.Button.GOLD_LINE, cmd("guide")),
                new Quick("Regeln", KitData.Icon.WRITTEN_BOOK, KitData.Button.GOLD_LINE, cmd("regeln")),
                new Quick("Support", KitData.Icon.BELL, KitData.Button.GOLD_LINE, cmd("support")),
                new Quick("Einstellungen", KitData.Icon.SET_GEAR_BIG, KitData.Button.GOLD_LINE, cmd("settings")));
        return new View(p.getName(), modeName, plugin.economy().balance(p.getUniqueId()),
                plugin.jewels().balance(p.getUniqueId()), quick, sections, footer, head, modeColor);
    }

    /** Nur Darstellung (auch für Vorschau und Selbsttest). */
    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Hauptmenü", GOLD);
        // Kopf: Titel gold, darunter Gruß; rechts Coins und Juwelen
        c.box(0, 0, W, 54, KitData.Box.HUB);
        KitData.Sprite head = v.head() != null ? v.head() : KitData.Sprite.HB_MENU;
        c.sprite(10 + (32 - head.width) / 2, 11 + (32 - head.height) / 2, head);
        c.title(50, 12, "Menü");
        int pw = c.text(50, 36, Canvas.fit(Canvas.lit(v.player()), false, 140) + " · ", GRAY, false);
        c.text(50 + pw, 36, v.mode(), v.modeColor() != null ? v.modeColor() : GRAY, true);
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
                if (t.art() != null) c.sprite(x + (TW - t.art().width) / 2, ty + 3 + (32 - t.art().height) / 2, t.art());
                else c.icon(x + (TW - 32) / 2, ty + 3, t.icon(), true, Icons.tint(t.icon()));
                c.textCenter(x + TW / 2, ty + 35, Canvas.fit(t.name(), true, TW - 8), CREAM, true);
                c.action(x, ty, TW, TH, t.click(), t.name() + " · " + t.desc());
                if (t.alert()) c.sprite(x + TW - 9, ty - 6, KitData.Sprite.HB_ALERT);
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
            if (q.art() != null) c.sprite(gx + (16 - q.art().width) / 2, y + 3 + (16 - q.art().height) / 2, q.art());
            else c.icon(gx, y + 3, q.icon(), false);
            if (line) c.text(gx + 20, y + 7, label, color, false);
            else if (q.style() == KitData.Button.TEAL || q.style() == KitData.Button.GREEN) c.textFlat(gx + 20, y + 7, label, DARK, true);
            else c.text(gx + 20, y + 7, label, WHITE, true);
            c.action(x, y, w, 22, q.click(), q.label());
        }
    }
}
