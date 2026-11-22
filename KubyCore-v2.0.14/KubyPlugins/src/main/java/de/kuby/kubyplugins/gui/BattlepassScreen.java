package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.BattlepassService;
import de.kuby.kubyplugins.service.BattlepassService.Period;
import de.kuby.kubyplugins.service.BattlepassService.QuestState;
import de.kuby.kubyplugins.service.BattlepassService.Reward;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Battle-Pass auf dem Dialog-Kit wie in der Referenz: Kopf mit Season-Countdown, Reiter Belohnungen, Aufgaben und
 * Premium. Belohnungen: sieben Stufen je Seite mit Free- und Premium-Feldern, Stufen-Knöpfen und Verbindungsbalken;
 * ein Klick auf ein erreichtes Feld holt die Spur ab. Aufgaben: Tages- und Wochenaufgaben mit Balken. Premium:
 * Premium-Pass holen und Stufen kaufen (Menge mit −5/−1/+1/+5). Käufe brauchen einen zweiten Klick.
 *
 * <p>Ohne Grafik-Pack öffnet /battlepass weiter das Truhen-Menü (BattlepassMenu).
 */
public final class BattlepassScreen {

    public enum Tab { REWARDS, QUESTS, PREMIUM }

    /** Feld einer Spur: keine Belohnung, nicht erreicht, abholbar, abgeholt, Premium fehlt. */
    public enum Slot { EMPTY, LOCKED, READY, CLAIMED, NO_PREMIUM }

    static final int W = 390, HEIGHT = 270, PER_PAGE = 7;
    private static final long ARM_MS = 8000;

    private static final TextColor SUB = TextColor.color(0x9FA1A7),
            GOLD = TextColor.color(0xFCD45D), GOLD_Q = TextColor.color(0xFCC965),
            BULLET = TextColor.color(0xFCC63C), DESC = TextColor.color(0xD1D2D5), WHITE = TextColor.color(0xFCFCFC),
            CREAM = TextColor.color(0xFCEECD), GRAY = TextColor.color(0xC0C4CB),
            OFF = TextColor.color(0x797D84), PILL_DARK = TextColor.color(0x1C1A23), GREEN = TextColor.color(0x6DE288),
            JEWELS = TextColor.color(0x7CEDA6), TIP_CLICK = TextColor.color(0xFCFC54);
    /** Abgedunkelte Icons (nicht erreicht bzw. abgeholt), Farben aus der Referenz. */
    private static final TextColor TINT_LOCKED = TextColor.color(0x767880), TINT_CLAIMED = TextColor.color(0xA2A2A2),
            TINT_READY = TextColor.color(0xFFFFFF);

    /**
     * Stil je Pass (v2.05): Battlepass (SMP) violett mit Ticket, Skypass (Skyblock) himmelblau mit schwebender Insel,
     * Free-Reihe in Wiesen-Türkis. Gold (Premium), Grün (abgeholt) und Grau bleiben gleich.
     */
    public record Look(String name, KitData.Box head, KitData.Box level, KitData.Box free, KitData.Box tab,
                       KitData.Box tabOn, KitData.Box pillOn, KitData.Box pillCur, KitData.Box questDay,
                       KitData.Box buyBox, KitData.Box btnMain, KitData.Fill fill, KitData.Sprite ticket,
                       KitData.Sprite title, KitData.Sprite freepass, KitData.Sprite play, KitData.Sprite tabScroll,
                       TextColor accent, TextColor freeColor, TextColor dark) {}

    public static final Look BATTLE = new Look("Battlepass", KitData.Box.BP_HEAD, KitData.Box.BP_LEVEL,
            KitData.Box.BP_FREE, KitData.Box.BP_TAB, KitData.Box.BP_TAB_ON, KitData.Box.BP_PILL_ON,
            KitData.Box.BP_PILL_CUR, KitData.Box.BP_QUEST_DAY, KitData.Box.BP_BUY_BOX, KitData.Box.BP_BTN_VIOLET,
            KitData.Fill.BP_VIOLET, KitData.Sprite.BP_TICKET, KitData.Sprite.BP_TITLE, KitData.Sprite.BP_FREEPASS,
            KitData.Sprite.BP_PLAY, KitData.Sprite.BP_TAB_SCROLL, TextColor.color(0xC7B6FC), TextColor.color(0xB5CCED),
            TextColor.color(0x1C122D));
    public static final Look SKY = new Look("Skypass", KitData.Box.SP_HEAD, KitData.Box.SP_LEVEL,
            KitData.Box.SP_FREE, KitData.Box.SP_TAB, KitData.Box.SP_TAB_ON, KitData.Box.SP_PILL_ON,
            KitData.Box.SP_PILL_CUR, KitData.Box.SP_QUEST_DAY, KitData.Box.SP_BUY_BOX, KitData.Box.SP_BTN_SKY,
            KitData.Fill.SP_SKY, KitData.Sprite.SP_ISLAND, KitData.Sprite.SP_TITLE, KitData.Sprite.SP_FREEPASS,
            KitData.Sprite.SP_PLAY, KitData.Sprite.SP_TAB_SCROLL, TextColor.color(0x9EDCF7), TextColor.color(0xA8EBD0),
            TextColor.color(0x0F2233));

    public static Look look(String pass) {
        return BattlepassService.SKYPASS.equals(pass) ? SKY : BATTLE;
    }

    /** Ein Feld: Stufe, Belohnungen, Zustand. */
    public record Cell(int level, List<Reward> rewards, Slot state) {}

    public record View(Look look, Tab tab, boolean english, int season, long seasonLeft, int level, long points, int perLevel,
                       boolean premium, int page, int pages, List<Cell> free, List<Cell> prem, int claimable,
                       List<QuestState> daily, List<QuestState> weekly, long dailyLeft, long weeklyLeft,
                       long premiumPrice, int freeCount, int premCount, String perks, long jewels, int amount,
                       long levelPrice, int leftToday, String armed) {}

    public interface Actions {
        Consumer<Player> tab(Tab tab);

        Consumer<Player> page(int page);

        Consumer<Player> claim();

        Consumer<Player> premium();

        Consumer<Player> amount(int delta);

        Consumer<Player> buy();
    }

    private final KubyPlugins plugin;
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, Integer> amounts = new HashMap<>();
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();

    public BattlepassScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /**
     * /battlepass bzw. /skypass: Belohnungen auf der Seite der nächsten Stufe. Ohne Grafik-Pack das Truhen-Menü.
     * In SMP der Battlepass, in Skyblock der Skypass, sonst ein Hinweis (Survival: Farmpass und Angelpass).
     */
    public void open(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        if (!BattlepassService.enabled(mode)) {
            boolean en = plugin.playerSettings().english(p.getUniqueId());
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, de.kuby.kubyplugins.service.ModeService.SURVIVAL.equals(mode)
                    ? (en ? "<gray>Survival has its own passes: <white>/farmpass <gray>and <white>/angeln pass<gray>."
                    : "<gray>Survival hat eigene Pässe: <white>/farmpass <gray>und <white>/angeln pass<gray>.")
                    : (en ? "<gray>The <light_purple>Battlepass <gray>is in <white>SMP<gray>, the <aqua>Skypass <gray>in <white>Skyblock<gray>."
                    : "<gray>Den <light_purple>Battlepass <gray>gibt es in <white>SMP<gray>, den <aqua>Skypass <gray>in <white>Skyblock<gray>."));
            return;
        }
        int level = plugin.battlepass().level(p.getUniqueId());
        pages.put(p.getUniqueId(), Math.min(level, BattlepassService.MAX_LEVEL - 1) / PER_PAGE);
        open(p, Tab.REWARDS);
    }

    public void open(Player p, Tab tab) {
        plugin.questProgress(p, "battlepass", tab.name(), 1);
        if (!plugin.uiPack().active(p)) {
            new BattlepassMenu(plugin, 0, plugin.battlepass().passOf(p.getUniqueId())).open(p);
            return;
        }
        UUID id = p.getUniqueId();
        Long at = armedAt.get(id);
        if (at != null && System.currentTimeMillis() - at > ARM_MS) {
            armed.remove(id);
            armedAt.remove(id);
        }
        if (!BattlepassService.enabled(plugin.modes().modeOf(id))) return;
        UiDialog.show(p, canvas(view(p, tab), actions(tab)), HEIGHT);
    }

    public void forget(UUID id) {
        pages.remove(id);
        amounts.remove(id);
        armed.remove(id);
        armedAt.remove(id);
    }

    private static int pageCount() {
        return (BattlepassService.MAX_LEVEL + PER_PAGE - 1) / PER_PAGE;
    }

    View view(Player p, Tab tab) {
        UUID id = p.getUniqueId();
        BattlepassService bp = plugin.battlepass();
        String pass = bp.passOf(id);
        int level = bp.level(id);
        boolean premium = bp.hasPremium(id);
        int page = Math.max(0, Math.min(pageCount() - 1, pages.getOrDefault(id, 0)));
        List<Cell> free = cells(pass, page, level, BattlepassService.FREE, bp.claimedOf(id, BattlepassService.FREE), true);
        List<Cell> prem = cells(pass, page, level, BattlepassService.PREMIUM, bp.claimedOf(id, BattlepassService.PREMIUM), premium);
        int left = bp.levelsLeftToday(id);
        int amount = Math.max(1, Math.min(Math.max(1, left), amounts.getOrDefault(id, 1)));
        return new View(look(pass), tab, plugin.playerSettings().english(id), bp.season(pass), bp.seasonLeft(pass), level,
                bp.pointsInLevel(id), bp.xpPerLevel(pass), premium, page, pageCount(), free, prem,
                bp.claimable(id, BattlepassService.FREE) + bp.claimable(id, BattlepassService.PREMIUM),
                bp.quests(id, Period.DAILY), bp.quests(id, Period.WEEKLY),
                BattlepassService.untilReset(Period.DAILY), BattlepassService.untilReset(Period.WEEKLY),
                bp.premiumPrice(pass), bp.rewardLevels(BattlepassService.FREE, pass),
                bp.rewardLevels(BattlepassService.PREMIUM, pass), perks(pass, plugin.playerSettings().english(id)),
                plugin.jewels().balance(id), amount, bp.levelPrice(pass), left, armed.get(id));
    }

    private List<Cell> cells(String pass, int page, int level, String track, int claimed, boolean unlocked) {
        Map<Integer, List<Reward>> all = plugin.battlepass().byLevel(track, pass);
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < PER_PAGE; i++) {
            int lvl = page * PER_PAGE + i + 1;
            if (lvl > BattlepassService.MAX_LEVEL) break;
            List<Reward> rs = all.getOrDefault(lvl, List.of());
            Slot s;
            if (rs.isEmpty()) s = Slot.EMPTY;
            else if (!unlocked) s = Slot.NO_PREMIUM;
            else if (lvl <= claimed) s = Slot.CLAIMED;
            else if (lvl <= level) s = Slot.READY;
            else s = Slot.LOCKED;
            out.add(new Cell(lvl, rs, s));
        }
        return out;
    }

    private String perks(String pass, boolean en) {
        boolean sky = BattlepassService.SKYPASS.equals(pass);
        String path = pass + (en ? ".premium-perks-en" : ".premium-perks");
        return plugin.getConfig().getString(path, sky ? (en ? "Saplings, farm blocks and rare island items"
                : "Setzlinge, Farm-Blöcke und seltene Insel-Items")
                : en ? "Gear, spawners and cosmetics" : "Ausrüstung, Spawner und Kosmetik");
    }

    /** Ansicht ohne Spieler (Selbsttest, Vorschau): Stufe 3 mit 50 Punkten, Stufe 2 abgeholt, wie in der Referenz. */
    public View sample(Tab tab) {
        return sample(tab, BattlepassService.BATTLEPASS);
    }

    public View sample(Tab tab, String pass) {
        BattlepassService bp = plugin.battlepass();
        List<Cell> free = new ArrayList<>(), prem = new ArrayList<>();
        Map<Integer, List<Reward>> f = bp.byLevel(BattlepassService.FREE, pass), pr = bp.byLevel(BattlepassService.PREMIUM, pass);
        for (int lvl = 1; lvl <= PER_PAGE; lvl++) {
            List<Reward> a = f.getOrDefault(lvl, List.of()), b = pr.getOrDefault(lvl, List.of());
            free.add(new Cell(lvl, a, a.isEmpty() ? Slot.EMPTY : lvl <= 3 ? Slot.CLAIMED : Slot.LOCKED));
            prem.add(new Cell(lvl, b, b.isEmpty() ? Slot.EMPTY : Slot.NO_PREMIUM));
        }
        List<QuestState> daily = new ArrayList<>(), weekly = new ArrayList<>();
        List<BattlepassService.Quest> d = bp.pool(Period.DAILY, pass), w = bp.pool(Period.WEEKLY, pass);
        for (int i = 0; i < Math.min(bp.questCount(Period.DAILY, pass), d.size()); i++) daily.add(new QuestState(d.get(i), 0, false));
        for (int i = 0; i < Math.min(bp.questCount(Period.WEEKLY, pass), w.size()); i++) {
            weekly.add(new QuestState(w.get(i), i == 0 || i == 4 ? 1 : 0, false));
        }
        long day = 17 * 3_600_000L + 9 * 60_000L + 3_000L;
        long season = (46 * 86_400L + 16 * 3_600L + 9 * 60L + 11L) * 1000L;
        return new View(look(pass), tab, false, 3, season, 3, 50, bp.xpPerLevel(pass), false, 0, pageCount(), free, prem, 0,
                daily, weekly, day, day, bp.premiumPrice(pass), bp.rewardLevels(BattlepassService.FREE, pass),
                bp.rewardLevels(BattlepassService.PREMIUM, pass), perks(pass, false), 0, 1, bp.levelPrice(pass),
                bp.levelDailyLimit(pass), null);
    }

    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    private Actions actions(Tab tab) {
        return new Actions() {
            public Consumer<Player> tab(Tab target) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    armed.remove(pl.getUniqueId());
                    open(pl, target);
                };
            }

            public Consumer<Player> page(int page) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.TICK);
                    pages.put(pl.getUniqueId(), Math.max(0, Math.min(pageCount() - 1, page)));
                    open(pl, Tab.REWARDS);
                };
            }

            public Consumer<Player> claim() {
                return pl -> {
                    BattlepassService bp = plugin.battlepass();
                    int n = bp.claimTrack(pl, BattlepassService.FREE);
                    if (bp.hasPremium(pl.getUniqueId())) n += bp.claimTrack(pl, BattlepassService.PREMIUM);
                    boolean en = plugin.playerSettings().english(pl.getUniqueId());
                    if (n == 0) {
                        Sfx.play(plugin, pl, Sfx.Kind.ERROR);
                        Msg.send(pl, en ? "<gray>Nothing to claim yet." : "<gray>Noch nichts zum Abholen.");
                    } else {
                        Sfx.play(plugin, pl, Sfx.Kind.LEVEL_UP);
                        Msg.send(pl, en ? "<green>Claimed rewards of " + n + (n == 1 ? " level." : " levels.")
                                : "<green>Belohnungen von " + n + (n == 1 ? " Stufe" : " Stufen") + " abgeholt.");
                    }
                    open(pl, tab);
                };
            }

            public Consumer<Player> premium() {
                return pl -> {
                    if (!confirm(pl, "premium")) return;
                    String err = plugin.battlepass().buyPremium(pl);
                    boolean en = plugin.playerSettings().english(pl.getUniqueId());
                    Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                    Msg.send(pl, err == null ? (en ? "<gold>Premium pass unlocked! <gray>Claim your rewards under Rewards."
                            : "<gold>Premium-Pass freigeschaltet! <gray>Abholen unter Belohnungen.") : "<red>" + err);
                    open(pl, Tab.PREMIUM);
                };
            }

            public Consumer<Player> amount(int delta) {
                return pl -> {
                    UUID id = pl.getUniqueId();
                    int left = Math.max(1, plugin.battlepass().levelsLeftToday(id));
                    amounts.put(id, Math.max(1, Math.min(left, amounts.getOrDefault(id, 1) + delta)));
                    armed.remove(id);
                    Sfx.play(plugin, pl, Sfx.Kind.TICK);
                    open(pl, Tab.PREMIUM);
                };
            }

            public Consumer<Player> buy() {
                return pl -> {
                    if (!confirm(pl, "levels")) return;
                    UUID id = pl.getUniqueId();
                    int n = amounts.getOrDefault(id, 1);
                    String err = plugin.battlepass().buyLevels(pl, n);
                    boolean en = plugin.playerSettings().english(id);
                    Sfx.play(plugin, pl, err == null ? Sfx.Kind.LEVEL_UP : Sfx.Kind.ERROR);
                    if (err == null) {
                        amounts.remove(id);
                        Msg.send(pl, en ? "<green>Bought " + n + (n == 1 ? " level" : " levels") + ". <gray>Now level "
                                + plugin.battlepass().level(id) + "." : "<green>" + n + (n == 1 ? " Stufe" : " Stufen")
                                + " gekauft. <gray>Jetzt Stufe " + plugin.battlepass().level(id) + ".");
                    } else {
                        Msg.send(pl, "<red>" + err);
                    }
                    open(pl, Tab.PREMIUM);
                };
            }
        };
    }

    /** Erster Klick schaltet scharf (Knopf zeigt „Sicher?“), zweiter Klick innerhalb von 8 s kauft. */
    private boolean confirm(Player p, String what) {
        UUID id = p.getUniqueId();
        Long at = armedAt.get(id);
        if (what.equals(armed.get(id)) && at != null && System.currentTimeMillis() - at <= ARM_MS) {
            armed.remove(id);
            armedAt.remove(id);
            return true;
        }
        armed.put(id, what);
        armedAt.put(id, System.currentTimeMillis());
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        open(p, Tab.PREMIUM);
        return false;
    }

    // =================================================================================================================
    // Zeichnen
    // =================================================================================================================

    /** Restzeit wie in der Referenz: 1mo 16d 16h 9m 11s (führende Nullen weg, Monat = 30 Tage). */
    public static String countdown(long ms) {
        long s = Math.max(0, ms / 1000);
        long[] v = {s / 2_592_000, s % 2_592_000 / 86_400, s % 86_400 / 3_600, s % 3_600 / 60, s % 60};
        String[] u = {"mo", "d", "h", "m", "s"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (v[i] == 0 && b.isEmpty() && i < v.length - 1) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(v[i]).append(u[i]);
        }
        return b.toString();
    }

    static KitData.Icon icon(String name, KitData.Icon fallback) {
        String n = name.toLowerCase(Locale.ROOT);
        n = switch (n) {
            case "experience_bottle" -> "experience";
            case "totem_of_undying" -> "totem";
            case "fishing_rod" -> "rod";
            case "enchanted_book" -> "book";
            case "nether_star" -> "star";
            case "tropical_fish" -> "tropical";
            case "pufferfish" -> "puffer";
            case "enchanted_golden_apple" -> "golden_apple";
            default -> n;
        };
        try {
            return KitData.Icon.valueOf(n.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Icon einer Belohnung: Feld ICON, sonst nach Art bzw. Material. */
    static KitData.Icon icon(Reward r) {
        if (!r.icon().isEmpty()) return icon(r.icon(), KitData.Icon.BUNDLE);
        return switch (r.type()) {
            case "COINS" -> KitData.Icon.GOLD_NUGGET;
            case "XP" -> KitData.Icon.EXPERIENCE;
            case "JEWELS" -> KitData.Icon.EMERALD;
            case "COMMAND" -> KitData.Icon.BP_KEY;
            case "ITEM" -> icon(r.value().split(":")[0], KitData.Icon.BUNDLE);
            default -> KitData.Icon.PAPER;
        };
    }

    /**
     * Bild einer Belohnung bzw. Aufgabe: eigenes Kit-Icon, sonst das Item-Bild aus dem Pack (Skypass: Setzlinge,
     * Knochenmehl, Lava …), zuletzt das Bündel.
     */
    static void drawIcon(Canvas c, int x, int y, String name, boolean big, TextColor tint) {
        KitData.Icon kit = icon(name, null);
        org.bukkit.Material m = kit == null ? org.bukkit.Material.matchMaterial(name) : null;
        if (m != null) c.item(x, y, m, big, KitData.Icon.BUNDLE, tint);
        else c.icon(x, y, kit != null ? kit : KitData.Icon.BUNDLE, big, tint);
    }

    static void drawReward(Canvas c, int x, int y, Reward r, boolean big, TextColor tint) {
        if (r.icon().isEmpty() && "ITEM".equals(r.type())) drawIcon(c, x, y, r.value().split(":")[0], big, tint);
        else c.icon(x, y, icon(r), big, tint);
    }

    private static void center(Canvas c, int cx, int y, String text, TextColor color, boolean bold) {
        int w = Canvas.width(Canvas.clean(text), bold);
        c.text(cx - (w + 1) / 2, y, text, color, bold);
    }

    public static Canvas canvas(View v, Actions a) {
        Canvas c = new Canvas(W).heading(v.look().name(), SUB);
        head(c, v, a);
        tabs(c, v, a);
        switch (v.tab()) {
            case REWARDS -> rewards(c, v, a);
            case QUESTS -> quests(c, v);
            case PREMIUM -> premium(c, v, a);
        }
        return c;
    }

    private static void head(Canvas c, View v, Actions a) {
        boolean en = v.english();
        Look l = v.look();
        c.box(0, 0, W, 48, l.head());
        c.sprite(4, 8, l.ticket());
        c.sprite(50, 10, l.title());
        String left = v.seasonLeft() < 0 ? "" : en ? " · " + countdown(v.seasonLeft()) + " left"
                : " · noch " + countdown(v.seasonLeft());
        c.text(50, 30, "Season " + v.season() + left, SUB, false);
        String label = v.premium() ? "Premium-Pass" : "Free-Pass";
        int w = 28 + Canvas.width(label, false) + 8;
        int x = 383 - w;
        c.box(x, 13, w, 22, KitData.Box.BP_GRAY, 1);
        c.sprite(x + 3, 16, v.look().freepass());
        c.text(x + 28, 20, label, v.premium() ? GOLD : GRAY, false);
        if (a != null) {
            c.action(x, 13, w, 22, a.tab(Tab.PREMIUM), v.premium()
                    ? tip(label, en ? "You have the premium pass this season." : "Du hast den Premium-Pass in dieser Season.", null)
                    : tip(label, en ? "You have the free pass. Premium gives a reward on every level."
                    : "Du hast den Free-Pass. Premium gibt auf jeder Stufe eine Belohnung.",
                    en ? "Click: get premium" : "Klick: Premium holen"));
        }
    }

    private static void tabs(Canvas c, View v, Actions a) {
        boolean en = v.english();
        int x = 0;
        for (Tab t : Tab.values()) {
            boolean on = v.tab() == t;
            String label = switch (t) {
                case REWARDS -> en ? "Rewards" : "Belohnungen";
                case QUESTS -> en ? "Quests" : "Aufgaben";
                case PREMIUM -> "Premium";
            };
            Look l = v.look();
            KitData.Box style = t == Tab.PREMIUM ? (on ? KitData.Box.BP_TAB_GOLD_ON : KitData.Box.BP_TAB_GOLD)
                    : on ? l.tabOn() : l.tab();
            KitData.Sprite icon = switch (t) {
                case REWARDS -> KitData.Sprite.BP_TAB_CHEST;
                case QUESTS -> l.tabScroll();
                case PREMIUM -> KitData.Sprite.BP_TAB_CROWN;
            };
            int w = 28 + Canvas.width(label, on) + 8;
            c.box(x, 54, w, 22, style);
            c.sprite(x + 3, 57, icon);
            c.text(x + 28, 61, label, on ? l.dark() : t == Tab.PREMIUM ? GOLD : l.accent(), on);
            if (a != null && !on) c.action(x, 54, w, 22, a.tab(t), Component.text(label, WHITE));
            x += w + 4;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Belohnungen
    // ---------------------------------------------------------------------------------------------------------------

    private static void rewards(Canvas c, View v, Actions a) {
        boolean en = v.english();
        pager(c, v, a);
        // Stufe und Punkte
        Look l = v.look();
        c.box(0, 81, W, 33, l.level());
        boolean max = v.level() >= BattlepassService.MAX_LEVEL;
        c.text(9, 88, List.of(Span.bold((en ? "Level " : "Stufe ") + v.level(), l.accent()),
                Span.of(" / " + BattlepassService.MAX_LEVEL, SUB)));
        c.barFlat(9, 101, 170, max ? 1 : (double) v.points() / v.perLevel(), l.fill());
        if (max) {
            c.text(185, 100, en ? "Highest level reached" : "Höchste Stufe erreicht", SUB, false);
        } else {
            c.text(185, 100, List.of(Span.of(Msg.coins(v.points()), CREAM),
                    Span.of(" / " + Msg.coins(v.perLevel()) + (en ? " points" : " Punkte"), SUB)));
        }
        if (v.claimable() > 0) {
            String label = (en ? "Claim · " : "Abholen · ") + v.claimable();
            int w = Canvas.width(label, true) + 14;
            int x = 383 - w;
            c.box(x, 86, w, 22, KitData.Box.BP_BTN_GREEN, 1);
            c.text(x + 8, 93, label, GREEN, true);
            if (a != null) {
                c.action(x, 86, w, 22, a.claim(), tip(en ? "Claim rewards" : "Belohnungen abholen",
                        en ? "Claims all reached rewards of your tracks." : "Holt alle erreichten Belohnungen deiner Spuren ab.",
                        en ? "Click: claim" : "Klick: abholen"));
            }
        } else {
            String label = en ? "All claimed" : "Alles abgeholt";
            int w = Canvas.width(label, false);
            c.sprite(381 - w - 20, 91, KitData.Sprite.BP_CHECK_BIG);
            c.text(381 - w, 94, label, SUB, false);
        }
        // Free
        c.box(0, 117, W, 60, l.free());
        c.sprite(19, 130, KitData.Sprite.BP_FREE_CHEST);
        center(c, 27, 152, "Free", l.freeColor(), false);
        for (int i = 0; i < v.free().size(); i++) slot(c, v, a, 49 + 48 * i, 124, v.free().get(i), false);
        // Stufen
        center(c, 27, 188, en ? "Level" : "Stufe", SUB, false);
        int first = v.page() * PER_PAGE + 1;
        int n = Math.min(PER_PAGE, BattlepassService.MAX_LEVEL - first + 1);
        for (int i = 0; i < n; i++) {
            int lvl = first + i;
            int x = 56 + 48 * i;
            KitData.Box style;
            TextColor color;
            if (lvl <= v.level()) {
                style = l.pillOn();
                color = PILL_DARK;
            } else if (lvl == v.level() + 1) {
                style = l.pillCur();
                color = l.accent();
            } else {
                style = KitData.Box.BP_PILL_OFF;
                color = GRAY;
            }
            c.box(x, 181, 32, 22, style);
            center(c, x + 16, 188, String.valueOf(lvl), color, lvl <= v.level() + 1);
            if (i < n - 1) {
                double frac = lvl + 1 <= v.level() ? 1 : lvl == v.level() ? (double) v.points() / v.perLevel() : 0;
                c.barFlat(x + 34, 188, 12, frac, lvl + 1 <= v.level() ? KitData.Fill.BP_GREEN : l.fill());
            }
        }
        // Premium
        c.box(0, 207, W, 60, KitData.Box.BP_PREM);
        if (v.premium()) c.sprite(17, 221, KitData.Sprite.BP_TAB_CROWN);
        else c.sprite(21, 220, KitData.Sprite.BP_PREM_LOCK);
        center(c, 27, 242, "Premium", GOLD, false);
        for (int i = 0; i < v.prem().size(); i++) slot(c, v, a, 49 + 48 * i, 214, v.prem().get(i), true);
    }

    private static void pager(Canvas c, View v, Actions a) {
        boolean en = v.english();
        int first = v.page() * PER_PAGE + 1;
        int last = Math.min(BattlepassService.MAX_LEVEL, first + PER_PAGE - 1);
        String label = first + "–" + last;
        int mw = Canvas.width(label, false) + 16;
        int mx = 358 - mw;
        boolean prev = v.page() > 0, next = v.page() < v.pages() - 1;
        Look l = v.look();
        c.box(mx - 25, 54, 21, 22, prev ? l.tab() : KitData.Box.BP_GRAY);
        c.text(mx - 18, 61, "‹", prev ? l.accent() : OFF, true);
        c.box(mx, 54, mw, 22, l.tab());
        c.text(mx + 8, 61, label, WHITE, false);
        c.box(362, 54, 21, 22, next ? l.tab() : KitData.Box.BP_GRAY);
        c.text(370, 61, "›", next ? l.accent() : OFF, true);
        if (a != null) {
            if (prev) c.action(mx - 25, 54, 21, 22, a.page(v.page() - 1), Component.text(en ? "Previous levels" : "Vorherige Stufen", WHITE));
            if (next) c.action(362, 54, 21, 22, a.page(v.page() + 1), Component.text(en ? "Next levels" : "Nächste Stufen", WHITE));
        }
    }

    private static void slot(Canvas c, View v, Actions a, int x, int y, Cell cell, boolean premium) {
        boolean en = v.english();
        KitData.Box style = switch (cell.state()) {
            case EMPTY -> premium ? KitData.Box.BP_SLOT_GOLD : KitData.Box.BP_SLOT;
            case CLAIMED -> KitData.Box.BP_SLOT_GREEN;
            default -> premium ? KitData.Box.BP_SLOT_GOLD : KitData.Box.BP_SLOT_ITEM;
        };
        c.box(x, y, 46, 46, style, 1);
        if (cell.state() == Slot.EMPTY) return;
        TextColor tint = switch (cell.state()) {
            case READY -> TINT_READY;
            case CLAIMED -> TINT_CLAIMED;
            default -> TINT_LOCKED;
        };
        drawReward(c, x + 7, y + 7, cell.rewards().get(0), true, tint);
        if (cell.state() == Slot.CLAIMED) c.sprite(x + 27, y + 28, KitData.Sprite.BP_CHECK);
        if (cell.state() == Slot.NO_PREMIUM) c.sprite(x + 29, y + 27, KitData.Sprite.BP_LOCK);
        if (a == null) return;
        TextComponent.Builder b = Component.text();
        b.append(Component.text((en ? "Level " : "Stufe ") + cell.level() + " · " + (premium ? "Premium" : "Free"),
                premium ? GOLD : v.look().freeColor()).decorate(TextDecoration.BOLD));
        for (Reward r : cell.rewards()) {
            b.append(Component.newline()).append(Msg.parse("<gray>• " + BattlepassService.describe(r, en)));
        }
        b.append(Component.newline()).append(Component.newline());
        b.append(Component.text(switch (cell.state()) {
            case READY -> en ? "Click: claim" : "Klick: abholen";
            case CLAIMED -> en ? "Claimed" : "Abgeholt";
            case NO_PREMIUM -> en ? "Needs the premium pass · Click: get premium" : "Braucht den Premium-Pass · Klick: Premium holen";
            default -> en ? "Reach level " + cell.level() + " to unlock" : "Ab Stufe " + cell.level();
        }, cell.state() == Slot.CLAIMED ? GREEN : TIP_CLICK));
        Consumer<Player> click = switch (cell.state()) {
            case READY -> a.claim();
            case NO_PREMIUM -> a.tab(Tab.PREMIUM);
            default -> null;
        };
        if (click != null) c.action(x, y, 46, 46, click, b.build());
        else c.action(x, y, 46, 46, pl -> { }, b.build());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Aufgaben
    // ---------------------------------------------------------------------------------------------------------------

    private static void quests(Canvas c, View v) {
        boolean en = v.english();
        int y = questBox(c, v, 81, v.daily(), en ? "Daily quests" : "Tagesaufgaben", v.look().accent(), v.dailyLeft(),
                v.look().questDay(), v.look().fill());
        questBox(c, v, y + 3, v.weekly(), en ? "Weekly quests" : "Wochenaufgaben", GOLD_Q, v.weeklyLeft(),
                KitData.Box.BP_QUEST_WEEK, KitData.Fill.BP_GOLD);
    }

    /** Kasten mit Aufgaben in zwei Spalten. Gibt die Unterkante zurück. */
    private static int questBox(Canvas c, View v, int y, List<QuestState> list, String title, TextColor color,
                                long left, KitData.Box style, KitData.Fill fill) {
        boolean en = v.english();
        int rows = Math.max(1, (list.size() + 1) / 2);
        int h = 15 + 27 * rows;
        c.box(0, y, W, h, style);
        long done = list.stream().filter(QuestState::done).count();
        int tw = c.text(9, y + 8, title, color, true);
        c.text(9 + tw + 6, y + 8, done + "/" + list.size(), SUB, false);
        String reset = (en ? "New in " : "Neu in ") + countdown(left);
        c.textRight(381, y + 8, reset, SUB, false);
        for (int i = 0; i < list.size(); i++) {
            QuestState q = list.get(i);
            int bx = 9 + 190 * (i % 2), by = y + 20 + 22 * (i / 2);
            drawIcon(c, bx, by + 1, q.quest().icon().isEmpty() ? "paper" : q.quest().icon(), false, TINT_READY);
            c.text(bx + 21, by, Canvas.fit(q.quest().label(en), true, 160), WHITE, true);
            double frac = (double) q.progress() / q.quest().target();
            c.barFlat(bx + 21, by + 12, 64, frac, q.done() ? KitData.Fill.BP_GREEN : fill);
            if (q.done()) {
                c.text(bx + 91, by + 11, en ? "✓ Done" : "✓ Erledigt", GREEN, false);
            } else {
                c.text(bx + 91, by + 11, List.of(Span.of(Msg.coins(q.progress()), CREAM),
                        Span.of("/" + Msg.coins(q.quest().target()), SUB)));
            }
            c.textRight(bx + 182, by + 11, "+" + Msg.coins(q.quest().points()) + " P", q.done() ? SUB : color, false);
        }
        return y + h;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Premium und Stufen kaufen
    // ---------------------------------------------------------------------------------------------------------------

    private static void premium(Canvas c, View v, Actions a) {
        boolean en = v.english();
        c.box(0, 81, W, 78, KitData.Box.BP_PREM_BOX);
        c.sprite(11, 96, KitData.Sprite.BP_CROWN);
        c.text(66, 89, "Premium-Pass", GOLD, true);
        String[] lines = {
                en ? "A reward on every level, " + v.premCount() + " instead of " + v.freeCount()
                        : "Jede Stufe eine Belohnung, " + v.premCount() + " statt " + v.freeCount(),
                v.perks(),
                en ? "Retroactive: claim what you reached right away" : "Rückwirkend: Erreichtes sofort abholen"};
        for (int i = 0; i < lines.length; i++) {
            c.text(66, 101 + 11 * i, "■", BULLET, false);
            c.text(75, 101 + 11 * i, Canvas.fit(lines[i], false, 300), DESC, false);
        }
        if (v.premium()) {
            String label = en ? "✓ Premium active" : "✓ Premium aktiv";
            int w = Canvas.width(label, true) + 14;
            c.box(66, 132, w, 22, KitData.Box.BP_BTN_GREEN, 1);
            c.text(74, 139, label, GREEN, true);
        } else {
            boolean armedNow = "premium".equals(v.armed());
            String label = (armedNow ? (en ? "Really buy? · " : "Wirklich kaufen? · ") : (en ? "Get premium · " : "Premium holen · "))
                    + Msg.coins(v.premiumPrice());
            int w = 28 + Canvas.width(label, true) + 8;
            c.box(66, 132, w, 22, KitData.Box.BP_BTN_GOLD_ON, 1);
            c.sprite(75, 135, KitData.Sprite.JS_EMERALD_PRICE);
            c.text(94, 139, label, v.look().dark(), true);
            if (a != null) {
                boolean enough = v.jewels() >= v.premiumPrice();
                c.action(66, 132, w, 22, a.premium(), tip(en ? "Premium pass" : "Premium-Pass",
                        (en ? "Costs " : "Kostet ") + Msg.coins(v.premiumPrice()) + (en ? " jewels, valid for season " : " Juwelen, gilt für Season ")
                                + v.season() + (en ? "." : ".") + (enough ? "" : en ? " You are missing " + Msg.coins(v.premiumPrice() - v.jewels()) + " jewels."
                                : " Dir fehlen " + Msg.coins(v.premiumPrice() - v.jewels()) + " Juwelen."),
                        armedNow ? (en ? "Click again: buy now" : "Nochmal klicken: jetzt kaufen")
                                : en ? "Click: buy (then confirm)" : "Klick: kaufen (danach bestätigen)"));
            }
        }
        // Stufen kaufen
        c.box(0, 162, W, 87, v.look().buyBox());
        c.text(9, 173, en ? "Buy levels" : "Stufen kaufen", v.look().accent(), true);
        String n = Msg.coins(v.jewels());
        int nx = 381 - Canvas.width(n, true);
        c.text(nx, 173, n, JEWELS, true);
        c.sprite(nx - 18, 169, KitData.Sprite.JS_EMERALD_PRICE);
        c.textRight(nx - 24, 173, en ? "Your jewels" : "Deine Juwelen", SUB, false);
        c.text(9, 188, en ? "One level costs " + Msg.coins(v.levelPrice()) + " jewels and unlocks its rewards right away."
                : "Eine Stufe kostet " + Msg.coins(v.levelPrice()) + " Juwelen und gibt ihre Belohnungen sofort frei.", DESC, false);
        int amount = v.amount();
        int max = Math.max(1, v.leftToday());
        step(c, a, 7, "-5", amount - 5 >= 1, -5, en);
        step(c, a, 41, "-1", amount - 1 >= 1, -1, en);
        String al = amount + (en ? (amount == 1 ? " level" : " levels") : amount == 1 ? " Stufe" : " Stufen");
        int vw = 28 + Canvas.width(al, true) + 8;
        c.box(75, 200, vw, 22, v.look().btnMain(), 1);
        c.sprite(79, 203, v.look().play());
        c.text(103, 207, al, WHITE, true);
        step(c, a, 75 + vw + 4, "+1", amount + 1 <= max, 1, en);
        step(c, a, 75 + vw + 38, "+5", amount + 5 <= max, 5, en);
        boolean armedNow = "levels".equals(v.armed());
        String bl = (armedNow ? (en ? "Sure? · " : "Sicher? · ") : (en ? "Buy · " : "Kaufen · "))
                + Msg.coins(v.levelPrice() * amount);
        int bw = 28 + Canvas.width(bl, true) + 8;
        int bx = 383 - bw;
        c.box(bx, 200, bw, 22, KitData.Box.BP_BTN_RED_ON, 1);
        c.sprite(bx + 9, 203, KitData.Sprite.JS_EMERALD_PRICE);
        c.text(bx + 28, 207, bl, v.look().dark(), true);
        if (a != null) {
            long price = v.levelPrice() * amount;
            String text = v.leftToday() <= 0 ? (en ? "No more levels can be bought today." : "Heute sind keine Stufen mehr kaufbar.")
                    : v.jewels() < price ? (en ? "You are missing " + Msg.coins(price - v.jewels()) + " jewels."
                    : "Dir fehlen " + Msg.coins(price - v.jewels()) + " Juwelen.")
                    : (en ? "Levels " : "Stufen ") + (v.level() + 1) + "–" + Math.min(BattlepassService.MAX_LEVEL, v.level() + amount)
                    + (en ? " for " : " für ") + Msg.coins(price) + (en ? " jewels." : " Juwelen.");
            c.action(bx, 200, bw, 22, a.buy(), tip(en ? "Buy levels" : "Stufen kaufen", text,
                    armedNow ? (en ? "Click again: buy now" : "Nochmal klicken: jetzt kaufen")
                            : en ? "Click: buy (then confirm)" : "Klick: kaufen (danach bestätigen)"));
        }
        c.text(9, 228, en ? Msg.coins(v.leftToday()) + " left today · Bought levels give no XP"
                : "Heute noch " + Msg.coins(v.leftToday()) + " · Gekaufte Stufen geben keine XP", SUB, false);
    }

    private static void step(Canvas c, Actions a, int x, String label, boolean on, int delta, boolean en) {
        c.box(x, 200, 30, 22, on ? KitData.Box.BP_BTN_GREEN : KitData.Box.BP_BTN_GRAY, 1);
        center(c, x + 15, 207, label, on ? GREEN : OFF, true);
        if (a != null && on) {
            c.action(x, 200, 30, 22, a.amount(delta), Component.text(label + (en ? (Math.abs(delta) == 1 ? " level" : " levels")
                    : Math.abs(delta) == 1 ? " Stufe" : " Stufen"), WHITE));
        }
    }

    private static Component tip(String title, String text, String click) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(title, WHITE).decorate(TextDecoration.BOLD));
        for (String l : Canvas.wrap(text, 200)) b.append(Component.newline()).append(Component.text(l, DESC));
        if (click != null) b.append(Component.newline()).append(Component.newline()).append(Component.text(click, TIP_CLICK));
        return b.build();
    }
}
