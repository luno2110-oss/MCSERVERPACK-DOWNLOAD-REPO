package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.EconomyMarketService;
import de.kuby.kubyplugins.service.QuizService;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Kleinere Dialoge, jeder mit eigener Optik: XP-Shop (/xpshop) leuchtend grün wie Erfahrung, Investments (/investments)
 * als Börsen-Tafel (dunkles Türkis, Gewinne grün, Verluste rot), Duo-RTP (/rtpqueue) als Arena in Glut-Orange und die
 * Quiz-Abstimmung (/quizvote) als Schultafel mit Kreide-Balken. Ohne Pack: die Truhen.
 */
public final class ExtraScreens {

    static final int W = 390;
    static final TextColor XP = TextColor.color(0xC8F870), LIME = TextColor.color(0x9AC860), CREAM = TextColor.color(0xF2F6E8),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xFF7A7A),
            JEWEL = TextColor.color(0x5FE3A0), WHITE = TextColor.color(0xF4F4F4), DARK = TextColor.color(0x0C140A),
            MINT = TextColor.color(0x6FF0D0), TEALDIM = TextColor.color(0x5EA898), EMBER = TextColor.color(0xFFAA66),
            ASH = TextColor.color(0xC09078), CHALK = TextColor.color(0xEAF4EA), CHALKDIM = TextColor.color(0x9CB8A0);

    // ---- Modelle ----

    /** Ware im XP-Shop: Icon, Name, Farbe, Preis in XP, kaufbar (genug XP), Klick. */
    public record XpOffer(KitData.Icon icon, String name, TextColor color, long cost, boolean affordable, Consumer<Player> buy) {}

    /** Anlage: Icon, Name, aktiv, Shop-Preis, Anteilspreis, eigene Anteile, Wert, Gewinn, Klicks. */
    public record Stock(KitData.Icon icon, String name, boolean active, long price, double share, double mine, long value,
                        long gain, Consumer<Player> small, Consumer<Player> big, Consumer<Player> sell) {}

    public record Portfolio(long value, long invested, long step, List<Stock> stocks) {}

    /** Quiz-Kategorie: Name, Icon, Stimmen, eigene Stimme, Klick. */
    public record Category(String name, KitData.Icon icon, int votes, boolean mine, Consumer<Player> vote) {}

    public record Quiz(String active, int questions, List<Category> categories) {}

    private final KubyPlugins plugin;

    public ExtraScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // =================================================================================================================
    // XP-Shop
    // =================================================================================================================

    public void xpShop(Player p) {
        if (!plugin.uiPack().active(p)) {
            new XpShopMenu(plugin).open(p);
            return;
        }
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        List<XpOffer> offers = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("xp.shop")) {
            String[] parts = raw.split(";");
            if (parts.length < 3) continue;
            String kind = parts[0].trim().toUpperCase(Locale.ROOT), value = parts[1].trim();
            long cost;
            try {
                cost = Long.parseLong(parts[2].trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            KitData.Icon icon = kind.equals("COINS") ? KitData.Icon.GOLD_NUGGET : kind.equals("JEWELS") ? KitData.Icon.EMERALD : KitData.Icon.NAME_TAG;
            String name = kind.equals("COINS") ? value + "$" : kind.equals("JEWELS") ? value + " Juwelen" : "Titel " + value;
            TextColor color = kind.equals("COINS") ? GOLD : kind.equals("JEWELS") ? JEWEL : CREAM;
            long price = cost;
            offers.add(new XpOffer(icon, name, color, cost, s.xp >= cost, pl -> {
                buyXp(pl, kind, value, price);
                xpShop(pl);
            }));
        }
        UiDialog.show(p, xpShop(s.xp, offers), 260);
    }

    private void buyXp(Player p, String kind, String value, long cost) {
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        if (s.xp < cost) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Dir fehlen noch " + Msg.coins(cost - s.xp) + " XP.");
            return;
        }
        try {
            switch (kind) {
                case "COINS" -> plugin.economy().deposit(p.getUniqueId(), Long.parseLong(value));
                case "JEWELS" -> plugin.jewels().add(p.getUniqueId(), Long.parseLong(value));
                case "TAG" -> plugin.tags().grant(p.getUniqueId(), value);
                default -> throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Diese Ware ist in der config.yml falsch eingetragen.");
            return;
        }
        s.xp -= cost;
        plugin.stats().save();
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<green>Gekauft! <gray>-" + Msg.coins(cost) + " XP");
    }

    public static Canvas xpShop(long xp, List<XpOffer> offers) {
        Canvas c = new Canvas(W).heading("XP-Shop", XP);
        c.box(0, 0, W, 54, KitData.Box.XP);
        c.icon(10, 11, KitData.Icon.EXPERIENCE, true);
        c.title(50, 12, "XP-Shop", false, XP);
        c.text(50, 36, "XP aus Kills, Quests und Votes eintauschen", LIME, false);
        c.textRight(378, 14, Msg.coins(xp), XP, true);
        c.textRight(378, 26, Canvas.caps("XP verfügbar"), LIME, false);
        int y = 60, cols = 3, cw = (W - 6) / cols, ch = 58;
        for (int i = 0; i < offers.size(); i++) {
            XpOffer o = offers.get(i);
            int x = (i % cols) * (cw + 3), oy = y + (i / cols) * (ch + 3);
            c.box(x, oy, cw, ch, o.affordable() ? KitData.Box.XP : KitData.Box.GRAY);
            c.icon(x + (cw - 16) / 2, oy + 6, o.icon(), false);
            c.textCenter(x + cw / 2, oy + 26, Canvas.fit(Canvas.lit(o.name()), true, cw - 10), o.affordable() ? o.color() : CHALKDIM, true);
            c.smallButton(x + 8, oy + ch - 22, cw - 16, o.affordable() ? KitData.Small.GREEN : KitData.Small.DARK,
                    Msg.coins(o.cost()) + " XP", o.affordable() ? WHITE : CHALKDIM, false);
            if (o.affordable()) c.action(x, oy, cw, ch, o.buy(), o.name() + " für " + Msg.coins(o.cost()) + " XP");
        }
        if (offers.isEmpty()) c.text(4, y, "Im XP-Shop gibt es gerade nichts.", LIME, false);
        return c;
    }

    // =================================================================================================================
    // Investments
    // =================================================================================================================

    public void investments(Player p) {
        if (!plugin.uiPack().active(p)) {
            new InvestmentsMenu(plugin).open(p);
            return;
        }
        UUID id = p.getUniqueId();
        EconomyMarketService inv = plugin.investments();
        long step = plugin.getConfig().getLong("economy.invest-step", 1000);
        double value = 0;
        long invested = 0;
        List<Stock> list = new ArrayList<>();
        for (String sec : EconomyMarketService.DYNAMIC) {
            for (ShopService.Item it : plugin.shop().items(sec)) {
                double v = inv.valueOf(id, it.id());
                long cost = inv.costOf(id, it.id());
                value += v;
                invested += cost;
                String name = it.name() != null ? Canvas.clean(it.name()) : nice(it.material());
                list.add(new Stock(Icons.of(it.material(), KitData.Icon.RAW_GOLD), name, inv.isActive(it.id()), inv.priceOf(it),
                        inv.sharePrice(it.id()), inv.sharesOf(id, it.id()), Math.round(v), Math.round(v - cost),
                        pl -> invest(pl, it, step), pl -> invest(pl, it, step * 10), pl -> {
                    String err = plugin.investments().sellShares(pl.getUniqueId(), it.id());
                    Msg.send(pl, err == null ? "<green>Anteile verkauft." : "<red>" + err);
                    Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                    investments(pl);
                }));
            }
        }
        UiDialog.show(p, investments(new Portfolio(Math.round(value), invested, step, list)), 330);
    }

    private void invest(Player p, ShopService.Item it, long amount) {
        String err = plugin.investments().invest(p.getUniqueId(), it.id(), amount);
        Msg.send(p, err == null ? "<green>" + Msg.coins(amount) + "$ investiert." : "<red>" + err);
        Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        investments(p);
    }

    static String nice(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String signed(long v) {
        return (v >= 0 ? "+" : "−") + Msg.coins(Math.abs(v)) + "$";
    }

    public static Canvas investments(Portfolio v) {
        Canvas c = new Canvas(W).heading("Investments", MINT);
        c.box(0, 0, W, 54, KitData.Box.TERMINAL);
        c.icon(10, 11, KitData.Icon.RAW_GOLD, true);
        c.title(50, 12, "Börse", false, MINT);
        c.text(50, 36, "Anteile an Shop-Waren · Kurse schwanken", TEALDIM, false);
        long gain = v.value() - v.invested();
        c.textRight(378, 10, Msg.coins(v.value()) + "$", CREAM, true);
        c.textRight(378, 21, Canvas.caps("Depotwert"), TEALDIM, false);
        c.textRight(378, 36, signed(gain), gain >= 0 ? GREEN : RED, true);
        // Tabellenkopf
        int y = 60;
        c.text(30, y, Canvas.caps("Ware"), TEALDIM, false);
        c.textRight(176, y, Canvas.caps("Kurs"), TEALDIM, false);
        c.textRight(244, y, Canvas.caps("Wert"), TEALDIM, false);
        c.text(252, y, Canvas.caps("Kaufen · Verkaufen"), TEALDIM, false);
        y += 11;
        for (Stock s : v.stocks()) {
            c.box(0, y, W, 30, s.active() ? KitData.Box.TERMINAL_ROW : KitData.Box.GRAY);
            c.icon(7, y + 7, s.icon(), false);
            c.text(30, y + 6, Canvas.fit(Canvas.lit(s.name()), true, 110), s.active() ? CREAM : CHALKDIM, true);
            c.text(30, y + 17, String.format(Locale.GERMANY, "%.2f Anteile", s.mine()), TEALDIM, false);
            c.textRight(176, y + 6, Msg.coins(s.price()) + "$", CREAM, false);
            c.textRight(176, y + 17, String.format(Locale.GERMANY, "%.2f", s.share()), TEALDIM, false);
            c.textRight(244, y + 6, Msg.coins(s.value()) + "$", CREAM, false);
            c.textRight(244, y + 17, signed(s.gain()), s.gain() >= 0 ? GREEN : RED, false);
            int bx = 252;
            String a = "+" + short_(v.step()), b = "+" + short_(v.step() * 10);
            int aw = Canvas.width(a, false) + 10, bw2 = Canvas.width(b, false) + 10;
            if (s.active()) {
                c.smallButton(bx, y + 7, aw, KitData.Small.GREEN, a, WHITE, false);
                c.action(bx, y + 7, aw, KitData.SMALL_H, s.small(), Msg.coins(v.step()) + "$ investieren");
                c.smallButton(bx + aw + 3, y + 7, bw2, KitData.Small.GREEN, b, WHITE, false);
                c.action(bx + aw + 3, y + 7, bw2, KitData.SMALL_H, s.big(), Msg.coins(v.step() * 10) + "$ investieren");
            } else {
                c.text(bx, y + 11, "gesperrt", CHALKDIM, false);
            }
            if (s.mine() > 0) {
                int sx = W - 8 - 30;
                c.smallButton(sx, y + 7, 30, KitData.Small.DARK, "alle", RED, false);
                c.action(sx, y + 7, 30, KitData.SMALL_H, s.sell(), "Alle Anteile verkaufen");
            }
            y += 30 + 2;
        }
        if (v.stocks().isEmpty()) c.text(4, y, "Gerade gibt es keine Waren mit Anteilen.", TEALDIM, false);
        return c;
    }

    /** 1.000 → 1k, 10.000 → 10k (kurz für Knöpfe). */
    static String short_(long v) {
        if (v >= 1_000_000 && v % 1_000_000 == 0) return v / 1_000_000 + "M";
        if (v >= 1000 && v % 1000 == 0) return v / 1000 + "k";
        return Msg.coins(v);
    }

    // =================================================================================================================
    // Duo-RTP
    // =================================================================================================================

    public void rtp(Player p) {
        if (!plugin.uiPack().active(p)) {
            new RtpQueueMenu(plugin).open(p);
            return;
        }
        boolean queued = plugin.rtpQueue().inQueue(p.getUniqueId());
        UiDialog.show(p, rtp(queued, pl -> {
            String err = plugin.rtpQueue().join(pl);
            if (err != null) Msg.send(pl, "<red>" + err);
            rtp(pl);
        }, pl -> {
            String err = plugin.rtpQueue().leave(pl.getUniqueId());
            Msg.send(pl, err == null ? "<gray>Aus der Warteschlange entfernt." : "<red>" + err);
            rtp(pl);
        }), 220);
    }

    public static Canvas rtp(boolean queued, Consumer<Player> join, Consumer<Player> leave) {
        Canvas c = new Canvas(W).heading("Duo-RTP", EMBER);
        c.box(0, 0, W, 54, KitData.Box.EMBER);
        c.icon(10, 11, KitData.Icon.FIRE_CHARGE, true);
        c.title(50, 12, "Duo-RTP", false, EMBER);
        c.text(50, 36, "Zwei Spieler, ein Zufallsort, ein Kampf", ASH, false);
        int y = 60;
        c.box(0, y, W, 80, KitData.Box.EMBER_CARD);
        // Du gegen jemanden
        c.icon(70, y + 14, KitData.Icon.IRON_SWORD, true);
        c.title(W / 2 - Canvas.titleWidth("VS") / 2, y + 22, "VS", true, null);
        c.icon(W - 70 - 32, y + 14, KitData.Icon.IRON_SWORD, true);
        c.textCenter(86, y + 52, "Du", CREAM, true);
        c.textCenter(W - 86, y + 52, queued ? "wird gesucht …" : "?", queued ? EMBER : ASH, true);
        c.textCenter(W / 2, y + 66, queued ? "Du wartest. Sobald jemand beitritt, geht es los." : "Nicht in der Warteschlange.",
                queued ? GOLD : ASH, false);
        y += 80 + 8;
        int bw = (W - 3) / 2;
        c.button(0, y, bw, queued ? KitData.Button.DARK : KitData.Button.GREEN);
        if (queued) c.textCenter(bw / 2, y + 7, "Wartet …", ASH, true);
        else {
            c.textFlat(bw / 2 - Canvas.width("Beitreten", true) / 2, y + 7, "Beitreten", DARK, true);
            c.action(0, y, bw, 22, join, "In die Warteschlange");
        }
        c.button(bw + 3, y, bw, queued ? KitData.Button.RED : KitData.Button.DARK);
        c.text(bw + 3 + bw / 2 - Canvas.width("Verlassen", true) / 2, y + 7, "Verlassen", queued ? WHITE : ASH, true);
        if (queued) c.action(bw + 3, y, bw, 22, leave, "Warteschlange verlassen");
        return c;
    }

    // =================================================================================================================
    // Quiz-Abstimmung
    // =================================================================================================================

    static KitData.Icon quizIcon(String cat) {
        return switch (cat) {
            case "physik" -> KitData.Icon.PRISMARINE;
            case "mathe" -> KitData.Icon.MAP;
            case "deutsch" -> KitData.Icon.WRITABLE;
            case "geografie" -> KitData.Icon.COMPASS;
            case "chemie" -> KitData.Icon.EXPERIENCE;
            case "minecraft" -> KitData.Icon.GRASS_BLOCK;
            default -> KitData.Icon.ENDER_EYE;
        };
    }

    public void quiz(Player p) {
        if (!plugin.uiPack().active(p)) {
            new QuizVoteMenu(plugin).open(p);
            return;
        }
        QuizService q = plugin.quiz();
        Map<String, Integer> tally = q.tally();
        String mine = q.myVote(p.getUniqueId());
        List<Category> cats = new ArrayList<>();
        for (String cat : QuizService.CATEGORIES) {
            cats.add(new Category(cat, quizIcon(cat), tally.getOrDefault(cat, 0), cat.equals(mine), pl -> {
                String err = plugin.quiz().vote(pl.getUniqueId(), cat);
                Sfx.play(plugin, pl, err == null ? Sfx.Kind.PING : Sfx.Kind.ERROR);
                Msg.send(pl, err == null ? "<green>Stimme für " + cat + " gespeichert." : "<red>" + err);
                quiz(pl);
            }));
        }
        UiDialog.show(p, quiz(new Quiz(q.activeCategory(), q.questionCount(), cats)), 280);
    }

    public static Canvas quiz(Quiz v) {
        Canvas c = new Canvas(W).heading("Quiz-Abstimmung", CHALK);
        c.box(0, 0, W, 54, KitData.Box.CHALK);
        c.icon(10, 11, KitData.Icon.BOOK_PLAIN, true);
        c.title(50, 12, "Quiz", false, CHALK);
        c.text(50, 36, "Welche Kategorie kommt nächste Woche?", CHALKDIM, false);
        int y = 60;
        c.text(4, y, "Aktuell: " + (v.active() == null ? "alle Kategorien" : cap(v.active())) + " · " + v.questions() + " Fragen · Auswertung montags 00:05",
                CHALK, false);
        y += 14;
        int total = 0;
        for (Category cat : v.categories()) total += cat.votes();
        for (Category cat : v.categories()) {
            c.box(0, y, W, 28, cat.mine() ? KitData.Box.GOLD : KitData.Box.CHALK);
            c.icon(7, y + 6, cat.icon(), false);
            c.text(30, y + 10, cap(cat.name()), cat.mine() ? GOLD : CHALK, true);
            double f = total == 0 ? 0 : cat.votes() / (double) total;
            c.bar(130, y + 10, 160, f, cat.mine() ? KitData.Fill.GOLD : KitData.Fill.GREEN);
            c.textRight(332, y + 10, cat.votes() + (cat.votes() == 1 ? " Stimme" : " Stimmen"), CHALKDIM, false);
            if (cat.mine()) c.textRight(382, y + 10, "✔ du", GREEN, true);
            else {
                c.smallButton(340, y + 6, 44, KitData.Small.DARK, "wählen", CHALK, false);
                c.action(0, y, W, 28, cat.vote(), "Für " + cap(cat.name()) + " stimmen");
            }
            y += 28 + 3;
        }
        return c;
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
